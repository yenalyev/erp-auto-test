"""Report the fresh 31-case create/delete dev suite without altering historical 31-case runs."""
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
PREFIX="faita-mapping-crud"


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--import-local",action="store_true")
    args=parser.parse_args()
    expected={c["testId"]:c for c in spec.CASES}
    folder=ROOT/"target/faita-mapping-crud-final-allure"
    latest={}
    for path in folder.glob("*-result.json"):
        raw=json.loads(path.read_text(encoding="utf-8"))
        match=re.search(r"TC-FMAP-(?:UI|API|DB)-\d{3}",raw.get("name",""))
        if match:
            assert match[0] in expected,"Unexpected/retired case executed: "+match[0]
            if match[0] not in latest or latest[match[0]]["stop"]<raw["stop"]: latest[match[0]]=raw
    assert set(latest)==set(expected) and len(expected)==31
    for path in folder.glob("*-container.json"):
        container=json.loads(path.read_text(encoding="utf-8"))
        assert not any(f.get("status") in ("failed","broken") for f in container.get("befores",[])+container.get("afters",[])),path
    framework=(ROOT/"target/faita-mapping-crud-final-framework.log").read_text(encoding="utf-8",errors="replace")
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
        for attachment in history.attachments(raw):
            if attachment.get("name") in ("Created mapping evidence","Deleted mapping evidence"):
                source=folder/attachment["source"]
                destination=evidence/(key+"-lifecycle.json")
                shutil.copyfile(source,destination)
                result["lifecycleEvidence"]=json.loads(source.read_text(encoding="utf-8"))
                result["lifecycleEvidenceFile"]=str(destination.relative_to(OUT)).replace("\\","/")
        results.append(result)
    counts=dict(Counter(r["status"] for r in results))
    for result in results:
        if result["testCaseId"] in {f"TC-FMAP-UI-{n:03}" for n in range(16,20)} and result["status"]=="PASS":
            assert result.get("lifecycleEvidence"),"Missing successful lifecycle trace: "+result["testCaseId"]
    output=dict(environment="dev",sourceAllureDirectory=str(folder.relative_to(ROOT)),counts=counts,results=results,
        retiredCaseIds=spec.RETIRED_CASE_IDS,configurationFailureCount=0,frameworkPass=int(summaries[-1][0]),
        historicalRunsPreserved=dict(dev=199,prod=121))
    (OUT/(PREFIX+"-dev-results.json")).write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding="utf-8")
    if args.import_local:
        history.import_local(results,remote_run_id="faita-mapping-dev-20261017-crud",report_prefix=PREFIX,
            run_name="FAITA mapping dev 2026-10-17 create/delete 31 active cases")
    report=["# Словники Файти: створення та видалення зіставлень", "",
        "Дата: 17.10.2026. ERP dev: https://dev.cukerochka.sbs/faita-mapping.", "",
        "Новий фактичний прогін після перегляду: **31 кейс**, "+", ".join(f"**{number} {status}**" for status,number in counts.items())
            +f". Framework: **{output['frameworkPass']} PASS**; помилок setup/cleanup немає.", "",
        "## Обсяг і вилучення", "",
        "4 фічі, 19 АС; 4 DB + 19 UI + 8 API. TC-FMAP-API-009…012 вилучено за рішенням користувача як несуттєві. Видалено тестовий клас, fault-fixture helper і реєстрації в suites. У TCM ці записи DEPRECATED без API/UI automation IDs; старі результати не перетворено на PASS. Попередні runs dev 198 / prod 120 збережено з усіма 31 результатами.", "",
        "## Нове покриття створення й видалення", "",
        "- UI-016/017: створення звичайного зіставлення для амуніції й ініціаторів через реальний UI. Без ресурсу збереження недоступне; скасування не створює alias; збереження перевірене через POST, GET, SQL, чергу та reload.",
        "- UI-018/019: власний валідний alias підготовлений штатним POST; UI-підтвердження показує правильну назву й ресурс. Скасування зберігає повний SQL snapshot; підтвердження відправляє DELETE саме alias ID. GET, SQL і reload підтверджують відсутність.",
        "- Перевірено FAITA ID, ресурс, created_by/updated_by, для амуніції multiplier=1.75 і matchByName=false; у кожному сценарії сусідній alias незмінний.",
        "- Використано наявні неспівставлені FAITA ID та лише власні UUID ERP-ресурси. Guard не допускає takeover спільного name-only alias. Після методу cleanup видаляє тільки пару source ID + власний resource ID і перевіряє її відсутність; власні ресурси деактивовано.",
        "- Події та довідники Файти, stock та materialized views не змінювалися. Перерахунок витрат і повернення до черги після наступного refresh цим набором не заявляються.", "",
        "Частини складеної амуніції за назвою, лічильники, фільтр типу амуніції, пагінація, RBAC, повне UI-редагування зі збереженням та фактичне списання залишаються поза чинним набором. Вони не заявляються як автоматизовано перевірені. Нових продуктових дефектів не робимо висновком лише з відсутності такого покриття.", "",
        "## Актуальні артефакти", "",
        "- [Документація, АС і зв’язки з тестами](../../REQ-FAITA-MAP.md)",
        "- [31 покроковий кейс](../../FAITA_EXPENSE_MAPPING_TEST_CASES.md)",
        f"- [Нові результати]({PREFIX}-dev-results.json)",
        f"- [Чинний TCM payload]({PREFIX}-tcm-payload.json)",
        f"- [TCM dev: синхронізація та вилучення]({PREFIX}-tcm-local-sync.json)",
        f"- [TCM prod: синхронізація та вилучення]({PREFIX}-tcm-prod-sync.json)",
        f"- [Фінальна документація: перевірка обох TCM і SHA256 джерела]({PREFIX}-tcm-documents-final.json)",
        "- [Попередній набір 27 кейсів](faita-mapping-current-scope-report.md)",
        "- [Попередній повний прогін з 31 кейсом](faita-mapping-toggle-dev-report.md)",
        "- [SELECT-аудит наявних даних без фікстур](faita-mapping-existing-data-audit.json)", ""]
    for environment in ("local","prod"):
        path=OUT/(PREFIX+f"-tcm-{environment}-run.json")
        if path.exists():
            data=json.loads(path.read_text(encoding="utf-8"))
            saved=data["verifiedRun"]
            assert {i["testId"]:i["status"] for i in saved["items"]}=={r["testCaseId"]:r["status"] for r in results}
            assert saved["environment"]=="dev"
            report += [f"TCM {'dev' if environment=='local' else 'prod'}: **run {saved['id']}**, перевірено всі 31 статус. ERP-середовище виконання — dev.", ""]
    report += ["## Статуси сценаріїв", ""]
    for result in results:
        report.append(f"- **{result['testCaseId']} — {result['status']}**: {expected[result['testCaseId']]['title']}."
            +(f" [JSON-доказ]({result['lifecycleEvidenceFile']})." if result.get("lifecycleEvidenceFile") else ""))
        if result["errorMessage"]: report.append("  "+result["errorMessage"].splitlines()[0])
    report += ["", "Логи: target/faita-mapping-crud-final-dev.log; target/faita-mapping-crud-final-framework.log. Повторно виконано тільки чинний набір; вилучені кейси не запускалися. Початкову спробу з помилкою локатора автокомпліту збережено в target/faita-mapping-crud-dev.log; її результати не підмішано до фінального прогону.", ""]
    (OUT/"faita-mapping-crud-scope-report.md").write_text("\n".join(report),encoding="utf-8")
    print(json.dumps(dict(activeCases=31,counts=counts),ensure_ascii=False))


if __name__=="__main__": main()
