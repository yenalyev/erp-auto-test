ERP API Test Automation Framework
 
Data-Driven фреймворк для автоматизації тестування REST API, з фокусом на перевірці безпеки (RBAC) та контрактному тестуванні (Contract Testing).

Технологічний стек
Java 21
RestAssured — API клієнт.
TestNG — Test runner & Data Providers.
Allure Report — Детальна звітність.
Jackson — Серіалізація JSON/YAML.
Lombok — Зменшення бойлерплейту.


🏗 Архітектура: Key Concepts
Фреймворк побудований на принципі "Single Source of Truth" (Єдине джерело правди) 
та відокремленні тестових даних від логіки виконання.

1. ApiEndpointDefinition (Single Source of Truth)
   Всі метадані ендпоінтів зберігаються в одному Enum (ApiEndpointDefinition). 
Це включає:

- URL шлях та HTTP метод.
- Типи запитів та відповідей (Class objects).
- Шляхи до JSON-схем для валідації.
- Вимоги до наявності тіла запиту (Body) або параметрів шляху (Path Params).

2. RBAC Matrix (Data-Driven Testing)
   Тести доступу генеруються динамічно на основі файлу конфігурації 
src/test/resources/rbac-policy.yml.

Не потрібно писати окремий метод для кожного тесту.
Матриця автоматично створює позитивні (ALLOWED) та негативні 
(DENIED) сценарії для кожної ролі.

3. RequestBodyFactory (Strategy Pattern)
   Генерація тестових даних реалізована через патерн "Стратегія". 
Фабрика приймає ApiEndpointDefinition і автоматично підбирає потрібний білдер 
для створення валідного або невалідного тіла запиту.

4. Smart Validation & Reporting
   Contract Testing: Автоматична валідація JSON-схеми відповіді (якщо вказана в ApiEndpointDefinition).

Fallback Validation: Якщо схеми немає, застосовується базова евристична перевірка.

Allure: Звіти містять повну інформацію:

Request/Response body & headers.

Порівняння "Expected Schema vs Actual JSON".

Причини пропущених тестів (Skipped reasons).

🚀 Як додати новий тест
Опишіть ендпоінт: Додайте новий запис в ApiEndpointDefinition (вкажіть шлях, метод, DTO класи та схему).

Налаштуйте доступ: Додайте правило в rbac-policy.yml (хто має доступ, хто ні).

Додайте дані (Optional): Якщо це POST/PUT запит, зареєструйте стратегію генерації тіла в RequestBodyFactory.

Матриця генерується в `RbacAccessMatrixTest`, але наразі цей клас виключений із регулярних suite. Зміна YAML сама по собі не додає перевірку до регресії.

📦 Запуск тестів
За замовчуванням запускається `smoke` на `dev` — перевірки входу та Bot Internal API SLA:

```bash
mvn clean test
```

Повний підтримуваний набір API/UI-регресії:

```bash
mvn clean test -Denv=dev -Dsuite=regression
```

Тематичні набори обираються через `-Dsuite=<назва XML без розширення>`:

```bash
mvn test -Denv=dev -Dsuite=inventory
mvn test -Denv=dev -Dsuite=relocations
mvn test -Denv=dev -Dsuite=ui
```

Для разового запуску класу або методу використовуйте `-Dtest`, без створення нового XML:

```bash
mvn test -Denv=dev -Dtest=InventoryStockApiTest
mvn test -Denv=dev "-Dtest=InventoryStockApiTest#exportExcelWithAndWithoutZeroStock"
```

`-Dtest` перевизначає вибір suite; listeners із suite XML у такому запуску не підключаються. Для TCM/Google Sheets використовуйте підтримувану suite.

У [каталозі suite](src/test/resources/suites/) залишені основні й тематичні набори. Тимчасові `next-*`, `*-verify`, `*-rerun*`, набори конкретних прогонів, `ui-dev` та порожні `dev-test`/`rbac` видалено. Історія доступна в Git. Новий постійний XML додавайте для окремого повторюваного набору перевірок. Запуск без жодного тесту завершується помилкою.

Генерація звіту Allure:

```bash
mvn allure:serve
```

Перевірки самого фреймворку — локальні файли та HTTP-сервери `127.0.0.1`, без ERP, БД і браузерного входу:

```bash
mvn test -Dsuite=framework -Dtcm.enabled=false -Dgoogle.sheets.enabled=false
```

Набір `framework` перевіряє ізоляцію TCM-результатів, HTTP-діагностику та тайм-аути JSON/multipart. Він виконується послідовно, оскільки тести тимчасово змінюють системну конфігурацію й відновлюють її після завершення.

TCM outbox створюється для конкретного `remoteRunId`: без ID від runner кожна suite отримує новий UUID. Значення `-Dtcm.remote.run.id=...` зберігається. `-Dtcm.results.file=...` підтримується; runner повинен виділяти окремий шлях для кожного прогону. Кожному результату в JSONL передує службовий рядок із `remoteRunId`; listener читає лише результати поточного прогону. Runner пропускає службові рядки без `testCaseId`, тому формат результату для TCM не змінюється. Старі записи без ID listener автоматично не імпортує. Файл `tcm-import.ok` також містить `remoteRunId`.

Автоматичні HTTP-вкладення Allure, HTTP-логи та `RequestDiagnostics` маскують cookies, заголовки авторизації й відомі поля секретів у діагностичній копії. Запити, відповіді та їхня валідація використовують оригінальні значення. Для власних текстових вкладень із такими даними використовуйте `HttpSecretRedactor.redact(...)`.

📂 Структура проекту
src/main/java/com/erp/api/endpoints — Визначення API (Enum).

src/main/java/com/erp/data — Фабрики даних та завантажувачі YAML.

src/main/java/com/erp/models — DTO (POJO) класи.

src/main/java/com/erp/validators — Валідатори схем.

src/test/resources/schemas — JSON-схеми для контрактів.

src/test/resources/rbac-policy.yml — Матриця доступу.

### Cleanup та SKIP

- Усі успішні `STORAGE_POST_CREATE` / `STORAGE_REGION_POST_CREATE` / `ORDER_POST_CREATE` через `ApiExecutor` реєструють ID у `SessionClient` цього suite. Класи suite ділять реєстр; новий suite отримує новий реєстр. Імена, префікси та результати GET не підтверджують володіння даними.
- Cleanup складів, регіонів, prefix purge та очищення visibility/grants обмежені зареєстрованими об’єктами. Перед suite масового очищення немає. Після suite повторюється очищення залишків за ID, без сканування каталогу; невдалі спроби зберігаються для повтору. Успішні DELETE та unarchive оновлюють чергу.
- Дані попередніх JVM або створені в обхід `ApiExecutor` автоматично не видаляються. Відновлення після аварійного завершення потребує окремого механізму з доказом володіння. `-Dsuite.artifact.sweep=false` вимикає фінальний повтор; `-Dstaging.cleanup=false` залишає автоматично керовані артефакти на staging для діагностики.
- Очищення бронювань скасовує лише замовлення поточного suite; чужі `IN_PROGRESS` не змінюються, навіть на спільному складі.
- Fight/FAITA: `-Dfight.integration.enabled=false` / `-Dfaita.integration.enabled=false` явно дозволяють SKIP відповідних сценаріїв. Типове значення — `true`; HTTP 403/404/5xx, HTML замість JSON або помилка мережі є failure, а не визначенням «інтеграції немає».
- Ввімкнена БД повинна пройти pre-flight; помилка SSH/JDBC зриває setup. Якщо БД вимкнена, DB-only сценарії можуть бути SKIP. Відсутність необхідних довідникових даних залишається поясненим SKIP. Помилки підготовки запасів і відсутність UI-елементів, які перевіряє сценарій замовлення, є failure. TestNG може позначати залежні тести як SKIP після configuration failure; сам configuration failure робить Maven-запуск невдалим.
- RBAC-матриця відкладена до появи бізнес-вимог; її правила не змінювалися.

Локальні перевірки ізоляції cleanup і класифікації передумов входять у `mvn test -Dsuite=framework -Dtcm.enabled=false -Dgoogle.sheets.enabled=false`.
