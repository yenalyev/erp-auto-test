#!/usr/bin/env python3
"""Summarize actual Allure results and TestNG configuration failures; build TCM import."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


def attachments(node):
    yield from node.get("attachments", [])
    for step in node.get("steps", []):
        yield from attachments(step)


def summarize(allure_dir, testng_xml, remote_run_id, output, expected_count=39, utc_offset=None,
              overlay_allure=None, overlay_testng=None):
    try:
        local_zone = ZoneInfo("Europe/Kiev")
    except ZoneInfoNotFoundError:
        if not utc_offset or not re.fullmatch(r"[+-]\d{2}:\d{2}", utc_offset):
            raise RuntimeError("Install tzdata or pass --utc-offset matching Europe/Kiev at execution time")
        hours, minutes = map(int, utc_offset[1:].split(":"))
        if hours > 14 or minutes > 59:
            raise ValueError("Invalid UTC offset")
        local_zone = timezone((1 if utc_offset[0] == "+" else -1) * timedelta(hours=hours, minutes=minutes))
    executions = []
    sources = [allure_dir, *(overlay_allure or [])]
    xml_sources = [testng_xml, *(overlay_testng or [])]
    latest, attempts = {}, []
    for directory in sources:
        counts = Counter()
        for path in sorted(directory.glob("*-result.json")):
            result = json.loads(path.read_text(encoding="utf-8"))
            ids = [label["value"] for label in result.get("labels", []) if label["name"] == "testCaseId"]
            ids = [value for value in ids if re.fullmatch(r"TC-REL-SUP-(API|UI)-\d{3}", value)]
            if not ids:
                continue
            assert len(ids) == 1, ids
            counts[result["status"]] += 1
            # Parameter display names can be arg0 after focused javac; values and order identify the invocation.
            key = (ids[0], tuple(p["value"] for p in result.get("parameters", [])))
            if key not in latest or result["stop"] > latest[key][2]["stop"]:
                latest[key] = (ids[0], directory, result)
        attempts.append(dict(source=str(directory), counts=dict(counts)))
    evidence_dir = output.parent / "relocation-supplier-evidence"
    evidence_dir.mkdir(parents=True, exist_ok=True)
    for test_id, directory, result in latest.values():
        evidence = []
        for attachment in attachments(result):
            source = directory / attachment["source"]
            if source.suffix in (".png", ".xlsx") and source.is_file():
                target = evidence_dir / source.name
                shutil.copyfile(source, target)
                evidence.append(str(target.relative_to(output.parent)))
        executions.append(dict(
            testId=test_id, method=result["fullName"], parameters=result.get("parameters", []), source=str(directory),
            status={"passed": "PASS", "failed": "FAIL", "broken": "FAIL", "skipped": "SKIPPED"}[result["status"]],
            durationMs=result["stop"] - result["start"],
            executedAt=datetime.fromtimestamp(result["start"] / 1000, local_zone).replace(tzinfo=None).isoformat(),
            message=result.get("statusDetails", {}).get("message", "")[:5000], evidence=evidence,
        ))
    if len(executions) != expected_count:
        raise RuntimeError(f"Expected {expected_count} actual executions, got {len(executions)}")
    failures = []
    for xml in xml_sources:
        for method in ET.parse(xml).getroot().findall(".//test-method"):
            if method.attrib.get("is-config") == "true" and method.attrib.get("status") == "FAIL":
                failures.append(dict(source=str(xml), method=method.attrib["name"],
                                     message=method.findtext("./exception/message", "")[:5000]))
    results = []
    for test_id in sorted({item["testId"] for item in executions}):
        group = [item for item in executions if item["testId"] == test_id]
        status = "FAIL" if any(item["status"] == "FAIL" for item in group) else (
            "PASS" if all(item["status"] == "PASS" for item in group) else "BLOCKED")
        messages = [f"{item['parameters']}: {item['message']}" for item in group if item["status"] != "PASS"]
        results.append(dict(testCaseId=test_id, status=status, durationMs=sum(item["durationMs"] for item in group),
                            executedAt=min(item["executedAt"] for item in group),
                            errorMessage="\n".join(messages)[:5000] if messages else None))
    summary = dict(sources=[str(p) for p in sources], testngSources=[str(p) for p in xml_sources],
                   attempts=attempts, selection="Latest actual result for each testId and parameter set", timezone="Europe/Kiev",
                   counts=dict(Counter(item["status"] for item in executions)),
                   configurationFailures=failures, executions=executions,
                   tcmImport=dict(projectId=1, featureId=166, environment="dev", suite="relocation-supplier-filters",
                                  remoteRunId=remote_run_id,
                                  runName="Supplier filter — ERP dev" + (" (full + search retest)" if overlay_allure else ""),
                                  results=results))
    output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(counts=summary["counts"], configurationFailures=len(failures), cases=len(results))))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("allure_dir", type=Path)
    parser.add_argument("testng_xml", type=Path)
    parser.add_argument("--remote-run-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-count", type=int, default=39)
    parser.add_argument("--utc-offset", help="Fallback for Python without tzdata, e.g. +03:00; must match run date")
    parser.add_argument("--overlay-allure-dir", type=Path, action="append", default=[], help="Actual rerun results; latest per parameters wins")
    parser.add_argument("--overlay-testng-xml", type=Path, action="append", default=[], help="Also validate rerun configuration/cleanup")
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    summarize(args.allure_dir, args.testng_xml, args.remote_run_id, args.output, args.expected_count, args.utc_offset,
              args.overlay_allure_dir, args.overlay_testng_xml)
