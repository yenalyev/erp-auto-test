#!/usr/bin/env python3
"""Synchronize the order relocation-request amount limit with TCM.

The script is idempotent. It updates REQ-ORD documentation, upserts AC-14 and
the API/UI regression cases, and verifies every write by reading it back. It
does not publish test execution results.
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
AC_KEY = "AC-14"
ROOT = Path(__file__).resolve().parent.parent
DOCUMENTATION_PATH = ROOT / "docs" / "REQ-ORD-orders.md"

AC_TEXT = (
    "Для замовлення у стані IN_PROGRESS з призначеною локацією збору "
    "Order_Admin-ROLE може створити запит на переміщення з іншої локації. "
    "Максимум окремого запиту обмежений кількістю позиції замовлення та вільним "
    "залишком джерела; залишок і броні на локації збору цю межу не зменшують. "
    "Запит понад межу повертає 400, не створює завдання і не резервує джерело. "
    "Успішний NEW-запит резервує джерело, а скасування повністю звільняє резерв."
)

CASES: list[dict[str, Any]] = [
    {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": "TC-ORD-ADMIN-005",
        "title": "Ліміт запиту на переміщення дорівнює кількості замовлення",
        "description": (
            "API-регресія ліміту relocation task: локальний залишок на зборі не "
            "віднімається від максимальної кількості запиту."
        ),
        "priority": "CRITICAL",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "FUNCTIONAL",
        "preconditions": (
            "Замовлення на 5 одиниць взято в роботу; на призначеній локації збору "
            "є 2 одиниці; на іншій локації-джерелі доступно щонайменше 5; "
            "Order_Admin-ROLE має право керувати замовленням."
        ),
        "expectedResult": (
            "Запит на 6 повертає 400 без створення задачі та резерву. Запит на 5 "
            "успішний і резервує 5 на джерелі; скасування NEW-запиту звільняє резерв."
        ),
        "tags": "orders,relocation-task,amount-limit,reservation,api,regression,automated",
        "apiAutomationIds": ["TC-ORD-ADMIN-005"],
        "uiAutomationIds": [],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Створити замовлення на 5 одиниць, взяти його в роботу та "
                    "призначити збір із залишком 2."
                ),
                "expectedText": "Замовлення IN_PROGRESS; на зборі on-hand=2.",
            },
            {
                "stepOrder": 2,
                "actionText": "Створити запит на переміщення 6 одиниць із джерела.",
                "expectedText": (
                    "HTTP 400; relocation task не створений; bookedAmount джерела = 0."
                ),
            },
            {
                "stepOrder": 3,
                "actionText": "Створити запит на переміщення 5 одиниць із джерела.",
                "expectedText": (
                    "Запит створений у стані NEW; bookedAmount джерела = 5 незалежно "
                    "від залишку 2 на зборі."
                ),
            },
            {
                "stepOrder": 4,
                "actionText": "Скасувати створений NEW-запит.",
                "expectedText": (
                    "Запит має стан CANCELLED; bookedAmount джерела повертається до 0."
                ),
            },
        ],
    },
    {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": "TC-ORD-E2E-012",
        "title": "UI не зменшує максимум запиту на залишок локації збору",
        "description": (
            "UI-регресія: для замовлення на 5 із залишком 2 на зборі форма запиту "
            "дозволяє замовити всі 5 одиниць."
        ),
        "priority": "CRITICAL",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "UI",
        "preconditions": (
            "Замовлення на 5 одиниць перебуває в роботі; на призначеній локації "
            "збору є 2 одиниці; на доступній локації-джерелі є 5 одиниць."
        ),
        "expectedResult": (
            "У полі кількості запиту HTML max дорівнює 5, запит на 5 створюється "
            "та резервує джерело; після скасування резерв дорівнює 0."
        ),
        "tags": "orders,relocation-task,amount-limit,ui,regression,automated",
        "apiAutomationIds": [],
        "uiAutomationIds": ["TC-ORD-E2E-012"],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Через UI створити замовлення на 5, взяти в роботу та обрати "
                    "локацію збору із залишком 2."
                ),
                "expectedText": "Замовлення має стан «В роботі» і призначений збір.",
            },
            {
                "stepOrder": 2,
                "actionText": (
                    "Відкрити «Замовити переміщення», обрати джерело й позицію замовлення."
                ),
                "expectedText": "Поле кількості має max=5, а не 3.",
            },
            {
                "stepOrder": 3,
                "actionText": "Ввести 5 і підтвердити створення запиту.",
                "expectedText": "Запит створений; на джерелі зарезервовано 5.",
            },
            {
                "stepOrder": 4,
                "actionText": "Скасувати запит у картці замовлення.",
                "expectedText": "Запит CANCELLED; резерв джерела повністю звільнений.",
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

    case_actions: dict[str, str] = {}
    for case in CASES:
        test_id = case["testId"]
        case_path = (
            f"/api/ai/projects/{PROJECT_ID}/test-cases/"
            f"{urllib.parse.quote(test_id, safe='')}"
        )
        existing = client.get_optional(case_path)
        payload = dict(case)
        payload["acceptanceCriterionId"] = int(criterion["id"])
        if existing is None:
            client.request("POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", payload)
            case_actions[test_id] = "created"
        else:
            client.request("PUT", case_path, payload)
            case_actions[test_id] = "updated"

    feature_check = client.request("GET", feature_path)
    criterion_check = next(
        item
        for item in feature_check.get("acceptanceCriteria", [])
        if item["acId"] == AC_KEY
    )
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
    case_checks: dict[str, Any] = {}
    for expected in CASES:
        test_id = expected["testId"]
        actual = client.request(
            "GET",
            f"/api/ai/projects/{PROJECT_ID}/test-cases/"
            f"{urllib.parse.quote(test_id, safe='')}",
        )
        matches = (
            actual.get("featureId") == FEATURE_ID
            and actual.get("acId") == AC_KEY
            and all(actual.get(field) == expected.get(field) for field in fields)
        )
        case_checks[test_id] = {
            "featureId": actual.get("featureId"),
            "acId": actual.get("acId"),
            "status": actual.get("status"),
            "apiAutomationIds": actual.get("apiAutomationIds") or [],
            "uiAutomationIds": actual.get("uiAutomationIds") or [],
            "stepCount": len(actual.get("steps") or []),
            "matchesExpected": matches,
        }

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
