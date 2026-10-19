# Узгодження вимог підказок техкарт із реалізацією

Історичний частковий прогін. Наступний [повний прогін](global-plan-tech-map-hints-full-dev-report.md) підтвердив **10/10 PASS** на dev та **85/85 PASS** framework після виправлення вимірювання HTTP-timeout.

15 жовтня 2026 року користувач погодив фактичну реалізацію індикатора та підказки глобальних планів. Оновлено вимоги REQ-GP-HINT, критерії AC-02/AC-03, тест-план, автотести та локальний пакет TCM.

## Прийнята поведінка

- Помаранчевий бейдж попередження `!` замість числового лічильника.
- У підказці глобальних планів посилання містять описи планів без доданого місяця/року в дужках.
- Заголовок: «Розподіл у глобальних планах … став недійсним для техкарт:».
- Назви змінених техкарт подано маркованим списком із можливістю розгортання довгого списку.
- Окрема інструкція: «Відкрийте кожен план, призначте ресурс знову й повторно згенеруйте плани на локації.»

Правило доступу global-plan::update та сценарії повторного призначення й генерації зберігаються.

## Перевірка на dev

Середовище: https://dev.cukerochka.sbs, встановлений Chrome, справжні API та сесія ADMIN.

**Консолідований результат двох змінених кейсів: 2 PASS, 0 FAIL, 0 SKIP.**

- **TC-GP-HINT-UI-004 — PASS:** один помаранчевий `!`, відсутність цифр; RGB нормалізується браузером, помаранчевий відтінок перевіряється в діапазоні 20–50°.
- **TC-GP-HINT-UI-006 — PASS:** точний заголовок, описи-посилання обох планів, назви обох змінених карт у списку, окрема точна інструкція та жовтий фон. Незмінена третя карта/план відсутні у підказці; snapshot третього плану незмінений.

TC-004 пройшов у запуску двох кейсів, що завершився о 14:17 за Europe/Kiev. Першу перевірку TC-006 зупинило читання всього DOM-контейнера замість заголовка; після виправлення Page Object цільовий повтор TC-006 пройшов о 14:20. Підсумок використовує останній результат кожного кейсу. Прогін цієї редакції охоплює саме TC-004 та TC-006.

Cleanup обох запусків завершився без configuration failures: власні GP/location plans видалено, активні версії власних карт і ресурси архівовано, початковий режим техкарт відновлено.

[Помаранчевий бейдж](global-plan-tech-map-hints-aligned-evidence/TC-GP-HINT-UI-004.png).
[Підказка зі списком](global-plan-tech-map-hints-aligned-evidence/TC-GP-HINT-UI-006.png).
[Машинні результати](global-plan-tech-map-hints-aligned-results.json).

## Перевірки фреймворку

**84/85 PASS**, 1 FAIL, 0 SKIP. RegressionSuiteCompletenessTest — **PASS**; клас підключено до regression.

Залишається окреме падіння `HttpClientInfrastructureTest#allRequestTypesRespectTheConfiguredTimeout(JSON)`: 5206 мс за очікуваної тривалості <5000 мс. Тип SocketTimeoutException правильний. Поріг цього інфраструктурного тесту не змінювався; вимоги фічі його не охоплюють.

## Артефакти та відтворення

- TC-004: `target/global-plan-hints-aligned/surefire-reports/testng-results-initial.xml`, метод `updaterSeesOrangeExclamationInsteadOfCount`.
- TC-006: `target/global-plan-hints-aligned/surefire-reports/testng-results-hint-rerun.xml`.
- Allure: `target/global-plan-hints-aligned/allure-results` (TC-004) та `target/global-plan-hints-aligned/hint-rerun-allure` (TC-006).
- Framework: `target/global-plan-hints-aligned/surefire-reports/testng-results.xml`, лог `target/global-plan-hints-aligned-framework.log`.

```powershell
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'
mvn -o test '-Denv=dev' '-Dtest=GlobalPlanTechMapHintsUiTest#updaterSeesOrangeExclamationInsteadOfCount+globalPlansHintListsAffectedPlansAndMaps' '-Dframework.browser.channel=chrome' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dtest.output.directory=target/global-plan-hints-aligned' '-Dallure.results.directory=target/global-plan-hints-aligned/allure-results'
```

Після збереження UI XML framework запускається без повторної компіляції через `mvn -o surefire:test '-Dsuite=framework' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dtest.output.directory=target/global-plan-hints-aligned'`.

[Чинні вимоги](../../REQ-GP-HINT.md).
[Тест-план](../../GLOBAL_PLAN_TECH_MAP_HINTS_TEST_PLAN.md).
[Історичний звіт первісної редакції](global-plan-tech-map-hints-dev-test-report.md).

Пакет [TCM](global-plan-tech-map-hints-tcm-payload.json) оновлено локально. Production TCM у цій роботі не змінювався.
