# Аудит тестового фреймворку ERP

Дата: 11 вересня 2026. Об'єкт: робоча копія `erp-auto-test`. Код фреймворку не змінювався.

Фреймворк компілюється і має значний набір API/UI-сценаріїв, але поточний успішний запуск не є достатнім доказом перевіреної регресії. Виявлено 8 проблем: 5 високого пріоритету P1 і 3 середнього P2. P1 — виправити перед використанням результатів як обов'язкового критерію випуску; P2 — виправити наступним кроком.

## Обсяг і метод

Переглянуто конфігурацію Maven/TestNG, базові API/UI-класи, клієнти, сесії, validators, fixtures/cleanup, listeners TCM/Allure, RBAC і вибрані тести. Машинно перевірено всі 152 suite XML на посилання на класи та явні method include. Це аудит ядра та вибірки сценаріїв, а не ручний розбір усіх тестових методів.

Статичний інвентар: 461 Java-файл у src/main, 233 у src/test; 1168 текстових входжень @Test, 52 конструкції throw new SkipException, 89 входжень dependsOnMethods. Ці числа не є кількістю виконаних тестів, бізнес-покриттям або частотою flaky. regression.xml містить 212 активних class-посилань у 28 test-блоках.

## Перевірки

- Чиста компіляція 461 main і 233 test Java-файлів пройшла на наявній JDK 25.0.1 з target 21. Створено ізольовану копію POM у target/framework-audit-20260911: змінено лише шляхи source/resources/build/suite для відокремлення результатів; зовнішню звітність вимкнено.
- Налаштування default suite відтворили `Tests run: 0, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Локальний Java probe на 127.0.0.1 відтворив витік синтетичної cookie у Allure, імпорт старого TCM PASS новою порожньою suite та відсутність api.timeout на multipart POST. Перевірено, що ім'я свіжого активного прогону відповідає orphan-предикату.
- Реальні ERP, Keycloak, БД, зовнішні TCM і Google Sheets не викликалися. Повна API/UI-регресія не запускалась; її pass rate, тривалість і flaky rate цим аудитом не встановлені. Реальне конкурентне видалення даних не виконувалося.

## Знахідки

### F1 · P1 · Запуск за замовчуванням успішний без жодного тесту

**Доказ:** Відтворено. pom.xml обирає suite=dev-test, а dev-test.xml не містить жодного <test>. В ізольованій копії POM із тими самими налаштуваннями Surefire отримано Tests run: 0 і BUILD SUCCESS. README називає mvn clean test запуском усіх тестів; наведений параметр -DsuiteXmlFile не використовується цим POM.

**Наслідок:** CI або локальний запуск може виглядати успішним, хоча перевірок не було.

**Виправлення:** Обрати явну непорожню suite за замовчуванням або вимагати вибір suite. Додати failIfNoTests=true й перевірку очікуваної кількості тестів. Виправити README на -Dsuite=smoke / -Dsuite=regression.

**Критерій приймання:** Без suite команда запускає задокументований набір; порожня suite завершує Maven ненульовим кодом.

Код: [pom.xml:18](C:/Users/gigam/IdeaProjects/erp-auto-test/pom.xml:18), [pom.xml:335](C:/Users/gigam/IdeaProjects/erp-auto-test/pom.xml:335), [dev-test.xml:2](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/dev-test.xml:2), [README.md:67](C:/Users/gigam/IdeaProjects/erp-auto-test/README.md:67).

### F2 · P1 · Порожній новий прогін TCM імпортує старий PASS

**Доказ:** Відтворено на localhost. Без tcm.remote.run.id outbox завжди пишеться в tmp/tcm-autotest/local/tcm-results.jsonl у режимі APPEND. Listener генерує новий remoteRunId лише у своєму полі. Якщо поточний буфер порожній, onFinish читає весь старий outbox. Локальний probe записав PASS із датою 2020-01-01, створив нову порожню suite і зафіксував імпорт цього результату під новим run ID.

**Наслідок:** TCM може показати результат попереднього запуску як результат поточного; перевірка порожнього scope також обходиться, якщо знайдено старий outbox.

**Виправлення:** Створювати єдиний run ID до ініціалізації listener/outbox; прив'язати файли, записи й import-ok до нього. Читати лише поточний run; recovery минулих запусків зробити окремою явною операцією.

**Критерій приймання:** Два послідовні локальні запуски мають різні outbox. Другий порожній прогін не імпортує результати першого.

Код: [TcmReportListener.java:37](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/listeners/TcmReportListener.java:37), [TcmReportListener.java:49](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/listeners/TcmReportListener.java:49), [TcmResultOutbox.java:36](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/helpers/TcmResultOutbox.java:36), [TcmResultOutbox.java:61](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/helpers/TcmResultOutbox.java:61).

### F3 · P1 · Сесійна cookie потрапляє у вкладення Allure

**Доказ:** Відтворено на localhost. BaseClient і multipart-методи підключають стандартний AllureRestAssured. RequestDiagnostics маскує Cookies лише у власній текстовій копії. При logging.verbose=false локальний HTTP-запит із синтетичною SESSION-cookie залишив її точне значення у файлі Allure-вкладення; у RequestDiagnostics значення було приховане.

**Наслідок:** Доступ до артефактів звіту може давати доступ до чинної ERP-сесії. Відтворення використовувало лише вигадану cookie, реальні секрети не перевірялися.

**Виправлення:** Запровадити маскування request/response вкладень Allure та HTTP-логів для Cookie, Set-Cookie, Authorization й токенів. Маскувати копію для звіту, не змінюючи реальний HTTP-запит.

**Критерій приймання:** Синтетичні секрети доходять до localhost-сервера, але відсутні у всіх звітах і логах при обох режимах verbose.

Код: [BaseClient.java:53](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/BaseClient.java:53), [SessionClient.java:102](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/SessionClient.java:102), [RequestDiagnostics.java:66](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/RequestDiagnostics.java:66).

### F4 · P1 · Orphan cleanup може знищувати дані іншого активного прогону

**Доказ:** Шлях коду + локальна перевірка предиката. BeforeSuite видаляє всі тестові регіони, AfterSuite також архівує тестові локації. Відбір використовує загальний -loc_ / unique-suffix, без run ID, перевірки віку чи активності власника. archiveStorage спочатку обнуляє залишки. Свіже ім'я іншого активного прогону проходить той самий предикат orphan sweep.

**Наслідок:** Два незалежні JVM-прогони на одному середовищі можуть видаляти регіони та обнуляти запаси один одного. parallel=none захищає лише порядок усередині окремої suite.

**Виправлення:** Очищати поточні ресурси за зареєстрованими ID і run ID. Окремий orphan collector має перевіряти TTL та завершення/відсутність активного власника. До цього вимикати глобальний sweep для конкурентних прогонів.

**Критерій приймання:** Два одночасні прогони створюють різні ресурси; cleanup одного не змінює регіони, локації й залишки другого.

Код: [BaseTest.java:130](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/java/com/erp/tests/BaseTest.java:130), [TestArtifactCleanup.java:68](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/fixtures/TestArtifactCleanup.java:68), [StorageDataFactory.java:56](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/data/factories/storage/StorageDataFactory.java:56), [StorageFixture.java:344](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/fixtures/StorageFixture.java:344).

### F5 · P1 · Загальна RBAC-матриця вимкнена

**Доказ:** Перевірено XML без коментарів. У rbac.xml клас RbacAccessMatrixTest закоментовано, а regression.xml явно виключає матрицю. XML-перевірка бачить нуль активних класів у rbac suite. README досі описує автоматичну генерацію позитивних і негативних перевірок із rbac-policy.yml.

**Наслідок:** Зміни YAML-політики не запускають загальну матрицю в регресії. Окремі рольові тести в репозиторії існують, але вони не замінюють весь заявлений набір.

**Виправлення:** Повернути стабільну частину матриці в обов'язковий запуск; проблемні сценарії ізолювати адресно з причиною і власником. Перевіряти, що обов'язкова RBAC suite має ненульовий набір.

**Критерій приймання:** Зміна дозволу для конкретної ролі створює/запускає відповідний сценарій; навмисний RBAC-дефект робить перевірку червоною.

Код: [rbac.xml:18](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/rbac.xml:18), [regression.xml:378](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/regression.xml:378), [RbacAccessMatrixTest.java:71](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/java/com/erp/tests/rbac/RbacAccessMatrixTest.java:71).

### F6 · P2 · HTTP-помилка може бути класифікована як вимкнена інтеграція

**Доказ:** Перевірено шлях коду. CrewWriteOffTest визначає fightIntegrationEnabled через stats.statusCode()==200. Будь-яка інша відповідь, включно з 500, 401 або несподіваним 403, переводить TC-CREW-FIGHT-001 у SkipException із поясненням Fight sync disabled.

**Наслідок:** Регресія API або доступів стає пропуском перевірки й втрачає діагностичний сигнал. Це конкретний проблемний випадок; не всі 52 throw new SkipException у тестах є дефектами.

**Виправлення:** Визначати доступність необов'язкової інтеграції конфігурацією чи явним capability-контрактом. На ввімкненому середовищі несподівані HTTP-коди мають бути помилкою.

**Критерій приймання:** 500 і несподіваний 403 у probe спричиняють FAIL; лише явно вимкнена інтеграція дає задокументований SKIP.

Код: [CrewWriteOffTest.java:62](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/java/com/erp/tests/functional/storage/CrewWriteOffTest.java:62), [CrewWriteOffTest.java:102](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/java/com/erp/tests/functional/storage/CrewWriteOffTest.java:102).

### F7 · P2 · Multipart-запити ігнорують api.timeout

**Доказ:** Відтворено на localhost. Звичайні запити успадковують HTTP-тайм-аути із BaseClient.requestSpec. Multipart POST/PUT/file POST створюють given() заново і не додають цю конфігурацію. При api.timeout=1 і локальній відповіді із затримкою 1,5 с звичайний запит завершився тайм-аутом, multipart успішно відповів приблизно через 2 с.

**Наслідок:** Однаковий параметр запуску по-різному обмежує API-запити; повільний upload/receive може суттєво затримувати послідовну suite.

**Виправлення:** Винести спільну HTTP-конфігурацію в окремий builder і застосовувати її до JSON та всіх multipart-запитів без перенесення JSON Content-Type на multipart.

**Критерій приймання:** Однаковий локальний delayed endpoint завершує JSON і multipart-запити за узгодженим timeout; окремо перевірити multipart PUT та file POST.

Код: [BaseClient.java:40](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/BaseClient.java:40), [SessionClient.java:97](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/SessionClient.java:97), [SessionClient.java:121](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/api/clients/SessionClient.java:121).

### F8 · P2 · Дві rerun-suite посилаються на перейменований метод

**Доказ:** Перевірено XML і класи. dev-remain-24.xml і run85-fail-skip-rerun.xml включають neededTabDisabledForPastMonth. У PlanNeededResourcesUiTest тепер існує neededCalculationUnavailableForPastMonth; старого імені немає також у базових класах. Скан 152 XML-файлів не знайшов відсутніх класів, але знайшов ці два застарілі method include.

**Наслідок:** Ці повторні прогони не вибирають актуальну перевірку минулого місяця. Основна regression suite включає клас цілком, тому цей дефект стосується саме rerun-наборів.

**Виправлення:** Оновити два method include. Додати локальну перевірку suite-посилань, включно з успадкуванням і допустимими regex, перед виконанням.

**Критерій приймання:** У двох наборах вибирається потрібний метод; будь-яке наступне перейменування з неоновленим include виявляється до запуску ERP-тестів.

Код: [dev-remain-24.xml:366](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/dev-remain-24.xml:366), [run85-fail-skip-rerun.xml:474](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/run85-fail-skip-rerun.xml:474), [PlanNeededResourcesUiTest.java:131](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/java/com/erp/tests/ui/PlanNeededResourcesUiTest.java:131).

## Що вже працює добре

Є окремі шари API endpoint metadata, клієнтів, DTO, fixtures, assertions і Page Objects. Локальні ізольовані owner-scope fixtures дають основу для поліпшення ізоляції. JSON-schema validator накопичує помилки й кидає AssertionError, а не просто логує їх. TCM outbox забезпечує збереження результатів при проблемах доставки; його потрібно правильно прив'язати до прогону. RequestDiagnostics уже маскує cookies у своїй копії — цю практику слід поширити на всі артефакти.

## Порядок робіт

1. F1–F3: прибрати порожній зелений запуск, ізолювати outbox, закрити витік cookies. Для кожного залишити автономний тест без ERP.
2. F4–F5: зробити cleanup безпечним для одночасних запусків і повернути обов'язкове RBAC-покриття.
3. F6–F8: уточнити політику skip, вирівняти HTTP timeout, валідовувати suite-посилання.
4. Після виправлень провести два послідовні та два одночасні прогони на окремому тестовому середовищі: порівняти набір test ID, FAIL/SKIP, залишені ресурси та відсутність взаємного впливу.

Додаткові спостереження, без окремого пріоритетного дефекту: у відстежуваних файлах цього репозиторію немає Maven Wrapper та конфігурації GitHub Actions/Jenkins (зовнішній runner може існувати); `source/target=21` не обмежує доступні API новішої JDK, тому доцільно перевіряти JDK 21 і використовувати `release=21`. Чиста компіляція на JDK 25 не доводить виконуваність на JDK 21. Оцінка актуальності залежностей/CVE не проводилася.

## Артефакти відтворення

- [Лог чистої збірки й порожнього запуску](C:/Users/gigam/IdeaProjects/erp-auto-test/target/framework-audit-20260911/default-run.log)
- [Лог локальних probes](C:/Users/gigam/IdeaProjects/erp-auto-test/target/framework-audit-20260911/probe.log)
- [Код локального probe](C:/Users/gigam/IdeaProjects/erp-auto-test/target/framework-audit-20260911/AuditProbe.java)
- [Статичні метрики](C:/Users/gigam/IdeaProjects/erp-auto-test/target/framework-audit-20260911/static-metrics.json)
- [Перевірка suite-посилань](C:/Users/gigam/IdeaProjects/erp-auto-test/target/framework-audit-20260911/suite-reference-check.txt)
- [Ізольований POM](C:/Users/gigam/IdeaProjects/erp-auto-test/target/framework-audit-20260911/audit-pom.xml)
