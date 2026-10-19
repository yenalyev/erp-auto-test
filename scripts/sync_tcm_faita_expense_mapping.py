#!/usr/bin/env python3
"""Build reviewable FAITA mapping cases and idempotently sync them to local TCM.

Production writes use tcm-ai-prod MCP, never this script or a local token.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs/REQ-FAITA-MAP.md"
PARENT = "REQ-FAITA-MAP"
FEATURES = [
    (PARENT, None, "Словник мапінгу Файта — Цукерка для витрат", [
        ("AC-01", "Сайдбар містить «Словники Файти»; перехід відкриває вкладки «Амуніція» та «Ініціатори». Назву «Амуніція» погоджено користувачем 17.10.2026.")]),
    (PARENT + "-AUDIT", PARENT, "Автор змін і міграція словників Файти", [
        ("AC-01", "resource_alias_with_ammo містить текстове updated_by; старі записи отримують system без втрати id, name, resource_id, multiplier і match_by_name."),
        ("AC-02", "resource_alias_with_initiators містить текстове updated_by; старі записи отримують system без втрати id, name і resource_id.")]),
]
for suffix, label in [("AMMO", "Амуніція"), ("INIT", "Ініціатори")]:
    FEATURES.append((PARENT + "-" + suffix, PARENT, "Словники Файти — " + label, [
        ("AC-01", f"Вкладка «{label}» показує name як «Назва Файти» та назву ресурсу за resource_id; "
         + ("БЧ додатково показує multiplier, включно з дробовими збереженими значеннями." if suffix == "AMMO" else "колонки множника немає.")),
        ("AC-02", "Погоджено два незалежні поля «Назва Файти» і «Ресурс»: кожне шукає у своєму полі, разом діють як AND. Відсутній збіг дає порожній набір; очищення одного поля зберігає інший фільтр; очищення обох відновлює список. Погоджено користувачем 17.10.2026."),
        ("AC-03", "Погоджено взаємовиключні режими «Неспівставлені» / «Співставлені». Перший показує чергу витрат Файти: назва, остання витрата, дія «Співставити», для амуніції також виробник. Пошук назви звужує чергу; відсутній збіг дає нуль, очищення відновлює список. Ресурсний пошук, колонки aliases та їх сортування доступні у «Співставлені»; повернення відновлює aliases згідно з фільтрами. Погоджено 17.10.2026."),
        ("AC-04", "Типово обрано «Назва Файти»; ASC/DESC застосовує name, потім resource_name в тому самому напрямі."),
        ("AC-05", "Сортування «Ресурс» ASC/DESC застосовує resource_name, потім name в тому самому напрямі."),
        ("AC-06", "Кнопка з PencilIcon відкриває редагування відповідного запису. Закриття без збереження не змінює даних. "
         "Додаткова перевірка чинного PUT-контракту: зміни зберігаються з автором без зміни сусіднього запису.")]))

for feature in FEATURES:
    if feature[0].endswith(("-AMMO", "-INIT")):
        feature[3].extend([
            ("AC-07", "Створення: у Неспівставлені дія Співставити відкриває форму вибраного FAITA ID. Без ресурсу збереження недоступне; Скасувати не створює alias. З вибраним ресурсом і для амуніції множником 1.75 збереження створює рівно один alias з правильними FAITA ID/resource_id та created_by/updated_by поточного користувача. Для амуніції matchByName=false. Запис видимий у Співставлені після reload, ціль зникає з черги; сусідні aliases не змінюються."),
            ("AC-08", "Видалення: дія в рядку Співставлені відкриває підтвердження з назвою й ресурсом вибраного alias. Скасувати залишає запис незмінним. Підтвердження видаляє лише вибраний alias: GET і SQL не знаходять його, після reload рядка немає, сусідній alias незмінний. Перерахунок витрат/оновлення materialized views не є частиною цього сценарію.")])

RETIRED_CASE_IDS = [f"TC-FMAP-API-{number:03}" for number in range(9, 13)]
RETIREMENT_REASON = "Вилучено користувачем 17.10.2026 як несуттєві перевірки штучно неконсистентних даних. Історію виконання збережено."

CASES: list[dict] = []
def case(test_id, feature, ac, title, action, expected, method, *, data=None, automation_class=None):
    ui = "-UI-" in test_id
    cls = automation_class or ("FaitaExpenseMappingUiTest" if ui else "FaitaExpenseMappingDbTest" if "-DB-" in test_id else "FaitaExpenseMappingApiTest")
    CASES.append(dict(testId=test_id, featureId=feature, acKey=ac, title=title,
        description=f"Автоматизація: {cls}#{method}. Відмінності реалізації від вимог не вважаються PASS.",
        preconditions="Dev ERP, ADMIN із faita-mapping permissions, доступний PostgreSQL. Для UI — реальна OAuth-сесія. "
            "Mutable fixtures створюють лише власні aliases/ресурси; після сценарію виконати cleanup.",
        testData=data or "Дві власні ERP-назви A/B; 5 aliases: однакові назви з різними ресурсами, однаковий ресурс із різними назвами, один resource_id=NULL; множники 2/1.5/1/3/0.25.",
        steps=[dict(stepOrder=1, actionText=action, expectedText=expected),
               dict(stepOrder=2, actionText="Перевірити фактичні значення/порядок та виконати cleanup власних даних.", expectedText="Очікуваний набір без сторонніх рядків; створені aliases видалені, власні ресурси деактивовані; для TEMP — rollback.")],
        expectedResult=expected, priority="HIGH", severity="MAJOR", status="ACTIVE",
        testType="UI" if ui else "FUNCTIONAL", tags="faita,mapping,expenses,automated,regression",
        apiAutomationIds=[] if ui else [test_id], uiAutomationIds=[test_id] if ui else []))

for number, suffix, kind, method in [
    (1,"AMMO","БЧ","ammoHasUpdatedBy"),(2,"INIT","Ініціатори","initiatorsHaveUpdatedBy")]:
    case(f"TC-FMAP-DB-{number:03}", PARENT+"-AUDIT", f"AC-{number:02}", f"{kind}: поле updated_by на dev",
         "Прочитати information_schema.columns і підрахувати порожні/системні updated_by у відповідній таблиці.",
         "updated_by є текстовим полем; немає NULL/порожніх авторів; статистика додана в Allure.", method,
         data="Існуючі dev aliases; запити тільки SELECT.")
for number, ac, kind, method in [(3,1,"БЧ","ammoLegacyRowsBackfilledWithoutDataLoss"),(4,2,"Ініціатори","initiatorLegacyRowsBackfilledWithoutDataLoss")]:
    case(f"TC-FMAP-DB-{number:03}", PARENT+"-AUDIT", f"AC-{ac:02}", f"{kind}: V156 заповнює legacy автора system",
         "Створити TEMP копії legacy таблиць із mapped/unmapped записами; виконати реальні alias ALTER statements із V156, замінивши тільки схему на pg_temp; прочитати рядки; rollback.",
         "Обидва старі записи отримали system; ID, name, resource_id, ammunition_id/initiator_id та множник/ознака для БЧ збережені; немає втрати/дублів рядків. Реальні faita таблиці не змінювалися.", method,
         data="TEMP id=1 resource_id=41 FAITA ID=101; id=2 resource_id=NULL FAITA ID=102; для БЧ multiplier=1.5 і 3, match_by_name=true/false.")
case("TC-FMAP-UI-001", PARENT,"AC-01","Навігація та назви вкладок",
     "У сайдбарі перейти через Словники Файти; перевірити назви обох вкладок.",
     "Є погоджені вкладки Амуніція та Ініціатори; показаний довідник /faita-mapping.","sidebarAndBothTabs",data="ADMIN; fixture даних не потрібний.")
ui_pairs = [
    (2,"AC-01","Колонки і джерело даних","Відкрити вкладку, прочитати всі власні рядки й заголовки.","name і ресурс відповідають fixture; NULL прив'язка видима; для БЧ числові множники збережені, для Ініціаторів множника немає.","ColumnsAndData"),
    (4,"AC-02","Окреме поле Назва Файти та незалежне очищення","У полі Назва Файти знайти назву Б, відсутній збіг і marker. Додати фільтр Ресурс=ERP prefix; очистити назву, потім відновити marker й очистити ресурс.","Один рядок, нуль, усі п'ять; після додавання ресурсу чотири; очищення назви зберігає ресурсний фільтр; очищення ресурсу повертає п'ять.","SearchByAlias"),
    (6,"AC-02","Окреме поле Ресурс, AND та скидання","Знайти ресурс A, потім ERP prefix. У Назва Файти ввести Б, а в Ресурс — A; перевірити порожній AND. Перевірити відсутній ресурс і очистити його.","Два aliases A, потім чотири mapped; суперечливі фільтри і відсутній ресурс дають нуль; очищення ресурсу з marker у назві повертає п'ять.","SearchByResource"),
    (8,"AC-03","Перемикач режимів, пошук і повернення до aliases","У Співставлені перевірити 5 власних aliases. Очистити назву, перемкнути Неспівставлені; отримати реальну назву з GET /unmapped. Знайти її, перевірити відсутній збіг і очищення пошуку. Повернутися до Співставлені та знайти власний marker.","Режими взаємовиключні. У черзі є реальна позиція, Назва Файти, Остання витрата та Співставити; Виробник є лише для амуніції, ресурсної колонки й множника немає; пошук ресурсу відсутній. Пошук назви знаходить позицію; відсутній збіг дає нуль; очищення повертає позицію. Після повернення є ресурсний пошук, колонки aliases та всі 5 власних рядків.","UnmatchedAndSearch"),
    (10,"AC-04","Назва Файти: ASC/DESC і другий ключ","На чотирьох mapped aliases перемкнути Назва Файти DESC, потім ASC.","Обидва напрями відповідають name і resource_name; однакові назви розташовані за ресурсом у тому самому напрямі.","SortByAliasWithResourceTieBreaker"),
    (12,"AC-05","Ресурс: ASC/DESC і другий ключ","На чотирьох mapped aliases перемкнути Ресурс ASC, потім DESC.","Обидва напрями відповідають resource_name і name; aliases одного ресурсу впорядковані за name у тому самому напрямі.","SortByResourceWithAliasTieBreaker"),
    (14,"AC-06","Олівець відкриває редактор","Натиснути PencilIcon першого рядка; відкрити редактор; закрити Escape без збереження.","У редакторі назва й ресурс саме вибраного рядка; для амуніції множник початковий, для ініціаторів поля множника немає. Після Escape значення й порядок рядків залишилися початковими.","EditPencilOpensForm")]
for first,ac,title,action,expected,method in ui_pairs:
    for offset,suffix,label,prefix in [(0,"AMMO","Амуніція","ammo"),(1,"INIT","Ініціатори","initiator")]:
        case(f"TC-FMAP-UI-{first+offset:03}",PARENT+"-"+suffix,ac,f"{label}: {title}",action,expected,prefix+method)
for offset,suffix,label,prefix in [(0,"AMMO","БЧ","ammo"),(1,"INIT","Ініціатори","initiator")]:
    for first,ac,title,action,expected,method in [
        (1,"AC-01","API: значення та типове сортування","GET aliases без sort із faitaName=marker.","Рівно 5 власних рядків у name/resource ASC; resource.id/name і multiplier для БЧ відповідають fixture.","ValuesAndDefaultOrder"),
        (3,"AC-02","API: окремі пошуки та AND","GET за faitaName; окремо за resourceName; відсутній збіг; суперечливий AND; скинути фільтри.","Повний відповідний набір, порожній результат для суперечності, після скидання всі 5; погоджений контракт двох незалежних параметрів.","FiltersAndReset"),
        (5,"AC-05","API: обидва ключі й напрями","GET sort=name,ASC/DESC і resourceName,ASC/DESC на mapped aliases.","Точні ID у правильному порядку двох ключів; жодного пропущеного/зайвого рядка.","BothSortKeysAndDirections"),
        (7,"AC-06","API: зміна мапінгу, автор та ізоляція","Відтворити PUT з FaitaAliasDialog: resourceId, для БЧ також multiplier=7.5 і matchByName=true для name-only alias; GET; SQL updated_by; звірити сусідній рядок.","Зміна збережена; для амуніції multiplier=7.5 і matchByName=true підтверджені GET; updated_by поточний ADMIN; назва збережена; сусідній alias ідентичний snapshot; власний target відновлено.","UpdatePersistsAuthorAndPreservesSibling")]:
        case(f"TC-FMAP-API-{first+offset:03}",PARENT+"-"+suffix,ac,f"{label}: {title}",action,expected,prefix+method)

for offset,suffix,label,prefix in [(0,"AMMO","Амуніція","ammo"),(1,"INIT","Ініціатори","initiator")]:
    case(f"TC-FMAP-UI-{16+offset:03}",PARENT+"-"+suffix,"AC-07",f"{label}: створення зіставлення, скасування й збереження",
         "Вибрати наявну неспівставлену позицію з FAITA ID без alias і без ризику takeover однойменного name-only alias. Через Співставити перевірити вимкнене Зберегти без ресурсу, вибрати власний ERP-ресурс, для амуніції multiplier=1.75; скасувати й перевірити SQL count=0. Повторити зі збереженням через UI. Перевірити POST, GET, SQL авторів і посилань, чергу та список Співставлені після reload; звірити snapshot сусіда.",
         "Скасування не створило запису. POST створив рівно один alias для вибраного FAITA ID з власним ресурсом; для амуніції multiplier=1.75 і matchByName=false. created_by/updated_by поточний ADMIN. Ціль зникає з Неспівставлені й показана у Співставлені після reload; сусід не змінений. Власне зіставлення та ресурси очищені після тесту.",
         prefix+"CreateMapping",data="Реальна наявна plain-позиція Файти, лише читання джерела; власний UUID ERP-ресурс і aliases. Немає змін подій, вихідних довідників, stock або refresh views. Cleanup прив’язаний до FAITA ID + власного resource_id.")
    case(f"TC-FMAP-UI-{18+offset:03}",PARENT+"-"+suffix,"AC-08",f"{label}: видалення зіставлення, скасування й підтвердження",
         "Через штатний POST створити власне валідне зіставлення до наявного вільного FAITA ID. Знайти його у Співставлені, натиснути видалення й перевірити назву/ресурс у підтвердженні. Скасувати: SQL snapshot і GET збережені. Повторити з підтвердженням; перевірити DELETE саме alias ID, GET/SQL відсутність, reload і незмінність сусіднього запису.",
         "Скасування зберігає запис повністю. Підтверджений DELETE видаляє лише вибране власне зіставлення; воно відсутнє в API, SQL і після reload. Сусідній alias незмінний; власні дані очищені. Негайне повернення в чергу після refresh витрат цим кейсом не заявляється.",
         prefix+"DeleteMapping",data="Власне валідне зіставлення, створене через API; реальний незайнятий FAITA ID і власний UUID ERP-ресурс. Ресурс/FAITA позиція не видаляються; видаляється лише alias. Stock, події й materialized views не змінюються.")

def render_cases():
    out=["# Тест-кейси: словник мапінгу Файта — Цукерка для витрат", "", f"{len(CASES)} автоматизований сценарій; 4 фічі; {sum(len(f[3]) for f in FEATURES)} АС. Назву Амуніція, окремі поля пошуку та перемикач Неспівставлені/Співставлені погоджено 17.10.2026. Джерело: scripts/sync_tcm_faita_expense_mapping.py.", ""]
    for c in sorted(CASES,key=lambda c:c["testId"]):
        out += [f"## {c['testId']} — {c['title']}", "", f"Фіча/АС: `{c['featureId']}/{c['acKey']}`. Статус: ACTIVE. Пріоритет: HIGH.", "", c["description"], "", "Передумови: "+c["preconditions"], "", "Дані: "+c["testData"], ""]
        for s in c["steps"]: out += [f"{s['stepOrder']}. {s['actionText']} Очікування: {s['expectedText']}"]
        out += ["", "Очікуваний результат: "+c["expectedResult"], ""]
    (ROOT/"docs/FAITA_EXPENSE_MAPPING_TEST_CASES.md").write_text("\n".join(out),encoding="utf-8")

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sync-local",action="store_true")
    parser.add_argument("--refresh-docs-only",action="store_true")
    parser.add_argument("--report",type=Path,default=ROOT/"docs/audits/2026-10-17/faita-mapping-crud-tcm-local-sync.json")
    args=parser.parse_args()
    render_cases()
    payload=dict(features=[dict(featureId=f,parentFeatureId=p,title=t,acceptanceCriteria=[dict(acKey=k,text=v) for k,v in ac]) for f,p,t,ac in FEATURES],testCases=CASES)
    export=ROOT/"docs/audits/2026-10-17/faita-mapping-crud-tcm-payload.json"
    export.parent.mkdir(parents=True,exist_ok=True)
    export.write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding="utf-8")
    if not args.sync_local and not args.refresh_docs_only:
        print(json.dumps(dict(features=len(FEATURES),acceptanceCriteria=sum(len(f[3]) for f in FEATURES),cases=len(CASES))))
        return
    token=os.getenv("TCM_AI_TOKEN")
    if not token:
        for line in (ROOT/".env").read_text(encoding="utf-8").splitlines():
            if line.startswith("TCM_AI_TOKEN="): token=line.split("=",1)[1].strip().strip("\"'")
    if not token: raise SystemExit("Local TCM token is not configured")
    client=core.TcmClient("http://localhost:18100",token)
    if args.refresh_docs_only:
        for feature,_,_,_ in FEATURES:
            path=f"/api/ai/projects/1/features/{feature}"
            client.request("GET",path)
            client.request("PUT",path,{"documentation":DOC.read_text(encoding="utf-8")})
            assert client.request("GET",path)["documentation"]==DOC.read_text(encoding="utf-8")
        print("Verified documentation refresh for all 4 local features",flush=True)
        return
    reports=[]
    for feature,parent,title,acs in FEATURES:
        print("Syncing "+feature,flush=True)
        core.PROJECT_ID=1;core.FEATURE_ID=feature;core.FEATURE_PARENT_ID=parent
        core.FEATURE_TITLE=title;core.FEATURE_DESCRIPTION=title;core.FEATURE_MODULE="FAITA";core.FEATURE_PRIORITY="HIGH"
        core.DOCUMENTATION_PATH=DOC;core.ACCEPTANCE_CRITERIA=acs
        core.CASES=[c for c in CASES if c["featureId"]==feature]
        report=core.sync(client)
        assert report["verification"]["documentationMatchesSource"] and report["verification"]["allCasesPresent"]
        for source in core.CASES:
            saved=client.request("GET",f"/api/ai/projects/1/test-cases/{source['testId']}")
            for key in ["title","expectedResult","preconditions","testData","apiAutomationIds","uiAutomationIds"]:
                assert saved[key]==source[key],(source["testId"],key)
            assert saved["featureId"]==feature and saved["acId"]==source["acKey"]
            assert saved["steps"] and len(saved["steps"])==len(source["steps"])
            for actual_step,expected_step in zip(saved["steps"],source["steps"]):
                for key in ["stepOrder","actionText","expectedText"]:
                    assert actual_step[key]==expected_step[key],(source["testId"],key)
        reports.append(report)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding="utf-8")
    print(f"Verified local TCM: 4 features, {sum(len(f[3]) for f in FEATURES)} AC, {len(CASES)} automated cases")

if __name__=="__main__": main()
