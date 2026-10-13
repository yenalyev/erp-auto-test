#!/usr/bin/env python3
"""Synchronize verified 50MB upload documentation and six cases with TCM."""
from __future__ import annotations

from pathlib import Path

import sync_tcm_relocation_batch_accounting as core


ROOT = Path(__file__).resolve().parent.parent
core.FEATURE_ID = "REQ-UPLOAD-50MB"
core.FEATURE_PARENT_ID = None
core.FEATURE_TITLE = "Завантаження файлів до 50MB"
core.FEATURE_DESCRIPTION = (
    "Правила завантаження файлів проєктного виробництва та фото ресурсу: "
    "розміри, помилки й збереження."
)
core.FEATURE_MODULE = "UPLOAD"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-UPLOAD-50MB.md"
core.ACCEPTANCE_CRITERIA = [
    ("AC-01", "Невеликий допустимий файл можна завантажити до проєктного виробництва й пізніше відкрити без зміни вмісту."),
    ("AC-02", "Файл або завантаження, загальний розмір якого перевищує 50 МБ, система відхиляє з повідомленням «Розмір файлу перевищує допустимий 50MB.»; відхилений файл не зберігається."),
    ("AC-03", "Якщо файл для проєктного виробництва перевищує ліміт, форма показує повідомлення про помилку й не повідомляє про успішне збереження."),
    ("AC-04", "Невелике фото ресурсу можна завантажити й відкрити. Форма приймає одне фото до 5 МБ; фото понад серверний ліміт 50 МБ відхиляється."),
    ("AC-05", "Якщо кілька файлів проєктного виробництва разом зі службовими даними перевищують 50 МБ, система відхиляє все завантаження."),
]


def case(test_id: str, ac_key: str, title: str, action: str, expected: str, *, ui: bool = False) -> dict:
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": title,
        "priority": "HIGH",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "UI" if ui else "FUNCTIONAL",
        "preconditions": "Користувач з правом редагування, ізольовані тестові записи та стандартний MULTIPART_MAX_SIZE=50MB на dev.",
        "expectedResult": expected,
        "tags": "upload,50mb,automated",
        "apiAutomationIds": [] if ui else [test_id],
        "uiAutomationIds": [test_id] if ui else [],
        "steps": [
            {"stepOrder": 1, "actionText": action, "expectedText": expected},
            {"stepOrder": 2, "actionText": "Перевірити відповідь і стан збереженого запису.", "expectedText": expected},
        ],
    }


ERROR = "Розмір файлу перевищує допустимий 50MB."
core.CASES = [
    case("TC-UPLOAD-50-01", "AC-01", "Успішне завантаження PDF", "Завантажити малий PDF до проєктного виробництва й отримати його назад.", "Успішна відповідь, вкладення наявне, байти збігаються."),
    case("TC-UPLOAD-50-02", "AC-02", "Повідомлення backend для PDF понад ліміт", "Надіслати PDF 51 МіБ до проєктного виробництва.", f"HTTP 400, повідомлення «{ERROR}», список вкладень незмінний."),
    case("TC-UPLOAD-50-03", "AC-03", "Повідомлення про ліміт у UI", "У формі редагування проєктного виробництва додати PDF 51 МіБ.", f"UI показує «{ERROR}», не показує успіх, файл не збережено.", ui=True),
    case("TC-UPLOAD-50-04", "AC-04", "Успішне завантаження фото ресурсу", "Надіслати мале PNG-фото ресурсу й отримати фото через GET.", "Завантаження успішне, imagePath заповнено, GET фото повертає 200 і вміст."),
    case("TC-UPLOAD-50-05", "AC-04", "Повідомлення для фото ресурсу понад ліміт", "Надіслати PNG 51 МіБ на API фото ресурсу.", f"HTTP 400, повідомлення «{ERROR}», imagePath порожній, GET фото повертає 404."),
    case("TC-UPLOAD-50-06", "AC-05", "Десять файлів по 5 МіБ в одному запиті", "Надіслати один multipart-запит створення проєктного виробництва з десятьма PNG по 5 МіБ.", f"Повний запит понад 50MB відхилено з HTTP 400 і повідомленням «{ERROR}»."),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
