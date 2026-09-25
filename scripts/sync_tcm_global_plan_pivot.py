#!/usr/bin/env python3
"""Idempotently synchronize the global-plan resource pivot documentation and UI cases with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-GLOBAL-PLAN-PIVOT"
core.FEATURE_PARENT_ID = "REQ-GLOBAL-PLAN"
core.FEATURE_TITLE = "Глобальний план — pivot table view"
core.FEATURE_DESCRIPTION = (
    "Представлення «Ресурси» на /global-plans: ресурси у рядках, періоди у колонках, "
    "точні output-кількості, category multiselect і URL-backed перемикання view."
)
core.FEATURE_MODULE = "GLOBAL"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-GLOBAL-PLAN-PIVOT.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-GP-PIVOT-01",
        "Без параметра view сторінка /global-plans відкриває «Список». Перемикач «Ресурси» "
        "показує pivot і встановлює view=resources; повернення до «Список» прибирає параметр. "
        "Одночасно активний рівно один режим.",
    ),
    (
        "AC-GP-PIVOT-02",
        "Pivot містить один рядок на унікальний output-ресурс із назвою та одиницею виміру. "
        "Колонки періодів відсортовані від нового до старого, значення дорівнюють output amount, "
        "а відсутність ресурсу в періоді позначається «—».",
    ),
    (
        "AC-GP-PIVOT-03",
        "Мультифільтр «Категорії» доступний у view «Ресурси», фільтрує рядки за exact category ID, "
        "поєднує кілька категорій за OR і після очищення знову показує всі ресурси без зміни колонок і сум.",
    ),
    (
        "AC-GP-PIVOT-04",
        "Прямий URL /global-plans?view=resources і reload зберігають pivot view. Асинхронне завантаження "
        "завершується таблицею, empty state або error state, а не нескінченним spinner.",
    ),
]


def steps(*rows: tuple[str, str]) -> list[dict[str, Any]]:
    return [
        {"stepOrder": index, "actionText": action, "expectedText": expected}
        for index, (action, expected) in enumerate(rows, start=1)
    ]


def case(
    test_id: str,
    ac_key: str,
    title: str,
    description: str,
    preconditions: str,
    expected: str,
    case_steps: list[dict[str, Any]],
    *,
    priority: str = "HIGH",
    severity: str = "MAJOR",
) -> dict[str, Any]:
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": description,
        "priority": priority,
        "severity": severity,
        "status": "ACTIVE",
        "testType": "UI",
        "preconditions": preconditions,
        "expectedResult": expected,
        "tags": "global-plan,pivot,resources,category-filter,ui,automated",
        "roleName": "Адміністратор",
        "apiAutomationIds": [],
        "uiAutomationIds": [test_id],
        "steps": case_steps,
    }


PIVOT_PRECONDITIONS = (
    "ADMIN має доступ до /global-plans. API setup створює дві ізольовані production-техкарти "
    "з output-ресурсами у двох різних категоріях і два глобальні плани у вільних майбутніх періодах. "
    "Після класу плани видаляються, техкарти та ресурси деактивуються."
)

core.CASES = [
    case(
        "TC-GP-PIVOT-001",
        "AC-GP-PIVOT-01",
        "Перемикання між списком і resource pivot",
        "Перевіряє default view, стани toggle group та синхронізацію режиму з query-параметром.",
        PIVOT_PRECONDITIONS,
        "List → resources → list працює без reload; pivot і category filter видимі лише у resources view; URL узгоджений із режимом.",
        steps(
            ("Відкрити /global-plans без query-параметрів.", "Активний «Список»; параметр view відсутній."),
            ("Натиснути перемикач «Ресурси».", "Активний resources view, видимі pivot і «Категорії», URL містить view=resources."),
            ("Натиснути перемикач «Список».", "Відновлено list view; параметр view прибрано."),
        ),
        priority="CRITICAL",
        severity="CRITICAL",
    ),
    case(
        "TC-GP-PIVOT-002",
        "AC-GP-PIVOT-02",
        "Точна матриця ресурсів і періодів у pivot",
        "Два плани мають спільний ресурс A; новіший план додатково містить ресурс B.",
        PIVOT_PRECONDITIONS + " Старіший план: A=4; новіший план: A=10, B=2.5.",
        "Новіший період перед старішим; A має 10 і 4; B має 2.5 і «—»; кожен ресурс має один рядок із правильною одиницею.",
        steps(
            ("Відкрити /global-plans?view=resources.", "Pivot завантажено без spinner."),
            ("Прочитати заголовки періодів.", "Обидва тестові періоди є; новіший розташований лівіше старішого."),
            ("Знайти рядок ресурсу A за resourceId.", "Рядок один; видно назву/unit; значення нового/старого періодів = 10/4."),
            ("Знайти рядок ресурсу B за resourceId.", "Рядок один; видно назву/unit; значення нового/старого періодів = 2.5/«—»."),
        ),
        priority="CRITICAL",
        severity="CRITICAL",
    ),
    case(
        "TC-GP-PIVOT-003",
        "AC-GP-PIVOT-03",
        "Category multiselect фільтрує pivot за OR-семантикою",
        "Ресурс A належить category A, ресурс B — category B; обидва присутні в pivot.",
        PIVOT_PRECONDITIONS,
        "Одна категорія лишає свій ресурс; дві показують A і B; видалення одного chip лишає інший; порожній вибір відновлює всі рядки.",
        steps(
            ("У pivot вибрати category A.", "A видимий, B прихований."),
            ("Додати category B.", "A і B видимі одночасно."),
            ("Прибрати category A.", "A прихований, B видимий."),
            ("Прибрати category B.", "Вибір порожній; A і B знову видимі."),
        ),
        priority="CRITICAL",
    ),
    case(
        "TC-GP-PIVOT-004",
        "AC-GP-PIVOT-04",
        "Deep link і reload зберігають resources view",
        "Перевіряє URL-backed стан без залежності від попередньої навігації у SPA.",
        PIVOT_PRECONDITIONS,
        "Прямий URL і reload показують активний pivot; повернення у список прибирає view=resources.",
        steps(
            ("Відкрити напряму /global-plans?view=resources.", "Resources toggle активний; pivot і category filter видимі."),
            ("Перезавантажити сторінку браузера.", "Resources view лишається активним; дані повторно завантажені."),
            ("Перемкнутися на «Список».", "List view активний; view-параметр відсутній."),
        ),
    ),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
