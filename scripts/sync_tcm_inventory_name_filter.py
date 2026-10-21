"""Build a verified payload; sync the local/stage TCM. Production uses tcm-ai-prod MCP."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tomllib
import urllib.request

from sync_tcm_relocation_batch_accounting import TcmClient
from sync_tcm_commercial_document_required_fields import verify_case

ROOT = Path(__file__).resolve().parents[1]
FEATURE_ID = 'REQ-INV-NAME'
PARENT_ID = 'REQ-WMS-003'


def payload():
    documentation = (ROOT / 'docs/REQ-INV-NAME.md').read_text(encoding='utf-8')
    criteria = [dict(featureId=FEATURE_ID, acKey=key, text=text) for key, text in
                re.findall(r'^- \*\*(AC-\d+):\*\* (.+)$', documentation, re.M)]
    cases = []
    for test_id, layer, title, ac_key, body in re.findall(
            r'^### (TC-INV-NAME-(API|UI)-\d+) — (.+?) \[(AC-\d+)\]\n(.*?)(?=^### |^## |\Z)',
            documentation, re.M | re.S):
        class_name, method = re.search(r'Автотест: `(\w+)#(\w+)`', body).groups()
        package = 'functional/inventory' if layer == 'API' else 'ui'
        java = ROOT / f'src/test/java/com/erp/tests/{package}/{class_name}.java'
        source = java.read_text(encoding='utf-8')
        assert f'@TestCaseId("{test_id}")' in source and f'void {method}(' in source, test_id
        actions = re.findall(r'^\d+\. (.+)$', body, re.M)
        expected = re.search(r'^Очікування: (.+)$', body, re.M).group(1)
        cases.append(dict(projectId=1, featureId=FEATURE_ID, acKey=ac_key, testId=test_id, title=title,
            description=f'{class_name}#{method}. Suite inventory-name-filter; docs/REQ-INV-NAME.md. '+body.strip(),
            preconditions='ERP dev; ADMIN; власна локація, три унікальні ресурси та 4 процеси. UI: Chrome, workspace власної локації. JDBC/SSH не потрібні.',
            testData='InventoryNameFilterFixture: mixed А:+2.25/Б:-3.5/Кабель:+4; cableOnly Кабель:+1; manual і rejected без diff. Залишки 12.25/6.5/15. Cleanup власних ресурсів і локації.',
            expectedResult=expected, status='ACTIVE', priority='HIGH', severity='MAJOR',
            testType='FUNCTIONAL' if layer == 'API' else 'UI', tags='inventory,history,resource-name,filter,excel,automated,dev',
            apiAutomationIds=[test_id] if layer == 'API' else [], uiAutomationIds=[test_id] if layer == 'UI' else [],
            steps=[dict(stepOrder=i, actionText=action, **({'expectedText': expected} if i == len(actions) else {}))
                   for i, action in enumerate(actions, 1)]))
    assert len(criteria) == 3 and len(cases) == 9
    return dict(feature=dict(projectId=1, featureId=FEATURE_ID, parentFeatureId=PARENT_ID, module='WMS',
            title='Інвентаризація — фільтр по назві ресурсу', priority='HIGH',
            description='Інвентаризація → Закриті: пошук за назвою, тільки відповідні ресурси в розбіжностях, Excel за поточним фільтром.',
            documentation=documentation), criteria=criteria, cases=cases)


def sync_stage(client):
    assert client.base_url == 'http://localhost:18100', 'Production writes must use tcm-ai-prod MCP'
    data = payload()
    prefix = '/api/ai/projects/1'
    parent = client.request('GET', f'{prefix}/features/{PARENT_ID}')
    assert parent['featureId'] == PARENT_ID
    path = f'{prefix}/features/{FEATURE_ID}'
    existing = client.get_optional(path)
    client.request('PUT' if existing else 'POST', path if existing else f'{prefix}/features', data['feature'])
    feature = client.request('GET', path)
    acs = {ac['acId']: ac for ac in feature['acceptanceCriteria']}
    for ac in data['criteria']:
        current = acs.get(ac['acKey'])
        client.request('PUT' if current else 'POST',
            f"{prefix}/acceptance-criteria/{current['id']}" if current else f'{prefix}/acceptance-criteria', ac)
    feature = client.request('GET', path)
    acs = {ac['acId']: ac for ac in feature['acceptanceCriteria']}
    for case in data['cases']:
        case_path = f"{prefix}/test-cases/{case['testId']}"
        current = client.get_optional(case_path)
        write = dict(case, acceptanceCriterionId=acs[case['acKey']]['id'])
        client.request('PUT' if current else 'POST', case_path if current else f'{prefix}/test-cases', write)
        verify_case(client.request('GET', case_path), case)
    feature = client.request('GET', path)
    assert feature['documentation'] == data['feature']['documentation']
    assert feature['parentFeatureId'] == PARENT_ID
    for ac in data['criteria']:
        assert acs[ac['acKey']]['text'] == ac['text']
    return dict(baseUrl=client.base_url, featureDbId=feature['id'], featureId=FEATURE_ID,
                criteriaVerified=3, casesVerified=9, automationLinksVerified=9,
                documentationSha256=hashlib.sha256(feature['documentation'].encode()).hexdigest(), result='SUCCESS')


def import_stage(client, results_path):
    prefix = '/api/ai/projects/1'
    feature = client.request('GET', f'{prefix}/features/{FEATURE_ID}')
    body = json.loads(results_path.read_text(encoding='utf-8'))['tcmImport']
    assert {r['testCaseId'] for r in body['results']} == {c['testId'] for c in payload()['cases']}
    body = dict(body, featureId=feature['id'])
    try:
        imported = client.request('POST', f'{prefix}/test-runs/import', body)
    except RuntimeError as error:
        if 'HTTP 405' not in str(error):
            raise
        values = {}
        for line in (ROOT.parent/'tcm/.env').read_text(encoding='utf-8').splitlines():
            key, separator, value = line.partition('=')
            if separator and not key.lstrip().startswith('#'):
                values[key.strip()] = value.strip().strip('"\'')
        token = os.environ.get('TCM_AUTOTEST_TOKEN') or values['TCM_AUTOTEST_TOKEN']
        headers = {'X-TCM-Api-Token': token, 'Content-Type': 'application/json; charset=utf-8'}
        request = urllib.request.Request(f"{client.base_url}/api/autotest/suites/features/{feature['id']}", headers=headers)
        with urllib.request.urlopen(request, timeout=90) as response:
            suite = json.load(response)
        assert set(suite['automationTestIds']) == {r['testCaseId'] for r in body['results']}
        request = urllib.request.Request(f'{client.base_url}/api/autotest/runs', method='POST',
                    data=json.dumps(body, ensure_ascii=False).encode('utf-8'), headers=headers)
        with urllib.request.urlopen(request, timeout=90) as response:
            imported = json.load(response)
    assert imported['matched'] == 9 and not imported['unmatched'] and not imported['outOfScope']
    run = client.request('GET', f"{prefix}/test-runs/{imported['runId']}")
    assert run['environment'] == 'dev' and run['status'] == 'COMPLETED'
    assert {r['testId']: r['status'] for r in run['items']} == {r['testCaseId']: r['status'] for r in body['results']}
    return dict(baseUrl=client.base_url, featureDbId=feature['id'], importResponse=imported, run=run, result='SUCCESS')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--payload', type=Path)
    parser.add_argument('--sync-stage', action='store_true')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--import-results', type=Path)
    args = parser.parse_args()
    if args.payload:
        args.payload.parent.mkdir(parents=True, exist_ok=True)
        args.payload.write_text(json.dumps(payload(), ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        print('Prepared 3 AC and 9 automated test cases')
    if args.sync_stage or args.import_results:
        config = tomllib.loads((Path.home()/'.codex/config.toml').read_text(encoding='utf-8'))
        token = os.environ.get('TCM_AI_TOKEN') or config['mcp_servers']['tcm-ai']['env']['TCM_AI_TOKEN']
        client = TcmClient('http://localhost:18100', token)
        report = sync_stage(client) if args.sync_stage else import_stage(client, args.import_results)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        summary = report if 'run' not in report else dict(baseUrl=report['baseUrl'],
            runId=report['run']['id'], matched=report['importResponse']['matched'], result=report['result'])
        print(json.dumps(summary))
