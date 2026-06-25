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

### Пов’язані тести (інші suite)

| ID | Клас | Сценарій |
|:---|:-----|:---------|
| **TC-INV-REL-001..003** | `StorageRelationInventoryTest` | Stock on INTERNAL receive; no stock on EXTERNAL receive/send |
| **TC-INV-REL-004** | `StorageRelationInventoryTest` | Inventory session PUT on EXTERNAL (WMS path vs relocation) |
| **TC-INV-REL-005** | `StorageRelationInventoryTest` | EXTERNAL receive no-op для STORAGE, UNIT, PRODUCTION |
| **TC-EQ-SEL-001..003** | `EquipmentSelectorContractTest` | Equipment form sender selector API |
| **TC-REL-REL-001..002** | `RelocationTest` | Send→EXTERNAL AUTO_FINISHED; INTERNAL→INTERNAL resolve |

RBAC: `STORAGE_GET_ALL`, `POST`, `PUT`, `GET_BY_ID`, `DELETE`, `UNARCHIVE` — у `rbac-policy.yml`.

---

## Технічні особливості

- **Fixture:** `StorageFixture` — `createUniqueStorage`, `createExternalChildStorage`, `getById`, `getNames(relation, types)`, `getPage`, `getMyUnits`
- **Factory:** `StorageDataFactory.childStorage()` (INTERNAL default для backward compat), `externalStorage()` (UI default)
- **Cleanup:** `createStorage` / `createChildStorage` / `createExternalChildStorage` → `trackForCleanup` → `DELETE /storages/{id}` у `@AfterMethod` + `@AfterClass` (`StorageApiTestBase`). На `staging` cleanup пропускається.
- **Верифікація:** `verifyEntityViaGetById` / `verifyUpdatedEntity` у `BaseFunctionalTest`
- **Схеми:** paged list (`storage-paged-list-schema.json`), names array (`storage-names-list-schema.json`)
- **Запуск:** `mvn test -Denv=dev -Dtest=StorageTest,StorageRelationTest,StorageDeactivationTest`
