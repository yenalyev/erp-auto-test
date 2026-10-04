#!/usr/bin/env python3
"""Synchronize the production analytics Excel contract and automated cases with TCM."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-ANL-PRODUCTION-EXCEL"
core.FEATURE_PARENT_ID = "REQ-ANL-PRODUCTION"
core.FEATURE_TITLE = "Аналітика виробництва: експорт статистики в Excel"
core.FEATURE_DESCRIPTION = (
    "Один XLSX із п'ятьма аркушами. Результати фільтруються за виробами, "
    "витрати — за окремим вибором матеріалів; перевірено на DEV 2026-09-28."
)
core.FEATURE_MODULE = "ANL"
core.FEATURE_PRIORITY = "CRITICAL"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-ANL-PRODUCTION-EXCEL-EXPORT.md"

DOCUMENTATION = core.DOCUMENTATION_PATH.read_text(encoding="utf-8")


def section_body(start: str, end: str) -> str:
    match = re.search(rf"(?ms)^{start}\n(.*?)(?=^{end}|\Z)", DOCUMENTATION)
    if match is None:
        raise ValueError(f"Missing documentation section: {start}")
    return match.group(1).strip()


core.ACCEPTANCE_CRITERIA = []
for number in range(1, 9):
    source_key = f"AC-EXCEL-{number:02d}"
    body = section_body(
        rf"### {source_key} — [^\n]+",
        r"### AC-EXCEL-|## 4\.",
    )
    core.ACCEPTANCE_CRITERIA.append((f"AC-{number:02d}", f"[{source_key}] {body}"))

CASE_AC = {
    13: "AC-01", 14: "AC-03", 15: "AC-03", 16: "AC-05",
    17: "AC-05", 18: "AC-05", 19: "AC-05", 20: "AC-05",
    21: "AC-04", 22: "AC-04", 23: "AC-04", 24: "AC-06",
    25: "AC-07", 26: "AC-05", 27: "AC-07", 28: "AC-08",
}

core.CASES = []
for number, ac_key in CASE_AC.items():
    test_id = f"TC-ANL-UI-{number:03d}"
    match = re.search(
        rf"(?ms)^### {test_id} — ([^\n]+)\n(.*?)(?=^### TC-ANL-UI-|^## 6\.|\Z)",
        DOCUMENTATION,
    )
    if match is None:
        raise ValueError(f"Missing test case section: {test_id}")
    title, body = match.group(1).strip(), match.group(2).strip()
    priority_match = re.search(r"Пріоритет: \*\*(\w+)\*\*", body)
    expected_match = re.search(r"(?ms)^Очікувано:\s*(.*)$", body)
    if priority_match is None or expected_match is None:
        raise ValueError(f"Missing priority or result: {test_id}")
    expected = expected_match.group(1).strip()
    actions = [
        " ".join(action.split())
        for action in re.findall(
            r"(?ms)^\d+\.\s+(.*?)(?=^\d+\.\s+|^Очікувано:|\Z)",
            body,
        )
    ]
    if not actions:
        raise ValueError(f"Missing steps: {test_id}")
    priority = {"blocker": "CRITICAL", "critical": "CRITICAL", "high": "HIGH", "normal": "MEDIUM"}[
        priority_match.group(1).lower()
    ]
    core.CASES.append(
        {
            "featureId": core.FEATURE_ID,
            "acKey": ac_key,
            "testId": test_id,
            "title": title,
            "description": f"Автоматизований сценарій Excel-експорту: {title}.",
            "priority": priority,
            "severity": "CRITICAL" if priority == "CRITICAL" else "MAJOR",
            "status": "ACTIVE",
            "testType": "UI",
            "preconditions": (
                "Доступний /analytics/production; ізольовані локації L1/L2, користувач, "
                "ресурси й операції створюються автотестом та очищаються після класу."
            ),
            "expectedResult": expected,
            "tags": "analytics,production,excel,automated,ui",
            "roleName": "Керівник локації",
            "apiAutomationIds": [],
            "uiAutomationIds": [test_id],
            "steps": [
                {"stepOrder": index, "actionText": action, "expectedText": expected if index == len(actions) else ""}
                for index, action in enumerate(actions, start=1)
            ],
        }
    )


if __name__ == "__main__":
    raise SystemExit(core.main())
