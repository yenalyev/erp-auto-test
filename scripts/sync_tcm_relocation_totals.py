#!/usr/bin/env python3
"""Idempotently publish the relocation totals plan and automated cases to a TCM AI API."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


PROJECT_ID = 1
FEATURE_ID = "REQ-REL-TOTALS"
ROOT = Path(__file__).resolve().parent.parent
DOCUMENTATION = ROOT / "docs" / "RELOCATION_TOTALS_TEST_PLAN.md"
FEATURE = {
    "featureId": FEATURE_ID,
    "title": "Підсумки «Всього» у Логістиці та Видати/Отримати",
    "description": "Сума відфільтрованих переміщень за одиницею виміру на /logistics і /relocations.",
    "module": "REL",
    "priority": "CRITICAL",
    "status": "ACTIVE",
}
CRITERIA = {
    "AC-01": "Підсумок ресурсу рахується за всією відфільтрованою вибіркою окремо для кожної одиниці виміру; різні одиниці не змішуються.",
    "AC-02": "Підсумок на /logistics і /relocations відображається для поточної вкладки та оновлюється після зміни фільтрів fromIds/toIds і статусів.",
    "AC-03": "Пошук ресурсу звужує підсумок до відповідних позицій; пагінація таблиці не змінює загальну суму.",
    "AC-04": "Користувач із глобальною системною роллю «Логістика: загальний перегляд» читає журнал і підсумки; користувач без глобального права не читає їх без дозволеної області.",
}
CASES = [
    {
        "testId": "TC-LOG-UI-012", "acKey": "AC-02", "testType": "UI",
        "title": "Логістика: «Всього» за одиницями після зміни вкладки та локацій",
        "description": "Перевірити інтеграцію /logistics із GET /relocations/totals на контрольованих відповідях.",
        "preconditions": "Відкрито /logistics під адміністратором; відповіді журналу й totals підмінено відомими даними з двома одиницями.",
        "expectedResult": "Окремі суми по одиницях відповідають активній або історичній вкладці та фільтрам fromIds/toIds.",
        "steps": [
            {"stepOrder": 1, "actionText": "Відкрити Логістику й перевірити підсумок вкладки «В дорозі».", "expectedText": "Показано окремі суми для двох одиниць виміру."},
            {"stepOrder": 2, "actionText": "Вибрати «Від кого» і «Кому», потім перейти до історії.", "expectedText": "GET /totals містить fromIds/toIds та статуси вкладки; суми перераховані за відповідними записами."},
        ],
        "uiAutomationIds": ["TC-LOG-UI-012"],
    },
    {
        "testId": "TC-LOG-UI-013", "acKey": "AC-02", "testType": "UI",
        "title": "Видати/Отримати: «Всього» на вкладках журналу",
        "description": "Перевірити підсумки /relocations на вкладках «В дорозі», «Отримано» й «Видано».",
        "preconditions": "Відкрито /relocations під адміністратором; відповіді журналу й totals підмінено відомими даними.",
        "expectedResult": "Кожна вкладка надсилає власні статуси; суми по одиницях відповідають її фільтру.",
        "steps": [
            {"stepOrder": 1, "actionText": "Відкрити «В дорозі», «Отримано», «Видано» по черзі.", "expectedText": "Після кожного переходу виконано GET /totals зі статусами поточної вкладки."},
            {"stepOrder": 2, "actionText": "Звірити чипи одиниць виміру.", "expectedText": "Для кожної одиниці показана її сума без змішування з іншими."},
        ],
        "uiAutomationIds": ["TC-LOG-UI-013"],
    },
    {
        "testId": "TC-REL-TOTAL-API-001", "acKey": "AC-01", "testType": "FUNCTIONAL",
        "title": "API totals: історія агрегується за одиницею та фільтром ресурсу",
        "description": "Три ізольовані надходження двох одиниць; перевірка точних сум, productIds, productName та порожнього результату.",
        "preconditions": "Створено тестові ресурси з двома одиницями виміру й надходження до ізольованої локації.",
        "expectedResult": "GET /relocations/totals повертає 10 і 4 у різних одиницях; товарні фільтри лишають тільки відповідний ресурс.",
        "steps": [
            {"stepOrder": 1, "actionText": "Запросити історичний підсумок для тестової локації.", "expectedText": "Отримано дві групи за одиницями з точними кількостями."},
            {"stepOrder": 2, "actionText": "Застосувати productIds, productName та назву без збігів.", "expectedText": "Кожна відповідь містить лише відповідні суми; без збігів список груп порожній."},
        ],
        "apiAutomationIds": ["TC-REL-TOTAL-API-001"],
    },
    {
        "testId": "TC-REL-TOTAL-API-002", "acKey": "AC-03", "testType": "FUNCTIONAL",
        "title": "API totals: активні переміщення охоплюють усі сторінки",
        "description": "Два ізольовані відправлення різних одиниць і сторінка журналу розміром один рядок.",
        "preconditions": "Створено два відправлення та окремі історичні надходження.",
        "expectedResult": "Підсумок активних записів дорівнює 4 і 1 за одиницями, хоча сторінка таблиці містить один запис.",
        "steps": [
            {"stepOrder": 1, "actionText": "Отримати totals для CREATED/CANCELLED із fromIds/toIds.", "expectedText": "Враховано два активні відправлення, історичні записи виключено."},
            {"stepOrder": 2, "actionText": "Отримати першу сторінку журналу з size=1 і повторити totals.", "expectedText": "У сторінці один рядок, але підсумок охоплює обидва записи і не змінюється."},
        ],
        "apiAutomationIds": ["TC-REL-TOTAL-API-002"],
    },
    {
        "testId": "TC-REL-TOTAL-API-003", "acKey": "AC-04", "testType": "SECURITY",
        "title": "API totals: глобальний перегляд для динамічного логіста",
        "description": "Тимчасовий бізнес актор LOGIST отримує системну роль «Логістика: загальний перегляд» з областю ALL.",
        "preconditions": "Фікстура створює користувача, призначає глобальну системну роль і прив'язує його сесію до DYNAMIC_GLOBAL_ACTOR.",
        "expectedResult": "Динамічний логіст читає глобальний журнал і totals (HTTP 200); звичайний власник без області отримує HTTP 403.",
        "steps": [
            {"stepOrder": 1, "actionText": "Запросити журнал і totals під динамічним логістом.", "expectedText": "Обидва запити повертають HTTP 200."},
            {"stepOrder": 2, "actionText": "Запросити totals під власником локації без глобальної області.", "expectedText": "Запит повертає HTTP 403; тимчасового користувача деактивовано після тесту."},
        ],
        "apiAutomationIds": ["TC-REL-TOTAL-API-003"],
    },
]


def request(base_url: str, token: str, method: str, path: str, body: dict | None = None):
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        base_url.rstrip("/") + path, data=payload, method=method,
        headers={"X-TCM-Ai-Token": token, "Accept": "application/json",
                 "Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path}: HTTP {error.code}: {detail}") from error


def optional(base_url: str, token: str, path: str):
    try:
        return request(base_url, token, "GET", path)
    except RuntimeError as error:
        if "HTTP 400" in str(error) or "HTTP 404" in str(error):
            return None
        raise


def sync(base_url: str, token: str):
    feature_path = f"/api/ai/projects/{PROJECT_ID}/features/{FEATURE_ID}"
    documentation = DOCUMENTATION.read_text(encoding="utf-8")
    feature = optional(base_url, token, feature_path)
    payload = {**FEATURE, "documentation": documentation}
    request(base_url, token, "POST" if feature is None else "PUT",
            f"/api/ai/projects/{PROJECT_ID}/features" if feature is None else feature_path,
            payload)
    current = request(base_url, token, "GET", feature_path)
    ac_by_key = {item["acId"]: item for item in current.get("acceptanceCriteria", [])}
    ac_ids = {}
    for key, text in CRITERIA.items():
        existing = ac_by_key.get(key)
        ac = request(base_url, token, "POST" if existing is None else "PUT",
                     f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria" if existing is None
                     else f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria/{existing['id']}",
                     {"featureId": FEATURE_ID, "acKey": key, "text": text})
        ac_ids[key] = ac["id"]
    for case in CASES:
        path = f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(case['testId'])}"
        existing = optional(base_url, token, path)
        payload = {
            **case, "featureId": FEATURE_ID,
            "acceptanceCriterionId": ac_ids[case["acKey"]],
            "priority": "CRITICAL" if case["acKey"] in {"AC-01", "AC-04"} else "HIGH",
            "severity": "MAJOR", "status": "ACTIVE", "tags": "relocation,totals,automated",
        }
        request(base_url, token, "POST" if existing is None else "PUT",
                f"/api/ai/projects/{PROJECT_ID}/test-cases" if existing is None else path,
                payload)
    current = request(base_url, token, "GET", feature_path)
    saved_doc = current.get("documentation") or ""
    results = {}
    for case in CASES:
        test_id = case["testId"]
        item = request(base_url, token, "GET",
                       f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id)}")
        results[test_id] = {
            "featureId": item.get("featureId"), "acId": item.get("acId"),
            "apiAutomationIds": item.get("apiAutomationIds") or [],
            "uiAutomationIds": item.get("uiAutomationIds") or [],
        }
    if saved_doc != documentation or set(ac_ids) - {
        item["acId"] for item in current.get("acceptanceCriteria", [])
    } or any(item["featureId"] != FEATURE_ID for item in results.values()):
        raise RuntimeError("TCM verification failed")
    return {
        "featureId": FEATURE_ID, "baseUrl": base_url.rstrip("/"),
        "documentationSha256": hashlib.sha256(saved_doc.encode("utf-8")).hexdigest(),
        "acceptanceCriteria": list(ac_ids), "testCases": results,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:18100")
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    token = os.getenv(args.token_env)
    if not token:
        print(f"Missing {args.token_env}", file=sys.stderr)
        return 2
    result = sync(args.base_url, token)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                               encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
