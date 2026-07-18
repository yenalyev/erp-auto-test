#!/usr/bin/env python3
"""
Generate TCM import XLSX for Resource Viewer (wolf) — BOM decomposer + filters + UI.

Covers:
  - existing automated TC-RVW-BOM-*, TC-RVW-ALT-*, TC-RVW-001, TC-RVW-API-001..003, TC-UI-RES-AC-001
  - new automated TC-RVW-BOM-030..034, TC-RVW-API-010..017, TC-RVW-API-020, TC-UI-RVW-001/002
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from tcm_import_common import Case, Step, write_xlsx_with_features

OUTPUT = Path(__file__).resolve().parent.parent / "docs" / "tcm-import-resource-viewer.xlsx"

FEAT_RVW = "REQ-RVW"
FEAT_RVW_BOM = "REQ-RVW-BOM"
FEAT_RVW_FLT = "REQ-RVW-FILTER"
FEAT_RVW_UI = "REQ-RVW-UI"

ROLE_RVW = "ResourceViewer"
PRE_WOLF = "@ResourceViewer (wolf) залогінений. Середовище dev/staging."
PRE_ADMIN_BOM = (
    f"{PRE_WOLF} @Admin підготував техкарти PRODUCTION + stock на OWNER_1 storage; "
    "receiver = UNIT storage."
)


def mk(
    test_id: str,
    feature_id: str,
    ac_id: str,
    title: str,
    goal: str,
    *,
    priority: str = "HIGH",
    severity: str = "MAJOR",
    preconditions: str = PRE_WOLF,
    expected_result: str = "",
    tags: str = "resource-viewer,api",
    role_name: str = ROLE_RVW,
    layer: str | None = "API",
    automation_test_id: str | None = None,
    steps: list[tuple[str, str]],
) -> Case:
    return Case(
        test_id=test_id,
        feature_id=feature_id,
        ac_id=ac_id,
        title=title,
        description=f"Мета: {goal}",
        priority=priority,
        severity=severity,
        preconditions=preconditions,
        expected_result=expected_result or (steps[-1][1] if steps else ""),
        tags=tags,
        role_name=role_name,
        automation_layer=layer,
        automation_test_id=automation_test_id or test_id,
        cross_features=["resource-viewer"],
        steps=[Step(i + 1, a, e) for i, (a, e) in enumerate(steps)],
    )


def features() -> list[tuple]:
    return [
        (FEAT_RVW, "", "Відстеження ресурсів (Resource Viewer)",
         "Роль Resource Viewer (wolf): журнал переміщень з BOM-декомпозицією, sum, export, RBAC.",
         "RES", "CRITICAL", "1", "0"),
        (FEAT_RVW_BOM, FEAT_RVW, "BOM / декомпозер",
         "Розкладання виданої продукції на інгредієнти: origin, depth, aggregation, alt-groups, edge cases.",
         "RES", "CRITICAL", "2", "0"),
        (FEAT_RVW_FLT, FEAT_RVW, "Фільтри journal/sum/export",
         "categoryIds, supplier, період, guard порожньої відповіді, пагінація, Excel export.",
         "RES", "HIGH", "2", "1"),
        (FEAT_RVW_UI, FEAT_RVW, "UI «Відстеження ресурсів»",
         "Сторінка /resources-viewer/relocation: sidebar, фільтри, sum card, таблиця.",
         "RES", "HIGH", "2", "2"),
    ]


def acceptance_criteria() -> list[tuple]:
    rows = [
        (FEAT_RVW_BOM, "AC-01", "Пряма видача tracked resource → isProduct=false; sum=amount", "0"),
        (FEAT_RVW_BOM, "AC-02", "Self-produced batch → ingredient usage × relocate", "1"),
        (FEAT_RVW_BOM, "AC-03", "External FG без production → tech map fallback", "2"),
        (FEAT_RVW_BOM, "AC-04", "Mixed produced+ready-made → scale на весь relocate", "3"),
        (FEAT_RVW_BOM, "AC-05", "Depth 1/2/3 вкладеності BOM", "4"),
        (FEAT_RVW_BOM, "AC-06", "Агрегація прямих + вкладених видач у sum", "5"),
        (FEAT_RVW_BOM, "AC-07", "Mid-level ресурс як product і ingredient", "6"),
        (FEAT_RVW_BOM, "AC-08", "Unrelated product виключений фільтром resourceIds", "7"),
        (FEAT_RVW_BOM, "AC-09", "Alternative groups: default і non-default з production_process_input", "8"),
        (FEAT_RVW_BOM, "AC-10", "Blending кількох production у один batch number", "9"),
        (FEAT_RVW_BOM, "AC-11", "Item без партій → tech map на весь обсяг", "10"),
        (FEAT_RVW_BOM, "AC-12", "lookupTmByDate вибір версії техкарти", "11"),
        (FEAT_RVW_BOM, "AC-13", "Цикл у рецептах не зациклює expand", "12"),
        (FEAT_RVW_BOM, "AC-14", "Scale-down коли producedQty > amount (legacy drift)", "13"),
        (FEAT_RVW_FLT, "AC-01", "categoryIds як tracking target без resourceIds", "0"),
        (FEAT_RVW_FLT, "AC-02", "supplier (Постачальник) AND-фільтр", "1"),
        (FEAT_RVW_FLT, "AC-03", "start/end відсікають переміщення", "2"),
        (FEAT_RVW_FLT, "AC-04", "Guard: без tracking/receiver → empty page", "3"),
        (FEAT_RVW_FLT, "AC-05", "Пагінація + сортування date DESC", "4"),
        (FEAT_RVW_FLT, "AC-06", "Excel export Content-Disposition .xlsx", "5"),
        (FEAT_RVW_FLT, "AC-07", "Sender STORAGE/PRODUCTION only; UNIT→CREW excluded", "6"),
        (FEAT_RVW_FLT, "AC-08", "sum сортування resourceName ASC", "7"),
        (FEAT_RVW_FLT, "AC-09", "UI states виключають CANCELLED/RETURNED", "8"),
        (FEAT_RVW_FLT, "AC-10", "Отримувачі ПМ 414 / СБС / Інші (+ AND з receiverIds)", "9"),
        (FEAT_RVW_UI, "AC-01", "Wolf бачить «Відстеження ресурсів» у sidebar", "0"),
        (FEAT_RVW_UI, "AC-02", "Пошук: таблиця + Сумарно переміщено = API sum", "1"),
        (FEAT_RVW_UI, "AC-03", "Autocomplete ресурсів з фільтром категорії", "2"),
    ]
    return rows


def bom_cases() -> list[Case]:
    return [
        mk("TC-RVW-BOM-001", FEAT_RVW_BOM, "AC-01",
           "BOM — пряма видача tracked Alcohol",
           "Пряма видача Alcohol STORAGE→UNIT: journal isProduct=false; sum == sendAmount.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Створити Alcohol; ensureStock; POST send STORAGE→UNIT", "relocationId"),
               ("GET /resources-viewer/relocations/sum як wolf", "amount = sendAmount"),
               ("GET journal", "рядок isProduct=false; totallyUsage = sendAmount"),
           ]),
        mk("TC-RVW-BOM-002", FEAT_RVW_BOM, "AC-02",
           "BOM — self-produced Product з Alcohol@2",
           "Виробили Product → видача партії → sum = relocate × 2.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Tech map Product←Alcohol@2; produce; send batch isProduced=true", "setup"),
               ("GET sum/journal як wolf", "Alcohol amount = relocate×2; isProduct=true"),
           ]),
        mk("TC-RVW-BOM-003", FEAT_RVW_BOM, "AC-03",
           "BOM — external FG, tech map fallback",
           "Партія без локального production → BOM з техкарти.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Tech map; seed external batch; send isProduced=false", "setup"),
               ("GET journal/sum", "Alcohol = relocate × usage з tech map"),
           ]),
        mk("TC-RVW-BOM-004", FEAT_RVW_BOM, "AC-04",
           "BOM — mixed produced + ready-made scale",
           "Produce 5 + supplier 5 → relocate 10; sum = 10×usage (scale path).",
           preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Produce 5 + seed 5; send 10 з двома партіями", "relocationId"),
               ("GET sum", "Alcohol = 10 × usage"),
           ]),
        mk("TC-RVW-BOM-010", FEAT_RVW_BOM, "AC-05",
           "BOM — depth 1",
           "Alcohol → Product (depth 1).",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[("Produce+relocate depth1", "sum = relocate × alcPerUnit")]),
        mk("TC-RVW-BOM-011", FEAT_RVW_BOM, "AC-05",
           "BOM — depth 2",
           "Alcohol → Semi → Product.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[("Chain SF→Product; relocate Product", "sum = relocate×semi×alc")]),
        mk("TC-RVW-BOM-012", FEAT_RVW_BOM, "AC-05",
           "BOM — depth 3",
           "Alcohol → SF1 → SF2 → Product.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[("3-level chain; relocate", "sum = добуток коефіцієнтів")]),
        mk("TC-RVW-BOM-020", FEAT_RVW_BOM, "AC-06",
           "BOM — агрегація direct + nested",
           "Пряма видача Alcohol + Product зі Alcohol → одна sum.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[("Nested + direct issue", "sum = nested + direct")]),
        mk("TC-RVW-BOM-021", FEAT_RVW_BOM, "AC-07",
           "BOM — mid-level як product і ingredient",
           "resourceIds=[Semi]: видача Semi + видача Product зі Semi.",
           preconditions=PRE_ADMIN_BOM,
           steps=[("Дві видачі; GET journal/sum для Semi", "обидва рядки; sum = direct + nested")]),
        mk("TC-RVW-BOM-022", FEAT_RVW_BOM, "AC-08",
           "BOM — unrelated product excluded",
           "Product без Alcohol не в journal при resourceIds=[Alcohol].",
           preconditions=PRE_ADMIN_BOM,
           steps=[("Send unrelated; GET sum/journal Alcohol", "sum=0; relocation відсутній")]),
        mk("TC-RVW-ALT-001", FEAT_RVW_BOM, "AC-09",
           "BOM alt — non-default alternative counted",
           "Фактичний non-default input з production_process_input потрапляє в sum.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM, tags="resource-viewer,api,alt-groups",
           steps=[
               ("Tech map з alt group; produce з non-default alt", "batch"),
               ("Relocate; GET sum для alt resource", "amount = usage×relocate"),
           ]),
        mk("TC-RVW-ALT-002", FEAT_RVW_BOM, "AC-09",
           "BOM alt — default alternative counted",
           "Default alternative з production_process_input враховується.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM, tags="resource-viewer,api,alt-groups",
           steps=[
               ("Produce з default alt; relocate", "batch"),
               ("GET sum для default alt resource", "amount коректний"),
           ]),
        mk("TC-RVW-BOM-030", FEAT_RVW_BOM, "AC-10",
           "BOM — blending двох production в один batch",
           "Один batch number, різні рецепти → зважене середнє, не сума.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Produce 4@usage2 + 6@usage4 з shared batch", "setup"),
               ("Relocate 10; GET sum", "Alcohol = 32 (per-unit 3.2)"),
           ]),
        mk("TC-RVW-BOM-031", FEAT_RVW_BOM, "AC-11",
           "BOM — item без партій → tech map",
           "Send Product без batches → повний обсяг через tech map.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Tech map; ensureStock Product; send без batch", "relocationId"),
               ("GET sum/journal", "Alcohol = relocate × usage"),
           ]),
        mk("TC-RVW-BOM-032", FEAT_RVW_BOM, "AC-12",
           "BOM — версія техкарти на дату видачі (історична)",
           "LocalDate=startOfDay UTC: дата до createdAt → MIN(id)=V1; дата після обох → newest V2.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Дві техкарти V1@2 потім V2@5; external sends date=yesterday і tomorrow", "2 relocationId"),
               ("GET journal", "past×2; future×5"),
           ]),
        mk("TC-RVW-BOM-033", FEAT_RVW_BOM, "AC-13",
           "BOM — цикл рецептів не infinite",
           "A←B←A: expand зупиняється на path; B один раз.",
           preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Дві циклічні tech maps; send A без production", "relocationId"),
               ("GET journal resourceIds=[B]", "totallyUsage = amount; без дубля"),
           ]),
        mk("TC-RVW-BOM-034", FEAT_RVW_BOM, "AC-14",
           "BOM — scale-down (legacy producedQty > amount)",
           "Партії claim більше за amount рядка: sum = amount×usage (не producedQty×usage). "
           "Arrange: produce+relocate; DB shrink relocation_item.amount.",
           severity="MAJOR", preconditions=PRE_ADMIN_BOM + " Доступ до БД (use.database).",
           steps=[
               ("Produce+relocate Product; UPDATE amount вниз", "drift"),
               ("GET sum/journal Alcohol", "amount×usage; не повний producedQty×usage"),
           ]),
    ]


def filter_cases() -> list[Case]:
    return [
        mk("TC-RVW-001", FEAT_RVW_FLT, "AC-08",
           "API — sums сортування + pre-seed amount=0 (regression)",
           "GET /relocations → sums: порядок і amount=0 без руху. "
           "Відомий дефект tk: empty BOM → sums=[]; очікується pre-seed нулів.",
           severity="CRITICAL",
           steps=[
               ("Створити 8 ресурсів з префіксами", "ids"),
               ("GET /resources-viewer/relocations?resourceIds=…&receiverIds=UNIT", "HTTP 200"),
               ("Перевірити sums містить усі ids з amount=0 і порядок ASC",
                "До фіксу: sums=[]; після фіксу: 111→…→їжа, нулі"),
           ]),
        mk("TC-RVW-API-001", FEAT_RVW, "AC-01",
           "API — my-units без UNIT для ResourceViewer",
           "Селектор my-units: лише STORAGE/PRODUCTION.",
           severity="CRITICAL", tags="resource-viewer,api,my-units",
           steps=[
               ("GET /storages/names/my-units як wolf", "HTTP 200"),
               ("Жоден type=UNIT", "OK"),
           ]),
        mk("TC-RVW-API-002", FEAT_RVW_FLT, "AC-07",
           "API — journal лише STORAGE/PRODUCTION→UNIT",
           "У журналі видно STORAGE/PRODUCTION→UNIT; UNIT→UNIT приховано.",
           severity="CRITICAL",
           steps=[
               ("ADMIN: send STORAGE/PRODUCTION→UNIT і UNIT→UNIT", "2 relocationId"),
               ("GET /resources-viewer/relocations як wolf (receiverIds=UNIT)", "HTTP 200"),
               ("Перевірити content", "Є STORAGE/PRODUCTION→UNIT; немає UNIT→UNIT"),
           ]),
        mk("TC-RVW-API-003", FEAT_RVW_FLT, "AC-07",
           "API — UNIT→CREW excluded from sums",
           "UNIT→CREW не в sums з GET /relocations.",
           steps=[
               ("Send UNIT→CREW tracked resource (ADMIN arrange)", "relocation"),
               ("GET /relocations як wolf → sums", "amount=0"),
           ]),
        mk("TC-RVW-API-010", FEAT_RVW_FLT, "AC-01",
           "API — categoryIds як tracking target",
           "Без resourceIds: інгредієнти категорії A трекаються; pre-seed нулів вимкнений.",
           severity="CRITICAL",
           steps=[
               ("Product з inputs у cat A і cat B; relocate", "setup"),
               ("GET sum?categoryIds=A", "A у sum; B відсутній"),
           ]),
        mk("TC-RVW-API-011", FEAT_RVW_FLT, "AC-02",
           "API — supplier AND-фільтр",
           "Постачальник збігається → sum>0; інший → 0.",
           severity="CRITICAL",
           steps=[
               ("Alcohol з property Постачальник=Match; produce+relocate", "setup"),
               ("GET sum supplier=Match / Other", "Match>0; Other=0"),
           ]),
        mk("TC-RVW-API-012", FEAT_RVW_FLT, "AC-03",
           "API — start/end date range",
           "Період відсікає переміщення поза діапазоном.",
           severity="CRITICAL",
           steps=[
               ("Send сьогодні; GET з in-range і out-of-range", "in: сума>0; out: 0"),
           ]),
        mk("TC-RVW-API-013", FEAT_RVW_FLT, "AC-04",
           "API — empty guards",
           "Без tracking target або без receiver filter → content=[].",
           severity="CRITICAL",
           steps=[
               ("GET без resourceIds/categoryIds", "content=[]"),
               ("GET без receiverIds/unit*", "content=[]"),
           ]),
        mk("TC-RVW-API-014", FEAT_RVW_FLT, "AC-05",
           "API — пагінація journal + date DESC",
           "page metadata; рядки відсортовані за датою desc.",
           steps=[
               ("3+ sends; GET page=0&size=1", "totalElements≥3; totalPages≥3"),
               ("GET size=100", "dates DESC"),
           ]),
        mk("TC-RVW-API-015", FEAT_RVW_FLT, "AC-09",
           "API — UI states виключають CANCELLED/RETURNED",
           "Активна видача: UI states видно; states=CANCELLED|RETURNED — ні. "
           "Якщо RETURNED (API/DB): з UI states зникає.",
           severity="CRITICAL",
           steps=[
               ("ADMIN send STORAGE→UNIT; GET UI states", "рядок і sum>0"),
               ("GET states=CANCELLED|RETURNED", "немає relocationId"),
               ("(опц.) RETURNED; GET UI states + end bust-cache", "немає рядка; sum≈0"),
           ]),
        mk("TC-RVW-API-016", FEAT_RVW_FLT, "AC-10",
           "API — отримувачі ПМ 414 / СБС / Інші",
           "unitsOther=Інші включає типовий UNIT; unit414Pm і СБС без ПМ — ні.",
           severity="CRITICAL",
           steps=[
               ("ADMIN send на типовий UNIT", "relocationId"),
               ("GET unitsOther=Інші", "є relocation; sum=amount"),
               ("GET unit414Pm / unitSbsExcept414Pm", "немає relocation"),
           ]),
        mk("TC-RVW-API-017", FEAT_RVW_FLT, "AC-10",
           "API — receiverIds AND unit414Pm",
           "Перетин явного receiverIds з ПМ 414 порожній, якщо UNIT поза ПМ.",
           steps=[
               ("Send на UNIT∈Інші", "setup"),
               ("GET receiverIds+unit414Pm", "content=[]; sum=0"),
           ]),
        mk("TC-RVW-API-020", FEAT_RVW_FLT, "AC-06",
           "API — Excel export",
           "Валідний фільтр → .xlsx непорожній; guard → порожнє тіло.",
           severity="CRITICAL",
           steps=[
               ("GET /resources-viewer/export з resourceIds+receiverIds", "200; Content-Disposition .xlsx; body>0"),
               ("GET export без tracking", "200; body порожній"),
           ]),
    ]


def ui_cases() -> list[Case]:
    return [
        mk("TC-UI-RES-AC-001", FEAT_RVW_UI, "AC-03",
           "UI — autocomplete з фільтром категорії",
           "Категорія A: autocomplete показує ресурс A, не B.",
           tags="resource-viewer,ui", layer="UI",
           steps=[
               ("Відкрити /resources-viewer/relocation", "Сторінка завантажена"),
               ("Обрати категорію A; пошук ресурсів", "є A; немає B"),
               ("Очистити; пошук знову", "є A і B"),
           ]),
        mk("TC-UI-RVW-001", FEAT_RVW_UI, "AC-01",
           "UI — wolf sidebar «Відстеження ресурсів»",
           "Після логіну wolf бачить пункт sidebar і відкриває журнал.",
           severity="CRITICAL", tags="resource-viewer,ui,smoke", layer="UI",
           steps=[
               ("Увійти як wolf", "SPA завантажено"),
               ("Перевірити sidebar: «Відстеження ресурсів» видимий", "link visible"),
               ("Відкрити /resources-viewer/relocation", "h1 «Журнал переміщень ресурсів»"),
           ]),
        mk("TC-UI-RVW-002", FEAT_RVW_UI, "AC-02",
           "UI — пошук: таблиця + Сумарно переміщено",
           "Після Шукати картка sum збігається з API; у таблиці product/ingredient рядки.",
           severity="CRITICAL", tags="resource-viewer,ui", layer="UI",
           preconditions=PRE_ADMIN_BOM + " Видано Product зі Alcohol (self-produced).",
           steps=[
               ("Відкрити viewer; обрати Alcohol; увімкнути «Інші»; Шукати", "Дані завантажені"),
               ("Перевірити «Сумарно переміщено»", "amount ≈ API sum"),
               ("Перевірити таблицю", "є product і ingredient рядки для Alcohol"),
           ]),
    ]


def all_cases() -> list[Case]:
    cases = bom_cases() + filter_cases() + ui_cases()
    seen: set[str] = set()
    unique: list[Case] = []
    for c in cases:
        if c.test_id in seen:
            continue
        seen.add(c.test_id)
        unique.append(c)
    return unique


def main() -> None:
    cases = all_cases()
    write_xlsx_with_features(
        cases,
        OUTPUT,
        features=features(),
        acceptance_criteria=acceptance_criteria(),
        meta_extra=[("source", "erp-auto-test resource-viewer BOM + filters 2026-07-17")],
    )
    print(f"Wrote {len(cases)} test cases to {OUTPUT}")
    for c in cases:
        link = f"{c.automation_layer}:{c.automation_test_id}" if c.automation_layer else "manual"
        print(f"  {c.test_id} [{link}]")


if __name__ == "__main__":
    main()
