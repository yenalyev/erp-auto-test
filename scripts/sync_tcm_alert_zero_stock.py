#!/usr/bin/env python3
"""Append the zero-stock alert requirement to REQ-ALERT and upsert its TCM cases."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parent.parent
APPENDIX = (ROOT / "docs" / "REQ-ALERT-ZERO-STOCK-TCM.md").read_text(encoding="utf-8").strip()
START = "<!-- BEGIN ALERT_ZERO_STOCK_2026_10_01 -->"
END = "<!-- END ALERT_ZERO_STOCK_2026_10_01 -->"
FEATURE_ID = "REQ-ALERT"
AC_KEY = "AC-06"
AC_TEXT = (
    "На /inventory ресурс із amount=0 і налаштованим для вибраної локації сповіщенням "
    "видимий при вимкненому «Показувати нульові залишки» з кількістю 0, бейджем "
    "«відсутній» та порогом. Нульовий ресурс без сповіщення прихований. Увімкнений "
    "тогл показує обидва; після видалення сповіщення та вимкнення тогла ресурс "
    "зникає. Hierarchy GET із showZeroStock=false/true повертає відповідну вибірку."
)


def case(
    test_id: str,
    title: str,
    description: str,
    preconditions: str,
    expected: str,
    steps: list[tuple[str, str]],
    *,
    automated: bool,
    ui: bool,
) -> dict[str, Any]:
    return {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": test_id,
        "title": title,
        "description": description,
        "priority": "CRITICAL" if automated else "HIGH",
        "severity": "MAJOR",
        "status": "ACTIVE" if automated else "DRAFT",
        "testType": "UI" if ui else "FUNCTIONAL",
        "preconditions": preconditions,
        "expectedResult": expected,
        "tags": "inventory,stock-alert,zero-stock," + ("automated" if automated else "planned"),
        "apiAutomationIds": [test_id] if automated and not ui else [],
        "uiAutomationIds": [test_id] if automated and ui else [],
        "steps": [
            {"stepOrder": index, "actionText": action, "expectedText": result}
            for index, (action, result) in enumerate(steps, start=1)
        ],
    }


CASES = [
    case(
        "TC-ALERT-006",
        "API: нульовий ресурс зі сповіщенням при showZeroStock=false",
        "Автоматизований hierarchy API-контракт. Dev ERP: PASS 01.10.2026.",
        "Admin; ізольована локація; A має сповіщення і amount=0; B без сповіщення має збережений StorageItem.amount=0.",
        "За false повертається A, але не B; за true повертаються A і B.",
        [
            ("Перевірити single-storage GET: amount=0 для A і B, alertLimit є лише для A.", "Передумови підтверджені."),
            ("Викликати hierarchy GET для кожного ресурсу з showZeroStock=false.", "A присутній, B відсутній."),
            ("Повторити з showZeroStock=true.", "Обидва ресурси присутні."),
        ],
        automated=True,
        ui=False,
    ),
    case(
        "TC-UI-ALERT-005",
        "UI: нульовий ресурс зі сповіщенням при вимкненому тоглі",
        "Автоматизований сценарій сторінки «Залишки». Dev ERP: PASS 01.10.2026.",
        "Admin; ізольована локація; A має сповіщення і amount=0; B без сповіщення має amount=0; C має додатний залишок.",
        "За вимкненого тогла A видимий із 0 і «відсутній», B прихований, C видимий; за ввімкненого A і B видимі; після видалення сповіщення A прихований.",
        [
            ("Відкрити /inventory?storageId=...; вимкнути тогл і знайти ресурси.", "A видимий з 0 та «відсутній», B прихований, C видимий."),
            ("Увімкнути тогл.", "A і B видимі з 0."),
            ("Вимкнути тогл, видалити сповіщення A й оновити таблицю.", "A і B приховані, C лишився."),
        ],
        automated=True,
        ui=True,
    ),
    case(
        "TC-UI-ALERT-006",
        "UI: сповіщення налаштоване тільки на іншій локації",
        "Запланована перевірка ізоляції сповіщення за локацією.",
        "Ресурс A має amount=0 у двох локаціях; сповіщення задане лише на першій.",
        "За вимкненого тогла A видимий у першій локації та прихований у другій.",
        [("Відкрити обидві локації по черзі з вимкненим тоглом.", "A є лише в локації зі сповіщенням.")],
        automated=False,
        ui=True,
    ),
    case(
        "TC-UI-ALERT-007",
        "UI: пошук, пагінація і зміна нульового залишку",
        "Запланована регресія одного рядка та оновлення кількості/бейджа.",
        "Ресурс A має сповіщення; даних досить для кількох сторінок.",
        "A з'являється лише один раз у пошуку/пагінації; при >0 → 0 → >0 лишається видимим з актуальною кількістю і статусом.",
        [
            ("З вимкненим тоглом виконати пошук і перейти між сторінками.", "A присутній один раз."),
            ("Змінити залишок A: >0 → 0 → >0, оновлюючи таблицю.", "Кількість і бейдж відповідають поточному залишку."),
        ],
        automated=False,
        ui=True,
    ),
]


def merge_documentation(existing: str) -> str:
    if START in existing:
        if END not in existing:
            raise ValueError("TCM documentation has an unterminated zero-stock section")
        before = existing[: existing.index(START)].rstrip()
        after = existing[existing.index(END) + len(END) :].strip()
        return before + "\n\n" + APPENDIX + ("\n\n" + after if after else "\n")
    return existing.rstrip() + "\n\n" + APPENDIX + "\n"


def sync(client: TcmClient) -> dict[str, Any]:
    path = f"/api/ai/projects/1/features/{FEATURE_ID}"
    feature = client.request("GET", path)
    old_doc = feature.get("documentation") or ""
    new_doc = merge_documentation(old_doc)
    if old_doc != new_doc:
        client.request("PUT", path, {
            key: feature[key]
            for key in ("featureId", "parentFeatureId", "title", "description", "module", "priority", "status")
            if feature.get(key) is not None
        } | {"documentation": new_doc})

    feature = client.request("GET", path)
    existing_ac = next((item for item in feature.get("acceptanceCriteria", []) if item.get("acId") == AC_KEY), None)
    if existing_ac is None:
        ac = client.request("POST", "/api/ai/projects/1/acceptance-criteria", {
            "featureId": FEATURE_ID, "acKey": AC_KEY, "text": AC_TEXT,
        })
        ac_action = "created"
    else:
        ac = client.request("PUT", f"/api/ai/projects/1/acceptance-criteria/{existing_ac['id']}", {
            "featureId": FEATURE_ID, "text": AC_TEXT,
        })
        ac_action = "updated"

    case_actions = {}
    for item in CASES:
        test_id = item["testId"]
        case_path = f"/api/ai/projects/1/test-cases/{test_id}"
        existing = client.get_optional(case_path)
        payload = dict(item)
        payload["acceptanceCriterionId"] = ac["id"]
        if existing is None:
            client.request("POST", "/api/ai/projects/1/test-cases", payload)
            case_actions[test_id] = "created"
        else:
            client.request("PUT", case_path, payload)
            case_actions[test_id] = "updated"

    final = client.request("GET", path)
    final_doc = final.get("documentation") or ""
    checks = {}
    for item in CASES:
        current = client.request("GET", f"/api/ai/projects/1/test-cases/{item['testId']}")
        checks[item["testId"]] = {
            "featureId": current.get("featureId"),
            "acId": current.get("acId"),
            "status": current.get("status"),
            "apiAutomationIds": current.get("apiAutomationIds") or [],
            "uiAutomationIds": current.get("uiAutomationIds") or [],
        }
    ok = final_doc == new_doc and any(x.get("acId") == AC_KEY for x in final.get("acceptanceCriteria", []))
    ok = ok and all(x["featureId"] == FEATURE_ID and x["acId"] == AC_KEY for x in checks.values())
    if not ok:
        raise RuntimeError("TCM read-after-write verification failed")
    return {
        "baseUrl": client.base_url,
        "featureId": FEATURE_ID,
        "documentationChanged": old_doc != new_doc,
        "documentationLength": len(final_doc),
        "documentationSha256": hashlib.sha256(final_doc.encode("utf-8")).hexdigest(),
        "acceptanceCriterion": {"acKey": AC_KEY, "action": ac_action, "id": ac["id"]},
        "testCases": case_actions,
        "verification": {"documentationMatchesSource": final_doc == new_doc, "cases": checks},
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "result": "SUCCESS",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url")
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--insecure", action="store_true")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--payload-only", action="store_true")
    args = parser.parse_args()
    if args.payload_only:
        print(json.dumps({"appendix": APPENDIX, "acKey": AC_KEY, "acText": AC_TEXT, "cases": CASES}, ensure_ascii=False))
        return 0
    if not args.base_url or not args.report:
        parser.error("--base-url and --report are required for a sync")
    token = os.getenv(args.token_env)
    if not token:
        parser.error(f"environment variable {args.token_env} is required")
    report = sync(TcmClient(args.base_url, token, args.insecure))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": report["result"], "baseUrl": report["baseUrl"], "documentationSha256": report["documentationSha256"], "testCases": report["testCases"], "report": str(args.report)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
