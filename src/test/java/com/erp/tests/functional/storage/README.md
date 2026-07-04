# Functional Tests: Storages (Локації / Бізнес-юніти)

## Огляд

API-тести життєвого циклу локацій: створення, оновлення, валідація, архівація та розархівація.
Відповідають бекенд-контракту `StorageController` (`/api/v1/storages`).

Поле **`relation`** (`INTERNAL` / `EXTERNAL`) — окремо від **`accessMode`** (видимість між організаціями).

---

## Тестові сценарії

| ID | Клас | Сценарій | Severity |
|:---|:-----|:---------|:---------|
| **TC-STR-001** | `StorageTest` | Create — дочірня локація з `parentId`, `type=STORAGE`, `relation=INTERNAL` | Critical |
| **TC-STR-002** | `StorageTest` | Update — повне PUT-тіло; змінюємо name, identifierNumber, accessMode; **relation** та решта без змін | Critical |
| **TC-STR-003** | `StorageTest` | Duplicate name among active storages → 400 | Critical |
| **TC-STR-004** | `StorageTest` | Validation — null/empty/blank `name` → 400 | Normal |
| **TC-STR-005** | `StorageTest` | Relation guard — INTERNAL→EXTERNAL on update → 400 | Normal |
| **TC-STR-006** | `StorageDeactivationTest` | Deactivate — `active=false`, hidden from `/names?isActive=true` | Critical |
| **TC-STR-007** | `StorageDeactivationTest` | Unarchive — restore `active=true` | Critical |
| **TC-STR-008** | `StorageDeactivationTest` | Reuse archived name for new POST | Normal |
| **TC-STR-009** | `StorageFieldLengthValidationTest` | POST — name/alias/identifierNumber/nameForInvoices at 255 chars → 200 | Normal |
| **TC-STR-010** | `StorageFieldLengthValidationTest` | POST — name/alias at 256 chars → 400 | Normal |
| **TC-STR-011** | `StorageFieldLengthValidationTest` | PUT — 255-char acceptance | Normal |
| **TC-STR-012** | `StorageFieldLengthValidationTest` | PUT — name/alias 256 chars → 400 | Normal |
| **TC-STR-013** | `StorageRelationTest` | Create — `relation=EXTERNAL` (UI default) | Critical |
| **TC-STR-014** | `StorageRelationTest` | Update — EXTERNAL→INTERNAL allowed | Normal |
| **TC-STR-015** | `StorageRelationTest` | GET `/storages?relation=INTERNAL` filter | Normal |
| **TC-STR-016** | `StorageRelationTest` | GET `/names?relation=EXTERNAL` — equipment «Звідки» contract | Critical |
| **TC-STR-017** | `StorageRelationTest` | GET `/storages?relation=EXTERNAL` + schema | Normal |
| **TC-STR-018** | `StorageRelationTest` | relation filter незалежний від type (STORAGE, UNIT, PRODUCTION) | Critical |

### Області видимості (Storage Regions)

Термінологія бекенду: **RESTRICTED** → `accessMode=REGIONS`, **FULL** → `FULL_ACCESS`. Область = `StorageRegion`.

| ID | Клас | Сценарій | Severity |
|:---|:-----|:---------|:---------|
| **TC-STR-REG-001** | `StorageRegionTest` | ADMIN POST `/storages/regions`: name + recipientStorage + accessMode; схема response | Critical |
| **TC-STR-REG-002** | `StorageRegionTest` | GET `/storages/regions?name=` — фільтрований paged list містить створену область | Normal |
| **TC-STR-REG-003** | `StorageRegionTest` | GET `/storages/regions/{id}` — id, name, recipientStorage | Critical |
| **TC-STR-REG-004** | `StorageRegionTest` | PUT — зміна name, recipientStorage, accessMode (FULL_ACCESS→REGIONS) | Critical |
| **TC-STR-REG-005** | `StorageRegionTest` | DELETE область → GET by id не 200 | Critical |
| **TC-STR-REG-010** | `StorageRegionTest` | PUT/DELETE `/regions/{id}/locations` — додати A,B,C; видалити B,C; залишити A | Critical |
| **TC-STR-REG-012** | `StorageRegionTest` | PUT/DELETE `/regions/{id}/members` — підрозділи-споживачі області | Critical |
| **TC-STR-REG-014** | `StorageRegionTest` | Одна локація в двох областях — обидві містять її в locations (підготовка до union) | Normal |
| **TC-STR-REG-015** | `StorageRegionTest` | GET `/storages/locations/suggest?name=` — autocomplete для адмін-UI | Normal |
| **TC-STR-REG-050** | `StorageRegionTest` | Explicit grant: `PUT /storages/{visible}/locations?locations={viewer}`; revoke; перевірка links | Critical |
| **TC-STR-REG-054** | `StorageRegionTest` | GET `/storages/{visible}/locations` — перегляд viewers з explicit grant | Normal |
| **TC-STR-REG-020** | `StorageVisibilityTest` | POST підрозділ з `accessMode=REGIONS` (RESTRICTED) | Critical |
| **TC-STR-REG-021** | `StorageVisibilityTest` | Контраст FULL vs REGIONS: OWNER_1 `/names` ширший; OWNER_2 рівно 1 (власний id); перевірка accessMode обох storages | Normal |
| **TC-STR-REG-022** | `StorageVisibilityTest` | OWNER_2 без областей: `/names` = лише `owner2.storage.id` | Critical |
| **TC-STR-REG-030** | `StorageVisibilityTest` | Region REGIONS: у `/names` — `region.name`, не `recipient.name`; outsider прихований | Critical |
| **TC-STR-REG-031** | `StorageVisibilityTest` | Region FULL_ACCESS: реальні імена локацій у `/names` | Critical |
| **TC-STR-REG-034** | `StorageVisibilityTest` | Member у 3 областях — спільна локація без дублікатів id у `/names` | Critical |
| **TC-STR-REG-035** | `StorageVisibilityTest` | TC-STR-REG-034 × типи STORAGE/UNIT/PRODUCTION та FULL_ACCESS/REGIONS/змішані | Critical |
| **TC-STR-REG-033** | `StorageVisibilityTest` | `/storages/names/my-units` — REGIONS owner: 1 internal unit | Normal |
| **TC-STR-REG-040** | `StorageVisibilityTest` | Alias локації в FULL_ACCESS region → `name` у `/names` = alias | Critical |
| **TC-STR-REG-052** | `StorageVisibilityTest` | Explicit grant → реальне `storage.name` у `/names` (не alias області) | Critical |

### Області видимості ресурсів (Resource Scopes)

Термінологія: область з `accessMode=RESOURCES` + `PUT/GET/DELETE .../regions/{id}/resources`.
RESTRICTED підрозділ = `accessMode=REGIONS`; фільтр номенклатури в `/resources/autocomplete?storageId=`.

| ID | Клас | Сценарій | Severity |
|:---|:-----|:---------|:---------|
| **TC-STR-RES-001** | `StorageResourceVisibilityTest` | ADMIN: додати/видалити ресурси області; GET list | Critical |
| **TC-STR-RES-002** | `StorageResourceVisibilityTest` | RESTRICTED без областей → autocomplete порожній | Critical |
| **TC-STR-RES-003** | `StorageResourceVisibilityTest` | Member бачить лише granted у autocomplete та GET page | Critical |
| **TC-STR-RES-004** | `StorageResourceVisibilityTest` | 2 області RESOURCES → union ресурсів | Normal |
| **TC-STR-RES-005** | `StorageResourceVisibilityTest` | Internal relocation receive → auto-grant у селекторі | Critical |
| **TC-STR-RES-006** | `StorageResourceVisibilityTest` | FULL_ACCESS vs RESTRICTED — контраст ширини номенклатури | Normal |
| **TC-STR-RES-007** | `StorageResourceVisibilityTest` | DELETE ресурсу з області при stock>0 (guard 2.1.1) | Normal |
| **TC-STR-RES-008** | `StorageResourceVisibilityTest` | Inventory PUT: ресурс поза областю → 400, stock не змінюється | Critical |
| **TC-STR-RES-010** | `StorageResourceVisibilityTest` | Inventory PUT: ресурс з області → 200, stock оновлюється | Normal |
| **TC-STR-RES-011** | `StorageResourceVisibilityTest` | INTERNAL→INTERNAL receive: outOfScope додається до видимості + stock | Critical |
| **TC-STR-RES-009** | `StorageResourceVisibilityTest` | SUPPLIER receive невидимого ресурсу (4.1 / auto-grant 2.2) | Normal |

### Переміщення × області видимості (Relocation Visibility)

**Матриця покриття:** що вже було vs що додає `RelocationVisibilityTest`.

| Категорія | Наявні ID (суміжні) | Що перевіряють | Прогалина |
|:----------|:--------------------|:---------------|:----------|
| `/names` без send | TC-STR-REG-030..035 | Селектор локацій для REGIONS-member | Не викликають `POST send` |
| Resource auto-grant | TC-STR-RES-005, 011 | Receive розширює autocomplete | Не guard send до чужої **локації** |
| CREWS discovery | TC-STR-CREW-005/006 | `hasCrews` in/out області | Лише CREWS, не location regions |
| UI dropdown (позитив) | TC-UI-REL-010 | «Кому відправляю» = `/names`, dedup | Без негативу outsider |
| UI invoice in-region | TC-UI-REL-011..014 | Накладна при видачі в області | Журнал/негативи |
| Загальний relocation | TC-REL-010, TC-REL-005 | Lifecycle без REGIONS setup | Owner1↔Owner2 без областей |

| ID | Клас | Сценарій | Severity |
|:---|:-----|:---------|:---------|
| **TC-REL-VIS-001** | `RelocationVisibilityTest` | Send в межах області (OWNER_2 member) → CREATED, stock −N | Critical |
| **TC-REL-VIS-002** | `RelocationVisibilityTest` | Send поза областю → 4xx, stock без змін | Critical |
| **TC-REL-VIS-003** | `RelocationVisibilityTest` | Resolve FINISHED отримувачем у області | Critical |
| **TC-REL-VIS-004** | `RelocationVisibilityTest` | Resolve з outsider storageId → 4xx | Critical |
| **TC-REL-VIS-005** | `RelocationVisibilityTest` | REGIONS alias: `/names` містить ім'я області; send дозволено | Critical |
| **TC-REL-VIS-009** | `RelocationVisibilityTest` | Send на аліас REGIONS → `recipient.id` = anchor; stock на anchor після FINISHED | Critical |
| **TC-REL-VIS-007** | `RelocationVisibilityTest` | Explicit grant → send на granted локацію | Critical |
| **TC-REL-VIS-008** | `RelocationVisibilityTest` | Revoke grant → повторний send заборонено | Normal |
| **TC-REL-VIS-010** | `RelocationVisibilityTest` | Send ресурсом з області RESOURCES → 200 | Critical |
| **TC-REL-VIS-011** | `RelocationVisibilityTest` | Send ресурсом поза областю RESOURCES → 4xx | Critical |
| **TC-UI-REL-VIS-001** | `RelocationVisibilityUiTest` | Dropdown: outsider відсутній у «Кому відправляю» | Critical |
| **TC-UI-REL-VIS-002** | `RelocationVisibilityUiTest` | REGIONS: у dropdown ім'я області, не recipient.name | Critical |
| **TC-UI-REL-VIS-003** | `RelocationVisibilityUiTest` | UI send на аліас → `recipient.id` = anchor (recipientStorage) | Critical |

**Запуск лише relocation-visibility API:**
```bash
mvn test -Denv=dev -Dtest=RelocationVisibilityTest
```

**Suite (усі області видимості — API + CREWS + UI):**
```bash
mvn test -Denv=dev -Dsuite=storage-regions
```

**Запуск лише resource-visibility тестів:**
```bash
mvn test -Denv=dev -Dtest=StorageResourceVisibilityTest
```

**Передумова `StorageVisibilityTest`:** `@BeforeClass` тимчасово ставить `accessMode=REGIONS` для OWNER_2 (`owner2.storage.id`) і **purge** member/grants через `purgeViewerVisibilityScope`; `@AfterClass` відновлює accessMode.

**Модель explicit grant:** `storage_location.storage_id` = видима локація, `location_storage_id` = viewer (підрозділ).

**Запуск лише location-region API (без CREWS/UI):**
```bash
mvn test -Denv=dev -Dtest=StorageRegionTest,StorageVisibilityTest,StorageNamesEndpointTest,StorageResourceVisibilityTest
```

### Області видимості CREWS (Видача на екіпажі)

| ID | Клас | Сценарій | Severity |
|:---|:-----|:---------|:---------|
| **TC-STR-CREW-001..004** | `StorageCrewRegionTest` | CRUD області `accessMode=CREWS`, locations, members | Critical |
| **TC-STR-CREW-005..006** | `CrewVisibilityTest` | `hasCrews`, crew-units, crew-names | Critical |
| **TC-STR-CREW-011..012** | `CrewVisibilityTest` | Ієрархія UNIT та рекурсивний пошук екіпажів | Critical |
| **TC-CREW-REL-001..003** | `CrewRelocationTest` | Send→CREW AUTO_FINISHED, journal, insufficient stock | Critical |
| **TC-UI-CREW-012..014** | `CrewJournalNameVisibilityUiTest` | UI «Видано»/«Отримано»: назва CREW завжди реальна поза REGIONS scope; non-CREW → `_приховано_` | Critical |
| **TC-CREW-INV-001,006,007,007b,008** | `CrewInventoryTest` | STOCK report; OWNER_1 denied direct; Crew-Manager direct; OWNER_2 denied | Critical |
| **TC-CREW-INV-002** | `CrewInventoryTest` | INCOME report — **disabled** (див. коментар у тесті) | Normal |

**Запуск crew-тестів:**
```bash
mvn test -Denv=dev -Dtest=StorageCrewRegionTest,CrewVisibilityTest,CrewRelocationTest,CrewInventoryTest
```

**Fixture:** `CrewRegionFixture` — `prepareSingleCrewScenario`, `prepareHierarchyScenario`.

**Crew-Manager UI/API:** `user.unit.username=argument` (`UserRole.CREW_MANAGER`), `unit.storage.id=77` — Keycloak `Crew-Manager-ROLE` для direct crew inventory (`TC-UI-CREW-004`, `TC-CREW-INV-007b`).

**Cleanup:** API crew-тести extends `CrewApiTestBase` → `StorageApiTestBase` (`@AfterMethod`/`@AfterClass`). UI — `TestArtifactCleanup` (див. `CrewIssuanceUITest`).

### Пов’язані тести (інші suite)

| ID | Клас | Сценарій |
|:---|:-----|:---------|
| **TC-INV-REL-001..003** | `StorageRelationInventoryTest` | Stock on INTERNAL receive; no stock on EXTERNAL receive/send |
| **TC-INV-REL-004** | `StorageRelationInventoryTest` | Inventory session PUT on EXTERNAL (WMS path vs relocation) |
| **TC-INV-REL-005** | `StorageRelationInventoryTest` | EXTERNAL receive no-op для STORAGE, UNIT, PRODUCTION |
| **TC-EQ-SEL-001..003** | `EquipmentSelectorContractTest` | Equipment form sender selector API |
| **TC-REL-REL-001..002** | `RelocationTest` | Send→EXTERNAL AUTO_FINISHED; INTERNAL→INTERNAL resolve |
| **TC-REL-VIS-001..011** | `RelocationVisibilityTest` | Send/resolve in/out location & resource visibility regions |
| **TC-UI-REL-VIS-001..003** | `RelocationVisibilityUiTest` | UI dropdown in/out scope + send на REGIONS alias |

RBAC: `STORAGE_*`, `STORAGE_REGION_*`, `STORAGE_PUT_ADD_LOCATION_LINKS` — у `rbac-policy.yml`.

---

## Технічні особливості

- **Fixture:** `StorageFixture` — `createUniqueStorage`, `createExternalChildStorage`, `getById`, `getNames(relation, types)`, `getPage`, `getMyUnits`
- **Fixture:** `StorageRegionFixture` — CRUD областей, locations/members, explicit grants, cleanup
- **Factory:** `StorageDataFactory.childStorage()` (INTERNAL default), `restrictedStorage()` (REGIONS), `StorageRegionDataFactory`
- **Cleanup:** `createStorage` / `createChildStorage` / `createExternalChildStorage` → `trackForCleanup` → `DELETE /storages/{id}` у `@AfterMethod` + `@AfterClass` (`StorageApiTestBase`). На `staging` cleanup пропускається.
- **Верифікація:** `verifyEntityViaGetById` / `verifyUpdatedEntity` у `BaseFunctionalTest`
- **Схеми:** paged list (`storage-paged-list-schema.json`), names array (`storage-names-list-schema.json`)
- **Запуск:** `mvn test -Denv=dev -Dtest=StorageTest,StorageRelationTest,StorageDeactivationTest`
