#!/usr/bin/env python3
"""Synchronize received-date documentation and automated cases with TCM.

The sync upserts documentation, acceptance criteria, and cases by business ID.
It does not publish ERP test run results.
"""
from __future__ import annotations

from pathlib import Path
from typing import Any

import sync_tcm_relocation_batch_accounting as core


ROOT = Path(__file__).resolve().parent.parent
core.FEATURE_ID = "REQ-EDIT_REL-009"
core.FEATURE_PARENT_ID = "REQ-EDIT_REL"
core.FEATURE_TITLE = "Дата отримання у журналі переміщень"
core.FEATURE_DESCRIPTION = (
    "Обрана дата зовнішнього отримання або видачі та дата прийняття внутрішнього "
    "переміщення зберігаються в колонці «Отримано»."
)
core.FEATURE_MODULE = "EDIT_REL"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs" / "RECEIVED_DATE_TEST_PLAN.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-01",
        "Під час зовнішнього отримання дата з поля «Дата» форми, включно з датою в минулому, "
        "записується в «Отримано» замість поточної дати.",
    ),
    (
        "AC-02",
        "Під час видачі зовнішньому отримувачу дата з форми видачі записується в «Отримано» "
        "для автоматично завершеного переміщення.",
    ),
    (
        "AC-03",
        "Діалог «Прийняти» для внутрішнього переміщення дозволяє обрати дату прийому від "
        "дати «Надіслано» до сьогодні включно. Дейтпікер працює як у формі «Отримано». "
        "Обрана дата зберігається в «Отримано», а «Надіслано» не змінюється.",
    ),
]


def case(
    test_id: str,
    ac_key: str,
    title: str,
    action: str,
    expected: str,
    *,
    ui: bool = False,
) -> dict[str, Any]:
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": f"Дата в журналі «Отримано»: {title}",
        "priority": "HIGH",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "UI" if ui else "FUNCTIONAL",
        "preconditions": (
            "Є активні внутрішні локації, зовнішній постачальник/отримувач, "
            "доступний ресурс і користувач із правом на операцію."
        ),
        "expectedResult": expected,
        "tags": "relocation,received-date,automated",
        "apiAutomationIds": [] if ui else [test_id],
        "uiAutomationIds": [test_id] if ui else [],
        "steps": [
            {"stepOrder": 1, "actionText": action, "expectedText": expected},
            {
                "stepOrder": 2,
                "actionText": "Знайти створене переміщення у журналі «Отримано» та звірити дати.",
                "expectedText": expected,
            },
        ],
    }


core.CASES = [
    case(
        "TC-REL-RECEIVED-DATE-001", "AC-01",
        "Зовнішнє отримання з обраною датою в минулому",
        "Через API отримати ресурс від зовнішнього постачальника, задавши дату три дні тому.",
        "Відповідь і запис журналу містять обрану дату в receivedAt за календарем Києва.",
    ),
    case(
        "TC-REL-RECEIVED-DATE-002", "AC-02",
        "Видача зовнішньому отримувачу з обраною датою",
        "Через API видати ресурс зовнішньому отримувачу, задавши дату два дні тому.",
        "Переміщення має стан AUTO_FINISHED; відповідь і журнал отримувача містять обрану дату в receivedAt.",
    ),
    case(
        "TC-REL-RECEIVED-DATE-003", "AC-03",
        "Окрема дата прийому внутрішнього переміщення",
        "Надіслати ресурс внутрішньому отримувачу датою чотири дні тому й прийняти його датою через два дні після надсилання.",
        "Дата надсилання лишається в date; відповідь і журнал показують обрану дату прийому в receivedAt.",
    ),
    case(
        "TC-UI-REL-RECEIVED-DATE-001", "AC-03",
        "Дейтпікер і межі дати в діалозі «Прийняти»",
        "Відкрити діалог для внутрішнього переміщення; перевірити початкову дату, нижню й верхню межі, заборону дат поза ними; обрати допустиму дату та підтвердити.",
        "Поле дати працює як у формі «Отримано», дозволяє дати від «Надіслано» до сьогодні включно; журнал показує обрану дату без зміни дати надсилання.",
        ui=True,
    ),
    case(
        "TC-UI-REL-RECEIVED-DATE-002", "AC-01",
        "Дата з UI-форми «Отримано»",
        "У формі зовнішнього отримання вибрати вчорашню дату й підтвердити операцію.",
        "Запис журналу «Отримано» має вчорашню дату, а не дату створення запису.",
        ui=True,
    ),
    case(
        "TC-UI-REL-RECEIVED-DATE-003", "AC-02",
        "Дата з UI-форми видачі назовні",
        "У формі видачі зовнішньому отримувачу вибрати вчорашню дату й підтвердити операцію.",
        "Автоматично завершене переміщення показує в журналі «Отримано» вибрану дату.",
        ui=True,
    ),
    {
        **case(
            "TC-REL-DATE-017", "AC-08",
            "Прийняття давнього переміщення з допустимою датою прийому",
            "Створити внутрішнє переміщення у TSUK-ієрархії датою 30 днів тому й прийняти його з обраною датою після надсилання та до сьогодні.",
            "Вік переміщення не забороняє прийняття; «Надіслано» зберігає стару дату, а «Отримано» показує обрану дату прийому.",
        ),
        "_ownerFeatureId": "REQ-EDIT_REL",
        "_ownerAcKey": "AC-08",
    },
]


if __name__ == "__main__":
    raise SystemExit(core.main())
