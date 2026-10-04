#!/usr/bin/env python3
"""Synchronize Resource Viewer battalion documentation and cases with TCM."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from sync_tcm_relocation_batch_accounting import TcmClient


PROJECT_ID = 1
FEATURE_ID = "REQ-RVW-UI"
DOCUMENTATION_PATH = Path(__file__).resolve().parent.parent / "docs" / "REQ-RVW-UI.md"
ACCEPTANCE_CRITERIA = {
    "AC-07": "Поле «Батальйони» відкриває список доступних батальйонів ПМ без дублікатів; вибір можливий, якщо конкретного отримувача не задано.",
    "AC-08": "Вибір батальйону вимикає «Отримувачі», «ПМ 414», «СБС без ПМ 414» та «Інші»; раніше вибрана група не впливає на пошук. Вибір конкретного отримувача вимикає батальйон.",
    "AC-09": "«Очистити» скидає вибір батальйону або конкретного отримувача і знову активує всі фільтри отримувачів.",
    "AC-10": "Пошук, підсумки та Excel-експорт за батальйоном містять лише його переміщення; зміна батальйону оновлює результат, а за відсутності даних результат порожній.",
}


def case(
    test_id: str,
    ac_key: str,
    title: str,
    preconditions: str,
    steps: list[tuple[str, str]],
    *,
    automated: bool = False,
    priority: str = "HIGH",
) -> dict[str, Any]:
    return {
        "featureId": FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": "Фільтр батальйонів у журналі переміщень ресурсів.",
        "priority": priority,
        "severity": "CRITICAL" if priority == "CRITICAL" else "MAJOR",
        "status": "ACTIVE",
        "testType": "UI",
        "preconditions": preconditions,
        "expectedResult": steps[-1][1],
        "tags": "resource-viewer,battalion,ui," + ("automated" if automated else "manual"),
        "apiAutomationIds": [],
        "uiAutomationIds": [test_id] if automated else [],
        "steps": [
            {"stepOrder": index, "actionText": action, "expectedText": expected}
            for index, (action, expected) in enumerate(steps, 1)
        ],
    }


PRE = "Відкрити /resources-viewer/relocation під користувачем із правом «Відстеження ресурсів»; очистити фільтри."
PRE_DATA = PRE + " Підготувати ресурс і контрольні переміщення до двох батальйонів ПМ та стороннього отримувача."

CASES = [
    case("TC-UI-RVW-006", "AC-07", "Список батальйонів ПМ", PRE,
         [("Натиснути поле «Батальйони».", "Відкривається непорожній список батальйонів ПМ."),
          ("Перевірити значення списку.", "Значення не дублюються; повноту відносно довідника ПМ звірити окремо.")],
         automated=True, priority="CRITICAL"),
    case("TC-UI-RVW-007", "AC-08", "Батальйон вимикає інші фільтри отримувачів", PRE,
         [("Увімкнути «Інші», вибрати батальйон.", "«Отримувачі», «ПМ 414», «СБС без ПМ 414» та «Інші» неактивні."),
          ("Виконати пошук і перевірити запит.", "Попередня група «Інші» не передається в пошук за батальйоном.")],
         automated=True, priority="CRITICAL"),
    case("TC-UI-RVW-008", "AC-08", "Конкретний отримувач вимикає батальйон", PRE,
         [("Вибрати конкретного отримувача.", "Селектор батальйону стає неактивним.")],
         automated=True, priority="CRITICAL"),
    case("TC-UI-RVW-009", "AC-09", "Очищення відновлює фільтри отримувачів", PRE,
         [("Вибрати батальйон і натиснути «Очистити».", "Батальйон, «Отримувачі» та три чекбокси знову активні."),
          ("Вибрати конкретного отримувача і натиснути «Очистити».", "Батальйон знову доступний; старий вибір скинуто.")],
         automated=True),
    case("TC-UI-RVW-010", "AC-10", "Точність фільтрації за батальйоном", PRE_DATA,
         [("Вибрати ресурс, перший батальйон і виконати пошук; повторити для другого.",
           "Журнал і підсумки містять лише переміщення вибраного батальйону; суми відповідають рядкам.")]),
    case("TC-UI-RVW-011", "AC-10", "Зміна батальйону оновлює результат", PRE_DATA,
         [("Після пошуку за першим батальйоном вибрати другий і повторити пошук.",
           "Таблиця та підсумки оновилися; записи першого батальйону зникли.")]),
    case("TC-UI-RVW-012", "AC-10", "Порожній результат за батальйоном", PRE,
         [("Вибрати ресурс без переміщень до вибраного батальйону і виконати пошук.",
           "Журнал порожній; підсумки не містять сторонніх переміщень; помилки інтерфейсу немає.")]),
    case("TC-UI-RVW-013", "AC-10", "Excel-експорт із фільтром батальйону", PRE_DATA,
         [("Виконати пошук за батальйоном та експортувати Excel.",
           "Файл відкривається і містить той самий набір переміщень, що й журнал.")]),
]


def sync(client: TcmClient) -> dict[str, Any]:
    documentation = DOCUMENTATION_PATH.read_text(encoding="utf-8").rstrip() + "\n"
    feature_path = f"/api/ai/projects/{PROJECT_ID}/features/{FEATURE_ID}"
    feature = client.request("GET", feature_path)
    feature_payload = {
        key: feature[key]
        for key in ("featureId", "parentFeatureId", "title", "description", "module", "priority", "status")
    }
    feature_payload["documentation"] = documentation
    client.request("PUT", feature_path, feature_payload)

    feature = client.request("GET", feature_path)
    existing_ac = {item["acId"]: item for item in feature.get("acceptanceCriteria", [])}
    ac_ids: dict[str, int] = {}
    for key, text in ACCEPTANCE_CRITERIA.items():
        if key in existing_ac:
            response = client.request(
                "PUT", f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria/{existing_ac[key]['id']}",
                {"featureId": FEATURE_ID, "text": text},
            )
        else:
            response = client.request(
                "POST", f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria",
                {"featureId": FEATURE_ID, "acKey": key, "text": text},
            )
        ac_ids[key] = int(response["id"])

    created: list[str] = []
    updated: list[str] = []
    for item in CASES:
        test_id = item["testId"]
        path = f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}"
        existing = client.get_optional(path)
        payload = {**item, "acceptanceCriterionId": ac_ids[item["acKey"]]}
        if existing is None:
            client.request("POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", payload)
            created.append(test_id)
        else:
            client.request("PUT", path, payload)
            updated.append(test_id)

    feature_check = client.request("GET", feature_path)
    if feature_check.get("documentation") != documentation:
        raise RuntimeError("TCM feature documentation differs from the source file")
    actual_ac = {item["acId"]: item for item in feature_check.get("acceptanceCriteria", [])}
    if not set(ACCEPTANCE_CRITERIA).issubset(actual_ac):
        raise RuntimeError("TCM acceptance criteria are incomplete")
    for item in CASES:
        test_id = item["testId"]
        path = f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}"
        current = client.request("GET", path)
        if (current.get("featureId") != FEATURE_ID
                or current.get("acId") != item["acKey"]
                or (current.get("uiAutomationIds") or []) != item["uiAutomationIds"]):
            raise RuntimeError(f"TCM case differs from source: {test_id}")

    return {
        "baseUrl": client.base_url,
        "featureId": FEATURE_ID,
        "documentationSha256": hashlib.sha256(documentation.encode("utf-8")).hexdigest(),
        "acceptanceCriteria": list(ACCEPTANCE_CRITERIA),
        "testCasesCreated": created,
        "testCasesUpdated": updated,
        "verifiedCaseCount": len(CASES),
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "result": "SUCCESS",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--insecure", action="store_true")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    token = os.getenv(args.token_env)
    if not token:
        print(f"Missing token: set {args.token_env}", file=sys.stderr)
        return 2
    report = sync(TcmClient(args.base_url, token, args.insecure))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
