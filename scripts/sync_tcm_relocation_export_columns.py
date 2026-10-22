"""Idempotent REQ-REL-COL documentation/AC/cases sync and verified dev result import."""
import argparse
import json
import os
import re
import tomllib
import urllib.request
from pathlib import Path

import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parents[1]
AUDIT = ROOT / 'docs/audits/2026-10-21'
core.FEATURE_ID = 'REQ-REL-COL'
core.FEATURE_PARENT_ID = 'REQ-EDIT_REL'
core.FEATURE_TITLE = 'Експорт даних — Переміщення: порядок колонок, групи та kind'
core.FEATURE_DESCRIPTION = 'Група отримувача після Отримувач, новий Тип отримувача (kind), Група відправника після Видав; регресія дублів із повною звіркою Видано/Отримано.'
core.FEATURE_MODULE = 'REL'
core.FEATURE_PRIORITY = 'HIGH'
core.DOCUMENTATION_PATH = ROOT / 'docs/REQ-REL-COL.md'
document = core.DOCUMENTATION_PATH.read_text(encoding='utf-8')
core.ACCEPTANCE_CRITERIA = re.findall(r'^- \*\*(AC-\d+):\*\* (.+)$', document, re.M)
core.CASES = []
for test_id, title, ac, body in re.findall(
        r'^### (TC-[A-Z0-9-]+) — (.+?) \[(AC-\d+)\]\n(.*?)(?=^### |^## |\Z)', document, re.M | re.S):
    expected = re.search(r'^Очікування: (.+)$', body, re.M).group(1)
    actions = re.findall(r'^\d+\. (.+)$', body, re.M)
    ui = '-UI-' in test_id
    core.CASES.append(dict(testId=test_id, featureId=core.FEATURE_ID, acKey=ac, title=title,
        description=body.strip(), expectedResult=expected,
        preconditions='ERP dev; ADMIN; Java 21; Chrome для UI; read-only JDBC для груп. Власні локації й ресурси RelocationExportColumnsFixture.',
        testData='Базові 4 переміщення/8 позицій; матриця 4 групи × 3 kind, 12 переміщень/24 позиції. Кількості 3.75 (партії 1.25+2.5) та 4.75; дві однакові законні передачі.',
        steps=[dict(stepOrder=i, actionText=action, expectedText=expected if i==len(actions) else '')
               for i, action in enumerate(actions, 1)],
        priority='CRITICAL' if test_id.endswith('005') else 'HIGH', severity='MAJOR',
        status='ACTIVE', testType='UI' if ui else 'FUNCTIONAL',
        tags='export-analytics,relocation,excel,columns,duplicates,automated,dev',
        apiAutomationIds=[] if ui else [test_id], uiAutomationIds=[test_id] if ui else []))
assert len(core.ACCEPTANCE_CRITERIA) == 6 and len(core.CASES) == 8

def payload():
    return dict(feature=dict(featureId=core.FEATURE_ID, parentFeatureId=core.FEATURE_PARENT_ID,
        title=core.FEATURE_TITLE, description=core.FEATURE_DESCRIPTION, documentation=document,
        module='REL', priority='HIGH'),
        criteria=[dict(acKey=key, text=text) for key,text in core.ACCEPTANCE_CRITERIA], cases=core.CASES)

def verify(client):
    prefix = '/api/ai/projects/1'
    feature = client.request('GET', prefix+'/features/'+core.FEATURE_ID)
    assert feature['documentation'] == document
    acs = {a['acId']:a for a in feature['acceptanceCriteria']}
    for key,text in core.ACCEPTANCE_CRITERIA: assert acs[key]['text'] == text
    for case in core.CASES:
        actual = client.request('GET', prefix+'/test-cases/'+case['testId'])
        for key in ('title','description','expectedResult','preconditions','testData','apiAutomationIds','uiAutomationIds','status','testType'):
            assert actual.get(key)==case[key], (case['testId'],key)
        assert actual['featureId']==core.FEATURE_ID and actual['acId']==case['acKey']
        assert [(s['actionText'],s.get('expectedText') or '') for s in actual['steps']] == [
            (s['actionText'],s.get('expectedText') or '') for s in case['steps']]
    return feature

def import_run(client, path):
    feature = verify(client)
    body = json.loads(path.read_text(encoding='utf-8'))['tcmImport']
    body = dict(body, featureId=feature['id'])
    assert body['environment']=='dev'
    assert {r['testCaseId'] for r in body['results']}=={c['testId'] for c in core.CASES}
    try:
        imported=client.request('POST','/api/ai/projects/1/test-runs/import',body)
    except RuntimeError as error:
        if 'HTTP 405' not in str(error) or client.base_url!='http://localhost:18100': raise
        values={}
        for line in (ROOT.parent/'tcm/.env').read_text(encoding='utf-8').splitlines():
            key,sep,value=line.partition('=')
            if sep and not key.lstrip().startswith('#'): values[key.strip()]=value.strip().strip('"\'')
        token=os.environ.get('TCM_AUTOTEST_TOKEN') or values['TCM_AUTOTEST_TOKEN']
        headers={'X-TCM-Api-Token':token,'Content-Type':'application/json; charset=utf-8'}
        # Commit the feature's auto-plan before import's REQUIRES_NEW run transaction reads it.
        suite_request=urllib.request.Request(client.base_url+f"/api/autotest/suites/features/{feature['id']}",headers=headers)
        with urllib.request.urlopen(suite_request,timeout=60) as response: suite=json.load(response)
        assert set(suite['automationTestIds'])=={c['testId'] for c in core.CASES}
        req=urllib.request.Request(client.base_url+'/api/autotest/runs',method='POST',
            data=json.dumps(body,ensure_ascii=False).encode(),
            headers=headers)
        with urllib.request.urlopen(req,timeout=60) as response: imported=json.load(response)
    assert imported['matched']==8 and not imported['unmatched'] and not imported['outOfScope']
    run=client.request('GET',f"/api/ai/projects/1/test-runs/{imported['runId']}")
    assert run['environment']=='dev' and run['status']=='COMPLETED'
    assert {r['testId']:r['status'] for r in run['items']}=={r['testCaseId']:r['status'] for r in body['results']}
    return dict(importResponse=imported,run=run)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sync-stage',action='store_true')
    parser.add_argument('--verify-prod',action='store_true')
    parser.add_argument('--docs-stage',action='store_true')
    parser.add_argument('--import-stage',type=Path)
    parser.add_argument('--audit-dir',type=Path,default=AUDIT)
    args=parser.parse_args()
    AUDIT=args.audit_dir.resolve()
    AUDIT.mkdir(parents=True,exist_ok=True)
    (AUDIT/'relocation-export-columns-tcm-payload.json').write_text(json.dumps(payload(),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Prepared 6 AC / 8 automated cases')
    if args.sync_stage or args.import_stage or args.verify_prod or args.docs_stage:
        config=tomllib.loads((Path.home()/'.codex/config.toml').read_text(encoding='utf-8'))
        env=config['mcp_servers']['tcm-ai-prod' if args.verify_prod else 'tcm-ai']['env']
        client=core.TcmClient('http://tcm.lab' if args.verify_prod else 'http://localhost:18100',env['TCM_AI_TOKEN'])
        if args.docs_stage:
            client.request('PUT','/api/ai/projects/1/features/'+core.FEATURE_ID,dict(documentation=document))
            feature=verify(client)
            report=dict(result='SUCCESS',featureDbId=feature['id'],casesVerified=8,criteriaVerified=6)
            suffix='stage-documentation'
        elif args.import_stage:
            report=import_run(client,args.import_stage)
            suffix='stage-run'
        elif args.verify_prod:
            feature=verify(client)
            report=dict(result='SUCCESS',featureDbId=feature['id'],casesVerified=8,criteriaVerified=6)
            suffix='prod-readback'
        else:
            report=core.sync(client)
            feature=verify(client)
            report.update(result='SUCCESS',featureDbId=feature['id'],casesVerified=8,criteriaVerified=6)
            suffix='stage-sync'
        (AUDIT/f'relocation-export-columns-tcm-{suffix}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps({k:v for k,v in report.items() if k not in ('run','verification')},ensure_ascii=True))
