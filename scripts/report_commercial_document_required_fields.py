#!/usr/bin/env python3
"""Build eight TCM case results from the actual 21 API executions and four UI retests."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path

from sync_tcm_commercial_document_required_fields import COUNTS, FEATURE_ID, ROOT


def summarize(api_dir: Path, ui_dir: Path, utc_offset: str) -> dict:
    sign = -1 if utc_offset.startswith("-") else 1
    hours, minutes = map(int, utc_offset[1:].split(":"))
    zone = timezone(sign * timedelta(hours=hours, minutes=minutes))
    selected = {}
    for layer, directory in (("API", api_dir), ("UI", ui_dir)):
        for path in directory.glob("*-result.json"):
            actual = json.loads(path.read_text(encoding="utf-8"))
            ids = [label["value"] for label in actual.get("labels", [])
                   if label["name"] == "testCaseId" and label["value"].startswith(f"TC-CD-REQ-{layer}-")]
            if not ids:
                continue
            assert len(ids) == 1
            test_id = ids[0]
            parameters = actual.get("parameters", [])
            key = (test_id, tuple(p["value"] for p in parameters))
            if key not in selected or selected[key]["stop"] < actual["stop"]:
                selected[key] = dict(actual, resultSource=str(path.relative_to(ROOT)), testId=test_id)
    assert len(selected) == 25, len(selected)
    results, executions = [], []
    for suffix, expected_count in COUNTS.items():
        test_id = "TC-CD-REQ-" + suffix
        group = [item for item in selected.values() if item["testId"] == test_id]
        assert len(group) == expected_count, (test_id, len(group))
        mapped = {"passed": "PASS", "failed": "FAIL", "broken": "FAIL", "skipped": "SKIPPED"}
        statuses = [mapped[item["status"]] for item in group]
        status = "PASS" if all(s == "PASS" for s in statuses) else (
            "FAIL" if "FAIL" in statuses else "SKIPPED")
        failures = [item.get("statusDetails", {}).get("message", "") for item in group
                    if item["status"] != "passed"]
        results.append(dict(testCaseId=test_id, status=status,
                            durationMs=sum(item["stop"] - item["start"] for item in group),
                            executedAt=datetime.fromtimestamp(min(item["start"] for item in group) / 1000,
                                                              zone).replace(tzinfo=None).isoformat(),
                            errorMessage="\n".join(failures)[:5000] if failures else None))
        for item in group:
            executions.append(dict(testId=test_id, status=mapped[item["status"]],
                                   parameters=item.get("parameters", []), method=item["fullName"],
                                   durationMs=item["stop"] - item["start"], source=item["resultSource"]))
    return dict(featureId=FEATURE_ID, environment="dev", timezone="Europe/Kiev", utcOffset=utc_offset,
                selection="API from full attempt; UI from successful retest. Each parameter set retained once.",
                sources=dict(api=str(api_dir.relative_to(ROOT)), ui=str(ui_dir.relative_to(ROOT))),
                counts=dict(Counter(item["status"] for item in executions)), executions=executions,
                tcmImport=dict(projectId=1, remoteRunId="commercial-document-required-fields-dev-20261020",
                               environment="dev", suite="commercial-document-required-fields",
                               runName="Обов'язкові поля комерційного документа — dev (API + UI retest)",
                               results=results))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-allure", type=Path, required=True)
    parser.add_argument("--ui-allure", type=Path, required=True)
    parser.add_argument("--utc-offset", required=True, help="Europe/Kiev offset at the execution date")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = summarize(args.api_allure.resolve(), args.ui_allure.resolve(), args.utc_offset)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(counts=report["counts"], cases=len(report["tcmImport"]["results"]))))


if __name__ == "__main__":
    main()
