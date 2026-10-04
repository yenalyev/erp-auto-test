#!/usr/bin/env python3
"""Upsert REQ-ALERT inventory documentation, AC-07, and three API cases."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parent.parent
APPENDIX = (ROOT / "docs" / "REQ-ALERT-INVENTORY-TCM.md").read_text(encoding="utf-8").strip()
START = "<!-- BEGIN ALERT_INVENTORY_2026_10_03 -->"
END = "<!-- END ALERT_INVENTORY_2026_10_03 -->"
FEATURE_ID = "REQ-ALERT"
AC_KEY = "AC-07"
AC_TEXT = (
    "Проведення інвентаризації не видаляє і не змінює налаштоване для ресурсу й локації "
    "сповіщення по залишках. Вилучення ресурсу з повного snapshot обнуляє залишок "
    "(weight=100) і залишає нульовий рядок видимим при showZeroStock=false; зміна "
    "кількості та додавання раніше відсутнього ресурсу перераховують weight відносно "
    "порогу, зберігаючи alertLimit та запис у GET alerts."
)


def case(test_id: str, title: str, description: str, preconditions: str,
         expected: str, steps: list[tuple[str, str]]) -> dict[str, Any]:
    return {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": test_id,
        "title": title,
        "description": description,
        "priority": "CRITICAL",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "FUNCTIONAL",
        "preconditions": preconditions,
        "expectedResult": expected,
        "tags": "inventory,stock-alert,automated",
        "apiAutomationIds": [test_id],
        "uiAutomationIds": [],
        "steps": [
            {"stepOrder": index, "actionText": action, "expectedText": result}
            for index, (action, result) in enumerate(steps, 1)
        ],
    }


CASES = [
    case(
        "TC-ALERT-007", "API: вилучення ресурсу зі сповіщенням інвентаризацією",
        "AlertStockHighlightApiTest.inventoryRemovalKeepsAlertOnZeroStock; Dev ERP: PASS 03.10.2026.",
        "Admin; ізольована локація; ресурс має залишок 4 і поріг 10; сесію інвентаризації відкрито.",
        "Залишок 0, weight=100, alertLimit=10; сповіщення з порогом 10 збережене; "
        "hierarchy API з showZeroStock=false повертає ресурс.",
        [
            ("Перевірити залишок 4, weight=60 і поріг 10 у GET inventory та GET alerts.",
             "Передумови підтверджені."),
            ("Провести інвентаризацію з повним snapshot без цього ресурсу й закрити сесію.",
             "PUT inventory успішний."),
            ("Прочитати залишки, сповіщення й hierarchy з showZeroStock=false.",
             "Залишок 0, weight=100, alertLimit=10; сповіщення збережене; ресурс видимий."),
        ],
    ),
    case(
        "TC-ALERT-008", "API: зміна кількості ресурсу зі сповіщенням інвентаризацією",
        "AlertStockHighlightApiTest.inventoryAmountChangeRecalculatesAlertWeight; Dev ERP: PASS 03.10.2026.",
        "Admin; ізольована локація; ресурс має залишок 4 і поріг 10; сесію інвентаризації відкрито.",
        "Після зміни кількості на 25: weight=40, alertLimit=10 і поріг у GET alerts дорівнює 10.",
        [
            ("Перевірити залишок 4, weight=60 і поріг 10.", "Передумови підтверджені."),
            ("Провести інвентаризацію з кількістю ресурсу 25 й закрити сесію.",
             "PUT inventory успішний."),
            ("Прочитати залишки та сповіщення.",
             "Кількість 25, weight=40; alertLimit і поріг у GET alerts лишилися 10."),
        ],
    ),
    case(
        "TC-ALERT-009", "API: додавання ресурсу зі сповіщенням інвентаризацією",
        "AlertStockHighlightApiTest.inventoryAdditionPreservesExistingAlert; Dev ERP: PASS 03.10.2026.",
        "Admin; ізольована локація; на ресурс із нульовим залишком задано поріг 10; "
        "сесію інвентаризації відкрито.",
        "Після додавання 4 одиниць: weight=60, alertLimit=10 і поріг у GET alerts дорівнює 10.",
        [
            ("Перевірити нульовий залишок, weight=100 і поріг 10.", "Передумови підтверджені."),
            ("Додати ресурс із кількістю 4 у повний snapshot інвентаризації й закрити сесію.",
             "PUT inventory успішний."),
            ("Прочитати залишки та сповіщення.",
             "Кількість 4, weight=60; alertLimit і поріг у GET alerts лишилися 10."),
        ],
    ),
]


def merge_documentation(existing: str) -> str:
    if START in existing:
        if END not in existing:
            raise ValueError("TCM documentation has an unterminated alert inventory section")
        before = existing[:existing.index(START)].rstrip()
        after = existing[existing.index(END) + len(END):].strip()
        return before + "\n\n" + APPENDIX + ("\n\n" + after if after else "\n")
    return existing.rstrip() + "\n\n" + APPENDIX + "\n"


def sync(client: TcmClient) -> dict[str, Any]:
    feature_path = f"/api/ai/projects/1/features/{FEATURE_ID}"
    feature = client.request("GET", feature_path)
    old_doc = feature.get("documentation") or ""
    new_doc = merge_documentation(old_doc)
    if old_doc != new_doc:
        client.request("PUT", feature_path, {
            key: feature[key]
            for key in ("featureId", "parentFeatureId", "title", "description", "module", "priority", "status")
            if feature.get(key) is not None
        } | {"documentation": new_doc})

    feature = client.request("GET", feature_path)
    existing_ac = next((item for item in feature.get("acceptanceCriteria", [])
                        if item.get("acId") == AC_KEY), None)
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

    actions = {}
    for item in CASES:
        test_id = item["testId"]
        case_path = f"/api/ai/projects/1/test-cases/{test_id}"
        payload = dict(item) | {"acceptanceCriterionId": ac["id"]}
        if client.get_optional(case_path) is None:
            client.request("POST", "/api/ai/projects/1/test-cases", payload)
            actions[test_id] = "created"
        else:
            client.request("PUT", case_path, payload)
            actions[test_id] = "updated"

    final = client.request("GET", feature_path)
    checks = {}
    for item in CASES:
        current = client.request("GET", f"/api/ai/projects/1/test-cases/{item['testId']}")
        checks[item["testId"]] = {
            "featureId": current.get("featureId"),
            "acId": current.get("acId"),
            "status": current.get("status"),
            "apiAutomationIds": current.get("apiAutomationIds") or [],
            "steps": len(current.get("steps") or []),
        }
    final_doc = final.get("documentation") or ""
    ok = final_doc == new_doc and any(x.get("acId") == AC_KEY for x in final.get("acceptanceCriteria", []))
    ok = ok and all(x["featureId"] == FEATURE_ID and x["acId"] == AC_KEY
                    and x["apiAutomationIds"] == [test_id] and x["steps"] == 3
                    for test_id, x in checks.items())
    if not ok:
        raise RuntimeError("TCM read-after-write verification failed")
    return {
        "baseUrl": client.base_url,
        "featureId": FEATURE_ID,
        "documentationChanged": old_doc != new_doc,
        "documentationLength": len(final_doc),
        "documentationSha256": hashlib.sha256(final_doc.encode("utf-8")).hexdigest(),
        "acceptanceCriterion": {"acKey": AC_KEY, "action": ac_action, "id": ac["id"]},
        "testCases": actions,
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
        print(json.dumps({"appendix": APPENDIX, "acKey": AC_KEY, "acText": AC_TEXT,
                          "cases": CASES}, ensure_ascii=False))
        return 0
    if not args.base_url or not args.report:
        parser.error("--base-url and --report are required for a sync")
    token = os.getenv(args.token_env)
    if not token:
        parser.error(f"environment variable {args.token_env} is required")
    report = sync(TcmClient(args.base_url, token, args.insecure))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": report["result"], "testCases": report["testCases"],
                      "report": str(args.report)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
