#!/usr/bin/env python3
"""Synchronize REQ-ORD unread-comment documentation and cases with TCM.

The script is idempotent and updates only the REQ-ORD feature documentation,
AC-04 text, and the three unread-comment cases. It does not publish ERP test
execution results.
"""
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

from sync_tcm_order_comment_author_fallback import TcmClient


PROJECT_ID = 1
FEATURE_ID = "REQ-ORD"
AC_KEY = "AC-04"
ROOT = Path(__file__).resolve().parent.parent
DOCUMENTATION_PATH = ROOT / "docs" / "REQ-ORD-orders.md"

AC_TEXT = (
    "Коментарі GET/POST /orders/{id}/comments без storageId: доступ read на замовника "
    "або збір; blank text → 400; автор береться з поточної сесії, а за відсутності "
    "firstName/lastName authorName дорівнює username. Непрочитаність ведеться окремо "
    "для кожного username: власні коментарі не враховуються, GET /orders і "
    "GET /orders/{id} повертають unreadCommentsCount, comments повертають unread, "
    "unreadComments=true фільтрує список, POST /orders/{id}/comments/read позначає "
    "поточний зріз прочитаним лише для поточного користувача. Новіші коментарі зверху."
)

CASES: list[dict[str, Any]] = [
    {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": "TC-ORD-046",
        "title": (
            "Непрочитані коментарі per-user: count/flags/filter, власні не враховуються"
        ),
        "description": (
            "API-перевірка персональних unreadCommentsCount і comment.unread, "
            "виключення власних коментарів та фільтра unreadComments=true."
        ),
        "priority": "CRITICAL",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "testType": "FUNCTIONAL",
        "preconditions": (
            "Є замовлення та два активні користувачі з доступом до нього; кожен "
            "може читати й додавати коментарі."
        ),
        "expectedResult": (
            "Для кожного користувача власний коментар має unread=false, чужий — "
            "unread=true; unreadCommentsCount і unread-only список узгоджені."
        ),
        "tags": "orders,comments,unread,per-user,filter,api,automated",
        "apiAutomationIds": ["TC-ORD-046"],
        "uiAutomationIds": [],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Створити замовлення; від імені замовника додати власний "
                    "коментар, від іншого користувача з доступом — чужий."
                ),
                "expectedText": (
                    "Обидва коментарі збережені та доступні обом користувачам."
                ),
            },
            {
                "stepOrder": 2,
                "actionText": (
                    "Отримати список, картку й comments під замовником."
                ),
                "expectedText": (
                    "unreadCommentsCount=1; власний comment.unread=false, чужий "
                    "comment.unread=true."
                ),
            },
            {
                "stepOrder": 3,
                "actionText": (
                    "Запросити список з unreadComments=true і відкрити картку "
                    "під автором другого коментаря."
                ),
                "expectedText": (
                    "Замовлення є у filtered list замовника; для другого "
                    "користувача його власний коментар прочитаний, а коментар "
                    "замовника — непрочитаний."
                ),
            },
        ],
    },
    {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": "TC-ORD-047",
        "title": (
            "Mark read per-user; наступний чужий коментар знову непрочитаний"
        ),
        "description": (
            "API-перевірка персонального POST comments/read та повторної появи "
            "непрочитаного стану після нового чужого коментаря."
        ),
        "priority": "CRITICAL",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "testType": "FUNCTIONAL",
        "preconditions": (
            "Є замовлення та два активні користувачі з доступом до нього; у "
            "замовника є чужий непрочитаний коментар."
        ),
        "expectedResult": (
            "Mark-read очищає unread лише поточного користувача; чужий коментар, "
            "доданий пізніше, знову дає count=1 і повертає замовлення у filter."
        ),
        "tags": "orders,comments,unread,mark-read,per-user,api,automated",
        "apiAutomationIds": ["TC-ORD-047"],
        "uiAutomationIds": [],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Додати до замовлення чужий коментар і викликати POST "
                    "/orders/{id}/comments/read під замовником."
                ),
                "expectedText": (
                    "У картці замовника unreadCommentsCount=0, коментар "
                    "unread=false; unread-only list не містить замовлення."
                ),
            },
            {
                "stepOrder": 2,
                "actionText": (
                    "Додати відповідь замовника й перевірити картку другого "
                    "користувача."
                ),
                "expectedText": (
                    "Read-state замовника не поширився на іншого користувача; "
                    "його власний коментар не unread, відповідь замовника "
                    "unread=true."
                ),
            },
            {
                "stepOrder": 3,
                "actionText": (
                    "Після mark-read додати ще один чужий коментар."
                ),
                "expectedText": (
                    "Для замовника тільки новий коментар unread=true, count=1, "
                    "замовлення знову є в unread-only list."
                ),
            },
        ],
    },
    {
        "featureId": FEATURE_ID,
        "acKey": AC_KEY,
        "testId": "TC-ORD-UI-033",
        "title": (
            "UI hint непрочитаних коментарів: sidebar/tab/row/filter/detail"
        ),
        "description": (
            "Браузерна перевірка unread hints, лічильника, фільтра, chip/бейджа "
            "у картці та очищення стану після відкриття замовлення."
        ),
        "priority": "CRITICAL",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "testType": "UI",
        "preconditions": (
            "Є незавершене замовлення з чужим непрочитаним коментарем і контрольне "
            "замовлення без непрочитаних коментарів."
        ),
        "expectedResult": (
            "Сині hints є у sidebar, вкладці та рядку; unread filter працює; у "
            "картці видно новий коментар, після відкриття hint очищається."
        ),
        "tags": "orders,comments,unread,hint,sidebar,filter,ui,automated",
        "apiAutomationIds": [],
        "uiAutomationIds": ["TC-ORD-UI-033"],
        "steps": [
            {
                "stepOrder": 1,
                "actionText": (
                    "Підготувати незавершене замовлення з чужим непрочитаним "
                    "коментарем і відкрити список під замовником."
                ),
                "expectedText": (
                    "Синя крапка є в sidebar, на вкладці та в рядку; рядок "
                    "акцентований, лічильник дорівнює 1."
                ),
            },
            {
                "stepOrder": 2,
                "actionText": "Увімкнути фільтр «Непрочитані коментарі».",
                "expectedText": (
                    "Замовлення з новим коментарем лишається, замовлення без "
                    "непрочитаних приховане."
                ),
            },
            {
                "stepOrder": 3,
                "actionText": "Відкрити картку замовлення.",
                "expectedText": (
                    "Видно chip «1 новий», коментар має синій акцент і бейдж "
                    "«Новий»; UI успішно викликає comments/read."
                ),
            },
            {
                "stepOrder": 4,
                "actionText": (
                    "Закрити картку, вимкнути unread-фільтр і перевірити рядок."
                ),
                "expectedText": (
                    "Замовлення знову видно без синьої крапки; "
                    "unreadCommentsCount=0."
                ),
            },
        ],
    },
]


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
            client.request(
                "POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", write_payload
            )
            case_actions[test_id] = "created"
        else:
            client.request("PUT", case_path, write_payload)
            case_actions[test_id] = "updated"

    feature_check = client.request("GET", feature_path)
    ac_check = next(
        item
        for item in feature_check.get("acceptanceCriteria", [])
        if item["acId"] == AC_KEY
    )
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

    documentation_check = feature_check.get("documentation") or ""
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
