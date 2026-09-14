# Аудит тестової інфраструктури ERP — 11.09.2026

Перевірено три локальні репозиторії, працюючі Docker-контейнери, каталог automation links локальної TCM та вибрані історичні прогони через локальний і prod MCP. Основний висновок: інфраструктура вже має робочий наскрізний запуск і fallback-доставку, але її поточні статуси та звіти недостатньо надійні для автоматичного release gate. Виявлено 16 пріоритетних проблем: 8 P1 і 8 P2. P1 — виправити першочергово; P2 — наступний етап. Це пріоритет робіт, не CVSS-оцінка.

## Межі та метод

Статичний аналіз orchestration, auth/config, suite selection, TestNG listeners, JSONL outbox, імпорту/агрегації TCM, fixtures, Docker/deployment і звітів. Повністю скомпільовано три проєкти. Запущено всі 7 наявних тестів runner, 39 вибраних unit-тестів інтеграційної частини TCM та 3 локальні unit-тести автотестів: усі 49 пройшли. Чотири додані діагностичні тести в тимчасових копіях падають на правильних очікуваннях і відтворюють дефекти A05/A06/A08/A12; окремий probe відтворив A11. Порожній default-suite реально дав 0 tests + BUILD SUCCESS.

Бізнесові автотести ERP не запускалися: аудит використовує існуючу історію прогонів, оскільки regression створює й очищує shared-дані. Дані TCM не змінювалися. Не виконувалися penetration test, перевірка публічного ingress/firewall, restore backup, CVE-сканування залежностей або звірка deploy image з git SHA. Prod MCP — окреме джерело; його дані не змішуються з локальною БД. Перевірені selected unit-тести не є повним тестуванням TCM UI/RBAC/DB. Чутливі значення не включені у звіт.

## Архітектура, яка фактично існує

TCM MVC/UI → AutotestRunnerService/JobRegistry (jobs у PostgreSQL) → remote runner REST API → in-memory queue → Maven/TestNG → API через RestAssured + UI через Playwright + частина fixtures через JDBC/SSH → TcmReportListener → /api/autotest/runs → TestRun/TestRunItem. Паралельно TestNG пише progress.json та JSONL outbox. Якщо listener не залишив import marker, runner виконує fallback POST. Allure зберігається окремо у спільному target/allure-results. У TCM збережений альтернативний local execution path.

Сильні сторони: enum метаданих API, fixtures/context, явні automation IDs; whitelist env/suite і token-auth у runner; послідовне виконання; timeout/retry у клієнтах доставки; JSONL outbox; remoteRunId з унікальним DB-індексом; scope filtering та агрегація найгіршого результату; Flyway, non-root TCM image, PostgreSQL healthcheck; TCM jobs відновлюються після restart самого TCM. Ці механізми корисні, але крайові сценарії нижче порушують їхні гарантії.

## Масштаб та фактичний стан

- erp-auto-test: 461 main Java-файл, 233 test Java-файли, 152 suite XML, 1148 унікальних annotation IDs. 1168 входжень @Test — статичний показник, не число runtime-викликів. 24 ID повторюються між методами/API/UI; це потребує явної політики, не є автоматично дублем-дефектом. У всіх XML перевірені class references — відсутніх класів не знайдено.

- TCM: 230 main Java-файлів, 40 test Java-файлів; локальна БД projectId=1 має 1154 test cases та 1112 automation links. Статична звірка враховує FQN @TestCaseId та локальні String-константи, нормалізує trim/uppercase; не доводить, що всі знайдені методи виконуються в regression.

- Runner: 20 main Java-файлів і 3 test Java-файли; 7 наявних тестів. Запущений контейнер має JDK 21.0.5, ліміт пам’яті 4 GiB; локальна валідація проводилася на JDK 21.0.9. TCM і runner не мають Docker healthcheck; PostgreSQL healthy. На момент перевірки TCM RestartCount=13; у хвості логів траплялися DNS/Flyway/BeanCreation exceptions — причина restart не доведена.

- Артефакти runner: 15G Allure, 16347 result.json, 56 каталогів run logs (~11M). Показники з контейнера на момент аудиту, не оцінка приросту за день.

- TCM local connector, run 88, staging, AUTO TP REQ-PLN: 115 results; PASS 77, FAIL 6, SKIPPED 32. Статус COMPLETED описує завершення, а не успішність усіх тестів.

- TCM local connector, run 85, staging, AUTO TP CONSOLE: 1087 results; PASS 858, FAIL 96, SKIPPED 133. Статус COMPLETED описує завершення, а не успішність усіх тестів.

- TCM prod connector, run 92, dev, AUTO TP REQ-PLN: 107 results; PASS 83, FAIL 23, SKIPPED 1. Статус COMPLETED описує завершення, а не успішність усіх тестів.

Локальний run 85 — AUTO TP CONSOLE, 02.09.2026. Runs 88 local і 92 prod — scoped AUTO TP REQ-PLN; їх не можна трактувати як зменшення повної regression з 1087 до 115/107. PASS-rate не є метрикою requirement coverage.

## Знахідки

### A01 · P1 · У запущених контейнерах використовуються відомі стандартні токени

**Доказ.** Read-only перевірка Docker env підтвердила стандартні значення TCM_AI_TOKEN, RUNNER_API_TOKEN і TCM_API_TOKEN. Порти TCM 8100, runner 8103 і PostgreSQL 5433 слухають 0.0.0.0 та IPv6. TCM також отримує стандартний bootstrap-пароль; це не доводить, що пароль існуючого адміністратора не змінено.

**Наслідок.** Клієнт із мережевим доступом і відомим токеном може користуватися відповідним API. Доступність із публічного інтернету та правила host firewall не перевірялися.

**Виправлення.** Замінити токени узгоджено в TCM, runner і MCP; прибрати стандартні значення з deployment-конфігурацій, вимагати явні секрети. Обмежити listen/network-доступ потрібними клієнтами; окремо перевірити bootstrap-акаунт.

**Критерій приймання.** Запуск без секретів має завершуватися помилкою конфігурації; старий токен має отримувати 401.

**Код:** [docker-compose.yml:16](C:/Users/gigam/IdeaProjects/erp-test-runner/docker-compose.yml:16), [docker-compose.yml:36](C:/Users/gigam/IdeaProjects/tcm/docker-compose.yml:36).

### A02 · P1 · Типовий запуск Maven успішний із нульовою кількістю тестів

**Доказ.** pom.xml обирає suite=dev-test, а dev-test.xml не містить test/classes. Контрольний запуск із вимкненим зовнішнім звітуванням дав Tests run: 0 та BUILD SUCCESS. README називає mvn clean test запуском усіх тестів і пропонує параметр suiteXmlFile, який не підставляється в заданий шлях POM.

**Наслідок.** Локальна або майбутня CI-перевірка може виглядати успішною без жодного тесту.

**Виправлення.** Обрати підтримуваний непорожній suite за замовчуванням, увімкнути failIfNoTests та виправити README на -Dsuite=<name>. Зробити перевірку XML-каталогу частиною build.

**Критерій приймання.** Порожній suite завершується ненульовим exit code; задокументована команда справді запускає очікувані тести.

**Код:** [pom.xml:18](C:/Users/gigam/IdeaProjects/erp-auto-test/pom.xml:18), [dev-test.xml:2](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/dev-test.xml:2), [pom.xml:335](C:/Users/gigam/IdeaProjects/erp-auto-test/pom.xml:335).

### A03 · P1 · Черга та статуси запусків губляться після restart ранера

**Доказ.** RunRepository зберігає записи у ConcurrentHashMap, RunQueueService — у LinkedBlockingQueue. Відновлення із disk/DB відсутнє. TCM має збереження job у БД та rehydration, але воно не відновлює втрачені записи самого ранера.

**Наслідок.** Після restart queued-запуски втрачаються; TCM не може отримати статус попереднього remoteRunId. Незавершені outbox залишаються без автоматичного повторного відправлення.

**Виправлення.** Зберігати queue/run state у БД або журналі; на startup переводити перервані jobs у явний стан і відновлювати queue/outbox. Додати idempotency key для запиту запуску.

**Критерій приймання.** Перезапустити окремий тестовий runner з queued та running jobs: жоден job не зникає, повторний submit не створює дубль.

**Код:** [RunRepository.java:14](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/repository/RunRepository.java:14), [RunQueueService.java:31](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/RunQueueService.java:31), [AutotestJobRehydrator.java:26](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestJobRehydrator.java:26).

### A04 · P1 · Завислий Maven блокує єдиний worker без обмеження часу

**Доказ.** MavenExecutor використовує process.waitFor() без timeout; чергу обробляє один worker. HTTP-таймаути окремих клієнтів не обмежують компіляцію, plugin, teardown або весь процес. Такий самий необмежений wait є у local-режимі TCM.

**Наслідок.** Один завислий прогін утримує всю чергу до ручного скасування. Queue не має обмеження місткості.

**Виправлення.** Додати configurable run deadline, watchdog за heartbeat/progress, kill дерева процесів та стан TIMED_OUT; обмежити queue і відхиляти надлишкові запити.

**Критерій приймання.** Синтетичний завислий процес зупиняється після deadline; наступний queued job починається автоматично.

**Код:** [MavenExecutor.java:81](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:81), [RunQueueService.java:147](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/RunQueueService.java:147), [AutotestRunnerService.java:447](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestRunnerService.java:447).

### A05 · P1 · Скасування під час preflight губиться, а процес усе одно запускається

**Доказ.** cancel() встановлює CANCELLING, коли Process ще null. Після preflight execute() без перевірки cancellation запускає процес і переписує статус на RUNNING. Ізольований тест отримав SUCCESS замість очікуваного CANCELLED.

**Наслідок.** Користувач скасовує прогін, але тести можуть почати змінювати ERP. Це відтворено на бездіяльному локальному cmd-процесі, без виклику ERP.

**Виправлення.** Запровадити атомарні переходи стану/cancellation token; перевіряти його після кожної фази та перед process.start(), не перезаписувати terminal/cancelling state.

**Критерій приймання.** Скасування у QUEUED, PREFLIGHT, MAVEN_START і RUNNING має припиняти запуск та завершувати job як CANCELLED.

**Код:** [MavenExecutor.java:58](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:58), [MavenExecutor.java:75](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:75), [MavenExecutor.java:159](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:159).

### A06 · P1 · Успішність імпорту визначається недостатньо надійними ознаками

**Доказ.** Runner ставить SUCCESS за exitCode=0 навіть коли ensureImported=false — це відтворено. Fallback приймає будь-який 2xx, listener пише OK-marker навіть при matched=0. TCM створює TestRun в REQUIRES_NEW перед імпортом items; findResultRun() та syncRemoteJob() вважають саму наявність рядка достатньою для completed.

**Наслідок.** Статуси runner, job і результатів можуть розійтися. При відкаті транзакції імпорту може лишитися IN_PROGRESS-запис, який UI прийме за імпортований прогін. Звичайний випадок повної відсутності TestRun TCM частково захищає через WAITING_IMPORT/grace period.

**Виправлення.** Розділити executionStatus та importStatus; підтверджувати commit, correlation ID, matched/expected і перелік unmatched. Вимагати успішний стан імпорту, а не лише існування TestRun чи marker. Помилку доставки не маскувати SUCCESS.

**Критерій приймання.** Перевірити 2xx+matched=0, timeout після commit, rollback після створення run, частковий import і повторну доставку. Не дублювати run/items.

**Код:** [MavenExecutor.java:114](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:114), [TcmFallbackImportService.java:147](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/TcmFallbackImportService.java:147), [AutotestRunResolver.java:34](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestRunResolver.java:34), [AutotestRunnerService.java:375](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestRunnerService.java:375), [AutotestRunnerService.java:579](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestRunnerService.java:579).

### A07 · P1 · Allure-вкладення містять незамасковані session cookies

**Доказ.** У 10 із перших 20 HTML-вкладень у контейнері знайдено JSESSIONID із непорожнім незамаскованим значенням. Перевірка виводила тільки кількість; cookies не копіювалися до звіту. BaseClient і multipart-клієнт підключають стандартний AllureRestAssured. Redaction у RequestDiagnostics стосується іншої копії діагностики.

**Наслідок.** Особа з доступом до артефактів може прочитати облікові дані сесії. Чинність історичних cookies і можливість повторного використання не перевірялися.

**Виправлення.** Єдина санітизація Authorization, Cookie, Set-Cookie і секретних полів перед усіма log/Allure sinks; приватний доступ і retention для звітів. Перевірити збережені артефакти та відкликати сесії за потреби.

**Критерій приймання.** Синтетичні token/cookie/password ніколи не потрапляють до Allure, Maven log або errorMessage, що надсилається до TCM.

**Код:** [BaseClient.java:53](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/BaseClient.java:53), [SessionClient.java:102](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/SessionClient.java:102), [RequestDiagnostics.java:63](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/RequestDiagnostics.java:63).

### A08 · P2 · TCM втрачає причини SKIPPED під час агрегації

**Доказ.** Listener передає skipReason, але AutotestStatusAggregator копіює errorMessage лише для FAIL/BLOCKED. Діагностичний unit-тест очікував JDBC unavailable, отримав null. У локальних run 85 і 88 відповідно всі 133 та 32 SKIPPED не мають actualResult/comment; у prod connector run 92 — 1 із 1.

**Наслідок.** У TCM неможливо відрізнити відсутні дані/права, проблеми JDBC, недоступний сервіс та пропуск через залежність. Звіт приховує причину неповного покриття.

**Виправлення.** Зберігати причину для SKIPPED/NOT_RUN і визначити детерміноване об’єднання кількох причин; додати категорію infrastructure/data/product/dependency.

**Критерій приймання.** Після агрегації та import повідомлення SKIPPED доступне через API, UI та export.

**Код:** [AutotestStatusAggregator.java:70](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/util/AutotestStatusAggregator.java:70), [TcmReportListener.java:108](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/listeners/TcmReportListener.java:108).

### A09 · P2 · Каталог automation ID у TCM розходиться з кодом

**Доказ.** Локальна БД: 1154 test cases, 1112 унікальних links. Код: 1148 унікальних @TestCaseId з урахуванням FQN-анотацій та локальних String-констант. Після trim+uppercase: 38 ID без явного link, 11 без link і без testId, 2 links без ID у коді: TC-UI-CREW-011 та TC-UI-HIST-EQ-007. Повні списки — coverage-comparison.json.

**Наслідок.** Feature/AC scope збирається тільки з automation links, тому частина наявних тестів не потрапляє до scoped-запусків. Import має fallback за testId, тож відсутність link не означає автоматично unmatched для всіх 38.

**Виправлення.** Узгодити links і код; затвердити alias-політику. Генерувати каталог із compiled annotations і перевіряти двобічну звірку в CI. Окремо перевіряти членство ID у підтримуваних suites.

**Критерій приймання.** Немає непояснених відсутніх/застарілих ID; кожен automation link має виконуваний метод і підтримуваний suite.

**Код:** [AutotestSuiteService.java:176](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestSuiteService.java:176), [TcmScopeListener.java:40](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/listeners/TcmScopeListener.java:40), [TestCaseAutomationService.java:67](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/TestCaseAutomationService.java:67).

### A10 · P2 · Allure-результати різних прогонів накопичуються в спільному каталозі

**Доказ.** У контейнері target/allure-results займає 15G за du -sh і містить 16347 result.json. Runner викликає mvn test без clean, видаляє лише TEST-TestSuite.xml. Allure пишеться в сталий target/allure-results; parser читає всі result.json. Run logs мають 56 каталогів; автоматичного видалення артефактів не знайдено.

**Наслідок.** Генерація Allure з цього каталогу змішує історичні результати; диск поступово заповнюється, зростає обсяг потенційно чутливих даних. Це не доводить змішування JSONL імпорту remote runs: їхні outbox мають окремі runId.

**Виправлення.** Окремі allure-results/surefire/workdir на runId, immutable manifest і report URL. Додати retention та disk-space threshold, зберігати останні/невдалі прогони за визначеною політикою.

**Критерій приймання.** Два послідовні прогони мають непересічні каталоги; звіт другого не містить першого; retention видаляє тільки дозволені старі артефакти.

**Код:** [allure.properties:1](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/allure.properties:1), [MavenExecutor.java:199](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:199), [MavenExecutor.java:215](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:215), [AllureResultsParser.java:38](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/parser/AllureResultsParser.java:38).

### A11 · P2 · Outbox пошкоджується керівними символами й може повторно використати старі локальні результати

**Доказ.** Ручний escapeJson не екранує TAB та інші control characters. Виклик реального TcmResultOutbox.append зі синтетичним TAB дав JSON, який Jackson відхиляє з JsonParseException. Fallback припиняє читання всього файла при одному невалідному рядку. Без remoteRunId усі локальні запуски пишуть до tmp/tcm-autotest/local, файл доповнюється й не очищується; listener читає його при порожньому buffer.

**Наслідок.** Після падіння JVM/network fallback може втратити весь прогін через один рядок. Порожній локальний scoped-run може підхопити результати попереднього запуску.

**Виправлення.** Використати Jackson JSONL, унікальний runId також для локальних запусків і окремий outbox; обробляти обірваний останній рядок з явною ознакою partial; перевіряти marker за correlation ID.

**Критерій приймання.** Round-trip для TAB, CR/LF, backslash, Unicode; kill під час запису; порожній новий запуск не імпортує старі дані.

**Код:** [TcmResultOutbox.java:137](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/helpers/TcmResultOutbox.java:137), [TcmResultOutbox.java:36](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/helpers/TcmResultOutbox.java:36), [TcmReportListener.java:49](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/listeners/TcmReportListener.java:49), [TcmFallbackImportService.java:94](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/TcmFallbackImportService.java:94).

### A12 · P2 · Налаштування USE_DATABASE=false не передається до автотестів

**Доказ.** RunnerProperties має useDatabase, application.properties читає USE_DATABASE, але buildCommand безумовно додає -Duse.database=true. Діагностичний тест із props.setUseDatabase(false) це підтвердив. Окремі UI-класи мають власну поведінку DB initialization, тому прапорець не означає, що кожен UI-тест відкриє JDBC.

**Наслідок.** API-only запуск несподівано може вимагати доступу до БД; оператор не може керувати цією залежністю через документовану конфігурацію.

**Виправлення.** Передавати properties.isUseDatabase(); DB-dependent suites або cases оголосити явно й перевіряти preflight тільки для них.

**Критерій приймання.** У дочірньому JVM -Duse.database збігається з конфігурацією; non-DB suite працює без JDBC.

**Код:** [MavenExecutor.java:243](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/service/MavenExecutor.java:243), [application.properties:10](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/resources/application.properties:10).

### A13 · P2 · Глобальний cleanup може видалити дані іншого активного прогону

**Доказ.** Suite sweep до/після тестів шукає всі локації/регіони за загальним шаблоном імен і працює від ADMIN. Маркера ownership за runId/lease/age немає. Послідовна queue захищає лише один instance ранера; IDE, CLI або другий runner на тому самому ERP не координуються.

**Наслідок.** Паралельний локальний і remote-запуск можуть деактивувати/очистити дані один одного, спричинивши нестабільні результати. Масові cleanup-виклики також додають навантаження на auth/ERP.

**Виправлення.** Тегувати кожен ресурс runId; видаляти тільки власні tracked ID. Orphan sweep винести в окремий процес із TTL та перевіркою відсутності активного owner; застосувати lock на shared environment.

**Критерій приймання.** Два прогони в одному test environment не змінюють ресурси один одного; orphan sweep пропускає активний lease.

**Код:** [TestArtifactCleanup.java:68](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/fixtures/TestArtifactCleanup.java:68), [StorageFixture.java:198](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/fixtures/StorageFixture.java:198), [StorageDataFactory.java:56](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/data/factories/storage/StorageDataFactory.java:56).

### A14 · P1 · Scoped-запуск перестає фільтрувати тести, якщо звітування вимкнене

**Доказ.** TcmScopeListener.loadScopeIfNeeded() повертається при !isTcmReportingEnabled(). Ця умова спрацьовує також при порожньому API token або tcm.enabled=false. Потім intercept() повертає всі методи, якщо scope не активований, навіть коли CLI містить tcm.feature.id/ac.id.

**Наслідок.** Помилка конфігурації scoped-запуску може виконати весь обраний suite замість одного Feature/AC. Це особливо небезпечно для suite із записами в shared ERP. Сценарій підтверджений аналізом control flow, live-запуск не виконувався.

**Виправлення.** Відокремити selection scope від reporting; якщо scope явно заданий, але його неможливо завантажити, завершувати прогін до setup та API mutations.

**Критерій приймання.** featureId/acId + missing token або disabled reporting не запускає жодного тесту та повертає зрозумілу configuration error.

**Код:** [TcmScopeListener.java:55](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/listeners/TcmScopeListener.java:55), [TcmScopeListener.java:37](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/listeners/TcmScopeListener.java:37), [ConfigProvider.java:214](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/config/ConfigProvider.java:214).

### A15 · P2 · Час і версія TestRun не описують фактичне виконання тестів

**Доказ.** TestRun.startedAt заповнюється LocalDateTime.now() під час resolve/import; DTO durationMs агрегується, але не записується у TestRunItem. У локальному run 85 межі executedAt охоплюють приблизно 3 год 33 хв, тоді як startedAt→completedAt становить близько 66 с. У трьох перевірених runs version=null; runner не фіксує git SHA у RunRecord.

**Наслідок.** За TCM неможливо надійно оцінити тривалість прогону, порівняти performance або відтворити результат на точній ревізії. executedAt — час завершення окремих тестів, тому цей інтервал не є точною повною тривалістю.

**Виправлення.** Передавати actual started/finished timestamps з timezone, зберігати duration, test commit SHA, ERP build, JDK/browser/image digest та selected scope snapshot.

**Критерій приймання.** Run manifest дозволяє відновити точну команду/ревізію без секретів; TCM показує execution duration окремо від import duration.

**Код:** [AutotestRunResolver.java:57](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestRunResolver.java:57), [AutotestImportService.java:119](C:/Users/gigam/IdeaProjects/tcm/src/main/java/tcm/service/AutotestImportService.java:119), [RunRecord.java:13](C:/Users/gigam/IdeaProjects/erp-test-runner/src/main/java/com/erp/runner/model/RunRecord.java:13).

### A16 · P2 · Частина регресій перетворюється на SKIPPED, а RBAC matrix виключена з regression

**Доказ.** OrderListUiTest пропускає TC-ORD-UI-004, якщо не видно workspace selector або create button — тобто перевірюваний UI може зникнути, не спричинивши FAIL. regression.xml прямо зазначає, що RbacAccessMatrixTest parked. Є інші рольові тести, тому це не означає повну відсутність RBAC coverage.

**Наслідок.** Високий SKIPPED може приховувати регресії продукту. Завершений прогін або високий PASS не гарантує виконання критичних перевірок.

**Виправлення.** Розділити перевірку передумов і assertions продукту, вимагати FAIL для зниклого очікуваного UI. Для SKIPPED — причина, власник, термін і бюджет; повернути стабілізовану RBAC matrix до контрольованого suite.

**Критерій приймання.** Видалення очікуваного елемента дає FAIL; обов’язкові case ID мають результат, а перевищення skip budget блокує gate.

**Код:** [OrderListUiTest.java:61](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/java/com/erp/tests/ui/OrderListUiTest.java:61), [regression.xml:20](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/regression.xml:20).

## Експлуатаційні прогалини

У tracked-файлах усіх трьох репозиторіїв не знайдено GitHub/GitLab/Jenkins/Azure pipeline-конфігурацій. Обидва Dockerfile збирають application із пропуском тестів. Це доказ відсутності pipeline-as-code тут, а не доказ, що зовнішнього CI ніде немає. Потрібен обов’язковий build/unit/contract gate до image publication.

Не знайдено активного описаного backup/restore workflow для PostgreSQL та uploads із RPO/RTO і перевіркою відновлення. Наявність named volumes чи старої data-backup директорії не підтверджує відновлюваність. Запланувати restore drill у відокремлене середовище.

Preflight перевіряє переважно доступність HTTP-сторінок, приймає 4xx як reachable і не доводить валідність TCM API token, доступність потрібної JDBC-схеми або відповідність suite. Health runner безумовно повертає UP. Розділити liveness/readiness; додати показники queue age, heartbeat age, import failure, disk free та browser/process leaks.

Deployment опис містить різні host/container порти та auto-loaded dev override; base compose runner має named volume без автоматичного provisioning коду. Для відтворюваності потрібні explicit compose files, immutable versioned image/code artifact і єдиний runbook. У Python TCM helper scripts залежності та параметри запуску не винесені в підтримуваний reproducible package; частина сценаріїв використовує hardcoded local URL/project/default credentials. Не запускати архівні helpers як production workflow.

## Рекомендована послідовність робіт

1. **Спочатку усунути небезпечні та хибно успішні сценарії:** A01/A07 (токени та redaction), A02 (нуль тестів), A05 (cancel), A06 (import acknowledgement), A14 (scope). Результат етапу — відсутність неочікуваного повного запуску і явний статус помилки доставки.

2. **Забезпечити життєвий цикл запуску:** A03/A04 (persist/deadline), A11/A12 (outbox/config), A10 (per-run artifacts/retention), A13 (resource ownership). Додати сценарії restart/timeout/cancel/partial JSONL до інтеграційного стенда з fake ERP/TCM.

3. **Повернути довіру до аналітики:** A08/A09/A15/A16 (skip reasons, ID sync, manifest, assertion policy), CI contract tests, readiness/метрики та backup restore drill. Визначити явні критерії release gate: tests > 0, expected IDs accounted for, import committed, критичні FAIL/SKIPPED відсутні або погоджені винятки.

## Докази та відтворення

Усі зміни цього аудиту — звітні файли та тимчасові probes; production/test sources трьох вихідних репозиторіїв не виправлялися. На початку erp-auto-test уже мав зміни .idea/workspace.xml і untracked службові файли; вони не використовувалися як інструкції й не змінювалися. tcm та runner були clean. Перевірені ревізії:

- erp-auto-test: `d6006488cb1ec30a6b918ea169ccfe266f9222a5`

- tcm: `2b450989dea29180b282140bb4497e98c124f8cb`

- erp-test-runner: `b56ff10e32a5c167356934ce69e75466e750bd54`

Повні дані: [findings.json](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/findings.json), [coverage-comparison.json](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/coverage-comparison.json), [inventory.json](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/inventory.json), [run-summary.json](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/run-summary.json).

Зведення перевірок: [validation.txt](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/validation.txt). Відтворювані probes: [runner](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/RunnerInfrastructureAuditProbeTest.java), [TCM](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/TcmInfrastructureAuditProbeTest.java), [outbox](C:/Users/gigam/IdeaProjects/erp-auto-test/docs/audits/2026-09-11/OutboxProbe.java). Назви класів у Java probes залишені InfrastructureAuditProbeTest; для запуску копіювати у відповідні packages тестового checkout під оригінальним ім’ям. Probes навмисно перевіряють правильну поведінку та на поточному коді падають; це не нові невдачі штатних 49 тестів.

Maven-валідація: offline mode, JDK 21.0.9, наявний локальний Maven cache. TCM/runner виконувалися у target/infrastructure-audit/<repo>; autotest unit/default suite — у вихідному workspace з tcm.enabled=false і google.sheets.enabled=false. Повні локальні build logs і synthetic fixtures залишені в target/infrastructure-audit; вони не є частиною production workflow.
