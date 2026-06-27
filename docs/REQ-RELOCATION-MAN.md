# Експорт для мануальної команди · 2026-06-17 · Переміщення (ресурси + обладнання)

## Переміщення

**REQ-RELOCATION-MAN**  
**Журнал переміщень: видача, отримання, підтвердження, редагування, видалення**  
**RELOCATION · HIGH · ACTIVE**

---

## Документація

**Маршрут UI:** `/relocations` — журнал з вкладками **«Активні»** / **«Історія»** (підвкладка **«Отримано»** для зовнішніх).

| Дія UI | Маршрут |
|--------|---------|
| Отримати (зовнішнє) | `/relocation/create-input` |
| Видати | `/relocation/create-output` |
| Редагувати отримання | `/relocation/update-input/:id` |
| Редагувати видачу | `/relocation/update-output/:id` |

**Ролі для тестів:**

| Роль | Призначення |
|------|-------------|
| **Owner 1** | Основний власник складу №1 (локація з правами create/update) |
| **Owner 2** | Інший власник — негативні RBAC-перевірки |
| **Admin** | Редагування/видалення зовнішніх переміщень на BU власника |

**Обов'язково:** у шапці SPA обрана **конкретна локація** (не «Всі локації») — інакше кнопки «Видати»/«Отримати» можуть бути неактивні.

### Життєвий цикл (ресурси)

```
CREATED          ← видача storage → storage
AUTO_FINISHED    ← видача storage → UNIT  АБО  отримання SUPPLIER → storage
CREATED → FINISHED     (отримувач підтверджує)
CREATED → CANCELLED → RETURNED  (відхилення → повернення відправнику)
AUTO_FINISHED → редагування / видалення
```

### Вплив на залишки

| Операція | Відправник | Отримувач |
|----------|------------|-----------|
| Видача (CREATED) | −N | без змін до підтвердження |
| Підтвердження FINISHED | — | +N |
| Зовнішнє отримання | — | +N (+ нова партія) |
| Видача на UNIT | −N | UNIT (без залишку) |
| Повернення RETURNED | +N | — |
| Редагування AUTO_FINISHED | перерахунок дельти | перерахунок дельти |
| Видалення AUTO_FINISHED | повний відкат | повний відкат |

---

## Acceptance Criteria

**AC-01:** Owner може видавати ресурси між своїми складами та на UNIT; залишок відправника зменшується одразу.

**AC-02:** Отримувач може підтвердити (FINISHED) або відхилити (CANCELLED) активне переміщення; після RETURNED залишок повертається відправнику.

**AC-03:** Зовнішнє отримання (постачальник → склад) одразу в статусі AUTO_FINISHED; створюється партія.

**AC-04:** AUTO_FINISHED переміщення можна редагувати та видаляти; CREATED — ні (помилка 400).

**AC-05:** Admin та Owner можуть редагувати/видаляти зовнішнє отримання на своєму BU; Owner 2 — ні.

**AC-06:** Недостатній залишок при видачі/редагуванні/видаленні блокується без зміни залишків.

**AC-07:** Обладнання при видачі переходить у IN_TRANSIT; після FINISHED — AVAILABLE на складі отримувача.

### Області видимості локацій (REGIONS)

Автоматизація в suite `storage-regions`: **`RelocationVisibilityTest`** (API, TC-REL-VIS-001..011, 009), **`RelocationVisibilityUiTest`** (UI, TC-UI-REL-VIS-001..003). Див. також `src/test/java/com/erp/tests/functional/storage/README.md` — матриця наявне vs gap (TC-STR-REG/RES/CREW/UI-REL).

---

## Загальні Preconditions (для більшості кейсів)

1. Середовище **dev/staging** доступне, користувач залогінений.
2. Обрана локація **Owner 1** (склад з тестовими ресурсами).
3. На складі є ресурс із залишком ≥ 50 од. (або поповнити через «Отримати» з постачальника).
4. Для партійних кейсів — є постачальник у довіднику та можливість вказати № партії.

---

# A. Читання та фільтри (API / журнал)

## Test Case: TC-REL-001 / Список переміщень за відправником

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-001` (API)

### Мета

Перевірити фільтрацію журналу за складом-відправником.

### Steps

1. Виконати `GET /api/v1/relocations?senderIds={storageId}&size=10` під Owner 1 **або** відкрити `/relocations` з обраною локацією.  
   **Expected:** HTTP 200; у відповіді/таблиці лише переміщення, де обраний склад — відправник.

---

## Test Case: TC-REL-002 / Список за отримувачем

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-002` (API)

### Steps

1. `GET /api/v1/relocations?receiverIds={storageId}&size=10`.  
   **Expected:** HTTP 200; записи з обраним отримувачем.

---

## Test Case: TC-REL-003 / Фільтр за категорією ресурсу

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-003` (API)

### Steps

1. Дізнатися `categoryId` тестового ресурсу.  
2. `GET /api/v1/relocations?senderIds={storageId}&category={categoryId}`.  
   **Expected:** HTTP 200; у результатах лише переміщення з ресурсами цієї категорії.

---

## Test Case: TC-REL-004 / Фільтр за productIds

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-004` (API)

### Steps

1. `GET /api/v1/relocations?senderIds={storageId}&productIds={resourceId}`.  
   **Expected:** HTTP 200; список містить переміщення з цим ресурсом.

---

## Test Case: TC-REL-005 / Опції створення

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-005` (API)

### Steps

1. `GET /api/v1/relocations/creation-options?storageId={storageId}`.  
   **Expected:** HTTP 200; у відповіді доступні постачальники, ресурси, одиниці виміру для форм.

---

## Test Case: TC-REL-006 / Експорт у Excel

**MEDIUM · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-006` (API)

### Steps

1. `GET /api/v1/relocations/export?senderIds={storageId}`.  
   **Expected:** HTTP 200; `Content-Type: application/octet-stream`; файл не порожній.

---

# B. Видача ресурсів (send)

## Test Case: TC-REL-010 / Видача storage → storage

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-REL-010` (API), `TC-UI-REL-002` (UI+API)

### Мета

Створити активне переміщення між двома складами; залишок відправника зменшується, отримувач — без змін.

### Preconditions

Owner 1; залишок ресурсу на складі A ≥ 10 од.

### Steps

1. Зафіксувати залишок ресурсу на складі A (**Залишки** або API inventory).
2. `/relocations` → **«Видати»** → отримувач = склад B, ресурс, кількість **5**.  
   **Expected:** форма зберігається; редирект у журнал.
3. Вкладка **«Активні»** — новий запис у статусі очікування.  
   **Expected:** стан CREATED / «Активне».
4. Перевірити залишки.  
   **Expected:** склад A: −5 од.; склад B: без змін.

---

## Test Case: TC-REL-011 / Видача з явною партією

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-011` (API), `TC-UI-REL-008` (UI+API)

### Preconditions

На складі A є партія `BATCH-001` з залишком ≥ 10 од.

### Steps

1. Зафіксувати залишок партії `BATCH-001` на складі A.
2. **«Видати»** → вказати ту саму партію, кількість **5**, отримувач — склад B.
3. Перевірити залишок партії на A.  
   **Expected:** партія −5 од.; загальний залишок ресурсу −5.

---

## Test Case: TC-REL-012 / Видача без партії (FIFO)

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-012` (API)

### Steps

1. Поповнити склад кількома партіями без вказання партії у формі видачі.
2. Видати **N** од. без поля партії.  
   **Expected:** загальний залишок −N; списання з партій за FIFO (найстаріші першими).

---

## Test Case: TC-REL-013 / Видача на UNIT (AUTO_FINISHED)

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-REL-013` (API), `TC-UI-REL-003` (UI)

### Steps

1. Зафіксувати залишок на складі A.
2. **«Видати»** → отримувач = **підрозділ (UNIT)**, кількість **10**.
3. Вкладка **«Історія»**.  
   **Expected:** запис одразу завершений (AUTO_FINISHED).
4. Залишок складу A: **−10**; UNIT не має залишку ресурсу.

---

## Test Case: TC-REL-014 / Недостатній залишок при видачі

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-014` (API)

### Steps

1. Зафіксувати поточний залишок R на складі A.
2. Спробувати видати **R + 1000** од.  
   **Expected:** помилка валідації; залишки на A і B без змін.

---

# C. Зовнішнє отримання (receive)

## Test Case: TC-REL-020 / Отримання SUPPLIER → storage

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-REL-020` (API), `TC-UI-REL-001` (UI+API)

### Steps

1. Зафіксувати залишок на складі A.
2. `/relocations` → **«Отримати»** → постачальник, ресурс, кількість **15**, № партії, № накладної.
3. **«Підтвердити»**.  
   **Expected:** запис у **Історії** / «Отримано»; статус AUTO_FINISHED.
4. Залишок на A: **+15 од.**

---

## Test Case: TC-REL-021 / Отримання створює партію

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-021` (API)

### Steps

1. Вказати новий № партії при отриманні.
2. Перевірити **Залишки** → деталізація партій.  
   **Expected:** нова партія з вказаною кількістю.

---

## Test Case: TC-REL-022 / Отримання з internal storage (негатив)

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-022` (API)

### Steps

1. Через API спробувати `POST /relocations/receive` з `senderId` = інший склад (не SUPPLIER).  
   **Expected:** HTTP 400; залишки без змін.

---

# D. Підтвердження (resolve)

## Test Case: TC-REL-030 / CREATED → FINISHED

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-REL-030` (API), `TC-UI-REL-002` (UI+API)

### Preconditions

Є активне переміщення storage → storage на **5** од. (TC-REL-010).

### Steps

1. Увійти як **отримувач** (Owner з правами на склад B).
2. Вкладка **«Активні»** → **«Завершити»** / підтвердити.  
   **Expected:** статус FINISHED; запис у історії.
3. Залишок складу B: **+5 од.**

---

## Test Case: TC-REL-031 / CANCELLED → RETURNED

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-031` (API), `TC-UI-REL-006` (UI+API)

### Steps

1. Створити видачу storage → storage (**5** од.); зафіксувати залишок відправника.
2. Отримувач: **«Відхилити»** → CANCELLED.
3. Відправник: **«Повернути»** → RETURNED.  
   **Expected:** залишок відправника як до видачі; обладнання/ресурс повернуто.

---

## Test Case: TC-REL-032 / Resolve у фінальному стані

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-032` (API)

### Steps

1. Мати AUTO_FINISHED запис (зовнішнє отримання).
2. Спробувати `PUT /relocations/{id}/resolve?storageId=...` зі станом FINISHED.  
   **Expected:** HTTP 400; залишки без змін.

---

# E. Редагування видачі (PUT /{id}/send)

> Лише для **AUTO_FINISHED** (типово видача на UNIT).

## Test Case: TC-REL-040 / Зменшити кількість видачі

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-040` (API), `TC-UI-REL-007` (UI+API)

### Steps

1. Видати на UNIT **15** од.; зафіксувати залишок.
2. **«Редагувати»** (видача) → змінити на **10** од.  
   **Expected:** залишок відправника **+5** (повернення різниці).

---

## Test Case: TC-REL-041 / Редагування CREATED (негатив)

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-041` (API)

### Steps

1. Мати активне (CREATED) переміщення storage → storage.
2. Спробувати редагувати видачу.  
   **Expected:** HTTP 400 або кнопка недоступна.

---

## Test Case: TC-REL-042 / Збільшити кількість без залишку

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-042` (API)

### Steps

1. AUTO_FINISHED видача на UNIT на **5** од.; майже весь залишок списати іншими операціями.
2. Редагувати видачу на **9999** од.  
   **Expected:** помилка; залишки без змін.

---

## Test Case: TC-REL-043 / Змінити отримувача

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-043` (API)

### Steps

1. Видача на UNIT **6** од.
2. Редагувати: змінити `recipientId` на інший склад.  
   **Expected:** HTTP 200; у картці новий отримувач.

---

## Test Case: TC-REL-044 / Змінити відправника (негатив)

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-044` (API)

### Steps

1. Спробувати змінити `senderId` при редагуванні видачі.  
   **Expected:** HTTP 400.

---

## Test Case: TC-REL-045 / Поля осіб у накладній

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-045` (API)

### Steps

1. Редагувати AUTO_FINISHED видачу: ПІБ/звання відправника та отримувача.  
   **Expected:** поля збережені у відповіді / картці переміщення.

---

## Test Case: TC-REL-046 / Редагування без накладної

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-046` (API)

### Steps

1. Редагувати видачу без попередньої накладної.  
   **Expected:** накладна **не** створюється автоматично (`canGenerateInvoice` ≠ true).

---

# F. Редагування зовнішнього отримання (PUT /{id}/receive)

## Test Case: TC-REL-050 / Зменшити кількість отримання

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-050` (API), `TC-UI-REL-004` (UI+API)

### Steps

1. Зовнішнє отримання **15** од., партія `B-050`.
2. **«Редагувати»** → **10** од.  
   **Expected:** залишок складу **−5**; партія **−5**.

---

## Test Case: TC-REL-051 / Змінити постачальника

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-051`, `TC-REL-056` (API)

### Steps

1. Зовнішнє отримання від постачальника S1.
2. Admin: редагувати → постачальник S2 (інший SUPPLIER).  
   **Expected:** HTTP 200; загальний залишок на складі **без змін**; у картці новий sender.

---

## Test Case: TC-REL-052 / Змінити отримувача (негатив)

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-052` (API)

### Steps

1. Спробувати змінити `recipientId` при редагуванні отримання.  
   **Expected:** HTTP 400.

---

## Test Case: TC-REL-053 / removeInvoiceFile

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-053` (API), `TC-UI-REL-EQ-003` (equipment)

### Steps

1. Отримання з файлом накладної (якщо є).
2. Редагувати з прапорцем **видалити файл накладної**.  
   **Expected:** `hasExternalInvoicePhoto` = false.

---

# G. Видалення

## Test Case: TC-REL-060 / Видалити AUTO_FINISHED receive

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-060` (API)

### Steps

1. Зовнішнє отримання **10** од.
2. **«Видалити»** → підтвердити діалог.  
   **Expected:** запис зник; залишок **−10**.

---

## Test Case: TC-REL-061 / Видалити CREATED (негатив)

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-061` (API)

### Steps

1. Активне переміщення CREATED.
2. Спробувати DELETE.  
   **Expected:** HTTP 400.

---

## Test Case: TC-REL-062 / Видалити при недостатньому залишку

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-062` (API)

### Steps

1. Отримати **10** од.; потім видати ці **10** од. зі складу.
2. Спробувати видалити запис отримання.  
   **Expected:** HTTP 400; залишки без змін.

---

## Test Case: TC-REL-063 / Видалити з чужим storageId

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-063` (API)

### Steps

1. `DELETE /relocations/{id}?storageId={чужий_склад}`.  
   **Expected:** HTTP 403.

---

# H. Admin — зовнішні переміщення

## Test Case: TC-REL-054 / Admin редагує amount і description

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-REL-054` (API), `TC-UI-REL-004` (UI)

### Preconditions

Admin; `localStorage.selectedStorageId` = склад Owner 1.

### Steps

1. Owner 1 створює зовнішнє отримання **20** од.
2. Admin: **«Редагувати»** → **12** од., нова примітка.
3. Перевірити залишок і партію.  
   **Expected:** −8 од. від загального залишку та партії.

---

## Test Case: TC-REL-055 / Admin редагує поля накладної

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-055` (API)

### Steps

1. Admin редагує: `invoiceNumber`, `isPaidByCash`, `paidAmount`.  
   **Expected:** поля відображаються у картці; залишок без змін.

---

## Test Case: TC-REL-057 / Admin видаляє зовнішнє отримання

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-REL-057` (API), `TC-UI-REL-005` (UI)

### Steps

1. Owner 1: отримання **15** од.
2. Admin: **«Видалити»** → підтвердити.  
   **Expected:** повний відкат залишку та партії.

---

## Test Case: TC-REL-058 / Edit потім delete — net zero

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-058` (API)

### Steps

1. Зафіксувати baseline залишку.
2. Отримання **20** → Admin edit **25** → Admin delete.  
   **Expected:** залишок = baseline.

---

## Test Case: TC-REL-059 / Owner 2 не може edit/delete

**HIGH · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-REL-059` (API), RBAC matrix

### Steps

1. Owner 1 створює зовнішнє отримання.
2. Owner 2: спроба PUT receive / DELETE.  
   **Expected:** HTTP 403; залишки без змін.

---

# I. Партії (batch)

| ID | Сценарій | Ключова перевірка | Автотест |
|----|----------|-------------------|----------|
| **TC-REL-B01** | Named batch + FIFO | Списання **10** од. з кількох партій | API |
| **TC-REL-B02** | `isProduced=true` → FINISHED | Партія на отримувачі з `isProduced` | API |
| **TC-REL-B03** | Два ресурси в одній видачі | Окреме списання по лініях | API |
| **TC-REL-B04** | Edit receive 15→10 | Партія −5, total −5 | API + UI-004 |
| **TC-REL-B05** | Delete receive з партією | Партія зникла / 0 | API + UI-005 |
| **TC-REL-B06** | Edit send batch | Партії відправника перераховані | API |
| **TC-REL-B07** | Resolve FINISHED | Нова партія на отримувачі | API |

### Приклад: TC-REL-B04 (детально)

**Preconditions:** зовнішнє отримання 15 од., партія `B-04`.

**Steps:**

1. Зафіксувати amount партії `B-04` = 15.
2. Редагувати отримання → 10 од.
3. **Expected:** партія = 10; загальний залишок −5.

---

# J. Обладнання (API)

| ID | Сценарій | Очікуваний результат | Автотест |
|----|----------|----------------------|----------|
| **TC-REL-EQ-001** | Видача storage→storage | CREATED; equipment **IN_TRANSIT** | API |
| **TC-REL-EQ-002** | Видача на UNIT | AUTO_FINISHED; equipment на UNIT | API |
| **TC-REL-EQ-003** | Resolve FINISHED | equipment **AVAILABLE** у отримувача | API + UI-EQ-002 |
| **TC-REL-EQ-004** | CANCELLED → RETURNED | equipment повернуто відправнику | API |
| **TC-REL-EQ-005** | Sender CREATED→RETURNED | Той самий shortcut | API |
| **TC-REL-EQ-006** | Recipient CREATED→RETURNED | **403** | API |
| **TC-REL-EQ-007** | Видача RETIRED | **4xx** | API |
| **TC-REL-EQ-008** | Equipment не на відправнику | **4xx** | API |
| **TC-REL-EQ-009** | Delete supplier receive | Equipment видалено з системи | API |
| **TC-REL-EQ-010** | Delete при ASSIGNED | **400** | API — *ручна перевірка* |
| **TC-REL-EQ-011** | Delete + історія assignment | OK | API |
| **TC-REL-EQ-012** | Delete після подальшого transfer | **400** | API |
| **TC-REL-EQ-013** | Delete при IN_TRANSIT | **400** | API |
| **TC-REL-EQ-014** | Delete storage AUTO_FINISHED | equipment на відправнику | API |
| **TC-REL-EQ-015** | Edit receive (опис) | **200** | API |
| **TC-REL-EQ-016** | Edit removeInvoiceFile | flag false | API + UI-EQ-003 |
| **TC-REL-EQ-017** | Edit без remove | файл збережено | API |
| **TC-REL-EQ-018** | Edit send person fields | без auto-invoice | API |

### TC-REL-EQ-010 (ручна перевірка)

**Preconditions:** обладнання з постачальника на складі; статус **Призначено** співробітнику.

**Steps:**

1. Спробувати видалити початкове переміщення отримання.  
   **Expected:** помилка 400; equipment лишається в системі.

---

# K. UI — happy path

## Test Case: TC-UI-REL-001 / Журнал + форма отримання + залишки

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-001`

### Steps

1. Owner 1 → `/relocations`, обрана локація.
2. Перевірити кнопки **«Отримати»**, **«Видати»**.
3. **«Отримати»** → заповнити № накладної, примітки (повна форма — ресурс, постачальник, партія).
4. Після збереження — **Залишки**: +N од. та партія.

---

## Test Case: TC-UI-REL-002 / Видача + підтвердження

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-002`

### Steps

1. Видати **6** од. storage → storage.
2. **«Активні»** — запис видимий.
3. Отримувач **«Завершити»**.
4. Залишки: відправник −6, отримувач +6.

---

## Test Case: TC-UI-REL-003 / Видача на UNIT

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-003`

### Steps

1. Видати на UNIT **4** од.
2. **«Історія»** — AUTO_FINISHED.
3. Залишок відправника −4.

---

## Test Case: TC-UI-REL-004 / Admin edit зовнішнього отримання

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-004`

### Steps

1. Owner 1: setup receive (API або UI).
2. Admin: **«Історія»** → **«Отримано»** → **«Редагувати»** → зменшити qty.
3. Залишки: delta відповідає зміні.

---

## Test Case: TC-UI-REL-005 / Admin delete

**HIGH · CRITICAL · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-005`

### Steps

1. Setup receive.
2. Admin: **«Видалити»** → діалог **«Видалити»**.
3. Повний відкат залишків.

---

## Test Case: TC-UI-REL-006 / Відхилити → повернути

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-006`

### Steps

1. Видача storage → storage.
2. Отримувач **«Відхилити»**; відправник **«Повернути»**.
3. Залишок відправника відновлено.

---

## Test Case: TC-UI-REL-007 / Редагування видачі на UNIT

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-007`

### Steps

1. Видача на UNIT **8** → **10** од. через **«Редагувати»** (update-output).
2. Залишок +2 од. (повернення різниці).

---

## Test Case: TC-UI-REL-008 / Видача з партією

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-008`

### Steps

1. Видача з named batch **5** од.
2. Партія на відправнику −5.

---

## Test Case: TC-UI-REL-EQ-001 / Журнал + видача обладнання

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-EQ-001`

### Steps

1. `/relocations` — кнопка **«Видати»** доступна.
2. *(Опційно)* Видача обладнання → **«Активні»** — IN_TRANSIT.

---

## Test Case: TC-UI-REL-EQ-002 / Resolve обладнання

**MEDIUM · MAJOR · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-EQ-002`

### Steps

1. Видача equipment → отримувач підтверджує.
2. Обладнання AVAILABLE на складі отримувача; запис у **Історії**.

---

## Test Case: TC-UI-REL-EQ-003 / Edit equipment receive

**LOW · MINOR · ACTIVE**  
> **Автоматизовано:** `TC-UI-REL-EQ-003`

### Steps

1. Отримання обладнання від постачальника.
2. Редагування з `removeInvoiceFile` — прапорець файлу скинуто.

---

# L. RBAC (орієнтир для мануальної перевірки)

| Операція | Admin | Owner 1 (свій BU) | Owner 2 |
|----------|-------|-------------------|---------|
| Список / export | ✓ | ✓ | ✗ (чужий BU) |
| Send / receive | ✓* | ✓ | ✗ |
| Resolve | ✓ | ✓ (учасник) | ✗ |
| Edit receive (external) | ✓ | ✓ | ✗ |
| Delete AUTO_FINISHED | ✓ | ✓ | ✗ |
| Equipment send | — | ✓ | ✗ |

\*залежить від Keycloak policy на середовищі.

**Автоматизовано:** `RbacAccessMatrixTest` + `rbac-policy.yml`.

---

## Довідка: автотести erp-auto-test

| Група | Клас | Кількість кейсів |
|-------|------|------------------|
| API ресурси (базові) | `RelocationTest` | 22 |
| API ресурси (розширені) | `RelocationExtendedTest` | 28 |
| API обладнання | `EquipmentRelocationTest` | 18 |
| UI ресурси | `RelocationUITest` | 8 |
| UI обладнання | `EquipmentRelocationUITest` | 3 |

**Сьют:**

```bash
mvn test -Denv=dev -Dsuite=relocations
```

**Файли автотестів:**

- `src/test/java/com/erp/tests/functional/relocation/`
- `src/test/java/com/erp/tests/ui/RelocationUITest.java`
- `src/test/resources/suites/relocations.xml`

---

## Поза scope (не тестувати в цьому REQ)

- Стан **LOST** для ресурсів
- Асинхронна генерація PDF-накладних (`generateInvoice=true`)
- Автоматичні переміщення з **Замовлень**
- UI негативні тости (403) — лише API RBAC

---

*Джерело: план relocation_test_cases · Синхронізовано з erp-auto-test suite `relocations.xml`*
