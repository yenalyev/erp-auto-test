#!/usr/bin/env python3
"""Upsert fly-point archiving requirements and cases in one TCM environment.

Run once per environment with its own URL and token. Does not publish test runs.
"""
from __future__ import annotations

from pathlib import Path
from typing import Any

import sync_tcm_relocation_batch_accounting as core


ROOT = Path(__file__).resolve().parent.parent
core.FEATURE_ID = "REQ-CREW-004"
core.FEATURE_PARENT_ID = "REQ-CREW"
core.FEATURE_TITLE = "Архівація точки вильоту"
core.FEATURE_DESCRIPTION = (
    "Архівація точки після врегулювання активних екіпажів, фізичних залишків "
    "і резерву з перевіркою прав та серверних блокерів."
)
core.FEATURE_MODULE = "CREW"
core.FEATURE_PRIORITY = "CRITICAL"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-CREW-004-fly-point-archiving.md"
core.ACCEPTANCE_CRITERIA = [
    ("AC-01", "Адміністратор і комірник лише з ролями «Керівник локації» та «Екіпажі: перегляд» можуть архівувати свою порожню активну точку через кнопку на картці; сторонні користувачі не можуть. Історія зберігається."),
    ("AC-02", "Активні екіпажі блокують архівацію точки в UI та API. Їх переміщують лише на активну точку свого підрозділу або архівують за відсутності власного/проксованого залишку й активних операцій."),
    ("AC-03", "Додатний фізичний залишок і резерв блокують архівацію. Попередження показує назву, одиницю, кількість і партію; резерв відображається всередині фізичної кількості без подвоєння і знімається вручну. Лише вантаж у дорозі не блокує."),
    ("AC-04", "Переміщення залишків і надзвичайна подія відкривають чинні форми та після успішного сабміту повертають на /fly-points/close/{id}; скасування й помилка не змінюють дані."),
    ("AC-05", "Якщо є екіпажі та залишки, спершу показуються екіпажі. Сервер повторно перевіряє блокери під час архівації та відхиляє застаріле підтвердження після конкурентної зміни."),
    ("AC-06", "Після архівації точка має active=false, відсутня серед активних точок і цілей нових операцій, але доступна у фільтрі «Всі» та історії за чинними правилами."),
]


def case(
    number: int,
    ac_key: str,
    title: str,
    action: str,
    expected: str,
    *,
    automation: str = "manual",
    priority: str = "HIGH",
) -> dict[str, Any]:
    test_id = f"TC-FPA-{number:03d}"
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": (
            f"Архівація точки вильоту. Покриття: {automation}. "
            "DEV-результати за 30.09.2026 наведені у feature documentation; "
            "публікація кейсу не є результатом запуску на PROD ERP."
        ),
        "priority": priority,
        "severity": "CRITICAL" if priority == "CRITICAL" else "MAJOR",
        "status": "ACTIVE",
        "testType": "UI",
        "preconditions": (
            "Є батальйон із тестовими точками P1/P2; адміністратор і комірник "
            "зі стандартними ролями. Дані сценарію ізольовані від інших тестів."
        ),
        "expectedResult": expected,
        "tags": "crew,fly-point,archiving," + automation.replace(" ", "-"),
        "apiAutomationIds": [],
        "uiAutomationIds": [test_id] if automation != "manual" else [],
        "steps": [
            {"stepOrder": 1, "actionText": action, "expectedText": expected},
            {"stepOrder": 2, "actionText": "Звірити стан точки, екіпажу та залишків через API й UI.", "expectedText": expected},
        ],
    }


core.CASES = [
    case(1, "AC-01", "Адміністратор архівує порожню точку", "На картці порожньої P1 натиснути «Архівувати» та підтвердити.", "P1 active=false; немає в «Активні», є у «Всі».", automation="automated", priority="CRITICAL"),
    case(2, "AC-01", "Комірник архівує без додаткових прав", "Увійти комірником лише з двома погодженими ролями та архівувати порожню P1.", "Кнопка видима, архівація дозволена; комірнику не видано додаткових грантів.", automation="automated; DEV user-create 403", priority="CRITICAL"),
    case(3, "AC-02", "Активний екіпаж блокує точку", "Додати C1 до P1; перевірити UI й прямий DELETE точки.", "Попередження про екіпаж; DELETE повертає 4xx, P1 активна.", automation="automated", priority="CRITICAL"),
    case(4, "AC-02", "Переміщення екіпажу й архівація точки", "Перемістити C1 з P1 на активну P2 свого батальйону й архівувати P1.", "C1.parent=P2; P1 active=false; чужі й архівні цілі недоступні.", automation="partial automation", priority="CRITICAL"),
    case(5, "AC-03", "Залишок і повний перелік у попередженні", "Створити фізичний залишок з партією на P1; відкрити архівацію й спробувати прямий DELETE.", "У переліку є назва, одиниця, кількість, партія; DELETE відхилено.", automation="automated", priority="CRITICAL"),
    case(6, "AC-04", "Переходи та повернення після врегулювання", "З попередження відкрити переміщення та подію; зберегти кожну форму.", "Форми мають правильні маршрути й returnTo; після успіху повернення до архівації.", automation="partial automation: routes only"),
    case(7, "AC-03", "Вантаж у дорозі не блокує", "Видати весь фізичний залишок P1 на P2, залишивши операцію в дорозі, та архівувати P1.", "За нульового фізичного залишку архівація успішна.", automation="automated", priority="CRITICAL"),
    case(8, "AC-03", "Ручне зняття резерву", "Зарезервувати M з N на P1, зняти бронь користувачем з правом на замовлення, врегулювати N.", "Резерв не подвоює N; після ручного зняття можна перемістити/списати й архівувати.", priority="CRITICAL"),
    case(9, "AC-02", "Заборона архівації екіпажу з балансом чи операцією", "Спробувати архівувати прикріплений C1 із балансом на точці та C2 з активною операцією.", "Обидві дії, включно з прямим DELETE, відхилені; екіпажі активні.", automation="partial automation: attached balance API", priority="CRITICAL"),
    case(10, "AC-05", "Екіпажі й залишок одночасно", "Створити C1 і запас на P1; перемістити C1, потім урегулювати залишок.", "Порядок блокерів: екіпажі → залишки; фінальне підтвердження доступне лише після обох.", automation="partial automation: blocker order", priority="CRITICAL"),
    case(11, "AC-04", "Скасування і помилки форм", "Скасувати сторінку архівації та форми екіпажів, переміщення й події; перевірити 4xx/5xx.", "Без успішного сабміту стан незмінний; помилка показана в контексті форми.", automation="partial automation: archive cancel"),
    case(12, "AC-01", "Права й ізоляція іншого батальйону", "Комірником іншого батальйону й користувачем без прав відкрити P1 та надіслати прямі запити.", "Дані чужої точки не розкриті, всі мутації відхилені.", priority="CRITICAL"),
    case(13, "AC-05", "Конкурентна зміна перед підтвердженням", "Відкрити порожню P1; окремим запитом додати C1; підтвердити архівацію.", "DELETE відхилено, P1 активна, UI показує змінений стан.", automation="partial automation: crew race", priority="CRITICAL"),
    case(14, "AC-06", "Стан після архівації", "Перевірити архівну P1 у фільтрах, історії та селекторах нових операцій.", "P1 відсутня серед активних цілей, доступна як неактивна з історією."),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
