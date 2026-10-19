# Аналітика для плану: початкова категорія «Боєприпас» — перевірка 16.10.2026

ERP: `https://dev.cukerochka.sbs`, ADMIN, Java 21, headless Chromium. Перевірялися лише тести й тестова документація; ERP production не запускався.

## Зміни

Початковий порожній стан у тестах замінено автоматичним вибором «Боєприпас». ID категорії визначається з довідника за точною назвою. Порожній стан тепер перевіряється після явного очищення категорії. Незалежний вибір ресурсів і довільної категорії також починається з очищення дефолту.

Додано `TC-PLAN-ANL-UI-008`, `TC-PLAN-ANL-UI-009`, `TC-PLAN-ANL-XLSX-UI-003`: автоматичний rows із правильними ID й датами; очищення/заміна/відновлення категорії; справжній Excel за початковою вибіркою з усіх сторінок. Оновлено перевірки постачальників, порожнього стану та експорту. Класи вже включено до `ui` і `regression`.

## Фактичні результати

Початковий прогін двох класів: **11 методів, 10 PASS, 1 FAIL, 0 SKIP**. Усі три нові сценарії пройшли. `supplierOptionsSearchAndRepeatedQueryFilterRows` має два `@TestCaseId`, тому 11 методів відповідають 12 TCM-кейсам.

Єдиний тайм-аут стався в `TC-PLAN-ANL-UI-005` під час відкриття постачальника після reload та повторного очищення категорії й вибору ресурсу. У тесті прибрано зайву повторну підготовку ресурсу: відновлена категорія вже є ціллю для перевірки постачальника. Окремий повтор: **1 PASS, 0 FAIL, 0 SKIP**, `BUILD SUCCESS`.

Отже, актуальні результати **всіх 11 методів / 12 пов'язаних кейсів — PASS**. Це підсумок початкового прогону та одного повтору, а не новий повний прогін після виправлення. Перша перевірка не приховується:

- [Початкові фактичні результати](plan-analytics-default-category-initial-results.json).
- [Актуальні результати з часовими мітками й джерелами Allure](plan-analytics-default-category-final-results.json).
- Maven logs: `target/plan-default-category-20261016.log`, `target/plan-default-category-recheck-20261016.log`.

На dev автоматичний rows передавав `categoryIds=6`, `fromDate=2026-09-01`, `toDate=2026-09-30`. ID 6 є спостереженням цього запуску, а не константою тесту. Новий Excel-тест завантажив усі сторінки rows та порівняв файл із ними, видимими рядками й підсумками UI. Підміни backend у цих нових перевірках немає; наявні перевірки постачальників використовують контрольовані rows.

## TCM dev і prod

В обох TCM синхронізовано й перевірено повторним читанням:

- Документацію `REQ-PLAN-ANL`, `REQ-PLAN-ANL-SUPPLIER`, `REQ-PLAN-ANL-EXCEL`.
- `REQ-PLAN-ANL/AC-01`, `REQ-PLAN-ANL/AC-03`, `REQ-PLAN-ANL-SUPPLIER/AC-03`.
- 9 наявних кейсів і 3 нові кейси з UI automation IDs та посиланнями на методи.

Докази: [TCM dev](plan-analytics-default-category-tcm-dev-sync.json), [TCM prod](plan-analytics-default-category-tcm-prod-sync.json). Повторювана синхронізація: `scripts/sync_tcm_plan_analytics_default_category.py`; сторонні критерії, кейси та наявні automation links зберігаються.

Конектор TCM dev налаштований на недоступний `localhost:8100`; синхронізацію виконано через доступний `http://localhost:18100`. Конфігурацію конектора не змінено. TCM prod: `http://tcm.lab`.

Результати виконання збережено локально у JSON вище. Додаткова спроба імпорту прогонів через AI API на TCM dev отримала HTTP 405; перевірка старого API TCM prod — HTTP 401. Прогони не опубліковано; документація, критерії та кейси синхронізовані успішно. Публікація кейсів у TCM prod не означає тестування ERP production.

## Команди перевірки

```powershell
mvn test -Denv=dev "-Dtest=PlanAnalyticsUiTest,PlanAnalyticsExcelExportUiTest" -Dtcm.enabled=false -Dgoogle.sheets.enabled=false -Dtest.output.directory=target/plan-default-category-20261016
mvn -o test -Denv=dev "-Dtest=PlanAnalyticsUiTest#supplierSurvivesPeriodChangeButNotClearReloadOrReopen" -Dtcm.enabled=false -Dgoogle.sheets.enabled=false -Dtest.output.directory=target/plan-default-category-20261016
python -m py_compile scripts/sync_tcm_plan_analytics_default_category.py scripts/sync_tcm_plan_analytics_supplier.py scripts/sync_tcm_plan_analytics_excel_export.py
```

Java-компіляція та Python syntax check успішні. `git diff --check` для змінених tracked-файлів цієї задачі не виявив помилок пробілів.
