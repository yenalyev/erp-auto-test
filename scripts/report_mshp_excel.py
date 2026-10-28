"""Collect actual TestNG/Allure results and evidence for one immutable МШП Excel run."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

def collect(run, output):
    output.mkdir(parents=True, exist_ok=True)
    evidence = output / (run.name + '-evidence')
    evidence.mkdir(exist_ok=True)
    xml_path = run / 'surefire-reports/testng-results.xml'
    xml = ET.parse(xml_path).getroot()
    shutil.copy2(xml_path, evidence / 'testng-results.xml')
    failures = []
    for method in xml.iter('test-method'):
        if method.get('is-config') == 'true' and method.get('status') == 'FAIL':
            message = method.find('exception/message')
            failures.append(dict(method=method.get('name'), error=''.join(message.itertext()).strip() if message is not None else ''))
    results = []
    regression = []
    regression_ids = {'TC-MSHP-API-007', 'TC-MSHP-API-008', 'TC-MSHP-UI-010'}
    attachments = []
    zone = timezone(timedelta(hours=2))  # Europe/Kyiv on 2026-10-28
    for path in sorted((run / 'allure-results').glob('*-result.json')):
        value = json.loads(path.read_text(encoding='utf-8'))
        ids = [x['value'] for x in value.get('labels', []) if x['name'] == 'testCaseId'
               and (x['value'].startswith('TC-MSHP-XLSX-UI-') or x['value'] in regression_ids)]
        if not ids:
            continue
        assert len(ids) == 1
        status = {'passed': 'PASS', 'failed': 'FAIL', 'broken': 'FAIL', 'skipped': 'BLOCKED' if failures else 'SKIPPED'}[value['status']]
        target_results = regression if ids[0] in regression_ids else results
        target_results.append(dict(testCaseId=ids[0], status=status, durationMs=value['stop']-value['start'],
            executedAt=datetime.fromtimestamp(value['start']/1000, zone).replace(tzinfo=None).isoformat(),
            errorMessage=value.get('statusDetails', {}).get('message') if status != 'PASS' else None))
        sources = list(value.get('attachments', []))
        def steps(items):
            for step in items:
                sources.extend(step.get('attachments', []))
                steps(step.get('steps', []))
        steps(value.get('steps', []))
        for index, item in enumerate(sources):
            if item.get('name') == 'Export request':
                source = run / 'allure-results' / item['source']
                target = evidence / (ids[0] + '-' + str(index) + '-request.md')
                target.write_text('# Browser export request\n\n```text\n' + source.read_text(encoding='utf-8') + '\n```\n', encoding='utf-8')
                attachments.append(dict(testCaseId=ids[0], name=item['name'], path=str(target)))
                continue
            if item.get('type') not in ('image/png', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'):
                continue
            source = run / 'allure-results' / item['source']
            target = evidence / (ids[0] + '-' + str(index) + source.suffix)
            shutil.copy2(source, target)
            attachments.append(dict(testCaseId=ids[0], name=item['name'], path=str(target)))
    assert {x['testCaseId'] for x in results} == {'TC-MSHP-XLSX-UI-' + str(i).zfill(3) for i in range(1,9)}
    assert len(results) == 8
    results.sort(key=lambda x:x['testCaseId'])
    data = dict(environment='dev', endpoint='https://dev.cukerochka.sbs', runDirectory=str(run),
                timezone='Europe/Kyiv', counts=dict(Counter(x['status'] for x in results)),
                regressionCounts=dict(Counter(x['status'] for x in regression)), regressionResults=regression,
                testngCounts={key:xml.get(key) for key in ('passed','failed','skipped','total')},
                configurationFailures=failures, attachments=attachments, results=results)
    for file in ('low-value-write-off-owned.json', 'mshp-export-cleanup.json'):
        if (run / file).exists():
            shutil.copy2(run / file, evidence / file)
    (output / (run.name + '-results.json')).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    body = dict(projectId=1, remoteRunId=run.name, environment='dev', suite='low-value-write-off-export',
                runName='Списання МШП — Excel / dev / ' + run.name, results=results)
    (output / (run.name + '-tcm-import.json')).write_text(json.dumps(body, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    if regression:
        assert {x['testCaseId'] for x in regression} == regression_ids
        body['remoteRunId'] += '-journal-regression'
        body['runName'] += ' / adjacent journal regression'
        body['results'] = regression
        (output / (run.name + '-regression-tcm-import.json')).write_text(json.dumps(body, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    return dict(counts=data['counts'], regressionCounts=data['regressionCounts'], configurationFailures=len(failures), attachments=len(attachments))

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    print(json.dumps(collect(a.run, a.output)))
