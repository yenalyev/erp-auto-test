#!/usr/bin/env python3
"""Synchronize relocation batch accounting documentation and cases with TCM.

The sync is idempotent: the feature, acceptance criteria, and test cases are
upserted by their business identifiers. It does not publish ERP test results.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


PROJECT_ID = 1
FEATURE_ID = "REQ-EDIT_REL-008"
FEATURE_PARENT_ID = "REQ-EDIT_REL"
FEATURE_TITLE = "Бухгалтерська назва та вартість партії ресурсу"
FEATURE_DESCRIPTION = (
    "Опціональні бухгалтерська назва й повна вартість рядка під час зовнішнього "
    "отримання ресурсу, унікальність партії та загальна вартість форми"
)
FEATURE_MODULE = "EDIT_REL"
ROOT = Path(__file__).resolve().parent.parent
DOCUMENTATION_PATH = ROOT / "docs" / "RELOCATION_BATCH_ACCOUNTING_TEST_PLAN.md"


ACCEPTANCE_CRITERIA: list[tuple[str, str]] = [
    (
        "AC-01",
        "У кожному рядку ресурсу зовнішнього отримання доступні незалежні опціональні поля "
        "«Бухгалтерська назва» і «Сума». Значення зберігаються в партії; batchNumber "
        "залишається окремим атрибутом.",
    ),
    (
        "AC-02",
        "В одному переміщенні один ресурс можна додати кількома рядками лише з різними "
        "бухгалтерськими назвами. Ключ relocation + resource + trim(lower(accountingName)) "
        "унікальний; batchNumber його не змінює. Одна назва дозволена для різних ресурсів "
        "і в різних переміщеннях. UI показує помилку дубліката, API відхиляє його.",
    ),
    (
        "AC-03",
        "Сума є повною вартістю рядка, має бути не меншою за 0 і містити не більше двох "
        "знаків після коми. Це не ціна одиниці й вона не множиться на кількість.",
    ),
    (
        "AC-04",
        "Якщо заповнена хоча б одна сума, форма показує загальну вартість як суму всіх "
        "заповнених значень, включно з явно введеним 0,00. Якщо всі суми порожні, підсумок "
        "не показується.",
    ),
    (
        "AC-05",
        "Нові атрибути не є обов'язковими для legacy-сценаріїв і поки не відображаються "
        "в журналі, залишках, експорті чи накладній. Звичайне редагування інших даних не "
        "очищає збережені бухгалтерські атрибути.",
    ),
    (
        "AC-06",
        "Створення отримання є атомарним: помилка одного рядка не створює переміщення і "
        "не змінює залишки. Для валідних рядків фізичний залишок збільшується на суму їх "
        "кількостей незалежно від бухгалтерської суми.",
    ),
    (
        "AC-07",
        "Створювати зовнішнє отримання та бачити його бухгалтерські атрибути може лише "
        "користувач із відповідним правом на склад; прямий запит не обходить RBAC.",
    ),
]


def steps(action: str, expected: str) -> list[dict[str, Any]]:
    return [
        {"stepOrder": 1, "actionText": action, "expectedText": expected},
        {
            "stepOrder": 2,
            "actionText": "Перевірити відповідь API або стан форми, партії та складський залишок.",
            "expectedText": expected,
        },
    ]


def case(
    test_id: str,
    ac_key: str,
    title: str,
    action: str,
    expected: str,
    *,
    automated: bool = False,
    priority: str = "HIGH",
    severity: str = "MAJOR",
) -> dict[str, Any]:
    is_ui = "-UI-" in test_id or test_id == "TC-REL-ACC-020"
    return {
        "featureId": FEATURE_ID,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": f"Перевірка бухгалтерських атрибутів партії: {title}",
        "priority": priority,
        "severity": severity,
        "status": "ACTIVE",
        "testType": "UI" if is_ui else "FUNCTIONAL",
        "preconditions": (
            "Є активний склад-отримувач, зовнішній постачальник, користувач із правом "
            "створення отримання та ізольовані ресурси/бухгалтерські назви."
        ),
        "expectedResult": expected,
        "tags": "relocation,batch,accounting," + ("automated" if automated else "manual"),
        "apiAutomationIds": [test_id] if automated and not is_ui else [],
        "uiAutomationIds": [test_id] if automated and is_ui else [],
        "steps": steps(action, expected),
    }


CASES: list[dict[str, Any]] = [
    case(
        "TC-REL-ACC-001", "AC-01", "Один ресурс з бухгалтерською назвою та сумою",
        "Створити отримання ресурсу з бухгалтерською назвою, кількістю та сумою 1200,00.",
        "Отримання створене; accResourceId і paidAmount=1200,00 збережені в партії; залишок збільшений на кількість.",
        automated=True, priority="CRITICAL",
    ),
    case(
        "TC-REL-ACC-002", "AC-02", "Один ресурс із двома різними бухгалтерськими назвами",
        "В одному отриманні додати той самий ресурс двома рядками з різними бухгалтерськими назвами.",
        "Створено дві окремі партії; обидві назви й суми збережені; залишок дорівнює сумі кількостей.",
        automated=True, priority="CRITICAL",
    ),
    case(
        "TC-REL-ACC-003", "AC-02", "Дублікат ресурсу та бухгалтерської назви",
        "Додати два рядки одного ресурсу з однаковою бухгалтерською назвою.",
        "UI блокує підтвердження, API повертає контрольовану validation-помилку; переміщення і залишок не змінені.",
        automated=True, priority="CRITICAL", severity="CRITICAL",
    ),
    case(
        "TC-REL-ACC-004", "AC-02", "Одна бухгалтерська назва для різних ресурсів",
        "Створити рядки двох різних ресурсів з однаковою бухгалтерською назвою.",
        "Обидва рядки прийняті й збережені, бо resourceId є частиною унікального ключа.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-005", "AC-02", "Одна комбінація в різних переміщеннях",
        "Створити два окремі отримання з однаковими ресурсом і бухгалтерською назвою.",
        "Обидва переміщення створені, бо унікальність обмежена одним relocation.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-006", "AC-01", "Опціональні бухгалтерські поля",
        "Окремо створити отримання без нових полів, лише з назвою, лише із сумою та з обома полями.",
        "Усі чотири комбінації прийняті; незаповнені поля збережені як null/відсутні без підміни значень.",
        automated=True, priority="CRITICAL",
    ),
    case(
        "TC-REL-ACC-007", "AC-02", "Порожня бухгалтерська назва в унікальному ключі",
        "Додати два рядки одного ресурсу без назви, а також варіанти null, відсутнього поля й пробілів.",
        "Порожні значення нормалізуються однаково; дубль відхилено без часткового створення.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-008", "AC-06", "Видалення отримання з кількома бухгалтерськими рядками",
        "Створити та видалити отримання з двома бухгалтерськими партіями одного ресурсу.",
        "Обидві кількості коректно відкочені; сторонні партії та залишки не змінені.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-009", "AC-06", "Атомарність multi-resource запиту",
        "Надіслати отримання з валідним рядком і рядком із невалідною сумою або дубльованим ключем.",
        "Усе отримання відхилено; жодної партії та зміни залишку не створено.",
        automated=True, priority="CRITICAL", severity="CRITICAL",
    ),
    case(
        "TC-REL-ACC-010", "AC-03", "Сума є повною вартістю, а не ціною одиниці",
        "Створити рядок кількістю 10 і сумою 1200,00.",
        "Збережено 1200,00; система не множить суму на кількість і не зберігає 12000,00.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-011", "AC-01", "Null, відсутнє поле і пробіли",
        "Передати null, не передавати поле та ввести назву з крайніми пробілами.",
        "Опціональні значення оброблені без помилки; назва нормалізується для унікальності без зміни інших символів.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-012", "AC-03", "Межі суми",
        "Перевірити 0,00, 0,10, від'ємне значення, три десяткові знаки та велике додатне значення.",
        "Нуль і валідні двозначні суми прийняті; від'ємні та значення з понад двома знаками відхилені HTTP 400 без зміни залишку.",
        automated=True, priority="CRITICAL", severity="CRITICAL",
    ),
    case(
        "TC-REL-ACC-013", "AC-01", "Межі бухгалтерської назви",
        "Перевірити довгу назву, кирилицю, латиницю, цифри, пробіли та спеціальні символи.",
        "Назви прийняті без штучного бізнес-обмеження; значення не пошкоджене.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-014", "AC-02", "Нормалізація унікальності",
        "Додати один ресурс із назвами, які відрізняються лише регістром або крайніми пробілами; повторити з різними batchNumber.",
        "Комбінації визнані дублікатами й відхилені незалежно від batchNumber.",
        automated=True, priority="CRITICAL", severity="CRITICAL",
    ),
    case(
        "TC-REL-ACC-015", "AC-06", "Узгодженість item amount і batches",
        "Створити отримання з кількома партіями та перевірити суму batch.amount проти item.amount.",
        "Неконсистентний payload відхилено; валідний залишок дорівнює сумі кількостей партій.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-016", "AC-05", "Збереження атрибутів після звичайного редагування",
        "Створити бухгалтерську партію, прочитати relocation, змінити небухгалтерське поле й прочитати повторно.",
        "Бухгалтерські назва та сума лишилися без змін і не стали доступними для неузгодженого редагування.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-017", "AC-07", "Права доступу до бухгалтерських атрибутів",
        "Спробувати створити отримання та прочитати його з дозволеної й сторонньої локації.",
        "Дозволений користувач виконує операцію; сторонній запит відхилено без розкриття атрибутів і партій.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-UI-001", "AC-01", "Поля бухгалтерської назви та суми у формі",
        "Відкрити форму зовнішнього отримання та вибрати ресурс.",
        "У рядку доступні опціональні поля бухгалтерської назви й суми; сума має min=0 та крок 0,01.",
        automated=True, priority="CRITICAL",
    ),
    case(
        "TC-REL-ACC-UI-002", "AC-02", "Другий рядок того самого ресурсу",
        "Додати той самий ресурс другим рядком, вибрати іншу бухгалтерську назву та заповнити суму.",
        "Обидва рядки доступні для введення і можуть бути підтверджені з різними назвами.",
        automated=True, priority="CRITICAL",
    ),
    case(
        "TC-REL-ACC-UI-003", "AC-02", "Збереження введених значень після помилки дубліката",
        "У двох рядках одного ресурсу вибрати однакову назву, отримати помилку й виправити другий рядок.",
        "UI показує помилку і блокує submit; інші значення не губляться; після виправлення форма валідна.",
        automated=True, priority="CRITICAL",
    ),
    case(
        "TC-REL-ACC-UI-004", "AC-04", "Загальна вартість форми",
        "Перевірити форму з порожніми сумами, явним 0,00 і кількома додатними сумами.",
        "Без сум підсумок прихований; для 0,00 і додатних значень показано коректну суму з двома десятковими знаками.",
        automated=True, priority="CRITICAL",
    ),
    case(
        "TC-REL-ACC-018", "AC-05", "Legacy-партія без бухгалтерських атрибутів",
        "Виконати чинні сценарії отримання, видачі, повернення й виробництва без нових полів.",
        "Існуючі операції працюють без обов'язкового заповнення бухгалтерських атрибутів.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-019", "AC-05", "Журнал, експорт, накладна та інвентаризація",
        "Створити кілька бухгалтерських партій і перевірити чинні downstream-представлення.",
        "Нові поля там не відображаються; наявні кількості не дублюються і не спотворюються.",
        automated=True,
    ),
    case(
        "TC-REL-ACC-020", "AC-03", "Локалізація та формат суми",
        "Ввести допустиму суму й перевірити українські підписи, помилки та відображення значення.",
        "Підписи й помилки зрозумілі; сума відображається з двома десятковими знаками без зміни числового значення.",
        automated=True,
    ),
]


@dataclass
class TcmClient:
    base_url: str
    token: str
    insecure: bool = False

    def __post_init__(self) -> None:
        self.base_url = self.base_url.rstrip("/")
        self.context = ssl._create_unverified_context() if self.insecure else None

    def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            method=method,
            headers={
                "X-TCM-Ai-Token": self.token,
                "Accept": "application/json",
                "Content-Type": "application/json; charset=utf-8",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=90, context=self.context) as response:
                raw = response.read().decode("utf-8")
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} -> HTTP {exc.code}: {detail}") from exc

    def get_optional(self, path: str) -> Any | None:
        try:
            return self.request("GET", path)
        except RuntimeError as exc:
            if "HTTP 400" in str(exc) or "HTTP 404" in str(exc):
                return None
            raise


def sync(client: TcmClient) -> dict[str, Any]:
    documentation = DOCUMENTATION_PATH.read_text(encoding="utf-8")
    feature_payload = {
        "featureId": FEATURE_ID,
        "parentFeatureId": FEATURE_PARENT_ID,
        "title": FEATURE_TITLE,
        "description": FEATURE_DESCRIPTION,
        "documentation": documentation,
        "module": FEATURE_MODULE,
        "priority": "CRITICAL",
        "status": "ACTIVE",
    }
    report: dict[str, Any] = {
        "baseUrl": client.base_url,
        "feature": {"created": False, "updated": False},
        "acceptanceCriteria": {"created": [], "updated": []},
        "testCases": {"created": [], "updated": []},
        "verification": {},
    }

    feature_path = f"/api/ai/projects/{PROJECT_ID}/features/{FEATURE_ID}"
    existing_feature = client.get_optional(feature_path)
    if existing_feature is None:
        client.request("POST", f"/api/ai/projects/{PROJECT_ID}/features", feature_payload)
        report["feature"]["created"] = True
    else:
        client.request("PUT", feature_path, feature_payload)
        report["feature"]["updated"] = True

    feature = client.request("GET", feature_path)
    existing_by_key = {item["acId"]: item for item in feature.get("acceptanceCriteria", [])}
    ac_ids: dict[str, int] = {}
    for ac_key, text in ACCEPTANCE_CRITERIA:
        existing = existing_by_key.get(ac_key)
        if existing:
            updated = client.request(
                "PUT",
                f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria/{existing['id']}",
                {"featureId": FEATURE_ID, "text": text},
            )
            report["acceptanceCriteria"]["updated"].append(ac_key)
        else:
            updated = client.request(
                "POST",
                f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria",
                {"featureId": FEATURE_ID, "acKey": ac_key, "text": text},
            )
            report["acceptanceCriteria"]["created"].append(ac_key)
        ac_ids[ac_key] = int(updated["id"])

    for payload in CASES:
        test_id = payload["testId"]
        path = f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}"
        existing = client.get_optional(path)
        write_payload = dict(payload)
        write_payload["acceptanceCriterionId"] = ac_ids[payload["acKey"]]
        if existing is None:
            client.request("POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", write_payload)
            report["testCases"]["created"].append(test_id)
        else:
            client.request("PUT", path, write_payload)
            report["testCases"]["updated"].append(test_id)

    feature_check = client.request("GET", feature_path)
    actual_ac = {item["acId"] for item in feature_check.get("acceptanceCriteria", [])}
    case_checks: dict[str, Any] = {}
    for payload in CASES:
        test_id = payload["testId"]
        current = client.request(
            "GET",
            f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}",
        )
        case_checks[test_id] = {
            "featureId": current["featureId"],
            "acId": current["acId"],
            "status": current["status"],
            "apiAutomationIds": current.get("apiAutomationIds") or [],
            "uiAutomationIds": current.get("uiAutomationIds") or [],
            "stepCount": len(current.get("steps") or []),
        }

    required_ac = {key for key, _ in ACCEPTANCE_CRITERIA}
    report["verification"] = {
        "featureId": feature_check["featureId"],
        "parentFeatureId": feature_check.get("parentFeatureId"),
        "status": feature_check.get("status"),
        "documentationLength": len(feature_check.get("documentation") or ""),
        "documentationSha256": hashlib.sha256(
            (feature_check.get("documentation") or "").encode("utf-8")
        ).hexdigest(),
        "sourceDocumentationSha256": hashlib.sha256(documentation.encode("utf-8")).hexdigest(),
        "requiredAcPresent": required_ac.issubset(actual_ac),
        "managedAcCount": len(required_ac),
        "managedCaseCount": len(CASES),
        "automatedCaseCount": sum(
            bool(item["apiAutomationIds"] or item["uiAutomationIds"]) for item in CASES
        ),
        "manualCaseCount": sum(
            not bool(item["apiAutomationIds"] or item["uiAutomationIds"]) for item in CASES
        ),
        "allCasesPresent": len(case_checks) == len(CASES),
        "documentationMatchesSource": (
            feature_check.get("documentation") or ""
        ) == documentation,
        "testCases": case_checks,
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--token", help=argparse.SUPPRESS)
    parser.add_argument("--insecure", action="store_true", help="Allow an internal certificate")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    token = args.token or os.getenv(args.token_env)
    if not token:
        print(f"Missing token: set {args.token_env}", file=sys.stderr)
        return 2

    report = sync(TcmClient(args.base_url, token, args.insecure))
    report["completedAt"] = datetime.now(timezone.utc).isoformat()
    report["result"] = "SUCCESS"
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({
        "result": report["result"],
        "baseUrl": report["baseUrl"],
        "feature": report["feature"],
        "acceptanceCriteria": {
            key: len(value) for key, value in report["acceptanceCriteria"].items()
        },
        "testCases": {key: len(value) for key, value in report["testCases"].items()},
        "verification": {
            key: report["verification"][key]
            for key in (
                "requiredAcPresent",
                "managedAcCount",
                "managedCaseCount",
                "automatedCaseCount",
                "manualCaseCount",
                "allCasesPresent",
                "documentationMatchesSource",
            )
        },
        "report": str(args.report),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
