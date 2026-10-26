"""МШП: repeatable documentation/payload generation and verified stage TCM publication.

Production publication uses tcm-ai-prod MCP. Never put API tokens in artifacts.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import tomllib
import urllib.request
from sync_tcm_relocation_batch_accounting import TcmClient
from sync_tcm_commercial_document_required_fields import verify_case

ROOT = Path(__file__).resolve().parents[1]
FEATURE = 'REQ-MSHP'
DOC = ROOT / 'docs/REQ-MSHP-resource-write-off.md'
EDIT_CASE_SUFFIXES = {'API-010', 'UI-011', 'UI-012'}

def case_class(suffix):
    return 'LowValueWriteOffEditTest' if suffix in EDIT_CASE_SUFFIXES else 'LowValueWriteOffTest'
CRITERIA = [
 ('AC-01', 'У редагуванні ресурсу ознака МШП задається через select «Облікова категорія» і зберігається. За погодженням від 26.10.2026 повна підказка в цій формі необов’язкова; пояснення МШП доступне на формі списання.'),
 ('AC-02', 'Словник ресурсів має select способу обліку з опцією LOW_VALUE_ITEM для МШП. Вибір показує лише МШП, скидання повертає повний список з урахуванням пошуку. Select замість чекбокса погоджено 26.10.2026.'),
 ('AC-03', 'Кнопка списання на залишках відкриває форму вибору ресурсів як при видачі. Доступні лише МШП з позитивним залишком обраної локації; без дублів і ресурсів інших локацій.'),
 ('AC-04', 'Валідація як у видачі: хоча б один ресурс, додатна кількість, не більше доступного залишку/партії. Неуспішний запит атомарний; успішний точно зменшує залишки всіх ресурсів.'),
 ('AC-05', 'Успішне списання додається в Історію операцій з типом LOW_VALUE_WRITE_OFF, ресурсом, кількістю, локацією та коментарем, і має окремий підсумок.'),
 ('AC-06', 'Сайдбар веде в історію списань. Адміністратор і Бухгалтер бачать журнал; користувач без права не отримує доступ через UI або прямий API. Показано локацію, дату, користувача та ресурси.'),
 ('AC-07', 'Журнал підтримує пошук за назвою ресурсу, multiselect локацій (OR), період, комбінацію й скидання фільтрів; пагінація не губить і не дублює записи.'),
 ('AC-08', 'Регресія чинного контракту: явна партія списується точно; редагування поточного дня замінює дельту; скасування повертає залишки; чужа локація не відкриває й не видаляє запис. Дата створення лише поточна.'),
]
# ID, AC, method, title, actions, expected. IDs remain identical in prod and stage.
CASES = [
 ('API-001','AC-01','categoryPersistsAndDictionaryFilters','Збереження ознаки МШП та API-фільтр',
  ['Змінити власний ресурс REGULAR → LOW_VALUE_ITEM через PUT і повторно GET.', 'Запитати словник за marker + assetCategory, потім зняти ознаку й повторити запит.'],
  'GET зберігає категорію; точні ID списку змінюються згідно ознаки, звичайний ресурс виключений.'),
 ('API-002','AC-03','availableResourcesAreEligibleAndLocal','Доступні лише МШП із залишків вибраної локації',
  ['GET storage-item з assetCategory=LOW_VALUE_ITEM для A.', 'Повторити для B; порівняти точні resourceId та кількості.'],
  'A містить лише gloves/cloth із позитивними залишками, B лише gloves; regular і no-stock відсутні.'),
 ('API-003','AC-04','multiResourceWriteOffDebitsExactAmounts','Списання кількох ресурсів і дробових кількостей',
  ['Запам’ятати залишки; POST 2.5 gloves та 1.25 cloth з авторами/коментарем.', 'GET деталі; перевірити точні дельти; DELETE власного списання.'],
  'HTTP 200, дві позиції, поточна дата, WRITE_OFF, автори й коментар збережені; точне списання та відновлення залишків.'),
 ('API-004','AC-04','invalidWriteOffIsAtomic','Матриця валідації та атомарність списання',
  ['Для кожного варіанта зберегти залишки, партії та ID журналу.', 'POST: порожньо, 0, -1, без amount, перевищення, regular, no-stock, дубль, чужа UUID партії, external, вчора, завтра, 10001 символ коментаря.', 'Порівняти стан після відмови з baseline.'],
  'Кожен запит має 400/409 та непорожні errors; жоден рядок не змінює залишки/партії та не створює операцію.'),
 ('API-005','AC-08','explicitBatchWriteOffAndCancellation','Списання конкретної партії та повернення',
  ['Зберегти всі партії gloves; POST 1.5 із конкретної batchId.', 'Перевірити кожну партію; DELETE списання та повторити GET.'],
  'Лише обрана партія зменшується на 1.5; після скасування всі партії дорівнюють baseline.'),
 ('API-006','AC-05','writeOffAppearsInOperationHistory','Списання в Історії операцій',
  ['POST 3 cloth на A; GET resource-operation-history з фільтром cloth та поточним днем.', 'Знайти LOW_VALUE_WRITE_OFF, перевірити ресурс, системний опис з id списання та окремий підсумок; скасувати.'],
  'Один рядок LOW_VALUE_WRITE_OFF із кількістю 3, потрібний ресурс та опис «Списання малоцінних ресурсів #id»; totalByOperations.lowValueWriteOff=3.'),
 ('API-007','AC-07','journalFiltersAndPagination','Пошук, локації, період та пагінація журналу',
  ['Шукати marker, cloth і gloves; вибрати A, потім A+B; поєднати з поточним днем.', 'Задати toDate=вчора; пройти два API page з size=1.'],
  'Точні ID A/B, OR двох локацій, включені межі сьогодні, порожньо до вчора, дві сторінки без втрат/дублів.'),
 ('API-008','AC-06','journalRbac','RBAC журналу та прямого створення',
  ['Автентифікувати нового актора з роллю Бухгалтер; GET журнал і деталі A.', 'Актором без write-off.read зробити GET журнал, GET деталі й POST списання.'],
  'Бухгалтер читає з HTTP 200; користувач без права отримує 403; залишок не змінюється.'),
 ('API-009','AC-08','sameDayEditAndLocationIsolation','Редагування дня створення й ізоляція локацій',
  ['POST 2 cloth; PUT 4 cloth; PUT понад залишок.', 'GET та DELETE з storageId=B для запису A; видалити з правильною A.'],
  'PUT замінює дельту (15→11), невалідний PUT атомарний; неправильна локація дає 404; видалення відновлює 15.'),
 ('UI-001','AC-01','resourceCategoryCanBeEdited','Редагування МШП у справжній формі ресурсу',
  ['Адміністратором відкрити редагування власного no-stock.', 'Вибрати REGULAR, зберегти та перевірити GET; повторити LOW_VALUE_ITEM.'],
  'Обидва значення успішно збережені через UI і підтверджені GET після переходу.'),
 ('UI-002','AC-01','writeOffFormExplainsMshp','Погоджене пояснення МШП на формі списання',
  ['Відкрити редагування ресурсу й перевірити select облікової категорії.', 'Відкрити форму списання та перевірити пояснення МШП і використання у виробництві.'],
  'Select категорії видимий; форма списання пояснює МШП. Повна підказка у редагуванні ресурсу необов’язкова за погодженням від 26.10.2026.'),
 ('UI-003','AC-02','dictionaryFilterWorksAndResets','Працездатність чинного UI-фільтра МШП',
  ['Шукати marker у словнику; вибрати LOW_VALUE_ITEM у поточному select.', 'Порівняти назви; скинути спосіб обліку.'],
  'Реальний GET містить assetCategory=LOW_VALUE_ITEM; лише 3 власні МШП; скидання повертає всі 4.'),
 ('UI-004','AC-02','dictionaryUsesAcceptedMshpSelect','Погоджений select МШП у словнику ресурсів',
  ['Відкрити словник і select способу обліку; перевірити опції ALL та LOW_VALUE_ITEM.', 'Вибрати LOW_VALUE_ITEM; перевірити запит і відображення вибраного підпису.'],
  'Видимий combobox з обома опціями; вибір МШП надсилає assetCategory=LOW_VALUE_ITEM і відображає вибраний підпис. Select погоджено замість чекбокса 26.10.2026.'),
 ('UI-005','AC-04','inventoryButtonAndFormValidation','Кнопка залишків, валідація кількості та скасування',
  ['Натиснути реальну кнопку списання на /inventory; перевірити disabled порожньої форми.', 'Вибрати cloth; перевірити 0, -1, перевищення, потім 1.5; Скасувати.'],
  'Форма відкривається; невалідні кількості блокують submit, 1.5 дозволена; скасування повертає на залишки без змін.'),
 ('UI-006','AC-03','formOffersOnlyEligibleResources','Вибір ресурсів: виключення непридатних та дублів',
  ['Відкрити форму; шукати regular та no-stock у dropdown.', 'Вибрати cloth, додати другу позицію й шукати cloth повторно.'],
  'Звичайного, відсутнього на залишках і вже вибраного ресурсу немає серед опцій.'),
 ('UI-007','AC-04','createWriteOffThroughUi','Наскрізне створення списання через UI',
  ['Вибрати cloth, 1.5 і коментар; Підтвердити; зберегти id реального POST.', 'Перевірити залишок та рядок історії; скасувати власну операцію через cleanup API.'],
  'POST 200, залишок 13.5, журнал показує ресурс/кількість/коментар; cleanup відновлює 15.'),
 ('UI-008','AC-06','journalSidebarColumnsAndSearch','Сайдбар та журнал для Адміністратора й Бухгалтера',
  ['Окремо обома ролями відкрити журнал через реальний sidebar link.', 'Перевірити локацію, дату, автора та ресурс; пошук cloth приховує gloves, скидання повертає рядок.'],
  'Обидві ролі бачать журнал і потрібні колонки/дані; пошук і reset працюють; Бухгалтер не має кнопки створення.'),
 ('UI-009','AC-06','unauthorizedUserCannotOpenWriteOffPages','Заборона сайдбара та прямих маршрутів без права',
  ['Увійти актором location.read + inventory.read + resource.read без права списань.', 'Перевірити відсутній sidebar link; відкрити history і form за прямими URL.'],
  'Посилання приховано; прямі маршрути показують 403, рядки журналу і submit відсутні.'),
 ('UI-010','AC-07','journalLocationAndPeriodFilters','UI multiselect локацій та період у комбінації з пошуком',
  ['У просторі Всі локації знайти gloves; через multiselect вибрати A, потім додати B.', 'Вибрати період 1 день, перевірити запит від/до сьогодні; скинути фільтри.'],
  'A приховує B; A+B повертає обидва рядки; реальний GET має сьогоднішні межі, після reset обидва записи видимі.'),
 ('API-010','AC-08','fifoApiAcceptsValidEdit','API-контроль редагування FIFO: 6 із 10',
  ['На власній локації з 10 одиницями МШП створити FIFO-списання 6; перевірити залишок 4.', 'Окремо PUT кількість 4, 6 та 10; перевірити збереження й залишок; скасувати власне списання.'],
  'Кожен валідний PUT має HTTP 200; залишок дорівнює 10 мінус нова кількість; видалення відновлює 10.'),
 ('UI-011','AC-08','fifoUiAcceptsValidEdit','Регресія: FIFO-редагування не блокує кількість у доступних межах',
  ['Створити FIFO-списання 6 із 10 на власній локації; відкрити його редагування.', 'Перевірити доступно 10, очистити та повторно ввести 4, 6 або 10 через звичайне поле кількості.', 'Перевірити доступність Підтвердити, реальний PUT, збережену кількість і залишок.'],
  'Валідна кількість не позначається перевищенням; Підтвердити активна; PUT 200; залишок 10 мінус нова кількість. Без очищення прихованих batches через JS або API-обходу форми.'),
 ('UI-012','AC-08','batchTrackedUiAcceptsValidEdit','UI-контроль редагування списання з явним вибором партії',
  ['Для власного МШП увімкнути ручний вибір партії; створити списання 6 із партії на 10.', 'Відкрити редагування, змінити кількість партії на 4 й підтвердити.', 'Перевірити PUT і залишок, скасувати списання та вимкнути власне налаштування.'],
  'Партія доступна з урахуванням власних 6; кількість 4 приймається UI та PUT 200; залишок 6, після скасування 10.'),
]

def build_doc():
    lines = ['# Списання ресурсів — МШП', '', 'Вимога `REQ-MSHP`. Дата специфікації: 26.10.2026. Проєкт TCM: 1.', '',
      '## Бізнес-поведінка', '',
      'МШП — малоцінні та швидкозношувані предмети, які використовуються у виробництві й підлягають списанню. Ознака ресурсу зберігається незалежно від кількості та способу обліку партій. Списання зменшує фізичний залишок; це окрема операція в загальній історії та журналі списань.', '',
      'Користувач обирає локацію → Залишки → Списання → ресурси та кількості → Підтвердити. Скасування форми не змінює даних. В журналі доступні пошук назви ресурсу, декілька локацій та період.', '',
      '## Погоджена поведінка від 26.10.2026', '',
      'Користувач прийняв поточну реалізацію: повна підказка МШП у редагуванні ресурсу необов’язкова; select у словнику замість чекбокса є штатним елементом. Обидва пункти входять до погодженої документації й не вважаються дефектами. AC-01/AC-02 та UI-002/UI-004 оновлені зі збереженням ID. Попередні результати виконання збережені як історія; статуси нового контракту визначає окремий повторний прогін.', '',
      '## Критерії приймання', '']
    lines += [f'- **{key}:** {text}' for key,text in CRITERIA]
    lines += ['', '## Погоджений API/UI контракт', '',
      '- Ресурс: `assetCategory=REGULAR|LOW_VALUE_ITEM`, PUT/GET `/api/v1/resources/{id}`. МШП не дорівнює налаштуванню ручного вибору партій.',
      '- Облікова категорія ресурсу та фільтр словника реалізовані select-елементами «Облікова категорія» та «Усі способи обліку». Поточний підпис LOW_VALUE_ITEM — «МПШ (малоцінний ресурс)». UI-004 перевіряє погоджений select, UI-003 — результати фільтрації та скидання.',
      '- Повна підказка у редагуванні ресурсу необов’язкова. Пояснення на формі списання описує МШП і використання у виробництві; його перевіряє UI-002.',
      '- Кнопка залишків має текст «Списати МШП»; сайдбар/журнал — «Списання МШП», колонка автора — «Створив» замість «Користувач». Перевіряються бізнес-функція та значення автора; ці відмінності назв явно зафіксовані.',
      '- POST `/api/v1/storages/inventory/low-value-resources/write-off`: storageId, date, actors, comment, assets[{resourceId,batchId?,amount}]. GET списку за базовим шляхом: parentStorageId, storageIds, resourceName, fromDate, toDate, page, size, sort.',
      '- GET/PUT/DELETE `/api/v1/storages/inventory/low-value-resources/{id}`; GET/DELETE потребують storageId. Вихідна реалізація дозволяє зміну/видалення тільки в день створення, створення лише сьогодні. Тести не змінюють серверний годинник чи БД.',
      '- Вибір ресурсів: GET `/api/v1/storages/{id}/storage-item?assetCategory=LOW_VALUE_ITEM`. Історія операцій: `/api/v1/statistics/resource-operation-history`.',
      '- Історія операцій містить системний опис «Списання малоцінних ресурсів #id»; користувацький коментар зберігається у деталях і журналі списань. API-пагінація журналу використовує дозволене сортування storageName,ASC; непідтримуване id не передається.',
      '- Бухгалтер має право читання списань, створення/зміна/видалення залежать від окремих permissions. Вимога надає Бухгалтеру доступ до журналу; вона не заявляє право створення.', '',
      '## Автоматизація та дані', '',
      'Набір `low-value-write-off`: класи `LowValueWriteOffTest` та `LowValueWriteOffEditTest`; 10 API + 12 UI кейсів, 39 виконань. Початковий набір має 32 виконання; регресія редагування додає 7 (3 API, 3 FIFO UI, 1 попартійний UI). Усі ID прив’язані до автоматизації в TCM.', '',
      'Окремий запуск регресії редагування: `powershell -ExecutionPolicy Bypass -File scripts/run_low_value_write_off_dev.ps1 -Suite low-value-write-off-edit`. Підготовка: 10 МШП, списання 6, залишок 4; редагування повинне дозволяти 4/6/10 з урахуванням власного списання. Розбір ручного повідомлення — [BUG-MSHP-EDIT-FIFO](BUG-MSHP-EDIT-FIFO.md).', '',
      '26.10.2026 підтверджено BUG-MSHP-EDIT-FIFO на dev: 4/6/10 приймаються API, але FIFO UI блокує підтвердження; попартійний UI-контроль проходить. Окрема регресія: 4 PASS / 3 FAIL, помилок підготовки/очищення немає. Раніше UI-редагування FIFO не входило до початкових 32 виконань.', '',
      'Власні незалежні внутрішні A/B і external; gloves=МШП (дві партії), cloth=МШП, regular, no-stock=МШП без залишку. A: 30 gloves, 15 cloth, 10 regular; B: 20 gloves. Два базові списання: 1 gloves на A, 2 на B. Нові актори Бухгалтер та читач залишків без прав списань. Адміністратор використовує налаштовану сесію.', '',
      'Кожен успішний POST відразу реєструється, включно з помилково прийнятими негативними запитами. Manifest `low-value-write-off-owned.json` зберігається протягом підготовки й виконання. Cleanup видаляє тільки власні списання/отримання, перевіряє нульові залишки й видаляє залишені нульові рядки інвентаризацією з явними removed=true та version, архівує локації/ресурси й деактивує акторів. Точний GET inventory?resourceId знаходить навіть приховані нульові рядки. JDBC/SSH та мережеві mock не використовуються.', '',
      'Запуск: `powershell -ExecutionPolicy Bypass -File scripts/run_low_value_write_off_dev.ps1`.',
      'Прогін лише на dev; `environment=dev` зберігається і при імпорті результатів у prod/stage TCM. Prod/stage TCM — сховища тестів, це не прогін ERP на prod/stage.', '',
      '## Тест-кейси', '']
    for suffix,ac,method,title,actions,expected in CASES:
        lines += [f'### TC-MSHP-{suffix} — {title} [{ac}]', '', f'Автотест: `{case_class(suffix)}#{method}`.', '']
        lines += [f'{i}. {action}' for i,action in enumerate(actions,1)]
        lines += ['', f'Очікування: {expected}', '']
    lines += ['## Межі покриття', '',
      'Обмеження редагування списання минулого дня вимагає окремого застарілого fixture або переходу календарного дня; цей прогін перевіряє відмову створення вчора/завтра, але не заявляє автоматизоване покриття редагування застарілого запису. Конкурентне списання, подальші експорти й великі обсяги не входять у початкову вимогу.', '']
    DOC.write_text('\n'.join(lines), encoding='utf-8')

def payload():
    cases = []
    for suffix,ac,method,title,actions,expected in CASES:
        class_name=case_class(suffix)
        source=(ROOT/f'src/test/java/com/erp/tests/ui/{class_name}.java').read_text(encoding='utf-8-sig')
        test_id = f'TC-MSHP-{suffix}'
        assert f'@TestCaseId("{test_id}")' in source and f'void {method}(' in source, test_id
        is_api = suffix.startswith('API')
        cases.append(dict(projectId=1,featureId=FEATURE,acKey=ac,testId=test_id,title=title,
          description=f'{class_name}#{method}. Suite low-value-write-off; docs/REQ-MSHP-resource-write-off.md.' + (' Відомий дефект BUG-MSHP-EDIT-FIFO: UI блокує валідну кількість при редагуванні FIFO; API-контроль проходить.' if suffix=='UI-011' else ''),
          preconditions='ERP dev; Chrome; LowValueWriteOffFixture ізолює ресурси, локації, партії та нових акторів. JDBC/SSH не потрібні.',
          testData=('Ізольований LowValueWriteOffEditTest: cloth=10, власне списання=6, залишок=4; 4/6/10 для FIFO, окремий попартійний контроль. Cleanup відновлює 10.' if suffix in EDIT_CASE_SUFFIXES else 'Власні A/B/external; gloves/cloth LOW_VALUE_ITEM, regular REGULAR, no-stock без залишку; A gloves=29 після базового списання, cloth=15; B gloves=18. Незалежні кейси з cleanup.'),
          expectedResult=expected,status='ACTIVE',priority='HIGH',severity='MAJOR',testType='FUNCTIONAL' if is_api else 'UI',
          tags='inventory,mshp,low-value,write-off,automated,dev',apiAutomationIds=[test_id] if is_api else [],uiAutomationIds=[] if is_api else [test_id],
          steps=[dict(stepOrder=i,actionText=a,expectedText=expected if i==len(actions) else 'Крок виконаний; дані fixture збережені.') for i,a in enumerate(actions,1)]))
    return dict(feature=dict(projectId=1,featureId=FEATURE,module='WMS',title='Списання ресурсів — МШП',priority='HIGH',
      description='Ознака МШП, фільтрація, форма списання із залишків, валідація, Історія операцій, журнал, RBAC та фільтри.',
      documentation=DOC.read_text(encoding='utf-8')),criteria=[dict(projectId=1,featureId=FEATURE,acKey=k,text=t) for k,t in CRITERIA],cases=cases)

def sync_stage(client):
    assert client.base_url == 'http://localhost:18100'
    data = payload(); prefix='/api/ai/projects/1'; path=f'{prefix}/features/{FEATURE}'
    existing = client.get_optional(path)
    client.request('PUT' if existing else 'POST',path if existing else f'{prefix}/features',data['feature'])
    feature = client.request('GET',path)
    acs = {a['acId']:a for a in feature['acceptanceCriteria']}
    for ac in data['criteria']:
        current=acs.get(ac['acKey'])
        client.request('PUT' if current else 'POST',f"{prefix}/acceptance-criteria/{current['id']}" if current else f'{prefix}/acceptance-criteria',ac)
    feature=client.request('GET',path); acs={a['acId']:a for a in feature['acceptanceCriteria']}
    for case in data['cases']:
        case_path=f"{prefix}/test-cases/{case['testId']}"; current=client.get_optional(case_path)
        client.request('PUT' if current else 'POST',case_path if current else f'{prefix}/test-cases',dict(case,acceptanceCriterionId=acs[case['acKey']]['id']))
        verify_case(client.request('GET',case_path),case)
    feature=client.request('GET',path)
    assert feature['documentation']==data['feature']['documentation']
    assert all(acs[a['acKey']]['text']==a['text'] for a in data['criteria'])
    return dict(result='SUCCESS',baseUrl=client.base_url,featureDbId=feature['id'],criteriaVerified=len(acs),casesVerified=len(data['cases']),
                documentationSha256=hashlib.sha256(feature['documentation'].encode()).hexdigest())

def import_stage(client, result_path):
    assert client.base_url == 'http://localhost:18100'
    prefix='/api/ai/projects/1'
    feature=client.request('GET',f'{prefix}/features/{FEATURE}')
    report=json.loads(result_path.read_text(encoding='utf-8'))
    assert not report['configurationFailures'], 'Resolve configuration/cleanup failures before importing'
    body=dict(report['tcmImport'],featureId=feature['id'])
    assert body['environment']=='dev'
    imported_ids={r['testCaseId'] for r in body['results']}
    assert imported_ids in [{c['testId'] for c in payload()['cases']}, {'TC-MSHP-'+s for s in EDIT_CASE_SUFFIXES}]
    try:
        imported=client.request('POST',f'{prefix}/test-runs/import',body)
    except RuntimeError as error:
        if 'HTTP 405' not in str(error): raise
        values={}
        for line in (ROOT.parent/'tcm/.env').read_text(encoding='utf-8').splitlines():
            key,sep,value=line.partition('=')
            if sep and not key.lstrip().startswith('#'): values[key.strip()]=value.strip().strip('"\'')
        token=os.environ.get('TCM_AUTOTEST_TOKEN') or values['TCM_AUTOTEST_TOKEN']
        headers={'X-TCM-Api-Token':token,'Content-Type':'application/json; charset=utf-8'}
        # Stage requires the feature's auto plan to exist before the import transaction starts.
        suite_request=urllib.request.Request(f"{client.base_url}/api/autotest/suites/features/{feature['id']}",headers=headers)
        with urllib.request.urlopen(suite_request,timeout=90) as response: suite=json.load(response)
        assert imported_ids <= set(suite['automationTestIds'])
        request=urllib.request.Request(f'{client.base_url}/api/autotest/runs',method='POST',
          data=json.dumps(body,ensure_ascii=False).encode('utf-8'),headers=headers)
        with urllib.request.urlopen(request,timeout=90) as response: imported=json.load(response)
    assert imported['matched']==len(imported_ids) and not imported['unmatched'] and not imported['outOfScope']
    run=client.request('GET',f"{prefix}/test-runs/{imported['runId']}")
    assert run['environment']=='dev' and run['status']=='COMPLETED'
    assert {r['testId']:r['status'] for r in run['items']}=={r['testCaseId']:r['status'] for r in body['results']}
    return dict(result='SUCCESS',baseUrl=client.base_url,featureDbId=feature['id'],importResponse=imported,run=run)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-doc',action='store_true'); parser.add_argument('--payload',type=Path)
    parser.add_argument('--sync-stage',action='store_true'); parser.add_argument('--report',type=Path)
    parser.add_argument('--import-results',type=Path)
    args=parser.parse_args()
    if args.build_doc: build_doc()
    if args.payload:
        args.payload.parent.mkdir(parents=True,exist_ok=True)
        args.payload.write_text(json.dumps(payload(),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(f'Prepared {len(CRITERIA)} AC and {len(CASES)} automated cases')
    if args.sync_stage or args.import_results:
        config=tomllib.loads((Path.home()/'.codex/config.toml').read_text(encoding='utf-8'))
        token=os.environ.get('TCM_AI_TOKEN') or config['mcp_servers']['tcm-ai']['env']['TCM_AI_TOKEN']
        client=TcmClient('http://localhost:18100',token)
        result=sync_stage(client) if args.sync_stage else import_stage(client,args.import_results)
        if args.report: args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(result if 'run' not in result else dict(result=result['result'],runId=result['run']['id'],matched=result['importResponse']['matched'])))
