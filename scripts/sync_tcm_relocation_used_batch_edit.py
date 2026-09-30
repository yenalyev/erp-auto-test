#!/usr/bin/env python3
"""Upsert external-receipt edit cases after an explicitly selected batch was defected."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path

from sync_tcm_relocation_batch_accounting import TcmClient


PROJECT_ID = 1
FEATURE_ID = "REQ-EDIT_REL-010"
PARENT_ID = "REQ-EDIT_REL"
ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs" / "RELOCATION_USED_BATCH_EDIT_TEST_PLAN.md"

ACCEPTANCE_CRITERIA = [
    ("AC-01", "Зовнішнє отримання не можна зменшити нижче суми всіх явних списань його партії в брак незалежно від залишку інших партій ресурсу. Відхилений PUT не змінює отримання, партії, залишок або записи браку."),
    ("AC-02", "Кількість зовнішнього отримання можна зменшити до суми всіх явних списань його партії або збільшити. Залишок тієї самої партії дорівнює новій кількості мінус сума списань; UUID і записи браку збережені."),
    ("AC-03", "Після повного явного використання партії дозволено змінити реквізити отримання без зміни кількості та історії браку."),
    ("AC-04", "Після RELOCATION-браку від постачальника, прив'язаного до конкретного отримання, його кількість не можна зменшити нижче вже списаної кількості незалежно від залишку інших партій того самого ресурсу. Для 10 отриманих і 8 списаних редагування до 4 має відхилятися без змін даних."),
]


def case(number: int, ac: str, title: str, used: int, amount: int,
         expected: str, defect_amounts: tuple[int, ...] | None = None) -> dict:
    test_id = f"TC-REL-USED-{number:03d}"
    defect_amounts = defect_amounts or (used,)
    if sum(defect_amounts) != used:
        raise ValueError(f"{test_id}: defect amounts must sum to {used}")
    defect_list = " + ".join(str(value) for value in defect_amounts)
    return {
        "featureId": FEATURE_ID,
        "acKey": ac,
        "testId": test_id,
        "title": title,
        "description": "Редагування зовнішнього отримання після явного списання саме його партії в брак.",
        "priority": "CRITICAL" if number <= 2 else "HIGH",
        "severity": "CRITICAL" if number <= 2 else "MAJOR",
        "status": "ACTIVE",
        "testType": "FUNCTIONAL",
        "preconditions": (
            "Ізольований ресурс із залишком 0; зовнішнє отримання 10 од. в одну партію "
            "isProduced=true; STORAGE-брак явно вказує defectBatches.batchUuid саме цієї партії. "
            f"Списання за всіма записами: {defect_list} = {used} од. Серійне, несерійне і проєктне виробництво "
            "не є передумовою: явний вибір отриманої партії там наразі недосяжний."
        ),
        "expectedResult": expected,
        "tags": "relocation,external-receive,explicit-batch,defect,api,automated",
        "apiAutomationIds": [test_id],
        "uiAutomationIds": [],
        "steps": [
            {"stepOrder": 1, "actionText": "Створити зовнішнє отримання 10 од. та зафіксувати UUID його партії.",
             "expectedText": "Отримання і партію створено; залишок партії 10 од."},
            {"stepOrder": 2, "actionText": "Створити окремі STORAGE-браки на " + defect_list + " од. з явним defectBatches.batchUuid цієї партії; кожен збережений брак повторно прочитати через GET.",
             "expectedText": "Усі браки створені до редагування отримання; GET підтверджує UUID партії в кожному записі та суму списання " + str(used) + " од.; залишок партії " + str(10-used) + " од."},
            {"stepOrder": 3, "actionText": ("Після створення та перевірки браку змінити лише примітку, лишивши 10 од." if number == 5
                                          else f"Після створення та перевірки браку спробувати змінити кількість того самого отримання з 10 на {amount} од."),
             "expectedText": expected},
            {"stepOrder": 4, "actionText": "Повторно прочитати отримання, брак, залишок ресурсу та партії.",
             "expectedText": expected},
        ],
    }


CASES = [
    case(1, "AC-01", "Частковий брак: заборона зменшення нижче списаного", 4, 3,
         "HTTP 4xx; отримання 10, брак 4, залишок партії 6; UUID не змінено."),
    case(2, "AC-01", "Повний брак: заборона зменшення отримання", 10, 9,
         "HTTP 4xx; отримання 10, брак 10, залишок партії 0; UUID не змінено."),
    case(3, "AC-02", "Частковий брак: зменшення до списаної кількості", 4, 4,
         "PUT успішний; отримання 4, брак 4, залишок партії 0; UUID не змінено."),
    case(4, "AC-02", "Частковий брак: збільшення отримання", 4, 12,
         "PUT успішний; отримання 12, брак 4, залишок тієї самої партії 8."),
    case(5, "AC-03", "Повний брак: редагування примітки отримання", 10, 10,
         "PUT успішний; нова примітка збережена, брак 10 і залишок 0 без змін."),
    case(6, "AC-01", "Два браки: заборона зменшення нижче суми списань", 5, 4,
         "HTTP 4xx; отримання 10, браки 2 і 3, сумарне списання 5, залишок партії 5; UUID не змінено.",
         defect_amounts=(2, 3)),
    case(7, "AC-02", "Два браки: зменшення до суми списань", 5, 5,
         "PUT успішний; отримання 5, браки 2 і 3 збережені, залишок партії 0; UUID не змінено.",
         defect_amounts=(2, 3)),
]

CASES.append({
    "featureId": FEATURE_ID,
    "acKey": "AC-04",
    "testId": "TC-REL-USED-008",
    "title": "Брак від постачальника 8: заборона редагування отримання 10 до 4",
    "description": "Редагування того самого зовнішнього отримання після RELOCATION-браку за його relocationId.",
    "priority": "CRITICAL",
    "severity": "CRITICAL",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "preconditions": (
        "Ізольований ресурс; зовнішнє отримання від постачальника 10 од. в одну партію "
        "isProduced=false; уже створено RELOCATION-брак 8 од. з relocationId цього отримання; "
        "GET браку підтверджує тип, зв'язок та кількість; залишок партії 2 од."
    ),
    "expectedResult": "HTTP 4xx; отримання залишається 10, RELOCATION-брак 8, залишок ресурсу й партії 2.",
    "tags": "relocation,external-receive,supplier-defect,defect,api,automated",
    "apiAutomationIds": ["TC-REL-USED-008"],
    "uiAutomationIds": [],
    "steps": [
        {"stepOrder": 1, "actionText": "Створити зовнішнє отримання від постачальника 10 од. в одну партію.",
         "expectedText": "Отримання створене, залишок партії 10 од."},
        {"stepOrder": 2, "actionText": "Створити RELOCATION-брак 8 од. з relocationId цього отримання; повторно прочитати брак через GET.",
         "expectedText": "Збережений брак має тип RELOCATION, посилається на отримання та списує 8 од.; залишок партії 2 од."},
        {"stepOrder": 3, "actionText": "Після створення браку спробувати змінити кількість того самого отримання з 10 на 4 од.",
         "expectedText": "HTTP 4xx: нова кількість 4 менша за вже списані 8 од."},
        {"stepOrder": 4, "actionText": "Повторно прочитати отримання, брак, залишок ресурсу та партії.",
         "expectedText": "Отримання 10, брак 8 із тим самим relocationId, залишок ресурсу й партії 2 од."},
    ],
})

CASES.append({
    "featureId": FEATURE_ID,
    "acKey": "AC-04",
    "testId": "TC-REL-USED-009",
    "title": "Брак від постачальника 8: інша партія не може покривати редагування отримання до 4",
    "description": "Негативний регресійний кейс: dev 30.09.2026 повертає 200 і зберігає некоректне отримання 4 після браку 8.",
    "priority": "CRITICAL",
    "severity": "CRITICAL",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "preconditions": (
        "Ізольований ресурс; два зовнішні отримання по 10 од. в різні партії isProduced=false; "
        "RELOCATION-брак 8 од. має relocationId першого отримання; після браку залишки "
        "партій 2 і 10 од., загальний залишок 12 од."
    ),
    "expectedResult": (
        "PUT першого отримання 10→4 повертає 4xx; отримання 10, брак 8, "
        "залишки партій 2 і 10 та загальний залишок 12 не змінюються. "
        "На dev 30.09.2026 фактично 200, отримання 4, залишки партій 0 і 6."
    ),
    "tags": "relocation,external-receive,supplier-defect,multiple-batches,defect,api,known-bug,automated",
    "apiAutomationIds": ["TC-REL-USED-009"],
    "uiAutomationIds": [],
    "steps": [
        {"stepOrder": 1, "actionText": "Створити два зовнішні отримання того самого ресурсу по 10 од. в різні партії; зберегти ID першого.",
         "expectedText": "Залишки обох партій 10, загальний залишок 20 од."},
        {"stepOrder": 2, "actionText": "Створити RELOCATION-брак 8 од. за relocationId першого отримання й повторно прочитати його та залишки партій.",
         "expectedText": "Брак посилається на перше отримання; залишки партій 2 і 10, загалом 12 од."},
        {"stepOrder": 3, "actionText": "Після браку спробувати змінити перше отримання з 10 на 4 од. як адміністратор.",
         "expectedText": "PUT має повернути 4xx, оскільки пов'язаний брак 8 перевищує нову кількість 4."},
        {"stepOrder": 4, "actionText": "Повторно прочитати перше отримання, брак, обидві партії та загальний залишок.",
         "expectedText": "Дані мають лишитися 10 / 8 / 2 + 10 / 12. На dev дефект: 4 / 8 / 0 + 6 / 6."},
    ],
})

CASES.append({
    "featureId": FEATURE_ID,
    "acKey": "AC-01",
    "testId": "TC-REL-USED-010",
    "title": "Явний STORAGE-брак 8: інша партія не може покривати редагування отримання до 4",
    "description": "Регресійний кейс із UUID явно використаної партії; dev 30.09.2026 повертає 200 і зберігає некоректне отримання 4.",
    "priority": "CRITICAL",
    "severity": "CRITICAL",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "preconditions": (
        "Ізольований ресурс; два зовнішні отримання по 10 од. в різні партії isProduced=true; "
        "STORAGE-брак 8 од. явно вказує batchUuid першої партії; після браку залишки "
        "партій 2 і 10 од., загальний залишок 12 од."
    ),
    "expectedResult": (
        "PUT першого отримання 10→4 повертає 4xx; отримання 10, брак 8 з тим самим UUID, "
        "залишки партій 2 і 10 та загальний залишок 12 не змінюються. "
        "На dev 30.09.2026 фактично 200, отримання 4, залишки партій 0 і 6."
    ),
    "tags": "relocation,external-receive,explicit-batch,multiple-batches,storage-defect,api,known-bug,automated",
    "apiAutomationIds": ["TC-REL-USED-010"],
    "uiAutomationIds": [],
    "steps": [
        {"stepOrder": 1, "actionText": "Створити два зовнішні отримання того самого ресурсу по 10 од. в різні партії; зафіксувати UUID першої.",
         "expectedText": "Залишки обох партій 10, загальний залишок 20 од."},
        {"stepOrder": 2, "actionText": "Створити STORAGE-брак 8 од. з явним batchUuid першої партії; повторно прочитати брак і залишки.",
         "expectedText": "Брак містить UUID першої партії; залишки партій 2 і 10, загалом 12 од."},
        {"stepOrder": 3, "actionText": "Після браку спробувати змінити перше отримання з 10 на 4 од. як адміністратор.",
         "expectedText": "PUT має повернути 4xx, оскільки явний брак 8 перевищує нову кількість 4."},
        {"stepOrder": 4, "actionText": "Повторно прочитати перше отримання, брак, обидві партії та загальний залишок.",
         "expectedText": "Дані мають лишитися 10 / 8 / 2 + 10 / 12. На dev дефект: 4 / 8 / 0 + 6 / 6."},
    ],
})

CASES.append({
    "featureId": FEATURE_ID,
    "acKey": "AC-04",
    "testId": "TC-UI-REL-USED-008",
    "title": "Адмін UI: після браку від постачальника 8 форма приймає 4, сервер відхиляє",
    "description": "Форма дозволяє ввести 4 та підтвердити; PUT повертає 400 і збережені дані не змінюються.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "preconditions": (
        "Зовнішнє отримання від постачальника 10 од. в одну партію; "
        "збережений RELOCATION-брак 8 од. посилається на його relocationId; "
        "адміністратор вибрав цю локацію в UI."
    ),
    "expectedResult": (
        "Форма приймає 4 і кнопка «Підтвердити» активна; після натискання PUT повертає 400, "
        "UI показує помилку; отримання 10, брак 8 і залишок партії 2 збережено."
    ),
    "tags": "relocation,external-receive,supplier-defect,defect,ui,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": ["TC-UI-REL-USED-008"],
    "steps": [
        {"stepOrder": 1, "actionText": "Створити отримання 10 і RELOCATION-брак 8 за його relocationId; перевірити збережений брак через GET.",
         "expectedText": "Брак 8 створено до редагування; залишок партії 2."},
        {"stepOrder": 2, "actionText": "Адміністратором відкрити це отримання у журналі, ввести кількість 4 у форму редагування.",
         "expectedText": "Поле показує 4, кнопка «Підтвердити» активна."},
        {"stepOrder": 3, "actionText": "Натиснути «Підтвердити» і перевірити відповідь запиту PUT та повідомлення форми.",
         "expectedText": "PUT повертає 400; UI показує помилку про недостатній залишок ресурсу."},
        {"stepOrder": 4, "actionText": "Повторно прочитати отримання, брак, ресурс і партію.",
         "expectedText": "Отримання 10, брак 8 і залишок партії 2 не змінилися."},
    ],
})

CASES.append({
    "featureId": FEATURE_ID,
    "acKey": "AC-04",
    "testId": "TC-UI-REL-USED-009",
    "title": "Адмін UI: друга партія дозволяє некоректно зберегти отримання 4 після браку 8",
    "description": "UI-регресія для двох партій: очікується відмова, але dev повертає 200 і зберігає суперечливу історію.",
    "priority": "CRITICAL",
    "severity": "CRITICAL",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "preconditions": (
        "Два отримання того самого ресурсу по 10 од. в різні партії; RELOCATION-брак 8 од. "
        "пов'язаний із першим; залишки партій 2 і 10, загальний залишок 12; адміністратор відкрив локацію."
    ),
    "expectedResult": (
        "Після підтвердження зміни першого отримання 10→4 запит відхилено і дані незмінні. "
        "На dev 30.09.2026 UI надсилає PUT, отримує 200 і зберігає 4 при браку 8."
    ),
    "tags": "relocation,external-receive,supplier-defect,multiple-batches,defect,ui,known-bug,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": ["TC-UI-REL-USED-009"],
    "steps": [
        {"stepOrder": 1, "actionText": "Створити два отримання по 10 в різні партії; створити RELOCATION-брак 8 за ID першого; перевірити залишки 2 і 10.",
         "expectedText": "Брак збережений до редагування, загальний залишок 12 од."},
        {"stepOrder": 2, "actionText": "Адміністратором відкрити форму редагування першого отримання та ввести 4.",
         "expectedText": "Поле показує 4, кнопка підтвердження активна."},
        {"stepOrder": 3, "actionText": "Натиснути підтвердження і перевірити відповідь PUT.",
         "expectedText": "Очікується 4xx; dev 30.09.2026 фактично повертає 200."},
        {"stepOrder": 4, "actionText": "Повторно прочитати отримання, брак, обидві партії та загальний залишок.",
         "expectedText": "Очікується 10 / 8 / 2 + 10 / 12; dev фактично зберігає 4 / 8 / 0 + 6 / 6."},
    ],
})


def sync(client: TcmClient) -> dict:
    documentation = DOC.read_text(encoding="utf-8")
    feature_path = f"/api/ai/projects/{PROJECT_ID}/features/{FEATURE_ID}"
    feature_payload = {
        "featureId": FEATURE_ID, "parentFeatureId": PARENT_ID,
        "title": "Редагування зовнішнього отримання після явного використання партії",
        "description": "Валідація кількості й збереження зв'язку з партією після явного браку.",
        "documentation": documentation, "module": "EDIT_REL",
        "priority": "CRITICAL", "status": "ACTIVE",
    }
    report = {"baseUrl": client.base_url, "feature": None, "acceptanceCriteria": [],
              "testCases": [], "verification": {}}
    existing = client.get_optional(feature_path)
    if existing and existing.get("title") != feature_payload["title"]:
        raise RuntimeError(f"Feature ID collision at {FEATURE_ID}: existing title differs")
    client.request("PUT" if existing else "POST",
                   feature_path if existing else f"/api/ai/projects/{PROJECT_ID}/features",
                   feature_payload)
    report["feature"] = "updated" if existing else "created"
    feature = client.request("GET", feature_path)
    ac_by_key = {item["acId"]: item for item in feature.get("acceptanceCriteria", [])}
    ac_ids = {}
    for key, text in ACCEPTANCE_CRITERIA:
        previous = ac_by_key.get(key)
        if previous:
            saved = client.request("PUT", f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria/{previous['id']}",
                                   {"featureId": FEATURE_ID, "text": text})
        else:
            saved = client.request("POST", f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria",
                                   {"featureId": FEATURE_ID, "acKey": key, "text": text})
        ac_ids[key] = saved["id"]
        report["acceptanceCriteria"].append(key)
    for payload in CASES:
        test_id = payload["testId"]
        path = f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}"
        previous = client.get_optional(path)
        if previous and previous.get("featureId") != FEATURE_ID:
            raise RuntimeError(f"Test case ID collision at {test_id}: existing feature differs")
        body = dict(payload)
        body["acceptanceCriterionId"] = ac_ids[body["acKey"]]
        client.request("PUT" if previous else "POST",
                       path if previous else f"/api/ai/projects/{PROJECT_ID}/test-cases", body)
        report["testCases"].append({"testId": test_id, "action": "updated" if previous else "created"})
    feature_check = client.request("GET", feature_path)
    case_checks = {}
    for payload in CASES:
        test_id = payload["testId"]
        current = client.request("GET", f"/api/ai/projects/{PROJECT_ID}/test-cases/{test_id}")
        case_checks[test_id] = {
            "featureId": current.get("featureId"), "acId": current.get("acId"),
            "apiAutomationIds": current.get("apiAutomationIds") or [],
            "uiAutomationIds": current.get("uiAutomationIds") or [],
            "stepCount": len(current.get("steps") or []),
        }
    report["verification"] = {
        "parentFeatureId": feature_check.get("parentFeatureId"),
        "documentationMatchesSource": feature_check.get("documentation") == documentation,
        "documentationSha256": hashlib.sha256(documentation.encode()).hexdigest(),
        "acceptanceCriteriaPresent": set(ac_ids).issubset(
            {item["acId"] for item in feature_check.get("acceptanceCriteria", [])}),
        "cases": case_checks,
        "allCasesLinked": all(case_checks[payload["testId"]]["featureId"] == FEATURE_ID
                              and case_checks[payload["testId"]]["apiAutomationIds"]
                              == payload["apiAutomationIds"]
                              and case_checks[payload["testId"]]["uiAutomationIds"]
                              == payload["uiAutomationIds"]
                              and case_checks[payload["testId"]]["stepCount"] == 4
                              for payload in CASES),
    }
    if not (report["verification"]["documentationMatchesSource"]
            and report["verification"]["acceptanceCriteriaPresent"]
            and report["verification"]["allCasesLinked"]):
        raise RuntimeError("TCM read-back verification failed")
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--insecure", action="store_true")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    token = os.getenv(args.token_env)
    if not token:
        print(f"Missing token: {args.token_env}", file=sys.stderr)
        return 2
    result = sync(TcmClient(args.base_url, token, args.insecure))
    result["completedAt"] = datetime.now(timezone.utc).isoformat()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"baseUrl": result["baseUrl"], "feature": result["feature"],
                      "caseCount": len(result["testCases"]), "verified": True,
                      "report": str(args.report)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
