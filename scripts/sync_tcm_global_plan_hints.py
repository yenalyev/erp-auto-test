#!/usr/bin/env python3
"""Upsert this feature in local TCM; production writes use tcm-ai-prod MCP."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
PAYLOAD_PATH = ROOT / "docs/audits/2026-10-15/global-plan-tech-map-hints-tcm-payload.json"


def configure() -> None:
    payload = json.loads(PAYLOAD_PATH.read_text(encoding="utf-8-sig"))
    feature = payload["feature"]
    core.FEATURE_ID = feature["featureId"]
    core.FEATURE_PARENT_ID = feature["parentFeatureId"]
    core.FEATURE_TITLE = feature["title"]
    core.FEATURE_DESCRIPTION = feature["description"]
    core.FEATURE_MODULE = feature["module"]
    core.FEATURE_PRIORITY = feature["priority"]
    core.ACCEPTANCE_CRITERIA = [(ac["acKey"], ac["text"]) for ac in payload["acceptanceCriteria"]]
    core.CASES = payload["cases"]
    core.DOCUMENTATION_PATH = ROOT / "target/global-plan-hints-tcm-documentation.md"
    core.DOCUMENTATION_PATH.parent.mkdir(parents=True, exist_ok=True)
    core.DOCUMENTATION_PATH.write_text(feature["documentation"], encoding="utf-8")


if __name__ == "__main__":
    # This script must never be used as a production MCP fallback.
    args = argparse.ArgumentParser(add_help=False)
    args.add_argument("--base-url", required=True)
    options, _ = args.parse_known_args()
    if options.base_url.rstrip("/") != "http://localhost:18100":
        raise SystemExit("Only http://localhost:18100 is supported; use tcm-ai-prod MCP for production")
    configure()
    raise SystemExit(core.main())
