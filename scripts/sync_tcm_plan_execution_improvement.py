#!/usr/bin/env python3
"""Idempotently synchronize /plan-execution improvement documentation and cases with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-PLAN-EXECUTION-IMPROVEMENT"
core.FEATURE_PARENT_ID = None
core.FEATURE_TITLE = "Improvement сторінки виконання плану"
core.FEATURE_DESCRIPTION = "Категорійні фільтри, Excel, bulk favourites, out-of-plan total та історична агрегація production records."
core.FEATURE_MODULE = "PLAN_EXECUTION"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-PLAN-EXECUTION-IMPROVEMENT.md"
core.ACCEPTANCE_CRITERIA = [
    ("AC-01", "Категорійний мультиселект фільтрує planned і out-of-plan за точними ID: parent не включає child автоматично; фільтр комбінується з пошуком і favourites."),
    ("AC-02", "Excel містить поточні видимі рядки, включно зі згорнутим out-of-plan: За планом зберігає чинні 6 колонок, Поза планом має Од. виміру після Категорія, Розбір лишається Продукт | Кількість."),
    ("AC-03", "Popup керування обраними підтримує Обрати все/Зняти все в межах фільтра; Active не повертає архівні, Archived повертає архівні."),
    ("AC-04", "Поза планом показує total лише для шт/комп і лише для видимих рядків; несумісні одиниці не додаються."),
    ("AC-05", "Execution враховує всі фінальні production records періоду після update/delete; архівування техкарти або ресурсу не стирає історію."),
    ("AC-06", "Агрегація виконується за resourceId; зміна unit не ділить рядок, усі amounts сумуються без конвертації, показуються останні категорія й одиниця ресурсу та вся сума закриває поточну ціль."),
]


def steps(action: str, expected: str) -> list[dict[str, Any]]:
    return [
        {"stepOrder": 1, "actionText": action, "expectedText": expected},
        {"stepOrder": 2, "actionText": "Звірити UI/API та відсутність зайвих рядків або подвійного підрахунку.", "expectedText": expected},
    ]


def case(test_id: str, ac: str, title: str, action: str, expected: str, automated: bool = True) -> dict[str, Any]:
    is_ui = "-UI-" in test_id
    return {
        "featureId": core.FEATURE_ID, "acKey": ac, "testId": test_id, "title": title,
        "description": f"/plan-execution: {title}", "priority": "HIGH", "severity": "MAJOR",
        "status": "ACTIVE", "testType": "UI" if is_ui else "FUNCTIONAL",
        "preconditions": "Ізольовані ресурс, production records поточного періоду, техкарта і за потреби план; вибрана конкретна локація.",
        "expectedResult": expected, "tags": "plan-execution,improvement," + ("automated" if automated else "manual"),
        "apiAutomationIds": [test_id] if automated and not is_ui else [],
        "uiAutomationIds": [test_id] if automated and is_ui else [],
        "steps": steps(action, expected),
    }


core.CASES = [
    case("TC-API-PLANEXEC-001", "AC-01", "Backend-фільтр категорій і актуальна категорія", "Змінити категорію ресурсу між productions та запитати стару і нову categoryIds.", "Стара категорія не повертає ресурс; нова повертає один агрегований рядок з останньою назвою."),
    case("TC-API-PLANEXEC-002", "AC-02", "Backend Excel", "Експортувати category-filtered execution.", "Валідний XLSX має точні структури трьох аркушів: чинні 6 колонок За планом; Од. виміру після Категорія у Поза планом; Продукт | Кількість у Розбір; поточну дату й потрібний ресурс."),
    case("TC-API-PLANEXEC-003", "AC-05", "Історія після архівування техкарти", "Створити production, архівувати техкарту, повторити execution.", "Фінальний production лишається в execution без дублювання."),
    case("TC-API-PLANEXEC-004", "AC-05", "Фінальний стан update/delete", "Створити 5, оновити до 7, видалити production.", "Execution послідовно показує 5, 7 і 0; попередні версії не додаються."),
    case("TC-API-RES-007", "AC-06", "ADMIN змінює одиницю ресурсу", "Створити productions до і після зміни unit та план у новій unit.", "PUT дозволений; execution має один рядок у новій unit, суму всіх amounts і повне зарахування до цілі."),
    case("TC-UI-PLANEXEC-014", "AC-01", "Категорійний мультиселект", "Обрати дві категорії на сторінці.", "Видно лише ресурси обох категорій у planned/out-of-plan."),
    case("TC-UI-PLANEXEC-015", "AC-02", "Excel-контракт і unit", "Експортувати поточний execution.", "XLSX має точні headers усіх трьох аркушів, поточну дату та unit у product sheets."),
    case("TC-UI-PLANEXEC-016", "AC-02", "Excel дорівнює видимим рядкам", "Застосувати пошук при згорнутому out-of-plan та експортувати.", "Файл містить видимий out-of-plan ресурс і не містить відфільтрований."),
    case("TC-UI-PLANEXEC-017", "AC-03", "Обрати все за фільтром", "Відфільтрувати popup і виконати Обрати все.", "Обрані всі й лише поточні результати."),
    case("TC-UI-PLANEXEC-018", "AC-03", "Зняти все за фільтром", "Після bulk-select виконати Зняти все.", "Зняті лише поточні результати, лічильник оновлено."),
    case("TC-UI-PLANEXEC-019", "AC-04", "Total поза планом", "Додати два countable productions.", "Total дорівнює сумі видимих шт/комп."),
    case("TC-UI-PLANEXEC-020", "AC-04", "Mixed units у total", "Додати шт/комп та іншу unit.", "Інша unit не додається до total шт/комп."),
    case("TC-UI-PLANEXEC-021", "AC-01", "Категорія + пошук + favourites", "Комбінувати category, search і favourite-state.", "Фільтри працюють як перетин."),
    case("TC-UI-PLANEXEC-023", "AC-03", "Active/Archived у popup", "Перемкнути стан каталогу.", "Active не показує архівні; Archived доступний і шукає архівні."),
    case("TC-UI-PLANEXEC-029", "AC-03", "Пошук за назвою Active/Archived", "Динамічним користувачем шукати у popup точні назви активного й архівного ресурсів у кожному стані.", "Active знаходить лише активний ресурс з isActive=true; Archived — лише архівний з isActive=false."),
    case("TC-UI-PLANEXEC-022", "AC-01", "Exact parent-category на сторінці", "Обрати parent у category filter сторінки /plan-execution.", "Child-resource не включений автоматично; для нього треба окремо вибрати child-категорію."),
    case("TC-UI-PLANEXEC-024", "AC-02", "Помилка Excel", "Повернути 4xx/5xx з export.", "Toast показано, кнопка доступна повторно, файл не створено."),
    case("TC-UI-PLANEXEC-025", "AC-05", "Planned після архівування ресурсу", "Після productions замінити output у техкарті динамічним replacement-resource, повністю видалити історичний ресурс з inventory включно з zero-row, архівувати техкарту й ресурс під ADMIN.", "Зроблено й Excel не змінюються та не показують replacement-resource."),
    case("TC-UI-PLANEXEC-026", "AC-05", "Out-of-plan після архівування", "Замінити output у техкарті, видалити out-of-plan resource з inventory включно з zero-row та архівувати його під ADMIN.", "Історичний total і рядок лишаються."),
    case("TC-UI-PLANEXEC-027", "AC-05", "Інваріант archive/unarchive", "Архівувати й розархівувати ресурс.", "Bucket, amount і Excel незмінні."),
    case("TC-UI-PLANEXEC-028", "AC-06", "Історичні metadata після зміни unit", "Змінити name/category/unit між productions.", "Один рядок на resourceId, сума всіх amounts, останні category/unit і остання production-назва."),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
