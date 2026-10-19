#!/usr/bin/env python3
"""Merge the default-category requirement into existing TCM features and cases.

Uses the established TCM AI API client. Unrelated fields, criteria, cases and
automation links are preserved. Read-after-write verifies every managed field.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path

from sync_tcm_relocation_batch_accounting import TcmClient

ROOT = Path(__file__).resolve().parent.parent
BASE = "/api/ai/projects/1"
DEFAULT_DOC = ROOT / "docs/REQ-PLAN-ANL-DEFAULT-CATEGORY.md"
MARKER = "\n\n<!-- plan-analytics-default-category -->\n"


def steps(*pairs):
    return [dict(stepOrder=i, actionText=action, expectedText=expected)
            for i, (action, expected) in enumerate(pairs, 1)]


def case(test_id, feature, ac, title, method, actions, expected):
    cls = "PlanAnalyticsExcelExportUiTest" if "XLSX" in test_id else "PlanAnalyticsUiTest"
    return dict(testId=test_id, featureId=feature, acKey=ac, title=title,
                description=f"Автотест: com.erp.tests.ui.{cls}#{method}. "
                            "Контракт: docs/REQ-PLAN-ANL-DEFAULT-CATEGORY.md. Suites: ui, regression.",
                preconditions="Сесія ADMIN; у довіднику є категорія Боєприпас; доступна аналітика ERP dev.",
                testData="ID категорій із поточного довідника; попередній повний місяць; кількості не фіксовані.",
                status="ACTIVE", priority="HIGH", severity="MAJOR", testType="UI",
                tags="analytics,plan,default-category,automated", uiAutomationIds=[test_id],
                steps=actions, expectedResult=expected)


NEW_CASES = [
    case("TC-PLAN-ANL-UI-008", "REQ-PLAN-ANL", "AC-03",
         "Боєприпас вибрано за замовчуванням і rows завантажується автоматично",
         "defaultAmmunitionCategoryRequestsRowsAutomatically", steps(
             ("Знайти точну категорію Боєприпас у реальному довіднику.", "Є рівно один відповідний ID; його не зафіксовано в тесті."),
             ("Відкрити /analytics/plan без вибору фільтрів та дочекатися rows.", "HTTP 200; вибрано тільки Боєприпас; categoryIds містить тільки його ID; resourceIds відсутній."),
             ("Перевірити дати, категорію кожного рядка та відображення підсумків.", "Попередній повний місяць; сторонніх категорій немає; початковий порожній стан відсутній.")),
         "Дані категорії Боєприпас завантажуються без ручного вибору ресурсу чи категорії."),
    case("TC-PLAN-ANL-UI-009", "REQ-PLAN-ANL", "AC-03",
         "Початкову категорію можна очистити й замінити; reload/reopen відновлюють Боєприпас",
         "defaultCategoryCanBeClearedReplacedAndRestoredOnReopen", steps(
             ("Відкрити сторінку й очистити категорію Боєприпас.", "Категорій і ресурсів немає; видно порожній стан."),
             ("Змінити період на 3 місяці.", "Дефолт не повертається; rows не викликається."),
             ("Вибрати іншу категорію з довідника.", "Rows містить тільки її ID; Боєприпас не додано."),
             ("Оновити сторінку; потім очистити категорію й відкрити сторінку повторно.", "Обидва входи відновлюють тільки початкову категорію Боєприпас.")),
         "Ручний вибір діє до reload/reopen; очищення в поточній сесії не скасовується зміною періоду."),
    case("TC-PLAN-ANL-XLSX-UI-003", "REQ-PLAN-ANL-EXCEL", "AC-03",
         "Початковий Excel містить тільки вибірку категорії Боєприпас",
         "defaultAmmunitionExportMatchesAllFilteredRows", steps(
             ("Відкрити /analytics/plan без ручного вибору фільтрів.", "Вибрано тільки Боєприпас; rows передає один categoryIds."),
             ("Завантажити Excel та зібрати всі сторінки rows за його бізнес-фільтрами.", "Export і rows мають однакові бізнес-фільтри; файл містить усю вибірку."),
             ("Порівняти видимі рядки й підсумки UI, усі рядки та п'ять показників XLSX.", "Точна відповідність; підсумки за одиницями окремі.")),
         "Початковий експорт обмежений категорією Боєприпас і відповідає повному rows."),
]

CASE_UPDATES = {
    "TC-PLAN-ANL-UI-001": dict(steps=steps(
        ("Відкрити Аналітика → Для плану через сайдбар.", "Автоматично вибрана тільки категорія Боєприпас; порожній стан відсутній."),
        ("Перевірити попередній повний місяць, перемкнути 3 місяці та повернути місяць.", "Дати відповідають повним місяцям; категорія зберігається."),
        ("Явно очистити категорію та вибрати ресурс; перевірити показники й розгорнути рядок за наявності.", "Видно п'ять показників, таблицю й деталізацію залишків.")),
        expectedResult="Початковий вибір Боєприпас працює; періоди, підсумки й ручний вибір ресурсу доступні."),
    "TC-PLAN-ANL-UI-005": dict(steps=steps(
        ("Очистити початкову категорію, вибрати ресурс та постачальника; змінити період.", "Supplier зберігається в rows."),
        ("Очистити постачальника та вибрати його повторно.", "Після очищення параметр відсутній; після вибору знову застосовується."),
        ("Оновити сторінку; повторити вибір і відкрити сторінку заново.", "Supplier очищений; Боєприпас відновлено; rows завантажується без supplier.")),
        expectedResult="Reload/reopen відновлюють Боєприпас і очищують постачальника; зміна періоду зберігає поточний supplier."),
    "TC-PLAN-ANL-UI-006": dict(steps=steps(
        ("Відкрити сторінку та явно очистити початкову категорію Боєприпас без вибору ресурсу.", "Порожній стан; початковий запит не враховується як запит після очищення."),
        ("Вибрати постачальника без категорії та ресурсу.", "Нові rows-запити відсутні; порожній стан зберігається."),
        ("Вибрати ресурс.", "Новий rows-запит містить раніше вибраний supplier."))),
    "TC-PLAN-ANL-XLSX-UI-001": dict(steps=steps(
        ("Відкрити сторінку й явно очистити початкову категорію Боєприпас без вибору ресурсу.", "Порожній стан таблиці."),
        ("Перевірити кнопку експорту.", "Кнопка прихована."))),
}


def sync(client):
    report = {"baseUrl": client.base_url, "features": [], "acceptanceCriteria": [],
              "created": [], "updated": [], "verified": []}
    pending = []
    features = {}
    for feature_id in ("REQ-PLAN-ANL", "REQ-PLAN-ANL-SUPPLIER", "REQ-PLAN-ANL-EXCEL"):
        path = f"{BASE}/features/{feature_id}"
        current = client.request("GET", path)
        features[feature_id] = current
        if feature_id == "REQ-PLAN-ANL":
            documentation = (current.get("documentation") or "").split(MARKER)[0] + MARKER + DEFAULT_DOC.read_text(encoding="utf-8")
        else:
            doc = "REQ-PLAN-ANL-SUPPLIER-FILTER.md" if feature_id.endswith("SUPPLIER") else "REQ-PLAN-ANL-EXCEL.md"
            documentation = (ROOT / "docs" / doc).read_text(encoding="utf-8")
        pending.append((path, {"documentation": documentation}, "feature"))

    ac_text = {
        ("REQ-PLAN-ANL", "AC-01"): "API rows без resourceIds/categoryIds повертає порожні content і totals. Початково UI вибирає категорію Боєприпас і передає її ID явно. Для вибраних ресурсів доступні вироблено, відвантажено, використано, залишок усього та у Цукрарні. Накопичені обсяги за довший період не менші, ніж за його останній місяць; залишок у Цукрарні не більший за загальний. Без входу дані недоступні.",
        ("REQ-PLAN-ANL", "AC-03"): "При відкритті Аналітика → Для плану автоматично вибрана тільки категорія Боєприпас з актуального довідника й завантажуються таблиця та підсумки за попередній повний місяць. Перемикання на місяць/3 місяці зберігає категорії. Після явного очищення категорій і ресурсів показано порожній стан; зміна періоду не відновлює дефолт. Можна вибрати іншу категорію; reload/reopen відновлюють Боєприпас. Розгортання рядка показує залишки Цукрарні та інших локацій.",
        ("REQ-PLAN-ANL-SUPPLIER", "AC-03"): "Постачальник додатково звужує звіт за ресурсами, категоріями та періодом. Початково вибрано Боєприпас, тому supplier одразу фільтрує цю категорію. Після явного очищення всіх категорій і ресурсів сам supplier не запускає rows.",
    }
    for (feature_id, key), value in ac_text.items():
        ac = next(a for a in features[feature_id]["acceptanceCriteria"] if a["acId"] == key)
        pending.append((f"{BASE}/acceptance-criteria/{ac['id']}", {"text": value}, "ac"))

    updates = dict(CASE_UPDATES)
    for test_id in ("TC-PLAN-ANL-UI-003", "TC-PLAN-ANL-UI-004", "TC-PLAN-ANL-UI-007", "TC-PLAN-ANL-XLSX-UI-002"):
        current = client.request("GET", f"{BASE}/test-cases/{test_id}")
        actions = current.get("steps") or []
        prefix = "Явно очистити початкову категорію Боєприпас. "
        clean_steps = [dict(stepOrder=s.get("stepOrder", i), actionText=s["actionText"],
                            expectedText=s.get("expectedText") or "") for i, s in enumerate(actions, 1)]
        if not clean_steps and test_id == "TC-PLAN-ANL-UI-007":
            clean_steps = steps(
                ("Вибрати контрольовані ресурси двох постачальників; послідовно вибрати МОУ й Інші.",
                 "У rows два повторювані supplier; показані ресурси обох постачальників і звичні колонки."))
        if not clean_steps:
            raise RuntimeError(f"No existing steps for {test_id}; refusing to replace case")
        if not clean_steps[0]["actionText"].startswith(prefix):
            clean_steps[0]["actionText"] = prefix + clean_steps[0]["actionText"]
        updates[test_id] = {"steps": clean_steps}
    updates["TC-PLAN-ANL-001"] = {"description": "Прямий rows API без resourceIds/categoryIds залишається порожнім; UI початково вибирає Боєприпас і передає його ID явно. Автотест PlanAnalyticApiTest#rowsStayEmptyUntilResourceOrCategoryIsSelected; anonymous 401/403."}
    for test_id, payload in updates.items():
        path = f"{BASE}/test-cases/{test_id}"
        current = client.request("GET", path)
        pending.append((path, {"title": current["title"], **payload}, "case"))

    # Complete preflight reads before writing any remote data.
    new = [(payload, client.get_optional(f"{BASE}/test-cases/{payload['testId']}")) for payload in NEW_CASES]
    for path, payload, kind in pending:
        client.request("PUT", path, payload)
        current = client.request("GET", path)
        for key, value in payload.items():
            if key == "steps":
                actual = [{k: s.get(k) for k in ("stepOrder", "actionText", "expectedText")} for s in current[key]]
                assert actual == value, (path, key)
            else:
                assert current.get(key) == value, (path, key)
        report[{"feature": "features", "ac": "acceptanceCriteria", "case": "updated"}[kind]].append(path.rsplit("/", 1)[-1])
        report["verified"].append(path)
    for payload, existing in new:
        path = f"{BASE}/test-cases/{payload['testId']}"
        if existing:
            assert existing["featureId"] == payload["featureId"], (path, "feature conflict")
            client.request("PUT", path, payload)
        else:
            client.request("POST", f"{BASE}/test-cases", payload)
        current = client.request("GET", path)
        assert current["uiAutomationIds"] == payload["uiAutomationIds"]
        assert current["featureId"] == payload["featureId"] and current["acId"] == payload["acKey"]
        for key in ("title", "description", "expectedResult", "preconditions", "testData", "status", "priority", "severity", "testType", "tags"):
            assert current[key] == payload[key], (path, key)
        assert [{k: s.get(k) for k in ("stepOrder", "actionText", "expectedText")} for s in current["steps"]] == payload["steps"]
        report["updated" if existing else "created"].append(payload["testId"])
        report["verified"].append(path)
    report["sourceSha256"] = hashlib.sha256(DEFAULT_DOC.read_bytes()).hexdigest()
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    report = sync(TcmClient(args.base_url, os.environ[args.token_env]))
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "verified"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
