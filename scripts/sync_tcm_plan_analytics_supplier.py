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
    "QA-контракт фільтра актуальної властивості ресурсу «Постачальник» "
    "на /analytics/plan без змін frontend або backend."
)
core.FEATURE_MODULE = "PLAN"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-PLAN-ANL-SUPPLIER-FILTER.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-01",
        'Опції multi-select походять із category_property_options["Постачальник"], '
        "є унікальними й непорожніми; пошук звужує список.",
    ),
    (
        "AC-02",
        "Один supplier дає точний збіг актуальної властивості, кілька supplier працюють "
        "як OR; ресурс без властивості виключений, історичне значення не використовується.",
    ),
    (
        "AC-03",
        "Supplier-група є додатковим AND до resourceIds, categoryIds і періоду; "
        "без ресурсу або категорії UI не викликає rows API.",
    ),
    (
        "AC-04",
        "Content, totalElements, totalPages і totals відповідають усій відфільтрованій "
        "вибірці; totals зберігають групування за одиницею.",
    ),
    (
        "AC-05",
        "Очищення прибирає supplier із наступного запиту; reload і повторне відкриття "
        "сторінки не відновлюють вибір.",
    ),
    (
        "AC-06",
        "Multi-select передається повторюваним текстовим query-параметром "
        "supplier=A&supplier=B; форма rows-response не змінюється.",
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
        "Знайти та послідовно вибрати МОУ й Інші у supplier multi-select.",
        "Опції унікальні; пошук працює; URL має один, потім два supplier; показані відповідні рядки.",
        "CRITICAL",
    ),
    case(
        "TC-PLAN-ANL-UI-005",
        "AC-05",
        "Період, очищення і відсутність persistence",
        "Змінити період, очистити supplier, потім перевірити reload і повторне відкриття.",
        "Період зберігає поточний supplier; очищення прибирає його; reload/reopen не відновлюють.",
    ),
    case(
        "TC-PLAN-ANL-UI-006",
        "AC-03",
        "Supplier без цілі не викликає rows",
        "Вибрати supplier без ресурсу/категорії, потім вибрати ресурс.",
        "До вибору цілі rows API не викликається; після вибору URL містить раніше вибраний supplier.",
    ),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
