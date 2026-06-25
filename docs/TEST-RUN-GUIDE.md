# Керівництво з запуску автотестів ERP

Документ описує, **як запускати тести**, **які suite за що відповідають** і як вони вкладені одна в одну.

---

## 1. Базова команда

```bash
mvn test -Denv=<середовище> -Dsuite=<suite>
```

**Приклади (PowerShell — параметри в лапках):**

```powershell
mvn test "-Denv=dev" "-Dsuite=smoke"
mvn test "-Denv=staging" "-Dsuite=regression"
mvn test "-Denv=dev" "-Dtest=RelocationJournalSortApiTest"
```

| Параметр | За замовчуванням (pom.xml) | Опис |
|----------|----------------------------|------|
| `env` | `dev` | Профіль конфігурації → `src/test/resources/config/{env}.properties` |
| `suite` | `dev-test` | TestNG suite → `src/test/resources/suites/{suite}.xml` |

**Один тест або клас** (suite ігнорується):

```powershell
mvn test "-Denv=dev" "-Dtest=RelocationTest"
mvn test "-Denv=dev" "-Dtest=RelocationJournalFilterSortUITest#sentHistoryProductFilterAndRecipientSort"
```

---

## 2. Середовища (`env`)

```
env
├── dev          → config/dev.properties      (https://dev.cukerochka.sbs)
├── staging      → config/staging.properties  (https://stage.cukerochka.sbs)
│   └── alias: stage  (ConfigProvider нормалізує stage → staging)
└── local        → config/local.properties    (Testcontainers + Docker)
```

| Профіль | Призначення | Автентифікація | БД |
|---------|-------------|----------------|-----|
| **dev** | Віддалений dev-сервер | Playwright (cookies) + опційно Keycloak token | Прямий JDBC через VPN (`use.database=true`) |
| **staging** | Stage / pre-prod | Те саме | Те саме |
| **local** | Локальний SUT у Docker | Testcontainers Keycloak | PostgreSQL у контейнері |

### Секрети ботів (`.env.dev` / `.env.staging`)

Файли в корені проєкту (gitignored). Підвантажуються автоматично для відповідного `env`:

- `CLIENT_ID`, `CLIENT_SECRET`, `GET_TOKEN_URL`
- `GET_DATA_URL`, `GET_RELOCATIONS_DATA_URL`

Потрібні для suite **`bots`**.

### Прямий доступ до БД (dev / staging)

```properties
use.database=true
ssh.enabled=false
db.url=jdbc:postgresql://<vpc-host>:5432/cukerochka
```

Потрібен **VPN** до VPC. SSH-тунель — запасний варіант (`ssh.enabled=true`, `ssh.key.path` або `ssh.password`).

```powershell
mvn test "-Denv=dev" "-Duse.database=true" "-Dssh.enabled=false" "-Dsuite=inventory"
```

---

## 3. Ієрархія suite-ів

```
suites/
│
├── АГРЕГУЮЧІ (повний або широкий прогін)
│   ├── regression          ← повний регрес: API + RBAC + UI (усі домени)
│   ├── smoke               ← швидка перевірка після деплою
│   └── functional          ← API functional CRUD (паралельно, 3 потоки)
│
├── ДОМЕННІ (вузький фокус — API ± UI одного модуля)
│   ├── production          ← серійне виробництво
│   ├── non-series-production
│   ├── relocations         ← переміщення / видати-отримати
│   ├── inventory           ← інвентаризація (WMS)
│   ├── global-plans        ← глобальні плани
│   ├── technological-maps
│   ├── defects             ← брак
│   └── resource-viewer     ← read-only переглядач ресурсів
│
├── ІНФРАСТРУКТУРНІ / СПЕЦІАЛЬНІ
│   ├── rbac                ← матриця доступів (~79 сценаріїв)
│   ├── bots                ← SLA internal API для whatsapp/delivery bot
│   ├── ui                  ← UI smoke по кількох екранах
│   └── ui-dev              ← швидкий UI smoke (login/logout, accountant)
│
└── СЛУЖБОВІ
    └── dev-test            ← порожній suite (дефолт pom — не використовувати для прогону)
```

### 3.1. `regression` — повний регрес

**Коли:** nightly, перед релізом, після великих змін.

**Структура всередині suite (послідовно, `parallel=none`):**

| Блок | Класи | Що перевіряє |
|------|-------|--------------|
| Smoke | `LoginSmokeTest`, `BotInternalApiLatencyTest` | Логін + SLA ботів |
| Authentication | `AuthenticationTest` | Keycloak / токени |
| RBAC | `RbacAccessMatrixTest` | Права по `rbac-policy.yml` |
| Dictionary API | `MeasurementUnitTest`, `ResourceTest`, `ResourceAutocompleteTest`, `ResourceDeactivationTest`, `StorageTest` | Довідники |
| Resource Viewer API | `ResourceViewerRelocationSumTest` | API переглядача |
| Technological Maps API | `TechnologicalMapTest` | Техкарти |
| Production API | `ProductionTest`, `ProductionJournalFilterApiTest` | Виробництво |
| Non-Series Production API | `NonSeriesProductionTest` | Несерійне виробництво |
| Defects API | `DefectTest` | Брак |
| Relocation API | `RelocationTest`, `RelocationExtendedTest`, `RelocationJournalSortApiTest`, `EquipmentRelocationTest` | Переміщення |
| Inventory API | `InventorySessionApiTest`, `InventoryConductApiTest`, `InventoryStockApiTest` | Інвентаризація |
| Global Plans API | `GlobalPlanCrudApiTest`, `GlobalPlanDecompositionApiTest`, `GlobalPlanGenerationApiTest` | Плани |
| UI Auth | `LoginUITest`, `LoginLogoutUITest`, `AccountantCabinetUITest` | Вхід / вихід |
| UI Production | `ProductionUITest`, `ProductionJournalFilterUITest`, `OperationHistoryUiTest` | UI виробництва |
| UI Non-Series | `NonSeriesProductionUITest` | UI несерійного |
| UI Resources | `ResourceAutocompleteUiTest` | Автокомпліт ресурсів |
| UI Relocation | `RelocationUITest`, `EquipmentRelocationUITest` | UI переміщень |
| UI Inventory | `InventoryUiTest` | UI інвентаризації |
| UI Global Plans | `GlobalPlansUiTest`, `GlobalPlanCreateUiTest` | UI планів |

```powershell
mvn test "-Denv=dev" "-Dsuite=regression"
```

> **Примітка:** `RelocationJournalFilterSortUITest` є в `relocations` і `ui`, але не в `regression`.

---

### 3.2. `smoke` — швидкий smoke

**Коли:** після деплою, у PR pipeline.

| Тест | Призначення |
|------|-------------|
| `LoginSmokeTest` | Базовий логін |
| `BotInternalApiLatencyTest` | Відповідь internal API для ботів |

```powershell
mvn test "-Denv=dev" "-Dsuite=smoke"
mvn test "-Denv=staging" "-Dsuite=smoke"
```

---

### 3.3. `functional` — API CRUD (паралельно)

**Коли:** розробка API, швидкий functional прогін.

`parallel="classes"` · `thread-count="3"`

Словники + переміщення + глобальні плани (без RBAC, UI, production journal, inventory).

```powershell
mvn test "-Denv=dev" "-Dsuite=functional"
mvn test "-Denv=local" "-Duse.docker=true" "-Dsuite=functional"
```

---

### 3.4. Доменні suite-и

Кожен доменний suite — **мінімальний набір** для одного модуля SUT.

#### `production`

| Шар | Класи |
|-----|-------|
| API | `ProductionTest`, `ProductionJournalFilterApiTest` |
| UI | `ProductionUITest`, `ProductionJournalFilterUITest`, `OperationHistoryUiTest` |

#### `non-series-production`

| Шар | Класи |
|-----|-------|
| API | `NonSeriesProductionTest` |
| UI | `NonSeriesProductionUITest` |

#### `relocations`

| Шар | Класи |
|-----|-------|
| API | `RelocationTest`, `RelocationExtendedTest`, `RelocationJournalSortApiTest`, `EquipmentRelocationTest` |
| UI | `RelocationUITest`, `RelocationJournalFilterSortUITest`, `EquipmentRelocationUITest` |

#### `inventory`

| Шар | Класи |
|-----|-------|
| API | `InventorySessionApiTest`, `InventoryConductApiTest`, `InventoryStockApiTest` |
| UI | `InventoryUiTest` |

Покриття manual TC-WMS-003 / TC-WMS-007.

#### `global-plans`

| Шар | Класи |
|-----|-------|
| API | `GlobalPlanCrudApiTest`, `GlobalPlanDecompositionApiTest`, `GlobalPlanGenerationApiTest` |
| UI | `GlobalPlansUiTest`, `GlobalPlanCreateUiTest` |

#### `technological-maps`

| API | `TechnologicalMapTest` |

#### `defects`

| API | `DefectTest` |

#### `resource-viewer`

| API | `ResourceViewerRelocationSumTest` |

---

### 3.5. Інфраструктурні suite-и

#### `rbac`

YAML-матриця `src/test/resources/rbac-policy.yml` → `RbacAccessMatrixTest`.

```powershell
mvn test "-Denv=dev" "-Dsuite=rbac"
```

#### `bots`

SLA internal API (`/api/v1/internal/**`) для whatsapp-bot і delivery-bot.

```powershell
mvn test "-Denv=dev" "-Dsuite=bots"
mvn test "-Denv=staging" "-Dsuite=bots"
```

#### `ui`

UI smoke без повного регресу:

- Login, Resources autocomplete, Relocation (+ journal filter/sort), Global Plans

```powershell
mvn test "-Denv=dev" "-Dsuite=ui"
```

Потрібен Playwright Chromium: `mvn exec:exec@install-chromium` (один раз).

#### `ui-dev`

Мінімальний UI smoke для dev:

- `LoginLogoutUITest`, `AccountantCabinetUITest`

```powershell
mvn test "-Denv=dev" "-Dsuite=ui-dev"
```

---

## 4. Матриця «коли який suite»

| Сценарій | Suite | env |
|----------|-------|-----|
| PR / після деплою | `smoke` | dev / staging |
| Nightly повний прогін | `regression` | dev |
| Перед релізом на stage | `regression` або доменні | staging |
| Розробка одного модуля | доменний suite | dev |
| Перевірка прав доступу | `rbac` | dev |
| Моніторинг ботів | `bots` | dev / staging |
| UI smoke | `ui` або `ui-dev` | dev |
| Локальна розробка з Docker | `functional` | local + `use.docker=true` |
| DB assertions | будь-який + `use.database=true` | dev / staging (VPN) |

---

## 5. Типи тестів (архітектура)

```
BaseTest
├── BaseFunctionalTest     → API CRUD, контракти, бізнес-логіка
├── BaseRbacTest           → YAML-driven RBAC (RbacAccessMatrixTest)
└── BaseUITest             → Playwright UI (LoginPage, RelocationPage, …)
```

| Тип | Базовий клас | Автентифікація | Типовий suite |
|-----|--------------|----------------|---------------|
| API functional | `BaseFunctionalTest` | Session cookies / JWT | functional, доменні |
| RBAC | `BaseRbacTest` | Per-role tokens | rbac, regression |
| UI | `BaseUITest` | Playwright login | ui, ui-dev, regression |
| Integration | `BaseTest` | Bot OAuth2 client_credentials | bots, smoke |
| Auth | `BaseTest` | Keycloak password grant | regression |

---

## 6. Звітність

### Allure

Результати: `target/allure-results/`

```powershell
mvn allure:serve
```

У звіті відображається **Test Case ID** (`@TestCaseId`) — label, TMS-link, префікс `[TC-xxx]` у назві.

### Google Sheets

Listener `GoogleSheetsReportListener` — увімкнути в config:

```properties
google.sheets.enabled=true
```

### TCM (Test Case Management)

```properties
tcm.enabled=true
tcm.base.url=...
tcm.api.token=...
tcm.test.plan.id=...
```

---

## 7. Корисні параметри

| Параметр | Опис |
|----------|------|
| `-Duse.database=true` | Pre-flight перевірка JDBC перед suite |
| `-Dssh.enabled=false` | Прямий JDBC без SSH-тунелю |
| `-Dlogging.verbose=true` | Детальні HTTP-логи RestAssured |
| `-Dtest=ClassName#method` | Один тест |
| `-Dtest=ClassName` | Один клас |

---

## 8. Швидкий довідник команд

```powershell
# Smoke на dev
mvn test "-Denv=dev" "-Dsuite=smoke"

# Повний регрес на dev
mvn test "-Denv=dev" "-Dsuite=regression"

# Тільки переміщення (API + UI)
mvn test "-Denv=dev" "-Dsuite=relocations"

# RBAC-матриця
mvn test "-Denv=dev" "-Dsuite=rbac"

# UI smoke
mvn test "-Denv=dev" "-Dsuite=ui-dev"

# Боти на staging
mvn test "-Denv=staging" "-Dsuite=bots"

# Один API-тест
mvn test "-Denv=dev" "-Dtest=RelocationJournalSortApiTest"

# Allure
mvn test "-Denv=dev" "-Dsuite=smoke"; mvn allure:serve
```

---

## 9. Файли конфігурації

| Шлях | Призначення |
|------|-------------|
| `src/test/resources/suites/*.xml` | TestNG suite-и |
| `src/test/resources/config/{env}.properties` | URL, користувачі, БД |
| `src/test/resources/config/default.properties` | Fallback-значення |
| `src/test/resources/rbac-policy.yml` | RBAC-сценарії |
| `src/test/resources/schemas/` | JSON Schema для contract validation |
| `.env.dev` / `.env.staging` | Секрети ботів (не в git) |
