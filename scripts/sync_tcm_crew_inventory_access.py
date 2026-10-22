"""Synchronize combined-role coverage with stage TCM; production uses tcm-ai-prod MCP."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tomllib
import urllib.request

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parents[1]
FEATURE = 'REQ-CREW-INVENTORY'
MARKER = '\n\n## Проведення з ролями Екіпажі облік та Керівник локації\n\n'

MATRIX_SCOPES = {'TC-CREW-INV-ACCESS-008': {'keeperScope': 'LOCATION',
                            'headScope': 'LOCATION',
                            'headKinds': None,
                            'targetKind': 'FLY_POINT',
                            'allowed': False},
 'TC-CREW-INV-ACCESS-009': {'keeperScope': 'LOCATION',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION'],
                            'targetKind': 'FLY_POINT',
                            'allowed': False},
 'TC-CREW-INV-ACCESS-010': {'keeperScope': 'LOCATION',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION', 'CREW'],
                            'targetKind': 'FLY_POINT',
                            'allowed': False},
 'TC-CREW-INV-ACCESS-011': {'keeperScope': 'LOCATION',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION', 'FLY_POINT'],
                            'targetKind': 'FLY_POINT',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-012': {'keeperScope': 'LOCATION',
                            'headScope': 'SUBTREE',
                            'headKinds': None,
                            'targetKind': 'FLY_POINT',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-013': {'keeperScope': 'SUBTREE',
                            'headScope': 'LOCATION',
                            'headKinds': None,
                            'targetKind': 'FLY_POINT',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-014': {'keeperScope': 'SUBTREE',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION'],
                            'targetKind': 'FLY_POINT',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-015': {'keeperScope': 'SUBTREE',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION', 'CREW'],
                            'targetKind': 'FLY_POINT',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-016': {'keeperScope': 'SUBTREE',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION', 'FLY_POINT'],
                            'targetKind': 'FLY_POINT',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-017': {'keeperScope': 'SUBTREE',
                            'headScope': 'SUBTREE',
                            'headKinds': None,
                            'targetKind': 'FLY_POINT',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-018': {'keeperScope': 'LOCATION',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION', 'CREW'],
                            'targetKind': 'CREW',
                            'allowed': True},
 'TC-CREW-INV-ACCESS-019': {'keeperScope': 'LOCATION',
                            'headScope': 'SUBTREE',
                            'headKinds': ['LOCATION', 'FLY_POINT'],
                            'targetKind': 'CREW',
                            'allowed': False}}


def merge_documentation(existing, document):
    return (existing or '').split(MARKER)[0].rstrip() + MARKER + document


def payload():
    document = (ROOT/'docs/CREW_INVENTORY_ACCESS_TEST_PLAN.md').read_text(encoding='utf-8')
    criteria = [dict(projectId=1, featureId=FEATURE, acKey=key, text=text)
                for key, text in re.findall(r'^- \*\*(AC-\d+):\*\* (.+)$', document, re.M)]
    source = (ROOT/'src/test/java/com/erp/tests/ui/CrewInventoryAccessUiTest.java').read_text(encoding='utf-8')
    cases = []
    positive_steps = document.split('## Кроки позитивних сценаріїв\n\n')[1].split('\n## ')[0]
    negative_steps = document.split('## Кроки негативних сценаріїв\n\n')[1].split('\n## ')[0]
    for test_id, ac, method, title in re.findall(
            r'^\d+\. \*\*(TC-CREW-INV-ACCESS-\d+)\*\*, (AC-\d+), `(\w+)`: (.+)$', document, re.M):
        assert f'@TestCaseId("{test_id}")' in source and f'void {method}(' in source
        matrix = MATRIX_SCOPES.get(test_id)
        denied = ac == 'AC-26' or bool(matrix and not matrix['allowed'])
        expected = ('API 403; UI не надає редагування цілі; T/R=10, T/S=4, K/R=7; повний OPEN процес незмінний.'
                    if denied else 'API та UI проводять два окремі OPEN процеси без 403: T/R=6, T/S=4, K/R=7; '
                    'CLOSED, completedBy=U, точний коментар, єдина розбіжність R=-4; повторний PUT=403 без зміни процесу та залишків.')
        actions = re.findall(r'^\d+\. (.+)$', negative_steps if denied else positive_steps, re.M)
        scope_details = ''
        if matrix:
            kinds = ', '.join(matrix['headKinds']) if matrix['headKinds'] else 'усі типи (null)'
            scope_details = (f"B — батальйон, W — склад усередині B, T — {matrix['targetKind']} під W. "
                f"Облік окремо на B та W: {matrix['keeperScope']}, без фільтра типів. "
                f"Керівник на B: {matrix['headScope']}, storageKinds={kinds}. "
                'Рівно три призначення двох ролей; B і W доступні, зайвих призначень немає. ')
            actions[0] = (scope_details + 'Перечитати всі три призначення, перевірити області та точні типи; '
                f"inventory.adjust на T має бути {'true' if matrix['allowed'] else 'false'}. "
                'Перевірити відсутність дубліката після повторного надання ролі керівника. '
                'Підготувати OPEN процес адміністратором.')
        cases.append(dict(projectId=1, featureId=FEATURE, acKey=ac, testId=test_id,
            title=title[0].upper()+title[1:].rstrip('.'),
            description=f'CrewInventoryAccessUiTest#{method}; suite crew-inventory-access; '
                        'docs/CREW_INVENTORY_ACCESS_TEST_PLAN.md. API та UI від імені того самого нового користувача U. '
                        'UI відкривається прямим посиланням на залишки/редактор; перехід через дашборд не входить у цей тест.',
            preconditions='ERP dev; новий U з рівно двома ролями «Екіпажі: облік» та «Керівник локації», '
                          'без інших ролей та індивідуальних призначень. '+title+' '+scope_details+
                          'Адміністратор створив і погодив OPEN процес. R=10, S=4 на T; R=7 на окремому K. '
                          'CREW вільний, без прикріплення до точки. Прямі цілі без дітей. '
                          'Chrome; JDBC/SSH не потрібні; cleanup лише власних даних.',
            testData='T/R: 10 → 6; T/S: 4 → 4; K/R: 7 → 7. Коментар: «Перевірка прав інвентаризації». '
                     'Повний resources snapshot з актуальними version та knownResourceIds. '
                     'Перед UI позитивного сценарію новий OPEN і відновлення R=10.',
            expectedResult=expected, priority='HIGH', severity='MAJOR', status='ACTIVE',
            testType='SECURITY' if denied else 'FUNCTIONAL',
            tags='inventory,crew,fly-point,rbac,combined-roles,location-scope,automated,ui,api',
            apiAutomationIds=[], uiAutomationIds=[test_id],
            steps=[dict(stepOrder=i, actionText=action, expectedText=expected if i == len(actions) else
                        'Виконати описаний крок і перевірити наведені в ньому очікування.')
                   for i, action in enumerate(actions, 1)]))
    assert len(criteria) == 3 and len(cases) == 19
    return dict(featureId=FEATURE, documentation=document, criteria=criteria, cases=cases)


def normalized_steps(steps):
    return [dict(stepOrder=s['stepOrder'], actionText=s['actionText'], expectedText=s.get('expectedText'))
            for s in steps]


def verify_case(actual, expected):
    assert actual.get('automation') is True, expected['testId']
    for key in ('testId', 'featureId', 'title', 'status', 'priority', 'severity', 'testType',
                'description', 'preconditions', 'testData', 'expectedResult', 'tags',
                'apiAutomationIds', 'uiAutomationIds'):
        assert actual.get(key) == expected[key], (expected['testId'], key)
    assert actual['acId'] == expected['acKey']
    assert normalized_steps(actual['steps']) == normalized_steps(expected['steps'])


def sync_stage(client):
    assert client.base_url == 'http://localhost:18100'
    data = payload()
    prefix = '/api/ai/projects/1'
    feature_path = f'{prefix}/features/{FEATURE}'
    before = client.request('GET', feature_path)
    documentation = merge_documentation(before['documentation'], data['documentation'])
    changed = dict(documentation=before['documentation'] != documentation, criteria=0, cases=0)
    if changed['documentation']:
        client.request('PUT', feature_path, dict(documentation=documentation))
    acs = {a['acId']: a for a in before['acceptanceCriteria']}
    for ac in data['criteria']:
        current = acs.get(ac['acKey'])
        if current is None or current['text'] != ac['text']:
            client.request('PUT' if current else 'POST',
                f"{prefix}/acceptance-criteria/{current['id']}" if current else f'{prefix}/acceptance-criteria', ac)
            changed['criteria'] += 1
    after = client.request('GET', feature_path)
    acs = {a['acId']: a for a in after['acceptanceCriteria']}
    for case in data['cases']:
        path = f"{prefix}/test-cases/{case['testId']}"
        current = client.get_optional(path)
        needs_update = current is None
        if current is not None:
            try:
                verify_case(current, case)
            except AssertionError:
                needs_update = True
        if needs_update:
            client.request('PUT' if current else 'POST', path if current else f'{prefix}/test-cases',
                           dict(case, acceptanceCriterionId=acs[case['acKey']]['id']))
            changed['cases'] += 1
        verify_case(client.request('GET', path), case)
    after = client.request('GET', feature_path)
    assert after['documentation'] == documentation
    for ac in data['criteria']:
        assert acs[ac['acKey']]['text'] == ac['text']
    return dict(result='SUCCESS', baseUrl=client.base_url, featureDbId=after['id'],
                changes=changed,
                featureId=FEATURE, criteriaVerified=len(data['criteria']), casesVerified=len(data['cases']),
                automationLinksVerified=len(data['cases']),
                documentationSha256=hashlib.sha256(documentation.encode()).hexdigest())


def import_stage(client, results_path):
    assert client.base_url == 'http://localhost:18100'
    prefix = '/api/ai/projects/1'
    feature = client.request('GET', f'{prefix}/features/{FEATURE}')
    body = json.loads(results_path.read_text(encoding='utf-8-sig'))['tcmImport']
    assert body['environment'] == 'dev'
    allowed = {c['testId'] for c in payload()['cases']}
    expected = {r['testCaseId'] for r in body['results']}
    assert expected and expected.issubset(allowed) and len(expected) == len(body['results'])
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
        assert expected.issubset(set(suite['automationTestIds']))
        request = urllib.request.Request(f'{client.base_url}/api/autotest/runs', method='POST',
                    data=json.dumps(body, ensure_ascii=False).encode('utf-8'), headers=headers)
        with urllib.request.urlopen(request, timeout=90) as response:
            imported = json.load(response)
    assert imported['matched'] == len(expected) and not imported['unmatched'] and not imported['outOfScope']
    run = client.request('GET', f"{prefix}/test-runs/{imported['runId']}")
    assert run['environment'] == 'dev' and run['status'] == 'COMPLETED'
    assert {r['testId']: r['status'] for r in run['items']} == {r['testCaseId']: r['status'] for r in body['results']}
    return dict(baseUrl=client.base_url, importResponse=imported, run=run, result='SUCCESS')


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
        print(f"Prepared {len(payload()['criteria'])} AC and {len(payload()['cases'])} automated cases")
    if args.sync_stage or args.import_results:
        config = tomllib.loads((Path.home()/'.codex/config.toml').read_text(encoding='utf-8'))
        token = os.environ.get('TCM_AI_TOKEN') or config['mcp_servers']['tcm-ai']['env']['TCM_AI_TOKEN']
        client = TcmClient('http://localhost:18100', token)
        report = sync_stage(client) if args.sync_stage else import_stage(client, args.import_results)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        print(json.dumps({k: v for k, v in report.items() if k != 'run'}))
