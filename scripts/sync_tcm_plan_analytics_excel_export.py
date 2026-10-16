#!/usr/bin/env python3
"""Synchronize the plan Excel contract; local uses core CLI, prod uses MCP."""
from __future__ import annotations

from pathlib import Path

import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
core.FEATURE_ID = "REQ-PLAN-ANL-EXCEL"
core.FEATURE_PARENT_ID = "REQ-PLAN-ANL"
core.FEATURE_TITLE = "Аналітика для плану: експорт в Excel"
core.FEATURE_DESCRIPTION = (
    "Excel містить підсумки та всі ресурси відфільтрованої аналітики для плану. "
    "Прямий експорт без вибору ресурсу чи категорії повертає весь каталог."
)
core.FEATURE_MODULE = "PLAN"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs/REQ-PLAN-ANL-EXCEL.md"

# Keep the business AC text identical to the repository documentation.
core.ACCEPTANCE_CRITERIA = []
for line in core.DOCUMENTATION_PATH.read_text(encoding="utf-8").splitlines():
    if line.startswith("- **AC-"):
        key, text = line.removeprefix("- **").split(".** ", 1)
        core.ACCEPTANCE_CRITERIA.append((key, text))


def case(suffix, ac, title, method, preconditions, actions, expected, data=""):
    layer = "UI" if suffix.startswith("UI") else "API"
    test_id = f"TC-PLAN-ANL-XLSX-{suffix}"
    package = "ui" if layer == "UI" else "functional.analytics"
    class_name = f"PlanAnalyticsExcelExport{'Ui' if layer == 'UI' else 'Api'}Test"
    suites = "ui, regression" if layer == "UI" else "functional, regression"
    return {
        "testId": test_id, "featureId": core.FEATURE_ID, "acKey": ac,
        "title": title, "status": "ACTIVE", "priority": "HIGH", "severity": "MAJOR",
        "testType": "UI" if layer == "UI" else "FUNCTIONAL",
        "description": (
            f"Автотест: com.erp.tests.{package}.{class_name}#{method}. "
            f"@TestCaseId(\"{test_id}\"); suites: {suites}. "
            "Дані ERP лише читаються. Документація: docs/REQ-PLAN-ANL-EXCEL.md. "
            "Результати dev: docs/audits/2026-10-15/plan-analytics-excel-export-dev-test-report.md."
        ),
        "preconditions": "Авторизована сесія ADMIN; доступна аналітика ERP. " + preconditions,
        "testData": data or "Поточні дані середовища; останній повний місяць. Кількості рядків не зафіксовані.",
        "expectedResult": expected,
        "tags": "analytics,plan,excel,export,automated" + (",factory" if suffix == "API-004" else ""),
        "apiAutomationIds": [test_id] if layer == "API" else [],
        "uiAutomationIds": [test_id] if layer == "UI" else [],
        "steps": [dict(stepOrder=i, actionText=a, expectedText=e)
                  for i, (a, e) in enumerate(actions, 1)],
    }


core.CASES = [
    case("API-001", "AC-01", "Excel містить усі сторінки ресурсів і підсумки за одиницями",
         "workbookIncludesAllPagesAndTotalsByUnit",
         "Вибірка всіх категорій містить понад два ресурси та понад одну одиницю виміру.", [
             ("Отримати всі категорії та всі сторінки rows за період.", "Повний еталон без пропусків; кількість дорівнює totalElements."),
             ("Викликати export з тими самими фільтрами та page=1, size=1.", "Завантажено XLSX як attachment; пагінація не обрізає дані."),
             ("Порівняти порядок аркушів, заголовки, всі рядки, п'ять показників і підсумки.", "Підсумки перший, Ресурси другий; точна вибірка; числові комірки; суми окремо за одиницями."),
         ], "Усі ресурси та їхні значення збігаються з повним rows; підсумки не змішують одиниці."),
    case("API-002", "AC-03", "Excel застосовує бізнес-фільтри аналітики для плану",
         "exportMatchesFilteredRows", "Доступні ресурси, категорії та налаштовані постачальники.", [
             ("Підготувати кожен із дев'яти наборів бізнес-фільтрів.", "Набір охоплює позитивні, порожні, OR та AND вибірки і зміну періоду."),
             ("Зібрати всі сторінки rows та експортувати XLSX з ідентичними фільтрами.", "Rows і export повертають успішні відповіді."),
             ("Порівняти всі рядки, п'ять числових показників і підсумки за одиницями.", "Точна відповідність еталону; порожній результат має тільки заголовки та назви показників."),
         ], "Кожний із дев'яти варіантів проходить повне порівняння Excel із rows.",
         "Один ресурс; два ресурси; категорія; ресурс AND категорія; supplier MOU; supplier OR MOU+Інші; невідомий supplier; невідомий factory AND suppliers; три повні місяці."),
    case("API-003", "AC-04", "Прямий Excel-експорт без вибору повертає весь каталог",
         "exportWithoutSelectionContainsWholeCatalog", "Доступний повний довідник категорій.", [
             ("Прочитати rows без resourceIds/categoryIds.", "Content і totals порожні: UI ще не має вибору."),
             ("Зібрати всі сторінки rows з повним переліком категорій.", "Отримано актуальну повну аналітичну вибірку каталогу."),
             ("Викликати export без resourceIds/categoryIds та порівняти з повним еталоном.", "Увесь каталог, п'ять показників і підсумки за одиницями збігаються."),
         ], "Повний експорт без вибору є правильною поведінкою, погодженою 15.10.2026; 1929 — лише спостереження dev, не очікувана константа."),
    case("API-004", "AC-03", "Позитивний Завод: точні ресурси, виключення та підсумки Excel",
         "factoryExportIncludesMatchingResourcesAndExcludesOthers",
         "У category_property_options[Завод] є завод з непорожньою аналітикою та непорожнім набором виключених ресурсів. Відсутні передумови — FAIL, не SKIP.", [
             ("Прочитати налаштовані заводи, повний rows baseline та повний каталог ресурсів сторінками по 500 з sort=name,ASC.", "Властивості каталогу охоплюють усі ID baseline; опції заводу взяті з того самого джерела, що UI."),
             ("Незалежно визначити ID за точним properties[name=Завод] для налаштованого значення.", "Відповідна вибірка та набір ресурсів інших заводів/без властивості непорожні."),
             ("Порівняти factory-filtered rows із незалежними ID.", "Усі відповідні ID присутні; усі невідповідні виключені."),
             ("Обмежити baseline незалежними ID, обчислити BigDecimal підсумки за одиницями, порівняти весь XLSX.", "Точні рядки та п'ять числових показників; підсумки збігаються; XLSX і текст вибірки прикріплені в Allure."),
         ], "Фільтр доведено непорожньою незалежною вибіркою та виключеннями; однакова помилка rows/export не дає хибного PASS.",
         "Dev 15.10.2026: завод 'завод', 443 включено, 1486 виключено, каталог 1929. Це доказ прогону, не фіксовані тестові очікування."),
    case("UI-001", "AC-04", "UI приховує експорт без вибору ресурсу чи категорії",
         "exportIsUnavailableWithoutSelection", "Доступний браузер і сторінка /analytics/plan.", [
             ("Відкрити аналітику для плану й явно прибрати початкову категорію Боєприпас, не вибираючи ресурсів.", "Порожній стан таблиці."),
             ("Перевірити доступність кнопки Експорт в Excel.", "Кнопка прихована."),
         ], "UI порожній і кнопка прихована; прямий API-експорт усього каталогу залишається дозволеним."),
    case("UI-002", "AC-02", "Завантаження Excel з UI: усі сторінки та оновлені фільтри",
         "downloadedWorkbookMatchesUiFiltersAndAllPages",
         "Доступний браузер; категорія з понад 25 рядками й два налаштовані постачальники. Реальний backend, без mocks.", [
             ("Явно прибрати початкову категорію Боєприпас; вибрати категорію з понад 25 рядками, розмір 25, перейти на другу сторінку й завантажити Excel.", "Файл містить усю вибірку, включно з іншими сторінками."),
             ("Вибрати двох фактичних постачальників, змінити період на три повні місяці й повторити завантаження.", "Новий Excel відповідає поточним фільтрам."),
             ("Очистити постачальників і повторити завантаження.", "Фільтр supplier відсутній у запиті; вибірка відновлена за рештою умов."),
             ("Для кожного файлу порівняти query export/rows, видимі комірки й картки UI, усі сторінки даних і підсумки XLSX.", "Бізнес-фільтри однакові; export без page/size/sort; UI до 5 десяткових знаків; XLSX точно відповідає всій вибірці. Скріншоти та 3 файли в Allure."),
         ], "Усі три справжні UI-завантаження відповідають актуальним фільтрам, даним і підсумкам усіх сторінок."),
    case("UI-003", "AC-03", "Початковий Excel містить тільки вибірку категорії Боєприпас",
         "defaultAmmunitionExportMatchesAllFilteredRows",
         "У довіднику є категорія Боєприпас; доступні справжні rows і export.", [
             ("Відкрити /analytics/plan без ручного вибору фільтрів.", "Вибрано тільки Боєприпас; rows автоматично передає один categoryIds."),
             ("Завантажити Excel і зібрати всі сторінки rows за його бізнес-фільтрами.", "Фільтри export і rows ідентичні; пагінація не обмежує файл."),
             ("Порівняти видимі рядки й підсумки UI, усі ресурси та п'ять показників XLSX.", "Файл точно відповідає початковій вибірці; одиниці виміру не змішуються."),
         ], "Початковий експорт застосовує Боєприпас, а не весь каталог."),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
