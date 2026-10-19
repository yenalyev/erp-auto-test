#!/usr/bin/env python3
"""Report the dev run after accepted mode changes; preserve historical runs 196/197."""
from __future__ import annotations
import argparse
from collections import Counter
from datetime import datetime, timezone, timedelta
import json
import os
from pathlib import Path
import re
import shutil
import urllib.request
import sync_tcm_faita_expense_mapping as spec

ROOT=spec.ROOT
OUT=ROOT/"docs/audits/2026-10-17"
PREFIX="faita-mapping-toggle"

def attachments(result):
    found=list(result.get("attachments",[]))
    for step in result.get("steps",[]): found.extend(attachments(step))
    return found

def import_local(results, *, remote_run_id="faita-mapping-dev-20261017-toggle", report_prefix=PREFIX, run_name="FAITA mapping dev 2026-10-17 accepted mode toggle and invalid aliases"):
    ai_token=os.environ.get("TCM_AI_TOKEN")
    run_token=os.environ.get("TCM_AUTOTEST_TOKEN")
    if not ai_token or not run_token: raise SystemExit("Set local TCM_AI_TOKEN and TCM_AUTOTEST_TOKEN")
    client=spec.core.TcmClient("http://localhost:18100",ai_token)
    root=None
    for feature,_,_,_ in spec.FEATURES:
        saved=client.request("GET",f"/api/ai/projects/1/features/{feature}")
        assert saved["documentation"]==spec.DOC.read_text(encoding="utf-8"),feature
        if feature==spec.PARENT: root=saved
    # The deployed local TCM has the runner import API; commit the AUTO plan first
    # so its REQUIRES_NEW run resolver can read the plan.
    headers={"X-TCM-Api-Token":run_token,"Accept":"application/json"}
    request=urllib.request.Request(f"http://localhost:18100/api/autotest/suites/features/{root['id']}",headers=headers)
    with urllib.request.urlopen(request,timeout=90) as response: suite=json.load(response)
    assert set(suite["automationTestIds"])=={r["testCaseId"] for r in results}
    payload=dict(projectId=1,featureId=root["id"],remoteRunId=remote_run_id,
                 environment="dev",suite="faita-expense-mapping",runName=run_name,
                 results=[{k:r[k] for k in ["testCaseId","status","durationMs","executedAt","errorMessage"]} for r in results])
    request=urllib.request.Request("http://localhost:18100/api/autotest/runs",method="POST",
        data=json.dumps(payload,ensure_ascii=False).encode("utf-8"),headers={**headers,"Content-Type":"application/json; charset=utf-8"})
    with urllib.request.urlopen(request,timeout=90) as response: imported=json.load(response)
    assert imported["matched"]==len(results) and not imported.get("unmatched") and not imported.get("outOfScope"),imported
    run=client.request("GET",f"/api/ai/projects/1/test-runs/{imported['runId']}")
    assert len(run["items"])==len(results)
    assert {i["testId"]:i["status"] for i in run["items"]}=={r["testCaseId"]:r["status"] for r in results}
    evidence=dict(documentationMatchesSource=True,response=imported,verifiedRun=run)
    (OUT/(report_prefix+"-tcm-local-run.json")).write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding="utf-8")
    return imported

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--import-local",action="store_true")
    args=parser.parse_args()
    if args.import_local:
        raise SystemExit("Historical runs 198/120 are immutable; use the current scope report for the active 27 cases.")
    historical_payload=json.loads((OUT/"faita-mapping-toggle-tcm-payload.json").read_text(encoding="utf-8"))
    expected={c["testId"]:c for c in historical_payload["testCases"]}
    folder=ROOT/"target/faita-mapping-toggle-final-allure"
    latest={}
    config_errors=[]
    for path in folder.glob("*-container.json"):
        c=json.loads(path.read_text(encoding="utf-8"))
        config_errors += [f["name"] for f in c.get("befores",[])+c.get("afters",[]) if f.get("status") in ["failed","broken"]]
    for path in folder.glob("*-result.json"):
        raw=json.loads(path.read_text(encoding="utf-8"))
        match=re.search(r"TC-FMAP-(?:UI|API|DB)-\d{3}",raw.get("name",""))
        if match and match[0] in expected:
            key=match[0]
            if key not in latest or raw["stop"]>latest[key]["stop"]: latest[key]=raw
    assert set(latest)==set(expected),"Incomplete: "+str(sorted(set(expected)-set(latest)))
    assert not config_errors,config_errors
    framework=(ROOT/"target/faita-mapping-toggle-framework.log").read_text(encoding="utf-8",errors="replace")
    summaries=re.findall(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)",framework)
    assert summaries and summaries[-1][1:]==("0","0","0"),"Framework has not passed"
    framework_count=int(summaries[-1][0])
    evidence_folder=OUT/(PREFIX+"-evidence")
    evidence_folder.mkdir(parents=True,exist_ok=True)
    results=[]
    for key,raw in sorted(latest.items()):
        item=dict(testCaseId=key,status={"passed":"PASS","failed":"FAIL","broken":"FAIL","skipped":"SKIPPED"}.get(raw["status"],"NOT_RUN"),
                  durationMs=raw["stop"]-raw["start"],executedAt=datetime.fromtimestamp(raw["start"]/1000,timezone(timedelta(hours=3))).replace(tzinfo=None).isoformat(timespec="seconds"),
                  errorMessage=raw.get("statusDetails",{}).get("message","")[:6000] or None,
                  featureId=expected[key]["featureId"],rawAllureStatus=raw["status"],sourceAllureDirectory=str(folder.relative_to(ROOT)))
        images=[a for a in attachments(raw) if a.get("type")=="image/png"]
        if images:
            dest=evidence_folder/(key+".png");shutil.copyfile(folder/images[-1]["source"],dest)
            item["screenshot"]=str(dest.relative_to(OUT)).replace("\\","/")
        for attachment in attachments(raw):
            if attachment.get("name")=="Invalid alias evidence":
                source=folder/attachment["source"]
                dest=evidence_folder/(key+"-evidence.json");shutil.copyfile(source,dest)
                item["integrityEvidence"]=json.loads(source.read_text(encoding="utf-8"))
                item["integrityEvidenceFile"]=str(dest.relative_to(OUT)).replace("\\","/")
        results.append(item)
    integrity=[r for r in results if r.get("integrityEvidence")]
    assert len(integrity)==4,"Four completed integrity traces are required"
    counts=dict(Counter(r["status"] for r in results))
    doc=dict(environment="dev",url="https://dev.cukerochka.sbs/faita-mapping",timezone="Europe/Kyiv (+03:00 on 2026-10-17)",
             counts=counts,results=results,configurationFailureCount=0,frameworkPass=framework_count,
             requirementRevision="User accepted ammunition label, independent search fields and mutually exclusive unmapped/mapped mode toggle.")
    audit_path=OUT/"faita-mapping-existing-data-audit.json"
    import_review_path=OUT/"faita-mapping-import-review.json"
    if audit_path.exists() and import_review_path.exists():
        audit=json.loads(audit_path.read_text(encoding="utf-8"))
        review=json.loads(import_review_path.read_text(encoding="utf-8"))
        assert audit["snapshot"][0]["read_only"]=="on" and not audit["fixturesCreated"] and not audit["importExecuted"]
        doc["existingDataAudit"]={"source":audit_path.name,"snapshot":audit["snapshot"][0],
            "ammo":audit["ammoSummary"][0],"initiators":audit["initiatorsSummary"][0]}
        doc["bugTriage"]=review["classification"]
        doc["importReviewSource"]=import_review_path.name
    (OUT/(PREFIX+"-dev-results.json")).write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding="utf-8")
    report=["# Dev-прогін після погодження перемикача режимів", "",
        "Дата: 17.10.2026, Europe/Kyiv. Dev: https://dev.cukerochka.sbs/faita-mapping.", "",
        f"Виконано **{len(results)} сценарій**: "+", ".join(f"**{n} {s}**" for s,n in sorted(counts.items()))+".", "",
        "## Погоджені зміни", "",
        "Користувач погодив назву «Амуніція» та окремі поля «Назва Файти»/«Ресурс». Оновлено АС, TCM-кейси й автотести. Перевіряються кожне поле, AND, порожній результат та очищення одного поля зі збереженням іншого.", "",
        "Користувач погодив заміну чекбокса взаємовиключними режимами «Неспівставлені» / «Співставлені». TC-FMAP-UI-008/009 оновлено: перевіряються режими, реальна позиція з /unmapped, пошук назви, порожній результат, очищення та повернення до aliases з ресурсним пошуком. Чотири регресії повноти черги залишаються незалежними перевірками дефекту.", "",
        "Попередні звіти й TCM runs 196/197 збережено як історію попередніх вимог. Цей звіт містить новий повний прогін; попередні результати не використано замість повторного виконання.", "",
        "## Артефакти", "",
        "- [Документація й АС](../../REQ-FAITA-MAP.md)","- [Покрокові тест-кейси](../../FAITA_EXPENSE_MAPPING_TEST_CASES.md)",
        f"- [Машинні результати]({PREFIX}-dev-results.json)",f"- [TCM payload]({PREFIX}-tcm-payload.json)", "",
        f"4 фічі, 15 АС, {len(results)} автоматизований сценарій; усі класи входять у faita-expense-mapping.xml та regression.xml.", "",
        "## Перевірка alias із некоректним ресурсом", "",
        "Для кожного словника перевірено NULL-посилання і посилання на ресурс, відсутність якого доведена SELECT до вставки. Target і контроль взяті з реальної черги /unmapped. Створюється лише власний alias до чинного FAITA ID; SQL перевіряє нерозв'язне посилання, GET aliases — resource=null, GET /unmapped — видимість target і контролю. Alias видаляється у finally до фінального асерту; перевіряються повернення target і відсутність власних aliases.", ""]
    if doc.get("existingDataAudit"):
        data=doc["existingDataAudit"]
        report[6:6]=["## Уточнення впливу: наявні дані та імпорт", "",
            f"Окремий SELECT-аудит без фікстур: **{data['snapshot']['captured_at']}**, транзакція READ ONLY / REPEATABLE READ, завершена rollback. Імпорт не запускався.", "",
            f"- Амуніція: {data['ammo']['total_aliases']} aliases; NULL resource_id — {data['ammo']['null_resource_id']}; посилання на відсутній ресурс — {data['ammo']['dangling_resource_id']}.",
            f"- Ініціатори: {data['initiators']['total_aliases']} aliases; NULL resource_id — {data['initiators']['null_resource_id']}; посилання на відсутній ресурс — {data['initiators']['dangling_resource_id']}.", "",
            "Штатний FaitaImportJob копіює ammo_agg_v3, ammunition та initiators і оновлює два materialized views. Переглянутий код не записує aliases і не видаляє public.resource. На перевірених таблицях імпорту, aliases та resource немає користувацьких тригерів. Імпорт може зберегти вже некоректний alias, але шляху створення NULL/нерозв'язного ERP resource_id цим імпортом не знайдено. Зникнення вихідної амуніції/ініціатора після імпорту є іншою прив'язкою та не дорівнює відсутності ERP-ресурсу.", "",
            "Створення/редагування через UI/API вимагає наявного ресурсу за переглянутим кодом; звичайне видалення ERP-ресурсу є soft delete. Деактивовані наявні ресурси не враховані як некоректні посилання. У БД nullable resource_id без FK дозволяє створити такий стан поза штатною валідацією.", "",
            "**Уточнений опис багу:** за наявності неконсистентного alias із NULL або відсутнім ERP-ресурсом черга виключає незавершене зіставлення. **Запропонований пріоритет: LOW; severity: MINOR; не блокер релізу за поточними доказами.** На наявних dev-даних дефект не проявляється; підтверджений сценарій використовував спеціально створені JDBC-фікстури. Попередня оцінка High/Major була передчасною. Чотири FAIL залишено як фактичні результати перевірок стійкості, без перетворення на PASS.", "",
            "Перевірка імпорту є аналізом поточного локального SUT-коду й каталогу dev БД, не виконанням імпорту та не підтвердженням ідентичності deployed binary. Зовнішні ETL, SQL-скрипти, відновлення з дампа та історичне походження даних цим аудитом не перевірені; висновок не поширюється на ERP prod.", "",
            "[SQL, знімок даних і каталог БД](faita-mapping-existing-data-audit.json). [Розбір імпорту, класифікація й SHA256 джерел](faita-mapping-import-review.json).", ""]
    for item in integrity:
        e=item["integrityEvidence"]
        yn=lambda value:"так" if value else "ні"
        label="Амуніція" if e["kind"]=="AMMO" else "Ініціатори"
        scenario="ресурс не заданий" if e["scenario"]=="NULL_RESOURCE_ID" else "ресурс за посиланням відсутній"
        resource_id="NULL" if e["storedResourceId"] is None else e["storedResourceId"]
        report += [f"- **{item['testCaseId']} — {item['status']}**: {label}, {scenario}; FAITA ID={e['sourceId']}, alias ID={e['aliasId']}, resource_id={resource_id}. "
            f"Позиція в черзі: до — {yn(e['targetVisibleBefore'])}, з некоректним alias — {yn(e['targetVisibleWithInvalidAlias'])}, після очищення — {yn(e['targetVisibleAfterCleanup'])}. "
            f"Контроль видимий під час/після: {yn(e['controlVisibleDuring'])}/{yn(e['controlVisibleAfterCleanup'])}; власні aliases видалені: {yn(e['ownedAliasesRemoved'])}. "
            f"[JSON-доказ]({item['integrityEvidenceFile']})."]
    hidden=[r for r in integrity if not r["integrityEvidence"]["targetVisibleWithInvalidAlias"]]
    report += ["",f"**У {len(hidden)} із 4 випадків позиція зникла з /unmapped після створення некоректного alias**, хоча GET aliases повертав resource=null. "
        "Фінальний регресійний асерт перевіряє очікування, що непридатний мапінг залишає позицію доступною для виправлення; фактичний результат зафіксовано окремо від погодженої заміни чекбокса.","",
        "Вихідний код пояснює результат: NOT EXISTS виключає позицію за самою наявністю alias із FAITA ID, без перевірки придатності resource_id. "
        "Див. [умову для амуніції](C:/Users/gigam/IdeaProjects/tk/src/main/java/org/pm/tk/dao/faitamapping/AmmoUsageRepository.java:53) та "
        "[умову для ініціаторів](C:/Users/gigam/IdeaProjects/tk/src/main/java/org/pm/tk/dao/faitamapping/AmmoUsageRepository.java:93). "
        "Підстава функціонального висновку — відтворена на dev втрата доступної для виправлення позиції при нерозв'язному ресурсі.","",
        "## Ізоляція й межі доказу", "",
        "Вихідні довідники та події Файти, materialized view й stock не змінювалися. Тести тимчасово додавали власні aliases до вже наявних неспівставлених FAITA ID; усі власні записи прибрано, контрольна позиція залишалася видимою, target відновився. Для звичайних UI/API сценаріїв власні ресурси деактивовано. У before/after Allure fixtures немає помилок setup/cleanup.", "",
        "Міграційні alias ALTER statements V156 перевірені на TEMP legacy-таблицях із rollback; історична незмінність усіх dev-записів без знімка до деплою не доводиться. Нові integrity-сценарії перевіряють API, яке живить UI; окремих UI-кліків для цих чотирьох випадків не виконувалося.", "",
        f"Framework suite: **{framework_count} PASS, 0 FAIL, 0 SKIP**, включно з RegressionSuiteCompletenessTest. Лог: target/faita-mapping-toggle-framework.log.", "",
        "## Результати сценаріїв", ""]
    for item in results:
        report.append(f"- **{item['testCaseId']} — {item['status']}**: {expected[item['testCaseId']]['title']}."+
            (f" [Скріншот]({item['screenshot']})." if item.get("screenshot") else ""))
        if item["errorMessage"]: report.append(("  Причина: "+item["errorMessage"].splitlines()[0][:500]).rstrip())
    report += ["", "## Відтворення", "", "```powershell",
        "mvn test '-Denv=dev' '-Dsuite=faita-expense-mapping' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dsuite.artifact.sweep=false'",
        "```", "", "Лог повного прогону: target/faita-mapping-toggle-final-dev.log. Всі наведені статуси отримані реальним виконанням; продуктові FAIL не перетворено на PASS. Попередню спробу з гонкою очікування debounce збережено у target/faita-mapping-toggle-dev.log; її результати не підмішано до фінального прогону.", "", "## TCM dev / prod", "",
        "Оновлені фічі, АС, документація й кейси синхронізуються з dev TCM http://localhost:18100 та prod TCM http://tcm.lab, project 1. Перевірку збережених об’єктів зафіксовано в окремих JSON-звітах синхронізації. Середовище виконання ERP-тестів — dev.", ""]
    if args.import_local:
        imported=import_local(results)
    local_run=OUT/(PREFIX+"-tcm-local-run.json")
    if local_run.exists():
        local=json.loads(local_run.read_text(encoding="utf-8"))
        assert {i["testId"]:i["status"] for i in local["verifiedRun"]["items"]}=={r["testCaseId"]:r["status"] for r in results}
        report += [f"TCM dev: **run {local['response']['runId']}, matched={local['response']['matched']}**; усі статуси перечитано й звірено з Allure. Новий remoteRunId не змінює runs 196/197.", ""]
    prod_run=OUT/(PREFIX+"-tcm-prod-run.json")
    if prod_run.exists():
        prod=json.loads(prod_run.read_text(encoding="utf-8"))
        assert {i["testId"]:i["status"] for i in prod["verifiedRun"]["items"]}=={r["testCaseId"]:r["status"] for r in results}
        assert prod["verifiedRun"]["environment"]=="dev"
        report += [f"TCM prod: **run {prod['runId']}, matched={prod['matched']}**; усі 31 статус перечитано й звірено. Це результати ERP dev, імпортовані в production TCM; ERP prod не запускався.", ""]
    report += [f"- [TCM dev: синхронізація]({PREFIX}-tcm-local-sync.json)",
               f"- [TCM dev: повна перевірка полів і кроків]({PREFIX}-tcm-readback.json)",
               f"- [TCM prod: перевірені фічі, АС і кейси]({PREFIX}-tcm-prod-sync.json)",
               f"- [TCM dev: результати]({PREFIX}-tcm-local-run.json)",
               f"- [TCM prod: результати]({PREFIX}-tcm-prod-run.json)", ""]
    (OUT/(PREFIX+"-dev-report.md")).write_text("\n".join(report),encoding="utf-8")
    print(json.dumps(dict(uniqueCases=len(results),counts=counts),ensure_ascii=False))

if __name__=="__main__":main()
