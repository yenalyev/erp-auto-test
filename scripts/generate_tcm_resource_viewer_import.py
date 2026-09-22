#!/usr/bin/env python3
"""
Generate TCM import XLSX for a dynamically created Resource Viewer — BOM decomposer + filters + UI.

Covers:
  - existing automated TC-RVW-BOM-*, TC-RVW-ALT-*, TC-RVW-001, TC-RVW-API-001..003, TC-UI-RES-AC-001
  - automated TC-RVW-BOM-030..036, TC-RVW-API-010..021, TC-UI-RVW-001..005
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
PRE_RVW = "Динамічний @ResourceViewer з глобальною роллю «Відстеження ресурсів» залогінений. Середовище dev/staging."
PRE_ADMIN_BOM = (
    f"{PRE_RVW} @Admin підготував tech maps PRODUCTION і stock на дочірній локації "
    "TSUK_PARENT_UNITS; receiver — локація поза TSUK_PARENT_UNITS."
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
    preconditions: str = PRE_RVW,
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
         "Користувач формує журнал переміщень вибраних ресурсів до виробничих підрозділів. "
         "Система повертає деталізовані переміщення, фактичну витрату компонентів, "
         "підсумки та Excel-експорт за однаковими критеріями пошуку.",
         "RES", "CRITICAL", "1", "0"),
        (FEAT_RVW_BOM, FEAT_RVW, "Розрахунок компонентів продукту",
         "Для переміщеного продукту система визначає фактично використані компоненти за виробничими даними, "
         "масштабує їх до кількості переміщення та проходить вкладені рецептури. Кількість без виробничого "
         "факту — зокрема підтверджена зовнішня або інвентаризаційна — розкладається за найновішою tech map, "
         "що існувала на момент переміщення; без такої карти ресурс вважається атомарним.",
         "RES", "CRITICAL", "2", "0"),
        (FEAT_RVW_FLT, FEAT_RVW, "Фільтрація, журнал і підсумки",
         "Фільтри визначають ресурси спостереження, одержувачів, постачальника та період. "
         "Category-only пошук згортає пов'язані рівні BOM і повертає унікальні фізичні переміщення; "
         "явно вибрані рівні не згортаються. Експорт використовує ті самі критерії.",
         "RES", "HIGH", "2", "1"),
        (FEAT_RVW_UI, FEAT_RVW, "Сторінка «Відстеження ресурсів»",
         "Користувач налаштовує фільтри, запускає пошук, бачить журнал і блок "
         "«Сумарно переміщено», керує напрямком category-only групування та може "
         "вивантажити той самий набір даних у Excel.",
         "RES", "HIGH", "2", "2"),
    ]


def acceptance_criteria() -> list[tuple]:
    rows = [
        (FEAT_RVW, "AC-01",
         "Користувач із правом Resource Viewer має доступ до журналу, підсумків та експорту; "
         "селектор джерел містить склади й виробництва, але не підрозділи типу UNIT.", "0"),
        (FEAT_RVW_BOM, "AC-01",
         "Якщо передано сам відстежуваний ресурс, журнал позначає його як не-продукт, "
         "а підсумок дорівнює фактичній кількості переміщення.", "0"),
        (FEAT_RVW_BOM, "AC-02",
         "Для продукту власного виробництва витрата кожного компонента визначається за фактичними "
         "виробничими даними та масштабується до кількості переміщення.", "1"),
        (FEAT_RVW_BOM, "AC-03",
         "Якщо підтверджено, що готовий продукт отримано ззовні, система розкладає його за алгоритмом "
         "інвентаризаційного залишку без партії; для alternative group використовується default-компонент.", "2"),
        (FEAT_RVW_BOM, "AC-04",
         "Якщо переміщення складається з виробленої та отриманої готовою кількості продукту, "
         "вироблена частина розкладається за фактичними даними виробництва, а отримана ззовні — "
         "за алгоритмом інвентаризаційного залишку без партії.", "3"),
        (FEAT_RVW_BOM, "AC-05",
         "Система розкладає продукт щонайменше на три рівні вкладеності та множить норми "
         "використання на кожному рівні.", "4"),
        (FEAT_RVW_BOM, "AC-06",
         "Підсумок ресурсу об'єднує його прямі переміщення та використання як вкладеного компонента.", "5"),
        (FEAT_RVW_BOM, "AC-07",
         "Проміжний ресурс враховується і як самостійно переміщений продукт, і як компонент іншого продукту.", "6"),
        (FEAT_RVW_BOM, "AC-08",
         "Переміщення продукту, який не містить жодного відстежуваного ресурсу, "
         "не потрапляє до журналу та підсумків.", "7"),
        (FEAT_RVW_BOM, "AC-09",
         "Для альтернативної групи враховується компонент, фактично використаний у виробництві, "
         "незалежно від того, чи є він стандартним варіантом рецептури.", "8"),
        (FEAT_RVW_BOM, "AC-10",
         "Якщо одну партію створено кількома виробництвами з різними витратами, "
         "система використовує дані останнього запису виробництва.", "9"),
        (FEAT_RVW_BOM, "AC-11",
         "Для інвентаризаційного залишку без підтвердженого походження система використовує найновішу "
         "активну або архівну tech map, що вже існувала на момент переміщення; alternative group використовує default.", "10"),
        (FEAT_RVW_BOM, "AC-12",
         "Створення нової карти, деактивація старої або зміна поточної активної карти після переміщення "
         "не змінює історичний склад цього переміщення.", "11"),
        (FEAT_RVW_BOM, "AC-13",
         "Циклічне посилання A-B-C-A зупиняється як A-B-C без зациклення та повторного підрахунку A.", "12"),
        (FEAT_RVW_BOM, "AC-14",
         "Якщо історичні дані партій перевищують фактичну кількість переміщення, "
         "витрата масштабується до кількості переміщення.", "13"),
        (FEAT_RVW_BOM, "AC-15",
         "Якщо на момент переміщення не існувало жодної tech map, ресурс залишається атомарним; "
         "карта, створена пізніше, на нього не впливає.", "14"),
        (FEAT_RVW_FLT, "AC-01",
         "Категорія є самостійною ціллю відстеження: ресурси категорії шукаються як прямі "
         "переміщення та як компоненти продуктів без обов'язкового вибору окремих ресурсів.", "0"),
        (FEAT_RVW_FLT, "AC-02",
         "Фільтр постачальника додатково звужує переміщення, відібрані за іншими критеріями.", "1"),
        (FEAT_RVW_FLT, "AC-03",
         "Початкова й кінцева дати однаково обмежують журнал і підсумки.", "2"),
        (FEAT_RVW_FLT, "AC-04",
         "Без цілі відстеження або без одержувача система повертає успішну порожню відповідь.", "3"),
        (FEAT_RVW_FLT, "AC-05",
         "Журнал підтримує пагінацію, повертає коректну загальну кількість записів і сортується "
         "за датою переміщення від нових до старих.", "4"),
        (FEAT_RVW_FLT, "AC-06",
         "Excel-експорт містить результат за тими самими критеріями, що й журнал; "
         "для порожнього обов'язкового контексту тіло експорту порожнє.", "5"),
        (FEAT_RVW_FLT, "AC-07",
         "До результату входять переміщення з дочірньої структури TSUK_PARENT_UNITS до структури, "
         "яка не належить TSUK_PARENT_UNITS; передачі між підрозділами, зокрема до екіпажу, виключаються.", "6"),
        (FEAT_RVW_FLT, "AC-08",
         "Підсумки ресурсів сортуються за назвою за зростанням.", "7"),
        (FEAT_RVW_FLT, "AC-09",
         "До робочого результату входять активні стани CREATED, FINISHED і AUTO_FINISHED; "
         "CANCELLED та RETURNED виключаються.", "8"),
        (FEAT_RVW_FLT, "AC-10",
         "Одержувачів можна вибирати групами ПМ 414, СБС та Інші; якщо одночасно задано "
         "receiverIds, результат є перетином явного списку та вибраної групи.", "9"),
        (FEAT_RVW_FLT, "AC-11",
         "Для пошуку лише за категорією кожне фізичне переміщення присутнє в журналі не більше одного "
         "разу за relocationId, навіть якщо продукт і його компоненти належать до вибраної категорії; "
         "пов'язані рівні згортаються без подвійного підрахунку.", "10"),
        (FEAT_RVW_FLT, "AC-12",
         "Якщо готовий виріб і його напівфабрикат вибрані явно через resourceIds, category-only групування "
         "не застосовується: журнал і підсумки показують декомпозицію кожного явно вибраного рівня.", "11"),
        (FEAT_RVW_FLT, "AC-13",
         "Для category-only пошуку groupByLowestComponents=true залишає найнижчі пов'язані компоненти, "
         "а false — найвищі; groupedFrom пояснює, які рівні було згорнуто.", "12"),
        (FEAT_RVW_UI, "AC-01",
         "Користувач із правом Resource Viewer бачить пункт «Відстеження ресурсів» і може відкрити журнал.", "0"),
        (FEAT_RVW_UI, "AC-02",
         "Після пошуку сторінка показує журнал і блок «Сумарно переміщено»; підсумок у UI відповідає API.", "1"),
        (FEAT_RVW_UI, "AC-03",
         "Автодоповнення ресурсів обмежується вибраною категорією та відновлює повний список після її очищення.", "2"),
        (FEAT_RVW_UI, "AC-04",
         "Excel-експорт запускається з поточними фільтрами та формує справний файл .xlsx.", "3"),
        (FEAT_RVW_UI, "AC-05",
         "Перемикач групування доступний лише для category-only пошуку; за замовчуванням залишає найнижчі "
         "компоненти, а згорнуті рівні позначаються як «Згруповано».", "4"),
        (FEAT_RVW_UI, "AC-06",
         "Після явного вибору готового виробу та його напівфабрикату перемикач групування недоступний, "
         "а UI показує обидві декомпозиції без позначки згорнутого рівня.", "5"),
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
               ("GET /resources-viewer/relocations/sum як Resource Viewer", "amount = sendAmount"),
               ("GET journal", "рядок isProduct=false; totallyUsage = sendAmount"),
           ]),
        mk("TC-RVW-BOM-002", FEAT_RVW_BOM, "AC-02",
           "BOM — self-produced Product з Alcohol@2",
           "Виробили Product → видача партії → sum = relocate × 2.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Tech map Product←Alcohol@2; produce; send batch isProduced=true", "setup"),
               ("GET sum/journal як Resource Viewer", "Alcohol amount = relocate×2; isProduct=true"),
           ]),
        mk("TC-RVW-BOM-003", FEAT_RVW_BOM, "AC-03",
           "BOM — external FG за історичною tech map",
           "Підтверджена зовнішня партія розкладається за найновішою tech map, яка існувала на момент переміщення; для alternative group береться default.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Створити tech map з fixed input і default/non-default alternatives", "Tech map існує до переміщення"),
               ("Seed external batch; send isProduced=false", "relocationId"),
               ("GET journal/sum", "Fixed і default alternative розраховані; non-default = 0"),
           ]),
        mk("TC-RVW-BOM-004", FEAT_RVW_BOM, "AC-04",
           "BOM — mixed produced + external незалежні алгоритми",
           "Produced-частина розкладається за фактичними inputs виробництва, external-частина — за історичною tech map з default alternative.",
           preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Produce 5 з non-default alternative; seed external 5", "Дві партії різного походження"),
               ("Send 10 з produced=true та produced=false batches", "relocationId"),
               ("GET journal/sum", "Produced використовує non-default; external використовує default; fixed враховано для всіх 10"),
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
           "BOM — останнє production для shared batch",
           "Один batch number і кілька production records з різними фактичними витратами → використовується останній запис.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("За однією tech map створити 4@usage2, потім 6@usage4 з shared batch", "Два валідні production records"),
               ("Relocate 10; GET sum", "Alcohol = 40 за останнім usage4"),
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
           "Для інвентаризаційного залишку використовується найновіша карта, що вже існувала на момент переміщення; майбутня карта історію не змінює.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Backdate V1@2 і V2@5; виконати переміщення між датами карт та після V2", "2 relocationId"),
               ("GET journal", "Перше переміщення = amount×2; друге = amount×5"),
               ("Деактивувати V1/V2, створити V3@9 після переміщень; повторити GET", "Обидва історичні склади не змінилися"),
           ]),
        mk("TC-RVW-BOM-033", FEAT_RVW_BOM, "AC-13",
           "BOM — цикл рецептів не infinite",
           "A←B←C←A: expand повертає A-B-C, зупиняється перед повторним A і не дублює компоненти.",
           preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Три циклічні tech maps; send A без production", "relocationId"),
               ("GET journal resourceIds=[A,B,C]", "Ingredients містять A, B і C по одному разу; повторного A немає"),
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
        mk("TC-RVW-BOM-035", FEAT_RVW_BOM, "AC-15",
           "BOM — до першої tech map ресурс атомарний",
           "Якщо на момент переміщення не існувало жодної tech map, пізніше створена карта не змінює історичний склад.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Створити inventory stock Product і перемістити його до появи tech map", "relocationId"),
               ("Після переміщення створити tech map Product←Component", "Tech map створена пізніше"),
               ("GET journal/sum", "Component відсутній; Product лишається атомарним, isProduct=false"),
           ]),
        mk("TC-RVW-BOM-036", FEAT_RVW_BOM, "AC-15",
           "BOM — зовнішня партія до першої tech map атомарна",
           "Зовнішньо отримана партія, переміщена до появи першої tech map, лишається атомарною; "
           "пізніше створена карта не змінює історичний склад.",
           severity="CRITICAL", preconditions=PRE_ADMIN_BOM,
           steps=[
               ("Отримати Product зовнішньою партією та перемістити її до появи tech map", "relocationId; isProduced=false"),
               ("Після переміщення створити tech map Product←Component", "Tech map створена пізніше"),
               ("GET journal/sum за Component", "Relocation відсутній; Component amount=0"),
               ("GET journal за Product", "Один атомарний рядок Product, isProduct=false"),
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
           "API — глобальному ResourceViewer не потрібен my-units",
           "Глобальна роль не має location grants; /my-units недоступний, viewer endpoints доступні.",
           severity="CRITICAL", tags="resource-viewer,api,my-units",
           steps=[
               ("GET /storages/names/my-units як Resource Viewer", "HTTP 403 без location grants"),
               ("Жоден type=UNIT", "OK"),
           ]),
        mk("TC-RVW-API-002", FEAT_RVW_FLT, "AC-07",
           "API — journal лише STORAGE/PRODUCTION→UNIT",
           "У журналі видно STORAGE/PRODUCTION→UNIT; UNIT→UNIT приховано.",
           severity="CRITICAL",
           steps=[
               ("ADMIN: send STORAGE/PRODUCTION→UNIT і UNIT→UNIT", "2 relocationId"),
               ("GET /resources-viewer/relocations як Resource Viewer (receiverIds=UNIT)", "HTTP 200"),
               ("Перевірити content", "Є STORAGE/PRODUCTION→UNIT; немає UNIT→UNIT"),
           ]),
        mk("TC-RVW-API-003", FEAT_RVW_FLT, "AC-07",
           "API — UNIT→CREW excluded from sums",
           "UNIT→CREW не в sums з GET /relocations.",
           steps=[
               ("Send UNIT→CREW tracked resource (ADMIN arrange)", "relocation"),
               ("GET /relocations як Resource Viewer → sums", "amount=0"),
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
        mk("TC-RVW-API-018", FEAT_RVW_FLT, "AC-11",
           "API — category-only не дублює переміщення продукту та компонента",
           "Один рядок journal за relocationId, коли продукт і компонент належать до вибраної "
           "категорії; найнижчий компонент містить groupedFrom, а sum не подвоюється.",
           severity="CRITICAL",
           expected_result=(
               "Передача продукту A присутня рівно один раз за relocationId; показано компонент Б, "
               "groupedFrom містить продукт A, а підсумок компонента не подвоєний."
           ),
           steps=[
               ("Створити продукт A з компонентом Б; обидва ресурси віднести до категорії «Взуття»",
                "Продукт A, компонент Б і спільна категорія створені"),
               ("Виробити продукт A та передати його до вибраного підрозділу",
                "Створено одне фізичне переміщення продукту A"),
               ("Виконати пошук Resource Viewer за категорією «Взуття» та одержувачем",
                "relocationId присутній один раз; компонент Б має groupedFrom=[продукт A]"),
           ]),
        mk("TC-RVW-API-019", FEAT_RVW_FLT, "AC-12",
           "API — явно вибрані готовий виріб і напівфабрикат не згортаються",
           "resourceIds=[A,Б] повертає декомпозицію A через Б та окреме представлення явно "
           "вибраного A; суми містять обидва рівні, groupedFrom порожній.",
           severity="CRITICAL",
           steps=[
               ("Створити A з компонентом Б, виробити A та передати його", "Один relocation готового виробу A"),
               ("GET journal з resourceIds=[A,Б] та receiverIds", "Для relocation є product-row з Б і self-row A"),
               ("Перевірити sums і groupedFrom", "A та Б присутні з коректними сумами; groupedFrom порожній"),
           ]),
        mk("TC-RVW-API-021", FEAT_RVW_FLT, "AC-13",
           "API — category-only групування за найвищим компонентом",
           "groupByLowestComponents=false залишає A, згортає Б у groupedFrom та не дублює relocation.",
           severity="CRITICAL",
           steps=[
               ("Створити A з компонентом Б в одній категорії та передати A", "Один relocation A"),
               ("GET за categoryIds з groupByLowestComponents=false", "Один self-row A; groupedFrom містить Б"),
               ("Перевірити sums", "Є A з кількістю relocation; Б окремо відсутній"),
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
           "UI — Resource Viewer sidebar «Відстеження ресурсів»",
           "Після логіну динамічний Resource Viewer бачить пункт sidebar і відкриває журнал.",
           severity="CRITICAL", tags="resource-viewer,ui,smoke", layer="UI",
           steps=[
               ("Увійти як динамічний Resource Viewer", "SPA завантажено"),
               ("Перевірити sidebar: «Відстеження ресурсів» видимий", "link visible"),
               ("Відкрити /resources-viewer/relocation", "h1 «Журнал переміщень ресурсів»"),
           ]),
        mk("TC-UI-RVW-002", FEAT_RVW_UI, "AC-02",
           "UI — пошук: таблиця + Сумарно переміщено",
           "Після Шукати картка sum збігається з API; у таблиці product/ingredient рядки.",
           severity="CRITICAL", tags="resource-viewer,ui", layer="UI",
           preconditions=PRE_ADMIN_BOM + " Видано Product зі Alcohol (self-produced).",
           steps=[
               ("Відкрити viewer; обрати Alcohol і конкретного зовнішнього отримувача; Шукати", "Дані завантажені"),
               ("Перевірити «Сумарно переміщено»", "amount ≈ API sum"),
               ("Перевірити таблицю", "є product і ingredient рядки для Alcohol"),
           ]),
        mk("TC-UI-RVW-003", FEAT_RVW_UI, "AC-04",
           "UI — Excel export поточного результату",
           "Поточні resourceIds і receiverIds передаються в export; завантажений XLSX непорожній.",
           severity="CRITICAL", tags="resource-viewer,ui", layer="UI",
           steps=[
               ("Виконати пошук за ресурсом і одержувачем", "Таблиця завантажена"),
               ("Натиснути «Експорт в Excel»", "Завантажено непорожній .xlsx"),
           ]),
        mk("TC-UI-RVW-004", FEAT_RVW_UI, "AC-05",
           "UI — category-only групує продукт і компонент в один рядок",
           "Перемикач доступний і checked; один relocation-row має позначку «Згруповано».",
           severity="CRITICAL", tags="resource-viewer,ui", layer="UI",
           steps=[
               ("Обрати категорію продукту A і компонента Б", "Перемикач grouping enabled і checked"),
               ("Обрати одержувача та виконати пошук", "Один рядок A; є Б і «Згруповано»"),
           ]),
        mk("TC-UI-RVW-005", FEAT_RVW_UI, "AC-06",
           "UI — явно вибрані готовий виріб і напівфабрикат показані окремо",
           "Після resourceIds=[A,Б] grouping disabled; UI показує обидві декомпозиції без «Згруповано».",
           severity="CRITICAL", tags="resource-viewer,ui", layer="UI",
           steps=[
               ("Явно вибрати готовий виріб A і компонент Б", "Перемикач grouping disabled"),
               ("Обрати одержувача та виконати пошук", "Два логічні рядки relocation; немає «Згруповано»"),
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
        meta_extra=[("source", "erp-auto-test resource-viewer business rules + tests 2026-09-17")],
    )
    print(f"Wrote {len(cases)} test cases to {OUTPUT}")
    for c in cases:
        link = f"{c.automation_layer}:{c.automation_test_id}" if c.automation_layer else "manual"
        print(f"  {c.test_id} [{link}]")


if __name__ == "__main__":
    main()
