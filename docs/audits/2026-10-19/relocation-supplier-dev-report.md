# Фільтр «Постачальник» — перевірка ERP dev, 19.10.2026

## Результат

**12/12 кейсів PASS; актуальні параметризовані виконання: 39 PASS / 0 FAIL / 0 SKIP**
(22 API та 17 UI). Framework: 100 PASS.

Повний прогін завершився о 17:47:09: **37 PASS / 2 FAIL**. Обидва падіння — у сценарії пошуку
після негативного запиту на «Видано» та «Отримано». Тест припускав очищення пошуку після
закриття popup. У сценарій додано явне очищення пошуку та перевірку відновлення опцій до
закриття. Повтор лише цього методу на всіх чотирьох вкладках: **4 PASS / 0 FAIL**, завершено
о **17:49:18**. Решта 35 виконань збережена з повного прогону.

Підсумок 39 PASS отримано за останнім фактичним результатом кожного ID та набору параметрів;
це повний прогін плюс цільовий повтор, а не один суцільний зелений Maven-прогін.
[Машинні результати з обома спробами](relocation-supplier-dev-results.json),
[повний TestNG XML](relocation-supplier-full-testng.xml),
[TestNG повтору](relocation-supplier-search-retest-testng.xml).

Середовище: `https://dev.cukerochka.sbs`, профіль `dev`, ADMIN, Chrome, Java 21,
без БД та без підміни відповідей. Час у звіті — Europe/Kiev.

## Додане покриття

- [Вимоги та 8 критеріїв приймання](../../REQ-REL-SUPPLIER.md).
- [12 автоматизованих кейсів](../../RELOCATION_SUPPLIER_FILTERS_TEST_CASES.md): 7 API / 5 UI, очікувано 39 виконань.
- API: точна властивість ресурсу, OR, невідоме значення/префікс, відсутня властивість, AND для тієї самої позиції, напрямки/дата/стани, підсумки, пагінація, Excel та зміна/видалення властивості.
- UI: опції/пошук, один/кілька постачальників, таблиця й числові підсумки, chips, перевідкриття, обладнання, реальні Excel-downloads, актуальна властивість наявної історії.
- Обидва класи включено до `relocations`, `regression`; UI — також `ui`. Окремий suite: `relocation-supplier-filters`.

## Джерела виконання

Повний прогін: `target/relocation-supplier-verified.log`,
`target/relocation-supplier-verified/allure-results`,
`target/relocation-supplier-verified/surefire-reports/testng-results.xml`.
Останній XML у target належить цільовому повтору; повний XML збережено окремо в цьому аудиті.
Повтор: `target/supplier-search-retest.log`, `target/supplier-search-retest/allure-results`.
Збережено 79 PNG/XLSX доказів актуальних виконань у `relocation-supplier-evidence/`;
точні відповідності файлів кейсам містяться в машинному JSON.

Команда виконання вже скомпільованих тестів:

```powershell
mvn surefire:test -Denv=dev -Dsuite=relocation-supplier-filters -Dtcm.enabled=false -Dgoogle.sheets.enabled=false -Dframework.browser.channel=chrome -Dtest.output.directory=target/relocation-supplier-verified -Dallure.results.directory=target/relocation-supplier-verified/allure-results
```

Вихідні класи зібрано Maven `test-compile` під Java 21; останні зміни Page Object та UI-класу скомпільовано
`javac --release 21` з тим самим dependency classpath. Для відтворення з чистою компіляцією:

```powershell
mvn test -Denv=dev -Dsuite=relocation-supplier-filters -Dframework.browser.channel=chrome -Dtcm.enabled=false -Dgoogle.sheets.enabled=false
```

## Перевірки фреймворку

**100 PASS / 0 FAIL / 0 SKIP**, завершено о 17:29:54 Europe/Kiev.
`RegressionSuiteCompletenessTest.allRunnableErpTestsAreRegisteredInRegressionSuite` — PASS.
[Збережений TestNG XML](relocation-supplier-framework-testng.xml),
[метадані](relocation-supplier-framework-results.json), лог `target/supplier-framework-final.log`.
Початковий sandbox-прогін не мав loopback-доступу до локальних HTTP-серверів;
повтор із дозволеним loopback успішний.

## Налагодження автоматизації

Початкові прогони мали 22 API PASS та 17 UI FAIL через помилки нового Page Object:
очікування URL без `/server`, очищення вже порожнього combobox input, очікування видимого
listbox для порожнього пошуку. Виправлено очікування реального контракту та взаємодію з Base UI:
відкриття перевіряється через aria-expanded, вибір виконується зі списку, порожній input повторно
не очищується, chip очікується до закриття popup. Порожній пошук враховує прихований listbox.
Ці падіння не зареєстровані як дефекти продукту й не імпортуються як остаточний результат фічі.

Додаткова перевірка реального контролу без створення запасів — 1 PASS, 17:40:23;
`target/supplier-control-diagnosis.log`. Проміжні дані старих повних прогонів очищені:
TestNG не зафіксував configuration failures, включно з cleanup.

## TCM і межі

У production TCM створено `REQ-REL-SUPPLIER` (DB ID 166), 8 AC, 12 ACTIVE автоматизованих
кейсів з однойменними apiAutomationIds/uiAutomationIds. Документацію, кроки й прив'язки перевірено
повторним читанням: [метадані синхронізації](relocation-supplier-tcm-prod-sync.json).
Імпортовано актуальні результати у **TCM run 139**, environment=`dev`,
`remoteRunId=relocation-supplier-dev-20261019-1740-verified`: 12 matched, unmatched=0.
Повторне читання run і кейсів підтвердило 12 PASS та статус COMPLETED.
[Імпорт і readback](relocation-supplier-tcm-run.json).

Cleanup повного прогону та повтору — без configuration failures. Власні інциденти прибрані,
активні передачі завершені/повернуті, перевірено видалення власних рядків залишків,
обладнання переведено у RETIRED, власні ресурси й локації архівовані, власні категорії видалені.

Не заявляється нова рольова матриця, live push без повторного запиту чи зміна глобального AppConfig.
LOST-обладнання перевіряється порожньою вибіркою; позитивні ресурсні дані LOST є.
Завершена історія та архівні сутності залишаються за lifecycle ERP; активні операції та залишки
власних тестових локацій очищуються fixture.
