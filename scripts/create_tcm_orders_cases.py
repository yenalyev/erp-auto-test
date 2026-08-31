# -*- coding: utf-8 -*-
"""Create REQ-WMS-010 order test cases via TCM AI API."""
from __future__ import annotations

import json
import urllib.error
import urllib.request

BASE = "http://localhost:8100"
PROJECT_ID = 1
TOKEN = "dev-ai-token"
FEATURE = "REQ-WMS-010"
HDR = {
    "X-TCM-Ai-Token": TOKEN,
    "Accept": "application/json",
    "Content-Type": "application/json; charset=utf-8",
}

# (acKey, testId, title, priority, severity, testType)
CASES: list[tuple[str, str, str, str, str, str]] = [
    # AC-01
    ("AC-01", "TC-ORD-001", "Створити замовлення NEW з ≥1 унікальною позицією", "CRITICAL", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-002", "Порожній lines / без storage → 400", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-003", "qty ≤ 0 → 400 (quantity.invalid)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-004", "Дубль ресурсу в позиціях → 400 (duplicated)", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-005", "Ресурс без grant на локації → 400 (notAccessible)", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-006", "FULL_ACCESS локація дозволяє будь-який ресурс", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-007", "CREW: grants через батьківський склад", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-008", "FLY_POINT: ресурси з батьківської ієрархії (фактична поведінка)", "MEDIUM", "MINOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-009", "Update ліній у NEW — replace lines OK", "CRITICAL", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-010", "Update у IN_PROGRESS/DONE/CANCELLED → 400 (notEditable)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-011", "Update з чужим/невідомим storageId / inaccessible line → 4xx", "MEDIUM", "MINOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-012", "Create/update не змінює залишки", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-013", "Некоректний JSON → 400 (не 500)", "MEDIUM", "MINOR", "FUNCTIONAL"),
    ("AC-01", "TC-ORD-014", "GET available-categories — лише категорії з доступними ресурсами", "MEDIUM", "MINOR", "FUNCTIONAL"),
    # AC-02
    ("AC-02", "TC-ORD-020", "take-to-work: NEW→IN_PROGRESS (manage)", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-021", "mark-done без ACTIVE броней: IN_PROGRESS→DONE", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-022", "mark-done з ACTIVE → 400 (done.activeBookings)", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-023", "cancel з NEW → CANCELLED", "CRITICAL", "MAJOR", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-024", "cancel з IN_PROGRESS → CANCELLED + ACTIVE→RELEASED", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-025", "cancel дозволений з update (без manage)", "HIGH", "MAJOR", "SECURITY"),
    ("AC-02", "TC-ORD-026", "Illegal transition (NEW→DONE, з DONE/CANCELLED) → 400", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-027", "take-to-work / mark-done вимагають manage на requester", "CRITICAL", "CRITICAL", "SECURITY"),
    # AC-03
    ("AC-03", "TC-ORD-030", "GET page: default sort createdAt DESC, pagination", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-03", "TC-ORD-031", "Фільтр states (один і кілька)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-03", "TC-ORD-032", "Фільтр resourceSearch (ILIKE по назві ресурсу лінії)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-03", "TC-ORD-033", "Фільтр startDate/endDate по createdAt", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-03", "TC-ORD-034", "storageIds: видно як requester, так і gathering", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-03", "TC-ORD-035", "GET by id без storageId — access requester OR gathering", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-03", "TC-ORD-036", "List progress activeBookings/preparedBookings лише з правом бачити броні", "MEDIUM", "MINOR", "FUNCTIONAL"),
    ("AC-03", "TC-ORD-037", "Без read на жодну з локацій → 403 / порожній scope", "HIGH", "MAJOR", "SECURITY"),
    # AC-04
    ("AC-04", "TC-ORD-040", "POST comment — authorName з сесії, text збережено", "CRITICAL", "MAJOR", "FUNCTIONAL"),
    ("AC-04", "TC-ORD-041", "GET comments — newest first", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-04", "TC-ORD-042", "Blank text → 400", "MEDIUM", "MINOR", "FUNCTIONAL"),
    ("AC-04", "TC-ORD-043", "Comment з read на gathering (не requester)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-04", "TC-ORD-044", "Comment без access → 403", "HIGH", "MAJOR", "SECURITY"),
    # AC-05
    ("AC-05", "TC-ORD-050", "GET availability: locations з amount + heldAmount", "CRITICAL", "MAJOR", "FUNCTIONAL"),
    ("AC-05", "TC-ORD-051", "Scope обмежений order_availability_root_storage (+ children)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-05", "TC-ORD-052", "Без конфига — усі локації з stock цих ресурсів", "MEDIUM", "MINOR", "FUNCTIONAL"),
    ("AC-05", "TC-ORD-053", "Availability лише з manage на requester", "HIGH", "MAJOR", "SECURITY"),
    # AC-06
    ("AC-06", "TC-ORD-060", "Призначити gathering STORAGE/PRODUCTION, order IN_PROGRESS", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-06", "TC-ORD-061", "Candidates: активні STORAGE/PRODUCTION, без requester; scope root", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-06", "TC-ORD-062", "Coverage «Покриває N з M / повністю» (availability math)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-06", "TC-ORD-063", "Change gathering заблоковано при non-RELEASED бронях", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-06", "TC-ORD-064", "Після release усіх — зміна дозволена", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-06", "TC-ORD-065", "sameAsRequester / inactive / non-capable / outOfScope / not IN_PROGRESS → 400", "HIGH", "MAJOR", "FUNCTIONAL"),
    # AC-07
    ("AC-07", "TC-ORD-070", "Book ≤ free і ≤ remaining → ACTIVE на gathering", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-07", "TC-ORD-071", "Повторний book — merge в один hold; prepared скидається", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-07", "TC-ORD-072", "Book > free → 400 (insufficientAvailable)", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-07", "TC-ORD-073", "Book > line qty → 400 (exceedsRequested)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-07", "TC-ORD-074", "Без gathering / не IN_PROGRESS / чужий resource / amount≤0 → 400", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-07", "TC-ORD-075", "Release → RELEASED; повторний book — новий ACTIVE", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-07", "TC-ORD-076", "Book/release лише manage на requester", "CRITICAL", "CRITICAL", "SECURITY"),
    # AC-08
    ("AC-08", "TC-ORD-080", "Mark prepared — preparedBy/At", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-08", "TC-ORD-081", "Bulk all prepared / unprepared", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-08", "TC-ORD-082", "Prepare на RELEASED / чужій броні → 400", "MEDIUM", "MINOR", "FUNCTIONAL"),
    ("AC-08", "TC-ORD-083", "Prepare лише update на gathering (без manage requester)", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-08", "TC-ORD-084", "Ship дозволений без prepared", "MEDIUM", "MINOR", "FUNCTIONAL"),
    # AC-09
    ("AC-09", "TC-ORD-090", "Send+orderId: sender=gathering, recipient=requester → DONE+FULFILLED+stock", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-091", "relocation.orderId set; UI бейдж «Створено на основі замовлення №N»", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-092", "Overship + extra resources дозволені", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-093", "Undersend / missing ordered resource → 400", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-094", "sender≠gathering / recipient≠requester → 400", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-095", "Relocation fail → order лишається відкритим (rollback)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-096", "Fulfill потребує manage на requester (canFulfillOrder)", "CRITICAL", "CRITICAL", "SECURITY"),
    # AC-10
    ("AC-10", "TC-ORD-100", "Inventory bookedAmount; free = amount − booked", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-10", "TC-ORD-101", "UI «Вільна к-сть» + жовтий бейдж + тултіп Всього/Заброньовано/Вільно", "HIGH", "MAJOR", "UI"),
    ("AC-10", "TC-ORD-102", "Inventory edit: «з них N заброньовано»", "MEDIUM", "MINOR", "UI"),
    ("AC-10", "TC-ORD-103", "Relocation picker «доступно» = free", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-10", "TC-ORD-104", "Будь-яке списання нижче hold → 400 (…заброньовано N)", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-10", "TC-ORD-REG-001", "Inventory adjust нижче броні → 400", "CRITICAL", "CRITICAL", "REGRESSION"),
    ("AC-10", "TC-ORD-REG-002", "Звичайна relocation send з джерела з hold → 400", "CRITICAL", "CRITICAL", "REGRESSION"),
    ("AC-10", "TC-ORD-REG-003", "Receive/rollback що ламає hold → 400", "HIGH", "MAJOR", "REGRESSION"),
    ("AC-10", "TC-ORD-REG-004", "Defect/брак нижче броні → 400", "CRITICAL", "CRITICAL", "REGRESSION"),
    ("AC-10", "TC-ORD-REG-005", "Production input нижче броні → 400", "CRITICAL", "CRITICAL", "REGRESSION"),
    ("AC-10", "TC-ORD-REG-006", "Після RELEASED/FULFILLED списання знову OK", "HIGH", "MAJOR", "REGRESSION"),
    ("AC-10", "TC-ORD-REG-007", "Редагування видачі в частині заброньованого залишку → 400", "HIGH", "MAJOR", "REGRESSION"),
    ("AC-10", "TC-ORD-UI-026", "UI — «Редагування видачі»: заброньовано недоступно", "HIGH", "MAJOR", "UI"),
    # AC-11
    ("AC-11", "TC-ORD-RBAC-001", "create: 200 з create / 403 без", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-11", "TC-ORD-RBAC-002", "update lines: лише update+NEW", "HIGH", "MAJOR", "SECURITY"),
    ("AC-11", "TC-ORD-RBAC-003", "manage-only: take-to-work, mark-done, gathering, book, send", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-11", "TC-ORD-RBAC-004", "gathering read: list+get+bookings view; без update — немає prepare", "HIGH", "MAJOR", "SECURITY"),
    ("AC-11", "TC-ORD-RBAC-005", "gathering update: prepare; без manage — немає book/send", "CRITICAL", "CRITICAL", "SECURITY"),
    # AC-12 UI
    ("AC-12", "TC-ORD-UI-001", "Список: колонки Дата/Локація/Ресурси/Статус/Створив; empty state", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-002", "Фільтри: пошук ресурсу, Період, Статус multi, reset", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-003", "Пагінація 25/100/200/500", "MEDIUM", "MINOR", "UI"),
    ("AC-12", "TC-ORD-UI-004", "«Всі локації»: create disabled + tooltip", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-005", "Створити: lines editor, category hierarchy, validation toasts", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-006", "Редагувати NEW (update): Зберегти / після take-to-work edit зникає", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-007", "Sidebar «Замовлення» лише з order::view", "MEDIUM", "MINOR", "UI"),
    ("AC-12", "TC-ORD-UI-010", "NEW+manage: «Взяти в роботу», «Скасувати»; confirm modal", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-011", "IN_PROGRESS+manage: «Позначити виконаним», booking panel", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-012", "DONE/CANCELLED: лише перегляд + comments", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-013", "Availability hover (manage): «Наявність на локаціях» / заброньовано", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-014", "Comments UI: додати / empty / author", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-015", "Deep-link ?orderId=N відкриває detail; close чистить query", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-016", "Gatherer card: prepare only; empty «ще немає броней»", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-017", "List accent жовтий/зелений + «Підготовлено X/Y»", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-020", "Панель збору: пошук локації, badges покриття, обрати", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-021", "Таблиця Потрібно/Заброньовано/Вільно; default max; зняти бронь", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-022", "«Відправити» активна лише при повному бронюванні", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-023", "/relocation/create-output?orderId=N: фіксовані from/to/lines", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-024", "E2E: create→work→gather→book→prepare→send→DONE", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-025", "Relocation list badge orderId", "HIGH", "MAJOR", "UI"),
]


def post(path: str, body: dict) -> dict:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=HDR, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"POST {path} -> HTTP {e.code}: {detail}") from e


def main() -> int:
    ok = fail = skip = 0
    for ac, tid, title, prio, sev, ttype in CASES:
        is_ui = tid.startswith("TC-ORD-UI-") or tid in ("TC-ORD-101", "TC-ORD-102")
        body = {
            "featureId": FEATURE,
            "acKey": ac,
            "testId": tid,
            "title": title,
            "priority": prio,
            "severity": sev,
            "status": "ACTIVE",
            "testType": ttype,
            "tags": "orders,req-wms-010",
            "expectedResult": title,
            "steps": [
                {"stepOrder": 1, "actionText": f"Виконати сценарій {tid}", "expectedText": title},
            ],
        }
        if is_ui:
            body["uiAutomationIds"] = [tid]
        else:
            body["apiAutomationIds"] = [tid]
        try:
            created = post(f"/api/ai/projects/{PROJECT_ID}/test-cases", body)
            print("OK", created.get("testId") or tid, "->", created.get("id"))
            ok += 1
        except RuntimeError as e:
            msg = str(e)
            if "409" in msg or "already" in msg.lower() or "exists" in msg.lower() or "duplicate" in msg.lower():
                print("SKIP", tid, msg[:120])
                skip += 1
            else:
                print("FAIL", tid, msg[:300])
                fail += 1
    print(f"done ok={ok} skip={skip} fail={fail} total={len(CASES)}")
    return 0 if fail == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
