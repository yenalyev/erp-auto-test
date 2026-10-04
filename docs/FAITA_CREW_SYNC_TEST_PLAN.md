# План тестування синхронізації екіпажів Файти з Цукерочкою

Документація вимоги: [REQ-FAITA-CREW-SYNC](REQ-FAITA-CREW-SYNC.md). Покрокові сценарії: [FAITA_CREW_SYNC_TEST_CASES](FAITA_CREW_SYNC_TEST_CASES.md). Постійний контрактний suite: `src/test/resources/suites/faita-crew-sync.xml`.

## Обсяг

Покривається повний ланцюг:

`FAITA team → crew.team → CREW storage → watch_schedule → FLY_POINT → flight usage → inventory write-off → UI`

План складено за кодом у трьох репозиторіях:

- backend: `tk`;
- frontend: `tk-ui`;
- API/UI автотести: `erp-auto-test`.

Базова матриця нижче: **35 сценаріїв**, із них **20 P0**, **14 P1**, **1 P2**. Окрема матриця нового поля «Позиція з ЖБД» наприкінці документа додає ще 6 сценаріїв.

## Критичні розбіжності між вимогою та кодом

1. **Повторна активація.** `INACTIVE → ACTIVE` не реактивує storage: repository не порівнює `team_status`, а update flow не викликає unarchive.
2. **Відв’язка від точки.** Якщо запис `watch_schedule` зник, SQL-порівняння з `NULL` та `isFlyPointChanged(non-empty)` не запускають update. Екіпаж може лишитися під старою точкою.
3. **Недостатній залишок.** Auto-complete переводить списання у `FAILED`, хоча вимога очікує `PENDING`.
4. **Ідентичність екіпажу.** `reconcileTeamByName=true` дозволяє новому `team_id` використати наявний CREW із таким самим ім’ям. Це суперечить строгому правилу «немає `team_id` → створити team і storage».
5. **Drift storage.** Якщо source snapshot не змінився, job не виправляє вручну змінені `storage.name` або `storage.active`.
6. **Пошук точки для списання.** Точка шукається глобально за назвою, без scope на unit/subunit. Однакові назви можуть спрямувати списання не туди.
7. **Дублікати журналу.** `crew.team_flight_log` не має унікального constraint, а імпорт використовує inclusive `BETWEEN`.
8. **Статус autofinished.** Backend і UI мають `PENDING`, `COMPLETED`, `FAILED`, `REJECTED_USER`; окремого `AUTO_FINISHED` немає.
9. **Однакова дата графіка.** `ORDER BY team_code, watch_date DESC` не має deterministic tie-break для двох записів одного дня.

До автоматизації потрібно узгодити пункти 1–4 як acceptance contract.

## Наявне покриття

- `SyncTeamProcessIT.shouldImportTeams` — створення екіпажів та базова ідемпотентність.
- `shouldUpdateTeamName` — rename storage, але без перевірки snapshot і drift.
- `shouldReconcileCrewsByName` — reconcile за ім’ям; може суперечити новій вимозі.
- `shouldMoveCrewUnderFlyPoint`, `shouldReconcileFlyPoint`, `shouldChangeFlyPoint` — базова робота з точками.
- `shouldDeactivateTeam` — деактивація без повторної активації.
- `shouldCreateWriteOffs` — створення implicit write-off без перевірки target storage, статусу та stock delta.
- `CrewWriteOffTest` — debit із CREW/FLY_POINT через прямий DB seed і ручний complete, без справжнього Fight sync.
- У `tk-ui` немає test runner для component tests; наявні лише typecheck, lint і build.
- Немає повного UI E2E: source tables → sync endpoint → stock/write-off → UI.

## P0 — release gate

### Синхронізація team і storage

#### SYNC-01 — новий активний екіпаж

- У FAITA є новий `ACTIVE team_id`.
- Після sync створено один рядок `crew.team` та один CREW storage.
- Зв’язок виконано за `team_id`.
- Storage має правильні `name`, `parent`, `kind=CREW`, `relation=INTERNAL`, `accessMode` та `active=true`.

#### SYNC-02 — однакове ім’я, різні team_id

- Створити два source teams з однаковим `name`, але різними `team_id`.
- Очікування: два окремі team/storage, якщо ідентичність визначається лише `team_id`.
- Тест навмисно має виявити поточний `reconcileTeamByName` gap.

#### SYNC-03 — перейменування

- Для існуючого `team_id` змінити `team_name`.
- Оновлюються `crew.team.team_name` і `storage.name`.
- ID, `alias`, `nameForInvoices`, features та залишки не змінюються.

#### SYNC-04 — ACTIVE → INACTIVE

- Snapshot стає `INACTIVE`.
- `crew.team.team_status=INACTIVE` і `storage.active=false`.
- Повторний запуск не створює дубль і не генерує зайвих змін.

#### SYNC-05 — INACTIVE → ACTIVE

- Реактивується той самий storage.
- Team/storage ID не змінюються.
- Name, parent та active синхронізовані.

#### SYNC-06 — виправлення active drift

- Source team активний, snapshot не змінився, але storage вручну деактивовано.
- Job має повернути `storage.active=true`.

### watch_schedule і точки вильоту

#### SCH-01 — вибір актуальної точки

- Для одного `team_code` є кілька записів із різними `watch_date`.
- Вибирається найновіший запис.
- FLY_POINT створено під правильним підрозділом.
- Crew переміщено під FLY_POINT; snapshot містить правильні `fly_point_id/name`.

#### SCH-02 — запису немає

- Для `team_code` немає `watch_schedule`.
- Crew залишається без точки, безпосередньо під підрозділом.
- `fly_point_id` і `fly_point_name` дорівнюють `NULL`.

#### SCH-03 — запис зник

- Перший sync прикріпив crew до точки.
- Перед другим sync запис видалено.
- Crew відв’язано від старої точки і повернуто під підрозділ.
- Поля точки очищено.

#### SCH-04 — точка змінилася A → B

- Crew переміщено до B.
- Залишки мігрують рівно один раз.
- Стара точка A не видаляється автоматично.
- Team snapshot посилається на B.

### Основна матриця списань

#### WO-01 — точка є, ресурс є

- Є reconciliation і достатній stock на FLY_POINT.
- Ресурс списується лише з FLY_POINT.
- CREW stock не змінюється.
- Статус стає погодженим terminal auto status.
- Operation history та audit вказують ту саму точку.

#### WO-02 — точка є, ресурсу немає

- На FLY_POINT немає mapping або достатнього stock.
- Статус залишається `PENDING` згідно з вимогою.
- Немає часткового debit.
- CREW не використовується як fallback.

#### WO-03 — точки немає, ресурс є

- Є reconciliation і достатній stock на CREW.
- Ресурс списується з CREW.
- Інші storage не змінюються.
- Статус стає погодженим terminal auto status.

#### WO-04 — точки немає, ресурсу немає

- Запис залишається `PENDING`.
- Stock не змінюється.

#### WO-05 — частково недостатній stock

- Stock дорівнює `amount - 1`.
- Статус `PENDING`.
- Операція атомарна: не допускається часткове списання.

#### WO-08 — ідемпотентність flight import

- Повторити sync на межі `lastSyncTimestamp/syncTimestamp`.
- Для одного source event існує один flight log і один логічний набір write-off.

### UI release gate

#### UI-01 — екіпаж із точкою

- Точка відображається на сторінці «Точки вильоту».
- Attached crew не відображається у «Екіпажі без точок».
- Картка точки має правильний crew count і active state.

#### UI-02 — екіпаж без точки

- Crew відображається у «Екіпажі без точок».
- Crew detail відкривається.
- Посилання «Залишки» веде на inventory екіпажу.

#### UI-03 — списання attached crew

- У глобальному журналі видно правильні crew і fly point.
- Запис присутній у detail точки.
- Amount, resource та status відповідають API.

#### UI-04 — списання unattached crew

- У колонці точки відображається `—`.
- Запис доступний із crew detail.
- Amount, resource та status відповідають API.

## P1 — regression до ввімкнення job

### Синхронізація

- **SYNC-07:** повторний запуск без змін — без нових team/storage/fly-point/write-off та зайвих audit UPDATE.
- **SYNC-08:** помилка одного team не блокує інші; sync watermark не губить непроцесовані записи.

### watch_schedule

- **SCH-05:** однакова назва точки у різних unit/subunit — пошук scoped до правильного підрозділу.
- **SCH-06:** два записи одного `team_code` з однаковим `watch_date` — deterministic tie-break або явне відхилення.
- **SCH-07:** `position_name` null/blank або `team_code` не збігається — точка не створюється.

### Списання

- **WO-06:** unmapped external resource → PENDING; mapping додано пізніше → запис оброблено один раз.
- **WO-07:** stock надходить після PENDING → retry списує з правильного CREW/FLY_POINT.
- **WO-09:** flight log point конфліктує з поточним parent crew — зафіксувати джерело істини.
- **WO-10:** ammunition, initiator та implicit resources мають правильні `externalId`, `amount`, `sourceId` і target.
- **WO-11:** перша поставка пізніше timestamp використання — узгоджений статус і відсутність несанкціонованого backdated debit.
- **WO-12:** один external resource зіставлено з N ERP resources — узгодити й перевірити семантику списання.

### UI

- **UI-05:** PENDING badge, доступність mapping, блокування complete без mapping, refresh після зміни стану.
- **UI-06:** фільтри storage/status/date/resource, query params, reset та deep links.
- **UI-07:** RBAC — read-only користувач бачить дані, але не може complete/reject/retry/match.

## P2 — hardening

- **UI-08:** loading, empty state, failed request, pagination і відсутність нескінченного spinner.

## Тестові дані

- Два unit і два subunit з однаковою назвою fly point.
- Team: new, existing, inactive, duplicate-name/different-id.
- Schedule: none, one, historical+latest, removed, blank, same-date tie.
- Resource: mapped/unmapped; stock `0`, `amount-1`, `amount`, `amount+1`; supply before/after usage.
- Flight event: boundary timestamp, duplicate, conflicting point, initiator, implicit resources.

## Обов’язкові інваріанти

1. Один `team_id` відповідає одному team snapshot та одному CREW storage.
2. Один source event створює один логічний набір write-off.
3. Terminal debit виконується рівно один раз і лише з одного target storage.
4. Недостатній stock не спричиняє часткового debit.
5. Stock delta дорівнює amount, а operation history та audit посилаються на той самий storage.

## Розміщення автоматизації

### Backend

- Розширити `tk/src/test/java/org/pm/tk/fight/process/SyncTeamProcessIT.java`.
- Додати repository cases для `TeamRepository`.
- Додати status/stock cases у `InventoryWriteOffServiceIT`.

### API E2E

В `erp-auto-test`:

1. Сіяти `imp.vw_cs_teams`, `imp.vw_cs_watch_schedule`, `imp.ammo_agg_v2` через DB fixture.
2. Викликати `POST /api/v1/integrations/faita/syncTeams`.
3. Перевіряти storage hierarchy, write-off API, stock delta, history та audit.
4. Не підміняти основний Fight flow прямим INSERT у `storage_item_write_off`.

### UI E2E

- Додати `FaitaCrewSyncUiTest`.
- Додати page object для глобального журналу списань.
- Перевіряти `/fly-points`, crew detail, fly-point detail та `/inventory-write-off`.
- У `tk-ui` залишити typecheck/lint/build, доки окремий component-test runner не буде свідомо додано.

## Рекомендований порядок

1. Узгодити контрактні розбіжності.
2. Реалізувати всі P0 backend tests.
3. Додати справжній API E2E через source tables і sync endpoint.
4. Додати UI-01…UI-04.
5. Закрити P1: idempotency, scope точки, retry, status transitions та часткові помилки.

## Актуальна перевірка покриття — 2026-09-26

Це аудит коду, а не результат запуску тестів. Перевірено `erp-auto-test`, а також доступні поруч `tk` і `tk-ui`. Позначка «частково» означає, що тест перевіряє лише нижчий шар або симуляцію, а не весь шлях від Fight-джерела.

### Що вже автоматизовано

- **Team/storage — частково.** Backend `SyncTeamProcessIT.shouldImportTeams`, `shouldUpdateTeamName`, `shouldDeactivateTeam`, `shouldReconcileCrewsByName` перевіряють створення, назву, деактивацію і reconcile за ім'ям. Повторну активацію, active drift і сувору унікальність за `team_id` не доводять.
- **watch_schedule — частково.** `SyncTeamProcessIT.shouldMoveCrewUnderFlyPoint`, `shouldReconcileFlyPoint`, `shouldChangeFlyPoint` покривають наявну та змінену точку. Немає перевірки зниклого запису, дубліката `team_code`, порожньої позиції та scope однакових назв.
- **Імпорт списання — частково.** `SyncTeamProcessIT.shouldCreateWriteOffs` доводить появу implicit-записів, але не перевіряє джерело stock, кінцевий статус, повноту набору для одного event і поле «Позиція з ЖБД».
- **Дебет з екіпажа/точки — частково.** Backend `InventoryWriteOffAutoCompleteProcessIT.shouldUseFlyPointIfAvailableInWriteOff` перевіряє пріоритет точки. Зовнішні `CrewWriteOffTest` (`TC-CREW-FIGHT-002`, `TC-FLY-WO-001`) сіють `PENDING` прямо в БД і викликають ручний `PUT /write-off/complete`; вони перевіряють stock delta, але не імпорт Fight та автоматичне завершення.
- **Журнал додаткових ресурсів — частково.** `FaitaImplicitResourceTest` (`TC-FAITA-IMPL-002`) також сіє записи прямо в `storage_item_write_off`, після чого перевіряє GET, ручне complete і stock. `TC-CREW-FIGHT-001` перевіряє лише HTTP 200 і ненульовий список, без вимоги знайти конкретну Fight-подію.
- **UI — суміжне покриття.** Є тести сторінок точок, залишків і зіставлення FAITA-ресурсів. Наскрізного UI-сценарію `Fight → sync → write-off → поле «Позиція з ЖБД»` немає. `faita-resources.xml` запускає ресурсні API/UI тести; `CrewWriteOffTest` входить до `functional.xml`, `regression.xml`, `storage-regions.xml` і `db-dependent.xml`.

### Підтверджені розриви контракту

- `tk` зараз імпортує `imp.ammo_agg_v2.position_name` у `crew.team_flight_log.fly_point_name`, але `InventoryWriteOffCreateRequest`, `StorageItemWriteOffEntry` та `InventoryWriteOffResponse` не мають окремого поля для сирої «Позиції з ЖБД». `tk-ui` тип `InventoryWriteOffResponse` і таблиця `/inventory-write-off` теж його не показують. Наявне `flyPointStorage` — це зіставлений склад точки; воно не гарантує збереження оригінального тексту ЖБД.
- Нинішній `StorageItemWriteOffStatus` має `PENDING`, `COMPLETED`, `FAILED`, `REJECTED_USER`. Автопроцес переводить успішний запис у `COMPLETED`, а запис без достатнього залишку — у `FAILED`. Вимога називає результати `autofinished` і `pending`; перед фіксацією assertion потрібно узгодити, чи це нові API/DB статуси, чи бізнес-назви наявних станів. Для відсутнього mapping запис залишається `PENDING`.
- Вибір точки для екіпажа у `TeamRepository` зараз обмежений `watch_date = current_date`. Вимога не задає часової межі. Відсутність рядка після попереднього прикріплення теж потребує окремого acceptance-правила та тесту.

## Додаток до тест-плану: «Позиція з ЖБД» у записі списання

**Робоче трактування.** Поле — сире `imp.ammo_agg_v2.position_name` конкретної Fight-події, збережене як snapshot у кожному створеному списанні. Воно може відрізнятися від поточного `watch_schedule` і від `flyPointStorage.name`. Це трактування слід підтвердити з власником вимоги перед реалізацією тестових assertions; без нього легко помилково протестувати назву поточної точки замість історичної позиції події.

### P0

1. **POS-01 — наявна позиція.** У події `position_name = «Позиція А»`; виконати імпорт через source table і sync job. Перевірити точне значення поля у `storage_item_write_off`, GET сторінки списань і рядку UI. Перевірити, що `flyPointStorage` окремо вказує фактично зіставлену точку.
2. **POS-02 — позиція без зіставленої точки.** Подія містить назву, якої немає серед точок. Сирий текст має залишитися в полі списання; відсутність `flyPointStorage` не стирає позицію. Дебет і статус перевірити за погодженим правилом для екіпажа без точки.
3. **POS-03 — `NULL` у джерелі.** Подія має `position_name = NULL`. Імпорт і GET не падають; поле має погоджене порожнє представлення (`null` в API/БД, `—` у UI), а логіка вибору складу не змінюється.
4. **POS-04 — один event, кілька списань.** Для основного ресурсу, ініціатора і додаткових ресурсів перевірити однакову позицію та `sourceId` у всіх записах; повторний sync не дублює їх.

### P1

5. **POS-05 — історичний snapshot.** Після створення списання змінити `watch_schedule`, назву точки або поточну точку екіпажа. Позиція з ЖБД у старому списанні не змінюється; UI і API показують те саме значення після refresh.
6. **POS-06 — межі тексту й старі записи.** Перевірити кирилицю, пробіли, максимальну дозволену довжину та записи до міграції без нового поля. Імпорт/список/експорт, якщо він існує, не мають втрачати рядки або обрізати значення без узгодженого правила.

Для POS-01…04 потрібен справжній імпорт із `imp.ammo_agg_v2`, а не INSERT готового `storage_item_write_off`: інакше передавання нового поля не перевіряється. Мінімальні assertions: `team_id`, `sourceId`, сире значення позиції, `flyPointStorage`, `storage`, ресурс, amount, status та зміна залишку рівно на amount.

### Рішення, які визначають очікування тестів

1. Яка точна назва поля в DB/API та чи показувати його лише в глобальному журналі, чи також у detail точки й екіпажа?
2. Чи є джерелом саме `ammo_agg_v2.position_name`, а не `watch_schedule.position_name`? Що робити, якщо вони різні?
3. Які точні стани відповідають словам `autofinished` та `pending`, зокрема коли mapping є, а залишку бракує?
4. Чи дозволено fallback на stock екіпажа, коли точка є, але на ній ресурсу немає? За наданою матрицею очікування — `PENDING` без fallback.

## Результат цільового запуску — 2026-09-26

- `tk`: `gradlew.bat test --tests org.pm.tk.fight.process.SyncTeamProcessIT --tests org.pm.tk.service.InventoryWriteOffAutoCompleteProcessIT --tests org.pm.tk.service.InventoryWriteOffServiceIT` — **BUILD SUCCESSFUL**; відповідно 10, 3 і 6 тестів, разом **19 passed**, 0 failed/skipped. Це підтверджує поточну реалізацію, але не закриває розриви нової вимоги, описані вище.
- `erp-auto-test`: `mvn test '-Denv=dev' '-Dtest=CrewWriteOffTest' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Duse.database=false'` — **BUILD FAILURE**: 1 failure у `setupWriteOffTests`, 4 skipped. Падіння сталося в `RelocationFixture.resolveUnitStorageId`: `No active EXTERNAL recipient with ORDERS and RELOCATIONS found`. Самі чотири test methods не виконали assertions.
- `RelocationFixture.java` вже мав незакомічені зміни до цього запуску. Поточний `resolveUnitStorageId` за назвою шукає UNIT, але фактично фільтрує `EXTERNAL` з двома features; `CrewWriteOffTest` викликає загальний `prepareContext`, хоча його власні сценарії не потребують зовнішнього отримувача. Потрібно розділити пошук UNIT та зовнішнього отримувача або прибрати зайву передумову з setup цього класу. Не змінювати контракт інших relocation-тестів без окремої перевірки їхніх маршрутів.
- Для перевірки `TC-CREW-FIGHT-002` і `TC-FLY-WO-001` після виправлення setup потрібен `-Duse.database=true` і доступна тестова БД; за `false` ці DB-seeded методи пропускаються. `TC-CREW-FIGHT-001` усе одно потребує посилення assertion до конкретного Fight event.
