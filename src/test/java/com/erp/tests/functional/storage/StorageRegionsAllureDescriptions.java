package com.erp.tests.functional.storage;

/**
 * Стандартизовані Allure-описи для suite {@code storage-regions}.
 * Формат: що перевіряємо → тестові дані → очікуваний результат → що дивитися при падінні.
 */
public final class StorageRegionsAllureDescriptions {

    private StorageRegionsAllureDescriptions() {
    }

    public static final String ON_FAIL_API = """

            При падінні: Allure → Response Details (HTTP status, body); assert — фактичні id, status, stock.
            """;

    public static final String ON_FAIL_UI = """

            При падінні: screenshot у Allure; assert — вкладка журналу, маркер рядка/№ накладної, номер спроби reload (max 5).
            """;

    public static final String ON_FAIL_STOCK = """

            При падінні: Allure → stock snapshot steps; assert — фактичні amount до/після на sender/recipient storageId.
            """;

    // --- Storage names ---

    public static final String TC_STR_NAMES_001 = """
            Що перевіряємо: контракт GET /storages/names?isActive=true (dropdown «Кому відправляю»).
            Тестові дані: сесія OWNER_1; без додаткового setup.
            Очікуваний результат: HTTP 200; JSON schema; кожен елемент має id + непорожній name.
            """ + ON_FAIL_API;

    public static final String TC_STR_NAMES_002 = """
            Що перевіряємо: унікальність storage.id у /storages/names?isActive=true (без дублікатів для Combobox).
            Тестові дані: сесія OWNER_1; повний список active names.
            Очікуваний результат: size(unique ids) == size(list); список duplicateLabels порожній.
            """ + ON_FAIL_API;

    // --- Resource visibility ---

    public static final String TC_STR_RES_001 = """
            Що перевіряємо: ADMIN додає/видаляє ресурси в області accessMode=RESOURCES.
            Тестові дані: RESTRICTED unit + region; ресурси res-vis-a, res-vis-b.
            Очікуваний результат: PUT .../resources → 200; GET list містить обидва id; після DELETE — лише один.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_002 = """
            Що перевіряємо: RESTRICTED підрозділ без областей RESOURCES не бачить autocomplete.
            Тестові дані: unit accessMode=REGIONS без region resources; пошук res-vis-*.
            Очікуваний результат: autocomplete storageId=unit порожній або без тестових resourceId.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_003 = """
            Що перевіряємо: member RESTRICTED бачить лише granted ресурси (autocomplete + GET page).
            Тестові дані: область RESOURCES з granted; hidden ресурс поза областю.
            Очікуваний результат: granted присутній; hidden відсутній у autocomplete і page.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_004 = """
            Що перевіряємо: union ресурсів з двох областей RESOURCES для одного member.
            Тестові дані: 2 області на одному unit; різні набори resources у кожній.
            Очікуваний результат: autocomplete містить union обох наборів без дублікатів id.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_005 = """
            Що перевіряємо: auto-grant ресурсу після internal relocation receive на RESTRICTED unit.
            Тестові дані: ресурс поза областю; receive з owner1 storage на restricted unit.
            Очікуваний результат: після receive ресурс з'являється в autocomplete storageId=unit.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_006 = """
            Що перевіряємо: контраст ширини номенклатури FULL_ACCESS vs RESTRICTED+одна область.
            Тестові дані: FULL unit vs RESTRICTED unit з одним granted resource.
            Очікуваний результат: FULL autocomplete ширший (більше id) ніж RESTRICTED.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_007 = """
            Що перевіряємо: guard 2.1.1 — DELETE ресурсу з області при stock>0 на unit.
            Тестові дані: ресурс у області; stock>0 через relocation receive.
            Очікуваний результат: DELETE .../resources → 400; ресурс лишається в autocomplete.
            При gap бекенду: WARN у логах, фактичний status 200.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_008 = """
            Що перевіряємо: guard 4.2 — inventory PUT з ресурсом поза областю RESOURCES.
            Тестові дані: visible у області; hidden не в області; відкрита inventory-сесія на RESTRICTED unit.
            Очікуваний результат: PUT /inventory з hidden → 400; stock hidden = 0.
            UI: autocomplete фільтрує hidden — дефект лише на прямому API PUT.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_010 = """
            Що перевіряємо: позитив до TC-STR-RES-008 — ресурс з області можна додати в inventory.
            Тестові дані: visible у області RESOURCES; сесія inventory відкрита.
            Очікуваний результат: PUT /inventory → 200; stock visible оновлюється.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_012 = """
            Що перевіряємо: область видимості ресурсів CREW береться з батьківського UNIT (CPMA-602).
            Тестові дані: parent UNIT accessMode=REGIONS + область RESOURCES (visible);
            CREW під parent з accessMode=REGIONS; hidden поза областю.
            Очікуваний результат: autocomplete?storageId=crewId містить visible, не містить hidden;
            PUT /storages/{crewId}/inventory з visible → 200, stock оновлюється
            (фікс додавання ресурсів під час інвентаризації екіпажу).
            """ + ON_FAIL_API;

    public static final String TC_UI_STR_RES_012 = """
            Що перевіряємо: UI інвентаризація CREW — додавання ресурсу з області видимості parent UNIT.
            Хто виконує:
            — API setup (UNIT REGIONS + RESOURCES region, CREW, open session): ADMIN;
            — UI (Playwright, /inventory/{crewId}, autocomplete «Оберіть ресурс», Додати, Зберегти): ADMIN.
            Тестові дані: visible у RESOURCES parent; hidden поза областю; stock на CREW = 0 до save.
            Очікуваний результат: visible у autocomplete, hidden відсутній; після save stock(visible)≈3.
            """ + ON_FAIL_UI;

    public static final String TC_STR_RES_011 = """
            Що перевіряємо: вимога 2.2 — INTERNAL receive outOfScope ресурсу розширює видимість + stock.
            Тестові дані: inScope у області; outOfScope поза нею; send+resolve на RESTRICTED unit.
            Очікуваний результат: outOfScope з'являється в autocomplete і stock>0 на unit.
            """ + ON_FAIL_STOCK;

    public static final String TC_STR_RES_009 = """
            Що перевіряємо: вимога 4.1 — SUPPLIER receive ресурсу поза областю.
            Тестові дані: ресурс не в області; receive з SUPPLIER на RESTRICTED unit.
            Очікуваний результат: 400 або auto-grant (2.2) — ресурс у autocomplete після receive.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_013 = """
            Що перевіряємо: UNIT type=UNIT + accessMode=REGIONS бачить лише granted у autocomplete та GET page.
            Тестові дані: restrictedUnitStorage; область RESOURCES з granted; outsider поза областю.
            Очікуваний результат: storage.type=UNIT; granted присутній; outsider відсутній.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_014 = """
            Що перевіряємо: GET /resources?storageId=&categoryIds= фільтрує в межах scoped-набору UNIT.
            Тестові дані: UNIT + RESOURCES region; granted і outsider в одній категорії.
            Очікуваний результат: categoryIds повертає granted; outsider відсутній.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_015 = """
            Що перевіряємо: глобальний словник (без storageId) ширший за scoped UNIT page.
            Тестові дані: UNIT + один granted; outsider поза областю.
            Очікуваний результат: global містить обидва; scoped лише granted; global.size > scoped.size.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_RBAC_001 = """
            Що перевіряємо: OWNER_2 не керує ресурсами області RESOURCES.
            Тестові дані: region accessMode=RESOURCES; resource для PUT/DELETE.
            Очікуваний результат: PUT та DELETE .../regions/{id}/resources → HTTP 403.
            """ + ON_FAIL_API;

    public static final String TC_STR_REG_RBAC_001 = """
            Що перевіряємо: OWNER_2 не керує областями видимості локацій.
            Тестові дані: recipient storage; region створена ADMIN; request POST/PUT.
            Очікуваний результат: POST/PUT/DELETE /storages/regions → HTTP 403.
            """ + ON_FAIL_API;

    public static final String TC_STR_REG_050B = """
            Що перевіряємо: revoke explicit grant прибирає viewer з links видимої локації.
            Тестові дані: visible + viewer storages; PUT grant; DELETE grant.
            Очікуваний результат: після DELETE GET .../locations не містить viewer.
            """ + ON_FAIL_API;

    public static final String TC_UI_RES_UNIT_001 = """
            Що перевіряємо: UI /resources показує лише ресурси з області видимості UNIT workspace.
            Хто виконує: API setup (ADMIN); UI Playwright /resources з selectedStorageId=UNIT.
            Тестові дані: granted у RESOURCES region; hidden поза областю.
            Очікуваний результат: granted у таблиці; hidden відсутній після пошуку за prefix.
            """ + ON_FAIL_UI;

    public static final String TC_UI_RES_UNIT_002 = """
            Що перевіряємо: перемикання sidebar workspace змінює набір ресурсів на /resources.
            Тестові дані: restricted UNIT (1 granted) vs FULL_ACCESS UNIT; extra поза областю restricted.
            Очікуваний результат: на restricted — extra відсутній; після switch на FULL — extra видимий.
            """ + ON_FAIL_UI;

    // --- Relocation visibility API ---

    public static final String TC_REL_VIS_001 = """
            Що перевіряємо: send у межах області видимості (OWNER_2 member) → CREATED, stock −N.
            Тестові дані: OWNER_2 REGIONS; region FULL_ACCESS з in-scope recipient; SEND_AMOUNT=5.
            Очікуваний результат: POST send → 200, state=CREATED; stock sender зменшився на N.
            """ + ON_FAIL_STOCK;

    public static final String TC_REL_VIS_002 = """
            Що перевіряємо: location guard — send на outsider поза /names → 4xx, stock без змін.
            Тестові дані: outsider storage не в жодній області OWNER_2; recipient.id=outsider.
            Очікуваний результат: HTTP 4xx; stock sender до == після.
            """ + ON_FAIL_STOCK;

    public static final String TC_REL_VIS_003 = """
            Що перевіряємо: resolve FINISHED отримувачем у області після in-scope send.
            Тестові дані: ADMIN send з in-scope sender; OWNER_2 resolve на owner2 storage.
            Очікуваний результат: state=FINISHED; stock recipient +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_REL_VIS_004 = """
            Що перевіряємо: resolve з outsider storageId на CREATED relocation → 4xx.
            Тестові дані: CREATED relocation; resolve storageId=outsider.
            Очікуваний результат: HTTP 4xx; state лишається CREATED.
            """ + ON_FAIL_API;

    public static final String TC_REL_VIS_009 = """
            Що перевіряємо: send на REGIONS alias → recipient.id = recipientStorage (anchor).
            Тестові дані: region accessMode=REGIONS; sender in locations; alias у /names.
            Очікуваний результат: після FINISHED stock +N на anchor, не на проміжну location.
            """ + ON_FAIL_STOCK;

    public static final String TC_REL_VIS_005 = """
            Що перевіряємо: REGIONS alias у /names; send на in-scope локацію дозволено.
            Тестові дані: region REGIONS; member OWNER_2; in-scope recipient.
            Очікуваний результат: /names містить ім'я області; send → 200 CREATED.
            """ + ON_FAIL_API;

    public static final String TC_REL_VIS_007 = """
            Що перевіряємо: explicit grant дозволяє send на granted локацію.
            Тестові дані: PUT /storages/{visible}/locations?locations={viewer}; stock на OWNER_2.
            Очікуваний результат: /names показує реальне ім'я; send → CREATED.
            """ + ON_FAIL_API;

    public static final String TC_REL_VIS_008 = """
            Що перевіряємо: після revoke explicit grant повторний send → 4xx.
            Тестові дані: grant → send OK → revoke grant → повторний send на ту ж локацію.
            Очікуваний результат: HTTP 4xx на другому send.
            """ + ON_FAIL_STOCK;

    public static final String TC_REL_VIS_010 = """
            Що перевіряємо: send ресурсом з області RESOURCES → 200.
            Тестові дані: RESTRICTED unit; resource у region RESOURCES.
            Очікуваний результат: POST send → 200.
            """ + ON_FAIL_API;

    public static final String TC_REL_VIS_011 = """
            Що перевіряємо: resource guard — send ресурсом поза областю RESOURCES → 4xx.
            Тестові дані: hidden resource не в autocomplete; inScope у області.
            Очікуваний результат: HTTP 4xx (UI autocomplete вже фільтрує).
            """ + ON_FAIL_STOCK;

    // --- CREW regions ---

    public static final String TC_STR_CREW_001 = """
            Що перевіряємо: ADMIN створює область accessMode=CREWS.
            Тестові дані: recipient у request — UNIT crew-reg-rec-; accessMode=CREWS.
            Очікуваний результат: HTTP 200; schema; accessMode=CREWS; id не null.
            Контракт stage: для CREWS API може не повертати recipientStorage (null — ок).
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_002 = """
            Що перевіряємо: locations області CREWS містять UNIT для пошуку екіпажів.
            Тестові дані: CrewRegionScenario (unit + crew + region).
            Очікуваний результат: GET .../locations містить unit.id.
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_003 = """
            Що перевіряємо: members області CREWS — підрозділ-споживач отримує доступ.
            Тестові дані: memberStorageId доданий до region members.
            Очікуваний результат: GET .../members містить memberStorageId.
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_004 = """
            Що перевіряємо: GET області CREWS за id.
            Тестові дані: prepareSingleCrewScenario.
            Очікуваний результат: id збігається; accessMode=CREWS.
            Контракт stage: для CREWS API може не повертати recipientStorage (null — ок).
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_005 = """
            Що перевіряємо: hasCrews у creation-options — in/out області CREWS.
            Тестові дані: member storage vs storage поза областю.
            Очікуваний результат: hasCrews=true для member; false для outsider.
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_006 = """
            Що перевіряємо: область CREWS без members → hasCrews=false.
            Тестові дані: region з locations, members порожні.
            Очікуваний результат: creation-options hasCrews=false.
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_011 = """
            Що перевіряємо: GET /storages/names/crew-units — ієрархія UNIT.
            Тестові дані: CrewRegionScenario з member у області.
            Очікуваний результат: HTTP 200; список містить очікувані unit nodes.
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_012 = """
            Що перевіряємо: GET /storages/names/crews?parentId= — екіпажі під UNIT.
            Тестові дані: ієрархія UNIT + CREW nodes.
            Очікуваний результат: crew.id присутній для parent UNIT.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_001 = """
            Що перевіряємо: видача на unattached CREW → CREATED («В дорозі»), потім FINISHED відправником.
            Тестові дані: CrewRegionScenario UNIT→CREW; resource зі stock; ISSUE_AMOUNT=15.
            Очікуваний результат: після send — CREATED, sender −N, crew без +N; після resolve FINISHED — crew +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_002 = """
            Що перевіряємо: після send — рядок у «В дорозі»; після finish — у «Видано».
            Тестові дані: send з description-маркером; in-transit journal + sentHistoryUi.
            Очікуваний результат: CREATED у in-transit; після FINISHED — id у sent history.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_003 = """
            Що перевіряємо: недостатній stock → send 400, залишки без змін.
            Тестові дані: amount > available stock на sender.
            Очікуваний результат: HTTP 400; stock snapshots unchanged.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_004 = """
            Що перевіряємо: multi-resource send → CREATED, потім FINISHED відправником; stock обох ресурсів.
            Тестові дані: CrewRegionScenario; 2 унікальних ресурси з stock.
            Очікуваний результат: після send — дебет sender, crew без credit; після FINISHED — credit на crew.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_005 = """
            Що перевіряємо: OWNER_2 не може send на crew поза CREWS region.
            Тестові дані: crew з OWNER_1 сценарію; сесія OWNER_2.
            Очікуваний результат: HTTP 403 або 404; stock без змін.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_006 = """
            Що перевіряємо: видача з PRODUCTION sender → CREATED → FINISHED відправником.
            Тестові дані: ephemeral PRODUCTION child; stock; crew recipient.
            Очікуваний результат: після send — PRODUCTION −N, crew без +N; після FINISHED — crew +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_007 = """
            Що перевіряємо: після FINISHED видача на CREW видима в журналі отримувача.
            Тестові дані: createSendAndFinishBySender → crew; query receivedHistoryUi(crewId).
            Очікуваний результат: relocation.id у сторінці журналу отримувача.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_008 = """
            Що перевіряємо: multi-item send у межах stock → CREATED → FINISHED.
            Тестові дані: 2 ресурси з відомим stock; суми в межах залишків.
            Очікуваний результат: HTTP 200; state CREATED після send, FINISHED після resolve відправником.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_009 = """
            Що перевіряємо: UNIT→CREW relocation відсутній у GET /relocations для ACCOUNTANT.
            Тестові дані: send+finish UNIT→crew під OWNER_1; query як ACCOUNTANT.
            Очікуваний результат: relocation.id не в результатах (бізнес-контракт логістики).
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_010 = """
            Що перевіряємо: отримувач (CREW) не може resolve FINISHED — лише відправник.
            Тестові дані: send → CREATED; resolveRaw зі storageId=crew.
            Очікуваний результат: HTTP 4xx; після resolve відправником — FINISHED.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_011 = """
            Що перевіряємо: відправник може скасувати CREATED (RETURNED) і відновити stock.
            Тестові дані: send → CREATED; resolve RETURNED відправником.
            Очікуваний результат: state=RETURNED; stock sender і crew як до send.
            """ + ON_FAIL_STOCK;

    public static final String TC_FLY_REL_001 = """
            Що перевіряємо: send на FLY_POINT → CREATED → FINISHED відправником; баланс на точці.
            Тестові дані: prepareFlyPointScenario; ISSUE_AMOUNT.
            Очікуваний результат: після send FLY_POINT без +N; після FINISHED — FLY_POINT +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_FLY_REL_002 = """
            Що перевіряємо: attached CREW (parent=FLY_POINT) — після FINISHED auto-forward на точку.
            Тестові дані: prepareAttachedCrewScenario UNIT→FLY_POINT→CREW.
            Очікуваний результат: після FINISHED crew без приросту; FLY_POINT +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_FLY_REL_003 = """
            Що перевіряємо: пряме CREW→FLY_POINT одразу AUTO_FINISHED.
            Тестові дані: unattached crew зі stock; окрема FLY_POINT під UNIT.
            Очікуваний результат: state=AUTO_FINISHED; crew −N, FLY_POINT +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_FLY_REL_004 = """
            Що перевіряємо: екіпаж лише до однієї FLY_POINT; reparent FP_A→FP_B;
            прикріплення зі stock авто-переміщує залишок CREW→FLY_POINT.
            Тестові дані: unattached CREW зі stock; дві FLY_POINT під UNIT; PUT parentId.
            Очікуваний результат: після attach до FP_A — crew −N, FP_A +N, parent=FP_A;
            після reparent на FP_B — parent=FP_B; crew names лише під FP_B.
            """ + ON_FAIL_STOCK;

    public static final String TC_FLY_REL_005 = """
            Що перевіряємо: кілька CREW під однією FLY_POINT — обидва auto-forward після FINISHED.
            Тестові дані: prepareAttachedCrewScenario + другий CREW під тим самим flyPoint.
            Очікуваний результат: crew1/crew2 без приросту; FLY_POINT +2N; обидва в crew-names точки.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_RET_001 = """
            Що перевіряємо: повернення від unattached CREW на склад (POST /relocations/receive).
            Тестові дані: prepareSingleCrewScenario; видача UNIT→CREW FINISHED; RETURN_AMOUNT < stock.
            Очікуваний результат: AUTO_FINISHED; CREW −N; склад локації +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_RET_002 = """
            Що перевіряємо: повернення від attached CREW — списання з FLY_POINT (ланцюг FP→CREW→склад).
            Тестові дані: prepareAttachedCrewScenario; після видачі stock на FP, CREW ≈ 0.
            Очікуваний результат: AUTO_FINISHED; FLY_POINT −N; склад +N; CREW знову ≈ 0.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_RET_003 = """
            Що перевіряємо: не можна повернути більше ніж залишок на unattached CREW.
            Тестові дані: stock на CREW = ISSUE_AMOUNT; receive amount = ISSUE_AMOUNT+1.
            Очікуваний результат: HTTP 400; CREW і склад без змін.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_RET_004 = """
            Що перевіряємо: не можна повернути більше ніж залишок на FLY_POINT (attached CREW).
            Тестові дані: stock на FP = ISSUE_AMOUNT; receive amount = ISSUE_AMOUNT+1.
            Очікуваний результат: HTTP 400; FLY_POINT і склад без змін.
            """ + ON_FAIL_STOCK;

    public static final String TC_FLY_WO_001 = """
            Що перевіряємо: complete write-off для attached CREW списує з parent FLY_POINT, не з CREW.
            Тестові дані: attached CREW; stock на FLY_POINT після видачі; DB seed PENDING write-off; PUT complete.
            Очікуваний результат: CREW stock unchanged; FLY_POINT −WRITE_OFF_AMOUNT.
            """ + ON_FAIL_STOCK;

    public static final String TC_FAITA_IMPL_001 = """
            Що перевіряємо: PUT implicit-resources зберігає кілька додаткових номенклатур на один виріб (глобально).
            Тестові дані: FLIGHT reconciliations для виробу + 2 implicit; ADMIN session.
            Очікуваний результат: HTTP 200; response і GET /faita/resources містять обидва implicit id;
            за наявності БД — sync_process_config.implicit_resource_usage містить усі три externalId.
            """ + ON_FAIL_API;

    public static final String TC_FAITA_IMPL_002 = """
            Що перевіряємо: після usage (симуляція SyncTeamProcess) у журналі з'являються write-off виробу
            і додаткових номенклатур з однаковим amount/sourceId; complete списує всі з FLY_POINT.
            Тестові дані: attached CREW; stock виробу+2 implicit на FLY_POINT; DB seed 3 PENDING write-off.
            Очікуваний результат: GET write-off page містить 3 externalId; після complete FLY_POINT −N для кожного.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_INC_001 = """
            Що перевіряємо: надзвичайна подія на send→CREW → LOST; crew без credit; WRITE_OFF на sender.
            Тестові дані: unattached CREW; POST /incidents/relocations.
            Очікуваний результат: LOST; crew stock unchanged; totalIncidentResources на sender +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_INC_002 = """
            Що перевіряємо: надзвичайна подія на send→FLY_POINT → LOST; точка без credit.
            Тестові дані: prepareFlyPointScenario; incident.
            Очікуваний результат: LOST; FLY_POINT stock unchanged.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_INC_003 = """
            Що перевіряємо: після LOST відправник не може FINISHED.
            Тестові дані: send→CREW → incident; resolveRaw FINISHED.
            Очікуваний результат: HTTP 4xx.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INC_004 = """
            Що перевіряємо: після FINISHED не можна створити incident.
            Тестові дані: createSendAndFinishBySender; POST incident.
            Очікуваний результат: HTTP 4xx.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INC_005 = """
            Що перевіряємо: DELETE incident → CREATED → FINISHED відправником → credit на CREW.
            Тестові дані: send → incident → delete → resolve FINISHED.
            Очікуваний результат: restore до CREATED; після FINISHED crew +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_INC_006 = """
            Що перевіряємо: incident на attached CREW до FINISHED — без auto-forward на FLY_POINT.
            Тестові дані: prepareAttachedCrewScenario; incident у CREATED.
            Очікуваний результат: FLY_POINT stock unchanged.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_HIST_001 = """
            Що перевіряємо: після send+finish на CREW → totalRemovedResources на sender +ISSUE_AMOUNT.
            Тестові дані: member storage; createSendAndFinishBySender.
            Очікуваний результат: delta removed ≈ ISSUE_AMOUNT (картка «Видано» після FINISHED).
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_013 = """
            Що перевіряємо: getCrewNames(UNIT-A) рекурсивно містить crew з дочірнього UNIT-AB.
            Тестові дані: prepareHierarchyScenario.
            Очікуваний результат: crew.id у списку для батьківського UNIT.
            """ + ON_FAIL_API;

    public static final String TC_STR_CREW_014 = """
            Що перевіряємо: getCrewUnits повертає дерево з батьком UNIT-A (не лише leaf units).
            Тестові дані: prepareHierarchyScenario.
            Очікуваний результат: root містить unitA.id; child unitAB під ним.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_001 = """
            Що перевіряємо: GET /storages/inventory/crews requestType=STOCK після видачі на CREW.
            Тестові дані: crew + resource після ISSUE_AMOUNT.
            Очікуваний результат: рядок crew+resource з amount≈ISSUE_AMOUNT.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_007 = """
            Що перевіряємо: OWNER_1 (Business_Unit_Owner) без inventory-list::{crew}::read —
            GET /storages/{crewId}/inventory для unattached CREW у області CREWS (AC-04).
            Тестові дані: prepareSingleCrewScenario (member=OWNER_1, location=unit, crew під unit) + видача;
            query як UI: searchTerm, page=0, size=100, sort=weight,desc + resource.name,asc.
            Очікуваний результат: HTTP 403 (STOCK-звіт для Owner — окремо TC-CREW-INV-001; direct read — Crew-Manager TC-007b).
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_007B = """
            Що перевіряємо: Crew-Manager (argument, UNIT) читає GET /storages/{crewId}/inventory після видачі.
            Тестові дані: CREWS member=unit.storage.id; crew.id після send від OWNER_1.
            Очікуваний результат: HTTP 200; stock≈ISSUE_AMOUNT (inventory-list::{crew}::read).
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_008 = """
            Що перевіряємо: OWNER_2 поза областю CREWS — доступ до inventory екіпажу заборонено.
            Тестові дані: crew з OWNER_1 сценарію (закріплений за локацією OWNER_1); сесія OWNER_2.
            Очікуваний результат: HTTP 403 або 404.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_008B = """
            Що перевіряємо: OWNER_1 — GET /storages/{crewId}/inventory для екіпажу, не закріпленого
            за локацією (UNIT+CREW без області CREWS / без member OWNER_1).
            Тестові дані: окремий unit+crew без createRegion(CREWS); сесія OWNER_1 після refresh.
            Очікуваний результат: HTTP 403.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_006 = """
            Що перевіряємо: STOCK-звіт /inventory/crews = direct GET /storages/{crewId}/inventory (Crew-Manager).
            Тестові дані: той самий crew/resource після видачі OWNER_1.
            Очікуваний результат: amount у звіті == direct stock (±0.01) під CREW_MANAGER.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_002 = """
            Що перевіряємо: GET /storages/inventory/crews requestType=INCOME — сума видач за період.
            Тестові дані: crew після ISSUE_AMOUNT; fromDate/toDate = сьогодні ±1 день.
            Очікуваний результат: income≥ISSUE_AMOUNT для crew+resource.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_009 = """
            Що перевіряємо: PUT /storages/{crewId}/inventory/status open — ADMIN ✓, OWNER_2 ✗.
            Тестові дані: crew з CREWS region.
            Очікуваний результат: ADMIN → 200 open=true; OWNER_2 → 403.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_010 = """
            Що перевіряємо: PUT /storages/{crewId}/inventory змінює amount на crew storage.
            Тестові дані: відкрита сесія (ADMIN); conduct як ADMIN.
            Очікуваний результат: stock оновлюється до target amount.
            """ + ON_FAIL_API;

    // --- UI relocation / invoice ---

    public static final String TC_UI_REL_010 = """
            Що перевіряємо: UI dropdown «Кому відправляю» = GET /names без дублікатів (TC-STR-REG-034).
            Тестові дані: OWNER_2 member у 3 областях; спільна location у всіх regions.
            Очікуваний результат: API — 1 entry для shared id; UI — 1 label; опції ⊆ API names.
            """ + ON_FAIL_UI;

    public static final String TC_UI_REL_016 = """
            Що перевіряємо: у формі «Видача» рядки «Список продукції» пронумеровані (1., 2., …).
            Хто виконує: OWNER_1 (UI).
            Тестові дані: /relocation/create-output; вибір ресурсу → «Додати позицію».
            Очікуваний результат: початково «1.»; після додавання позиції — «1.» і «2.».
            """ + ON_FAIL_UI;

    public static final String TC_UI_REL_017 = """
            Що перевіряємо: кнопка згорнути/розгорнути «Доступні партії (N)» на рядку видачі.
            Хто виконує: OWNER_1 (UI).
            Тестові дані: external receive isProduced=true (named batch) на складі OWNER_1;
            вибір ресурсу у формі «Видача».
            Очікуваний результат: за замовчуванням розгорнуто (чіп видимий); після кліку — згорнуто
            (чіп прихований, chevron -rotate-90); повторний клік — знову розгорнуто.
            """ + ON_FAIL_UI;

    public static final String TC_UI_REL_011 = """
            Що перевіряємо: відправник type=STORAGE — generateInvoice=true, № у journal API, download PDF/DOCX.
            Хто виконує: ADMIN (ephemeral child STORAGE у FULL_ACCESS region; OWNER_2 не має JWT read для child).
            Тестові дані: OWNER_2 REGIONS; child STORAGE + recipient у locations; POST /send?generateInvoice=true.
            Очікуваний результат: canGenerateInvoice=true; invoiceNumber у «В дорозі»/«Видано»; GET /invoice download >100 bytes.
            """ + ON_FAIL_API;

    public static final String TC_UI_REL_012 = """
            Що перевіряємо: отримувач завантажує накладну з вкладки «Отримано».
            Хто виконує:
            — API setup (region, stock, POST /send?generateInvoice=true): ADMIN;
            — API (journal «В дорозі», resolve FINISHED, journal «Отримано»): OWNER_2 (bar);
            — UI (Playwright, журнал «Отримано», download): OWNER_2, workspace=selectedStorageId=recipient (owner2 storage).
            Тестові дані: send ephemeral STORAGE → owner2 unit у FULL_ACCESS region; generateInvoice=true.
            Очікуваний результат: invoiceNumber у received history; UI download успішний.
            """ + ON_FAIL_UI;

    public static final String TC_UI_REL_013 = """
            Що перевіряємо: відправник type=PRODUCTION — те саме що TC-UI-REL-011 для PRODUCTION (API only).
            Хто виконує: ADMIN (ephemeral child PRODUCTION, relation=INTERNAL).
            Тестові дані: як TC-UI-REL-011, sender type=PRODUCTION; generateInvoice=true.
            Очікуваний результат: canGenerateInvoice=true; journal + GET /invoice download на «В дорозі» та «Видано».
            """ + ON_FAIL_API;

    public static final String TC_UI_REL_014 = """
            Що перевіряємо: відправник UNIT (ПМ БАР) завантажує накладну з UI журналу «В дорозі» та «Видано».
            Хто виконує:
            — API setup (region, stock, POST /send?generateInvoice=true): OWNER_2 (bar), sender=owner2 unit;
            — UI (Playwright, журнал, download): OWNER_2, workspace=selectedStorageId=owner2 (13);
            — API assert (journal, /invoice/exists, resolve FINISHED): OWNER_2 + ADMIN (resolve отримувачем).
            Тестові дані: FULL_ACCESS region; видача з ПМ БАР → ephemeral recipient у locations; generateInvoice=true.
            Очікуваний результат: canGenerateInvoice=true; № у журналі; клік → Playwright download PDF/DOCX >100 bytes.
            """ + ON_FAIL_UI;

    public static final String TC_UI_REL_VIS_001 = """
            Що перевіряємо: UI dropdown не показує outsider поза областями видимості.
            Тестові дані: region з in-scope location; окремий outsider storage.
            Очікуваний результат: in-scope label у dropdown; outsider відсутній.
            """ + ON_FAIL_UI;

    public static final String TC_WMS_REG_RES_016 = """
            Що перевіряємо: RESTRICTED OWNER_2 — autocomplete «Список продукції» на формі видачі
            показує лише in-scope ресурси області RESOURCES.
            Тестові дані: OWNER_2 REGIONS; RESOURCES region з granted; hidden поза областю; stock на обох.
            Очікуваний результат: granted у options; hidden відсутній.
            """ + ON_FAIL_UI;

    public static final String TC_UI_REL_VIS_002 = """
            Що перевіряємо: REGIONS — у dropdown ім'я області, не recipient.name.
            Тестові дані: region accessMode=REGIONS; anchor + location у region.
            Очікуваний результат: опція = region.name.
            """ + ON_FAIL_UI;

    public static final String TC_UI_REL_VIS_003 = """
            Що перевіряємо: UI send на REGIONS alias → recipient.id = anchor.
            Створюємо переміщення на аліас регіону. Перевіряємо що наші залишки зменшилися, переміщення 
            створилося саме на якірну локацію.
            Тестові дані: stock на OWNER_2; alias у dropdown; форма видачі з маркером TC-UI-REL-VIS-003.
            Очікуваний результат: POST /relocations/send → 200; sent.recipient.id = anchor.id;
            залишки на інших (non-anchor) локаціях області без змін; на anchor — без змін до resolve (CREATED).
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_012 = """
            Що перевіряємо: у «Видано» колонка «До» показує реальну назву екіпажу поза областю видимості.
            Хто виконує:
            — API setup (CREWS scenario, stock, send OWNER_2 → crew): ADMIN / OWNER_2;
            — UI (Playwright, вкладка «Видано»): OWNER_2, accessMode=REGIONS, crew не в scope.
            Тестові дані: prepareSingleCrewScenario; marker у description; recipient=CREW.
            Очікуваний результат: recipientName = crew.name; не «_приховано_».
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_013 = """
            Що перевіряємо: у «Отримано» колонка «Від» показує реальну назву екіпажу поза областю видимості.
            Хто виконує:
            — API setup (stock на crew, send crew → OWNER_2, resolve FINISHED): ADMIN / OWNER_2;
            — UI (Playwright, вкладка «Отримано»): OWNER_2, accessMode=REGIONS, crew не в scope.
            Тестові дані: prepareSingleCrewScenario; marker у description; sender=CREW.
            Очікуваний результат: senderName = crew.name; не «_приховано_».
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_014 = """
            Що перевіряємо: контраст — non-CREW outsider у «Отримано» маскується як «_приховано_».
            Хто виконує:
            — API setup (outsider STORAGE, send → OWNER_2, resolve FINISHED): ADMIN / OWNER_2;
            — UI (Playwright, вкладка «Отримано»): OWNER_2, accessMode=REGIONS.
            Тестові дані: ephemeral STORAGE поза scope; marker у description.
            Очікуваний результат: senderName = «_приховано_»; реальне outsider.name відсутнє.
            """ + ON_FAIL_UI;

    // --- Fly point / crew inventory (REQ-CREW-003 AC-16..21) ---

    public static final String TC_FLY_INV_001 = """
            Що перевіряємо: ADMIN відкриває/закриває inventory session на FLY_POINT.
            Тестові дані: prepareFlyPointScenario + stock після send.
            Очікуваний результат: open=true після open; open=false після close.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_002 = """
            Що перевіряємо: PUT inventory змінює stock на FLY_POINT при open session.
            Тестові дані: сесія open; target = ISSUE+5.
            Очікуваний результат: stock(FP)=target.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_003 = """
            Що перевіряємо: PUT inventory на FLY_POINT при closed session → 403.
            Очікуваний результат: HTTP 403; stock без змін.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_004 = """
            Що перевіряємо: OWNER_2 (outsider) не може open/PUT inventory на FLY_POINT OWNER_1.
            Очікуваний результат: 403/404 на status і conduct.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_005 = """
            Матриця FP-INV-04/05: додати новий ресурс і прибрати існуючий під час inventory на FLY_POINT.
            Очікуваний результат: extra з’являється; після omit — stock 0.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_006 = """
            Матриця FP-INV-03 (+history): після PUT inventory на FP історія містить ADDED_INV/REMOVED_INV.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_008 = """
            Матриця FP-INV-08: GET /storages/inventory?locations= включає рядок з FLY_POINT.

            Відомий дефект продукту (G2 / REQ-CREW-003): multi-location inventory не повертає
            resource на FLY_POINT. Тест червоний до фіксу в tk — очікування навмисно не послаблюємо.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_010 = """
            Матриця FP-INV-10: EXTERNAL FLY_POINT — create + inventory (політика INTERNAL-only або як INV-REL).
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_NEG_01 = """
            Матриця NEG-01: PUT inventory amount < 0 на FLY_POINT → 400.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_NEG_02 = """
            Матриця NEG-02: PUT inventory з неіснуючим resourceId на FLY_POINT → 4xx.
            """ + ON_FAIL_API;

    public static final String TC_FLY_INV_NEG_04 = """
            Матриця NEG-04: два послідовні PUT на відкриту сесію FP — last-write-wins.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_011 = """
            Матриця CR-INV-01 / OWN-03: unattached CREW inventory змінює stock CREW, sibling FLY_POINT без змін.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_012 = """
            Що перевіряємо: attached CREW — PUT /storages/{crewId}/inventory проксує на parent FLY_POINT.
            Тестові дані: UNIT→FP→CREW; видача на CREW → stock на FP.
            Очікуваний результат: після PUT на crewId stock(FP)=target; CREW не отримує target як окремий shelf.
            Відомий дефект: StorageItemFacade.inventory мутує path id без proxy на FLY_POINT (на відміну від write-off).
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_013 = """
            Що перевіряємо: attached PUT на crewId ≡ прямий PUT на flyPointId (ефект на FP).
            Очікуваний результат: обидва шляхи оновлюють stock FLY_POINT до заданого target.
            Відомий дефект: inventory proxy CREW→FLY_POINT ще не реалізовано на бекенді.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_014 = """
            Матриця CR-INV-02: closed session → PUT inventory на CREW → 403.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_015 = """
            Матриця CR-INV-03: Crew-Manager open + PUT inventory на CREW (CREWS region grants).
            """ + ON_FAIL_API;

    public static final String TC_CREW_OWN_001 = """
            Матриця OWN-01: після attach CREW→FP залишок на FP; GET inventory FP містить ресурс.
            """ + ON_FAIL_API;

    public static final String TC_CREW_OWN_002 = """
            Матриця OWN-02: після attached FINISHED stock на FP (auto-forward); inventory FP бачить ресурс, CREW ≈ 0.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_NEG_01 = """
            Матриця NEG-01: PUT inventory amount < 0 на CREW → 400.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_016 = """
            Матриця FP-INV-09 / CR-INV-06: autocomplete FLY_POINT = grants UNIT ancestor (RESOURCES).
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_017 = """
            Що перевіряємо: CREW під FLY_POINT autocomplete пропускає FP і бере UNIT ancestor.
            Тестові дані: UNIT→FP→CREW; RESOURCES на UNIT.
            Очікуваний результат: visible на crewId; hidden відсутній.
            """ + ON_FAIL_API;

    public static final String TC_STR_RES_018 = """
            Що перевіряємо: inventory PUT out-of-scope ресурсу на FLY_POINT → 400.
            Очікуваний результат: HTTP 400; stock hidden = 0.
            Відомий дефект: як TC-STR-RES-008 — inventory PUT ще не валідує RESOURCES scope (UI захищає autocomplete).
            """ + ON_FAIL_API;

    public static final String TC_UI_CREW_015 = """
            Матриця UI-CR-01/02: /crew-analytics → inventory CREW + conduct.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_016 = """
            Матриця UI-CR-03: attached рядок → fly-point-dashboard, не inventory crew.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_017 = """
            Матриця UI-FP-01/03: /fly-point-dashboard → inventory FP → conduct.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_018 = """
            Матриця UI-CR (checkbox): includeFlyPointStocks на /crew-analytics.
            """ + ON_FAIL_UI;

    public static final String TC_UI_FLY_INV_002 = """
            Матриця UI-FP-02: Admin Open/Close інвентаризації на /inventory?storageId=fp.
            """ + ON_FAIL_UI;

    public static final String TC_UI_FLY_INV_004 = """
            Матриця UI-FP-04: conduct disabled при closed session на FP deep-link.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_019 = """
            Матриця UI-CR-03: deep-link /inventory?storageId=attachedCrew — UX (порожньо / підказка / redirect).
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_020 = """
            Матриця UI-CR-04: CREW/FLY_POINT відсутні в sidebar workspace picker.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_021 = """
            Матриця UI-NAV-01: застарілий ?mode=crews — сторінка не падає, звичайні Залишки.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_022 = """
            Матриця UI-RBAC-01: OWNER_2 без inventory-status — немає Open/Close на FP deep-link.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_023 = """
            Матриця UI-RBAC-02: OWNER_2 без inventory update — немає/disabled Провести на FP deep-link.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_024 = """
            Матриця NEG-03: «Всі локації» — open/conduct недоступні (deep-link FP все одно конкретна локація;
            перевірка на unit workspace all-locations як WMS-003-004).
            """ + ON_FAIL_UI;

    public static final String TC_CREW_ANL_001 = """
            Що перевіряємо: аналітика екіпажів — залишки на екіпажах приховують архівні (деактивовані) CREW за замовчуванням.
            Клієнт: tk-ui StocksTab → GET /api/v1/crews/stocks з active=true (default «Активні»).
            Arrange: UNIT + CREWS region; активний CREW і архівний CREW (після видачі stock обнулено — DELETE не дозволяє archive з ненульовим stock).
            Очікуваний результат:
            — active=true: рядки лише активного екіпажу;
            — active=false: лише архівного;
            — active omitted: обидва.
            UI: /crew-analytics → «Залишки на екіпажах» — за замовчуванням без архівного; «Неактивні»/«Усі» показують його.
            """ + ON_FAIL_API;

    public static final String TC_CREW_RBAC_001 = """
            Crew-Read-ROLE + CREWS region member (unit.storage.id):
            GET /api/v1/crews/locations повертає location id батальйону з області.
            Arrange: prepareSingleCrewScenarioForMembers(member=unit.storage.id).
            """ + ON_FAIL_API;

    public static final String TC_CREW_RBAC_002 = """
            Crew-Read-ROLE: GET /api/v1/crews/hierarchy?parentId={unitId} — дерево UNIT→CREW
            без екіпажів чужого батальйону.
            """ + ON_FAIL_API;

    public static final String TC_CREW_RBAC_003 = """
            Crew-Read-ROLE: GET /api/v1/crews/stocks?parentId={unitId}&active=true
            після видачі UNIT→CREW показує CREW з ресурсом; чужий parentId → 403.
            JWT: var_business_unit_id=unit.storage.id; perm_crews-stocks::view.
            """ + ON_FAIL_API;

    public static final String TC_CREW_RBAC_004 = """
            Crew-Read-ROLE: region CREWS без member unit.storage.id → GET /crews/stocks?parentId → 403
            (роль є, область не покриває підрозділ користувача).
            """ + ON_FAIL_API;

    public static final String TC_CREW_RBAC_010 = """
            Crew-Write-ROLE vs Crew-Read-ROLE: PUT /storages/{crewId}/inventory/status open —
            Write 2xx, Read 403.
            """ + ON_FAIL_API;

    public static final String TC_UI_CREW_RBAC_001 = """
            UI: Crew-Read-ROLE — sidebar «Аналітика Екіпажів» видимий;
            /crew-analytics → «Залишки на екіпажах» показує екіпаж батальйону після видачі stock.
            """ + ON_FAIL_UI;

    public static final String TC_UI_CREW_025 = """
            Матриця UI-CR-05: вкладка «Залишки на екіпажах» — фільтр «Екіпажі» приховує архівні CREW за замовчуванням.
            """ + ON_FAIL_UI;

    public static final String TC_UI_STR_RES_013 = """
            Матриця FP-INV-09 UI: inventory FP autocomplete лише in-scope ancestor.
            """ + ON_FAIL_UI;

    public static final String TC_UI_FLY_LOAD_001 = """
            Що перевіряємо: ADMIN — sidebar «Екіпажі» → «Точки взлету» → «Залишки».
            Після GET /fly-points/stocks і /fly-points/short-stats спінер «Завантаження...» зникає;
            таблиця і фільтри usable; тестовий ресурс видно.
            """ + ON_FAIL_UI;

    public static final String TC_UI_FLY_LOAD_002 = """
            Що перевіряємо: таблиця Залишків видима до завершення GET /fly-points/short-stats;
            спінер сторінки не перекриває фільтри/таблицю (спінер short-stats лише в картках статистики).
            Клієнт: FlyPointDashboardPage loading прив’язаний до short-stats і рендериться над StocksTab.
            Відомий дефект: батьківський «Завантаження...» лишається, поки short-stats pending, хоча /stocks уже 200.
            """ + ON_FAIL_UI;

    public static final String TC_UI_FLY_LOAD_003 = """
            Що перевіряємо: abort GET /fly-points/short-stats не лишає вічний спінер;
            таблиця Залишків після /stocks лишається usable.
            """ + ON_FAIL_UI;

    public static final String TC_UI_FLY_LOAD_004 = """
            Що перевіряємо: після load Залишків перемикання Надходження / Використання / Зведені обороти
            і назад на Залишки не залишає спінер сторінки.
            """ + ON_FAIL_UI;

    public static final String TC_FLY_DASH_001 = """
            Що перевіряємо: GET /api/v1/fly-points/stocks?parentId={unit} після видачі на FLY_POINT.
            Клієнт: tk-ui StocksTab → flyPointsApi.listUnitStocks.
            Очікуваний результат: HTTP 200; вкладена модель містить flyPoint + resource + amount.
            """ + ON_FAIL_API;

    public static final String TC_FLY_DASH_002 = """
            Що перевіряємо: GET /api/v1/fly-points/short-stats?parentId={unit}&days=7 завершується 200
            протягом UI timeout (масив може бути порожній).
            Клієнт: FlyPointDashboardPage loadData → flyPointsApi.getShortStats.
            """ + ON_FAIL_API;
}
