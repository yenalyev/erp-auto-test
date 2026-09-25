#!/usr/bin/env python3
"""Idempotently synchronize production analytics documentation and UI cases with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-ANL-PRODUCTION"
core.FEATURE_PARENT_ID = "REQ-ANL"
core.FEATURE_TITLE = "Аналітика виробництва: Поденне та Статистика"
core.FEATURE_DESCRIPTION = (
    "Сторінка /analytics/production для керівника локації: звірка обсягу й персоналу "
    "з журналом, фільтри, витрати несерійного виробництва, статистичні таби та Excel."
)
core.FEATURE_MODULE = "ANL"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-ANL-PRODUCTION.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-01",
        "Керівник динамічно створеної локації має доступ до /analytics/production; "
        "тести не використовують OWNER_1 або shared-локації та очищають власні дані.",
    ),
    (
        "AC-02",
        "Поденне показує amount, рівний запису журналу виробництва, і staffCount, рівний "
        "workerQty snapshot-зміни; пресети періоду формують правильні дати.",
    ),
    (
        "AC-03",
        "Фільтри Вироби, Категорії та Локації додають відповідні resourceIds, categoryIds "
        "і storageIds до scoped timeline та зберігають правильні метрики журналу.",
    ),
    (
        "AC-04",
        "Витрати несерійного виробництва агрегують усі операції матеріалу: для 2×3 і 4×2 "
        "API та UI показують дві операції й сумарну витрату 14.",
    ),
    (
        "AC-05",
        "Статистика перемикає таби представлення й типу виробництва; Excel для поточного "
        "фільтра є валідним XLSX і містить динамічний матеріал.",
    ),
]


def step(order: int, action: str, expected: str) -> dict[str, Any]:
    return {"stepOrder": order, "actionText": action, "expectedText": expected}


def case(
    test_id: str,
    ac_key: str,
    title: str,
    description: str,
    preconditions: str,
    expected: str,
    steps: list[dict[str, Any]],
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
        "tags": "analytics,production,dynamic-location,dynamic-user,location-head,automated,ui",
        "roleName": "Керівник локації",
        "apiAutomationIds": [],
        "uiAutomationIds": [test_id],
        "steps": steps,
    }


DYNAMIC_PRECONDITIONS = (
    "Dev/prod ERP доступний; тест створює окремі локацію, користувача «Керівник локації», "
    "ресурси й виробничі записи та очищає їх після класу. Shared OWNER_1 не використовується."
)

core.CASES = [
    case(
        "TC-ANL-UI-005",
        "AC-02",
        "Пресети періоду аналітики виробництва",
        "Перевіряє стандартні пресети DateRangePicker вкладки «Поденне» під динамічним керівником локації.",
        DYNAMIC_PRECONDITIONS,
        "«1 день» встановлює today…today, «7 днів» — today-7…today; trigger оновлюється.",
        [
            step(1, "Відкрити /analytics/production та picker «Період».", "Видимі всі стандартні пресети."),
            step(2, "Вибрати «1 день».", "from і to дорівнюють поточній даті браузера."),
            step(3, "Вибрати «7 днів».", "from дорівнює today-7, to дорівнює today; trigger змінився."),
        ],
    ),
    case(
        "TC-ANL-UI-006",
        "AC-02",
        "Поденний обсяг і персонал відповідають журналу",
        "Звіряє timeline і картки вкладки «Поденне» з amount та snapshot-зміною production journal record.",
        DYNAMIC_PRECONDITIONS + " Створено journal record amount=6 зі зміною workerQty=7.",
        "Timeline та UI показують обсяг 6 і персонал 7; сторінка scoped до локації актора.",
        [
            step(1, "Відкрити вкладку «Поденне» динамічним керівником.", "Сторінка, таби й фільтри доступні без load error."),
            step(2, "Прочитати journal record і timeline за його датою.", "timeline.amount = journal.amount; staffCount = shift.workerQty."),
            step(3, "Звірити картки UI.", "«Обсяг за період» = 6; задіяний персонал = 7."),
        ],
        priority="CRITICAL",
    ),
    case(
        "TC-ANL-UI-007",
        "AC-04",
        "Сума витрат несерійного виробництва",
        "Регресія невірної суми у «Статистика → Несерійне виробництво → Витрати».",
        DYNAMIC_PRECONDITIONS + " Для матеріалу створено витрати 2×3 та 4×2.",
        "API й UI показують один матеріал, дві операції та сумарну витрату 14.",
        [
            step(1, "Відкрити «Статистика → Витрати → Несерійне виробництво».", "Запит /non-serial/input scoped до динамічної локації."),
            step(2, "Перевірити агрегований API-рядок матеріалу.", "count=2, operations має 2 записи, totalAmount=14."),
            step(3, "Перевірити summary і рядок матеріалу в UI.", "Обидва відображають 14 без помилки завантаження."),
        ],
        priority="CRITICAL",
        severity="CRITICAL",
    ),
    case(
        "TC-ANL-UI-008",
        "AC-03",
        "Фільтри вкладки Поденне",
        "Перевіряє фільтри виробу, категорії та локації разом зі збереженням journal metrics.",
        DYNAMIC_PRECONDITIONS + " Створено production з відомими product/category/location і зміною.",
        "Timeline URL містить вибрані IDs; amount і staffCount після кожного фільтра відповідають журналу.",
        [
            step(1, "Вибрати створений виріб.", "resourceIds містить product ID; метрики відповідають журналу."),
            step(2, "Вибрати категорію виробу.", "categoryIds містить category ID; метрики лишаються правильними."),
            step(3, "Вибрати динамічну локацію.", "storageIds містить location ID; scope і метрики правильні."),
        ],
        priority="CRITICAL",
    ),
    case(
        "TC-ANL-UI-009",
        "AC-05",
        "Таби статистики та Excel-експорт",
        "Перевіряє фільтрацію за табами/типами виробництва і завантаження Excel поточного набору.",
        DYNAMIC_PRECONDITIONS + " Створено серійне й несерійне виробництво.",
        "Усі таби активуються; non-series expenses містить матеріал; Excel валідний і містить цей матеріал.",
        [
            step(1, "Перемкнути За категоріями, За тегами, За виробами, Витрати, Продуктивність.", "Кожен вибраний таб стає active."),
            step(2, "Перемкнути Виготовлення, Розбір, Несерійне виробництво.", "Кожен вибраний тип стає active."),
            step(3, "Відкрити Несерійне виробництво → Витрати.", "Видимий рядок динамічного матеріалу."),
            step(4, "Натиснути «Експорт в Excel» і прочитати workbook.", "Файл непорожній, має XLSX signature і містить матеріал поточного фільтра."),
        ],
        priority="CRITICAL",
    ),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
