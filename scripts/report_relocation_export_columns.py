"""Collect actual isolated dev results and durable XLSX/screenshots, including fixture failures."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from sync_tcm_relocation_export_columns import AUDIT, ROOT, payload

def local_time(ms):
    utc=datetime.fromtimestamp(ms/1000,timezone.utc)
    try: zone=ZoneInfo('Europe/Kyiv')
    except ZoneInfoNotFoundError:
        assert utc.date().isoformat() in ('2026-10-21','2026-10-22')
        zone=timezone(timedelta(hours=3))
    return utc.astimezone(zone).replace(tzinfo=None).isoformat(timespec='seconds')

def all_attachments(item):
    yield from item.get('attachments',[])
    for step in item.get('steps',[]): yield from all_attachments(step)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build',type=Path)
    parser.add_argument('--ui-build',type=Path,help='Replace UI results with an isolated, completed UI rerun; retain superseded outcomes')
    parser.add_argument('--recheck-build',type=Path,help='Latest actual rerun of API-006 and the two UI cases')
    parser.add_argument('--audit-dir',type=Path,default=AUDIT)
    parser.add_argument('--approved-group-names',action='store_true',help='Report against the exact names approved on 22 October')
    args=parser.parse_args()
    AUDIT=args.audit_dir.resolve()
    if args.ui_build: args.ui_build=args.ui_build.resolve()
    if args.recheck_build: args.recheck_build=args.recheck_build.resolve()
    build=args.build.resolve()
    tree=ET.parse(build/'surefire-reports/testng-results.xml')
    builds=[build]+([args.ui_build] if args.ui_build else [])+([args.recheck_build] if args.recheck_build else [])
    configs=[]
    for run_build in builds:
        run_tree=ET.parse(run_build/'surefire-reports/testng-results.xml')
        for cls in run_tree.iter('class'):
            for method in cls.findall('test-method'):
                if method.get('is-config')=='true' and method.get('status')=='FAIL':
                    configs.append(dict(className=cls.get('name'),method=method.get('name'),
                        message=method.findtext('exception/message'),build=str(run_build.relative_to(ROOT))))
    evidence=AUDIT/'relocation-export-columns-evidence'
    evidence.mkdir(parents=True,exist_ok=True)
    invocations={}
    superseded=[]
    seen=set()
    for file in (file for run_build in builds for file in (run_build/'allure-results').glob('*-result.json')):
        item=json.loads(file.read_text(encoding='utf-8'))
        match=re.search(r'TC-REL-COL-(?:API|UI)-\d+',item.get('name',''))
        if not match: continue
        test_id=match[0]
        source_build=file.parent.parent
        assert (source_build,test_id) not in seen, 'Use a fresh build directory for each run'
        seen.add((source_build,test_id))
        if test_id in invocations:
            assert (args.ui_build and source_build==args.ui_build and '-UI-' in test_id) or (
                args.recheck_build and source_build==args.recheck_build and test_id in (
                    'TC-REL-COL-API-006','TC-REL-COL-UI-001','TC-REL-COL-UI-002'))
            superseded.append(invocations[test_id])
        attachments=[]
        for attachment in all_attachments(item):
            if attachment.get('type') in ('image/png','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'):
                target=evidence/(test_id+'-'+attachment['source'])
                shutil.copyfile(file.parent/attachment['source'],target)
                attachments.append(str(target.relative_to(ROOT)))
        invocations[test_id]=dict(testCaseId=test_id,
            status={'passed':'PASS','failed':'FAIL','broken':'FAIL','skipped':'SKIPPED','unknown':'NOT_RUN'}[item['status']],
            durationMs=max(0,item.get('stop',0)-item['start']),executedAt=local_time(item['start']),
            errorMessage=item.get('statusDetails',{}).get('message'),attachments=attachments,
            className=item.get('fullName','').rsplit('.',1)[0],sourceBuild=str(source_build.relative_to(ROOT)))
    assert set(invocations)=={c['testId'] for c in payload()['cases']},set(invocations)
    results=[]
    for test_id in sorted(invocations):
        item=invocations[test_id]
        row={key:value for key,value in item.items() if key not in ('attachments','className','sourceBuild')}
        failures=[c for c in configs if c['className']==item['className'] or c['method']=='globalSetup']
        if row['status']=='FAIL' and re.search(r'Connection refused|ERR_CONNECTION_REFUSED|Configured database is unavailable',
                                              row.get('errorMessage') or '',re.I):
            row['status']='BLOCKED'
        if failures and row['status'] in ('SKIPPED','NOT_RUN'):
            row['status']='BLOCKED'
            row['errorMessage']='Setup/cleanup failure: '+json.dumps(failures,ensure_ascii=False)
        results.append(row)
    dates=sorted({r['executedAt'][:10] for r in results})
    period=' — '.join(dates)
    report=dict(environment='dev',featureId='REQ-REL-COL',buildDirectory=str(build.relative_to(ROOT)),
        testngTotals=tree.getroot().attrib,configurationFailures=configs,
        sourceBuilds=[str(b.relative_to(ROOT)) for b in builds],supersededInvocations=superseded,
        invocationCounts=dict(Counter(v['status'] for v in invocations.values())),
        caseCounts=dict(Counter(r['status'] for r in results)),invocations=list(invocations.values()),
        tcmImport=dict(projectId=1,remoteRunId=build.name+('-recovery20261022' if args.recheck_build else '-ui-recheck' if args.ui_build else ''),environment='dev',
            runName='Експорт даних — Переміщення: колонки — dev — '+period,
            suite='relocation-export-columns',buildName='erp-auto-test Java 21 Chrome read-only JDBC',results=results))
    pending_path=AUDIT/'relocation-export-columns-pending-cleanup.json'
    if pending_path.exists():
        pending=json.loads(pending_path.read_text(encoding='utf-8'))
        report['pendingCleanup']=dict(status=pending['status'],manifest=str(pending_path.relative_to(ROOT)),
            sourceBuild=pending['sourceBuild'],storages=len(pending['storages']),resources=len(pending['resources']))
    cleanup_path=AUDIT/'relocation-export-columns-cleanup-recovery.json'
    if cleanup_path.exists():
        recovery=json.loads(cleanup_path.read_text(encoding='utf-8'))
        assert recovery['result']=='SUCCESS'
        assert {x['storageId'] for x in recovery['storages']}=={x['id'] for x in pending['storages']}
        assert set(recovery['archivedResourceIds'])=={x['id'] for x in pending['resources']}
        assert all(x['afterRows']==0 and x['active'] is False for x in recovery['storages'])
        pending['status']='RECOVERED'
        pending['recoveryReport']=str(cleanup_path.relative_to(ROOT))
        pending_path.write_text(json.dumps(pending,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        report['pendingCleanup']['status']='RECOVERED'
        report['cleanupRecovery']=recovery
    active_configs=[c for c in configs if any(v['sourceBuild']==c['build'] and
        (v['className']==c['className'] or c['method']=='globalSetup') for v in invocations.values())
        and not (c['method']=='cleanup' and report.get('cleanupRecovery'))]
    report['activeConfigurationFailures']=active_configs
    AUDIT.mkdir(parents=True,exist_ok=True)
    (AUDIT/'relocation-export-columns-dev-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    shutil.copyfile(build/'surefire-reports/testng-results.xml',AUDIT/'relocation-export-columns-dev-testng.xml')
    if args.ui_build:
        shutil.copyfile(args.ui_build/'surefire-reports/testng-results.xml',AUDIT/'relocation-export-columns-ui-recheck-testng.xml')
    if args.recheck_build:
        shutil.copyfile(args.recheck_build/'surefire-reports/testng-results.xml',AUDIT/'relocation-export-columns-recovery-testng.xml')
    lines=['# Експорт даних — Переміщення: dev, '+period,'',
        'ERP https://dev.cukerochka.sbs; Java 21, Chrome, прямий read-only JDBC через VPN; SSH вимкнено.', '',
        'Фактичні виконання: '+json.dumps(report['invocationCounts'])+'.',
        'TCM (пропуски через setup → BLOCKED): '+json.dumps(report['caseCounts'])+'.',
        'Активних конфігураційних помилок: '+str(len(active_configs))+'. Історичних: '+str(len(configs))+'.','']
    if active_configs:
        lines+=['Помилки fixture/config збережені окремо й не змінюють уже виконаних функціональних '
            'assertions. PASS не означає успішний cleanup. Пропущені через setup кейси мають BLOCKED.','']
    elif configs:
        lines+=['Історичні помилки закрито перевіреним cleanup recovery та успішним повтором залежних '
            'кейсів. Повтор завершився без конфігураційних помилок.','']
    if args.ui_build:
        lines+=['API взято з `'+str(build.relative_to(ROOT))+'`; UI — з окремого повтору `'+
            str(args.ui_build.relative_to(ROOT))+'` після уточнення include_batches=false та UI-фільтрів Від кого/Кому для ADMIN. '
            'Попередні UI-результати збережені в JSON як supersededInvocations; це не додаткові кейси фінального набору.','']
    if args.recheck_build:
        lines+=['Після відновлення dev виконано API-006 і обидва UI-кейси в `'+str(args.recheck_build.relative_to(ROOT))+
            '`. У підсумку використано ці останні реальні результати; попередні BLOCKED/помилки збережено як supersededInvocations.','']
    for row in results:
        lines.append('- '+row['testCaseId']+': **'+row['status']+'**.'+
                     (' '+str(row['errorMessage'])[:2400] if row['errorMessage'] else ''))
    lines+=['','[Контракт і кейси](../../REQ-REL-COL.md). [JSON результатів](relocation-export-columns-dev-results.json).',
        '[TestNG](relocation-export-columns-dev-testng.xml). XLSX та скриншоти: `relocation-export-columns-evidence/`.', '',
        'Перший запуск `target/rel-columns-initial-20261021` зупинився через мережеві обмеження sandbox '
        '(DNS БД/Chrome), пропущені тести не вважаються виконаними. Поточний звіт стосується '+str(build.relative_to(ROOT))+'.', '',
        'TCM зберігає результати середовища dev в обох системах; ERP prod/stage цим запуском не перевірявся.']
    lines+=['','Проміжний запуск `target/rel-columns-dev-20261021` виявив застаріле очікування локального '
        'формату XLSX; читання виправлено під фактичний dev (аркуш Переміщення, 20 колонок). '
        'Розбіжність підписів груп ПМ/СБС залишена як FAIL за наданою вимогою.', '',
        'Попередні cleanup намагалися видалити завершені передачі; dev забороняє це через API. '
        'Власні залишки прибрано, ресурси й локації архівовано. Поточна фікстура залишає завершену '
        'історію з ID в Allure, очищує залишки й архівує власні сутності. Конфігураційні результати '
        'фінальних запусків наведені вище без приховування помилок.', '',
        '[Опис розбіжності груп](relocation-export-columns-group-label-mismatch.md).']
    if pending_path.exists() and pending['status']!='RECOVERED':
        lines+=['','## Перерваний cleanup','',
            'Після нічної паузи 22.10.2026 dev ERP та PostgreSQL відхиляють TCP-з’єднання. '
            'Залишилося перевірити й очистити 20 власних локацій і 4 ресурси. '
            '[Точний manifest за успішними POST](relocation-export-columns-pending-cleanup.json). '
            'Після відновлення доступу: `powershell -File scripts/run_relocation_export_cleanup.ps1`.', '',
            'Перевірка періоду TC-REL-COL-API-006 пройшла у попередньому `target/rel-columns-final-20261021`, '
            'але її останній повтор перервався через Connection refused та має BLOCKED. '
            'Підсумкові статуси використовують останні результати, без підміни мережевого BLOCKED попереднім PASS.']
    elif report.get('cleanupRecovery'):
        lines+=['','## Cleanup після відновлення доступу','',
            '22.10.2026 усі 20 власних локацій перевірено за точними ID й назвами: залишків немає, '
            'active=false. Всі 4 ресурси архівовано. Завершена історія лишається за правилами API. '
            '[Перевірений recovery](relocation-export-columns-cleanup-recovery.json). '
            'Нові дані повторного прогону очищені його власною фікстурою; конфігурація повтору врахована вище.']
    if args.approved_group_names:
        lines=['# Експорт даних — Переміщення: погоджені назви груп, '+period,'',
            'Погоджені 22.10.2026 точні значення: ПМ без Цукрарня, СБС без ПМ, Цукрарня, Інші. '
            'Скорочення очікувань до ПМ/СБС видалено; попередні FAIL збережені як історія.', '',
            'Повний окремий dev-прогін: Java 21, Chrome, read-only JDBC; SSH вимкнено. '
            'Джерело: `'+str(build.relative_to(ROOT))+'`.', '',
            'Результат: '+json.dumps(report['caseCounts'])+'. '
            'Конфігураційних помилок: '+str(len(active_configs))+'.','']
        for row in results:
            lines.append('- '+row['testCaseId']+': **'+row['status']+'**.'+
                (' '+str(row['errorMessage'])[:1800] if row['errorMessage'] else ''))
        lines+=['','Перевірено порядок 20 колонок, три kind, матрицю 4 групи × 3 kind, '
            'незалежність груп відправника/отримувача, повну пагінацію Видано/Отримано, '
            'відсутність зайвих позицій із двома партіями, легітимні однакові передачі, '
            'повторний експорт, період та реальний UI download.', '',
            'Cleanup фікстур очищує власні залишки та архівує ресурси й локації; '
            'завершена історія залишається за правилами ERP. Конфігураційні помилки враховано вище.', '',
            '- [Контракт і кейси](../../REQ-REL-COL.md).',
            '- [Машинні результати](relocation-export-columns-dev-results.json).',
            '- [TestNG XML](relocation-export-columns-dev-testng.xml).',
            '- XLSX і скриншоти: `relocation-export-columns-evidence/`.', '',
            'Результати позначені середовищем dev у prod та stage TCM.']
    (AUDIT/'relocation-export-columns-dev-report.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps(dict(invocations=report['invocationCounts'],cases=report['caseCounts'],configurationFailures=len(configs))))
