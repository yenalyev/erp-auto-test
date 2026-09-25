#!/usr/bin/env python3
"""Idempotently synchronize REQ-FAITA-001 documentation and automated cases with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-FAITA-001"
core.FEATURE_PARENT_ID = "REQ-INTEGRATION"
core.FEATURE_TITLE = "Ресурси Файти — зіставлення та додаткові ресурси"
core.FEATURE_DESCRIPTION = (
    "Зіставлення FAITA/Fight з номенклатурою ERP, повний список implicit-ресурсів "
    "і довільний додатний цілий множник count для списання amount × count."
)
core.FEATURE_MODULE = "FAITA"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-FAITA-001-faita-resources.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-01",
        "Один виріб FAITA можна зіставити з кількома ресурсами ERP: створити кілька "
        "прив’язок одним запитом, додати ще одну й видалити окрему. Виріб з’являється "
        "у GET /integrations/faita/resources лише після першого FLIGHT reconciliation; "
        "POST не знімає невідмічені прив’язки.",
    ),
    (
        "AC-02",
        "До зіставленого виробу можна зберегти кілька додаткових FAITA-ресурсів повним "
        "списком PUT, замінити набір і прибрати позиції. Опції implicit — лише інші "
        "зіставлені FAITA-вироби.",
    ),
    (
        "AC-03",
        "UI /faita-resources підтримує пошук і фільтри списку, CRUD зіставлень та CRUD "
        "додаткових ресурсів. Додати implicit можна лише після створення зіставлення; "
        "список і картка показують множник count.",
    ),
    (
        "AC-04",
        "Для кожного implicit зберігається довільний цілий count >= 1. Його можна "
        "редагувати без втрати сусідніх зв’язків; при usage основного ресурсу в кількості "
        "N додатковий ресурс списується в кількості N × count, а legacy-запис без count "
        "трактується як count=1.",
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
    ui: bool = False,
    owner_feature_id: str | None = None,
    owner_ac_key: str | None = None,
) -> dict[str, Any]:
    payload = {
        "featureId": core.FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": description,
        "priority": "CRITICAL",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "UI" if ui else "FUNCTIONAL",
        "preconditions": preconditions,
        "expectedResult": expected,
        "tags": "faita,implicit,count,automated," + ("ui" if ui else "api"),
        "apiAutomationIds": [] if ui else [test_id],
        "uiAutomationIds": [test_id] if ui else [],
        "steps": steps,
    }
    if owner_feature_id:
        payload["_ownerFeatureId"] = owner_feature_id
        payload["_ownerAcKey"] = owner_ac_key or ac_key
    return payload


API_PRECONDITIONS = (
    "FAITA API доступне; ADMIN session; тест створює ізольовані ERP-ресурси й FLIGHT "
    "reconciliation та очищає створені зв’язки після виконання."
)
UI_PRECONDITIONS = (
    "FAITA API і /faita-resources доступні; ADMIN session; тест створює ізольовані "
    "ERP/FAITA дані та очищає їх після виконання."
)

core.CASES = [
    case(
        "TC-FAITA-REC-001", "AC-01", "Створення 1 FAITA → 2 ERP",
        "Перевіряє multi-resource FLIGHT reconciliation і появу виробу у FAITA list.",
        API_PRECONDITIONS,
        "Створено дві прив’язки; GET FAITA resources містить обидва ERP id та назви.",
        [
            step(1, "Створити два ERP-ресурси та POST FLIGHT reconciliation з обома resourceIds.", "HTTP 200; повернуто два id прив’язок."),
            step(2, "Прочитати виріб через GET /integrations/faita/resources.", "Виріб присутній і має рівно обидва ERP-ресурси."),
        ],
    ),
    case(
        "TC-FAITA-REC-002", "AC-01", "Додавання третього ERP до зіставлення",
        "Перевіряє, що повторний POST розширює, а не замінює reconciliation.",
        API_PRECONDITIONS,
        "GET FAITA resources містить попередні два та новий третій ERP без втрати зв’язків.",
        [
            step(1, "Створити FAITA-виріб із двома ERP-прив’язками.", "У виробу є дві прив’язки."),
            step(2, "Повторити POST для того самого externalId з третім resourceId.", "У списку присутні всі три ERP-ресурси."),
        ],
    ),
    case(
        "TC-FAITA-REC-003", "AC-01", "Видалення однієї з двох прив’язок",
        "Перевіряє адресне DELETE reconciliation без видалення сусіднього зв’язку.",
        API_PRECONDITIONS,
        "Видалена лише вибрана прив’язка; друга лишилася у FAITA list.",
        [
            step(1, "Створити виріб із двома ERP-прив’язками та зберегти їх id.", "GET показує дві прив’язки."),
            step(2, "DELETE /resources/reconciliations/{id} для однієї прив’язки.", "GET показує рівно одну невидалену прив’язку."),
        ],
    ),
    case(
        "TC-FAITA-IMPL-001", "AC-04", "Збереження кількох implicit із різними count",
        "CPMA-832: перевіряє persist A×2 і C×3 у PUT response, GET list та DB config.",
        API_PRECONDITIONS + " Запуск із -Duse.database=true потрібен для перевірки sync_process_config.",
        "PUT і GET містять A×2 та C×3; при доступній БД JSON implicit_resource_usage містить ті самі externalId/count.",
        [
            step(1, "Створити зіставлення B, A, C та PUT для B зі списком A×2, C×3.", "HTTP 200; response містить обидва implicit і точні count."),
            step(2, "Прочитати GET FAITA resources і sync_process_config при use.database=true.", "API та DB зберегли A×2 і C×3 без втрати значень."),
        ],
        owner_feature_id="REQ-CREW-003",
        owner_ac_key="AC-15",
    ),
    case(
        "TC-FAITA-IMPL-002", "AC-04", "DB-backed complete списує amount × count з FLY_POINT",
        "CPMA-832: перевіряє журнал, complete і залишки для B=4, A=8, C=12. "
        "Тест не викликає /faita/log або /faita/syncTeams і не доводить роботу sync job.",
        API_PRECONDITIONS + " Обов’язково -Duse.database=true; attached CREW, FLY_POINT і достатній stock B/A/C.",
        "Три write-off мають спільний sourceId та amounts 4/8/12; complete списує відповідні кількості з FLY_POINT.",
        [
            step(1, "Налаштувати B → A×2, C×3 та підготувати attached CREW із stock на FLY_POINT.", "Конфігурація й залишки готові до списання."),
            step(2, "Через БД створити PENDING write-off B=4, A=8, C=12 зі спільним sourceId.", "API журнал містить рівно три записи з очікуваними amounts."),
            step(3, "Виконати complete для трьох write-off і повторно прочитати stock.", "FLY_POINT зменшився на B=4, A=8, C=12; CREW не отримав помилковий debit."),
        ],
        owner_feature_id="REQ-CREW-003",
        owner_ac_key="AC-15",
    ),
    case(
        "TC-FAITA-IMPL-003", "AC-02", "Full-list PUT видаляє одну implicit-позицію",
        "Перевіряє семантику повного списку implicit і збереження count позиції, що лишилася.",
        API_PRECONDITIONS,
        "Після PUT з одним елементом C видалено, A лишилося з count=2 у response і GET.",
        [
            step(1, "Зберегти для B повний список A×2, C×4.", "Обидві позиції присутні з правильними count."),
            step(2, "Повторити PUT лише з A×2 і прочитати GET.", "Присутнє лише A×2; C відсутнє."),
        ],
    ),
    case(
        "TC-FAITA-IMPL-004", "AC-04", "Редагування count без втрати сусідніх implicit",
        "CPMA-832: перевіряє зміну A×2 → A×5 при незмінному C×3 через full-list PUT.",
        API_PRECONDITIONS,
        "PUT response і GET містять рівно A×5 та C×3, без втрати або дублювання.",
        [
            step(1, "Зберегти для B список A×2, C×3.", "GET повертає A×2 і C×3."),
            step(2, "Надіслати повний список A×5, C×3 і перечитати виріб.", "A має count=5, C має count=3; кожна позиція одна."),
        ],
    ),
    case(
        "TC-UI-FAITA-001", "AC-03", "Список показує reconciliation та implicit count",
        "Перевіряє sidebar, фільтри, пошук і формат «назва × count» у списку FAITA.",
        UI_PRECONDITIONS,
        "Після пошуку видно потрібний рядок, ERP-назву, implicit-назву та множник ×4.",
        [
            step(1, "Створити виріб з одним ERP і одним implicit×4; відкрити сторінку через sidebar.", "Видимі фільтри типу та таблиця FAITA."),
            step(2, "Знайти виріб за назвою.", "У рядку видно reconciliation, implicit і «× 4»."),
        ],
        ui=True,
    ),
    case(
        "TC-UI-FAITA-002", "AC-03", "Додавання другого ERP на картці",
        "Перевіряє діалог «Призначити ресурс» на картці FAITA-виробу.",
        UI_PRECONDITIONS,
        "Після додавання на картці одночасно відображаються перший і другий ERP.",
        [
            step(1, "Відкрити картку виробу з одним reconciliation.", "Перший ERP відображається."),
            step(2, "Через діалог вибрати й призначити другий ERP.", "Обидві ERP-назви відображаються без втрати першої."),
        ],
        ui=True,
    ),
    case(
        "TC-UI-FAITA-003", "AC-03", "Видалення одного ERP на картці",
        "Перевіряє UI-видалення однієї прив’язки та доступність implicit CTA.",
        UI_PRECONDITIONS,
        "Вибраний ERP зник, другий лишився, кнопка додавання implicit видима.",
        [
            step(1, "Відкрити картку виробу з двома ERP-прив’язками.", "Видимі обидва ERP."),
            step(2, "Натиснути «−» для першої прив’язки.", "Першого ERP немає; другий і CTA «Додати ресурс» лишилися."),
        ],
        ui=True,
    ),
    case(
        "TC-UI-FAITA-004", "AC-04", "Валідація, додавання, редагування і видалення count",
        "CPMA-832: наскрізний UI CRUD довільної кількості додаткового ресурсу.",
        UI_PRECONDITIONS,
        "count=0 заблоковано без PUT; implicit додано як ×3, змінено на ×5 і видалено.",
        [
            step(1, "У формі додавання implicit ввести count=0.", "UI блокує значення і не надсилає PUT."),
            step(2, "Додати implicit з count=3.", "На картці відображається «× 3»."),
            step(3, "Відредагувати count на 5.", "На картці відображається «× 5», інші зв’язки не змінені."),
            step(4, "Видалити implicit кнопкою «−».", "Implicit більше не відображається."),
        ],
        ui=True,
    ),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
