"""Sync the active FAITA scope and preserve retired cases; prod uses tcm-ai-prod MCP."""
import concurrent.futures
import json
import os
import urllib.request
import sync_tcm_faita_expense_mapping as spec


def main():
    client=spec.core.TcmClient("http://localhost:18100",os.environ["TCM_AI_TOKEN"])
    report=dict(baseUrl=client.base_url,projectId=1,features=[],activeCases=[],retiredCases=[])
    root_id=None
    ac_ids={}
    for feature,parent,title,acs in spec.FEATURES:
        path=f"/api/ai/projects/1/features/{feature}"
        before=client.request("GET",path)
        if before["documentation"]!=spec.DOC.read_text(encoding="utf-8"):
            client.request("PUT",path,{"documentation":spec.DOC.read_text(encoding="utf-8")})
        existing={a["acId"]:a for a in before["acceptanceCriteria"]}
        for key,text in acs:
            if key not in existing:
                client.request("POST","/api/ai/projects/1/acceptance-criteria",{"featureId":feature,"acKey":key,"text":text})
            elif existing[key]["text"]!=text:
                client.request("PUT",f"/api/ai/projects/1/acceptance-criteria/{existing[key]['id']}",{"text":text})
        saved=client.request("GET",path)
        assert saved["documentation"]==spec.DOC.read_text(encoding="utf-8")
        assert all(any(a["acId"]==key and a["text"]==text for a in saved["acceptanceCriteria"]) for key,text in acs)
        report["features"].append(dict(featureId=feature,documentationAndAllAcTextsVerified=True))
        ac_ids.update({(feature,a["acId"]):a["id"] for a in saved["acceptanceCriteria"]})
        if feature==spec.PARENT: root_id=saved["id"]

    for test_id in spec.RETIRED_CASE_IDS:
        path=f"/api/ai/projects/1/test-cases/{test_id}"
        before=client.request("GET",path)
        description=before["description"]
        if spec.RETIREMENT_REASON not in description: description+="\n\n"+spec.RETIREMENT_REASON
        if before["status"]!="DEPRECATED" or before["apiAutomationIds"] or before["uiAutomationIds"] or before["description"]!=description:
            client.request("PUT",path,dict(title=before["title"],status="DEPRECATED",apiAutomationIds=[],uiAutomationIds=[],description=description))
            saved=client.request("GET",path)
        else: saved=before
        assert saved["status"]=="DEPRECATED" and not saved["apiAutomationIds"] and not saved["uiAutomationIds"]
        assert saved["steps"]==before["steps"] and saved["executionStatus"]==before["executionStatus"]
        report["retiredCases"].append(dict(testId=test_id,status=saved["status"],automationUnlinked=True,historyPreserved=True))

    def get_case(case):
        return case,client.get_optional(f"/api/ai/projects/1/test-cases/{case['testId']}")
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        existing=list(pool.map(get_case,spec.CASES))
    for case,before in existing:
        path=f"/api/ai/projects/1/test-cases/{case['testId']}"
        fields=["title","description","preconditions","testData","expectedResult","status","priority","severity","testType","tags","apiAutomationIds","uiAutomationIds"]
        if before is None:
            client.request("POST","/api/ai/projects/1/test-cases",{**case,"acceptanceCriterionId":ac_ids[(case["featureId"],case["acKey"])]})
            saved=client.request("GET",path)
        elif any(before[key]!=case[key] for key in fields) or len(before["steps"])!=len(case["steps"]) or any(
                a[key]!=e[key] for a,e in zip(before["steps"],case["steps"]) for key in ["stepOrder","actionText","expectedText"]):
            client.request("PUT",path,case)
            saved=client.request("GET",path)
        else: saved=before
        assert all(saved[key]==case[key] for key in fields)
        assert saved["featureId"]==case["featureId"] and saved["acId"]==case["acKey"]
        assert len(saved["steps"])==len(case["steps"])
        assert all(a[key]==e[key] for a,e in zip(saved["steps"],case["steps"]) for key in ["stepOrder","actionText","expectedText"])
        report["activeCases"].append(dict(testId=case["testId"],allFieldsAndStepsVerified=True))
    headers={"X-TCM-Api-Token":os.environ["TCM_AUTOTEST_TOKEN"],"Accept":"application/json"}
    request=urllib.request.Request(f"http://localhost:18100/api/autotest/suites/features/{root_id}",headers=headers)
    with urllib.request.urlopen(request,timeout=90) as response: suite=json.load(response)
    assert set(suite["automationTestIds"])=={c["testId"] for c in spec.CASES}
    report["resolvedAutomationIds"]=sorted(suite["automationTestIds"])
    path=spec.ROOT/"docs/audits/2026-10-17/faita-mapping-crud-tcm-local-sync.json"
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    print(f"Verified dev TCM: {len(spec.RETIRED_CASE_IDS)} retired cases; {len(spec.CASES)} active cases; 4 docs/{sum(len(f[3]) for f in spec.FEATURES)} AC; runner IDs verified")


if __name__=="__main__": main()
