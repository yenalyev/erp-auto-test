# Аудит фреймворку ERP — 26.09.2026

## Виправлення після аудиту

Внесено правки F1–F8; висновок F4 уточнено після перевірки RelocationFacade: AUTO_FINISHED залишається чинним для EXTERNAL, виправлено підбір тестового отримувача.

- F1: розділено фабрики нової зовнішньої партії та наявної партії з обов'язковим UUID; оновлено single/multi-batch send, edit та Resource Viewer сценарії. Неоднозначний пошук за назвою тепер завершується помилкою.
- F2: UUID зберігається в DTO браку, списанні, фабриках та JSON-схемах відповідей.
- F3: 403/404/5xx, non-JSON, відсутній/null `content` більше не стають нульовим залишком. Валідний `content: []` підтримується.
- F4: fixture вибирає активного EXTERNAL отримувача з ORDERS і RELOCATIONS. Для нього збережено AUTO_FINISHED без зайвого resolve.
- F5: API та UI класи виконання плану включено до regression.xml.
- F6: кнопка редагування вибирається через точний `title`, незалежно від порядку дій і наявності кнопки видалення.
- F7: UI export вимагає фактичний Download, перевіряє failure та зберігає справжнє ім'я файла. HTTP-body fallback прибрано.
- F8: перевіряється native numeric type кожного значення в іменованих стовпцях «Обсяг» та «Кількість записів»; точне очікуване значення порівнюється саме зі стовпцем «Обсяг».

**Валідація:** `framework` — 73 passed; `framework-browser` — 4 passed. Обидва запуски: 0 failures, 0 errors, 0 skipped; компіляція успішна. Браузерні перевірки використовують headless Chrome з підміною всіх запитів. E2E на розгорнутому ERP не запускався.

- [Лог framework](C:/Users/gigam/IdeaProjects/erp-auto-test/target/audit-fixes-20260926-run.log)
- [Лог framework-browser](C:/Users/gigam/IdeaProjects/erp-auto-test/target/audit-browser-fixes-20260926-run.log)
- Команди: `mvn -o test -Dsuite=framework -Dtcm.enabled=false -Dgoogle.sheets.enabled=false` та `mvn -o test -Dsuite=framework-browser -Dframework.browser.channel=chrome -Dtcm.enabled=false -Dgoogle.sheets.enabled=false`.

Нижче збережено матеріали первинного аудиту; F4 виправлено відповідно до повного ланцюжка backend.

Перевірено поточну робочу копію відносно HEAD `84317fd`, нові Batch Inspector файли, базові helper-и UI/API та локальні контракти frontend/backend. Джерела продукту: tk-ui HEAD `1017939`, tk HEAD `6ee5b5de`; це локальні checkout-и, не підтвердження версії розгорнутого dev. Скановано 65 Java-файлів Page Object/компонентів; детально звірено аналітику, Batch Inspector, проєктне виробництво та критичні helper-и плану/переміщень. Це цільовий аудит ризиків, не сертифікація всіх локаторів.

## Результат первинних перевірок

- Maven offline framework suite: **59 тестів, 58 успішних, 1 failure, 0 errors, 0 skipped**. Компіляція main/test пройшла.
- Команда: `mvn -o test -Dsuite=framework -Dtcm.enabled=false -Dgoogle.sheets.enabled=false -Dtest.output.directory=target/audit-framework-20260926`.
- Початковий sandbox-запуск не зміг прочитати Java security config; повторний дозволений запуск завершив тести. Використано встановлений JDK 25 з target 21.
- [Лог запуску](C:/Users/gigam/IdeaProjects/erp-auto-test/target/audit-framework-20260926-run.log).
- [Локальне відтворення DTO/Excel](C:/Users/gigam/IdeaProjects/erp-auto-test/target/audit-framework-20260926/probe.log): підтверджено три несумісності DTO/фабрики та хибне проходження перевірки XLSX.
- E2E на розгорнутому ERP не запускався. Висновки щодо backend/frontend базуються на локальному коді. Не перевірено runtime DOM усіх екранів, browser events або актуальність deployment.
- Під час первинного аудиту код фреймворку не змінювався; після запиту на виправлення внесено описані вище зміни.

## Знахідки

### F1 · P1 · Частина видач досі відправляє batchUuid=null

**Статус:** Підтверджено локальним відтворенням і кодом backend.

**Місце:** [src/main/java/com/erp/data/factories/relocation/RelocationDataFactory.java](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/data/factories/relocation/RelocationDataFactory.java:120).

Нова overload із UUID використовується в RelocationFixture, але старі usageWithBatch/buildSendWithBatch підставляють null. Їх досі викликають TC-REL-B03, TC-REL-B06 та ResourceViewerBomApiTest.sendExternalBatchWithDate. Backend відхиляє явний вибір партії без UUID; позитивні сценарії отримують 400.

**Рекомендація:** Передавати UUID отриманої/виробленої партії в усі send/edit сценарії. Розділити фабрики зовнішнього надходження та вибору вже наявної партії.

**Докази:** tk: RelocationValidator.java:663–665; RelocationExtendedTest.java:572–573,640; ResourceViewerBomApiTest.java:890. Probe: legacy send factory produces batchUuid=null.

### F2 · P1 · DTO браку й списання втратили сумісність із backend

**Статус:** Підтверджено локальним відтворенням і кодом backend.

**Місце:** [src/main/java/com/erp/models/common/DefectBatchItem.java](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/models/common/DefectBatchItem.java:22).

DefectBatchItem і DefectWriteOffBatch не мають batchUuid. Поле з відповіді відкидається через ignoreUnknown, а buildWriteOffForDefect копіює тільки batchNumber/amount. Валідація backend вимагає UUID для defectBatches та write-off batches.

**Рекомендація:** Додати UUID до обох DTO та прокинути його через DefectDataFactory.batch і buildWriteOffForDefect, зберігаючи UUID із початкової відповіді.

**Докази:** tk: DefectValidator.java:232–234,341–345; DefectWriteOffRequest.java:30. erp-auto-test: DefectDataFactory.java:109–113,149–152. Probe підтвердив відсутність поля в обох скомпільованих класах.

### F3 · P1 · Помилка отримання залишків перетворюється на нуль

**Статус:** Підтверджено статичним аналізом; наявний дефект.

**Місце:** [src/main/java/com/erp/utils/helpers/ProductionStockAssertions.java](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/helpers/ProductionStockAssertions.java:305).

parseMultiInventoryContent повертає порожній список для 403,404 та будь-якої non-JSON відповіді. capture потім записує 0 для кожного ресурсу. Якщо обидва знімки неуспішні, перевірка незмінності запасів може пройти як 0 == 0; HTML 500 також приховується.

**Рекомендація:** Перед парсингом вимагати успішний HTTP-статус і контракт JSON. Відсутність доступу обробляти як окрему передумову, а не як фактичний нульовий залишок.

**Докази:** ProductionStockAssertions.java:299–314 → capture:126 → StockSnapshot.amount:57.

### F4 · P2 · Неправильний підбір отримувача для AUTO_FINISHED — уточнення аудиту

**Статус:** Первинний висновок про скасування AUTO_FINISHED виправлено після перевірки facade-рівня.

**Місце:** [src/test/java/com/erp/tests/functional/relocation/RelocationExtendedTest.java](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/java/com/erp/tests/functional/relocation/RelocationExtendedTest.java:204).

RelocationService спочатку встановлює CREATED, але RelocationFacade.send змінює його на AUTO_FINISHED, якщо RelocationUtil.requiresDeliveryConfirmation повертає false. Це стосується EXTERNAL отримувачів, а також переходів CREW ↔ FLY_POINT. Очікування AUTO_FINISHED у зазначених тестах є правильним для EXTERNAL. Помилка була у resolveUnitStorageId: він вибирав першу локацію з ORDERS, не перевіряючи relation, active або RELOCATIONS, тому міг вибрати INTERNAL.

**Виправлення:** resolveUnitStorageId тепер вибирає active EXTERNAL з ORDERS і RELOCATIONS та перевіряє HTTP-відповідь. Збережено строгі очікування AUTO_FINISHED; з UI journal тесту прибрано непотрібний resolve для такого отримувача. INTERNAL сценарії продовжують використовувати CREATED → FINISHED.

**Докази:** tk: RelocationFacade.java:132–139; utils/RelocationUtil.java:21–29; erp-auto-test: RelocationFixture.resolveUnitStorageId і RelocationJournalFilterSortUITest.

### F5 · P2 · Два класи виконання плану не включені до regression

**Статус:** Підтверджено запуском: 59 тестів, 58 passed, 1 failed.

**Місце:** [src/test/resources/suites/regression.xml](C:/Users/gigam/IdeaProjects/erp-auto-test/src/test/resources/suites/regression.xml:140).

RegressionSuiteCompletenessTest виявив відсутність PlanExecutionImprovementApiTest і PlanExecutionImprovementUiTest. Вони є в окремому plan-execution-improvement.xml, але загальна регресія їх не запускає.

**Рекомендація:** Додати обидва класи у відповідні секції regression.xml і повторити framework suite.

**Докази:** target/audit-framework-20260926/surefire-reports/TEST-TestSuite.xml; target/audit-framework-20260926-run.log.

### F6 · P2 · Кнопка редагування визначається індексом і може бути видаленням

**Статус:** Умовний дефект, підтверджений структурою frontend.

**Місце:** [src/main/java/com/erp/pages/ProjectProductionListPage.java](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/pages/ProjectProductionListPage.java:83).

actions.nth(1) коректний для поточного порядку з правом canUpdate. Але frontend показує ресурси + видалення, якщо canDelete=true і canUpdate=false: count()==2 проходить перевірку, а метод відкриває діалог видалення замість edit. title="Редагувати" уже існує.

**Рекомендація:** Вибирати button[title='Редагувати'] або getByRole(BUTTON, exact name), scoped до точного рядка. Перевіряти, що право редагування та потрібна кнопка присутні.

**Докази:** tk-ui: ProjectProductionListPage.tsx:670–721, умови canUpdate/canDelete. Це не твердження про збій поточного OWNER_1 запуску.

### F7 · P2 · UI-тест може пройти без фактичного завантаження файла

**Статус:** Підтверджено шляхом виконання в коді; наявний helper, нові споживачі.

**Місце:** [src/main/java/com/erp/pages/ProductionAnalyticsPage.java](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/pages/ProductionAnalyticsPage.java:297).

Якщо download event не виник за 1,5 с, helper зберігає HTTP body сам і підставляє production-analytics.xlsx. Новий тест excelExportButtonDownloadsValidWorkbook таким чином не доводить, що користувач отримав файл: збій frontend після успішного fetch буде прихований.

**Рекомендація:** Для UI-контракту вимагати Download, його filename і відсутність failure. Аналіз HTTP body залишити окремою API-перевіркою та явно позначати fallback.

**Докази:** ProductionAnalyticsPage.java:284–299; ProductionAnalyticsUiTest.java:828–842.

### F8 · P2 · Перевірка числової кількості приймає сторонню числову клітинку

**Статус:** Підтверджено локальним XLSX-відтворенням.

**Місце:** [src/main/java/com/erp/utils/helpers/XlsxWorkbookReader.java](C:/Users/gigam/IdeaProjects/erp-auto-test/src/main/java/com/erp/utils/helpers/XlsxWorkbookReader.java:78).

hasNumericDataCell повертає true після першої NUMERIC клітинки будь-якого стовпця. На аркушах із «Кількість записів» тест пройде навіть тоді, коли «Обсяг» збережений текстом. Перевірка anySatisfy у assertUniqueWorkbookRow також шукає кількість серед усіх чисел рядка.

**Рекомендація:** Знаходити індекс «Обсяг» за заголовком і перевіряти тип та значення цієї клітинки в кожному потрібному рядку. Окремо перевіряти «Кількість записів».

**Докази:** ProductionAnalyticsUiTest.java:1170–1176,1451–1453. Probe: numeric count=1 + text amount=WRONG TEXT AMOUNT → hasNumericDataCell=true.


## Селектори, звірені з frontend

- `production-analytics-export-excel` і `production-input-raw-resources-checkbox` наявні в ProductionBreakdownPanel.tsx. Використання test id відповідає поточному коду.
- BatchInspectorPage: `aria-label="Номер партії"`, заголовок партії, dt/dd, «Рух по складах» та «Залишок зараз» узгоджуються з BatchInspectorPage.tsx/BatchInspectionCard.tsx.
- Контейнер Batch Inspector через CSS-клас `rounded-xl` працездатний за поточною розміткою, але залежить від оформлення. `.first()` не розрізняє кілька UUID з однаковою назвою. Рекомендовано окремий стабільний атрибут UUID картки та сценарій колізії назв.
- Проєктне виробництво: маршрут `/project-production/update/:id` підтверджений; вибір `nth(1)` має дефект F6. Є готовий title для точного локатора.
- Спільні фільтри аналітики використовують структурний ancestor по `flex-col` та fallback із частковим текстом і `.first()`. Це ризик при дублюванні назв, а не доведений runtime-збій. Варто обмежити пошук відкритим popover і вимагати точний текст.
- `waitForResponseTolerant` / `waitForConditionTolerant` поглинають timeout: у кожного споживача потрібна фінальна позитивна перевірка очікуваного стану. Сам timeout log не підтверджує успіх дії.

## Оцінка змін логіки

Додавання UUID у relocation DTO, нового IN_MODIFICATION і API Batch Inspector відповідає напрямку змін продукту. Основний розрив — неповна міграція старих фабрик/DTO. Пагінаційна перевірка journal тепер шукає тестові ID на всіх API-сторінках; це усуває залежність від першої сторінки, але саме по собі не доводить доступність записів через UI-пагінацію. Нові Excel-сценарії розширюють перевірки аркушів, фільтрів та типів виробництва, проте F7/F8 послаблюють доказ завантаження й типів клітинок.

Локальні виправлення й регресійні перевірки завершено. Для підтвердження сумісності з deployment залишається цільовий E2E на відповідній версії ERP.
