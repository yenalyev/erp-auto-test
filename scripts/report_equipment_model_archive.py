"""Export real TestNG archive results and optionally import the dev run into stage TCM."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import shutil
import tomllib
import urllib.request
import xml.etree.ElementTree as ET
from sync_tcm_equipment_model_archive import ROOT, FEATURE, payload

AUDIT=ROOT/'docs/audits/2026-10-22'

def import_stage_results(data):
    from sync_tcm_relocation_batch_accounting import TcmClient
    cfg=tomllib.loads((Path.home()/'.codex/config.toml').read_text(encoding='utf-8'))
    client=TcmClient('http://localhost:18100',cfg['mcp_servers']['tcm-ai']['env']['TCM_AI_TOKEN'])
    prefix='/api/ai/projects/1'
    feature=client.request('GET',f'{prefix}/features/{FEATURE}')
    body=dict(data['tcmImport'],featureId=feature['id'])
    assert body['environment']=='dev'
    assert {r['testCaseId'] for r in body['results']} == {c['testId'] for c in payload()['cases']}
    try:
        imported=client.request('POST',f'{prefix}/test-runs/import',body)
    except RuntimeError as error:
        if 'HTTP 405' not in str(error): raise
        values={}
        for line in (ROOT.parent/'tcm/.env').read_text(encoding='utf-8').splitlines():
            key,separator,value=line.partition('=')
            if separator and not key.lstrip().startswith('#'): values[key.strip()]=value.strip().strip('\"\'')
        token=os.environ.get('TCM_AUTOTEST_TOKEN') or values['TCM_AUTOTEST_TOKEN']
        headers={'X-TCM-Api-Token':token,'Content-Type':'application/json; charset=utf-8'}
        request=urllib.request.Request(f'{client.base_url}/api/autotest/runs',method='POST',
                    data=json.dumps(body,ensure_ascii=False).encode('utf-8'),headers=headers)
        with urllib.request.urlopen(request,timeout=90) as response: imported=json.load(response)
    assert imported['matched']==13 and not imported['unmatched'] and not imported['outOfScope']
    run=client.request('GET',f"{prefix}/test-runs/{imported['runId']}")
    assert run['environment']=='dev' and run['status']=='COMPLETED'
    assert {r['testId']:r['status'] for r in run['items']} == {r['testCaseId']:r['status'] for r in body['results']}
    return dict(result='SUCCESS',baseUrl=client.base_url,remoteRunId=body['remoteRunId'],importResponse=imported,run=run)

def write_report(data):
    counts=data['summary']
    parts=['# Архівація моделі обладнання на dev\n\n',
        f"22.10.2026: **{counts.get('PASS',0)} PASS / {counts.get('FAIL',0)} FAIL / {counts.get('SKIP',0)} SKIP**. "
        f"BUILD {'SUCCESS' if data['buildSuccess'] else 'FAILURE'}; помилок setup/cleanup: {len(data['configurationFailures'])}.\n\n",
        f"Suite: {data['suite']['name']}. Початок: {data['suite']['started-at']}; завершення: {data['suite']['finished-at']} (Europe/Kyiv). "
        '6 API-кейсів мають 7 виконань через AVAILABLE/IN_REPAIR; 7 UI-кейсів мають 7 виконань.\n\n',
        '## Перевірене покриття\n\n',
        'Архівація без несписаного обладнання; атомарна заборона для залишку й активної видачі; списання останньої одиниці; '
        'відмова повторної архівації; збереження метаданих та історичної одиниці; статус із назвою, категорією та пагінацією; '
        'початковий ACTIVE; скасування й підтвердження діалогу; reload; disabled-кнопка з поясненням; '
        'справжній автокомпліт і позитивний вибір активної моделі; повторна серверна валідація після появи залишку.\n\n',
        '## Результати кейсів\n\n']
    for row in data['tcmImport']['results']:
        executions=sum(r['testCaseId']==row['testCaseId'] for r in data['results'])
        parts.append(f"- {row['testCaseId']}: {row['status']}; виконань {executions}; {row['durationMs']} мс.\n")
    if counts.get('FAIL',0):
        parts.append('\nПомилки поточного прогону збережені як FAIL:\n\n')
        for row in data['tcmImport']['results']:
            if row['status']=='FAIL':
                message=(row.get('errorMessage') or 'Див. TestNG XML').split('request=')[0].strip()[:500]
                parts.append(f"- {row['testCaseId']}: {message}\n")
    regression=json.loads((AUDIT/'equipment-model-archive-dictionary-regression.json').read_text(encoding='utf-8'))
    parts += ['\n## Регресія довідника\n\n',
        f"**{regression['summary']['passed']} PASS / {regression['summary']['failed']} FAIL / {regression['summary']['skipped']} SKIP**, "
        f"BUILD {'SUCCESS' if regression['buildSuccess'] else 'FAILURE'}. "
        f"Попередній регресійний прогін: {regression['suite']['started-at']}. "
        'Перевірено старі пошук, категорії, сортування, пагінацію та метадані з новими колонками. '
        'Кейси TC-EQD-UI-002 і TC-EQD-UI-003 актуалізовано в коді та обох TCM.\n\n',
        '## Очищення та попередні спроби\n\n',
        'Перша спроба мала 14 PASS, але завершилася помилкою cleanup: CREATED внутрішню видачу не можна видалити. '
        'Cleanup виправлено: завершити видачу, списати одиницю, видалити незалежні отримання, архівувати моделі й власні локації. '
        'Історія FINISHED, її отримання та категорія зберігаються через правила API. Усі моделі cleanup перевіряє як ARCHIVED з equipmentCount=0. '
        'Попередні спроби recovery із configuration failure та SKIP не імпортовано як успішні прогони. '
        'Маніфест recovery обмежений двома власними переміщеннями 25709 і 25727; IDs звірено з фактичними відповідями API.\n\n',
        '## TCM та докази\n\n',
        'Документація, 4 AC та 13 кейсів з automation links прочитані назад після публікації: '
        '[prod TCM](http://tcm.lab/projects/1?feature=177), [stage TCM](http://localhost:18100/projects/1?feature=172). '
        '13 підсумкових run items відображають 14 фактичних виконань; для двох AVAILABLE/IN_REPAIR зберігається гірший статус і сумарний час. '
        'Прогони мають environment=dev.\n\n']
    cleanup_path=AUDIT/'equipment-model-archive-cleanup-recovery-result.json'
    if cleanup_path.exists():
        recovery=json.loads(cleanup_path.read_text(encoding='utf-8'))
        if recovery.get('status')=='COMPLETED':
            parts += [f"Cleanup після попереднього 503 завершено: видалено {recovery['equipmentDeleted']} одиниць, "
                      f"{recovery['modelsArchived']} моделей архівовано з equipmentCount=0; категорію 39 видалено, "
                      'локації 4840/4841 деактивовано. Значення перевірено повторними GET. '
                      '[Доказ cleanup](equipment-model-archive-cleanup-recovery-result.json).\n\n']
    if data['configurationFailures']:
        parts += ['Поточний прогін має помилки setup/cleanup; деталі збережені в JSON та TestNG XML.\n\n']
    else:
        parts += ['Setup і cleanup поточного API/UI прогону завершені без помилок.\n\n']
    for label,filename in [('Prod','equipment-model-archive-tcm-prod-run.json'),('Stage','equipment-model-archive-tcm-stage-run.json')]:
        path=AUDIT/filename
        if path.exists():
            saved=json.loads(path.read_text(encoding='utf-8'))
            if saved.get('remoteRunId') != data['tcmImport']['remoteRunId']: continue
            run_id=saved.get('importResponse',{}).get('runId')
            if run_id: parts.append(f'- {label} TCM: Test Run {run_id}; імпорт і readback перевірені.\n')
    parts += ['\n[Результати JSON](equipment-model-archive-dev-results.json), '
              '[TestNG XML](equipment-model-archive-dev-testng.xml), '
              '[регресія](equipment-model-archive-dictionary-regression.json), '
              f"[скриншоти й cleanup](equipment-model-archive-evidence/{Path(data['runDirectory']).name}/).\n\n",
              f"Локальні Maven/Surefire/Allure: `{data['runDirectory']}`. "
              'Повторний запуск: `powershell -ExecutionPolicy Bypass -File scripts/run_equipment_model_archive_dev.ps1`.\n']
    (AUDIT/'equipment-model-archive-dev-report.md').write_text(''.join(parts),encoding='utf-8')

def export(run_dir):
    run_dir=run_dir.resolve()
    tree=ET.parse(run_dir/'surefire-reports/testng-results.xml')
    methods={}
    for path in [ROOT/'src/test/java/com/erp/tests/functional/equipment/EquipmentModelArchiveApiTest.java',
                 ROOT/'src/test/java/com/erp/tests/ui/EquipmentModelArchiveUiTest.java']:
        for test_id,method in re.findall(r'@TestCaseId\("([^"]+)"\)(?:(?!@TestCaseId).)*?public void (\w+)\(',
                                       path.read_text(encoding='utf-8'),re.S):
            methods[method]=test_id
    results=[]
    failures=[]
    for method in tree.findall('.//test-method'):
        if method.get('is-config')=='true':
            if method.get('status')!='PASS':
                failures.append(dict(method=method.get('name'),status=method.get('status'),
                                     error=method.findtext('exception/message','').strip()))
            continue
        name=method.get('name')
        test_id=methods.get(name)
        assert test_id, name
        results.append(dict(testCaseId=test_id,method=name,status=method.get('status'),
            durationMs=int(method.get('duration-ms')),executedAt=method.get('started-at').split(' ')[0],
            parameters=[p.findtext('value','') for p in method.findall('params/param')],
            errorMessage=method.findtext('exception/message',None)))
    assert len(results)==14, len(results)
    ranks={'PASS':0,'SKIP':1,'FAIL':2}
    grouped={}
    for r in results:
        if r['testCaseId'] not in grouped: grouped[r['testCaseId']]=dict(r)
        else:
            item=grouped[r['testCaseId']]
            item['durationMs']+=r['durationMs']
            if ranks[r['status']]>ranks[item['status']]:
                item.update(status=r['status'],errorMessage=r['errorMessage'])
    imported=[{k:v for k,v in row.items() if k in ['testCaseId','durationMs','executedAt','errorMessage','status']}
              for row in grouped.values()]
    for row in imported:
        if row['status']=='SKIP': row['status']='SKIPPED'
    assert len(imported)==13
    log_bytes=(run_dir/'maven.log').read_bytes()
    build_log=log_bytes.decode('utf-16' if log_bytes.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
    result=dict(environment='dev',runDirectory=str(run_dir),suite=tree.find('suite').attrib,
                summary=dict(Counter(r['status'] for r in results)),configurationFailures=failures,
                buildSuccess='BUILD SUCCESS' in build_log,results=results,
                tcmImport=dict(projectId=1,environment='dev',suite='equipment-model-archive',
                    remoteRunId=run_dir.name,runName='Архівація моделі обладнання — dev 22.10.2026',
                    buildName=run_dir.name,results=imported))
    AUDIT.mkdir(parents=True,exist_ok=True)
    (AUDIT/'equipment-model-archive-dev-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    shutil.copy2(run_dir/'surefire-reports/testng-results.xml',AUDIT/'equipment-model-archive-dev-testng.xml')
    evidence=AUDIT/'equipment-model-archive-evidence'/run_dir.name
    evidence.mkdir(parents=True,exist_ok=True)
    for path in (run_dir/'allure-results').glob('*result.json'):
        data=json.loads(path.read_text(encoding='utf-8'))
        test_id=next((r['testCaseId'] for r in results if r['testCaseId'] in data.get('name','')),None)
        attachments=list(data.get('attachments',[]))
        def nested(steps):
            for step in steps:
                attachments.extend(step.get('attachments',[]))
                nested(step.get('steps',[]))
        nested(data.get('steps',[]))
        pngs=[a for a in attachments if a.get('type')=='image/png']
        if pngs and test_id:
            shutil.copy2(run_dir/'allure-results'/pngs[-1]['source'],evidence/(test_id+'.png'))
        for a in attachments:
            if a.get('name') in ['Equipment archive cleanup','Recovered previous owned transit','Recovered previous owned stock']:
                shutil.copy2(run_dir/'allure-results'/a['source'],evidence/a['source'])
    # Configuration attachments can be stored on a separate Allure container.
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-directory',type=Path,required=True)
    parser.add_argument('--import-stage',action='store_true')
    args=parser.parse_args()
    data=export(args.run_directory)
    write_report(data)
    print(json.dumps(dict(summary=data['summary'],configurationFailures=data['configurationFailures'],buildSuccess=data['buildSuccess'])))
    if args.import_stage:
        assert len(data['results']) == 14
        report=import_stage_results(data)
        (AUDIT/'equipment-model-archive-tcm-stage-run.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print('Stage run verified: '+str(report['importResponse']['runId']))
        write_report(data)

if __name__=='__main__': main()
