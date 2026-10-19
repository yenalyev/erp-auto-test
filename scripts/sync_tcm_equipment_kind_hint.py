#!/usr/bin/env python3
"""Idempotent documentation/case sync; credentials supplied via environment, never saved."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
core.PROJECT_ID = 1
core.FEATURE_ID = "REQ-EQU-HINT"
core.FEATURE_PARENT_ID = "REQ-EQU-002"
core.FEATURE_TITLE = "Обладнання — підказка при внесенні для основних засобів"
core.FEATURE_DESCRIPTION = "Інформаційна підказка у формі нового обладнання: погоджений 19.10.2026 текст виключень, приклади окулярів і ноутбука та контакт адміністратора у разі сумнівів. Детальна класифікація основних засобів/розхідників потребує окремого погодження."
core.FEATURE_MODULE = "EQU"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs/REQ-EQU-HINT.md"
core.ACCEPTANCE_CRITERIA = [
    ("AC-01", "Відповідальний за локацію та адміністратор бачать підказку до збереження; повний текст містить виключення засобів захисту, побутових засобів і пакування, приклади окулярів і ноутбука HP ZBook Power G7 та пораду звернутися до адміністратора Цукерочки."),
    ("AC-02", "Погоджений користувачем 19.10.2026 повний текст: «Засоби захисту та засоби побутового користування, пакування і т.д. не входять в обладнання»; «Приклад:»; «Захисні окуляри - Засоби захисту, не обладнання»; «Ноутбук HP ZBook Power G7 - Обладнання»; «У разі сумнівів або неясності зверніться до адміністратора Цукерочки.». Текст прийнятий без додаткового пояснення внесення в ресурси. Детальна категоризація Основні засоби/Розхідники — окреме відкрите питання."),
    ("AC-03", "Розгортання/згортання має коректний aria-expanded і клавіатурну активацію; не змінює введені дані та не надсилає запити запису. Підказки карток незалежні; нове відкриття форми відновлює початковий стан."),
    ("AC-04", "Повний текст читається без обрізання блоку при 1280/390 px. Скасування не створює обладнання; після перегляду підказки стандартне пакетне створення валідних одиниць працює (регресія TC-UI-EQ-002)."),
]

SPECS = [
    ("001", "AC-01", "Відповідальний бачить підказку до введення даних", "ownerSeesAdviceBeforeEnteringEquipment", "OWNER_1, локація OWNER_1; порожня форма.", [
        ("Відкрити /equipment/create у робочій локації.", "Видно «Нове обладнання», першу картку та правило виключення засобів захисту, побутових засобів і пакування."),
        ("Перевірити початковий стан підказки.", "Кнопка «Більше...» видима, aria-expanded=false; обидва приклади та контакт приховані.")]),
    ("002", "AC-01", "Адміністратор читає повні приклади та контакт", "adminCanReadExamplesAndContact", "ADMIN, локація OWNER_1; порожня форма.", [
        ("Відкрити форму і перевірити початкову згорнуту підказку.", "Видно правило та «Більше...», aria-expanded=false."),
        ("Натиснути «Більше...».", "Видно правило; «Захисні окуляри - Засоби захисту, не обладнання»; «Ноутбук HP ZBook Power G7 - Обладнання»; «У разі сумнівів або неясності зверніться до адміністратора Цукерочки.»; «Згорнути» має aria-expanded=true.")]),
    ("003", "AC-02", "Відповідальний читає погоджений текст підказки", "ownerReadsApprovedClassificationText", "OWNER_1; повний текст підказки, погоджений користувачем 19.10.2026.", [
        ("Відкрити форму, розгорнути підказку та прочитати весь текст.", "Присутні правило, приклади та контакт."),
        ("Звірити всі пункти списків і абзаци з погодженим текстом.", "Рівно три пункти: «Засоби захисту та засоби побутового користування, пакування і т.д. не входять в обладнання», «Захисні окуляри - Засоби захисту, не обладнання», «Ноутбук HP ZBook Power G7 - Обладнання». Абзаци: «Приклад:» та «У разі сумнівів або неясності зверніться до адміністратора Цукерочки.». Кнопка «Згорнути». Додаткове пояснення внесення в ресурси не вимагається.")]),
    ("004", "AC-03", "Повторні перемикання зберігають поля та не записують дані", "repeatedTogglingPreservesInputAndDoesNotSubmit", "OWNER_1; назва Ноутбук HP ZBook Power G7 — перевірка без збереження; інвентарний HINT-UNSAVED-001; Без серійного номера.", [
        ("Заповнити назву, інвентарний номер і відмітити відсутність серійного номера. Почати запис POST/PUT/PATCH/DELETE до equipment, resources та relocations API.", "Дані введено; форма не збережена. Телеметрія analytics/page-view не є записом обладнання."),
        ("Тричі розгорнути і згорнути підказку, перевіряючи текст та aria-expanded.", "Кожен перехід показує правильний стан."),
        ("Перевірити поля, кількість позицій, URL і мережеві записи.", "Назва, інвентарний номер та checkbox незмінні; позиція одна; URL /equipment/create; жодного запиту запису.")]),
    ("005", "AC-03", "Незалежні підказки при пакетному внесенні", "batchItemsHaveIndependentAdvice", "OWNER_1; дві порожні картки.", [
        ("Розгорнути підказку першої картки, додати ще одну позицію.", "Нова картка має власну згорнуту підказку."),
        ("За потреби відкрити першу картку; розгорнути обидві підказки.", "Обидві містять повний правильний текст. Після перемонтування картки стан її підказки дозволено скинути."),
        ("Згорнути першу підказку.", "Перша згорнута, друга залишається розгорнутою; кількість позицій 2.")]),
    ("006", "AC-03", "Клавіатурне керування підказкою", "keyboardActivatesBothAdviceControls", "OWNER_1; клавіатура Enter та Space.", [
        ("Встановити фокус на «Більше...» та натиснути Enter.", "Кнопка фокусується; підказка розгорнута, aria-expanded=true, повний текст видимий."),
        ("Встановити фокус на «Згорнути» та натиснути Space.", "Підказка згорнута, aria-expanded=false; запитів запису немає.")]),
    ("007", "AC-04", "Читабельність повного тексту на 1280 та 390 px", "expandedAdviceIsReadableAtDesktopAndNarrowViewport", "OWNER_1; Chromium; viewport 1280×844 та 390×844.", [
        ("Для кожного viewport розгорнути підказку.", "Правило, обидва приклади та контакт видимі."),
        ("Перевірити геометрію блоку та переповнення кожного текстового елемента; зробити скріншот.", "Блок у межах ширини viewport; scrollWidth не перевищує clientWidth більш ніж на 1 px; весь текст можна прочитати."),
        ("Згорнути підказку.", "Кнопка знову доступна; форма залишається придатною до роботи.")]),
    ("008", "AC-03", "Скасування без створення та повторне відкриття", "cancellationDoesNotCreateEquipmentAndReopenRestoresAdvice", "OWNER_1; порожня форма; підказка розгорнута.", [
        ("Розгорнути підказку, перевірити повний текст і натиснути «Скасувати».", "Відкрито /equipment; POST/PUT/PATCH/DELETE до API не надсилались."),
        ("Знову відкрити /equipment/create.", "Підказка повернулась у початковий згорнутий стан із правильним правилом.")]),
]

core.CASES = []
for suffix, ac, title, method, data, steps in SPECS:
    test_id = f"TC-EQ-HINT-UI-{suffix}"
    core.CASES.append(dict(
        testId=test_id, acKey=ac, title=title,
        description=f"EquipmentKindHintUiTest#{method}. План: docs/REQ-EQU-HINT.md. Перевіряє dev ERP; підказка інформаційна.",
        preconditions="Доступний dev ERP, чинні облікові записи ADMIN/OWNER_1 і локація OWNER_1; Chromium; use.database=false.",
        testData=data, steps=[dict(stepOrder=i, actionText=a, expectedText=e) for i, (a, e) in enumerate(steps, 1)],
        expectedResult=steps[-1][1], priority="HIGH" if suffix in ("001", "002", "003", "004") else "MEDIUM",
        severity="MAJOR" if suffix in ("001", "002", "003", "004") else "MINOR", status="ACTIVE", testType="UI",
        tags="equipment,classification,hint,automated,dev", apiAutomationIds=[], uiAutomationIds=[test_id],
    ))
core.CASES.append(dict(
    testId="TC-EQ-HINT-MAN-001", acKey="AC-02", title="Погодження розподілу Обладнання / Основні засоби / Розхідники",
    description="DRAFT: бізнес-уточнення. Не автоматизується до появи погоджених правил; відсутність рішення не є пройденим функціональним тестом.",
    preconditions="Доступні власник процесу та адміністратор; перелік спірних позицій. Правила класифікації ще не затверджені.",
    testData="Захисні окуляри; одноразові засоби захисту; побутові засоби; пакування; ноутбук HP ZBook Power G7.",
    steps=[dict(stepOrder=1, actionText="Для кожної позиції погодити тип обліку, точну категорію ресурсу та відповідального за внесення.", expectedText="Є письмове рішення власника процесу; жодна непогоджена категорія не видається за вимогу."),
           dict(stepOrder=2, actionText="Зафіксувати окремі правила категоризації та додаткові форми, якщо потрібні. Поточний текст підказки вже погоджений користувачем 19.10.2026.", expectedText="Окремі правила документовані; після погодження категоризації кейс можна деталізувати й активувати. Погодження категоризації не блокує прийняття поточного тексту підказки.")],
    expectedResult="Погоджені окремі правила категоризації зафіксовані; до цього кейс залишається DRAFT/NOT_RUN. Поточний текст підказки прийнятий.",
    priority="HIGH", severity="MAJOR", status="DRAFT", testType="FUNCTIONAL", tags="equipment,classification,needs-clarification,manual",
    apiAutomationIds=[], uiAutomationIds=[],
))

REGRESSION_PATCH = dict(
    testId="TC-UI-EQ-002", title="Create Several Equipment In One Relocation",
    description="CPMA-563: пакетне внесення двох одиниць від постачальника. Перед збереженням переглянути й згорнути підказку класифікації (REQ-EQU-HINT). Перевірити фактичну локацію у POST-відповіді, журнал та одне receive-переміщення EQUIPMENT. Автоматизація: EquipmentCreateUITest#createSeveralEquipmentInOneRelocation.",
    preconditions="ADMIN з доступом до власної дочірньої тестової локації; OWNER_1 перевіряється окремими TC-EQ-HINT-UI-001/003..008. Категорія обладнання та EXTERNAL/SUPPLIER постачальник існують. dev/staging.",
    steps=[
        dict(stepOrder=1, actionText="Відкрити /equipment/create під ADMIN з обраною ізольованою дочірньою локацією.", expectedText="Форма «Нове обладнання» з однією карткою позиції."),
        dict(stepOrder=2, actionText="Обрати постачальника, заповнити 2 позиції (унікальна назва, категорія, Без серійного номера). Розгорнути/згорнути підказку другої картки.", expectedText="Лічильник 2; підказка працює; введені позиції доступні для збереження."),
        dict(stepOrder=3, actionText="Натиснути «Зберегти всі 2», перевірити POST-відповідь, відкрити журнал і знайти обидві назви.", expectedText="POST успішний, рівно 2 одиниці на ізольованій локації; redirect /equipment; обидві назви видимі."),
        dict(stepOrder=4, actionText="Через API знайти отримання з обома одиницями; після перевірок видалити тільки підтверджене власне отримання і перевірити обидва ID.", expectedText="Одне переміщення типу EQUIPMENT містить рівно 2 очікувані одиниці; після cleanup GET кожного ID повертає 404, власна локація архівована."),
    ],
    expectedResult="Обидві одиниці збережені на правильній локації та видимі в журналі; одне отримання EQUIPMENT містить рівно ці 2 одиниці. Cleanup підтверджено GET by ID=404.",
)

def export_payload():
    return dict(featureId=core.FEATURE_ID, parentFeatureId=core.FEATURE_PARENT_ID, title=core.FEATURE_TITLE,
                description=core.FEATURE_DESCRIPTION, module=core.FEATURE_MODULE, priority=core.FEATURE_PRIORITY,
                documentation=core.DOCUMENTATION_PATH.read_text(encoding="utf-8"),
                acceptanceCriteria=core.ACCEPTANCE_CRITERIA, cases=core.CASES, regressionCasePatch=REGRESSION_PATCH)

def write_cases():
    lines = ["# Тест-кейси — підказка класифікації обладнання", "", "План: [REQ-EQU-HINT.md](REQ-EQU-HINT.md). 8 автоматизованих UI-кейсів; 1 ручний DRAFT. Регресія TC-UI-EQ-002 належить існуючій фічі REQ-EQU-002, також запускається у suite equipment-kind-hint.", ""]
    for c in core.CASES:
        lines += [f"## {c['testId']} — {c['title']}", "", f"{c['acKey']}; {c['status']}; {c['priority']}/{c['severity']}. {c['description']}", "", f"Передумови: {c['preconditions']}", "", f"Дані: {c['testData']}", ""]
        for step in c['steps']:
            lines += [f"{step['stepOrder']}. {step['actionText']} Очікування: {step['expectedText']}"]
        lines += ["", f"Підсумкове очікування: {c['expectedResult']}", ""]
    lines += ["## TC-UI-EQ-002 — регресія стандартного створення", "", "ACTIVE, існуючий кейс REQ-EQU-002 / AC-02. EquipmentCreateUITest#createSeveralEquipmentInOneRelocation.", "", "На власній дочірній локації заповнити дві одиниці з унікальними назвами, категорією, постачальником і без серійних номерів. Розгорнути/згорнути підказку другої картки, натиснути «Зберегти всі 2». Обидві назви видимі в журналі; API повертає одне переміщення EQUIPMENT із рівно цими двома одиницями. Cleanup прибирає власні отримання та локацію. Новий дублікат цього кейса в TCM не створюється.", ""]
    (ROOT / "docs/EQUIPMENT_KIND_HINT_TEST_CASES.md").write_text("\n".join(lines), encoding="utf-8")

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--export", type=Path)
    p.add_argument("--base-url")
    p.add_argument("--token-env", default="TCM_AI_TOKEN")
    p.add_argument("--report", type=Path)
    args = p.parse_args()
    write_cases()
    if args.export:
        args.export.parent.mkdir(parents=True, exist_ok=True)
        args.export.write_text(json.dumps(export_payload(), ensure_ascii=False, indent=2), encoding="utf-8")
    if args.base_url:
        if not args.report:
            p.error("--report is required for sync")
        client = core.TcmClient(args.base_url, os.environ[args.token_env], False)
        report = core.sync(client)
        checks = report["verification"]
        assert checks["documentationMatchesSource"] and checks["requiredAcPresent"] and checks["allCasesPresent"]
        for c in core.CASES:
            saved = checks['testCases'][c['testId']]
            assert saved['status'] == c['status'] and saved['uiAutomationIds'] == c['uiAutomationIds']
            assert saved['acId'] == c['acKey'] and saved['stepCount'] == len(c['steps'])
        regression_path = '/api/ai/projects/1/test-cases/TC-UI-EQ-002'
        before = client.request('GET', regression_path)
        assert before['featureId'] == 'REQ-EQU-002' and before['acId'] == 'AC-02'
        client.request('PUT', regression_path, REGRESSION_PATCH)
        after = client.request('GET', regression_path)
        assert after['featureId'] == before['featureId'] and after['uiAutomationIds'] == before['uiAutomationIds']
        assert after['steps'] == REGRESSION_PATCH['steps'] and after['preconditions'] == REGRESSION_PATCH['preconditions']
        report['regressionCaseUpdated'] = dict(testId=after['testId'], featureId=after['featureId'], acId=after['acId'], verified=True)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Verified {core.FEATURE_ID}: 4 AC, 8 automated + 1 DRAFT; {args.base_url}")

if __name__ == "__main__":
    main()
