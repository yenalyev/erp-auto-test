# Повторний прогін підказок техкарт і виправлення HTTP-timeout

Дата: 15 жовтня 2026 року. Середовище фічі: https://dev.cukerochka.sbs, встановлений Chrome. Перевіряється погоджена редакція [REQ-GP-HINT](../../REQ-GP-HINT.md): помаранчевий `!`, посилання-описи планів, список змінених техкарт та окрема інструкція відновлення.

## Причина та виправлення

У попередньому framework-прогоні `HttpClientInfrastructureTest#allRequestTypesRespectTheConfiguredTimeout(JSON)` завершився правильним `SocketTimeoutException`, але перевірка загальної тривалості отримала 5206 мс за порогу <5000 мс. Таймер охоплював створення клієнта, перше завантаження HTTP-стека/серіалізатора/фільтрів та виконання асертів.

Виправлено вимірювання в `src/test/java/com/erp/tests/framework/HttpClientInfrastructureTest.java`:

- Клієнт створюється перед початком вимірювання.
- Для кожного типу запиту той самий клієнт спочатку виконує успішний `/echo`, щоб ініціалізувати відповідний HTTP-шлях.
- Тривалість `/slow` фіксується відразу після повернення винятку, до асертів і запису діагностики.
- Allure містить окремі таймінги створення клієнта, попереднього запиту та запиту з таймаутом.

Налаштований таймаут **1 секунда**, верхній поріг **<5000 мс** та вимога `SocketTimeoutException` збережені для JSON, multipart POST, multipart PUT і файлу. Локальний сервер відповідає на `/slow` через 1700 мс: якщо транспортний таймаут перестане працювати, перевірка обов'язкового винятку впаде. Налаштування HTTP-клієнта не змінювалися.

## Перевірки фреймворку

Повний suite `framework`: **85 PASS, 0 FAIL, 0 SKIP**. `RegressionSuiteCompletenessTest` — **PASS**. Окремий повтор `HttpClientInfrastructureTest` у свіжій JVM: **11 PASS, 0 FAIL, 0 SKIP**.

Виміряний `/slow` у повному framework suite: JSON — **1026 мс**, POST — **1035 мс**, PUT — **1017 мс**, FILE — **1015 мс**. Окремий запуск у свіжій JVM: JSON — **1033 мс**, POST — **1038 мс**, PUT — **1025 мс**, FILE — **1021 мс**.

У свіжій JVM створення першого JSON-клієнта зайняло **1322 мс**, успішний попередній JSON-запит — **2381 мс**; ці витрати тепер видно окремо від транспортного таймауту. Перевірки виконані послідовно, в ізольованому build-каталозі.

## Повний прогін фічі на dev

**10 PASS, 0 FAIL, 0 SKIP, 0 configuration failures.** Один повний запуск suite `global-plan-tech-map-hints`, без вибіркових повторів. Завершено о **14:32:23 Europe/Kiev**; тривалість TestNG suite — **228,7 с**.

- **TC-GP-HINT-UI-001 — PASS:** підказка й модалка доступні редактору; скасування зберігає версію, input і snapshot.
- **TC-GP-HINT-UI-002 — PASS:** без global-plan update підказка редагування відсутня.
- **TC-GP-HINT-UI-003 — PASS:** структурне редагування без global-plan update зберігається без модалки.
- **TC-GP-HINT-UI-004 — PASS:** редактор бачить помаранчевий `!` замість числового бейджа.
- **TC-GP-HINT-UI-005 — PASS:** користувач без дозволу не бачить `!`.
- **TC-GP-HINT-UI-006 — PASS:** точний заголовок, посилання-описи планів, список змінених карт, окрема інструкція й жовтий фон; незмінений план не зачеплено.
- **TC-GP-HINT-UI-007 — PASS:** перейменування не робить розподіл недійсним.
- **TC-GP-HINT-UI-008 — PASS:** повторне призначення й генерація прибирають підказку лише для виправленого плану.
- **TC-GP-HINT-UI-009 — PASS:** невикористана техкарта зберігається без попередження та модалки.
- **TC-GP-HINT-UI-010 — PASS:** показ визначає дозвіл, незалежно від admin-ролі; відкликання перевірено після нового входу.

Cleanup завершився успішно: власні глобальні й location plans видалено, активні версії власних техкарт та ресурси архівовано, тестового користувача деактивовано, початковий режим техкарт відновлено. Падінь HTTP-timeout у цьому прогоні немає.

[Машинні результати всіх прогонів і таймінги](global-plan-tech-map-hints-full-results.json). Збережено 13 скриншотів; відповідність кейсам є в полі `evidence` JSON. Приклади: [бейдж](global-plan-tech-map-hints-full-evidence/TC-GP-HINT-UI-004-01.png), [підказка](global-plan-tech-map-hints-full-evidence/TC-GP-HINT-UI-006-02.png), [після відновлення](global-plan-tech-map-hints-full-evidence/TC-GP-HINT-UI-008-02.png).

Цей прогін охоплює десять сценаріїв із тест-плану; він не розширює задокументовані межі перевірки до всіх комбінацій прав, історичних планів чи довгих списків. Зовнішню публікацію TCM/Google Sheets вимкнено.

## Відтворення й артефакти

```powershell
mvn -o test '-Dsuite=framework' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dtest.output.directory=target/global-plan-hints-timeout-fix' '-Dallure.results.directory=target/global-plan-hints-timeout-fix/framework-allure'
# Зберегти surefire-reports/testng-results.xml як framework-testng-results.xml.
mvn -o surefire:test '-Dtest=HttpClientInfrastructureTest' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dtest.output.directory=target/global-plan-hints-timeout-fix' '-Dallure.results.directory=target/global-plan-hints-timeout-fix/http-cold-jvm-allure'
# Зберегти surefire-reports/testng-results.xml як http-cold-jvm-testng-results.xml.
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'
mvn -o surefire:test '-Denv=dev' '-Dsuite=global-plan-tech-map-hints' '-Dframework.browser.channel=chrome' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dtest.output.directory=target/global-plan-hints-timeout-fix' '-Dallure.results.directory=target/global-plan-hints-timeout-fix/feature-allure'
```

- Framework XML: `target/global-plan-hints-timeout-fix/framework-testng-results.xml`.
- Окремий HTTP XML: `target/global-plan-hints-timeout-fix/http-cold-jvm-testng-results.xml`.
- Повний dev XML: `target/global-plan-hints-timeout-fix/feature-testng-results.xml`.
- Framework-логи: `target/global-plan-hints-timeout-fix-framework.log`, `target/global-plan-hints-timeout-fix-http-cold-jvm.log`.
- Dev-лог: `target/global-plan-hints-timeout-fix-dev.log`.
- Allure: окремі `framework-allure`, `http-cold-jvm-allure`, `feature-allure` в build-каталозі вище.

[Тест-план](../../GLOBAL_PLAN_TECH_MAP_HINTS_TEST_PLAN.md). [Попередній частковий прогін після узгодження вимог](global-plan-tech-map-hints-aligned-dev-report.md).
