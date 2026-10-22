"""Aggregate actual UI invocations, retaining the latest result for each case and tab."""
import argparse
import collections
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from sync_tcm_relocation_edit_return import payload, AUDIT, ROOT, FEATURE

def kyiv_time(epoch_ms):
    value = datetime.fromtimestamp(epoch_ms/1000, timezone.utc)
    try: zone = ZoneInfo('Europe/Kyiv')
    except ZoneInfoNotFoundError:
        assert value.date().isoformat() in ('2026-10-21','2026-10-22'), 'Install tzdata for other dates'
        zone = timezone(timedelta(hours=3))
    return value.astimezone(zone).replace(tzinfo=None).isoformat(timespec='seconds')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build', type=Path)
    parser.add_argument('--recheck', type=Path, action='append', default=[])
    parser.add_argument('--blocked-overrides', type=Path, help='Reviewed per-case/tab reasons; original results remain intact')
    args = parser.parse_args()
    build = args.build.resolve()
    builds = [build]+[p.resolve() for p in args.recheck]
    overrides = json.loads(args.blocked_overrides.read_text(encoding='utf-8')) if args.blocked_overrides else {}
    expected = {case['testId'] for case in payload()['cases']}
    evidence = AUDIT/'relocation-edit-return-evidence'
    evidence.mkdir(parents=True, exist_ok=True)
    configs = []
    methods = {re.search(r'#(\w+)',c['description'])[1]:c['testId'] for c in payload()['cases']}
    xml_latest, build_counts, sources = {}, {}, []
    for current in builds:
        source = current/'surefire-reports/testng-results.xml'
        sources.append(str(source))
        tree = ET.parse(source)
        raw_statuses = []
        for method in tree.iter('test-method'):
            if method.get('is-config') == 'true':
                if method.get('status') != 'PASS':
                    configs.append(dict(build=str(current),method=method.get('name'),status=method.get('status'),
                                        message=(method.findtext('exception/message') or '')[:8000]))
                continue
            test_id = methods.get(method.get('name'))
            if not test_id: continue
            tab = method.findtext('params/param/value').strip()
            status = {'SKIP':'SKIPPED'}.get(method.get('status'),method.get('status'))
            xml_latest[(test_id,json.dumps(dict(tab=tab),sort_keys=True))] = status
            raw_statuses.append(status)
        build_counts[str(current)] = dict(collections.Counter(raw_statuses))
    invocations = {}
    superseded = []
    files = [f for current in builds for f in (current/'allure-results').glob('*-result.json')]
    for file in files:
        item = json.loads(file.read_text(encoding='utf-8'))
        match = re.search(r'TC-REL-RETURN-UI-\d+', item.get('name', ''))
        if not match or match[0] not in expected: continue
        params = {p['name']:p['value'] for p in item.get('parameters', [])}
        key = (match[0], json.dumps(params, sort_keys=True))
        if key in invocations and invocations[key]['start'] > item['start']: continue
        if key in invocations: superseded.append(invocations[key])
        raw = {'passed':'PASS','failed':'FAIL','broken':'FAIL','skipped':'SKIPPED','unknown':'NOT_RUN'}[item['status']]
        error = item.get('statusDetails',{}).get('message')
        reason = overrides.get(match[0],{}).get(params.get('tab')) if raw != 'PASS' else None
        if raw != 'PASS' and re.search(r'ERR_CONNECTION_REFUSED|ERR_NETWORK_CHANGED|Connection refused',error or '',re.I):
            reason = reason or 'ERP dev unavailable after network interruption; no product verdict'
        status = 'BLOCKED' if reason else raw
        assert not reason or raw != 'PASS', 'Cannot override a successful invocation'
        invocations[key] = dict(testCaseId=match[0], parameters=params, start=item['start'],
            status=status, rawStatus=raw, classificationReason=reason,
            durationMs=max(0, item['stop']-item['start']), executedAt=kyiv_time(item['start']),
            errorMessage=(reason+'\n\n' if reason else '')+(error or '') or None, evidence=[],
            source=str(file.relative_to(ROOT)))
    rows = list(invocations.values())
    assert len(rows) == 12, f'Expected 12 case/tab invocations; got {len(rows)}'
    assert len(xml_latest) == 12, 'Require complete case/tab coverage from raw TestNG reports'
    xml_counts = collections.Counter(xml_latest.values())
    assert xml_counts == collections.Counter(r['rawStatus'] for r in rows), 'Allure/TestNG raw status mismatch'
    for key,row in invocations.items(): assert row['rawStatus']==xml_latest[key], 'Invocation-level Allure/TestNG mismatch'
    for row in rows:
        file = ROOT/row['source']
        item = json.loads(file.read_text(encoding='utf-8'))
        for attachment in item.get('attachments', []):
            if attachment.get('type') in ('image/png', 'text/plain'):
                target = evidence/(file.parent.parent.name+'-'+attachment['source'])
                shutil.copyfile(file.parent/attachment['source'], target)
                row['evidence'].append(str(target.relative_to(ROOT)))
    results = []
    for case in payload()['cases']:
        group = [r for r in rows if r['testCaseId'] == case['testId']]
        assert len(group) == 2, case['testId']
        status = next(s for s in ['FAIL','BLOCKED','SKIPPED','NOT_RUN','PASS'] if any(r['status']==s for r in group))
        errors = '\n\n'.join(json.dumps(r['parameters'],ensure_ascii=False)+': '+str(r['errorMessage'])
                             for r in group if r['status'] != 'PASS')
        results.append(dict(testCaseId=case['testId'], status=status, durationMs=sum(r['durationMs'] for r in group),
                            executedAt=max(r['executedAt'] for r in group), errorMessage=errors[:20000] or None))
    dates = sorted({r['executedAt'][:10] for r in rows})
    report = dict(environment='dev', featureId=FEATURE, invocationCounts=dict(collections.Counter(r['status'] for r in rows)),
        rawInvocationCounts=dict(collections.Counter(r['rawStatus'] for r in rows)),
        caseCounts=dict(collections.Counter(r['status'] for r in results)), configurationFailures=configs,
        invocations=rows, sources=sources, buildCounts=build_counts, supersededInvocations=superseded,
        tcmImport=dict(projectId=1, remoteRunId='relocation-edit-return-'+build.name, environment='dev',
            runName='Видано/Отримано — фільтри після редагування — dev — '+'…'.join(dates),
            suite='relocation-edit-return-filters', buildName=build.name, results=results))
    (AUDIT/'relocation-edit-return-dev-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('invocationCounts','caseCounts','configurationFailures')},ensure_ascii=False))

if __name__ == '__main__': main()
