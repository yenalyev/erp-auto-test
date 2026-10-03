#!/usr/bin/env python3
"""Synchronize TCM cases changed by the isolated regression fixture fixes.

The script updates existing cases in place and preserves their feature, acceptance
criterion, priority, severity, status, and execution result. It does not publish
test run results.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from sync_tcm_relocation_batch_accounting import TcmClient


PROJECT_ID = 1
ROOT = Path(__file__).resolve().parent.parent


def steps(*items: tuple[str, str]) -> list[dict[str, Any]]:
    return [
        {"stepOrder": index, "actionText": action, "expectedText": expected}
        for index, (action, expected) in enumerate(items, 1)
    ]


PATCHES: dict[str, dict[str, Any]] = {
    "TC-ORD-006": {
        "description": (
            "Admin із FULL_ACCESS створює замовлення з окремим активним ресурсом каталогу, "
            "який динамічно створений для тесту і не має grant на локації замовника."
        ),
        "preconditions": (
            "Динамічно створені локація-замовник і активний ресурс каталогу без grant на цій "
            "локації; Admin має FULL_ACCESS."
        ),
        "expectedResult": "POST /orders повертає 200; замовлення містить динамічний ресурс.",
        "tags": "orders,req-wms-010,automated,dynamic-fixture",
        "apiAutomationIds": ["TC-ORD-006"],
        "steps": steps(
            (
                "Створити унікальний активний ресурс каталогу без grant на динамічній локації-замовнику.",
                "Ресурс існує в каталозі й не входить до набору доступних ресурсів локації.",
            ),
            (
                "Під Admin створити замовлення на цій локації з новим ресурсом.",
                "HTTP 200; створене замовлення містить ідентифікатор нового ресурсу.",
            ),
            (
                "Скасувати замовлення та деактивувати ресурс.",
                "Динамічні дані тесту очищені.",
            ),
        ),
    },
    "TC-ORD-007": {
        "title": "CREW: створення замовлення відхиляється для типу локації",
        "description": (
            "CREW не підтримує функції локації для замовлень; пряме створення замовлення "
            "на динамічно створеному екіпажі відхиляється."
        ),
        "preconditions": (
            "Динамічно створені локація-замовник, ресурс і дочірня CREW; користувач має "
            "доступ до батьківської локації."
        ),
        "expectedResult": "POST /orders для CREW повертає HTTP 403.",
        "tags": "orders,location-kind,crew,automated,dynamic-fixture",
        "apiAutomationIds": ["TC-ORD-007"],
        "steps": steps(
            ("Створити CREW під динамічною локацією-замовником.", "Екіпаж створений для цього тесту."),
            (
                "Виконати POST /orders зі storageId екіпажу та динамічним ресурсом.",
                "HTTP 403; замовлення на CREW не створене.",
            ),
            ("Архівувати створений екіпаж.", "Динамічна локація очищена."),
        ),
    },
    "TC-ORD-008": {
        "title": "FLY_POINT: створення замовлення відхиляється для типу локації",
        "description": (
            "FLY_POINT не підтримує функції локації для замовлень; пряме створення "
            "замовлення на динамічно створеній точці вильоту відхиляється."
        ),
        "preconditions": (
            "Динамічно створені локація-замовник, ресурс і дочірня FLY_POINT; користувач "
            "має доступ до батьківської локації."
        ),
        "expectedResult": "POST /orders для FLY_POINT повертає HTTP 403.",
        "tags": "orders,location-kind,fly-point,automated,dynamic-fixture",
        "apiAutomationIds": ["TC-ORD-008"],
        "steps": steps(
            (
                "Створити FLY_POINT під динамічною локацією-замовником.",
                "Точка вильоту створена для цього тесту.",
            ),
            (
                "Виконати POST /orders зі storageId точки вильоту та динамічним ресурсом.",
                "HTTP 403; замовлення на FLY_POINT не створене.",
            ),
            ("Архівувати створену точку вильоту.", "Динамічна локація очищена."),
        ),
    },
    "TC-ORD-011": {
        "title": "Update замовлення з чужою динамічною локацією повертає 4xx",
        "description": (
            "Оновлення замовлення не дозволяє замінити його локацію на іншу ізольовану "
            "локацію, створену fixture цього ж тестового класу."
        ),
        "preconditions": (
            "Динамічно створені окремі локації замовника і збору, унікальний ресурс та NEW-замовлення."
        ),
        "expectedResult": "PUT /orders/{id} із чужим storageId повертає контрольований HTTP 4xx.",
        "tags": "orders,validation,automated,dynamic-fixture",
        "apiAutomationIds": ["TC-ORD-011"],
        "steps": steps(
            ("Створити NEW-замовлення на динамічній локації-замовнику.", "Замовлення створене."),
            (
                "Оновити замовлення, передавши storageId іншої динамічної локації збору.",
                "API повертає 4xx; локація замовлення не підмінена.",
            ),
        ),
    },
    "TC-ORD-ADMIN-001": {
        "title": "TC-ORD-ADMIN-001 — Order administrator permission boundary",
        "priority": "CRITICAL",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "testType": "SECURITY",
        "testData": None,
        "jiraIssueKey": None,
        "dependencies": None,
        "parameterized": False,
        "description": (
            "Order Admin поєднує scoped grant керівника власної динамічної локації з "
            "агрегованими order permissions і читанням виробничих замовлень."
        ),
        "preconditions": (
            "Динамічно створені локація-замовник, окрема виробнича локація збору, ресурси "
            "і користувач з ролями «Керівник локації» та «Замовлення: адміністратор»."
        ),
        "expectedResult": (
            "GET /users/me містить order::read/update/manage і production-order::read; grant "
            "керівника прив'язаний до власної локації. Читання production orders власної "
            "локації повертає 200, створення на чужій — 403."
        ),
        "tags": "orders,rbac,automated,dynamic-fixture",
        "apiAutomationIds": ["TC-ORD-ADMIN-001"],
        "uiAutomationIds": [],
        "steps": steps(
            (
                "Створити ізольовані локації й користувача Order Admin; прочитати GET /users/me.",
                "Є обидва grants, агреговані order permissions, production-order::read і scoped storage у grant керівника.",
            ),
            (
                "Прочитати production orders власної локації.",
                "HTTP 200.",
            ),
            (
                "Спробувати створити production order на чужій динамічній локації.",
                "HTTP 403.",
            ),
        ),
    },
    "TC-WMS-007-004": {
        "description": (
            "Admin у режимі «Всі локації» бачить динамічний ресурс в агрегованій таблиці "
            "з колонкою «Всього» та окремими колонками доступних локацій."
        ),
        "preconditions": (
            "Динамічно створені локація і унікальний ресурс із залишком; Admin має доступ "
            "до режиму «Всі локації»."
        ),
        "expectedResult": (
            "Агрегована таблиця містить ресурс, колонку «Всього» і щонайменше одну колонку локації."
        ),
        "tags": "inventory,stock,ui,api,automated,dynamic-fixture",
        "apiAutomationIds": ["TC-WMS-007-004"],
        "steps": steps(
            (
                "Створити унікальний ресурс із залишком на динамічній локації.",
                "Ресурс доступний у hierarchy inventory API.",
            ),
            (
                "Під Admin обрати «Всі локації», відкрити «Залишки» і знайти ресурс.",
                "Ресурс відображається в агрегованій таблиці.",
            ),
            (
                "Перевірити заголовки таблиці.",
                "Є «Ресурс», «Категорія», «Всього» та окремі колонки доступних локацій.",
            ),
        ),
    },
    "TC-WMS-007-018": {
        "tags": "inventory,stock,ui,api,filters,tags,automated,dynamic-fixture",
        "uiAutomationIds": ["TC-WMS-007-018"],
    },
    "TC-UI-ALERT-003": {
        "description": (
            "Для STORAGE, UNIT, PRODUCTION, CREW і FLY_POINT тест динамічно створює локацію, "
            "користувача та два ресурси. На /inventory ресурс з порогом іде першим; "
            "/alerts/{id} показує збережений поріг саме вибраної локації."
        ),
        "preconditions": (
            "Admin може створювати локації всіх п'яти типів. Кожна ітерація створює власну "
            "локацію, власника/комірника та унікальні alert/plain ресурси; готові IDs не використовуються."
        ),
        "expectedResult": (
            "Для кожного типу на /inventory alert-ресурс розташований вище plain-ресурсу й "
            "має «відсутній» та «мін. 10»; /alerts/{id} лишається на ID вибраної локації та "
            "показує ресурс із порогом 10."
        ),
        "tags": "alerts,storage-type,regression,ui,automated,dynamic-fixture",
        "uiAutomationIds": ["TC-UI-ALERT-003"],
        "steps": steps(
            (
                "Для чергового типу створити локацію, користувача й унікальні alert/plain ресурси; задати поріг 10.",
                "Дані ізольовані від наявних локацій і ресурсів середовища.",
            ),
            (
                "Відкрити /inventory вибраної локації, увімкнути нульові залишки й знайти обидва ресурси.",
                "Alert-ресурс вище plain; лише він має «відсутній» і «мін. 10».",
            ),
            (
                "Відкрити налаштування сповіщень або direct URL /alerts/{id}.",
                "URL містить ID вибраної локації; ресурс і поріг 10 відображаються.",
            ),
            (
                "Повторити для STORAGE, UNIT, PRODUCTION, CREW і FLY_POINT, потім очистити дані.",
                "Однакова семантика для всіх типів; динамічні артефакти видалені/деактивовані."
            ),
        ),
    },
    "TC-UI-PLANEXEC-011": {
        "description": (
            "Тест створює два ізольовані продукти з техкартами, виробництвом і планом поточного "
            "місяця, зберігає один favourite через API та перевіряє UI-модалку і фільтр таблиці."
        ),
        "preconditions": (
            "Для динамічних продуктів створені техкарти, виробництво й поточний план; favourite-список "
            "локації збережений і відновлюється після тесту."
        ),
        "expectedResult": (
            "UI показує «Керувати обраними (1)»; після «Лише обрані» видно тільки обраний продукт."
        ),
        "tags": "ui,pln-001,favourites,automated,dynamic-fixture",
        "uiAutomationIds": ["TC-UI-PLANEXEC-011"],
        "steps": steps(
            (
                "Створити два динамічні продукти з техкартами, виробництвом і планом; один зберегти як favourite.",
                "Обидва продукти присутні у виконанні плану, favourite-лічильник дорівнює 1.",
            ),
            (
                "Відкрити й закрити «Керування обраними», потім натиснути «Лише обрані».",
                "Кнопка в selected-стані; у таблиці видно тільки обраний продукт.",
            ),
        ),
    },
    "TC-UI-PLANEXEC-012": {
        "preconditions": (
            "Два динамічні продукти мають техкарти, виробництво і план поточного місяця; перший "
            "збережений як favourite, другий доступний у API-каталозі модалки."
        ),
        "tags": "ui,pln-001,favourites,automated,dynamic-fixture",
        "uiAutomationIds": ["TC-UI-PLANEXEC-012"],
        "steps": steps(
            (
                "Створити два динамічні продукти з техкартами, виробництвом і планом; перший зберегти як favourite.",
                "Лічильник дорівнює 1; другий продукт є в каталозі with-technological-map.",
            ),
            (
                "У модалці знайти другий продукт, позначити його та зберегти.",
                "PUT favourites успішний; лічильник дорівнює 2.",
            ),
            ("Увімкнути «Лише обрані».", "У таблиці видимі обидва динамічні продукти."),
        ),
    },
    "TC-UI-PLANEXEC-013": {
        "preconditions": (
            "Два динамічні продукти мають техкарти, виробництво і план поточного місяця; обидва "
            "збережені у favourites локації."
        ),
        "tags": "ui,pln-001,favourites,automated,dynamic-fixture",
        "uiAutomationIds": ["TC-UI-PLANEXEC-013"],
        "steps": steps(
            (
                "Створити два динамічні продукти з техкартами, виробництвом і планом; зберегти обидва як favourites.",
                "Лічильник favourites дорівнює 2.",
            ),
            (
                "У модалці ввімкнути «Лише обрані», зняти перший продукт і зберегти.",
                "Лічильник дорівнює 1; у модалці лишився тільки другий продукт.",
            ),
            ("Увімкнути «Лише обрані» на сторінці.", "У таблиці видно тільки залишений продукт."),
        ),
    },
    "TC-PLN-NR-044": {
        "preconditions": (
            "Тест динамічно створює production parent і дві production child локації, унікальний "
            "ланцюжок ресурсів/техкарт та різні плани на дітях."
        ),
        "tags": "plan-execution,needed-resources,ui,parent,automated,dynamic-fixture",
        "uiAutomationIds": ["TC-PLN-NR-044"],
        "steps": steps(
            (
                "Створити динамічні parent/child локації, ресурси, техкарти й різні плани на дітях.",
                "Тестові дані ізольовані та доступні користувачу.",
            ),
            (
                "Відкрити parent і вкладку «Потрібні ресурси».",
                "Таблиця показує агреговану потребу піддерева.",
            ),
            ("Перемкнутися на child A.", "Таблиця показує scope лише child A."),
            ("Повернутися на parent.", "Агреговані значення відновилися."),
        ),
    },
}


CREATE_DEFAULTS: dict[str, dict[str, Any]] = {
    "TC-ORD-ADMIN-001": {
        "featureId": "REQ-ORD",
        "acId": "AC-11",
        "priority": "CRITICAL",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "testType": "SECURITY",
        "testData": None,
        "jiraIssueKey": None,
        "dependencies": None,
        "apiAutomationIds": [],
        "uiAutomationIds": [],
        "parameterized": False,
        "steps": [],
    },
}


MUTABLE_FIELDS = (
    "title",
    "description",
    "priority",
    "severity",
    "status",
    "testType",
    "preconditions",
    "testData",
    "expectedResult",
    "tags",
    "jiraIssueKey",
    "dependencies",
    "apiAutomationIds",
    "uiAutomationIds",
    "parameterized",
    "steps",
)


def case_path(test_id: str) -> str:
    encoded = urllib.parse.quote(test_id, safe="")
    return f"/api/ai/projects/{PROJECT_ID}/test-cases/{encoded}"


def normalized(case: dict[str, Any]) -> dict[str, Any]:
    return {key: case.get(key) for key in MUTABLE_FIELDS}


def sync(client: TcmClient) -> dict[str, Any]:
    actions: dict[str, str] = {}
    before_hashes: dict[str, str | None] = {}
    after_hashes: dict[str, str] = {}

    for test_id, patch in PATCHES.items():
        path = case_path(test_id)
        current = client.get_optional(path)
        if current is None:
            defaults = CREATE_DEFAULTS.get(test_id)
            if defaults is None:
                raise RuntimeError(f"Required TCM case does not exist: {test_id}")
            feature = client.request(
                "GET", f"/api/ai/projects/{PROJECT_ID}/features/{defaults['featureId']}"
            )
            acceptance_criterion = next(
                (
                    item
                    for item in feature.get("acceptanceCriteria", [])
                    if item.get("acId") == defaults["acId"]
                ),
                None,
            )
            if acceptance_criterion is None:
                raise RuntimeError(
                    f"Required TCM acceptance criterion does not exist: "
                    f"{defaults['featureId']}/{defaults['acId']}"
                )
            current = {
                **defaults,
                "acceptanceCriterionId": acceptance_criterion["id"],
            }
            before_hashes[test_id] = None
            was_missing = True
        else:
            before = normalized(current)
            before_hashes[test_id] = hashlib.sha256(
                json.dumps(before, ensure_ascii=False, sort_keys=True).encode("utf-8")
            ).hexdigest()
            was_missing = False

        before = normalized(current)
        expected = before | patch
        payload = {
            "featureId": current["featureId"],
            "acceptanceCriterionId": current["acceptanceCriterionId"],
            "testId": test_id,
        } | expected

        if was_missing:
            client.request(
                "POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", payload
            )
            actions[test_id] = "created"
        elif before == expected:
            actions[test_id] = "unchanged"
        else:
            client.request("PUT", path, payload)
            actions[test_id] = "updated"

        actual_case = client.request("GET", path)
        actual = normalized(actual_case)
        if actual != expected:
            mismatched = [key for key in MUTABLE_FIELDS if actual.get(key) != expected.get(key)]
            raise RuntimeError(f"TCM verification failed for {test_id}: {mismatched}")
        after_hashes[test_id] = hashlib.sha256(
            json.dumps(actual, ensure_ascii=False, sort_keys=True).encode("utf-8")
        ).hexdigest()

    return {
        "baseUrl": client.base_url,
        "projectId": PROJECT_ID,
        "actions": actions,
        "verification": {
            "managedCaseCount": len(PATCHES),
            "allCasesMatch": True,
            "beforeSha256": before_hashes,
            "afterSha256": after_hashes,
        },
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "result": "SUCCESS",
    }


def read_dotenv_value(path: Path, name: str) -> str | None:
    if not path.is_file():
        return None
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == name:
            return value.strip().strip('"').strip("'")
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--dotenv", type=Path)
    parser.add_argument("--default-token")
    parser.add_argument("--insecure", action="store_true")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    token = os.getenv(args.token_env)
    if not token and args.dotenv:
        token = read_dotenv_value(args.dotenv, args.token_env)
    if not token:
        token = args.default_token
    if not token:
        print(f"Missing token: set {args.token_env} or pass --dotenv", file=sys.stderr)
        return 2

    report = sync(TcmClient(args.base_url, token, args.insecure))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({
        "result": report["result"],
        "baseUrl": report["baseUrl"],
        "created": sum(action == "created" for action in report["actions"].values()),
        "updated": sum(action == "updated" for action in report["actions"].values()),
        "unchanged": sum(action == "unchanged" for action in report["actions"].values()),
        "verified": report["verification"]["allCasesMatch"],
        "report": str(args.report),
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
