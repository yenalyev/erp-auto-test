# Functional Tests: Storages (Локації / Бізнес-юніти)

## Огляд

API-тести життєвого циклу локацій: створення, оновлення, валідація, архівація та розархівація.
Відповідають бекенд-контракту `StorageController` (`/api/v1/storages`).

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

RBAC: `STORAGE_GET_ALL`, `POST`, `PUT`, `GET_BY_ID`, `DELETE`, `UNARCHIVE` — у `rbac-policy.yml`.

---

## Технічні особливості

- **Fixture:** `StorageFixture` — `createUniqueStorage`, `getById`, `deactivate`, `unarchive`, `getNames`
- **Верифікація:** `verifyEntityViaGetById` / `verifyUpdatedEntity` у `BaseFunctionalTest`
- **Схеми:** paged list (`storage-paged-list-schema.json`), names array (`storage-names-list-schema.json`)
- **Запуск:** `mvn test -Denv=dev -Dtest=StorageTest,StorageDeactivationTest`
