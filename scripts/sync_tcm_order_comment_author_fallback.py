#!/usr/bin/env python3
"""Synchronize REQ-ORD comment-author fallback documentation and cases with TCM.

The script is idempotent and intentionally updates only the REQ-ORD feature
documentation, AC-04 text, and the two fallback cases. It does not publish ERP
execution results.
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
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


PROJECT_ID = 1
FEATURE_ID = "REQ-ORD"
AC_KEY = "AC-04"
ROOT = Path(__file__).resolve().parent.parent
DOCUMENTATION_PATH = ROOT / "docs" / "REQ-ORD-orders.md"

AC_TEXT = (
    "Коментарі GET/POST /orders/{id}/comments без storageId: доступ read на замовника "
    "або збір; blank text → 400; автор береться з поточної сесії. Якщо firstName і "
    "lastName відсутні, authorName дорівнює username і UI не показує «Невідомо». "
    "Список повертається newest first."
)

CASES: list[dict[str, Any]] = [
    {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": "TC-ORD-045",
        "title": "Коментар автора без firstName/lastName використовує username",
        "description": (
            "API-перевірка fallback автора коментаря на username для окремого "
            "динамічного користувача без firstName і lastName."
        ),
        "priority": "CRITICAL",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "FUNCTIONAL",
        "preconditions": (
            "Є доступне замовлення та окремий активний користувач із правом читання "
            "локації замовника, заповненим username і без firstName/lastName."
        ),
        "expectedResult": (
            "POST і GET comments повертають authorName, який точно дорівнює username; "
            "authorName не є null, порожнім або «Невідомо»."
        ),
        "tags": "orders,comments,author,username,fallback,automated",
        "apiAutomationIds": ["TC-ORD-045"],
        "uiAutomationIds": [],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Створити окремого активного користувача з доступом до локації "
                    "замовника, заповненим username і без firstName/lastName."
                ),
                "expectedText": (
                    "Профіль користувача активний; firstName і lastName відсутні; "
                    "username доступний у сесії."
                ),
            },
            {
                "stepOrder": 2,
                "actionText": (
                    "Створити доступне користувачу замовлення та додати від його "
                    "імені коментар через POST /orders/{id}/comments."
                ),
                "expectedText": (
                    "Коментар створено; text і createdAt заповнені; authorName точно "
                    "дорівнює username, а не null чи «Невідомо»."
                ),
            },
            {
                "stepOrder": 3,
                "actionText": "Отримати список через GET /orders/{id}/comments.",
                "expectedText": (
                    "Створений коментар повертається з тим самим authorName=username."
                ),
            },
        ],
    },
    {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": "TC-ORD-UI-018",
        "title": (
            "UI показує username автора без firstName/lastName замість «Невідомо»"
        ),
        "description": (
            "Браузерна перевірка відображення автора коментаря, коли профіль містить "
            "username, але не містить firstName і lastName."
        ),
        "priority": "CRITICAL",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "UI",
        "preconditions": (
            "Є замовлення з коментарем окремого активного користувача, у якого "
            "заповнений username і відсутні firstName/lastName."
        ),
        "expectedResult": (
            "У картці замовлення біля коментаря показаний точний username автора; "
            "текст «Невідомо» відсутній."
        ),
        "tags": "orders,comments,author,username,fallback,ui,automated",
        "apiAutomationIds": [],
        "uiAutomationIds": ["TC-ORD-UI-018"],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Підготувати замовлення з коментарем користувача, у якого є "
                    "username, але відсутні firstName/lastName."
                ),
                "expectedText": "API коментаря повертає authorName=username.",
            },
            {
                "stepOrder": 2,
                "actionText": (
                    "Відкрити картку замовлення у браузері та знайти підготовлений коментар."
                ),
                "expectedText": (
                    "Біля коментаря показаний точний username автора; напис "
                    "«Невідомо» відсутній."
                ),
            },
            {
                "stepOrder": 3,
                "actionText": "Повторно відкрити картку замовлення.",
                "expectedText": (
                    "Після повторного GET коментарів username автора відображається без змін."
                ),
            },
        ],
    },
]


@dataclass
class TcmClient:
    base_url: str
    token: str
    insecure: bool = False

    def __post_init__(self) -> None:
        self.base_url = self.base_url.rstrip("/")
        self.context = ssl._create_unverified_context() if self.insecure else None

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
    feature_payload = {
        "featureId": FEATURE_ID,
        "parentFeatureId": feature.get("parentFeatureId"),
        "title": feature["title"],
        "description": feature.get("description") or "",
        "documentation": documentation,
        "module": feature["module"],
        "priority": feature["priority"],
        "status": feature["status"],
    }
    client.request("PUT", feature_path, feature_payload)

    refreshed_feature = client.request("GET", feature_path)
    criterion = next(
        (item for item in refreshed_feature.get("acceptanceCriteria", []) if item["acId"] == AC_KEY),
        None,
    )
    if criterion is None:
        criterion = client.request(
            "POST",
            f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria",
            {"featureId": FEATURE_ID, "acKey": AC_KEY, "text": AC_TEXT},
        )
        ac_action = "created"
    else:
        criterion = client.request(
            "PUT",
            f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria/{criterion['id']}",
            {"featureId": FEATURE_ID, "text": AC_TEXT},
        )
        ac_action = "updated"

    case_actions: dict[str, str] = {}
    for payload in CASES:
        test_id = payload["testId"]
        case_path = (
            f"/api/ai/projects/{PROJECT_ID}/test-cases/"
            f"{urllib.parse.quote(test_id, safe='')}"
        )
        existing = client.get_optional(case_path)
        write_payload = dict(payload)
        write_payload["acceptanceCriterionId"] = int(criterion["id"])
        if existing is None:
            client.request("POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", write_payload)
            case_actions[test_id] = "created"
        else:
            client.request("PUT", case_path, write_payload)
            case_actions[test_id] = "updated"

    feature_check = client.request("GET", feature_path)
    ac_check = next(
        item for item in feature_check.get("acceptanceCriteria", []) if item["acId"] == AC_KEY
    )
    case_checks: dict[str, Any] = {}
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
                actual.get(field) == expected.get(field)
                for field in (
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
            ),
        }

    documentation_check = feature_check.get("documentation") or ""
    verification = {
        "featureId": feature_check.get("featureId"),
        "documentationLength": len(documentation_check),
        "documentationSha256": hashlib.sha256(documentation_check.encode("utf-8")).hexdigest(),
        "sourceDocumentationSha256": hashlib.sha256(documentation.encode("utf-8")).hexdigest(),
        "documentationMatchesSource": documentation_check == documentation,
        "acceptanceCriterionMatches": ac_check.get("text") == AC_TEXT,
        "allCasesMatch": all(item["matchesExpected"] for item in case_checks.values()),
        "testCases": case_checks,
    }
    if not all(
        verification[key]
        for key in (
            "documentationMatchesSource",
            "acceptanceCriterionMatches",
            "allCasesMatch",
        )
    ):
        raise RuntimeError(f"TCM read-back verification failed: {verification}")

    return {
        "baseUrl": client.base_url,
        "feature": "updated",
        "acceptanceCriterion": {AC_KEY: ac_action},
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
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "result": report["result"],
                "baseUrl": report["baseUrl"],
                "feature": report["feature"],
                "acceptanceCriterion": report["acceptanceCriterion"],
                "testCases": report["testCases"],
                "verification": {
                    key: report["verification"][key]
                    for key in (
                        "documentationMatchesSource",
                        "acceptanceCriterionMatches",
                        "allCasesMatch",
                    )
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
