#!/usr/bin/env python3
"""Commercial-document TCM payload and local sync; production writes use tcm-ai-prod MCP."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tomllib
import urllib.request

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parent.parent
DOCUMENT = ROOT / "docs/REQ-CD-REQ.md"
FEATURE_ID = "REQ-CD-REQ"
PARENT_ID = "REQ-DOCFLOW"
COUNTS = {"API-001": 9, "API-002": 9, "API-003": 2, "API-004": 1,
          "UI-001": 1, "UI-002": 1, "UI-003": 1, "UI-004": 1}


def payload() -> dict:
    documentation = DOCUMENT.read_text(encoding="utf-8")
    criteria = [dict(featureId=FEATURE_ID, acKey=key, text=text)
                for key, text in re.findall(r"^- \*\*(AC-\d+)\.\*\* (.+)$", documentation, re.M)]
    cases = []
    for match in re.finditer(
        r"^### (TC-CD-REQ-(API|UI)-\d{3}) — (.+?) \((AC-\d+)\)\n(.*?)(?=\n### |\n## |\Z)",
        documentation, re.M | re.S,
    ):
        test_id, layer, title, ac_key, body = match.groups()
        class_name, method = re.search(r"Автотест: `(\w+)#(\w+)`", body).groups()
        package = "functional.relocation" if layer == "API" else "ui"
        java = ROOT / f"src/test/java/com/erp/tests/{package.replace('.', '/')}/{class_name}.java"
        source = java.read_text(encoding="utf-8")
        assert f'@TestCaseId("{test_id}")' in source and f"void {method}(" in source, test_id
        actions = re.findall(r"^\d+\. (.+)$", body, re.M)
        expected = re.search(r"^Очікується: (.+)$", body, re.M).group(1)
        assert actions, test_id
        count = COUNTS[test_id.removeprefix("TC-CD-REQ-")]
        cases.append(dict(
            projectId=1, testId=test_id, featureId=FEATURE_ID, acKey=ac_key, title=title,
            status="ACTIVE", priority="HIGH", severity="MAJOR",
            testType="FUNCTIONAL" if layer == "API" else "UI",
            description=f"Автотест: com.erp.tests.{package}.{class_name}#{method}. "
                        f"Виконань: {count}. Вимоги та повна параметризація: docs/REQ-CD-REQ.md. "
                        "Suite: commercial-document-required-fields, relocations, regression; UI також ui. "
                        + ("Додаткові критерії: AC-02, AC-04. " if test_id.endswith("UI-002") else "")
                        + "Власні дані CommercialDocumentFixture; документи прибираються після методу, "
                          "надходження, облік, ресурси й локації — після класу.",
            preconditions="ERP dev, ADMIN із доступом до власної локації. Власні локації обліку "
                          "та зовнішнього постачальника, два ресурси A/B, надходження 2/3 од. "
                          "Для UI — Chrome та робочий простір локації обліку.",
            testData="Дата надходжень/API-документа — учора; UI — дата DatePicker. "
                     "Валідний PDF commercial-document.pdf; total=0; необов'язкові поля порожні. "
                     + body.split("\n\n", 1)[-1].split("\n\nОчікується:", 1)[0],
            expectedResult=expected,
            tags="docflow,commercial-document,required-fields,automated",
            apiAutomationIds=[test_id] if layer == "API" else [],
            uiAutomationIds=[test_id] if layer == "UI" else [],
            steps=[dict(stepOrder=index, actionText=action,
                        **({"expectedText": expected} if index == len(actions) else {}))
                   for index, action in enumerate(actions, 1)],
        ))
    assert len(criteria) == 6 and len(cases) == 8
    assert sum(COUNTS.values()) == 25
    for suite in ("commercial-document-required-fields", "relocations", "regression", "ui"):
        xml = (ROOT / f"src/test/resources/suites/{suite}.xml").read_text(encoding="utf-8")
        assert "com.erp.tests.ui.CommercialDocumentRequiredFieldsUiTest" in xml
        if suite != "ui":
            assert "com.erp.tests.functional.relocation.CommercialDocumentRequiredFieldsApiTest" in xml
    return dict(
        parent=dict(projectId=1, featureId=PARENT_ID, module="DOCFLOW", title="Документообіг переміщень",
                    description="Облік фактичних надходжень та комерційних документів у документообігу переміщень.",
                    priority="HIGH"),
        feature=dict(projectId=1, featureId=FEATURE_ID, parentFeatureId=PARENT_ID, module="DOCFLOW",
                     title="Новий комерційний документ — позначити обов'язкові поля",
                     description="Видимі позначки обов'язкових полів, API-валідація, блокування збереження, "
                                 "нульова сума, скасування та повторне відкриття. 8 кейсів, 25 виконань.",
                     priority="HIGH", documentation=documentation),
        criteria=criteria, cases=cases,
    )


def normalized_steps(steps: list[dict]) -> list[dict]:
    return [dict(stepOrder=s["stepOrder"], actionText=s["actionText"], expectedText=s.get("expectedText"))
            for s in steps]


def verify_case(actual: dict, expected: dict) -> None:
    assert actual.get("automation") is True, expected["testId"]
    for key in ("testId", "featureId", "title", "status", "priority", "severity", "testType",
                "description", "preconditions", "testData", "expectedResult", "tags",
                "apiAutomationIds", "uiAutomationIds"):
        assert actual.get(key) == expected[key], (expected["testId"], key)
    assert actual["acId"] == expected["acKey"]
    assert normalized_steps(actual["steps"]) == normalized_steps(expected["steps"])


def sync_local(client: TcmClient) -> dict:
    assert client.base_url == "http://localhost:18100", "Production must use tcm-ai-prod MCP"
    data = payload()
    prefix = "/api/ai/projects/1"
    parent_path = f"{prefix}/features/{PARENT_ID}"
    parent = client.get_optional(parent_path)
    if parent is None:
        client.request("POST", f"{prefix}/features", data["parent"])
    assert client.request("GET", parent_path)["featureId"] == PARENT_ID

    feature_path = f"{prefix}/features/{FEATURE_ID}"
    feature = client.get_optional(feature_path)
    client.request("POST" if feature is None else "PUT",
                   f"{prefix}/features" if feature is None else feature_path, data["feature"])
    current = client.request("GET", feature_path)
    ac_by_key = {a["acId"]: a for a in current["acceptanceCriteria"]}
    for expected in data["criteria"]:
        existing = ac_by_key.get(expected["acKey"])
        client.request("PUT" if existing else "POST",
                       f"{prefix}/acceptance-criteria/{existing['id']}" if existing
                       else f"{prefix}/acceptance-criteria", expected)
    current = client.request("GET", feature_path)
    ac_by_key = {a["acId"]: a for a in current["acceptanceCriteria"]}
    for expected in data["cases"]:
        case_path = f"{prefix}/test-cases/{expected['testId']}"
        existing = client.get_optional(case_path)
        write = dict(expected, acceptanceCriterionId=ac_by_key[expected["acKey"]]["id"])
        client.request("PUT" if existing else "POST", case_path if existing else f"{prefix}/test-cases", write)
        verify_case(client.request("GET", case_path), expected)

    current = client.request("GET", feature_path)
    for key in ("featureId", "title", "description", "documentation", "priority", "parentFeatureId"):
        assert current[key] == data["feature"][key], key
    for expected in data["criteria"]:
        assert ac_by_key[expected["acKey"]]["text"] == expected["text"]
    return dict(baseUrl=client.base_url, projectId=1, featureId=FEATURE_ID, featureDbId=current["id"],
                parentFeatureId=current["parentFeatureId"], criteriaVerified=6, casesVerified=8,
                apiCases=4, uiCases=4, automatedCases=8, executionVariants=25,
                documentationSha256=hashlib.sha256(current["documentation"].encode()).hexdigest(),
                result="SUCCESS")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--payload", type=Path)
    parser.add_argument("--sync-local", action="store_true")
    parser.add_argument("--import-results", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    if args.payload:
        args.payload.parent.mkdir(parents=True, exist_ok=True)
        args.payload.write_text(json.dumps(payload(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(dict(cases=8, criteria=6, payload=str(args.payload))))
    if args.sync_local or args.import_results:
        config = tomllib.loads((Path.home() / ".codex/config.toml").read_text(encoding="utf-8"))
        token = os.environ.get("TCM_AI_TOKEN") or config["mcp_servers"]["tcm-ai"]["env"]["TCM_AI_TOKEN"]
        client = TcmClient("http://localhost:18100", token)
        if args.sync_local:
            report = sync_local(client)
        if args.import_results:
            expected = payload()
            prefix = "/api/ai/projects/1"
            feature = client.request("GET", f"{prefix}/features/{FEATURE_ID}")
            assert feature["documentation"] == expected["feature"]["documentation"]
            for case in expected["cases"]:
                verify_case(client.request("GET", f"{prefix}/test-cases/{case['testId']}"), case)
            body = json.loads(args.import_results.read_text(encoding="utf-8"))["tcmImport"]
            body["featureId"] = feature["id"]
            try:
                imported = client.request("POST", f"{prefix}/test-runs/import", body)
            except RuntimeError as error:
                if "HTTP 405" not in str(error):
                    raise
                # Older local TCM exposes run imports through the autotest API only.
                local_env = ROOT.parent / "tcm/.env"
                values = {}
                if local_env.is_file():
                    for line in local_env.read_text(encoding="utf-8").splitlines():
                        key, separator, value = line.partition("=")
                        if separator and not key.lstrip().startswith("#"):
                            values[key.strip()] = value.strip().strip("\"'")
                run_token = os.environ.get("TCM_AUTOTEST_TOKEN") or values.get("TCM_AUTOTEST_TOKEN")
                if not run_token:
                    raise RuntimeError("Set TCM_AUTOTEST_TOKEN for local run import") from None
                headers = {"X-TCM-Api-Token": run_token, "Content-Type": "application/json; charset=utf-8"}
                # Resolves and commits the AUTO plan before the older importer starts its transaction.
                suite_request = urllib.request.Request(
                    f"http://localhost:18100/api/autotest/suites/features/{feature['id']}", headers=headers)
                with urllib.request.urlopen(suite_request, timeout=90) as response:
                    suite = json.load(response)
                assert set(suite["automationTestIds"]) == {r["testCaseId"] for r in body["results"]}
                request = urllib.request.Request(
                    "http://localhost:18100/api/autotest/runs", method="POST",
                    data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                    headers=headers)
                with urllib.request.urlopen(request, timeout=90) as response:
                    imported = json.load(response)
            assert imported["matched"] == 8 and not imported["unmatched"] and not imported["outOfScope"]
            run = client.request("GET", f"{prefix}/test-runs/{imported['runId']}")
            expected_statuses = {r["testCaseId"]: r["status"] for r in body["results"]}
            assert run["status"] == "COMPLETED" and run["environment"] == "dev"
            assert {r["testId"]: r["status"] for r in run["items"]} == expected_statuses
            for case in expected["cases"]:
                actual = client.request("GET", f"{prefix}/test-cases/{case['testId']}")
                assert actual["executionStatus"] == expected_statuses[case["testId"]]
            report = dict(baseUrl=client.base_url, featureDbId=feature["id"], importResponse=imported,
                          run=run, executionStatusesVerified=8, result="SUCCESS")
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        summary = report if "run" not in report else dict(
            baseUrl=report["baseUrl"], featureDbId=report["featureDbId"],
            runId=report["run"]["id"], matched=report["importResponse"]["matched"],
            executionStatusesVerified=report["executionStatusesVerified"], result=report["result"])
        print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
