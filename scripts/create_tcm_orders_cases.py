# -*- coding: utf-8 -*-
"""Create REQ-ORD order test cases via TCM AI API."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

BASE = os.getenv("TCM_BASE_URL", "http://localhost:18100").rstrip("/")
PROJECT_ID = 1
TOKEN = os.getenv("TCM_AI_TOKEN", "dev-ai-token")
FEATURE = "REQ-ORD"
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
    ("AC-02", "TC-ORD-024", "IN_PROGRESS: requester cancel → 403; global Admin → CANCELLED + ACTIVE→RELEASED", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-025", "cancel дозволений з update (без manage)", "HIGH", "MAJOR", "SECURITY"),
    ("AC-02", "TC-ORD-026", "Illegal transition (NEW→DONE, з DONE/CANCELLED) → 400", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-027", "take-to-work / mark-done вимагають manage на requester", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-02", "TC-ORD-028", "Часткова бронь: manage переводить IN_PROGRESS→READY_TO_DELIVER", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-02", "TC-ORD-029", "ready-to-deliver без жодної броні → 400", "HIGH", "MAJOR", "FUNCTIONAL"),
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
    ("AC-04", "TC-ORD-045", "Коментар автора без firstName/lastName використовує username", "CRITICAL", "MAJOR", "FUNCTIONAL"),
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
    ("AC-09", "TC-ORD-093", "Partial booking: send до READY → 400; після READY → DONE", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-094", "sender≠gathering / recipient≠requester → 400", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-095", "Relocation fail → order лишається відкритим (rollback)", "HIGH", "MAJOR", "FUNCTIONAL"),
    ("AC-09", "TC-ORD-096", "Requester без relocation::create на gathering не може відправити; Admin може", "CRITICAL", "CRITICAL", "SECURITY"),
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
    ("AC-11", "TC-ORD-RBAC-003", "manage-only: take-to-work, mark-done, gathering, book, ready", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-11", "TC-ORD-RBAC-004", "gathering read: list+get+bookings view; без update — немає prepare", "HIGH", "MAJOR", "SECURITY"),
    ("AC-11", "TC-ORD-RBAC-005", "Комірник: prepare+send READY; без manage — немає book/ready", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-11", "TC-ORD-ADMIN-001", "Order_Admin-ROLE: order manage + production read; без production create і relocation send", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-09", "TC-ORD-ADMIN-002", "Рольовий E2E: requester create → Order Admin partial ready → gatherer send → requester receive", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-06", "TC-ORD-ADMIN-003", "Relocation task E2E: NEW→SHIPPED→DONE → auto-book → final send/receive", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-06", "TC-ORD-ADMIN-005", "Relocation task: amount≤order quantity regardless of gathering stock; cancel NEW releases source reservation", "CRITICAL", "CRITICAL", "FUNCTIONAL"),
    ("AC-11", "TC-ORD-ADMIN-004", "Global Admin creates PO; Order Admin links/unlinks it but cannot create PO", "CRITICAL", "CRITICAL", "SECURITY"),
    ("AC-02", "TC-ORD-ADMIN-006", "IN_PROGRESS: requester cancel → 403; Order Admin → CANCELLED + ACTIVE→RELEASED", "CRITICAL", "CRITICAL", "SECURITY"),
    # AC-12 UI
    ("AC-12", "TC-ORD-UI-001", "Список: колонки Дата/Локація/Ресурси/Статус/Створив; empty state", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-002", "Фільтри: пошук ресурсу, Період, Статус multi, reset", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-003", "Пагінація 25/100/200/500", "MEDIUM", "MINOR", "UI"),
    ("AC-12", "TC-ORD-UI-004", "«Всі локації»: create disabled + tooltip", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-005", "Створити: lines editor, category hierarchy, validation toasts", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-006", "Редагувати NEW (update): Зберегти / після take-to-work edit зникає", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-007", "Sidebar «Замовлення» лише з order::view", "MEDIUM", "MINOR", "UI"),
    ("AC-12", "TC-ORD-UI-008", "Селектор доставки: тільки активні UNIT/STORAGE/PRODUCTION; без CREW/FLY_POINT", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-009", "Селектор зберігає явно обрану локацію доставки", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-010", "NEW+manage: «Взяти в роботу», «Скасувати»; confirm modal", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-011", "IN_PROGRESS+manage: «Позначити виконаним», booking panel", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-012", "DONE/CANCELLED: лише перегляд + comments", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-013", "Availability hover (manage): «Наявність на локаціях» / заброньовано", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-014", "Comments UI: додати / empty / author", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-015", "Deep-link ?orderId=N відкриває detail; close чистить query", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-016", "Gatherer card: prepare only; empty «ще немає броней»", "HIGH", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-017", "List accent жовтий/зелений + «Підготовлено X/Y»", "HIGH", "MAJOR", "UI"),
    ("AC-04", "TC-ORD-UI-018", "UI показує username автора без firstName/lastName замість «Невідомо»", "CRITICAL", "MAJOR", "UI"),
    ("AC-12", "TC-ORD-UI-020", "Панель збору: пошук локації, badges покриття, обрати", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-021", "Таблиця Потрібно/Заброньовано/Вільно; default max; зняти бронь", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-022", "Часткова бронь: «Відправити» активна лише після «Готово до відправки»", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-023", "/relocation/create-output?orderId=N: фіксовані from/to/lines", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-024", "E2E: create→work→gather→book→prepare→send→DONE", "CRITICAL", "CRITICAL", "UI"),
    ("AC-12", "TC-ORD-UI-025", "Relocation list badge orderId", "HIGH", "MAJOR", "UI"),
    # AC-13 multi-actor browser journeys
    ("AC-13", "TC-ORD-E2E-001", "UI E2E: повне замовлення з локального залишку", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-002", "UI E2E: часткова комплектація після підтвердження готовності", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-003", "UI E2E: поповнення збору запитом на переміщення", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-004", "UI E2E: виробничий дефіцит і виконання виробничого замовлення", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-005", "UI E2E: скасування зайвого запиту і часткова доставка", "HIGH", "MAJOR", "UI"),
    ("AC-13", "TC-ORD-E2E-006", "UI E2E: локальна бронь і часткове поповнення переміщенням", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-007", "UI E2E: локальна бронь і завершене виробництво дефіциту", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-008", "UI E2E: часткова доставка при незавершеному виробництві", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-009", "UI E2E: автор редагує і видаляє нове замовлення", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-010", "UI E2E: адміністратор скасовує повністю заброньоване замовлення", "CRITICAL", "CRITICAL", "UI"),
    ("AC-13", "TC-ORD-E2E-011", "UI E2E: автор обмежений у роботі, адміністратор скасовує", "HIGH", "MAJOR", "UI"),
]


E2E_STEPS: dict[str, list[dict[str, object]]] = {
    "TC-ORD-045": [
        {"stepOrder": 1, "actionText": "Створити окремого активного користувача з доступом до локації замовника, заповненим username і без firstName/lastName.", "expectedText": "Профіль користувача активний; firstName і lastName відсутні; username доступний у сесії."},
        {"stepOrder": 2, "actionText": "Створити доступне користувачу замовлення та додати від його імені коментар через POST /orders/{id}/comments.", "expectedText": "Коментар створено; text і createdAt заповнені; authorName точно дорівнює username, а не null чи «Невідомо»."},
        {"stepOrder": 3, "actionText": "Отримати список коментарів через GET /orders/{id}/comments.", "expectedText": "Створений коментар повертається з тим самим authorName=username."},
    ],
    "TC-ORD-UI-018": [
        {"stepOrder": 1, "actionText": "Підготувати замовлення з коментарем окремого користувача, у якого є username, але відсутні firstName/lastName.", "expectedText": "API коментаря повертає authorName=username."},
        {"stepOrder": 2, "actionText": "Відкрити картку замовлення у браузері та знайти підготовлений коментар.", "expectedText": "Біля тексту коментаря показаний точний username автора; напис «Невідомо» відсутній."},
        {"stepOrder": 3, "actionText": "Перезавантажити або повторно відкрити картку замовлення.", "expectedText": "Після повторного GET коментарів username автора відображається без змін."},
    ],
    "TC-ORD-E2E-001": [
        {"stepOrder": 1, "actionText": "Під `Unit_Owner-ROLE` створити замовлення на 5 одиниць із потрібною локацією доставки.", "expectedText": "Замовлення створене у стані «Нове»; фізичні залишки не змінилися."},
        {"stepOrder": 2, "actionText": "Під `Order_Admin-ROLE` відкрити замовлення, взяти в роботу та обрати локацію збору.", "expectedText": "Стан «В роботі»; доступна панель комплектації."},
        {"stepOrder": 3, "actionText": "Забронювати всі 5 одиниць із локального залишку збору.", "expectedText": "Створена активна бронь; замовлення автоматично має стан «Готово до доставки»."},
        {"stepOrder": 4, "actionText": "Перевірити дію відправлення під адміністратором замовлень.", "expectedText": "Адміністратор замовлень не може відправити ресурс замість комірника збору."},
        {"stepOrder": 5, "actionText": "Під `Unit_Owner-ROLE` локації збору позначити бронь підготовленою та відправити замовлення.", "expectedText": "Створена кінцева видача з фіксованими відправником і отримувачем; замовлення «Виконано»."},
        {"stepOrder": 6, "actionText": "Під `Unit_Owner-ROLE` локації доставки прийняти видачу.", "expectedText": "Переміщення завершене, ресурс оприбуткований на локації доставки, активних броней немає."},
    ],
    "TC-ORD-E2E-002": [
        {"stepOrder": 1, "actionText": "Створити замовлення на 5 одиниць, маючи лише 2 одиниці на локації збору.", "expectedText": "Замовлення створене у стані «Нове»."},
        {"stepOrder": 2, "actionText": "Під `Order_Admin-ROLE` взяти його в роботу, призначити збір і забронювати 2 одиниці.", "expectedText": "Замовлення лишається «В роботі»; відправлення недоступне; видно непокритий дефіцит 3."},
        {"stepOrder": 3, "actionText": "Натиснути «Готово до доставки» та підтвердити часткове виконання.", "expectedText": "Стан «Готово до доставки» попри неповне покриття; адміністратор не отримує права на складську видачу."},
        {"stepOrder": 4, "actionText": "Під комірником збору відправити лише 2 заброньовані одиниці без обов'язкової позначки «Підготовлено».", "expectedText": "Видача успішна, замовлення «Виконано», невиконана частина не блокує завершення."},
        {"stepOrder": 5, "actionText": "Прийняти видачу на локації доставки та повторно відкрити замовлення.", "expectedText": "Ресурс прийнято; повторна відправка недоступна; броні `FULFILLED` або `RELEASED`."},
    ],
    "TC-ORD-E2E-003": [
        {"stepOrder": 1, "actionText": "Створити замовлення на 5 одиниць при нульовому залишку на зборі та 5 одиницях на іншій локації.", "expectedText": "Замовлення створене; запас існує тільки на локації-джерелі."},
        {"stepOrder": 2, "actionText": "Під `Order_Admin-ROLE` взяти замовлення в роботу, призначити збір і створити запит на переміщення 5 одиниць із джерела.", "expectedText": "Запит `NEW`; 5 одиниць зарезервовано на джерелі."},
        {"stepOrder": 3, "actionText": "Під комірником джерела з `Unit_Owner-ROLE` відкрити пряму форму запиту `/relocation/create-output?relocationTaskId=…` і підтвердити видачу на збір.", "expectedText": "Task-форма доступна; загальний `/production-tasks` не потрібний для цієї ролі; запит `SHIPPED`, переміщення в дорозі."},
        {"stepOrder": 4, "actionText": "Під комірником збору прийняти вхідне переміщення.", "expectedText": "Запит `DONE`; ресурс на зборі автоматично заброньований; замовлення «Готово до доставки»."},
        {"stepOrder": 5, "actionText": "Підготувати та відправити замовлення зі збору, потім прийняти його замовником.", "expectedText": "Кінцева видача завершена, замовлення «Виконано», ресурс на локації доставки."},
    ],
    "TC-ORD-E2E-004": [
        {"stepOrder": 1, "actionText": "Створити замовлення на вироблюваний ресурс без залишку; під `Order_Admin-ROLE` взяти його в роботу й призначити виробничу локацію збору.", "expectedText": "У панелі дефіциту ресурс доступний для виробництва."},
        {"stepOrder": 2, "actionText": "Відкрити створення ВЗ під `Order_Admin-ROLE`.", "expectedText": "Адміністратор замовлень бачить дефіцит, але кнопка нового ВЗ недоступна."},
        {"stepOrder": 3, "actionText": "Під глобальним Admin створити попередньо заповнене ВЗ із картки замовлення, розподілити та згенерувати завдання.", "expectedText": "ВЗ прив'язане до замовлення; завдання створене для потрібної виробничої локації."},
        {"stepOrder": 4, "actionText": "Під виконавцем виробничої локації виконати згенероване завдання.", "expectedText": "Продукція оприбуткована на зборі та автоматично заброньована під замовлення; стан «Готово до доставки»."},
        {"stepOrder": 5, "actionText": "Під комірником збору відправити замовлення, під замовником прийняти.", "expectedText": "Замовлення і пов'язане переміщення завершені, продукція на локації доставки."},
    ],
    "TC-ORD-E2E-005": [
        {"stepOrder": 1, "actionText": "Створити замовлення на 5 одиниць: 2 на зборі, 5 на іншій локації.", "expectedText": "Замовлення створене у стані «Нове»."},
        {"stepOrder": 2, "actionText": "Під `Order_Admin-ROLE` взяти в роботу, призначити збір і створити через UI запит на всі 5 одиниць.", "expectedText": "Попри 2 одиниці на зборі, запит `NEW` створений на 5; резерв джерела дорівнює 5."},
        {"stepOrder": 3, "actionText": "Скасувати ще не відправлений запит на переміщення.", "expectedText": "Запит `CANCELLED`; резерв джерела повністю звільнений."},
        {"stepOrder": 4, "actionText": "Забронювати локальні 2 одиниці та підтвердити «Готово до доставки».", "expectedText": "Замовлення готове до часткової доставки без активної зайвої залежності."},
        {"stepOrder": 5, "actionText": "Відправити зі збору та прийняти на локації доставки.", "expectedText": "Часткова видача завершена; замовлення «Виконано»; активних броней і резерву скасованого запиту немає."},
    ],
    "TC-ORD-E2E-006": [
        {"stepOrder": 1, "actionText": "Створити замовлення на 5 одиниць: 2 одиниці на зборі та 3 на іншій локації.", "expectedText": "Замовлення створене у стані «Нове»."},
        {"stepOrder": 2, "actionText": "Взяти замовлення в роботу, обрати збір і забронювати локальні 2 одиниці.", "expectedText": "Активна локальна бронь дорівнює 2; дефіцит дорівнює 3."},
        {"stepOrder": 3, "actionText": "Створити запит на переміщення 3 одиниць, відправити їх із джерела та прийняти на зборі.", "expectedText": "Запит `DONE`; загальна активна бронь автоматично зросла до 5."},
        {"stepOrder": 4, "actionText": "Відправити повністю скомплектоване замовлення та прийняти його замовником.", "expectedText": "Видано 5 одиниць; замовлення «Виконано»."},
    ],
    "TC-ORD-E2E-007": [
        {"stepOrder": 1, "actionText": "Створити замовлення на 5 вироблюваних одиниць при локальному залишку 2.", "expectedText": "Замовлення створене; доступні 2 одиниці та дефіцит 3."},
        {"stepOrder": 2, "actionText": "Взяти замовлення в роботу, призначити виробничий збір і забронювати 2 одиниці.", "expectedText": "Активна бронь дорівнює 2."},
        {"stepOrder": 3, "actionText": "Під Admin створити й згенерувати ВЗ на 3 одиниці, під виробником виконати задачу.", "expectedText": "ВЗ `DONE`; результат автоматично заброньований, загальна бронь дорівнює 5."},
        {"stepOrder": 4, "actionText": "Відправити замовлення зі збору та прийняти на локації доставки.", "expectedText": "Замовлення повністю видане й має стан «Виконано»."},
    ],
    "TC-ORD-E2E-008": [
        {"stepOrder": 1, "actionText": "Створити замовлення на 5 вироблюваних одиниць, призначити збір і забронювати локальні 2.", "expectedText": "Активна бронь дорівнює 2; дефіцит дорівнює 3."},
        {"stepOrder": 2, "actionText": "Створити й згенерувати ВЗ на 3 одиниці, але не виконувати виробничу задачу.", "expectedText": "ВЗ не має стану `DONE`; активна бронь замовлення залишається 2."},
        {"stepOrder": 3, "actionText": "Під адміністратором підтвердити часткову готовність, відправити 2 одиниці та прийняти їх.", "expectedText": "Замовлення «Виконано», а ВЗ залишається незавершеним."},
    ],
    "TC-ORD-E2E-009": [
        {"stepOrder": 1, "actionText": "Під автором створити й повторно відкрити нове замовлення.", "expectedText": "Замовлення має стан «Нове»; доступна дія редагування."},
        {"stepOrder": 2, "actionText": "Перевірити дію видалення/скасування та підтвердити її.", "expectedText": "Дія доступна автору; замовлення переходить у `CANCELLED`."},
    ],
    "TC-ORD-E2E-010": [
        {"stepOrder": 1, "actionText": "Створити замовлення на 5 одиниць, взяти в роботу, обрати збір і повністю забронювати.", "expectedText": "Замовлення «Готово до доставки»; активна бронь дорівнює 5."},
        {"stepOrder": 2, "actionText": "Під `Order_Admin-ROLE` скасувати замовлення.", "expectedText": "Замовлення `CANCELLED`; активна бронь повністю звільнена."},
    ],
    "TC-ORD-E2E-011": [
        {"stepOrder": 1, "actionText": "Створити замовлення, взяти його в роботу та частково забронювати.", "expectedText": "Замовлення має стан «В роботі»."},
        {"stepOrder": 2, "actionText": "Під автором повторно відкрити замовлення.", "expectedText": "Дії редагування і видалення/скасування автору недоступні."},
        {"stepOrder": 3, "actionText": "Під `Order_Admin-ROLE` скасувати замовлення.", "expectedText": "Адміністратор бачить дію; замовлення переходить у `CANCELLED`."},
    ],
}

AC_DEFINITIONS = {
    "AC-13": (
        "Наскрізні багаторольові UI-флоу замовлення: повне й часткове виконання, "
        "поповнення зі сторонньої локації, виробничий дефіцит, скасування залежностей "
        "та права автора й адміністратора на різних етапах життєвого циклу."
    ),
}


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
    test_id_prefix = os.getenv("TCM_TEST_ID_PREFIX", "").strip()
    selected_cases = [
        case for case in CASES
        if not test_id_prefix or case[1].startswith(test_id_prefix)
    ]
    required_ac_keys = {case[0] for case in selected_cases}
    for ac_key, text in AC_DEFINITIONS.items():
        if ac_key not in required_ac_keys:
            continue
        try:
            created = post(
                f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria",
                {"featureId": FEATURE, "acKey": ac_key, "text": text},
            )
            print("OK", created.get("acId") or ac_key, "->", created.get("id"))
        except RuntimeError as e:
            msg = str(e)
            if "already" in msg.lower() or "exists" in msg.lower() or "вже існує" in msg.lower():
                print("SKIP", ac_key, "already exists")
            else:
                raise
    for ac, tid, title, prio, sev, ttype in selected_cases:
        is_ui = (tid.startswith("TC-ORD-UI-")
                 or tid.startswith("TC-ORD-E2E-")
                 or tid in ("TC-ORD-101", "TC-ORD-102"))
        body = {
            "featureId": FEATURE,
            "acKey": ac,
            "testId": tid,
            "title": title,
            "priority": prio,
            "severity": sev,
            "status": "ACTIVE",
            "testType": ttype,
            "tags": "orders,req-ord",
            "expectedResult": title,
            "steps": E2E_STEPS.get(tid, [
                {"stepOrder": 1, "actionText": f"Виконати сценарій {tid}", "expectedText": title},
            ]),
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
    print(f"done ok={ok} skip={skip} fail={fail} total={len(selected_cases)}")
    return 0 if fail == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
