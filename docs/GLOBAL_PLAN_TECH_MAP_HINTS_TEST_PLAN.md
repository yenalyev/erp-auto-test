# Тест план підказок редагування техкарт

## Мета і середовище

Перевірити три вимоги: обмеження warning/modal дозволом global-plan update, помаранчевий warning-бейдж !, жовта підказка недійсного розподілу на сторінці глобальних планів. Редакцію вимог узгоджено з фактичною реалізацією за вказівкою користувача 15 жовтня 2026 року. Середовище — dev, https://dev.cukerochka.sbs; браузер Chromium; Java TestNG + Playwright + RestAssured.

## Підготовка і критерій завершення

Використати ADMIN з global-plan::update та OWNER_1 без нього, який може редагувати техкарти власної локації. Реальні права перевіряти через API. Для позитивних сценаріїв створити ізольовані техкарти й generated GP зі snapshot, виконати структурну зміну та знову відкрити UI. Для негативних — незмінена карта, name-only PUT та карта без GP.

Усі десять сценаріїв повинні виконатися без FAIL, SKIP і configuration failures. Підтвердження — TestNG XML та Allure screenshots; TCM-результати публікуються після дозволеного створення записів. Звіт відокремлює дефект продукту від помилки підготовки чи автотесту. Передумова має підтверджуватися до негативного assert; прихований/незавантажений екран не є доказом відсутності підказки.

## Тест кейси

### TC-GP-HINT-UI-001 Редактор глобальних планів бачить підказку і може скасувати модалку

Критерій: AC-01. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#updaterSeesWarningAndCanCancelModal`.

Передумови: ADMIN; /users/me містить global-plan::update; EDIT_ALLOWED; generated майбутній GP із M1.

Кроки: Відкрити M1; перевірити посилання GP; змінити input amount; Зберегти; Скасувати.

Очікувано: Warning і confirmation видимі; кнопки та recovery text правильні; cancel не змінює version/input/snapshot.

### TC-GP-HINT-UI-002 Користувач без global-plan update не бачить підказку редагування

Критерій: AC-01. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#nonUpdaterDoesNotSeeEditWarning`.

Передумови: OWNER_1 має право редагувати M1, але не global-plan::update; M1 у live snapshot.

Кроки: Відкрити прямий URL update M1; дочекатися даних форми та live references.

Очікувано: Warning і confirmation відсутні; форма доступна.

### TC-GP-HINT-UI-003 Збереження техкарти без global-plan update не відкриває модалку

Критерій: AC-01. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#nonUpdaterSavesStructuralEditWithoutModal`.

Передумови: OWNER_1 без global-plan::update; EDIT_ALLOWED; M1 у generated live GP.

Кроки: Змінити input amount у формі M1; натиснути Зберегти; прочитати active map і GP.

Очікувано: Без confirmation збережено нову активну version+1 із новою нормою; snapshot GP містить M1.

### TC-GP-HINT-UI-004 Редактор бачить помаранчевий знак оклику замість числового бейджа

Критерій: AC-02. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#updaterSeesOrangeExclamationInsteadOfCount`.

Передумови: ADMIN із global-plan::update; generated live GP із M1.

Кроки: Structural PUT M1→M2; відкрити Глобальні плани; перевірити Виробничі плани в sidebar.

Очікувано: Один помаранчевий warning-бейдж !; числового лічильника в цьому пункті немає. Перевіряється саме помаранчевий відтінок, а не лише перевага червоного RGB-каналу.

### TC-GP-HINT-UI-005 Користувач без global-plan update не бачить знак оклику

Критерій: AC-02. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#nonUpdaterDoesNotSeeExclamation`.

Передумови: OWNER_1 без global-plan::update; розподіл GP недійсний після M1→M2.

Кроки: Відкрити M2; дочекатися sidebar; перевірити Виробничі плани.

Очікувано: Пункт доступний; бейдж ! і повідомлення про недійсний розподіл відсутні.

### TC-GP-HINT-UI-006 Жовта підказка перелічує змінені карти та актуальні плани

Критерій: AC-03. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#globalPlansHintListsAffectedPlansAndMaps`.

Передумови: ADMIN; два generated майбутні GP з окремими M1/M2; третій generated GP із незміненою картою.

Кроки: Структурно змінити M1/M2; відкрити Глобальні плани; прочитати підказки.

Очікувано: Жовта підказка має заголовок «Розподіл у глобальних планах … став недійсним для техкарт:». Посилання містять описи обох планів без доданого місяця/року в дужках. Після розгортання маркований список містить назви обох змінених карт. Окремо показано точну інструкцію «Відкрийте кожен план, призначте ресурс знову й повторно згенеруйте плани на локації.». Третій план/карта не згадані; немає шаблонних ${...}, null, undefined.

### TC-GP-HINT-UI-007 Перейменування техкарти не створює повідомлення про недійсний розподіл

Критерій: AC-04. Пріоритет: MEDIUM. Автотест: `GlobalPlanTechMapHintsUiTest#renameDoesNotInvalidateDistribution`.

Передумови: ADMIN; M1 у generated live GP.

Кроки: Name-only PUT M1; відкрити Глобальні плани; прочитати snapshot.

Очікувано: id/version незмінні; hint не згадує цей GP/карту; snapshot збережений.

### TC-GP-HINT-UI-008 Перегенерація прибирає підказку лише для виправленого плану

Критерій: AC-04. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#regenerationClearsOnlyRepairedPlanHint`.

Передумови: ADMIN; два недійсні GP після структурних змін двох карт.

Кроки: Перевірити hint до виправлення; reassign+decompose+generate першого; reload; повторити для другого.

Очікувано: Спершу перший hint зникає, другий лишається; snapshots переходять на нові id лише після відповідного generate; після другого generate обидва власні hint відсутні.

### TC-GP-HINT-UI-009 Невикористана техкарта зберігається без попередження та модалки

Критерій: AC-04. Пріоритет: MEDIUM. Автотест: `GlobalPlanTechMapHintsUiTest#unusedMapSavesWithoutWarningOrModal`.

Передумови: ADMIN із global-plan::update; EDIT_ALLOWED; M1 не використовується жодним GP.

Кроки: Відкрити M1; змінити input amount; Зберегти.

Очікувано: Warning і confirmation відсутні; створена активна version+1 з новим id.

### TC-GP-HINT-UI-010 Дозвіл працює незалежно від admin ролі

Критерій: AC-01. Пріоритет: HIGH. Автотест: `GlobalPlanTechMapHintsUiTest#granularPermissionControlsHintsIndependentlyOfAdminRole`.

Передумови: ізольований керівник локації; isAdmin=false; прямі grants global-plan.read та global-plan.manage (надає global-plan::update); техкарта у live snapshot.

Кроки: увійти цим користувачем; відкрити техкарту, змінити input amount і викликати модалку; скасувати; відкликати grant global-plan.manage, лишивши global-plan.read; увійти знову; повторити збереження.

Очікувано: за наявності update warning/modal видимі; після відкликання обидва приховані й техкарта зберігається без модалки з version+1. Роль користувача не змінюється. Snapshot лишається на попередній версії. Після тесту відкликаються grants і деактивується лише створений користувач.

## Ізоляція і відновлення

Один тест не змінює чужих сутностей. Для кількох планів перевіряється незмінений сусід або збереження невиправленого snapshot. Після кожного сценарію прибрати власні GP/location plans, активні версії техкарт і ресурси; відновити початковий tech-map mode. Cleanup failure робить прогін невдалим. Дані спільного dev можуть містити інші недійсні плани: тести зіставляють повідомлення з власними plan id/description та назвами карт.

## Запуски

- Цільовий dev: `mvn -o test '-Denv=dev' '-Dsuite=global-plan-tech-map-hints' '-Dframework.browser.channel=chrome' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false'`; перед запуском установити `$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'`.
- Фреймворк і повнота regression: `mvn -o test '-Dsuite=framework' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false'`.
- Вимоги та межі покриття: [REQ-GP-HINT.md](REQ-GP-HINT.md).

Повний повтор після виправлення HTTP-timeout: [10/10 PASS на dev, framework 85/85](audits/2026-10-15/global-plan-tech-map-hints-full-dev-report.md).

Попередній частковий прогін двох змінених кейсів: [звіт dev](audits/2026-10-15/global-plan-tech-map-hints-aligned-dev-report.md).
