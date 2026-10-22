"""Build shared documentation/cases; sync stage via local API, production via MCP."""
from pathlib import Path
import argparse
import json
import os
import re
import tomllib
import urllib.request
import sync_tcm_relocation_batch_accounting as core
from sync_tcm_commercial_document_required_fields import verify_case

ROOT = Path(__file__).resolve().parents[1]
AUDIT = ROOT / 'docs/audits/2026-10-21'
FEATURE = 'REQ-REL-EDIT-RETURN'

def payload():
    documentation = (ROOT / f'docs/{FEATURE}.md').read_text(encoding='utf-8')
    criteria = [dict(featureId=FEATURE, acKey=key, text=value) for key, value in
                re.findall(r'^- \*\*(AC-\d+):\*\* (.+)$', documentation, re.M)]
    cases = []
    source = (ROOT / 'src/test/java/com/erp/tests/ui/RelocationEditReturnFiltersUiTest.java').read_text(encoding='utf-8')
    for test_id, title, ac, body in re.findall(
            r'^### (TC-REL-RETURN-UI-\d+) — (.+?) \[(AC-\d+)\]\n(.*?)(?=^### |^## |\Z)', documentation, re.M | re.S):
        class_name, method = re.search(r'Автотест: `(\w+)#(\w+)`', body).groups()
        assert f'@TestCaseId("{test_id}")' in source and f'void {method}(' in source
        actions = re.findall(r'^\d+\. (.+)$', body, re.M)
        expected = re.search(r'^Очікування: (.+)$', body, re.M).group(1)
        cases.append(dict(projectId=1, featureId=FEATURE, acKey=ac, testId=test_id, title=title,
            description=f'{class_name}#{method}; suite relocation-edit-return-filters. Кожен кейс: Видано та Отримано. AC-01/AC-06 перевіряються в усіх кейсах.\n'+body.strip(),
            preconditions='ERP dev; ADMIN з правом редагування власної локації. Chrome, Java 21. Без БД та SSH.',
            testData='RelocationEditReturnFixture: власні зовнішня/внутрішні локації, ресурси різних категорій і постачальників, старі/нові AUTO_FINISHED, дві незалежні моделі/екземпляри обладнання для отримання й видачі та контрольні записи. Cleanup обов’язковий.',
            expectedResult=expected, status='ACTIVE', priority='HIGH', severity='MAJOR', testType='UI',
            tags='relocation,edit,filters,return-navigation,automated,dev', apiAutomationIds=[], uiAutomationIds=[test_id],
            steps=[dict(stepOrder=i, actionText=action, **({'expectedText': expected} if i == len(actions) else {}))
                   for i, action in enumerate(actions, 1)]))
    assert len(criteria) == 6 and len(cases) == 6
    return dict(feature=dict(projectId=1, featureId=FEATURE, parentFeatureId='REQ-WMS-006', module='WMS',
            title='Видано та Отримано — збереження фільтрів після редагування', priority='HIGH',
            description='Повернення на вихідну вкладку зі знімком фільтрів, актуальна вибірка й Excel після редагування ресурсів та обладнання.',
            documentation=documentation), criteria=criteria, cases=cases)

def sync_stage(client):
    assert client.base_url == 'http://localhost:18100', 'Use production MCP for production writes'
    data = payload()
    f = data['feature']
    core.FEATURE_ID, core.FEATURE_PARENT_ID = FEATURE, f['parentFeatureId']
    core.FEATURE_TITLE, core.FEATURE_DESCRIPTION = f['title'], f['description']
    core.FEATURE_MODULE, core.FEATURE_PRIORITY = f['module'], f['priority']
    core.DOCUMENTATION_PATH = ROOT / f'docs/{FEATURE}.md'
    core.ACCEPTANCE_CRITERIA = [(a['acKey'], a['text']) for a in data['criteria']]
    core.CASES = data['cases']
    report = core.sync(client)
    for case in data['cases']:
        verify_case(client.request('GET', f"/api/ai/projects/1/test-cases/{case['testId']}"), case)
    feature = client.request('GET', f'/api/ai/projects/1/features/{FEATURE}')
    report['featureDbId'] = feature['id']
    assert report['verification']['documentationMatchesSource'] and report['verification']['allCasesPresent']
    report['result'] = 'SUCCESS'
    return report

def import_stage(client, path):
    assert client.base_url == 'http://localhost:18100'
    body = json.loads(path.read_text(encoding='utf-8'))['tcmImport']
    expected = {c['testId'] for c in payload()['cases']}
    assert {r['testCaseId'] for r in body['results']} == expected and body['environment'] == 'dev'
    prefix = '/api/ai/projects/1'
    feature = client.request('GET', f'{prefix}/features/{FEATURE}')
    body = dict(body, featureId=feature['id'])
    try:
        imported = client.request('POST', f'{prefix}/test-runs/import', body)
    except RuntimeError as error:
        if 'HTTP 405' not in str(error): raise
        values = {}
        for line in (ROOT.parent/'tcm/.env').read_text(encoding='utf-8').splitlines():
            key, sep, value = line.partition('=')
            if sep and not key.lstrip().startswith('#'): values[key.strip()] = value.strip().strip('"\'')
        token = os.environ.get('TCM_AUTOTEST_TOKEN') or values['TCM_AUTOTEST_TOKEN']
        headers = {'X-TCM-Api-Token':token, 'Content-Type':'application/json; charset=utf-8'}
        request = urllib.request.Request(f"{client.base_url}/api/autotest/suites/features/{feature['id']}",headers=headers)
        with urllib.request.urlopen(request,timeout=90) as response: suite = json.load(response)
        assert set(suite['automationTestIds']) == expected
        request = urllib.request.Request(f'{client.base_url}/api/autotest/runs',method='POST',
            data=json.dumps(body,ensure_ascii=False).encode('utf-8'),headers=headers)
        with urllib.request.urlopen(request,timeout=90) as response: imported = json.load(response)
    assert imported['matched'] == 6 and not imported['unmatched'] and not imported['outOfScope']
    run = client.request('GET',f"{prefix}/test-runs/{imported['runId']}")
    assert run['environment'] == 'dev' and run['status'] == 'COMPLETED'
    assert {r['testId']:r['status'] for r in run['items']} == {r['testCaseId']:r['status'] for r in body['results']}
    return dict(result='SUCCESS',baseUrl=client.base_url,featureDbId=feature['id'],importResponse=imported,run=run)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sync-stage', action='store_true')
    parser.add_argument('--import-results', type=Path)
    args = parser.parse_args()
    AUDIT.mkdir(parents=True, exist_ok=True)
    (AUDIT/'relocation-edit-return-tcm-payload.json').write_text(json.dumps(payload(), ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print('Prepared 6 AC / 6 automated cases / 12 executions')
    if args.sync_stage or args.import_results:
        config = tomllib.loads((Path.home()/'.codex/config.toml').read_text(encoding='utf-8'))
        token = os.environ.get('TCM_AI_TOKEN') or config['mcp_servers']['tcm-ai']['env']['TCM_AI_TOKEN']
        client = core.TcmClient('http://localhost:18100', token)
        report = import_stage(client,args.import_results) if args.import_results else sync_stage(client)
        suffix = 'run' if args.import_results else 'sync'
        (AUDIT/f'relocation-edit-return-tcm-stage-{suffix}.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        print(json.dumps(dict(result=report['result'], featureDbId=report['featureDbId'])))
