"""Report the fresh 27-case dev review without altering historical 31-case runs."""
import argparse
from collections import Counter
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import re
import shutil
import report_faita_expense_mapping as history
import sync_tcm_faita_expense_mapping as spec

ROOT=spec.ROOT
OUT=ROOT/"docs/audits/2026-10-17"
PREFIX="faita-mapping-current"


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--import-local",action="store_true")
    args=parser.parse_args()
    if args.import_local:
        raise SystemExit("Historical scope: runs 199/121 are immutable. Use report_faita_mapping_crud.py for the new suite.")
    snapshot=json.loads((OUT/"faita-mapping-current-tcm-payload.json").read_text(encoding="utf-8"))
    expected={c["testId"]:c for c in snapshot["testCases"]}
    folder=ROOT/"target/faita-mapping-current-review-allure"
    latest={}
    for path in folder.glob("*-result.json"):
        raw=json.loads(path.read_text(encoding="utf-8"))
        match=re.search(r"TC-FMAP-(?:UI|API|DB)-\d{3}",raw.get("name",""))
        if match:
            assert match[0] in expected,"Unexpected/retired case executed: "+match[0]
            if match[0] not in latest or latest[match[0]]["stop"]<raw["stop"]: latest[match[0]]=raw
    assert set(latest)==set(expected) and len(expected)==27
    for path in folder.glob("*-container.json"):
        container=json.loads(path.read_text(encoding="utf-8"))
        assert not any(f.get("status") in ("failed","broken") for f in container.get("befores",[])+container.get("afters",[])),path
    framework=(ROOT/"target/faita-mapping-current-review-framework.log").read_text(encoding="utf-8",errors="replace")
    summaries=re.findall(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)",framework)
    assert summaries and summaries[-1][1:]==("0","0","0")
    results=[]
    evidence=OUT/(PREFIX+"-evidence")
    evidence.mkdir(exist_ok=True)
    for key,raw in sorted(latest.items()):
        result=dict(testCaseId=key,status={"passed":"PASS","failed":"FAIL","broken":"FAIL","skipped":"SKIPPED"}[raw["status"]],
            durationMs=raw["stop"]-raw["start"],executedAt=datetime.fromtimestamp(raw["start"]/1000,timezone(timedelta(hours=3))).replace(tzinfo=None).isoformat(timespec="seconds"),
            errorMessage=raw.get("statusDetails",{}).get("message","")[:6000] or None,featureId=expected[key]["featureId"])
        images=[a for a in history.attachments(raw) if a.get("type")=="image/png"]
        if images:
            destination=evidence/(key+".png")
            shutil.copyfile(folder/images[-1]["source"],destination)
            result["screenshot"]=str(destination.relative_to(OUT)).replace("\\","/")
        results.append(result)
    counts=dict(Counter(r["status"] for r in results))
    output=dict(environment="dev",sourceAllureDirectory=str(folder.relative_to(ROOT)),counts=counts,results=results,
        retiredCaseIds=spec.RETIRED_CASE_IDS,configurationFailureCount=0,frameworkPass=int(summaries[-1][0]),
        historicalRunsPreserved=dict(dev=198,prod=120))
    (OUT/(PREFIX+"-dev-results.json")).write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding="utf-8")
    if args.import_local:
        history.import_local(results,remote_run_id="faita-mapping-dev-20261017-current-review",report_prefix=PREFIX,
            run_name="FAITA mapping dev 2026-10-17 reviewed 27 active cases")
    report=["# Словники Файти: повторна перевірка чинного набору", "",
        "Дата: 17.10.2026. ERP dev: https://dev.cukerochka.sbs/faita-mapping.", "",
        "Новий фактичний прогін після перегляду: **27 кейсів**, "+", ".join(f"**{number} {status}**" for status,number in counts.items())
            +f". Framework: **{output['frameworkPass']} PASS**; помилок setup/cleanup немає.", "",
        "## Обсяг і вилучення", "",
        "4 фічі, 15 АС; 4 DB + 15 UI + 8 API. TC-FMAP-API-009…012 вилучено за рішенням користувача як несуттєві. Видалено тестовий клас, fault-fixture helper і реєстрації в suites. У TCM ці записи DEPRECATED без API/UI automation IDs; старі результати не перетворено на PASS. Попередні runs dev 198 / prod 120 збережено з усіма 31 результатами.", "",
        "## Результат повторного аналізу", "",
        "- Погоджені назва «Амуніція», два поля пошуку з AND та режими «Неспівставлені / Співставлені» узгоджені в документації, АС, кейсах і коді тестів.",
        "- Виправлено опис legacy NULL alias: це перевірка порожнього ресурсу у списку aliases, не джерело черги витрат і не вилучена fault-регресія.",
        "- Посилено UI-008/009: колонки черги, виробник лише для амуніції, відсутність колонки ресурсу й множника.",
        "- Посилено UI-014/015: редактор містить назву й ресурс вибраного alias, для амуніції — його множник.",
        "- Посилено API-007: GET підтверджує збереження matchByName=true поряд із ресурсом, множником та автором.",
        "- Посилено DB-003/004: TEMP-міграція зберігає не лише ERP resource_id, а й FAITA ammunition_id/initiator_id=101/102.", "",
        "Створення/видалення зіставлень, частини складеної амуніції, лічильники, фільтр типу амуніції, пагінація, RBAC, повне UI-збереження та фактичне списання є окремими сценаріями поза чинними погодженими 27 кейсами. Вони не заявляються як автоматизовано перевірені. Нових продуктових дефектів не робимо висновком лише з відсутності такого покриття.", "",
        "## Актуальні артефакти", "",
        "- [Документація, АС і зв’язки з тестами](../../REQ-FAITA-MAP.md)",
        "- [27 покрокових кейсів](../../FAITA_EXPENSE_MAPPING_TEST_CASES.md)",
        f"- [Нові результати]({PREFIX}-dev-results.json)",
        f"- [Чинний TCM payload]({PREFIX}-tcm-payload.json)",
        f"- [TCM dev: синхронізація та вилучення]({PREFIX}-tcm-local-sync.json)",
        f"- [TCM prod: синхронізація та вилучення]({PREFIX}-tcm-prod-sync.json)",
        "- [Попередній повний прогін з 31 кейсом](faita-mapping-toggle-dev-report.md)",
        "- [SELECT-аудит наявних даних без фікстур](faita-mapping-existing-data-audit.json)", ""]
    for environment in ("local","prod"):
        path=OUT/(PREFIX+f"-tcm-{environment}-run.json")
        if path.exists():
            data=json.loads(path.read_text(encoding="utf-8"))
            saved=data["verifiedRun"]
            assert {i["testId"]:i["status"] for i in saved["items"]}=={r["testCaseId"]:r["status"] for r in results}
            assert saved["environment"]=="dev"
            report += [f"TCM {'dev' if environment=='local' else 'prod'}: **run {saved['id']}**, перевірено всі 27 статусів. ERP-середовище виконання — dev.", ""]
    report += ["## Статуси сценаріїв", ""]
    for result in results:
        report.append(f"- **{result['testCaseId']} — {result['status']}**: {expected[result['testCaseId']]['title']}.")
        if result["errorMessage"]: report.append("  "+result["errorMessage"].splitlines()[0])
    report += ["", "Логи: target/faita-mapping-current-review-dev.log; target/faita-mapping-current-review-framework.log. Повторно виконано тільки чинний набір; вилучені кейси не запускалися.", ""]
    (OUT/"faita-mapping-current-scope-report.md").write_text("\n".join(report),encoding="utf-8")
    print(json.dumps(dict(activeCases=27,counts=counts),ensure_ascii=False))


if __name__=="__main__": main()
