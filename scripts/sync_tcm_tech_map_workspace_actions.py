#!/usr/bin/env python3
"""Merge CPMA-895 documentation into existing TCM features without changing their metadata."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parent.parent
START = "<!-- CPMA-895:START -->"
END = "<!-- CPMA-895:END -->"
FEATURES = ("REQ-MFG-001", "REQ-MFG-001-01", "REQ-MFG-001-02", "REQ-MFG-001-03")
UNCHANGED_FIELDS = ("id", "featureId", "title", "description", "module", "priority",
                    "status", "parentFeatureId", "acceptanceCriteria")


def merge_documentation(existing: str, section: str) -> str:
    block = f"{START}\n{section}\n{END}"
    if START not in existing and END not in existing:
        return existing + "\n\n" + block
    if existing.count(START) != 1 or existing.count(END) != 1:
        raise ValueError("Ambiguous CPMA-895 documentation markers")
    start, end = existing.index(START), existing.index(END)
    if end < start:
        raise ValueError("Invalid CPMA-895 documentation marker order")
    return existing[:start] + block + existing[end + len(END):]


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
    summary = (
        "## CPMA-895: дії з техкартою при виборі Цукрарні\n\n"
        "Якщо обрано Цукрарню, техкарти дочірніх локацій видно у списку, але для редагування "
        "або клонування потрібно перейти на відповідну локацію. Після натискання дії та "
        "помилки завантаження UI показує спливне повідомлення: «Для вибраної Технологічної "
        "карти виберіть одну з наступних локацій: {назви локацій}» і повертає до списку. "
        "Це toast після натискання; він не надає права змінювати карту з Цукрарні. "
        "Оригінал не змінюється, копія чи нова версія не створюється.\n\n"
        "Після вибору вказаної локації через сайдбар користувач повторює дію. "
        "Потрібні відповідні права та відкритий режим редагування EDIT_ALLOWED. "
        "Повні критерії й автоматизовані сценарії — у REQ-MFG-001-03, розділ CPMA-895. "
        "На dev 2026-09-26 пройшли 4/4 UI-сценарії; це не є прогоном на production ERP."
    )
    prepared = []
    for feature_id in FEATURES:
        path = f"/api/ai/projects/{args.project_id}/features/{feature_id}"
        before = client.request("GET", path)
        section = source if feature_id == "REQ-MFG-001-03" else summary
        merged = merge_documentation(before.get("documentation") or "", section)
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
