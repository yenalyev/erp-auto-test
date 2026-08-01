# -*- coding: utf-8 -*-
"""Reorganize TCM root features per plan tcm_root_features_reorg."""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8100"
PROJECT_ID = 1
TOKEN = "dev-ai-token"
HDR = {
    "X-TCM-Ai-Token": TOKEN,
    "Accept": "application/json",
    "Content-Type": "application/json; charset=utf-8",
}


def request(method: str, path: str, body: dict | None = None) -> dict:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=HDR, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> HTTP {e.code}: {detail}") from e


def get_feature(feature_id: str) -> dict:
    enc = urllib.parse.quote(feature_id, safe="")
    return request("GET", f"/api/ai/projects/{PROJECT_ID}/features/{enc}")


def create_feature(body: dict) -> dict:
    return request("POST", f"/api/ai/projects/{PROJECT_ID}/features", body)


def update_feature(feature_id: str, body: dict) -> dict:
    enc = urllib.parse.quote(feature_id, safe="")
    return request("PUT", f"/api/ai/projects/{PROJECT_ID}/features/{enc}", body)


def main() -> int:
    # 1) CREW root
    try:
        existing = get_feature("REQ-CREW")
        print("REQ-CREW already exists:", existing.get("title"), "parent=", existing.get("parentFeatureId"))
    except RuntimeError as e:
        if "404" in str(e) or "not found" in str(e).lower() or "400" in str(e):
            created = create_feature(
                {
                    "featureId": "REQ-CREW",
                    "title": "Екіпажі та точки вильоту",
                    "module": "CREW",
                    "priority": "HIGH",
                }
            )
            print("created REQ-CREW:", created.get("featureId"), created.get("title"))
        else:
            raise

    crew_children = [
        ("REQ-CREW-001", "Області CREWS / членство"),
        ("REQ-CREW-002", "Видача на екіпаж і FLY"),
        ("REQ-CREW-003", "Звіти та залишки екіпажу"),
    ]
    for fid, title in crew_children:
        upd = update_feature(fid, {"title": title, "parentFeatureId": "REQ-CREW"})
        print(
            "crew child",
            fid,
            "->",
            upd.get("title"),
            "parent=",
            upd.get("parentFeatureId"),
            "depth=",
            upd.get("treeDepth"),
        )

    # 2) Plans umbrella
    pln = update_feature("REQ-PLN", {"title": "Планування виробництва"})
    print("rename REQ-PLN ->", pln.get("title"))
    for fid in ("REQ-GLOBAL-PLAN", "REQ-MAN-PLN", "REQ-DEC-MAN-PLN"):
        upd = update_feature(fid, {"parentFeatureId": "REQ-PLN"})
        print(
            "plan child",
            fid,
            "parent=",
            upd.get("parentFeatureId"),
            "depth=",
            upd.get("treeDepth"),
        )

    # 3) Under WMS
    for fid in ("REQ-EDIT_REL", "REQ-OPER-HIST"):
        upd = update_feature(fid, {"parentFeatureId": "REQ-WMS"})
        print(
            "wms child",
            fid,
            "parent=",
            upd.get("parentFeatureId"),
            "depth=",
            upd.get("treeDepth"),
        )

    # 4) Under MFG
    ns = update_feature("REQ-NON-SER-MAN", {"parentFeatureId": "REQ-MFG"})
    print(
        "mfg child REQ-NON-SER-MAN parent=",
        ns.get("parentFeatureId"),
        "depth=",
        ns.get("treeDepth"),
    )

    # 5) RVW + WOLF
    rvw = update_feature(
        "REQ-RVW",
        {"title": "Відстеження ресурсів (BOM / viewer)"},
    )
    print("rename REQ-RVW ->", rvw.get("title"))
    wolf = update_feature("REQ-WOLF", {"parentFeatureId": "REQ-RVW"})
    print(
        "rvw child REQ-WOLF parent=",
        wolf.get("parentFeatureId"),
        "depth=",
        wolf.get("treeDepth"),
    )

    print("DONE")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001
        print("ERROR:", exc, file=sys.stderr)
        raise SystemExit(1) from exc
