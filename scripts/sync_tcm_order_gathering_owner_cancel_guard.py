#!/usr/bin/env python3
"""Synchronize the REQ-ORD gathering-owner cancellation guard with TCM.

The script is idempotent. It publishes the local REQ-ORD documentation and
upserts the API/UI regression cases without publishing execution results.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


PROJECT_ID = 1
FEATURE_ID = "REQ-ORD"
ROOT = Path(__file__).resolve().parent.parent
DOCUMENTATION_PATH = ROOT / "docs" / "REQ-ORD-orders.md"

CASES: list[dict[str, Any]] = [
    {
        "featureId": FEATURE_ID,
        "acKey": "AC-11",
        "testId": "TC-ORD-RBAC-006",
        "title": "Овнер локації збору не може скасувати замовлення",
        "description": (
            "API-регресія перевіряє межу прав між видимістю замовлення на "
            "призначеній локації збору та керуванням його життєвим циклом."
        ),
        "priority": "CRITICAL",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "testType": "SECURITY",
        "preconditions": (
            "Замовлення створене іншим користувачем, переведене в IN_PROGRESS, "
            "йому призначена окрема локація збору; тестовий користувач має роль "
            "Business_Unit_Owner-ROLE і grant «Керівник локації» лише на цю локацію збору."
        ),
        "expectedResult": (
            "Овнер збору читає замовлення, але PUT cancel повертає 403; замовлення "
            "залишається IN_PROGRESS і заборонена спроба не створює побічних змін."
        ),
        "tags": "orders,rbac,gathering-owner,cancel,security,regression,automated",
        "apiAutomationIds": ["TC-ORD-RBAC-006"],
        "uiAutomationIds": [],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Під автором створити замовлення, під Admin взяти його в роботу "
                    "та призначити окрему локацію збору."
                ),
                "expectedText": (
                    "Замовлення має стан IN_PROGRESS; локація збору призначена."
                ),
            },
            {
                "stepOrder": 2,
                "actionText": (
                    "Під овнером призначеної локації збору отримати замовлення за id."
                ),
                "expectedText": "GET успішний: замовлення доступне для комплектування.",
            },
            {
                "stepOrder": 3,
                "actionText": (
                    "Під тим самим овнером викликати PUT /orders/{id}/cancel."
                ),
                "expectedText": "API повертає 403 Access Denied.",
            },
            {
                "stepOrder": 4,
                "actionText": "Повторно отримати замовлення під автором.",
                "expectedText": "Стан замовлення лишається IN_PROGRESS.",
            },
        ],
    },
    {
        "featureId": FEATURE_ID,
        "acKey": "AC-12",
        "testId": "TC-ORD-UI-019",
        "title": "Овнер локації збору не бачить дії «Скасувати»",
        "description": (
            "UI-регресія перевіряє, що доступ до картки замовлення як овнера "
            "призначеної локації збору не показує керуючу дію скасування."
        ),
        "priority": "CRITICAL",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "testType": "UI",
        "preconditions": (
            "Замовлення іншого автора перебуває в IN_PROGRESS і має призначену "
            "локацію збору; поточний користувач є її овнером без order::manage."
        ),
        "expectedResult": (
            "Картка замовлення відкривається, але кнопка «Скасувати» відсутня."
        ),
        "tags": "orders,ui,rbac,gathering-owner,cancel,regression,automated",
        "apiAutomationIds": [],
        "uiAutomationIds": ["TC-ORD-UI-019"],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Створити замовлення іншим користувачем, перевести його в IN_PROGRESS "
                    "та призначити локацію збору."
                ),
                "expectedText": (
                    "Замовлення доступне овнеру призначеної локації збору."
                ),
            },
            {
                "stepOrder": 2,
                "actionText": (
                    "Увійти як овнер локації збору та відкрити deep-link картки замовлення."
                ),
                "expectedText": "Картка замовлення успішно відкрита.",
            },
            {
                "stepOrder": 3,
                "actionText": "Перевірити доступні дії в картці.",
                "expectedText": "Дія «Скасувати» не відображається.",
            },
        ],
    },
]


class TcmClient:
    def __init__(self, base_url: str, token: str, insecure: bool = False) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.context = ssl._create_unverified_context() if insecure else None

    def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            method=method,
            headers={
                "X-TCM-Ai-Token": self.token,
                "Accept": "application/json",
                "Content-Type": "application/json; charset=utf-8",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=90, context=self.context) as response:
                raw = response.read().decode("utf-8")
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} -> HTTP {exc.code}: {detail}") from exc

    def get_optional(self, path: str) -> Any | None:
        try:
            return self.request("GET", path)
        except RuntimeError as exc:
            if "HTTP 400" in str(exc) or "HTTP 404" in str(exc):
                return None
            raise


def sync(client: TcmClient) -> dict[str, Any]:
    documentation = DOCUMENTATION_PATH.read_text(encoding="utf-8")
    feature_path = f"/api/ai/projects/{PROJECT_ID}/features/{FEATURE_ID}"
    feature = client.request("GET", feature_path)
    client.request(
        "PUT",
        feature_path,
        {
            "featureId": FEATURE_ID,
            "parentFeatureId": feature.get("parentFeatureId"),
            "title": feature["title"],
            "description": feature.get("description") or "",
            "documentation": documentation,
            "module": feature["module"],
            "priority": feature["priority"],
            "status": feature["status"],
        },
    )

    refreshed_feature = client.request("GET", feature_path)
    criteria = {
        item["acId"]: item
        for item in refreshed_feature.get("acceptanceCriteria", [])
    }
    missing = sorted({case["acKey"] for case in CASES} - criteria.keys())
    if missing:
        raise RuntimeError(f"Missing REQ-ORD acceptance criteria: {missing}")

    case_actions: dict[str, str] = {}
    for payload in CASES:
        test_id = payload["testId"]
        case_path = (
            f"/api/ai/projects/{PROJECT_ID}/test-cases/"
            f"{urllib.parse.quote(test_id, safe='')}"
        )
        existing = client.get_optional(case_path)
        write_payload = dict(payload)
        write_payload["acceptanceCriterionId"] = int(criteria[payload["acKey"]]["id"])
        if existing is None:
            client.request("POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", write_payload)
            case_actions[test_id] = "created"
        else:
            client.request("PUT", case_path, write_payload)
            case_actions[test_id] = "updated"

    feature_check = client.request("GET", feature_path)
    documentation_check = feature_check.get("documentation") or ""
    case_checks: dict[str, Any] = {}
    compared_fields = (
        "title",
        "description",
        "priority",
        "severity",
        "status",
        "testType",
        "preconditions",
        "expectedResult",
        "tags",
        "apiAutomationIds",
        "uiAutomationIds",
        "steps",
    )
    for expected in CASES:
        test_id = expected["testId"]
        actual = client.request(
            "GET",
            f"/api/ai/projects/{PROJECT_ID}/test-cases/"
            f"{urllib.parse.quote(test_id, safe='')}",
        )
        case_checks[test_id] = {
            "featureId": actual.get("featureId"),
            "acId": actual.get("acId"),
            "status": actual.get("status"),
            "apiAutomationIds": actual.get("apiAutomationIds") or [],
            "uiAutomationIds": actual.get("uiAutomationIds") or [],
            "stepCount": len(actual.get("steps") or []),
            "matchesExpected": all(
                actual.get(field) == expected.get(field) for field in compared_fields
            ),
        }

    verification = {
        "featureId": feature_check.get("featureId"),
        "documentationLength": len(documentation_check),
        "documentationSha256": hashlib.sha256(
            documentation_check.encode("utf-8")
        ).hexdigest(),
        "sourceDocumentationSha256": hashlib.sha256(
            documentation.encode("utf-8")
        ).hexdigest(),
        "documentationMatchesSource": documentation_check == documentation,
        "allCasesMatch": all(item["matchesExpected"] for item in case_checks.values()),
        "testCases": case_checks,
    }
    if not verification["documentationMatchesSource"] or not verification["allCasesMatch"]:
        raise RuntimeError(f"TCM read-back verification failed: {verification}")

    return {
        "baseUrl": client.base_url,
        "feature": "updated",
        "testCases": case_actions,
        "verification": verification,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--token", help=argparse.SUPPRESS)
    parser.add_argument("--insecure", action="store_true")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    token = args.token or os.getenv(args.token_env)
    if not token:
        print(f"Missing token: set {args.token_env}", file=sys.stderr)
        return 2

    report = sync(TcmClient(args.base_url, token, args.insecure))
    report["completedAt"] = datetime.now(timezone.utc).isoformat()
    report["result"] = "SUCCESS"
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "result": report["result"],
                "baseUrl": report["baseUrl"],
                "feature": report["feature"],
                "testCases": report["testCases"],
                "verification": {
                    "documentationMatchesSource": report["verification"][
                        "documentationMatchesSource"
                    ],
                    "allCasesMatch": report["verification"]["allCasesMatch"],
                },
                "report": str(args.report),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
