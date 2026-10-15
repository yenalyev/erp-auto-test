#!/usr/bin/env python3
"""Append the MANUAL batch dev status to local TCM feature REQ-WMS-013.

Production TCM is updated separately through tcm-ai-prod MCP. This script refuses
non-local endpoints and never prints or stores the AI token.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs" / "REQ-MANUAL_RESOURCE_BATCH_ACCOUNTING.md"
HEADING = "## Стан перевірки на ERP dev — 15.10.2026"
BASE_URL = "http://localhost:18100"
FEATURE_PATH = "/api/ai/projects/1/features/REQ-WMS-013"


def token_from_environment() -> str:
    token = os.environ.get("TCM_AI_TOKEN")
    if token:
        return token
    local_env = ROOT.parent / "tcm" / ".env"
    for line in local_env.read_text(encoding="utf-8").splitlines():
        if line.startswith("TCM_AI_TOKEN="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise RuntimeError("TCM_AI_TOKEN is unavailable")


def request(method: str, token: str, body: dict | None = None) -> dict:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body else None
    headers = {"X-TCM-Ai-Token": token}
    if data is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(
        BASE_URL + FEATURE_PATH, data=data, headers=headers, method=method
    )
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)


def digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    source = SOURCE.read_text(encoding="utf-8")
    if HEADING not in source:
        raise RuntimeError(f"Missing status section: {HEADING}")
    status_section = HEADING + "\n\n" + source.split(HEADING, 1)[1].strip()
    token = token_from_environment()
    before = request("GET", token)
    if before.get("featureId") != "REQ-WMS-013":
        raise RuntimeError("Unexpected TCM feature")
    previous_doc = before.get("documentation") or ""
    preserved = previous_doc.split(HEADING, 1)[0].rstrip()
    next_doc = preserved + "\n\n" + status_section + "\n"
    before_ac = [(item["acId"], item["text"]) for item in before.get("acceptanceCriteria", [])]

    if args.apply and previous_doc != next_doc:
        request("PUT", token, {"documentation": next_doc})
    after = request("GET", token) if args.apply else before
    if args.apply:
        if after.get("documentation") != next_doc:
            raise RuntimeError("TCM documentation read-back differs from source")
        after_ac = [(item["acId"], item["text"]) for item in after.get("acceptanceCriteria", [])]
        if after_ac != before_ac:
            raise RuntimeError("TCM acceptance criteria changed unexpectedly")

    report = {
        "environment": "local-dev-tcm",
        "baseUrl": BASE_URL,
        "featureId": before["featureId"],
        "applied": args.apply,
        "changed": previous_doc != next_doc,
        "beforeDocumentationSha256": digest(previous_doc),
        "expectedDocumentationSha256": digest(next_doc),
        "afterDocumentationSha256": digest(after.get("documentation") or ""),
        "acceptanceCriteriaUnchanged": [
            (item["acId"], item["text"]) for item in after.get("acceptanceCriteria", [])
        ] == before_ac,
    }
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
