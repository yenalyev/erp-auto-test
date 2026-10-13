#!/usr/bin/env python3
"""Sync business rules for workspace actions into their dedicated TCM feature."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parent.parent
FEATURES = ("REQ-MFG-001-05",)
UNCHANGED_FIELDS = ("id", "featureId", "title", "description", "module", "priority",
                    "status", "parentFeatureId", "acceptanceCriteria")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--project-id", type=int, default=1)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--apply", action="store_true", help="Otherwise only validate and report the proposed changes")
    parser.add_argument("--insecure", action="store_true")
    args = parser.parse_args()
    token = os.environ.get(args.token_env)
    if not token:
        parser.error(f"Missing {args.token_env}")
    client = TcmClient(args.base_url, token, args.insecure)
    source = (ROOT / "docs/REQ-MFG-WORKSPACE-ACTIONS.md").read_text(encoding="utf-8-sig").strip()
    prepared = []
    for feature_id in FEATURES:
        path = f"/api/ai/projects/{args.project_id}/features/{feature_id}"
        before = client.request("GET", path)
        merged = source
        prepared.append((path, before, merged))
    report = {"baseUrl": args.base_url, "projectId": args.project_id,
              "timestamp": datetime.now(timezone.utc).isoformat(), "applied": args.apply, "features": []}
    for path, before, merged in prepared:
        changed = merged != (before.get("documentation") or "")
        if args.apply and changed:
            # Re-read before writing to avoid replacing a concurrently edited document.
            if client.request("GET", path) != before:
                raise RuntimeError(f"Concurrent feature change: {before['featureId']}")
            client.request("PUT", path, {"documentation": merged})
        after = client.request("GET", path) if args.apply else before
        if args.apply:
            assert after["documentation"] == merged, before["featureId"]
            for key in UNCHANGED_FIELDS:
                assert after.get(key) == before.get(key), (before["featureId"], key)
        report["features"].append({
            "featureId": before["featureId"], "id": before["id"], "changed": changed,
            "documentationVerified": args.apply and after["documentation"] == merged,
            "otherFieldsPreserved": args.apply,
            "beforeSha256": hashlib.sha256((before.get("documentation") or "").encode()).hexdigest(),
            "afterSha256": hashlib.sha256(merged.encode()).hexdigest(),
        })
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True))


if __name__ == "__main__":
    main()
