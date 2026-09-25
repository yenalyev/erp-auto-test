#!/usr/bin/env python3
"""Synchronize REQ-NOTIF documentation and automated cases with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-NOTIF"
core.FEATURE_PARENT_ID = "REQ-INTEGRATION"
core.FEATURE_TITLE = "Система сповіщень"
core.FEATURE_DESCRIPTION = (
    "Адміністративні та персональні підписки, браузерні події замовлень, "
    "журнал і зовнішня доставка сповіщень."
)
core.FEATURE_MODULE = "NOTIF"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-NOTIF-notifications.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-02",
        "GET шаблонів повертає актуальні seeded коди, включно з order_created і "
        "production_order_created, з полями code/description/template/state; "
        "invoice_generated і tech_map_mode_changed відсутні.",
    ),
    (
        "AC-09",
        "GET /notifications/my повертає актуальні персональні шаблони. Підписку на "
        "order_created або production_order_created можна створити, повторно прочитати "
        "і видалити; видалені коди у персональній конфігурації відсутні.",
    ),
    (
        "AC-10",
        "Після створення нового замовлення підписаний адміністратор замовлень отримує "
        "browser notification order_created для локації замовлення з template_code і storage_name.",
    ),
    (
        "AC-11",
        "Після створення нового виробничого замовлення підписаний користувач отримує "
        "browser notification production_order_created для цільової локації з "
        "template_code і storage_name.",
    ),
    (
        "AC-12",
        "У «Моїх сповіщеннях» адміністратор замовлень може ввімкнути «Нове замовлення», "
        "а глобальний Admin — «Нове виробниче замовлення». Окремої ролі адміністратора "
        "виробничих замовлень наразі немає; після її появи другий сценарій переводиться "
        "на permission-based роль з production-order.manage.",
    ),
]


def steps(*rows: tuple[str, str]) -> list[dict[str, Any]]:
    return [
        {"stepOrder": index, "actionText": action, "expectedText": expected}
        for index, (action, expected) in enumerate(rows, start=1)
    ]


def case(
    test_id: str,
    ac_key: str,
    title: str,
    description: str,
    preconditions: str,
    expected: str,
    case_steps: list[dict[str, Any]],
    *,
    ui: bool = False,
) -> dict[str, Any]:
    return {
        "featureId": core.FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": description,
        "priority": "CRITICAL" if test_id in {"TC-NOTIF-050", "TC-NOTIF-051"} else "HIGH",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "UI" if ui else "FUNCTIONAL",
        "preconditions": preconditions,
        "expectedResult": expected,
        "tags": "notifications,orders,automated",
        "apiAutomationIds": [] if ui else [test_id],
        "uiAutomationIds": [test_id] if ui else [],
        "steps": case_steps,
    }


core.CASES = [
    case(
        "TC-NOTIF-010",
        "AC-02",
        "API — актуальний каталог seeded notification templates",
        "Перевіряє нові шаблони замовлень і видалення застарілих кодів.",
        "Авторизований Admin; notification templates ініціалізовані.",
        "order_created і production_order_created присутні; invoice_generated і "
        "tech_map_mode_changed відсутні.",
        steps(
            ("Викликати GET /api/v1/notifications/templates.", "HTTP 200; повернуто список шаблонів."),
            ("Перевірити коди шаблонів.", "Нові коди присутні, обидва видалені коди відсутні."),
        ),
    ),
    case(
        "TC-NOTIF-040",
        "AC-09",
        "API — адміністратор замовлень керує підпискою order_created",
        "Перевіряє персональний subscribe/read/delete flow для нового замовлення.",
        "Активний ORDER_ADMIN з доступом до тестової локації.",
        "order_created доступний; підписка зберігається й видаляється.",
        steps(
            ("GET /notifications/my під ORDER_ADMIN.", "Є шаблон «Нове замовлення»."),
            ("POST підписку order_created для локації.", "Підписку збережено."),
            ("Повторити GET, потім DELETE і ще один GET.", "Спочатку subscribed=true, після DELETE=false."),
        ),
    ),
    case(
        "TC-NOTIF-041",
        "AC-09",
        "API — підписка production_order_created з production-order.manage",
        "API-контракт майбутньої permission-based ролі; окремої UI-ролі наразі немає.",
        "Активний користувач має production-order.manage на тестовій локації.",
        "production_order_created доступний через API; підписка зберігається й видаляється.",
        steps(
            ("GET /notifications/my.", "Є шаблон «Нове виробниче замовлення»."),
            ("POST підписку production_order_created.", "Підписку збережено."),
            ("Перевірити GET, виконати DELETE і перевірити знову.", "Стан підписки коректно змінюється."),
        ),
    ),
    case(
        "TC-NOTIF-042",
        "AC-09",
        "API — видалені шаблони відсутні у «Моїх сповіщеннях»",
        "Захищає персональну конфігурацію від повернення deprecated шаблонів.",
        "Авторизований активний користувач.",
        "invoice_generated і tech_map_mode_changed не повертаються.",
        steps(
            ("Викликати GET /notifications/my.", "HTTP 200."),
            ("Зібрати всі template codes із груп.", "Обидва видалені коди відсутні."),
        ),
    ),
    case(
        "TC-NOTIF-050",
        "AC-10",
        "Нове замовлення надсилає order_created",
        "Перевіряє browser notification після створення складського замовлення.",
        "ORDER_ADMIN підписаний на order_created для requester location; є валідний ресурс.",
        "Після create з'являється order_created з правильною назвою локації.",
        steps(
            ("Підписати ORDER_ADMIN на order_created для requester location.", "Підписку активовано."),
            ("Іншим користувачем створити нове замовлення.", "Замовлення створено у NEW."),
            ("Опитати browser notifications ORDER_ADMIN.", "Є order_created з template_code і storage_name."),
        ),
    ),
    case(
        "TC-NOTIF-051",
        "AC-11",
        "Нове виробниче замовлення надсилає production_order_created",
        "Перевіряє browser notification після створення виробничого замовлення.",
        "Користувач із production-order.manage підписаний для цільової локації; Admin може створити ВЗ.",
        "Після create з'являється production_order_created з правильною назвою локації.",
        steps(
            ("Підписати користувача на production_order_created.", "Підписку активовано."),
            ("Admin створює нове виробниче замовлення.", "ВЗ створене."),
            ("Опитати browser notifications підписаного користувача.", "Є production_order_created з template_code і storage_name."),
        ),
    ),
    case(
        "TC-NOTIF-UI-020",
        "AC-12",
        "UI — ORDER_ADMIN вмикає «Нове замовлення»",
        "Перевіряє рольову видимість та збереження перемикача персональної підписки.",
        "Активний ORDER_ADMIN має доступ до локації.",
        "Рядок «Нове замовлення» видимий, перемикач створює API-підписку.",
        steps(
            ("Увійти як ORDER_ADMIN і відкрити меню профілю → «Мої сповіщення».", "Відкрито персональну таблицю."),
            ("Знайти «Нове замовлення» та увімкнути перемикач.", "Перемикач увімкнений, API повертає subscribed=true."),
        ),
        ui=True,
    ),
    case(
        "TC-NOTIF-UI-021",
        "AC-12",
        "UI — глобальний Admin вмикає «Нове виробниче замовлення»",
        "Поточний сценарій виконується глобальним Admin, бо окремої ролі адміністратора "
        "виробничих замовлень ще немає. Після її появи перейти на production-order.manage.",
        "Авторизований глобальний Admin; вибрана активна локація.",
        "Рядок «Нове виробниче замовлення» видимий, перемикач створює API-підписку.",
        steps(
            ("Увійти як глобальний Admin і відкрити меню профілю → «Мої сповіщення».", "Відкрито персональну таблицю."),
            ("Знайти «Нове виробниче замовлення» та увімкнути перемикач.", "Перемикач увімкнений, API повертає subscribed=true."),
        ),
        ui=True,
    ),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
