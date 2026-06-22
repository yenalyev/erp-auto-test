# Експорт для мануальної команди · 2026-06-22 · Глобальні плани (декомпозиція)

## REQ-GLOBAL-PLAN — Декомпозиція виробничого плану

**Маршрут UI:** `/global-plans`, `/global-plans/create`, `/global-plans/:id`  
**API:** `/api/v1/global-plans` (CRUD, decompose, generate, requirements/export)

> Імпорт у TCM: `python scripts/generate_tcm_global_plans_import.py` → `docs/tcm-import-global-plans-20260622.xlsx`

---

## Документація

| Розділ UI | Маршрут | Призначення |
|-----------|---------|-------------|
| Список | `/global-plans` | Перелік глобальних планів, створення |
| Wizard | `/global-plans/create`, `/global-plans/:id` | 4 вкладки декомпозиції |

**Ролі (Keycloak):**

| Дія | Permission |
|-----|------------|
| Sidebar | `global-plan::view` |
| Створити | `global-plan::create` |
| Редагувати | `global-plan::update` |
| Видалити | `global-plan::delete` |
| Генерація планів | `global-plan::generate` |

---

## Acceptance Criteria

| ID | Критерій |
|----|----------|
| AC-GP-01 | Один глобальний план на місяць |
| AC-GP-02 | Output — лише ресурси з активною PRODUCTION техкартою |
| AC-GP-03 | Зміна Tab1 скидає декомпозицію |
| AC-GP-04 | Одна карта+локація → auto-assign |
| AC-GP-05 | Кілька карт/локацій → діалог призначення |
| AC-GP-06 | «Розподілити по локаціях» → Tab3/4 |
| AC-GP-07 | Tab3: напівфабрикати + сировина + залишки |
| AC-GP-08 | Tab4: «Замінить наявний» при існуючому плані |
| AC-GP-09 | Генерація створює Plan per storage |
| AC-GP-10 | DELETE global plan не видаляє location plans |
| AC-GP-11 | Проміжні компоненти на локації споживання |
| AC-GP-12 | RBAC global-plan::* |

---

## Тестові дані (мануальні)

| Сутність | Значення |
|----------|----------|
| M1 | 2B+3x → 1A+1C @L1 |
| M2 | 2y+1C → 1B @L1+L2 |
| M3 | 1z → 1C @L1 |
| План | A = 10 од. |

---

## Автоматизація (erp-auto-test)

| Шар | Suite | TestCaseId |
|-----|-------|------------|
| API CRUD | `global-plans` | TC-GP-001 … TC-GP-008 |
| API Decompose | `global-plans` | TC-GP-020 … TC-GP-027 |
| API Generate | `global-plans` | TC-GP-040 … TC-GP-044 |
| RBAC | `rbac` | GLOBAL_PLAN_* |
| UI smoke | `ui` | TC-GP-UI-SMOKE-001 … 003 |

Запуск: `mvn test -Denv=dev -Dsuite=global-plans`

---

## Мануальні кейси (вибірка)

Див. TCM import. Повний перелік UI кейсів — у плані тестування (Tab 1–4, TC-GP-UI-001 … 043).
