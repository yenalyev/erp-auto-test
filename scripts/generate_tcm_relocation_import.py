#!/usr/bin/env python3
"""
Generate TCM import XLSX for relocation test cases (resources + equipment).

Source: docs/REQ-RELOCATION-MAN.md + erp-auto-test suite relocations.xml
Feature/AC pairs validated against TCM export 2026-06-18.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from tcm_import_common import ROLE_ADMIN, ROLE_OWNER, Case, Step, write_xlsx

OUTPUT = Path(__file__).resolve().parent.parent / "docs" / "tcm-import-relocation-20260618.xlsx"

PRE_OWNER = (
    "@Owner 1 залогінений. Обрана конкретна локація (не «Всі локації»). "
    "На складі є ресурс із залишком ≥ 50 од. (або поповнити через «Отримати»)."
)
PRE_ADMIN = "@Admin залогінений. localStorage.selectedStorageId = склад Owner 1."
PRE_EQ = PRE_OWNER + " На складі є одиниця обладнання AVAILABLE для видачі."


def rel(
    test_id: str,
    feature_id: str,
    ac_id: str,
    title: str,
    description: str,
    *,
    priority: str = "HIGH",
    severity: str = "MAJOR",
    preconditions: str = PRE_OWNER,
    expected_result: str = "",
    tags: str = "relocations",
    role_name: str = ROLE_OWNER,
    layer: str = "API",
    steps: list[tuple[str, str]],
    cross: list[str] | None = None,
) -> Case:
    return Case(
        test_id=test_id,
        feature_id=feature_id,
        ac_id=ac_id,
        title=title,
        description=description,
        priority=priority,
        severity=severity,
        preconditions=preconditions,
        expected_result=expected_result,
        tags=tags,
        role_name=role_name,
        automation_layer=layer,
        automation_test_id=test_id,
        cross_features=cross or ["relocation"],
        steps=[Step(i + 1, a, e) for i, (a, e) in enumerate(steps)],
    )


def read_filter_cases() -> list[Case]:
    return [
        rel("TC-REL-001", "REQ-EDIT_REL", "AC-01", "Список переміщень за відправником",
            "Фільтрація журналу за складом-відправником.",
            priority="LOW", severity="MINOR", tags="relocations,api-regression",
            steps=[("GET /api/v1/relocations?senderIds={storageId}&size=10 або /relocations з обраною локацією",
                    "HTTP 200; лише записи з обраним відправником")]),
        rel("TC-REL-002", "REQ-EDIT_REL", "AC-01", "Список переміщень за отримувачем",
            "Фільтрація журналу за складом-отримувачем.",
            priority="LOW", severity="MINOR", tags="relocations,api-regression",
            steps=[("GET /api/v1/relocations?receiverIds={storageId}&size=10",
                    "HTTP 200; записи з обраним отримувачем")]),
        rel("TC-REL-003", "REQ-EDIT_REL", "AC-01", "Фільтр за категорією ресурсу",
            "Журнал фільтрується за categoryId.",
            priority="LOW", severity="MINOR", tags="relocations,api-regression",
            steps=[("Дізнатися categoryId тестового ресурсу",
                    "categoryId відомий"),
                   ("GET /api/v1/relocations?senderIds={storageId}&category={categoryId}",
                    "HTTP 200; лише переміщення з ресурсами цієї категорії")]),
        rel("TC-REL-004", "REQ-EDIT_REL", "AC-01", "Фільтр за productIds",
            "Журнал фільтрується за конкретним ресурсом.",
            priority="LOW", severity="MINOR", tags="relocations,api-regression",
            steps=[("GET /api/v1/relocations?senderIds={storageId}&productIds={resourceId}",
                    "HTTP 200; список містить переміщення з цим ресурсом")]),
        rel("TC-REL-005", "REQ-EDIT_REL", "AC-01", "Опції створення переміщення",
            "Довідник для форм видачі/отримання.",
            priority="LOW", severity="MINOR", tags="relocations,api-regression",
            steps=[("GET /api/v1/relocations/creation-options?storageId={storageId}",
                    "HTTP 200; постачальники, ресурси, одиниці виміру для форм")]),
        rel("TC-REL-006", "REQ-EDIT_REL", "AC-01", "Експорт журналу в Excel",
            "Експорт переміщень у файл.",
            priority="MEDIUM", severity="MINOR", tags="relocations,api-regression",
            steps=[("GET /api/v1/relocations/export?senderIds={storageId}",
                    "HTTP 200; Content-Type application/octet-stream; файл не порожній")]),
    ]


def send_cases() -> list[Case]:
    return [
        rel("TC-REL-010", "REQ-EDIT_REL", "AC-01", "Видача storage → storage",
            "Активне переміщення CREATED; залишок відправника −N, отримувач без змін до підтвердження.",
            priority="HIGH", severity="CRITICAL",
            expected_result="Статус CREATED; склад A −5 од.; склад B без змін.",
            tags="relocations,api-regression,smoke",
            steps=[("Зафіксувати залишок ресурсу на складі A", "Базове значення зафіксовано"),
                   ("«Видати» → отримувач склад B, ресурс, кількість 5", "Форма збережена; редирект у журнал"),
                   ("Вкладка «Активні» — новий запис", "Статус CREATED / «Активне»"),
                   ("Перевірити залишки", "Склад A: −5 од.; склад B: без змін")]),
        rel("TC-REL-011", "REQ-EDIT_REL", "AC-01", "Видача з явною партією",
            "Списання з named batch при видачі.",
            priority="HIGH", severity="MAJOR", cross=["relocation", "batch"],
            preconditions=PRE_OWNER + " На складі A є партія BATCH-001 з залишком ≥ 10 од.",
            steps=[("Зафіксувати залишок партії BATCH-001 на складі A", "Базове значення"),
                   ("«Видати» → партія BATCH-001, кількість 5, отримувач склад B", "Переміщення створено"),
                   ("Перевірити залишок партії на A", "Партія −5 од.; загальний залишок ресурсу −5")]),
        rel("TC-REL-012", "REQ-EDIT_REL", "AC-01", "Видача без партії (FIFO)",
            "Списання з найстаріших партій при видачі без вказання партії.",
            priority="MEDIUM", severity="MAJOR",
            steps=[("Поповнити склад кількома партіями", "Кілька партій на складі"),
                   ("Видати N од. без поля партії", "Загальний залишок −N; списання FIFO")]),
        rel("TC-REL-013", "REQ-EDIT_REL", "AC-01", "Видача на UNIT (AUTO_FINISHED)",
            "Видача на підрозділ одразу завершується.",
            priority="HIGH", severity="CRITICAL",
            expected_result="AUTO_FINISHED; залишок складу −10; UNIT без залишку ресурсу.",
            steps=[("Зафіксувати залишок на складі A", "Базове значення"),
                   ("«Видати» → отримувач UNIT, кількість 10", "Переміщення створено"),
                   ("Вкладка «Історія»", "Запис одразу завершений (AUTO_FINISHED)"),
                   ("Перевірити залишок складу A", "−10 од.")]),
        rel("TC-REL-014", "REQ-EDIT_REL", "AC-01", "Недостатній залишок при видачі",
            "Негатив: видача більше наявного залишку.",
            priority="HIGH", severity="MAJOR", tags="relocations,validation",
            steps=[("Зафіксувати поточний залишок R на складі A", "R відомий"),
                   ("Спробувати видати R + 1000 од.", "Помилка валідації; залишки без змін")]),
    ]


def receive_cases() -> list[Case]:
    return [
        rel("TC-REL-020", "REQ-EDIT_REL", "AC-03", "Отримання SUPPLIER → storage",
            "Зовнішнє отримання одразу AUTO_FINISHED; залишок +N.",
            priority="HIGH", severity="CRITICAL", cross=["relocation", "batch"],
            expected_result="Запис у «Історії»; статус AUTO_FINISHED; залишок +15 од.",
            steps=[("Зафіксувати залишок на складі A", "Базове значення"),
                   ("«Отримати» → постачальник, ресурс, 15 од., № партії, № накладної", "Форма збережена"),
                   ("Перевірити журнал", "Запис у «Історії» / «Отримано»; AUTO_FINISHED"),
                   ("Перевірити залишок на A", "+15 од.")]),
        rel("TC-REL-021", "REQ-EDIT_REL", "AC-03", "Отримання створює партію",
            "Нова партія з'являється в залишках після отримання.",
            priority="HIGH", severity="MAJOR", cross=["relocation", "batch"],
            steps=[("Вказати новий № партії при отриманні", "Отримання створено"),
                   ("Залишки → деталізація партій", "Нова партія з вказаною кількістю")]),
        rel("TC-REL-022", "REQ-EDIT_REL", "AC-03", "Отримання з internal storage (негатив)",
            "Receive лише від SUPPLIER.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            steps=[("POST /relocations/receive з senderId = інший склад (не SUPPLIER)", "HTTP 400; залишки без змін")]),
    ]


def resolve_cases() -> list[Case]:
    return [
        rel("TC-REL-030", "REQ-EDIT_REL", "AC-01", "Підтвердження CREATED → FINISHED",
            "Отримувач підтверджує активне переміщення.",
            priority="HIGH", severity="CRITICAL",
            preconditions=PRE_OWNER + " Є активне переміщення storage → storage на 5 од. (TC-REL-010).",
            expected_result="Статус FINISHED; залишок складу B +5 од.",
            steps=[("Увійти як отримувач (Owner з правами на склад B)", "Сесія отримувача"),
                   ("«Активні» → «Завершити» / підтвердити", "Статус FINISHED; запис у історії"),
                   ("Перевірити залишок складу B", "+5 од.")]),
        rel("TC-REL-031", "REQ-EDIT_REL", "AC-01", "Відхилення CANCELLED → RETURNED",
            "Повернення ресурсу відправнику після відхилення.",
            priority="HIGH", severity="MAJOR",
            steps=[("Створити видачу storage → storage (5 од.); зафіксувати залишок відправника", "CREATED створено"),
                   ("Отримувач: «Відхилити» → CANCELLED", "Статус CANCELLED"),
                   ("Відправник: «Повернути» → RETURNED", "Залишок відправника як до видачі")]),
        rel("TC-REL-032", "REQ-EDIT_REL", "AC-01", "Resolve у фінальному стані (негатив)",
            "Неможливо resolve AUTO_FINISHED запис.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            preconditions=PRE_OWNER + " Є AUTO_FINISHED запис (зовнішнє отримання).",
            steps=[("PUT /relocations/{id}/resolve?storageId=... зі станом FINISHED", "HTTP 400; залишки без змін")]),
    ]


def edit_send_cases() -> list[Case]:
    return [
        rel("TC-REL-040", "REQ-EDIT_REL", "AC-01", "Редагування видачі — зменшити кількість",
            "AUTO_FINISHED видача на UNIT: зменшення qty повертає різницю на склад.",
            priority="HIGH", severity="MAJOR",
            steps=[("Видати на UNIT 15 од.; зафіксувати залишок", "AUTO_FINISHED створено"),
                   ("«Редагувати» (видача) → змінити на 10 од.", "Залишок відправника +5 (повернення різниці)")]),
        rel("TC-REL-041", "REQ-EDIT_REL", "AC-01", "Редагування CREATED видачі (негатив)",
            "CREATED переміщення не редагується.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            preconditions=PRE_OWNER + " Є активне (CREATED) переміщення storage → storage.",
            steps=[("Спробувати редагувати видачу CREATED", "HTTP 400 або кнопка недоступна")]),
        rel("TC-REL-042", "REQ-EDIT_REL", "AC-01", "Збільшити кількість видачі без залишку (негатив)",
            "Редагування на більшу qty без достатнього залишку блокується.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            steps=[("AUTO_FINISHED видача на UNIT 5 од.; майже весь залишок списати", "Мінімальний залишок"),
                   ("Редагувати видачу на 9999 од.", "Помилка; залишки без змін")]),
        rel("TC-REL-043", "REQ-EDIT_REL", "AC-01", "Змінити отримувача при редагуванні видачі",
            "Зміна recipientId на інший склад.",
            priority="MEDIUM", severity="MAJOR",
            steps=[("Видача на UNIT 6 од.", "AUTO_FINISHED"),
                   ("Редагувати: змінити recipientId на інший склад", "HTTP 200; у картці новий отримувач")]),
        rel("TC-REL-044", "REQ-EDIT_REL", "AC-01", "Змінити відправника при редагуванні (негатив)",
            "senderId не можна змінити.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            steps=[("Спробувати змінити senderId при редагуванні видачі", "HTTP 400")]),
        rel("TC-REL-045", "REQ-EDIT_REL", "AC-01", "Поля осіб у накладній видачі",
            "ПІБ/звання відправника та отримувача зберігаються.",
            priority="LOW", severity="MINOR",
            steps=[("Редагувати AUTO_FINISHED видачу: ПІБ/звання відправника та отримувача", "Поля збережені у картці")]),
        rel("TC-REL-046", "REQ-EDIT_REL", "AC-01", "Редагування видачі без накладної",
            "Накладна не створюється автоматично при edit.",
            priority="LOW", severity="MINOR",
            steps=[("Редагувати видачу без попередньої накладної", "canGenerateInvoice ≠ true; накладна не створена")]),
    ]


def edit_receive_cases() -> list[Case]:
    return [
        rel("TC-REL-050", "REQ-EDIT_REL", "AC-04", "Редагування отримання — зменшити кількість",
            "Зменшення qty зовнішнього отримання зменшує залишок і партію.",
            priority="HIGH", severity="MAJOR", cross=["relocation", "batch"],
            preconditions=PRE_OWNER + " Зовнішнє отримання 15 од., партія B-050.",
            steps=[("«Редагувати» отримання → 10 од.", "Залишок складу −5; партія −5")]),
        rel("TC-REL-051", "REQ-EDIT_REL", "AC-04", "Змінити постачальника при редагуванні",
            "Admin змінює sender на іншого SUPPLIER.",
            priority="MEDIUM", severity="MAJOR", role_name=ROLE_ADMIN, preconditions=PRE_ADMIN,
            steps=[("Зовнішнє отримання від постачальника S1", "Запис існує"),
                   ("Admin: редагувати → постачальник S2", "HTTP 200; залишок без змін; новий sender у картці")]),
        rel("TC-REL-052", "REQ-EDIT_REL", "AC-04", "Змінити отримувача при редагуванні (негатив)",
            "recipientId не можна змінити.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            steps=[("Спробувати змінити recipientId при редагуванні отримання", "HTTP 400")]),
        rel("TC-REL-053", "REQ-EDIT_REL", "AC-04", "removeInvoiceFile при редагуванні",
            "Видалення файлу накладної через edit.",
            priority="LOW", severity="MINOR",
            steps=[("Отримання з файлом накладної", "hasExternalInvoicePhoto = true"),
                   ("Редагувати з прапорцем видалити файл накладної", "hasExternalInvoicePhoto = false")]),
        rel("TC-REL-056", "REQ-EDIT_REL", "AC-04", "Змінити постачальника (дубль TC-REL-051)",
            "Перевірка зміни senderId на іншого SUPPLIER.",
            priority="MEDIUM", severity="MAJOR", role_name=ROLE_ADMIN, preconditions=PRE_ADMIN,
            steps=[("Зовнішнє отримання від S1", "Запис існує"),
                   ("Admin: PUT receive з senderId = S2", "HTTP 200; залишок без змін")]),
    ]


def delete_cases() -> list[Case]:
    return [
        rel("TC-REL-060", "REQ-EDIT_REL", "AC-04", "Видалити AUTO_FINISHED receive",
            "Повний відкат залишку після видалення отримання.",
            priority="HIGH", severity="MAJOR", cross=["relocation", "batch"],
            steps=[("Зовнішнє отримання 10 од.", "AUTO_FINISHED"),
                   ("«Видалити» → підтвердити", "Запис зник; залишок −10")]),
        rel("TC-REL-061", "REQ-EDIT_REL", "AC-04", "Видалити CREATED (негатив)",
            "Активне переміщення не видаляється.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            preconditions=PRE_OWNER + " Є активне переміщення CREATED.",
            steps=[("Спробувати DELETE", "HTTP 400")]),
        rel("TC-REL-062", "REQ-EDIT_REL", "AC-04", "Видалити при недостатньому залишку (негатив)",
            "Видалення отримання блокується якщо товар уже видано.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,validation",
            steps=[("Отримати 10 од.; потім видати ці 10 од. зі складу", "Залишок 0"),
                   ("Спробувати видалити запис отримання", "HTTP 400; залишки без змін")]),
        rel("TC-REL-063", "REQ-EDIT_REL", "AC-04", "Видалити з чужим storageId (негатив)",
            "DELETE з невірним storageId → 403.",
            priority="MEDIUM", severity="MAJOR", tags="relocations,rbac",
            steps=[("DELETE /relocations/{id}?storageId={чужий_склад}", "HTTP 403")]),
    ]


def admin_cases() -> list[Case]:
    return [
        rel("TC-REL-054", "REQ-EDIT_REL", "AC-04", "Admin редагує amount і description",
            "Admin зменшує кількість зовнішнього отримання Owner 1.",
            priority="HIGH", severity="CRITICAL", role_name=ROLE_ADMIN, preconditions=PRE_ADMIN,
            cross=["relocation", "batch"],
            steps=[("Owner 1 створює зовнішнє отримання 20 од.", "Запис існує"),
                   ("Admin: «Редагувати» → 12 од., нова примітка", "HTTP 200"),
                   ("Перевірити залишок і партію", "−8 од. від загального залишку та партії")]),
        rel("TC-REL-055", "REQ-EDIT_REL", "AC-04", "Admin редагує поля накладної",
            "invoiceNumber, isPaidByCash, paidAmount без зміни залишку.",
            priority="MEDIUM", severity="MAJOR", role_name=ROLE_ADMIN, preconditions=PRE_ADMIN,
            steps=[("Admin редагує invoiceNumber, isPaidByCash, paidAmount", "Поля у картці; залишок без змін")]),
        rel("TC-REL-057", "REQ-EDIT_REL", "AC-04", "Admin видаляє зовнішнє отримання",
            "Повний відкат залишку та партії.",
            priority="HIGH", severity="CRITICAL", role_name=ROLE_ADMIN, preconditions=PRE_ADMIN,
            cross=["relocation", "batch"],
            steps=[("Owner 1: отримання 15 од.", "Запис існує"),
                   ("Admin: «Видалити» → підтвердити", "Повний відкат залишку та партії")]),
        rel("TC-REL-058", "REQ-EDIT_REL", "AC-04", "Edit потім delete — net zero",
            "Після create → edit → delete залишок = baseline.",
            priority="MEDIUM", severity="MAJOR", role_name=ROLE_ADMIN, preconditions=PRE_ADMIN,
            steps=[("Зафіксувати baseline залишку", "Baseline відомий"),
                   ("Отримання 20 → Admin edit 25 → Admin delete", "Залишок = baseline")]),
        rel("TC-REL-059", "REQ-EDIT_REL", "AC-04", "Owner 2 не може edit/delete (негатив)",
            "RBAC: чужий BU не має доступу до зовнішніх переміщень Owner 1.",
            priority="HIGH", severity="MAJOR", tags="relocations,rbac",
            preconditions="@Owner 2 залогінений. Owner 1 створив зовнішнє отримання.",
            steps=[("Owner 2: спроба PUT receive / DELETE", "HTTP 403; залишки без змін")]),
    ]


def batch_cases() -> list[Case]:
    specs = [
        ("TC-REL-B01", "AC-01", "Named batch + FIFO списання",
         [("Видати 10 од. з кількох партій без вказання партії", "Списання FIFO з найстаріших партій")]),
        ("TC-REL-B02", "AC-01", "isProduced=true після FINISHED",
         [("Підтвердити переміщення з isProduced=true", "Партія на отримувачі з isProduced")]),
        ("TC-REL-B03", "AC-01", "Два ресурси в одній видачі",
         [("Видати два різні ресурси в одному переміщенні", "Окреме списання по кожній лінії")]),
        ("TC-REL-B04", "AC-04", "Edit receive 15→10 — партія −5",
         [("Зовнішнє отримання 15 од., партія B-04", "Партія = 15"),
          ("Редагувати отримання → 10 од.", "Партія = 10; загальний залишок −5")]),
        ("TC-REL-B05", "AC-04", "Delete receive з партією",
         [("Зовнішнє отримання з партією", "Партія існує"),
          ("Видалити отримання", "Партія зникла / 0; залишок відкочено")]),
        ("TC-REL-B06", "AC-01", "Edit send — перерахунок партій",
         [("Редагувати AUTO_FINISHED видачу з партією", "Партії відправника перераховані")]),
        ("TC-REL-B07", "AC-01", "Resolve FINISHED — нова партія на отримувачі",
         [("Підтвердити CREATED → FINISHED", "Нова партія на складі отримувача")]),
    ]
    return [
        rel(tid, "REQ-EDIT_REL", ac, title, f"Партійний сценарій: {title}.",
            priority="MEDIUM", severity="MAJOR", cross=["relocation", "batch"],
            steps=steps)
        for tid, ac, title, steps in specs
    ]


def equipment_api_cases() -> list[Case]:
    def eq(tid, ac, title, desc, steps, **kw):
        kw.setdefault("tags", "relocations,equipment,api-regression")
        return rel(tid, "REQ-EQU-001", ac, title, desc,
                   preconditions=kw.pop("preconditions", PRE_EQ),
                   cross=["equipment", "relocation"], steps=steps, **kw)

    return [
        eq("TC-REL-EQ-001", "AC-01", "Видача обладнання storage→storage",
           "CREATED; equipment IN_TRANSIT.",
           [("POST send equipment storage → storage", "CREATED; equipment IN_TRANSIT")]),
        eq("TC-REL-EQ-002", "AC-01", "Видача обладнання на UNIT",
           "AUTO_FINISHED; equipment на UNIT.",
           [("POST send equipment → UNIT", "AUTO_FINISHED; equipment на UNIT")]),
        eq("TC-REL-EQ-003", "AC-02", "Resolve FINISHED — equipment AVAILABLE",
           "Після підтвердження equipment AVAILABLE у отримувача.",
           [("Видача equipment → resolve FINISHED", "equipment AVAILABLE на складі отримувача")]),
        eq("TC-REL-EQ-004", "AC-02", "CANCELLED → RETURNED для обладнання",
           "Equipment повернуто відправнику.",
           [("Видача → CANCELLED → RETURNED", "equipment повернуто відправнику")]),
        eq("TC-REL-EQ-005", "AC-02", "Sender CREATED→RETURNED shortcut",
           "Відправник може повернути без отримувача.",
           [("Відправник: resolve RETURNED з CREATED", "equipment повернуто")]),
        eq("TC-REL-EQ-006", "AC-02", "Recipient CREATED→RETURNED (негатив)",
           "Отримувач не може RETURNED напряму.",
           [("Отримувач: спроба CREATED→RETURNED", "HTTP 403")],
           priority="MEDIUM", tags="relocations,equipment,validation"),
        eq("TC-REL-EQ-007", "AC-01", "Видача RETIRED equipment (негатив)",
           "RETIRED equipment не видається.",
           [("Спроба видати RETIRED equipment", "HTTP 4xx")],
           priority="MEDIUM", tags="relocations,equipment,validation"),
        eq("TC-REL-EQ-008", "AC-01", "Equipment не на відправнику (негатив)",
           "Видача equipment з чужого складу блокується.",
           [("POST send з equipment не на відправнику", "HTTP 4xx")],
           priority="MEDIUM", tags="relocations,equipment,validation"),
        eq("TC-REL-EQ-009", "AC-04", "Delete supplier receive — equipment видалено",
           "Видалення початкового отримання видаляє equipment.",
           [("Отримання equipment від постачальника → DELETE", "Equipment видалено з системи")]),
        eq("TC-REL-EQ-010", "AC-04", "Delete при ASSIGNED (негатив)",
           "Не можна видалити receive якщо equipment ASSIGNED.",
           [("Спробувати видалити початкове отримання", "HTTP 400; equipment лишається")],
           priority="HIGH", tags="relocations,equipment,manual-check",
           preconditions=PRE_EQ + " Equipment ASSIGNED співробітнику."),
        eq("TC-REL-EQ-011", "AC-04", "Delete + історія assignment",
           "Видалення з історією assignment.",
           [("DELETE receive з історією assignment", "HTTP 200")]),
        eq("TC-REL-EQ-012", "AC-04", "Delete після подальшого transfer (негатив)",
           "Блокується якщо equipment вже переміщувалось.",
           [("Після transfer спроба DELETE початкового receive", "HTTP 400")],
           priority="MEDIUM", tags="relocations,equipment,validation"),
        eq("TC-REL-EQ-013", "AC-04", "Delete при IN_TRANSIT (негатив)",
           "Не можна видалити поки equipment IN_TRANSIT.",
           [("Спроба DELETE receive при IN_TRANSIT", "HTTP 400")],
           priority="MEDIUM", tags="relocations,equipment,validation"),
        eq("TC-REL-EQ-014", "AC-04", "Delete storage AUTO_FINISHED send",
           "Equipment повертається на відправника.",
           [("DELETE AUTO_FINISHED send storage→storage", "equipment на відправнику")]),
        eq("TC-REL-EQ-015", "AC-04", "Edit receive equipment (опис)",
           "Редагування опису отримання equipment.",
           [("PUT receive: змінити description", "HTTP 200")],
           priority="MEDIUM", severity="MINOR"),
        eq("TC-REL-EQ-016", "AC-04", "Edit removeInvoiceFile (equipment)",
           "Скидання прапорця файлу накладної.",
           [("Edit receive з removeInvoiceFile=true", "hasExternalInvoicePhoto = false")],
           priority="LOW", severity="MINOR"),
        eq("TC-REL-EQ-017", "AC-04", "Edit без removeInvoiceFile",
           "Файл накладної зберігається.",
           [("Edit receive без removeInvoiceFile", "Файл збережено")],
           priority="LOW", severity="MINOR"),
        eq("TC-REL-EQ-018", "AC-01", "Edit send person fields (equipment)",
           "Поля осіб без auto-invoice.",
           [("Edit send: ПІБ/звання", "Збережено; canGenerateInvoice ≠ true")],
           priority="LOW", severity="MINOR"),
    ]


def ui_resource_cases() -> list[Case]:
    specs = [
        ("TC-UI-REL-001", "AC-03", "Журнал + форма отримання + залишки", "CRITICAL",
         [("Owner 1 → /relocations, обрана локація", "Кнопки «Отримати», «Видати» доступні"),
          ("«Отримати» → повна форма (постачальник, ресурс, партія, накладна)", "Збережено"),
          ("Залишки: перевірити +N од. та партію", "Залишок і партія оновлені")]),
        ("TC-UI-REL-002", "AC-01", "Видача + підтвердження", "CRITICAL",
         [("Видати 6 од. storage → storage", "Запис у «Активні»"),
          ("Отримувач «Завершити»", "FINISHED"),
          ("Залишки: відправник −6, отримувач +6", "Залишки коректні")]),
        ("TC-UI-REL-003", "AC-01", "Видача на UNIT", "MAJOR",
         [("Видати на UNIT 4 од.", "Збережено"),
          ("«Історія» — AUTO_FINISHED", "Запис завершений"),
          ("Залишок відправника −4", "Коректно")]),
        ("TC-UI-REL-004", "AC-04", "Admin edit зовнішнього отримання", "CRITICAL",
         [("Owner 1: setup receive", "Запис існує"),
          ("Admin: «Історія» → «Отримано» → «Редагувати» → зменшити qty", "Збережено"),
          ("Залишки: delta відповідає зміні", "Коректно")]),
        ("TC-UI-REL-005", "AC-04", "Admin delete зовнішнього отримання", "CRITICAL",
         [("Setup receive", "Запис існує"),
          ("Admin: «Видалити» → діалог «Видалити»", "Підтверджено"),
          ("Повний відкат залишків", "Baseline відновлено")]),
        ("TC-UI-REL-006", "AC-01", "Відхилити → повернути", "MAJOR",
         [("Видача storage → storage", "CREATED"),
          ("Отримувач «Відхилити»; відправник «Повернути»", "RETURNED"),
          ("Залишок відправника відновлено", "Як до видачі")]),
        ("TC-UI-REL-007", "AC-01", "Редагування видачі на UNIT", "MAJOR",
         [("Видача на UNIT 8 од.", "AUTO_FINISHED"),
          ("«Редагувати» → 5 од. (update-output)", "Збережено"),
          ("Залишок +3 од. (повернення різниці 8−5)", "Коректна дельта")]),
        ("TC-UI-REL-008", "AC-01", "Видача з партією", "MAJOR",
         [("Видача з named batch 5 од.", "Збережено"),
          ("Партія на відправнику −5", "Коректно")]),
    ]
    cases = []
    for tid, ac, title, sev, steps in specs:
        role = ROLE_ADMIN if tid in ("TC-UI-REL-004", "TC-UI-REL-005") else ROLE_OWNER
        pre = PRE_ADMIN if role == ROLE_ADMIN else PRE_OWNER
        cross = ["relocation", "batch"] if "парті" in title.lower() or tid == "TC-UI-REL-001" else ["relocation"]
        cases.append(rel(
            tid, "REQ-EDIT_REL", ac, f"UI — {title}",
            f"Ручне проходження на /relocations: {title}.",
            priority="HIGH", severity=sev, preconditions=pre, role_name=role,
            layer="UI", tags="relocations,ui", cross=cross, steps=steps,
        ))
    return cases


def ui_equipment_cases() -> list[Case]:
    specs = [
        ("TC-UI-REL-EQ-001", "Журнал + видача обладнання",
         [("/relocations — кнопка «Видати» доступна", "Доступна"),
          ("(Опційно) Видача обладнання", "«Активні» — IN_TRANSIT")]),
        ("TC-UI-REL-EQ-002", "Resolve обладнання на UI",
         [("Видача equipment → отримувач підтверджує", "FINISHED"),
          ("Equipment AVAILABLE на складі отримувача; запис у «Історії»", "Коректно")]),
        ("TC-UI-REL-EQ-003", "Edit equipment receive — removeInvoiceFile",
         [("Отримання обладнання від постачальника", "Запис існує"),
          ("Редагування з removeInvoiceFile", "Прапорець файлу скинуто")]),
    ]
    return [
        rel(tid, "REQ-EQU-001", "AC-01", title,
            f"UI переміщення обладнання: {title}.",
            priority="MEDIUM" if tid != "TC-UI-REL-EQ-002" else "HIGH",
            severity="MAJOR" if tid != "TC-UI-REL-EQ-003" else "MINOR",
            preconditions=PRE_EQ, layer="UI", tags="relocations,equipment,ui",
            cross=["equipment", "relocation"], steps=steps)
        for tid, title, steps in specs
    ]


def all_relocation_cases() -> list[Case]:
    groups = [
        read_filter_cases, send_cases, receive_cases, resolve_cases,
        edit_send_cases, edit_receive_cases, delete_cases, admin_cases,
        batch_cases, equipment_api_cases, ui_resource_cases, ui_equipment_cases,
    ]
    cases: list[Case] = []
    for fn in groups:
        cases.extend(fn())
    seen: set[str] = set()
    unique: list[Case] = []
    for c in cases:
        if c.test_id in seen:
            continue
        seen.add(c.test_id)
        unique.append(c)
    return unique


if __name__ == "__main__":
    cases = all_relocation_cases()
    write_xlsx(cases, OUTPUT)
    print(f"Wrote {len(cases)} relocation test cases to {OUTPUT}")
