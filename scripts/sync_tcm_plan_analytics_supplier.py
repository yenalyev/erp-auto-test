#!/usr/bin/env python3
"""Idempotently synchronize the /analytics/plan supplier-filter QA contract with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-PLAN-ANL-SUPPLIER"
core.FEATURE_PARENT_ID = "REQ-PLAN-ANL"
core.FEATURE_TITLE = "Аналітика для плану: фільтр «Постачальник»"
core.FEATURE_DESCRIPTION = (
    "Фільтр «Постачальник» показує в аналітиці для плану лише вироби з вибраними "
    "поточними значеннями цієї властивості та поєднується з іншими умовами відбору."
)
core.FEATURE_MODULE = "PLAN"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-PLAN-ANL-SUPPLIER-FILTER.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-01",
        "У фільтрі постачальників можна знайти й вибрати одного або кількох постачальників із довідника; "
        "порожні та повторені назви не показуються.",
    ),
    (
        "AC-02",
        "Якщо вибрано одного постачальника, звіт містить ресурси з його поточним значенням. "
        "Якщо вибрано кількох, звіт містить ресурси будь-якого з них. "
        "Ресурси без зазначеного постачальника не входять.",
    ),
    (
        "AC-03",
        "Фільтр постачальників додатково звужує звіт за вибраними ресурсами, категоріями та періодом. "
        "Початково вибрано категорію Боєприпас. Після явного очищення всіх ресурсів і категорій "
        "сам постачальник не запускає таблицю.",
    ),
    (
        "AC-04",
        "Рядки, кількість сторінок і загальні підсумки відповідають усім даним за вибраними фільтрами. "
        "Кількості з різними одиницями вимірювання підсумовуються окремо.",
    ),
    (
        "AC-05",
        "Після очищення фільтра постачальник більше не обмежує звіт. "
        "Оновлення або повторне відкриття сторінки не відновлює попередній вибір.",
    ),
    (
        "AC-06",
        "Користувач може вибрати кількох постачальників одночасно; таблиця звіту зберігає "
        "звичні колонки й показує ресурси будь-якого з вибраних постачальників.",
    ),
]


def steps(action: str, expected: str) -> list[dict[str, Any]]:
    return [
        {"stepOrder": 1, "actionText": action, "expectedText": expected},
        {
            "stepOrder": 2,
            "actionText": "Звірити rows-response або відображення та фактичний query URL.",
            "expectedText": expected,
        },
    ]


def case(
    test_id: str,
    ac: str,
    title: str,
    action: str,
    expected: str,
    priority: str = "HIGH",
) -> dict[str, Any]:
    is_ui = "-UI-" in test_id
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac,
        "testId": test_id,
        "title": title,
        "description": f"/analytics/plan: {title}",
        "priority": priority,
        "severity": "CRITICAL" if priority == "CRITICAL" else "MAJOR",
        "status": "ACTIVE",
        "testType": "UI" if is_ui else "FUNCTIONAL",
        "preconditions": (
            "Користувач має analytics::read; для API створені ізольовані ресурси A "
            "(МОУ), B (Інші), C без властивості та ненульові залишки."
        ),
        "expectedResult": expected,
        "tags": "analytics,plan,supplier,automated",
        "apiAutomationIds": [] if is_ui else [test_id],
        "uiAutomationIds": [test_id] if is_ui else [],
        "steps": steps(action, expected),
    }


core.CASES = [
    case(
        "TC-PLAN-ANL-005",
        "AC-02",
        "Один supplier залишає точний збіг",
        "Запросити контрольні A, B, C з supplier=МОУ.",
        "HTTP 200; A присутній, B і C відсутні; чинна rows-схема валідна.",
        "CRITICAL",
    ),
    case(
        "TC-PLAN-ANL-006",
        "AC-03",
        "Повторювані supplier як OR, інші критерії як AND",
        "Передати supplier=МОУ&supplier=Інші та комбінувати з resourceIds, categoryIds і періодом.",
        "Supplier повертає A і B як OR; решта критеріїв додатково звужують результат.",
        "CRITICAL",
    ),
    case(
        "TC-PLAN-ANL-007",
        "AC-02",
        "Фільтр використовує актуальну властивість",
        "Змінити supplier ресурсу PUT-запитом і повторити фільтрацію старим та новим значенням.",
        "Ресурс збігається лише з новим актуальним значенням.",
    ),
    case(
        "TC-PLAN-ANL-008",
        "AC-04",
        "Виключення ресурсу без властивості, totals і pagination",
        "Запросити supplier-вибірку з малим size та порівняти сторінки з totals.",
        "C виключений; totalElements, totalPages і totals описують усі відфільтровані рядки.",
        "CRITICAL",
    ),
    case(
        "TC-PLAN-ANL-UI-004",
        "AC-01",
        "Опції, пошук, повторюваний query і контрольовані результати",
        "Очистити початкову категорію Боєприпас, вибрати контрольовані ресурси; знайти та послідовно вибрати МОУ й Інші у supplier multi-select.",
        "Опції унікальні; пошук працює; URL має один, потім два supplier; показані відповідні рядки.",
        "CRITICAL",
    ),
    case(
        "TC-PLAN-ANL-UI-005",
        "AC-05",
        "Період, очищення і відсутність persistence",
        "Очистити початкову категорію, вибрати ресурс і supplier; змінити період, очистити supplier, потім перевірити reload і повторне відкриття.",
        "Період зберігає поточний supplier; очищення прибирає його; reload/reopen очищують supplier, відновлюють Боєприпас і запускають rows.",
    ),
    case(
        "TC-PLAN-ANL-UI-006",
        "AC-03",
        "Supplier без цілі не викликає rows",
        "Явно прибрати початкову категорію Боєприпас; вибрати supplier без ресурсу/категорії, потім вибрати ресурс.",
        "До вибору цілі rows API не викликається; після вибору URL містить раніше вибраний supplier.",
    ),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
