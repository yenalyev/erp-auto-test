#!/usr/bin/env python3
"""Synchronize FAITA initiator write-off documentation and cases with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-FAITA-002"
core.FEATURE_PARENT_ID = "REQ-INTEGRATION"
core.FEATURE_TITLE = "FAITA: ініціатори без списання з точок вильоту"
core.FEATURE_DESCRIPTION = (
    "Ініціатори зі зазначеним стороннім постачальником завершуються без списання з точки вильоту. "
    "За наявності «ПМ Цукрарня» або відсутнього чи порожнього постачальника діє звичайне списання."
)
core.FEATURE_MODULE = "FAITA"
core.FEATURE_PRIORITY = "CRITICAL"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-FAITA-INITIATOR-WRITE-OFF.md"
core.ACCEPTANCE_CRITERIA = [
    ("AC-01", "Якщо всі зіставлені ресурси ініціатора мають зазначеного постачальника, відмінного від «ПМ Цукрарня», запис завершується без списання з точки вильоту за нульового або додатного залишку."),
    ("AC-02", "Якщо серед зіставлених ресурсів ініціатора є постачальник «ПМ Цукрарня», діє звичайне списання з точки вильоту, навіть за наявності інших постачальників. Нестача не спричиняє часткового списання."),
    ("AC-03", "Якщо постачальник хоча б одного зіставленого ресурсу відсутній або порожній, діє звичайне списання. Незіставлені ініціатори, боєприпаси й додаткові ресурси не отримують винятку за постачальником."),
    ("AC-04", "Повторна обробка однієї події Файти не дублює запис і списання; стан, ресурс та залишок відповідають правильній точці вильоту."),
    ("AC-05", "Журнал відображає правильні ресурс і точку та погоджений підпис автоматичного завершення."),
]


def case(
    number: int, ac: str, title: str, action: str, expected: str,
    *, automated: bool = False, ui: bool = False, result_note: str = "",
) -> dict[str, Any]:
    test_id = f"TC-FAITA-INIT-{number:03d}"
    scope = (
        "Автоматизовано: БД-фікстура PENDING FLIGHT, API complete, перевірка status і stock на dev ERP. "
        "Імпорт FAITA та плановий auto-complete не перевіряються."
        if automated else
        "Запланований кейс; автоматизації та підтвердженого прогону немає."
    )
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac,
        "testId": test_id,
        "title": title,
        "description": scope + (" " + result_note if result_note else "")
        + " Публікація в production TCM не є прогоном на production ERP.",
        "priority": "CRITICAL" if number in (1, 2, 3, 4, 5, 6) else "HIGH",
        "severity": "CRITICAL" if number in (1, 2, 3) else "MAJOR",
        "status": "ACTIVE",
        "testType": "UI" if ui else "FUNCTIONAL",
        "preconditions": (
            "Ізольовані точка вильоту, екіпаж, ресурс ERP та FLIGHT-зіставлення. "
            + ("Для автотесту потрібні dev ERP, use.database=true і ADMIN/OWNER_1."
               if automated else "Дані FAITA та правила постачальника відповідають сценарію.")
        ),
        "expectedResult": expected,
        "tags": "faita,initiator,write-off," + ("automated" if automated else "planned"),
        "apiAutomationIds": [test_id] if automated else [],
        "uiAutomationIds": [],
        "steps": [
            {"stepOrder": 1, "actionText": action, "expectedText": expected},
            {"stepOrder": 2, "actionText": "Звірити статус конкретного запису та залишок відповідної точки вильоту.", "expectedText": expected},
        ],
    }


core.CASES = [
    case(1, "AC-01", "Інший постачальник, нульовий залишок", "Зіставити i-ініціатор з ресурсом іншого постачальника, залишити на точці 0 і завершити запис.", "COMPLETED; залишок точки 0, жодного дебету.", automated=True),
    case(2, "AC-02", "ПМ Цукрарня, достатній залишок", "Зіставити i-ініціатор з ресурсом ПМ Цукрарня, забезпечити запас на точці й завершити запис.", "COMPLETED; залишок точки зменшився рівно на amount.", automated=True),
    case(3, "AC-01", "Інший постачальник, додатний залишок", "Зіставити i-ініціатор з ресурсом іншого постачальника, забезпечити запас на точці й завершити запис.", "COMPLETED; додатний залишок точки не змінився.", automated=True),
    case(4, "AC-03", "Незіставлений ініціатор", "Імпортувати FAITA-ініціатор без FLIGHT-зіставлення; згодом додати зіставлення.", "До зіставлення PENDING і без списання; після нього запис оброблено один раз."),
    case(5, "AC-02", "ПМ Цукрарня, нестача", "Імпортувати i-ініціатор ПМ Цукрарня з amount більшим за запас точки.", "Погоджений статус нестачі; часткового списання немає."),
    case(6, "AC-03", "Спільна подія з боєприпасом і implicit", "Імпортувати подію з i-ініціатором іншого постачальника, боєприпасом та implicit-ресурсом.", "Без дебету завершується лише ініціатор; інші записи обробляються за власними правилами."),
    case(7, "AC-04", "Повторний sync і auto-complete", "Двічі синхронізувати й обробити ту саму FAITA-подію.", "Один логічний набір записів, без повторного дебету."),
    case(8, "AC-04", "Правильна точка вильоту", "Обробити ініціатори двох екіпажів на різних точках.", "Кожен статус і залишок належить своїй точці; чужа точка незмінна."),
    case(9, "AC-05", "Підпис у Журналі з Файти", "Відкрити журнал після автоматичної обробки ініціатора.", "Показано правильні ресурс, точку й погоджений підпис автоматичного завершення.", ui=True),
    case(10, "AC-03", "Порожній постачальник: звичайне списання", "Завершити ініціатор із порожнім значенням властивості «Постачальник» за достатнього залишку точки.", "Залишок точки зменшується на кількість події.", automated=True, result_note="12.10.2026 на dev ERP виявлено дефект: запис завершився без списання."),
    case(11, "AC-02", "Змішані постачальники: звичайне списання", "Завершити ініціатор, зіставлений із ресурсами постачальників «ПМ Цукрарня» та іншого постачальника.", "Залишок точки зменшується на кількість події.", automated=True, result_note="12.10.2026 на dev ERP перевірка пройшла."),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
