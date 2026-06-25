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
| **TC-STR-REG-032** | `StorageVisibilityTest` | Member у 2 областях — union loc1 + loc2 у `/names` | Normal |
| **TC-STR-REG-033** | `StorageVisibilityTest` | `/storages/names/my-units` — REGIONS owner: 1 internal unit | Normal |
| **TC-STR-REG-040** | `StorageVisibilityTest` | Alias локації в FULL_ACCESS region → `name` у `/names` = alias | Critical |
| **TC-STR-REG-052** | `StorageVisibilityTest` | Explicit grant → реальне `storage.name` у `/names` (не alias області) | Critical |

**Передумова `StorageVisibilityTest`:** `@BeforeClass` тимчасово ставить `accessMode=REGIONS` для OWNER_2 (`owner2.storage.id`); `@AfterClass` відновлює.

**Модель explicit grant:** `storage_location.storage_id` = видима локація, `location_storage_id` = viewer (підрозділ).

**Запуск лише region-тестів:**
```bash
mvn test -Denv=dev -Dtest=StorageRegionTest,StorageVisibilityTest
```

### Пов’язані тести (інші suite)

| ID | Клас | Сценарій |
|:---|:-----|:---------|
| **TC-INV-REL-001..003** | `StorageRelationInventoryTest` | Stock on INTERNAL receive; no stock on EXTERNAL receive/send |
| **TC-INV-REL-004** | `StorageRelationInventoryTest` | Inventory session PUT on EXTERNAL (WMS path vs relocation) |
| **TC-INV-REL-005** | `StorageRelationInventoryTest` | EXTERNAL receive no-op для STORAGE, UNIT, PRODUCTION |
| **TC-EQ-SEL-001..003** | `EquipmentSelectorContractTest` | Equipment form sender selector API |
| **TC-REL-REL-001..002** | `RelocationTest` | Send→EXTERNAL AUTO_FINISHED; INTERNAL→INTERNAL resolve |

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
