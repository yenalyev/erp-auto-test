# REQ-WMS-010 — Замовлення та бронювання ресурсів

Документація фічі для команди QA / продукту / автотестів.  
TCM feature: **REQ-WMS-010** (модуль WMS, parent `REQ-WMS`).  
SUT: backend `tk`, frontend `tk-ui`. Автотести: `erp-auto-test`.  
SSOT у репозиторії: цей файл.

---

## 1. Огляд

**Замовлення** — заявка локації-замовника на ресурси. Lifecycle: `NEW` → `IN_PROGRESS` → `DONE` | `CANCELLED`.

У статусі «В роботі» адмін призначає **локацію збору**, бронює залишок (`resource_booking`), збирач позначає «Підготовлено», відправка — через `POST /relocations/send` з `orderId` (видача збору → замовнику). Броні зменшують **вільний** залишок на локації збору.

UI: `/orders` (список + діалоги), відправка `/relocation/create-output?orderId=N`.

---

## 2. Стани та переходи

| З | В | Дія | Permission |
|---|---|-----|------------|
| — | NEW | POST create | `order::create` на requester |
| NEW | IN_PROGRESS | take-to-work | `order::manage` |
| NEW / IN_PROGRESS | CANCELLED | cancel | `manage` **або** `update` |
| IN_PROGRESS | DONE | mark-done (без ACTIVE броней) або ship+orderId | `manage` / fulfill |
| DONE / CANCELLED | — | фінал | — |

Edit ліній — лише `NEW` + `order::update`. Cancel знімає ACTIVE → RELEASED. Mark-done заблоковано при ACTIVE.

---

## 3. Бронювання (IN_PROGRESS)

1. `PUT .../gathering-storage` — STORAGE/PRODUCTION ≠ requester, у scope `order_availability_root_storage` якщо заданий.
2. `POST .../bookings` — ACTIVE hold на gathering; ≤ free і ≤ line qty; merge на лінії.
3. `PUT .../prepared` — `order::update` на gathering; who/when.
4. `POST /relocations/send` + `orderId` — DONE + FULFILLED + stock move; `relocation.orderId`.

Зміна gathering заблокована поки є броні ≠ RELEASED.

---

## 4. ACL (продуктова модель ролей)

| Роль Keycloak | Order permissions |
|---------------|-------------------|
| **Business_Unit_Owner** | view, read, create, update, delete (+ `order-list::read`). **Без manage** |
| **Administrator** | ті самі + **`order::{bu}::manage`** (+ `order::close` у ролі) |

| Дія | Хто в автотестах / на практиці |
|-----|--------------------------------|
| Create / update lines | Owner (`order::create` / `update` на requester) |
| take-to-work, mark-done, gathering, book, release, send | **Admin** (`order::manage` на requester) |
| cancel | Owner (`update`) або Admin (`manage`) |
| list/get/comments | read на requester **або** gathering |
| see bookings | manage requester **або** read gathering |
| prepare | update на gathering (Owner збору / Admin) |
| «Прийняти» на `/relocations` (resolve → FINISHED) | **3bat** на своєму UNIT — тоді з’являється залишок |

Автотести: підрозділ **`3bat`** (`UNIT_ANALYST`) створює/бачить заявку на своєму UNIT;
`ADMIN` може теж створити, бере «Нове» в роботу (точка збору → бронь → видача);
`alkatras` (`OWNER_1`) заявки 3bat не бачить;
`ORDER_GATHERER` (`tyolki` / storage **10**) — своя нога на зборі (prepare).
Розклад «хто виготовляє / з яких складів» — кейси ВЗ, не цей тонкий шлях.
Staging/dev: `order.requester.unit.name=3bat`, `order.gathering.*=tyolki/10`,
`order.availability.root.storage.id=10`
(upsert `app_config` у `OrderFixture.ensureAvailabilityRootConfig` або
`scripts/seed_order_availability_root.sql`). `owner2` (`bar`/13) — лише regions/visibility.

---

## 5. API (контракт)

Base: `/api/v1/orders`

| Method | Path | Permission |
|--------|------|------------|
| GET | `/` | `order::read` на storageIds |
| GET | `/{id}` | access read (requester \| gathering) |
| POST | `/` | create |
| PUT | `/{id}` | update |
| PUT | `/{id}/cancel` | manage \| update |
| PUT | `/{id}/take-to-work` | manage |
| PUT | `/{id}/mark-done` | manage |
| GET | `/{id}/availability` | manage |
| GET | `/{id}/gathering-locations` | manage |
| PUT | `/{id}/gathering-storage` | manage |
| GET | `/{id}/bookings` | canSeeOrderBookings |
| POST | `/{id}/bookings` | manage |
| DELETE | `/{id}/bookings/{bookingId}` | manage |
| PUT | `/{id}/bookings/{bookingId}/prepared` | canGatherOrder |
| PUT | `/{id}/bookings/prepared` | canGatherOrder |
| GET/POST | `/{id}/comments` | access read |

Fulfill: `POST /api/v1/relocations/send` з `orderId` + `canFulfillOrder`.

Free stock gate: `storage.item.booked.insufficient` — «Недостатньо вільного залишку ресурсу {0} — заброньовано {1}».

---

## 6. Acceptance Criteria (TCM)

| AC | Суть |
|----|------|
| AC-01 | Create/Update + валідації (дубль, grant, FULL_ACCESS) |
| AC-02 | Lifecycle + cancel releases holds |
| AC-03 | List/Get + фільтри + gathering visibility |
| AC-04 | Comments |
| AC-05 | Availability + root scope |
| AC-06 | Gathering location |
| AC-07 | Booking / release |
| AC-08 | Prepare |
| AC-09 | Fulfill via relocation send |
| AC-10 | Free stock + write-off blocks |
| AC-11 | RBAC matrix |
| AC-12 | UI `/orders` + ship + badges |

---

## 7. Автотести

| ID | Клас |
|----|------|
| TC-ORD-001…014 | `OrderCrudApiTest` |
| TC-ORD-020…027 | `OrderLifecycleApiTest` |
| TC-ORD-030…037 | `OrderListApiTest` |
| TC-ORD-040…044 | `OrderCommentsApiTest` |
| TC-ORD-050…053 | `OrderAvailabilityApiTest` |
| TC-ORD-060…096 | `OrderBookingApiTest` |
| TC-ORD-100…104 | `OrderFreeStockApiTest` |
| TC-ORD-REG-* | `OrderStockRegressionApiTest` |
| TC-ORD-RBAC-* | `OrderRbacTest` / `rbac-policy.yml` |
| TC-ORD-UI-* | `OrderListUiTest`, `OrderCreateEditUiTest`, `OrderDetailUiTest`, `OrderBookingUiTest` |

Suites: `orders.xml`, `regression.xml`, `ui.xml`.

Fixture: `OrderFixture`. Endpoints: `ORDER_*` у `ApiEndpointDefinition`.

Related: `TC-STR-RES-026` (REQ-REGION-002) — grant-check через POST orders; не дублювати.

---

## 8. Ключові файли SUT (read-only)

**Backend `tk`:** `OrderController`, `BookingFacade`, `BookingService`, `OrderFulfillmentService`, `OrderValidator`, `TkAuthorization`, `StorageItemService.validateNotOverbooked`, V75 migration.

**Frontend `tk-ui`:** `OrderListPage`, `OrderDetailDialog`, `OrderBookingPanel`, `OrderGatheringCard`, `RelocationCreateOutputPage`, `FreeAmount`, `RelocationOrderBadge`.
