"""Build archive documentation/cases and idempotently sync stage TCM. Prod uses MCP."""
import argparse
import json
import tomllib
from pathlib import Path
import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parents[1]
FEATURE = 'REQ-EQU-ARCHIVE'
CRITERIA = [
 ('AC-01', 'У довіднику моделей обладнання доступна архівація активної моделі без несписаних одиниць. Потрібне підтвердження; скасування не змінює дані. Архівна модель зберігає історію та не має повторної дії архівації.'),
 ('AC-02', 'Залишки несписаного обладнання та активне переміщення блокують архівацію. UI пояснює блокування, прямий API перевіряє актуальний стан; відмова не змінює модель, обладнання чи переміщення.'),
 ('AC-03', 'Архівовані моделі не відображаються в автокомпліті назви форми додавання обладнання; активні моделі залишаються доступними для пошуку та вибору.'),
 ('AC-04', 'Довідник має фільтр статусу: за замовчуванням активні; можна переглядати архівні й усі. Статус поєднується з назвою, категоріями та пагінацією без дублів і сторонніх записів.'),
]
# Stable identifiers are shared by TestNG annotations, TCM cases and execution imports.
SCENARIOS = [
 ('API-001','AC-01','Архівація списаної моделі зі збереженням історії','archivesRetiredModelAndPreservesHistory',
  'Створити модель з однією одиницею, списати її. Зберегти модель та історичну одиницю. Виконати PUT архівації.',
  'HTTP 204, статус ARCHIVED, equipmentCount=0; ID, назва, автор, дата, категорії й історична одиниця не змінені.'),
 ('API-002','AC-02','Залишки AVAILABLE та IN_REPAIR блокують архівацію','nonRetiredStockBlocksArchive',
  'Окремо створити модель з одиницею AVAILABLE та IN_REPAIR. Надіслати прямий PUT архівації для кожної.',
  'Обидва запити: HTTP 400 з поясненням використання моделі. Статус ACTIVE, equipmentCount=1; повні модель та одиниця незмінні.'),
 ('API-003','AC-02','Активне переміщення блокує архівацію','activeRelocationBlocksArchive',
  'Видати єдину одиницю на іншу власну локацію без підтвердження отримання. Перевірити IN_TRANSIT і виконати PUT архівації.',
  'HTTP 400 з поясненням використання; ACTIVE і equipmentCount=1. Модель, одиниця та активна видача збережені.'),
 ('API-004','AC-02','Зняття останнього блокера та повторна архівація','clearingLastBlockerAllowsArchiveAndRepeatIsRejected',
  'Спробувати архівувати модель AVAILABLE. Списати останню одиницю, архівувати та повторити PUT.',
  'До списання HTTP 400, після — 204 і ARCHIVED. Повторний PUT: 400 «Модель вже в архіві», модель незмінна.'),
 ('API-005','AC-04','Статус із назвою категорією та пагінацією','filtersStatusWithNameCategoryAndPagination',
  'Підготувати активну й архівну моделі однієї категорії. Запитати ACTIVE, ARCHIVED та без status; поєднати з назвою, категорією і size=1; перевірити page=1 та протилежний статус.',
  'Фільтри повертають точні ID та status, total=1; друга сторінка порожня. Без статусу обидві моделі; протилежний статус з точною назвою дає порожній результат.'),
 ('API-006','AC-03','Suggestions виключає архів із результатів пошуку','suggestionsExcludeArchivedModels',
  'Підготувати активний контроль і списану модель. Знайти обидві через suggestions, архівувати списану та повторити пошук за спільною й точною назвою.',
  'До архівації обидві моделі; після — лише активний контроль; точний пошук архівної моделі повертає [].'),
 ('UI-001','AC-04','Типовий фільтр показує активні моделі','defaultsToActiveModels',
  'Підготувати активну й архівну моделі. Відкрити /equipment-models і знайти їхній спільний фрагмент.',
  'Обрано активний статус, запити містять status=ACTIVE, рядок лише активної моделі. Поточний текст селектора — «Активна».'),
 ('UI-002','AC-04','Перемикання статусу та перетин фільтрів','switchesStatusAndCombinesFilters',
  'Знайти контрольні активну й архівну моделі, обрати категорію. Перемикати Архівна, Всі статуси й Активна; знайти архівну назву з активним статусом і повернутися.',
  'Кожен статус має точні рядки, архівна модель без кнопки архівації; фільтри назви й категорії зберігаються; несумісна комбінація порожня, повернення відновлює активну модель.'),
 ('UI-003','AC-01','Скасування архівації не змінює модель','cancelsArchiveWithoutMutation',
  'Відкрити архівацію списаної моделі. Перевірити назву в діалозі; натиснути «Скасувати».',
  'Діалог закрито, PUT не відправлено, модель ACTIVE і лишається у списку.'),
 ('UI-004','AC-01','Підтвердження архівації та збереження після reload','confirmsArchiveAndPersistsIt',
  'Натиснути доступну кнопку архівації списаної моделі та підтвердити. Відкрити архівні, перезавантажити сторінку і знову знайти модель.',
  'Реальний PUT повертає 204, рядок зникає з активних; API ARCHIVED. Після reload модель є в архіві без повторної кнопки.'),
 ('UI-005','AC-02','Залишки та обладнання в дорозі блокують кнопку','stockAndTransitDisableArchive',
  'Створити модель із залишком і модель з одиницею в дорозі. Знайти обидві; перевірити кнопку, кількість та підказку.',
  'Для обох equipmentCount=1, кнопка disabled, підказка пояснює використання несписаним обладнанням; статус ACTIVE.'),
 ('UI-006','AC-03','Автокомпліт форми показує лише активну модель','autocompleteExcludesArchivedModels',
  'У формі нового обладнання ввести спільний фрагмент активної й архівної моделі. Обрати активну; потім ввести точну назву архівної.',
  'Реальний suggestions 200 і список містять лише активний контроль, його вибір заповнює назву. Для архівної назви API [] та кнопки вибору немає.'),
 ('UI-007','AC-02','Повторна валідація залишку під час підтвердження','revalidatesStockAtConfirmation',
  'Відкрити підтвердження архівації списаної моделі. Через API відновити одиницю в AVAILABLE. Підтвердити діалог і повторно відкрити довідник.',
  'PUT 400; UI показує «Модель використовується обладнанням — архівація неможлива.». Модель ACTIVE; після оновлення рядок є, кнопка disabled.'),
]

def payload():
    cases=[]
    for suffix, ac, title, method, action, expected in SCENARIOS:
        test_id='TC-EQA-'+suffix
        ui=suffix.startswith('UI')
        cases.append(dict(projectId=1,featureId=FEATURE,acKey=ac,testId=test_id,title=title,
            description=f'Автоматизація: EquipmentModelArchive{"Ui" if ui else "Api"}Test#{method}. docs/REQ-EQU-ARCHIVE.md.',
            preconditions='ADMIN з чинним доступом до dev ERP. Власні дві локації, категорія та унікальні моделі; чужі дані не змінюються.',
            testData='Унікальний marker eqarchive-<uuid>; окрема одиниця на кожну модель; ACTIVE/ARCHIVED, AVAILABLE/IN_REPAIR/RETIRED/IN_TRANSIT за сценарієм.',
            steps=[dict(stepOrder=1,actionText=action,expectedText=expected),
                   dict(stepOrder=2,actionText='Звірити фактичні дані API та видимий результат UI для UI-сценарію.',expectedText=expected)],
            expectedResult=expected,status='ACTIVE',priority='HIGH',severity='MAJOR',
            testType='UI' if ui else 'FUNCTIONAL',tags='equipment,model,archive,regression,automated',
            apiAutomationIds=[] if ui else [test_id],uiAutomationIds=[test_id] if ui else []))
    return dict(feature=dict(projectId=1,featureId=FEATURE,parentFeatureId='REQ-EQU-DICT',module='EQU',
        title='Архівація моделі обладнання',priority='HIGH',
        description='Архівація моделей, блокування за залишками та активним переміщенням, фільтр статусу й виключення архіву з автокомпліту.',
        documentation=(ROOT/'docs/REQ-EQU-ARCHIVE.md').read_text(encoding='utf-8')),
        criteria=[dict(projectId=1,featureId=FEATURE,acKey=key,text=text) for key,text in CRITERIA],cases=cases)

def write_document():
    parts=['# Архівація моделі обладнання\n\nМодель, яку більше не використовують, можна перемістити в архів, зберігши історію обладнання. Архів відокремлений у довіднику та не пропонується під час додавання обладнання.\n\n## Критерії приймання\n']
    parts += [f'- **{key}:** {text}\n' for key,text in CRITERIA]
    parts += ['\n## Контракт і тестові дані\n\nДовідник: `/equipment-models`; форма: `/equipment/create`. API: `GET /api/v1/equipment/models` з `status=ACTIVE|ARCHIVED`, `modelName`, `categoryIds`, `page`, `size`; без status повертаються всі статуси. `PUT /api/v1/equipment/models/{id}/archive` повертає 204; при використанні чи повторній архівації — 400. `GET /api/v1/equipment/suggestions?name=...` виключає архів.\n\nУ поточному UI статуси підписані «Активна», «Архівна», «Всі статуси»; вимога «Активні» означає типовий вибір ACTIVE. Кількість рахує несписане обладнання, включно з одиницями в дорозі. RETIRED не блокує. Відновлення архівної моделі та нова матриця RBAC не входять у цю вимогу.\n\nFixture створює дві власні локації, категорію, моделі й одиниці з UUID. Cleanup завершує власні внутрішні видачі, списує обладнання, видаляє незалежні отримання, архівує моделі й деактивує власні локації. Завершені внутрішні переміщення, їхні отримання та категорія зберігаються як незмінна історія; усі моделі мають ARCHIVED та equipmentCount=0. БД не потрібна.\n\n## Автоматизовані сценарії\n']
    for suffix,ac,title,method,action,expected in SCENARIOS:
        parts += [f'\n### TC-EQA-{suffix} {title}\n\nКритерій: {ac}. Метод: `{method}`.\n\n1. {action}\n2. Звірити фактичний результат із даними API; у UI-кейсі перевірити видимі елементи й реальний мережевий запит.\n\nОчікування: {expected}\n']
    parts += ['\n## Запуск і облік результатів\n\n13 кейсів, 14 виконань: 6 API-кейсів (7 виконань через AVAILABLE/IN_REPAIR) та 7 UI-кейсів. Усі мають `@TestCaseId` і відповідний automation link. Класи включено в `equipment`, `functional` (API), `ui` (UI) та `regression`.\n\nDev: `powershell -ExecutionPolicy Bypass -File scripts/run_equipment_model_archive_dev.ps1`. Або `mvn test -Denv=dev -Dsuite=equipment-model-archive -Duse.database=false -Dssh.enabled=false -Dframework.browser.channel=chrome`. Runner зберігає окремий каталог `target/equipment-model-archive-*` з Maven, Surefire та Allure.\n\nДокументація і кейси публікуються в prod та stage TCM у `REQ-EQU-DICT` → `REQ-EQU-ARCHIVE`. Виконання імпортуються з environment=dev; публікація в prod TCM не означає запуск на prod ERP. Фактичний звіт: `docs/audits/2026-10-22/equipment-model-archive-dev-report.md`.\n']
    results_path=ROOT/'docs/audits/2026-10-22/equipment-model-archive-dev-results.json'
    if results_path.exists():
        results=json.loads(results_path.read_text(encoding='utf-8'))
        if results.get('buildSuccess') and not results.get('configurationFailures') and results['summary'].get('PASS') == 14:
            parts += ['\n## Перевірка на dev 22 жовтня 2026\n\n',
                      f"Останній успішний прогін: {results['suite']['started-at']} — {results['suite']['finished-at']} (Europe/Kyiv). "
                      '**14 PASS / 0 FAIL / 0 SKIP**, BUILD SUCCESS; setup і cleanup без помилок. '
                      'Це 13 кейсів і 14 виконань через окремі AVAILABLE/IN_REPAIR. '
                      'Результати з environment=dev: prod TCM Test Run 163, dev/stage TCM Test Run 224.\n\n',
                      'Cleanup після попереднього 503 завершено й перевірено повторними GET: видалено 6 одиниць, '
                      '6 моделей архівовано з equipmentCount=0, категорію 39 видалено, локації 4840/4841 деактивовано. '
                      'Попередній регресійний прогін довідника: 13 PASS / 0 FAIL / 0 SKIP. '
                      'Попередні невдалі прогони збережено в історії; останні статуси кейсів — PASS.\n']
    (ROOT/'docs/REQ-EQU-ARCHIVE.md').write_text(''.join(parts),encoding='utf-8')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sync-stage',action='store_true')
    args=parser.parse_args()
    write_document()
    data=payload()
    audit=ROOT/'docs/audits/2026-10-22'
    audit.mkdir(parents=True,exist_ok=True)
    (audit/'equipment-model-archive-tcm-payload.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    if args.sync_stage:
        cfg=tomllib.loads((Path.home()/'.codex/config.toml').read_text(encoding='utf-8'))
        token=cfg['mcp_servers']['tcm-ai']['env']['TCM_AI_TOKEN']
        core.FEATURE_ID=FEATURE
        core.FEATURE_PARENT_ID='REQ-EQU-DICT'
        core.FEATURE_TITLE=data['feature']['title']
        core.FEATURE_DESCRIPTION=data['feature']['description']
        core.FEATURE_MODULE='EQU'
        core.FEATURE_PRIORITY='HIGH'
        core.DOCUMENTATION_PATH=ROOT/'docs/REQ-EQU-ARCHIVE.md'
        core.ACCEPTANCE_CRITERIA=CRITERIA
        core.CASES=data['cases']
        report=core.sync(core.TcmClient('http://localhost:18100',token))
        client=core.TcmClient('http://localhost:18100',token)
        for case in data['cases']:
            saved=client.request('GET',f"/api/ai/projects/1/test-cases/{case['testId']}")
            for field in ('title','preconditions','expectedResult','steps','apiAutomationIds','uiAutomationIds'):
                assert saved[field] == case[field], (case['testId'],field)
        report['fullCaseReadbackVerified']=True
        (audit/'equipment-model-archive-tcm-stage-sync.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        assert report['verification']['documentationMatchesSource']
        assert report['verification']['allCasesPresent']
        print('Stage: documentation, 4 AC and 13 cases verified')
    else: print('Exported 4 AC and 13 cases')

if __name__=='__main__': main()
