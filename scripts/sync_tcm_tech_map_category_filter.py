"""Sync category-filter documentation, ACs and automated cases to local/dev TCM only."""
import argparse
import hashlib
import json
import os
import tomllib
from pathlib import Path

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parents[1]
MARKER = '\n\n## Фільтр «Категорія виробу»'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    payload = json.loads(args.payload.read_text(encoding="utf-8"))
    assert payload["featureId"] == "REQ-MFG-001-03"
    assert len(payload["criteria"]) == 2 and len(payload["cases"]) == 12
    token = os.environ.get("TCM_AI_TOKEN")
    if not token:
        config = tomllib.loads((Path.home() / ".codex/config.toml").read_text(encoding="utf-8"))
        token = config["mcp_servers"]["tcm-ai"]["env"]["TCM_AI_TOKEN"]
    client = TcmClient("http://localhost:18100", token)
    prefix = "/api/ai/projects/1"
    feature_path = f"{prefix}/features/{payload['featureId']}"
    before = client.request("GET", feature_path)
    # Preserve the local feature's other documentation and metadata.
    expected_doc = (before.get("documentation") or "").split(MARKER)[0] + MARKER + payload["documentation"].split(MARKER, 1)[1]
    client.request("PUT", feature_path, {"documentation": expected_doc})
    feature = client.request("GET", feature_path)
    assert feature["documentation"] == expected_doc
    existing_ac = {ac["acId"]: ac for ac in feature["acceptanceCriteria"]}
    managed = {}
    for criterion in payload["criteria"]:
        key = criterion["acKey"]
        current = existing_ac.get(key)
        if current:
            result = client.request("PUT", f"{prefix}/acceptance-criteria/{current['id']}",
                                    {"text": criterion["text"]})
        else:
            result = client.request("POST", f"{prefix}/acceptance-criteria",
                                    {"featureId": payload["featureId"], **criterion})
        readback = client.request("GET", feature_path)
        actual = next(ac for ac in readback["acceptanceCriteria"] if ac["acId"] == key)
        assert actual["text"] == criterion["text"]
        managed[key] = result["id"]
    verified = []
    for expected in payload["cases"]:
        case_path = f"{prefix}/test-cases/{expected['testId']}"
        current = client.get_optional(case_path)
        body = {**expected, "acceptanceCriterionId": managed[expected["acKey"]]}
        client.request("PUT" if current else "POST", case_path if current else f"{prefix}/test-cases", body)
        actual = client.request("GET", case_path)
        for key in ("title", "description", "preconditions", "testData", "expectedResult", "tags",
                    "priority", "severity", "status", "testType", "apiAutomationIds", "uiAutomationIds"):
            assert actual.get(key) == expected.get(key), (expected["testId"], key)
        assert actual["steps"] == expected["steps"]
        assert actual["featureId"] == payload["featureId"] and actual["acId"] == expected["acKey"]
        assert actual["automation"]
        verified.append({"testId": expected["testId"], "id": actual["id"], "automation": True})
    final = client.request("GET", feature_path)
    assert final["documentation"] == expected_doc
    for key in ("title", "description", "parentFeatureId", "priority", "status"):
        assert final.get(key) == before.get(key), key
    old_criteria = {ac["acId"]: ac for ac in before["acceptanceCriteria"] if ac["acId"] not in managed}
    for criterion in final["acceptanceCriteria"]:
        if criterion["acId"] in old_criteria:
            assert criterion == old_criteria[criterion["acId"]]
    report = {"destination": "dev/local TCM", "baseUrl": client.base_url,
              "featureId": payload["featureId"], "featureDbId": final["id"],
              "documentationReadbackVerified": True,
              "documentationSha256": hashlib.sha256(expected_doc.encode("utf-8")).hexdigest(),
              "managedCriteria": managed, "verifiedCases": verified,
              "executionStatusesChanged": False, "unrelatedMetadataPreserved": True}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"documentationVerified": True, "criteriaVerified": len(managed),
                      "automatedCasesVerified": len(verified)}))


if __name__ == "__main__":
    main()
