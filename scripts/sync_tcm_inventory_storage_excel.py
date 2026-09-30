#!/usr/bin/env python3
"""Synchronize the inventory «По складах» Excel contract and cases with TCM."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-WMS-007-EXCEL"
core.FEATURE_PARENT_ID = "REQ-WMS-007"
core.FEATURE_TITLE = "Залишки — Excel-аркуш «По складах»"
core.FEATURE_DESCRIPTION = (
    "Експорт /inventory містить аркуш «По складах» з агрегованим рядком ресурсу "
    "і числовими залишками в окремих колонках доступних складів."
)
core.FEATURE_MODULE = "WMS"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-WMS-007-inventory-export-by-storage.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-01",
        "Excel зі сторінки «Залишки» містить наявний аркуш «Залишок» і новий «По складах». "
        "Перші п'ять колонок нового аркуша: Назва, Залишок, Заброньовано, "
        "Одиниця Виміру, Категорія; далі — по одній колонці кількості для кожного "
        "складу вибірки з однозначною назвою складу в заголовку.",
    ),
    (
        "AC-02",
        "Один ресурс на кількох складах займає один рядок: Залишок і Заброньовано "
        "є сумами за вибіркою, кожна складська колонка містить власний валовий "
        "залишок як числову Excel-комірку; одиниця й категорія відповідають ресурсу.",
    ),
    (
        "AC-03",
        "Книга відображає поточні parentStorageId або locations, пошук, категорію, "
        "теги та showZeroStock за погодженим правилом; чужі склади й ресурси не "
        "потрапляють у файл. UI завантажує книгу для актуального стану сторінки.",
    ),
]


def case(
    test_id: str,
    ac_key: str,
    title: str,
    description: str,
    preconditions: str,
    expected: str,
    actions: list[str],
    *,
    layer: str = "FUNCTIONAL",
    automated: bool = False,
    status: str = "ACTIVE",
) -> dict[str, Any]:
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": description,
        "priority": "CRITICAL" if automated else "HIGH",
        "severity": "MAJOR",
        "status": status,
        "testType": layer,
        "preconditions": preconditions,
        "expectedResult": expected,
        "tags": "wms,inventory,excel,per-storage," + ("automated" if automated else "manual"),
        "apiAutomationIds": [test_id] if automated and layer == "FUNCTIONAL" else [],
        "uiAutomationIds": [test_id] if automated and layer == "UI" else [],
        "steps": [
            {"stepOrder": index, "actionText": action,
             "expectedText": expected if index == len(actions) else ""}
            for index, action in enumerate(actions, start=1)
        ],
    }


core.CASES = [
    case(
        "TC-WMS-007-022", "AC-02", "По складах: агрегований ресурс на двох складах",
        "API-контракт XLSX: структура книги, заголовки, суми, числові типи, "
        "складські колонки й відсутність чужого ресурсу.",
        "Owner має доступ до складів A/B; ресурс R має 4 і 9 одиниць відповідно, "
        "бронь 0; на чужому складі є окремий ресурс.",
        "Аркуші «Залишок» і «По складах» наявні; заголовки точні; один рядок R "
        "має загальний залишок 13, бронь 0, A=4, B=9 у числових комірках; "
        "одиниця та категорія коректні, чужого ресурсу немає.",
        [
            "Створити ізольовані склади A/B і ресурси; посіяти R=4 на A, R=9 на B та чужий ресурс.",
            "GET /export-analytics/inventory з locations=A,B і searchTerm=R; відкрити XLSX.",
            "Зіставити заголовки, один рядок R та числові значення з inventory API.",
        ],
        automated=True,
    ),
    case(
        "TC-WMS-007-023", "AC-01", "По складах: UI завантажує новий аркуш",
        "UI-контракт кнопки «Експорт в Excel» у режимі «Всі локації».",
        "Owner має доступ до A/B з різними ресурсами; інший склад недоступний.",
        "Завантажений XLSX містить «Залишок» і «По складах», ресурси A/B "
        "та не містить чужого ресурсу.",
        [
            "Відкрити /inventory та вибрати «Всі локації».",
            "Натиснути «Експорт в Excel» і відкрити завантажений XLSX.",
            "Перевірити назви аркушів і склад ресурсів у книзі.",
        ],
        layer="UI",
        automated=True,
    ),
    case(
        "TC-WMS-007-024", "AC-03", "По складах: ієрархія та один склад",
        "Різні режими вибірки визначають набір динамічних колонок.",
        "Склад A має дочірній B; ресурс R є на обох.",
        "Для parentStorageId=A є тільки колонки складів ієрархії; для locations=A "
        "колонки B немає; суми збігаються з inventory API кожної вибірки.",
        [
            "Експортувати ієрархію через parentStorageId=A.",
            "Експортувати тільки A через locations=A.",
            "Порівняти динамічні колонки й суми обох книг з inventory API.",
        ],
    ),
    case(
        "TC-WMS-007-025", "AC-03", "По складах: фільтри та нульові залишки",
        "Пошук, категорія, теги й showZeroStock застосовуються до нового аркуша.",
        "Ресурси різних категорій/тегів; один вичерпаний StorageItem.",
        "Обидва аркуші відображають актуальні фільтри; нульовий ресурс "
        "обробляється за правилом showZeroStock після узгодження семантики для нового аркуша.",
        [
            "Застосувати пошук, категорію та тег на /inventory; експортувати.",
            "Перемкнути showZeroStock й експортувати повторно.",
            "Порівняти обидва аркуші з поточною вибіркою inventory API.",
        ],
        status="REVIEW",
    ),
    case(
        "TC-WMS-007-026", "AC-02", "По складах: бронь і дробові кількості",
        "Суми броні та дробові числові значення на двох складах.",
        "R має дробові залишки й бронь на A/B.",
        "Залишок — сума валових залишків; Заброньовано — сума броні; "
        "A/B мають власні валові кількості як числові Excel-комірки без втрати точності.",
        [
            "Підготувати дробові залишки та бронь R на A/B.",
            "Експортувати locations=A,B і прочитати «По складах».",
            "Порівняти числові комірки з inventory API.",
        ],
    ),
]


if __name__ == "__main__":
    if sys.argv[1:] == ["--payload-json"]:
        print(json.dumps({
            "feature": {
                "featureId": core.FEATURE_ID,
                "parentFeatureId": core.FEATURE_PARENT_ID,
                "title": core.FEATURE_TITLE,
                "description": core.FEATURE_DESCRIPTION,
                "documentation": core.DOCUMENTATION_PATH.read_text(encoding="utf-8"),
                "module": core.FEATURE_MODULE,
                "priority": core.FEATURE_PRIORITY,
            },
            "acceptanceCriteria": core.ACCEPTANCE_CRITERIA,
            "testCases": core.CASES,
        }, ensure_ascii=False))
    else:
        raise SystemExit(core.main())
