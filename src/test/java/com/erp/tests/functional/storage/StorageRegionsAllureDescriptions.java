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
            Тестові дані: recipient — UNIT crew-reg-rec-; request accessMode=CREWS.
            Очікуваний результат: HTTP 200; schema; accessMode=CREWS; recipientStorage.id збігається.
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
            Очікуваний результат: accessMode=CREWS; recipientStorage.id = unit.id.
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
            Що перевіряємо: видача UNIT→CREW → AUTO_FINISHED; stock sender −N, crew +N.
            Тестові дані: CrewRegionScenario; resource з stock 100; ISSUE_AMOUNT=20.
            Очікуваний результат: state=AUTO_FINISHED; stock snapshots підтверджують дебет/кредит.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_002 = """
            Що перевіряємо: видача на CREW у журналі за productIds + senderIds.
            Тестові дані: send UNIT→crew; journal query sentHistoryUi.
            Очікуваний результат: relocation.id у сторінці журналу.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_003 = """
            Що перевіряємо: недостатній stock → send 400, залишки без змін.
            Тестові дані: amount > available stock на sender.
            Очікуваний результат: HTTP 400; stock snapshots unchanged.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_004 = """
            Що перевіряємо: multi-resource send (2+ рядки) → AUTO_FINISHED; stock обох ресурсів оновлюється.
            Тестові дані: CrewRegionScenario; 2 унікальних ресурси з stock.
            Очікуваний результат: state=AUTO_FINISHED; дебет sender / кредит crew для кожного resourceId.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_005 = """
            Що перевіряємо: OWNER_2 не може send на crew поза CREWS region.
            Тестові дані: crew з OWNER_1 сценарію; сесія OWNER_2.
            Очікуваний результат: HTTP 403 або 404; stock без змін.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_006 = """
            Що перевіряємо: видача з PRODUCTION sender → AUTO_FINISHED.
            Тестові дані: ephemeral PRODUCTION child; stock; crew recipient.
            Очікуваний результат: state=AUTO_FINISHED; stock PRODUCTION −N, crew +N.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_007 = """
            Що перевіряємо: видача на CREW видима в журналі отримувача (crew storage).
            Тестові дані: send → crew; query receivedHistoryUi(crewId).
            Очікуваний результат: relocation.id у сторінці журналу отримувача.
            """ + ON_FAIL_API;

    public static final String TC_CREW_REL_008 = """
            Що перевіряємо: multi-item send — кожна позиція не перевищує stock на sender.
            Тестові дані: 2 ресурси з відомим stock; суми в межах залишків.
            Очікуваний результат: HTTP 200; stock snapshots коректні для обох.
            """ + ON_FAIL_STOCK;

    public static final String TC_CREW_REL_009 = """
            Що перевіряємо: UNIT→CREW relocation відсутній у GET /relocations для ACCOUNTANT.
            Тестові дані: send UNIT→crew під OWNER_1; query як ACCOUNTANT.
            Очікуваний результат: relocation.id не в результатах (бізнес-контракт логістики).
            """ + ON_FAIL_API;

    public static final String TC_CREW_HIST_001 = """
            Що перевіряємо: після createSend → totalRemovedResources на sender збільшується на ISSUE_AMOUNT.
            Тестові дані: member storage; resource після видачі на crew.
            Очікуваний результат: delta removed ≈ ISSUE_AMOUNT для resourceId (картка «Видано»).
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
            Що перевіряємо: OWNER_1 читає GET /storages/{crewId}/inventory після видачі.
            Тестові дані: member області CREWS; crew.id після send.
            Очікуваний результат: HTTP 200; stock≈ISSUE_AMOUNT.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_008 = """
            Що перевіряємо: OWNER_2 поза областю CREWS — доступ до inventory екіпажу заборонено.
            Тестові дані: crew з OWNER_1 сценарію; сесія OWNER_2.
            Очікуваний результат: HTTP 403 або 404.
            """ + ON_FAIL_API;

    public static final String TC_CREW_INV_006 = """
            Що перевіряємо: STOCK-звіт /inventory/crews = direct GET /storages/{crewId}/inventory.
            Тестові дані: той самий crew/resource після видачі.
            Очікуваний результат: amount у звіті == direct stock (±0.01).
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
}
