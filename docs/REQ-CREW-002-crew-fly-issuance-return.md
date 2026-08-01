# REQ-CREW-002 — Видача та повернення CREW/FLY

Документація фічі для команди QA / продукту / автотестів.  
TCM feature: **REQ-CREW-002** «Видача та повернення CREW/FLY» (модуль CREW, parent `REQ-CREW`) — **повна documentation у TCM**.  
Дзеркало в репо: `docs/REQ-CREW-002-crew-fly-issuance-return.md`.  
SUT: backend `tk`, frontend `tk-ui`. Автотести: `erp-auto-test`.

Суміжні фічі (не дублювати тут повністю):

| Feature | Що дає цій фічі |
|---------|-----------------|
| `REQ-CREW-003` | Інвентаризація / звіти STOCK·INCOME CREW/FLY (окремий doc) |
| `REQ-WMS-008` | Інциденти на видачі CREW/FP |
| `REQ-REGION-002` | CREWS region / видимість локацій |

---

## 1. Огляд

Фіча покриває **два напрямки** переміщень ресурсів між складом локації та CREW / FLY_POINT:

1. **Видача** (UNIT/склад → CREW або FLY_POINT) — send → CREATED → FINISHED / AUTO_FINISHED; для attached CREW — auto-forward на FLY_POINT.
2. **Повернення** (CPMA-647) — отримання від екіпажу назад на склад локації через `POST /relocations/receive` з `senderId` = CREW.

Цей документ детально описує **повернення (AC-22)**. Видача (AC-01…AC-21) — у TCM acceptance criteria тієї ж фічі; автотести: `CrewRelocationTest`, `FlyPointRelocationTest`, `CrewIssuanceUITest`.

### 1.1. Терміни

| Термін | Значення |
|--------|----------|
| **CREW** | `UnitType.CREW` — склад екіпажу |
| **FLY_POINT** | `UnitType.FLY_POINT` — точка вильоту |
| **Unattached CREW** | Parent ≠ FLY_POINT; залишок на shelf екіпажу |
| **Attached CREW** | Parent = FLY_POINT; операційний залишок на точці (після auto-forward) |
| **Receive / повернення** | `POST /api/v1/relocations/receive`, sender = CREW, recipient = склад локації |
| **CREWS region** | `accessMode=CREWS` — membership + locations для кнопок UI |

### 1.2. Типова ієрархія (fixtures)

```text
UNIT
├── FLY_POINT
│   └── CREW (attached)     ← prepareAttachedCrewScenario
└── CREW (unattached)       ← prepareSingleCrewScenario
```

---

## 2. Повернення (CPMA-647) — AC-22

### 2.1. Бізнес-правила

1. Повернути можна **не більше**, ніж є на залишку точки (attached) або екіпажу (unattached).
2. **Attached:** при списанні створюється переміщення FLY_POINT → CREW, потім CREW → склад локації.
3. **Unattached:** створюється переміщення CREW → склад локації.
4. Право оформлювати: `relocation::create` на **recipient** (склад локації); UI — кнопка «Отримати від екіпажа» при `hasCrews` і не «Всі локації».

```mermaid
flowchart TD
  receive["POST /relocations/receive sender=CREW"] --> stockCheck{"stockOwnerFor"}
  stockCheck -->|parent=FLY_POINT| checkFp["validateCrewStocks on FLY_POINT"]
  stockCheck -->|unattached| checkCrew["validateCrewStocks on CREW"]
  receive --> event["RelocationReceived"]
  event -->|attached| chain["auto send FLY_POINT to CREW then CREW to warehouse"]
  event -->|unattached| direct["CREW to warehouse only"]
```

### 2.2. API

| Метод | Path | Enum | Примітка |
|-------|------|------|----------|
| POST | `/api/v1/relocations/receive` | `RELOCATION_POST_RECEIVE` | multipart; sender=CREW або EXTERNAL SUPPLIER |
| GET | `/api/v1/storages/names/crew-units` | `STORAGE_GET_CREW_UNITS` | UI: каскад підрозділ |
| GET | `/api/v1/storages/names/crews` | `STORAGE_GET_CREW_NAMES` | UI: список екіпажів |

SUT (read-only):

- `RelocationValidator.validateCreateReceive` + `stockOwnerFor` + `validateCrewStocks`
- `FlyPointFacade.onRelocationReceived` — ланцюг для attached
- `RelocationController`: `@PreAuthorize` на `recipientId` + `relocation::create`

Fixture helpers: `RelocationFixture.createCrewReceive` / `tryCrewReceive`, `RelocationDataFactory.buildCrewReceiveRequest`.

### 2.3. UI

| Елемент | Значення |
|---------|----------|
| CTA журналу | «Отримати від екіпажа» → `/relocation/create-input-crew` |
| h1 форми | «Отримання від екіпажа» |
| Поля | Підрозділ → Екіпаж → ресурс (Autocomplete) → кількість → Підтвердити |
| Після submit | AUTO_FINISHED → вкладка «Отримано» |

Page objects: `RelocationPage.clickReceiveFromCrew()`, `RelocationCreateInputCrewPage`.

Та сама форма для unattached і attached — різниця лише в backend stock (FP vs CREW).

### 2.4. Acceptance Criteria — AC-22

**TCM:** Повернення від екіпажу на склад (CPMA-647): `POST /relocations/receive` з sender=CREW; amount ≤ stock (CREW або FLY_POINT для attached); unattached — CREW→склад; attached — ланцюг FLY_POINT→CREW→склад.

| TestCaseId | Клас / метод | Суть |
|------------|--------------|------|
| TC-CREW-RET-001 | `CrewReturnTest.unattachedCrewReturnDebitsCrewCreditsWarehouse` | API unattached: CREW −N, warehouse +N |
| TC-CREW-RET-002 | `CrewReturnTest.attachedCrewReturnDebitsFlyPointCreditsWarehouse` | API attached: FP −N, warehouse +N, CREW ≈ 0 |
| TC-CREW-RET-003 | `CrewReturnTest.unattachedCrewReturnOverStockRejected` | amount > CREW stock → 400 |
| TC-CREW-RET-004 | `CrewReturnTest.attachedCrewReturnOverStockOnFlyPointRejected` | amount > FP stock → 400 |
| TC-UI-CREW-RET-001 | `CrewReturnUITest.receiveFromCrewButtonVisible` | CTA видима |
| TC-UI-CREW-RET-002 | `CrewReturnUITest.happyPathUnattachedCrewReturn` | UI unattached + stock |
| TC-UI-CREW-RET-003 | `CrewReturnUITest.happyPathAttachedCrewReturnDebitsFlyPoint` | UI attached + FP debit |

---

## 3. Видача (коротко)

AC-01…AC-21 — видача UNIT→CREW/FLY, journal, RBAC, UI «Видати на екіпаж», інциденти, видимість імен.  
Автотести: `CrewRelocationTest`, `FlyPointRelocationTest`, `CrewFlyPointIncidentTest`, `CrewIssuanceUITest`, `CrewJournalNameVisibilityUiTest`.  
Деталі критеріїв — у TCM під тим самим `REQ-CREW-002`.

---

## 4. Як ганяти

```bash
# API повернення
mvn test -Denv=staging -Dtest=CrewReturnTest

# UI повернення
mvn test -Denv=staging -Dtest=CrewReturnUITest

# Обидва
mvn test -Denv=staging -Dtest=CrewReturnTest,CrewReturnUITest
```

Suites: `relocations.xml`, `functional.xml`, `storage-regions.xml`, `regression.xml`, `ui-dev.xml`.

**Staging:** cleanup локацій/областей зазвичай пропускається — для UI autocomplete шукати ресурс за унікальним суфіксом імені (див. `RelocationCreateInputCrewPage.selectResourceByName`).

---

## 5. Посилання на код SUT (read-only)

| Компонент | Шлях |
|-----------|------|
| Validate receive + crew stock | `tk` … `RelocationValidator#validateCreateReceive` |
| Attached chain on receive | `tk` … `FlyPointFacade#onRelocationReceived` |
| Receive endpoint | `tk` … `RelocationController` POST `/receive` |
| UI форма | `tk-ui` … `RelocationCreateInputCrewPage.tsx` |
| UI CTA | `tk-ui` … `RelocationPage.tsx` («Отримати від екіпажа») |

З `erp-auto-test` **не** редагувати `tk` / `tk-ui` (див. `.cursor/rules/sut-no-modify.mdc`).

---

## 6. Історія змін документа

| Дата | Зміна |
|------|--------|
| 2026-07-27 | Перша версія: фокус AC-22 повернення CPMA-647; карта TC API/UI; дзеркало TCM |
