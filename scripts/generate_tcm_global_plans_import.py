#!/usr/bin/env python3
"""Generate TCM import XLSX for global plans manual test cases."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from tcm_import_common import ROLE_ADMIN, ROLE_OWNER, Case, Step, write_xlsx

OUTPUT = Path(__file__).resolve().parent.parent / "docs" / "tcm-import-global-plans-20260622.xlsx"

FEAT = "REQ-GLOBAL-PLAN"
PRE_ADMIN = "@Admin залогінений. Середовище dev/staging. Підготовлені техкарти M1/M2/M3 (див. REQ-GLOBAL-PLAN-MAN.md)."
PRE_OWNER = "@Owner 1 залогінений без perm_global-plan::view."


def case(
    test_id: str,
    ac_id: str,
    title: str,
    description: str,
    *,
    role_name: str = ROLE_ADMIN,
    automation_test_id: str | None = None,
    steps: list[tuple[str, str]],
) -> Case:
    return Case(
        test_id=test_id,
        feature_id=FEAT,
        ac_id=ac_id,
        title=title,
        description=description,
        priority="HIGH",
        severity="MAJOR",
        preconditions=PRE_ADMIN if role_name == ROLE_ADMIN else PRE_OWNER,
        expected_result=steps[-1][1] if steps else "",
        tags="global-plans,manual",
        role_name=role_name,
        automation_test_id=automation_test_id,
        steps=[Step(i + 1, action, expected) for i, (action, expected) in enumerate(steps)],
    )


CASES = [
    case(
        "TC-GP-UI-001",
        "AC-GP-12",
        "Admin бачить список глобальних планів",
        "Перевірка sidebar та сторінки /global-plans",
        automation_test_id="TC-GP-UI-SMOKE-001",
        steps=[
            ("Відкрити SPA під Admin", "У sidebar є «Глобальні плани»"),
            ("Перейти на /global-plans", "Заголовок «Глобальні плани», кнопка «Створити план»"),
        ],
    ),
    case(
        "TC-GP-UI-002",
        "AC-GP-12",
        "Owner без global-plan permission не бачить пункт меню",
        "RBAC sidebar",
        role_name=ROLE_OWNER,
        steps=[
            ("Відкрити SPA під Owner 1", "Пункт «Глобальні плани» відсутній у sidebar"),
        ],
    ),
    case(
        "TC-GP-UI-010",
        "AC-GP-01",
        "Створити глобальний план на унікальний місяць",
        "Tab 1 Заплановано",
        automation_test_id="TC-GP-002",
        steps=[
            ("Натиснути «Створити план»", "Відкрився wizard"),
            ("Заповнити опис, місяць, рік, додати виріб з кількістю", "Дані збережені"),
            ("Натиснути «Створити план»", "План створено, Tab 2 активна"),
        ],
    ),
    case(
        "TC-GP-UI-014",
        "AC-GP-01",
        "Дублікат місяця — помилка",
        "Негативний сценарій",
        automation_test_id="TC-GP-003",
        steps=[
            ("Створити план на місяць M", "Успіх"),
            ("Спробувати створити другий план на той самий M", "Помилка / попередження про існуючий період"),
        ],
    ),
    case(
        "TC-GP-UI-020",
        "AC-GP-04",
        "Auto-assign при одній техкарті та локації",
        "Tab 2",
        steps=[
            ("Після збереження Tab1 перейти на Tab 2", "Рядок з auto-chip локації"),
        ],
    ),
    case(
        "TC-GP-UI-024",
        "AC-GP-06",
        "Розподілити по локаціях активує Tab 3/4",
        "Завершення декомпозиції",
        automation_test_id="TC-GP-023",
        steps=[
            ("Призначити всі рівні виробництва", "Кнопка «Розподілити по локаціям» активна"),
            ("Натиснути «Розподілити по локаціям»", "Tab 3 і Tab 4 доступні"),
        ],
    ),
    case(
        "TC-GP-UI-041",
        "AC-GP-08",
        "Попередження про заміну існуючого плану локації",
        "Tab 4",
        automation_test_id="TC-GP-024",
        steps=[
            ("Створити per-location план на той самий місяць на L1", "План існує"),
            ("Завершити декомпозицію до Tab 4", "Badge «Замінить наявний» на картці L1"),
        ],
    ),
    case(
        "TC-GP-UI-042",
        "AC-GP-09",
        "Створити плани по локаціях",
        "Генерація",
        automation_test_id="TC-GP-040",
        steps=[
            ("На Tab 4 натиснути «Створити плани по локаціям»", "Статус «Створено» / «Замінено»"),
            ("Перевірити /plans для локацій", "Плани з описом «Згенеровано з глобального плану»"),
        ],
    ),
]


def main() -> None:
    write_xlsx(CASES, OUTPUT)
    print(f"Wrote {OUTPUT} ({len(CASES)} cases)")


if __name__ == "__main__":
    main()
