#!/usr/bin/env python3
"""Use repository cases for local TCM sync; production catalog is consumed via MCP."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
core.FEATURE_ID = "REQ-NSP-EXCEL"
core.FEATURE_PARENT_ID = "REQ-NON-SER-MAN"
core.FEATURE_TITLE = "Несерійне виробництво — експорт в Excel"
core.FEATURE_DESCRIPTION = "Активні фільтри журналу визначають повну вибірку Excel; зміна та очищення фільтрів оновлюють наступний файл."
core.FEATURE_MODULE = "NSP"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs/REQ-NSP-EXCEL.md"
documentation = core.DOCUMENTATION_PATH.read_text(encoding="utf-8")
core.ACCEPTANCE_CRITERIA = []
for line in documentation.splitlines():
    if line.startswith("- **AC-"):
        key, text = line.removeprefix("- **").split(".** ", 1)
        core.ACCEPTANCE_CRITERIA.append((key, text))

core.CASES = []
for match in re.finditer(r"^### (TC-NSP-XLSX-(API|UI)-\d+) — ([^\n]+)\n(.*?)(?=^### |^## |\Z)", documentation, re.M | re.S):
    test_id, layer, title, body = match.groups()
    ac = {"TC-NSP-XLSX-API-001": "AC-06", "TC-NSP-XLSX-API-005": "AC-02",
          "TC-NSP-XLSX-API-006": "AC-04", "TC-NSP-XLSX-API-007": "AC-07",
          "TC-NSP-XLSX-UI-002": "AC-03"}.get(test_id, re.search(r"АС: (AC-\d+)", body).group(1))
    method = re.search(r"Метод: `([^`]+)`", body).group(1)
    expected = " ".join(body.split("Очікування:", 1)[1].split())
    actions = re.findall(r"^\d+\. (.*?)(?=^\d+\. |\n\nОчікування:)", body, re.M | re.S)
    package = "ui" if layer == "UI" else "functional.non_series_production"
    class_name = "NonSeriesExcelExportUiTest" if layer == "UI" else "NonSeriesExcelExportApiTest"
    steps = [{"stepOrder": i, "actionText": " ".join(action.split())} for i, action in enumerate(actions, 1)]
    steps[-1]["expectedText"] = expected
    core.CASES.append({
        "testId": test_id, "featureId": core.FEATURE_ID, "acKey": ac,
        "title": title, "status": "ACTIVE", "priority": "HIGH", "severity": "MAJOR",
        "testType": "UI" if layer == "UI" else "FUNCTIONAL",
        "description": f"Автотест com.erp.tests.{package}.{class_name}#{method}; @TestCaseId(\"{test_id}\"). "
                       "Документація: docs/REQ-NSP-EXCEL.md. Suites: non-series-production, regression; "
                       + ("ui." if layer == "UI" else "functional."),
        "preconditions": "Dev ERP, авторизована браузерна сесія ADMIN; успішна NonSeriesResourceFilterFixture "
                         "з двома власними локаціями, трьома матеріалами й шістьма виробничими записами. "
                         "Власні операції видаляються, локації архівуються після класу.",
        "testData": "Основна локація: Альфа/2/IN_PROGRESS/сьогодні/Мідь; Бета/3/DONE/сьогодні/Мідь+Мідь2+Сталь; "
                    "назва Мідь/5/сьогодні/тільки Сталь; без сировини/7/сьогодні; Раніше/11/сьогодні-10/Мідь. "
                    "Сусідня локація: 13/Мідь. UI-004 додає 21 власний виріб по 1 од. Імена унікальні.",
        "expectedResult": expected, "steps": steps,
        "tags": "non-series-production,excel,filters,automated",
        "apiAutomationIds": [test_id] if layer == "API" else [],
        "uiAutomationIds": [test_id] if layer == "UI" else [],
    })

assert len(core.CASES) == 11
assert len(core.ACCEPTANCE_CRITERIA) == 7

if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--export":
        Path(sys.argv[2]).write_text(json.dumps({
            "feature": {"featureId": core.FEATURE_ID, "parentFeatureId": core.FEATURE_PARENT_ID,
                        "title": core.FEATURE_TITLE, "description": core.FEATURE_DESCRIPTION,
                        "documentation": documentation, "module": core.FEATURE_MODULE, "priority": core.FEATURE_PRIORITY},
            "criteria": [{"acKey": key, "text": text} for key, text in core.ACCEPTANCE_CRITERIA],
            "cases": core.CASES,
        }, ensure_ascii=False, indent=2), encoding="utf-8")
        print("Exported 7 criteria and 11 automated cases")
    else:
        raise SystemExit(core.main())
