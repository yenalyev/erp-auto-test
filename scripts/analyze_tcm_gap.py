#!/usr/bin/env python3
"""Compare TCM export with erp-auto-test @TestCaseId annotations."""
import os
import re
import zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict

TCM_PATH = r"C:\Users\gigam\Downloads\tcm-ERP__________-project-20260623.xlsx"
PROJECT_ROOT = r"c:\Users\gigam\IdeaProjects\erp-auto-test"
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def col_row(ref):
    col = "".join(c for c in ref if c.isalpha())
    row = int("".join(c for c in ref if c.isdigit()))
    return col, row


def col_to_idx(col):
    n = 0
    for c in col:
        n = n * 26 + (ord(c) - 64)
    return n - 1


def read_tcm_test_cases(path):
    with zipfile.ZipFile(path) as z:
        ss = []
        root = ET.fromstring(z.read("xl/sharedStrings.xml"))
        for si in root.findall("m:si", NS):
            texts = [t.text or "" for t in si.findall(".//m:t", NS)]
            ss.append("".join(texts))

        wb = ET.fromstring(z.read("xl/workbook.xml"))
        rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
        rid_map = {
            rel.get("Id"): (
                "xl/" + rel.get("Target")
                if not rel.get("Target").startswith("xl/")
                else rel.get("Target")
            )
            for rel in rels
        }
        sheet_files = {}
        for s in wb.findall("m:sheets/m:sheet", NS):
            name = s.get("name")
            rid = s.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
            sheet_files[name] = rid_map[rid]

        data = defaultdict(dict)
        root = ET.fromstring(z.read(sheet_files["TestCases"]))
        for row in root.findall("m:sheetData/m:row", NS):
            for c in row.findall("m:c", NS):
                ref = c.get("r")
                col, r = col_row(ref)
                ci = col_to_idx(col)
                v = c.find("m:v", NS)
                val = v.text if v is not None else ""
                if c.get("t") == "s" and val.isdigit():
                    val = ss[int(val)]
                data[r][ci] = val
        max_col = max(max(row.keys()) for row in data.values())
        rows = [[data[r].get(i, "") for i in range(max_col + 1)] for r in sorted(data)]
        hdr = {h: i for i, h in enumerate(rows[0])}
        cases = {r[hdr["testId"]]: r for r in rows[1:]}
        return hdr, cases


def collect_automation_ids(root):
    auto = set()
    pattern = re.compile(r'@TestCaseId\("([^"]+)"\)')
    for dp, _, fns in os.walk(root):
        if "target" in dp.replace("\\", "/").split("/"):
            continue
        for fn in fns:
            if fn.endswith(".java"):
                with open(os.path.join(dp, fn), encoding="utf-8", errors="ignore") as f:
                    auto.update(pattern.findall(f.read()))
    return auto


def bucket(test_id):
    if test_id.startswith("TC-UI-PROD"):
        return "Production UI"
    if test_id.startswith("TC-PRD"):
        return "Production API"
    if test_id.startswith("TC-UI-REL-EQ") or test_id.startswith("TC-REL-EQ"):
        return "Equipment relocation"
    if test_id.startswith("TC-UI-REL") or test_id.startswith("TC-REL"):
        return "Relocation"
    if test_id.startswith("TC-DEF"):
        return "Defect"
    if test_id.startswith("TC-NSP") or test_id.startswith("TC-UI-NSP"):
        return "Non-series production"
    if test_id.startswith("TC-RES"):
        return "Resources"
    if test_id.startswith("TC-STR"):
        return "Storage"
    if test_id.startswith("TC-MU"):
        return "Measurement units"
    if test_id.startswith("TC-MFG"):
        return "Tech maps"
    if test_id.startswith("TC-AUTH") or test_id.startswith("TC-SMOKE"):
        return "Auth"
    if test_id.startswith("TC-UI"):
        return "UI general"
    if test_id.startswith("TC-RBAC"):
        return "RBAC"
    return "Other"


if __name__ == "__main__":
    hdr, tcm = read_tcm_test_cases(TCM_PATH)
    auto = collect_automation_ids(PROJECT_ROOT)
    missing = sorted(auto - set(tcm))
    groups = defaultdict(list)
    for m in missing:
        groups[bucket(m)].append(m)

    print(f"TCM cases: {len(tcm)}")
    print(f"Automation IDs: {len(auto)}")
    print(f"Missing from TCM: {len(missing)}")
    for g in sorted(groups):
        print(f"\n{g} ({len(groups[g])}):")
        for x in groups[g]:
            print(f"  {x}")
