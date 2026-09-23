#!/usr/bin/env python3
"""Synchronize the production-order relocation guard with TCM.

The script is idempotent. It updates REQ-PO documentation, upserts AC-05 and
TC-PO-008, and verifies all written data by reading it back. It does not publish
test execution results.
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
FEATURE_ID = "REQ-PO"
AC_KEY = "AC-05"
TEST_ID = "TC-PO-008"
ROOT = Path(__file__).resolve().parent.parent
DOCUMENTATION_PATH = ROOT / "docs" / "REQ-PO-production-orders.md"

AC_TEXT = (
    "Поки згенероване виробниче замовлення перебуває у стані IN_PROGRESS і "
    "виробниче завдання ще не виконане, у картці ВЗ відсутня дія «Створити "
    "переміщення». Передчасно створити переміщення невиробленого ресурсу неможливо."
)

CASE: dict[str, Any] = {
    "featureId": FEATURE_ID,
    "acKey": AC_KEY,
    "testId": TEST_ID,
    "title": "До виконання виробництва створення переміщення недоступне",
    "description": (
        "UI-перевірка регресії: після генерації завдань, але до виконання "
        "виробництва, картка ВЗ не дозволяє створити переміщення ще не "
        "виробленого ресурсу."
    ),
    "priority": "CRITICAL",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "UI",
    "preconditions": (
        "Є активна виробнича локація з можливостями PRODUCE, TASKS і RELOCATIONS, "
        "активна технологічна карта вихідного ресурсу та достатній залишок "
        "компонента для генерації виробничих завдань."
    ),
    "expectedResult": (
        "ВЗ має стан IN_PROGRESS і completedTasks=0; у його картці кнопка "
        "«Створити переміщення» відсутня, переміщення не створюється і повідомлення "
        "про недостатню кількість ресурсу не виникає."
    ),
    "tags": "production-order,production,relocation,guard,ui,regression,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": [TEST_ID],
    "steps": [
        {
            "stepOrder": 1,
            "actionText": (
                "Підготувати виробничу локацію, вихідний ресурс, компонент, активну "
                "технологічну карту та достатній залишок компонента."
            ),
            "expectedText": (
                "Передумови дозволяють створити ВЗ і згенерувати виробничі завдання."
            ),
        },
        {
            "stepOrder": 2,
            "actionText": (
                "Створити ВЗ, сформувати декомпозицію та згенерувати завдання, не "
                "виконуючи виробниче завдання."
            ),
            "expectedText": "ВЗ має state=IN_PROGRESS і completedTasks=0.",
        },
        {
            "stepOrder": 3,
            "actionText": "Відкрити картку згенерованого виробничого замовлення.",
            "expectedText": "Кнопка «Створити переміщення» не відображається.",
        },
        {
            "stepOrder": 4,
            "actionText": "Перевірити, що переміщення результату не було розпочате.",
            "expectedText": (
                "Переміщення не створене; помилка про недостатню кількість ресурсу "
                "не показується."
            ),
        },
    ],
}


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
    criterion = next(
        (
            item
            for item in refreshed_feature.get("acceptanceCriteria", [])
            if item["acId"] == AC_KEY
        ),
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

    case_path = (
        f"/api/ai/projects/{PROJECT_ID}/test-cases/"
        f"{urllib.parse.quote(TEST_ID, safe='')}"
    )
    existing = client.get_optional(case_path)
    case_payload = dict(CASE)
    case_payload["acceptanceCriterionId"] = int(criterion["id"])
    if existing is None:
        client.request("POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", case_payload)
        case_action = "created"
    else:
        client.request("PUT", case_path, case_payload)
        case_action = "updated"

    feature_check = client.request("GET", feature_path)
    criterion_check = next(
        item
        for item in feature_check.get("acceptanceCriteria", [])
        if item["acId"] == AC_KEY
    )
    case_check = client.request("GET", case_path)
    fields = (
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
    stored_documentation = feature_check.get("documentation") or ""
    verification = {
        "featureId": feature_check.get("featureId"),
        "documentationLength": len(stored_documentation),
        "documentationSha256": hashlib.sha256(
            stored_documentation.encode("utf-8")
        ).hexdigest(),
        "sourceDocumentationSha256": hashlib.sha256(
            documentation.encode("utf-8")
        ).hexdigest(),
        "documentationMatchesSource": stored_documentation == documentation,
        "acceptanceCriterionMatches": criterion_check.get("text") == AC_TEXT,
        "testCaseMatches": (
            case_check.get("featureId") == FEATURE_ID
            and case_check.get("acId") == AC_KEY
            and all(case_check.get(field) == CASE.get(field) for field in fields)
        ),
        "testCase": {
            "testId": case_check.get("testId"),
            "featureId": case_check.get("featureId"),
            "acId": case_check.get("acId"),
            "status": case_check.get("status"),
            "uiAutomationIds": case_check.get("uiAutomationIds") or [],
            "stepCount": len(case_check.get("steps") or []),
        },
    }
    if not all(
        verification[key]
        for key in (
            "documentationMatchesSource",
            "acceptanceCriterionMatches",
            "testCaseMatches",
        )
    ):
        raise RuntimeError(f"TCM read-back verification failed: {verification}")

    return {
        "baseUrl": client.base_url,
        "feature": "updated",
        "acceptanceCriterion": {AC_KEY: ac_action},
        "testCases": {TEST_ID: case_action},
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
                        "testCaseMatches",
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
