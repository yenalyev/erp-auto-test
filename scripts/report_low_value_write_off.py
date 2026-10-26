"""Preserve every parameterized execution and aggregate actual results for TCM."""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
from sync_tcm_low_value_write_off import payload

def build(run, output):
    expected = {c['testId'] for c in payload()['cases']}
    tree = ET.parse(run/'surefire-reports/testng-results.xml').getroot()
    failures = []
    for m in tree.iter('test-method'):
        if m.get('is-config')=='true' and m.get('status')=='FAIL':
            msg=m.find('exception/message')
            failures.append(dict(method=m.get('name'),error=''.join(msg.itertext()).strip() if msg is not None else ''))
    executions=[]; grouped=defaultdict(list); screenshots=[]
    output.mkdir(parents=True,exist_ok=True)
    evidence=output/'low-value-write-off-evidence'; evidence.mkdir(exist_ok=True)
    shutil.copy2(run/'surefire-reports/testng-results.xml',evidence/'testng-results.xml')
    # 26 October 2026 Europe/Kyiv: standard time UTC+02:00. Store explicit offset.
    zone=timezone(timedelta(hours=2))
    for path in sorted((run/'allure-results').glob('*-result.json')):
        value=json.loads(path.read_text(encoding='utf-8'))
        ids={x['value'] for x in value.get('labels',[]) if x['name']=='testCaseId'} & expected
        if not ids: continue
        assert len(ids)==1
        test_id=ids.pop()
        status={'passed':'PASS','failed':'FAIL','broken':'FAIL','skipped':'BLOCKED' if failures else 'SKIPPED'}[value['status']]
        row=dict(testCaseId=test_id,status=status,durationMs=value['stop']-value['start'],
          executedAt=datetime.fromtimestamp(value['start']/1000,zone).replace(tzinfo=None).isoformat(),
          parameters=value.get('parameters',[]),errorMessage=value.get('statusDetails',{}).get('message') if status!='PASS' else None)
        executions.append(row); grouped[test_id].append(row)
        attachments=list(value.get('attachments',[]))
        def collect(steps):
            for step in steps:
                attachments.extend(step.get('attachments',[])); collect(step.get('steps',[]))
        collect(value.get('steps',[]))
        for i,a in enumerate(x for x in attachments if x.get('type')=='image/png'):
            target=evidence/f'{test_id}-{value["uuid"][:8]}-{i}.png'
            shutil.copy2(run/'allure-results'/a['source'],target)
            screenshots.append(dict(testId=test_id,path=str(target)))
    assert set(grouped)==expected, f'Missing {expected-set(grouped)}'
    results=[]
    for test_id,rows in sorted(grouped.items()):
        status=next((s for s in ['FAIL','BLOCKED','SKIPPED'] if any(r['status']==s for r in rows)),'PASS')
        errors=['parameters='+json.dumps(r['parameters'],ensure_ascii=False)+'\n'+(r['errorMessage'] or '') for r in rows if r['status']!='PASS']
        results.append(dict(testCaseId=test_id,status=status,durationMs=sum(r['durationMs'] for r in rows),
          executedAt=min(r['executedAt'] for r in rows),errorMessage='\n\n'.join(errors) if errors else None))
    report=dict(environment='dev',timezone='Europe/Kyiv',utcOffset='+02:00',runDirectory=str(run),
      executionCounts=dict(Counter(r['status'] for r in executions)),caseCounts=dict(Counter(r['status'] for r in results)),
      configurationFailures=failures,executions=executions,screenshots=screenshots,
      tcmImport=dict(projectId=1,remoteRunId=run.name,environment='dev',suite='low-value-write-off',
        runName='Списання ресурсів МШП / dev',results=results))
    (output/'low-value-write-off-dev-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    counts=report['executionCounts']; cases=report['caseCounts']
    lines=['# Списання ресурсів МШП — dev, 26.10.2026','',
      f"Виконання: {counts.get('PASS',0)} PASS / {counts.get('FAIL',0)} FAIL / {counts.get('BLOCKED',0)} BLOCKED / {counts.get('SKIPPED',0)} SKIP.",
      f"Кейси TCM: {cases.get('PASS',0)} PASS / {cases.get('FAIL',0)} FAIL / {cases.get('BLOCKED',0)} BLOCKED. Configuration/cleanup failures: {len(failures)}.",'',
      'Команда: `powershell -ExecutionPolicy Bypass -File scripts/run_low_value_write_off_dev.ps1`.',
      f'Каталог виконання: `{run.as_posix()}`. Час Europe/Kyiv (UTC+02:00).','',
      '## Фактичні результати','']
    lines[8:8]=['Погоджений контракт від 26.10.2026:', '',
      '- Повна підказка МШП у редагуванні ресурсу необов’язкова; UI-002 перевіряє пояснення на формі списання.',
      '- Select замість чекбокса у словнику прийнято як штатну поведінку; UI-004 перевіряє цей елемент, UI-003 — фільтрацію та скидання.',
      '- Попередні прогони 168/229 перевіряли попередню редакцію очікувань. Їхні фактичні результати збережені в історії; поточний звіт містить нове виконання.', '']
    if not failures:
        lines[8:8]=['Cleanup пройшов: власні списання й отримання видалені, нульові рядки прибрані, локації/ресурси архівовані, актори деактивовані.', '']
    for r in results:
        lines.append(f"- {r['testCaseId']}: **{r['status']}**, виконань {len(grouped[r['testCaseId']])}.")
        if r['errorMessage']: lines.extend(['','```text',r['errorMessage'],'```',''])
    for failure in failures: lines.extend(['','## Помилка конфігурації/очищення','',failure['method'],'','```text',failure['error'],'```'])
    lines += ['', '## Артефакти', '', '- [Вимоги та 19 кейсів](../../REQ-MSHP-resource-write-off.md).',
      '- [Усі виконання та TCM payload](low-value-write-off-dev-results.json).',
      '- [TestNG](low-value-write-off-evidence/testng-results.xml).',
      '- Скріншоти — `low-value-write-off-evidence/`; HTTP attachments — Allure каталогу виконання.',
      '- [TCM prod](http://tcm.lab/projects/1?feature=180), [TCM stage](http://localhost:18100/projects/1?feature=175).','']
    for destination,base in [('prod','http://tcm.lab'),('stage','http://localhost:18100')]:
        saved=output/f'low-value-write-off-tcm-{destination}-run.json'
        if saved.exists():
            value=json.loads(saved.read_text(encoding='utf-8'))
            lines.append(f"- [Прогін dev у TCM {destination}: {value['run']['id']}]({base}/test-runs/{value['run']['id']}).")
    (output/'low-value-write-off-dev-report.md').write_text('\n'.join(lines),encoding='utf-8')
    return dict(executionCounts=counts,caseCounts=cases,configurationFailures=len(failures),screenshots=len(screenshots))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--run',type=Path,required=True); p.add_argument('--output',type=Path,required=True)
    args=p.parse_args(); print(json.dumps(build(args.run,args.output)))
