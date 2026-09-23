#!/usr/bin/env python3
"""Synchronize project-production business requirements with TCM.

The script is intentionally idempotent: features, acceptance criteria, and test
cases are upserted by their business identifiers.  It does not publish ERP test
run results.  Production credentials must be supplied through TCM_AI_TOKEN.
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
HISTORY_BLOCK_START = "<!-- PROJECT-PRODUCTION-HISTORY:START -->"
HISTORY_BLOCK_END = "<!-- PROJECT-PRODUCTION-HISTORY:END -->"


ROOT_DOCUMENTATION = """# Проєктне виробництво

Проєктне виробництво використовує ресурси як вхідні матеріали, а результатом є обладнання.

- `CREATION` виготовляє нову одиницю обладнання.
- `MODIFICATION` змінює вибрану наявну одиницю обладнання і не створює нову.
- Виробничі етапи списують ресурси; сумарне виконання етапів не може перевищувати 100%.
- Активна модифікація резервує вибране обладнання від інших несумісних операцій.
- Після скасування виробництва або видалення етапу ресурси повертаються повністю чи частково окремою швидкою дією.
- Кожне повернення відображається в історії операцією «Повернено з проекту».

Детальні правила розділено між дочірніми фічами виготовлення, шаблонів, модифікації, резервування та повернення ресурсів.

Опис у production TCM є нормативною документацією, але не означає виконання тестів на production ERP. Актуальний повний прогін виконувався на dev.
"""

FEATURES: list[dict[str, Any]] = [
    {
        "featureId": "REQ-PROJ",
        "title": "Проєктне виробництво",
        "description": "Виготовлення та модифікація обладнання з використанням ресурсів у виробничих етапах",
        "documentation": ROOT_DOCUMENTATION,
        "module": "PROJ",
        "priority": "CRITICAL",
        "status": "ACTIVE",
    },
    {
        "featureId": "REQ-PROJ-001",
        "parentFeatureId": "REQ-PROJ",
        "title": "Виготовлення обладнання",
        "description": "Створення обладнання, виконання етапів, списання ресурсів і життєвий цикл виробництва",
        "documentation": """# Виготовлення обладнання

Результатом `CREATION` є одна нова одиниця обладнання, а не партія ресурсу. Категорія, модель і серійний номер визначають майбутнє обладнання. Серійний номер обов'язковий та унікальний. Обладнання створюється тільки після завершення виробництва і стає доступним для подальших операцій.

Ресурси є вхідними матеріалами етапів. Не можна використати більше наявного залишку. Сумарний відсоток виконання етапів не може перевищувати 100%. Завершений проєкт не видаляється. Скасування завершення прибирає виготовлене обладнання та повертає проєкт у незавершений стан.
""",
        "module": "PROJ",
        "priority": "CRITICAL",
        "status": "ACTIVE",
    },
    {
        "featureId": "REQ-PROJ-002",
        "parentFeatureId": "REQ-PROJ",
        "title": "Шаблони проєктного виробництва",
        "description": "Повторне використання структури етапів без списання ресурсів і без закріплення конкретного обладнання",
        "documentation": """# Шаблони проєктного виробництва

Шаблон зберігає тип виробництва, структуру етапів і потрібні ресурси, але сам не списує залишки. Шаблон модифікації не містить конкретної одиниці обладнання і нічого не резервує. Обладнання вибирається у створеному зі шаблону проєкті.

Модифікацію зі шаблону без обладнання можна зберегти та скасувати, але не можна завершити. Для шаблону, як і для проєкту, сумарний відсоток виконання етапів не може перевищувати 100%.
""",
        "module": "PROJ",
        "priority": "HIGH",
        "status": "ACTIVE",
    },
    {
        "featureId": "REQ-PROJ-003",
        "parentFeatureId": "REQ-PROJ",
        "title": "[DEPRECATED] Каталог категорій і продуктів проєктного виробництва",
        "description": "Застарілий каталог замінено довідниками категорій і моделей обладнання",
        "documentation": "Застаріла модель project-category/project-product більше не визначає результат проєктного виробництва. Використовуються категорії, моделі та одиниці обладнання.",
        "module": "PROJ",
        "priority": "LOW",
        "status": "DEPRECATED",
    },
    {
        "featureId": "REQ-PROJ-004",
        "parentFeatureId": "REQ-PROJ",
        "title": "Модифікація обладнання",
        "description": "Зміна характеристик конкретної наявної одиниці обладнання без створення нової одиниці",
        "documentation": """# Модифікація обладнання

`MODIFICATION` виконується над однією конкретною одиницею обладнання. Після створення активної модифікації вибране обладнання не можна замінити. Його категорія та модель доступні лише для читання і не змінюються модифікацією.

Для інших характеристик окремий перелік дозволених полів поки не встановлюється: можна змінювати будь-які доступні характеристики, які підтримує форма. Зміни застосовуються до цієї самої одиниці після завершення. Нове обладнання не створюється. Скасування не застосовує незавершені зміни.
""",
        "module": "PROJ",
        "priority": "CRITICAL",
        "status": "ACTIVE",
    },
    {
        "featureId": "REQ-PROJ-005",
        "parentFeatureId": "REQ-PROJ",
        "title": "Резервування обладнання для модифікації",
        "description": "Захист обладнання в активній модифікації від паралельних несумісних операцій",
        "documentation": """# Резервування обладнання

Обладнання резервується одразу після його вибору та створення активної модифікації. До завершення або скасування його не можна переміщати, призначати, передавати в ремонт чи включати до іншої активної модифікації.

Шаблон не зберігає конкретне обладнання і не створює резервування. Модифікація зі шаблону резервує обладнання після його вибору. Завершення або скасування модифікації знімає резервування.
""",
        "module": "PROJ",
        "priority": "CRITICAL",
        "status": "ACTIVE",
    },
    {
        "featureId": "REQ-PROJ-006",
        "parentFeatureId": "REQ-PROJ",
        "title": "Повернення ресурсів проєктного виробництва",
        "description": "Повне або часткове повернення використаних ресурсів після скасування проєкту чи видалення етапу",
        "documentation": """# Повернення ресурсів проєктного виробництва

Після скасування проєктного виробництва або під час видалення виконаного чи частково виконаного етапу користувач обирає швидку дію «Повернути всі ресурси» або «Повернути частково». Автоматичне неявне повернення всіх ресурсів без вибору користувача не допускається.

Повне повернення відновлює всю ще не повернуту кількість. Часткове повернення задається окремо для кожного ресурсу. Сума повернень не може перевищувати використану кількість; кожне повторне повернення обмежене неповернутим залишком.

Кожна фактична операція створює окремий запис «Повернено з проекту». Для кількох ресурсів створюються окремі записи. Запис містить ресурс, кількість, проєкт, етап за наявності, дату, час і користувача. Невдала операція запису не створює.
""",
        "module": "PROJ",
        "priority": "CRITICAL",
        "status": "ACTIVE",
    },
]


ACCEPTANCE_CRITERIA: dict[str, list[tuple[str, str]]] = {
    "REQ-PROJ": [
        ("AC-01", "Проєктне виробництво підтримує створення й модифікацію обладнання, виконання етапів, завершення, скасування та заборону видалення завершеного проєкту."),
    ],
    "REQ-PROJ-001": [
        ("AC-01", "Виконання етапу списує вказану кількість вхідного ресурсу з доступного залишку."),
        ("AC-02", "Не можна використати в етапі більше ресурсу, ніж доступно на локації."),
        ("AC-03", "Завершення CREATION створює одну доступну одиницю обладнання з категорією, моделлю та серійним номером проєкту; ресурсна партія як результат не створюється."),
        ("AC-04", "Скасування завершення CREATION прибирає виготовлене обладнання та повертає проєкт у незавершений стан."),
        ("AC-05", "Повне повернення під час видалення допустимого проєкту відновлює весь використаний ресурс."),
        ("AC-06", "Часткове повернення під час видалення допустимого проєкту відновлює лише вибрану кількість ресурсу."),
        ("AC-07", "Для завершення CREATION серійний номер обов'язковий та унікальний."),
        ("AC-08", "[DEPRECATED] Застаріле правило залежності від каталогу продуктів не використовується; результат визначається обладнанням."),
        ("AC-09", "Створення, зміна, завершення, скасування та видалення доступні лише користувачам із відповідними правами на локації."),
        ("AC-10", "Сумарний відсоток виконання етапів проєкту не може перевищувати 100%."),
    ],
    "REQ-PROJ-002": [
        ("AC-01", "Шаблон та його етапи можна створювати, змінювати й видаляти без списання складських залишків."),
        ("AC-02", "З шаблону CREATION створюється виробництво з категорією, моделлю та етапами майбутнього обладнання."),
        ("AC-03", "Наявне проєктне виробництво можна зберегти як шаблон зі збереженням типу та структури етапів."),
        ("AC-04", "Модифікацію зі шаблону без вибраного обладнання можна створити та скасувати, але не можна завершити."),
        ("AC-05", "Сумарний відсоток виконання етапів шаблону не може перевищувати 100%."),
        ("AC-06", "Шаблон модифікації не зберігає конкретне обладнання і не резервує його; обладнання вибирається у створеному проєкті."),
    ],
    "REQ-PROJ-004": [
        ("AC-01", "Завершення MODIFICATION змінює вибрану одиницю обладнання та не створює нову."),
        ("AC-02", "Після створення активної модифікації вибране обладнання не можна замінити іншим."),
        ("AC-03", "Категорія та модель відповідають вибраному обладнанню, доступні лише для читання і не змінюються модифікацією."),
        ("AC-04", "Для інших характеристик немає фіксованого переліку: дозволено змінювати будь-які доступні у формі характеристики."),
        ("AC-05", "Скасування модифікації не застосовує незавершені зміни до обладнання."),
    ],
    "REQ-PROJ-005": [
        ("AC-01", "Обладнання резервується одразу після його вибору та створення активної модифікації; друга активна модифікація заборонена."),
        ("AC-02", "Зарезервоване активною модифікацією обладнання не можна перемістити."),
        ("AC-03", "Зарезервоване активною модифікацією обладнання не можна передати або призначити."),
        ("AC-04", "Зарезервоване активною модифікацією обладнання не можна передати в ремонт."),
        ("AC-05", "Завершення модифікації знімає резервування обладнання."),
        ("AC-06", "Скасування модифікації знімає резервування обладнання."),
        ("AC-07", "Шаблон не резервує обладнання; проєкт зі шаблону резервує його тільки після вибору."),
    ],
    "REQ-PROJ-006": [
        ("AC-01", "Після скасування виробництва та при видаленні виконаного етапу доступні швидкі дії «Повернути всі ресурси» і «Повернути частково»."),
        ("AC-02", "Повне повернення відновлює всю ще не повернуту кількість використаних ресурсів."),
        ("AC-03", "Часткове повернення задається окремо для кожного ресурсу і не може перевищувати використану кількість."),
        ("AC-04", "Повторне часткове повернення обмежується неповернутим залишком; після повного повернення повторна дія недоступна."),
        ("AC-05", "Скасування проєкту або видалення етапу не повертає всі ресурси неявно без вибору користувача."),
        ("AC-06", "Правила повернення однаково застосовуються до CREATION та MODIFICATION."),
        ("AC-07", "Кожне повне або часткове повернення створює окремий запис «Повернено з проекту» для кожного ресурсу; невдала операція запису не створює."),
    ],
    "REQ-OPER-HIST": [
        ("AC-07", "Ресурси, використані у CREATION або MODIFICATION, відображаються в історії операцією «Використано» з фактичною кількістю."),
        ("AC-18", "Виготовлене в CREATION обладнання має операцію «Виготовлено» у власній і загальній історії та відображається в таблиці обладнання; MODIFICATION не створює такої операції."),
        ("AC-19", "Кожне повне або часткове повернення ресурсу з проєктного виробництва відображається окремою операцією «Повернено з проекту» із ресурсом, кількістю, проєктом, етапом за наявності, датою, часом і користувачем."),
    ],
}


def steps(action: str, expected: str) -> list[dict[str, Any]]:
    return [
        {"stepOrder": 1, "actionText": action, "expectedText": expected},
        {"stepOrder": 2, "actionText": "Перевірити кінцевий бізнес-стан і пов'язані записи.", "expectedText": expected},
    ]


def case(
    test_id: str,
    feature_id: str,
    ac_key: str,
    title: str,
    action: str,
    expected: str,
    *,
    priority: str = "HIGH",
    severity: str = "MAJOR",
    test_type: str = "FUNCTIONAL",
    automated: bool = True,
) -> dict[str, Any]:
    is_ui = test_id.startswith("TC-UI-")
    return {
        "featureId": feature_id,
        "acKey": ac_key,
        "testId": test_id,
        "title": title,
        "description": f"Бізнес-сценарій: {title}",
        "priority": priority,
        "severity": severity,
        "status": "ACTIVE",
        "testType": "UI" if is_ui else test_type,
        "preconditions": "Підготовлені активна локація, користувач із потрібними правами та ізольовані тестові дані.",
        "expectedResult": expected,
        "tags": "project-production,business" + (",manual" if not automated else ",automated"),
        "apiAutomationIds": [test_id] if automated and not is_ui else [],
        "uiAutomationIds": [test_id] if automated and is_ui else [],
        "steps": steps(action, expected),
    }


CASES: list[dict[str, Any]] = [
    case("TC-PROJ-001", "REQ-PROJ-001", "AC-01", "Використання ресурсу на виробничому етапі", "Створити CREATION, додати етап із ресурсом і виконати його.", "Використана кількість списана із залишку; обладнання ще не створене.", priority="CRITICAL"),
    case("TC-PROJ-002", "REQ-PROJ-001", "AC-02", "Заборона використання ресурсу понад доступний залишок", "Спробувати виконати етап із кількістю ресурсу, більшою за доступну.", "Операцію відхилено; залишок не змінився.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-003", "REQ-PROJ-001", "AC-03", "Завершення виробництва створює обладнання", "Завершити CREATION зі 100% виконаними етапами та унікальним серійним номером.", "Створено одну доступну одиницю обладнання; ресурсна партія як результат відсутня.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-004", "REQ-PROJ-001", "AC-04", "Скасування завершення прибирає виготовлене обладнання", "Завершити CREATION, а потім скасувати завершення.", "Створене обладнання прибране, проєкт повернувся у незавершений стан.", priority="CRITICAL"),
    case("TC-PROJ-005", "REQ-PROJ-006", "AC-02", "Повне повернення використаних ресурсів", "Видалити допустимий проєкт і вибрати повернення всіх ресурсів.", "Уся ще не повернута кількість відновлена на залишку.", priority="CRITICAL"),
    case("TC-PROJ-006", "REQ-PROJ-006", "AC-03", "Часткове повернення використаних ресурсів", "Видалити допустимий проєкт і вказати часткову кількість повернення.", "Повернуто лише вибрану кількість; решта залишається неповернутою.", priority="CRITICAL"),
    case("TC-PROJ-007", "REQ-PROJ-001", "AC-07", "Обов'язковий та унікальний серійний номер", "Спробувати завершити CREATION без серійного номера та з номером наявного обладнання.", "Обидві спроби відхилено; нове обладнання не створене.", priority="CRITICAL"),
    case("TC-PROJ-009", "REQ-PROJ", "AC-01", "Завершений проєкт не можна видалити", "Завершити проєктне виробництво і спробувати його видалити.", "Видалення відхилено; результат і дані виробництва збережено.", priority="CRITICAL"),
    case("TC-PROJ-VAL-001", "REQ-PROJ-001", "AC-10", "Етапи проєкту не можуть перевищувати 100%", "Спробувати зберегти етапи із сумарним виконанням 105%.", "Збереження відхилено; стан понад 100% не створений.", priority="CRITICAL", severity="CRITICAL"),

    case("TC-PROJ-TPL-001", "REQ-PROJ-002", "AC-01", "Керування шаблоном не змінює залишки", "Створити, змінити та видалити шаблон з етапами й ресурсами.", "Шаблон змінено, складські залишки не змінилися."),
    case("TC-PROJ-TPL-002", "REQ-PROJ-002", "AC-02", "Створення виготовлення обладнання із шаблону", "Створити CREATION із шаблону.", "До проєкту перенесено категорію, модель та етапи майбутнього обладнання."),
    case("TC-PROJ-TPL-003", "REQ-PROJ-002", "AC-03", "Збереження виробництва як шаблону", "Зберегти наявне виробництво як шаблон.", "Шаблон зберіг тип виробництва та структуру етапів."),
    case("TC-PROJ-TPL-004", "REQ-PROJ-002", "AC-04", "Модифікація зі шаблону без обладнання не завершується", "Створити MODIFICATION із шаблону без обладнання, спробувати завершити, потім скасувати.", "Завершення відхилено, скасування дозволено.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-TPL-005", "REQ-PROJ-002", "AC-05", "Етапи шаблону не можуть перевищувати 100%", "Спробувати зберегти шаблон із сумарним виконанням етапів 105%.", "Збереження відхилено; стан понад 100% не створений.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-TPL-006", "REQ-PROJ-002", "AC-06", "Шаблон модифікації не зберігає конкретне обладнання", "Створити шаблон із проєкту модифікації та повторно використати його.", "Конкретне обладнання не перенесене й не зарезервоване; його обирають у новому проєкті.", automated=False),

    case("TC-PROJ-MOD-001", "REQ-PROJ-004", "AC-01", "Модифікація змінює вибране обладнання без створення нового", "Завершити MODIFICATION для вибраної одиниці обладнання.", "Зміни застосовані до цієї самої одиниці; нове обладнання не створене.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-MOD-006", "REQ-PROJ-004", "AC-02", "Вибране обладнання не можна замінити", "У створеній модифікації спробувати замінити обладнання A на B.", "Зміну відхилено; характеристики A і B не отримали побічних змін.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-MOD-007", "REQ-PROJ-004", "AC-03", "Категорія та модель обладнання незмінні", "У створеній модифікації спробувати змінити категорію та модель.", "Зміну відхилено; класифікація обладнання залишилася початковою.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-MOD-008", "REQ-PROJ-004", "AC-04", "Зміна доступних характеристик обладнання", "Змінити кілька доступних характеристик, крім категорії та моделі, і завершити модифікацію.", "Усі введені характеристики збережені на вибраному обладнанні.", automated=False),
    case("TC-PROJ-MOD-009", "REQ-PROJ-004", "AC-05", "Скасування не застосовує незавершені зміни", "Змінити характеристики в активній модифікації та скасувати її.", "Обладнання зберегло значення, які мало до модифікації.", automated=False),
    case("TC-UI-PROJ-MOD-001", "REQ-PROJ-004", "AC-03", "Обладнання, категорія та модель недоступні для зміни в UI", "Відкрити редагування створеної модифікації.", "Обладнання, категорія та модель доступні лише для читання; дія створення нової моделі відсутня.", priority="CRITICAL", severity="CRITICAL"),

    case("TC-PROJ-MOD-002", "REQ-PROJ-005", "AC-01", "Заборона другої активної модифікації", "Створити активну модифікацію та спробувати створити другу для того самого обладнання.", "Другу модифікацію відхилено.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-MOD-003", "REQ-PROJ-005", "AC-02", "Заборона переміщення обладнання в активній модифікації", "Спробувати перемістити зарезервоване обладнання.", "Переміщення відхилено; локація обладнання не змінилася.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-MOD-004", "REQ-PROJ-005", "AC-03", "Заборона призначення обладнання в активній модифікації", "Спробувати передати або призначити зарезервоване обладнання.", "Призначення відхилено; відповідальна особа не змінилася.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-MOD-005", "REQ-PROJ-005", "AC-04", "Заборона ремонту обладнання в активній модифікації", "Спробувати передати зарезервоване обладнання в ремонт.", "Операцію відхилено; стан обладнання не змінився.", priority="CRITICAL", severity="CRITICAL"),
    case("TC-PROJ-RES-001", "REQ-PROJ-005", "AC-05", "Зняття резервування після завершення модифікації", "Завершити модифікацію та виконати дозволену наступну операцію з обладнанням.", "Резервування зняте; наступна операція доступна.", automated=False),
    case("TC-PROJ-RES-002", "REQ-PROJ-005", "AC-06", "Зняття резервування після скасування модифікації", "Скасувати модифікацію та виконати дозволену наступну операцію з обладнанням.", "Резервування зняте; наступна операція доступна.", automated=False),
    case("TC-PROJ-RES-003", "REQ-PROJ-005", "AC-07", "Шаблон не резервує обладнання", "Створити й повторно використати шаблон модифікації.", "Жодна одиниця не зарезервована до вибору обладнання у проєкті.", automated=False),

    case("TC-PROJ-HIST-001", "REQ-OPER-HIST", "AC-07", "Ресурс виготовлення відображається як «Використано»", "Використати ресурс у CREATION і відкрити його історію.", "Є окремий запис «Використано» з фактичною кількістю.", priority="CRITICAL"),
    case("TC-PROJ-HIST-003", "REQ-OPER-HIST", "AC-07", "Ресурс модифікації відображається як «Використано»", "Використати ресурс у MODIFICATION і відкрити його історію.", "Є окремий запис «Використано» з фактичною кількістю.", priority="CRITICAL"),
    case("TC-PROJ-HIST-002", "REQ-OPER-HIST", "AC-18", "Виготовлене обладнання має операцію «Виготовлено»", "Завершити CREATION і перевірити історію створеного обладнання.", "В історії обладнання є операція «Виготовлено»; ресурсний результат не створено.", priority="CRITICAL"),
    case("TC-UI-PROJ-HIST-001", "REQ-OPER-HIST", "AC-18", "Виготовлене обладнання відображається в загальній історії", "Завершити CREATION і відкрити загальну історію операцій.", "Ресурс показано як «Використано», а створене обладнання — окремим рядком «Виготовлено».", priority="CRITICAL", severity="CRITICAL"),
    case("TC-UI-PROJ-HIST-002", "REQ-OPER-HIST", "AC-07", "Ресурс модифікації відображається в UI як «Використано»", "Використати ресурс у MODIFICATION і відкрити історію операцій.", "Ресурс присутній у картці та таблиці «Використано».", priority="CRITICAL"),

    case("TC-UI-PROJ-001", "REQ-PROJ-001", "AC-01", "Створення виготовлення обладнання з етапом через UI", "Через UI створити CREATION, визначити категорію, модель, серійний номер, етап і ресурс.", "Проєкт збережений, етап і вхідний ресурс відображаються коректно.", priority="CRITICAL"),
    case("TC-UI-PROJ-002", "REQ-PROJ-001", "AC-03", "Завершення виготовлення обладнання через UI", "Відкрити готовий CREATION і завершити його.", "Проєкт завершений, створене обладнання доступне для перегляду.", priority="CRITICAL"),
    case("TC-UI-PROJ-003", "REQ-PROJ-002", "AC-02", "Створення виробництва із шаблону через UI", "У списку шаблонів вибрати створення виробництва.", "Відкрито новий проєкт із даними та етапами шаблону."),
    case("TC-UI-PROJ-005", "REQ-PROJ", "AC-01", "Додавання наступного виробничого етапу", "На формі з наявним етапом натиснути «Додати етап».", "Новий етап додано після наявних; попередні дані збережено."),

    case("TC-PROJ-RET-001", "REQ-PROJ-006", "AC-01", "Повне повернення після скасування виготовлення", "Скасувати CREATION із використаними ресурсами та вибрати «Повернути всі ресурси».", "Усі неповернуті ресурси відновлено.", automated=False),
    case("TC-PROJ-RET-002", "REQ-PROJ-006", "AC-03", "Часткове повернення після скасування виготовлення", "Скасувати CREATION і вказати часткову кількість повернення.", "Відновлено лише вказану кількість.", automated=False),
    case("TC-PROJ-RET-003", "REQ-PROJ-006", "AC-06", "Повне повернення після скасування модифікації", "Скасувати MODIFICATION із використаними ресурсами та повернути всі.", "Усі неповернуті ресурси модифікації відновлено.", automated=False),
    case("TC-PROJ-RET-004", "REQ-PROJ-006", "AC-06", "Часткове повернення після скасування модифікації", "Скасувати MODIFICATION і повернути частину ресурсів.", "Відновлено лише вказану кількість.", automated=False),
    case("TC-PROJ-RET-005", "REQ-PROJ-006", "AC-01", "Повне повернення під час видалення етапу", "Видалити виконаний етап і вибрати повернення всіх ресурсів.", "Усі неповернуті ресурси етапу відновлено.", automated=False),
    case("TC-PROJ-RET-006", "REQ-PROJ-006", "AC-03", "Часткове повернення під час видалення етапу", "Видалити частково виконаний етап і вказати кількість повернення.", "Відновлено лише вказану кількість.", automated=False),
    case("TC-PROJ-RET-007", "REQ-PROJ-006", "AC-03", "Окремі кількості повернення для кількох ресурсів", "Повернути різні кількості кількох ресурсів одного проєкту.", "Залишок кожного ресурсу збільшився на його фактичну кількість повернення.", automated=False),
    case("TC-PROJ-RET-008", "REQ-PROJ-006", "AC-03", "Заборона повернення понад використану кількість", "Спробувати повернути більше ресурсу, ніж було використано.", "Операцію відхилено; залишок та історія не змінилися.", automated=False),
    case("TC-PROJ-RET-009", "REQ-PROJ-006", "AC-04", "Повторне часткове повернення в межах залишку", "Двічі частково повернути один ресурс у межах неповернутої кількості.", "Обидві операції виконані; доступний залишок повернення зменшувався після кожної.", automated=False),
    case("TC-PROJ-RET-010", "REQ-PROJ-006", "AC-04", "Повторне повернення недоступне після повного", "Повністю повернути ресурс і повторити дію.", "Повторна дія недоступна або відхилена; подвійного оприбуткування немає.", automated=False),
    case("TC-PROJ-RET-011", "REQ-PROJ-006", "AC-07", "Окремий запис для кожного часткового повернення", "Двічі частково повернути один ресурс.", "Створено два незмінні хронологічні записи «Повернено з проекту» з фактичними кількостями.", automated=False),
    case("TC-PROJ-RET-012", "REQ-OPER-HIST", "AC-19", "Контекст операції «Повернено з проекту»", "Повернути ресурс після скасування проєкту та після видалення етапу.", "Записи містять ресурс, кількість, проєкт, етап за наявності, дату, час і користувача.", automated=False),
    case("TC-PROJ-RET-013", "REQ-OPER-HIST", "AC-19", "Невдале повернення не створює запису історії", "Виконати невалідну спробу повернення понад доступний залишок.", "Новий запис «Повернено з проекту» не створений.", automated=False),
    case("TC-UI-PROJ-RET-001", "REQ-PROJ-006", "AC-01", "Швидкі дії повернення після скасування проєкту", "Скасувати проєкт із використаними ресурсами через UI.", "Доступні дії «Повернути всі ресурси» і «Повернути частково».", automated=False),
    case("TC-UI-PROJ-RET-002", "REQ-PROJ-006", "AC-01", "Швидкі дії повернення під час видалення етапу", "Видалити виконаний етап через UI.", "Доступний вибір повного або часткового повернення ресурсів етапу.", automated=False),
    case("TC-UI-PROJ-RET-003", "REQ-OPER-HIST", "AC-19", "Повернення відображається в UI як «Повернено з проекту»", "Виконати кілька часткових повернень і відкрити історію операцій.", "Кожне повернення показане окремим рядком із правильною кількістю та контекстом.", automated=False),
]


DEPRECATED_CASES = {
    "TC-PROJ-008": "[DEPRECATED] Залежність скасування від старої моделі продукту",
    "TC-PROJ-CAT-001": "[DEPRECATED] Каталог категорій проєктного виробництва",
    "TC-PROJ-CAT-002": "[DEPRECATED] Каталог продуктів проєктного виробництва",
    "TC-UI-PROJ-004": "[DEPRECATED] UI старого каталогу продуктів",
}


HISTORY_DOCUMENTATION_BLOCK = f"""{HISTORY_BLOCK_START}
## Проєктне виробництво

- Ресурс, використаний у `CREATION` або `MODIFICATION`, відображається як «Використано» з фактичною кількістю.
- Обладнання, виготовлене в `CREATION`, відображається як «Виготовлено» у власній історії, загальній історії обладнання та UI-таблиці.
- `MODIFICATION` не створює нове обладнання і не додає операцію «Виготовлено».
- Кожне повне або часткове повернення ресурсу створює окремий запис «Повернено з проекту» для кожного ресурсу.
- Запис повернення містить ресурс, кількість, проєкт, етап за наявності, дату, час і користувача. Невдала операція запису не створює.

Підтверджений стан dev від 22.09.2026: ресурси CREATION і MODIFICATION коректно відображаються як використані; власна історія виготовленого обладнання містить «Виготовлено». Відомий дефект: виготовлене обладнання відсутнє в загальній історії обладнання і через це не показується в UI. Очікувана вимога не змінюється.

Це не є підтвердженням виконання тестів на production ERP.
{HISTORY_BLOCK_END}"""


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
        req = urllib.request.Request(
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
            with urllib.request.urlopen(req, timeout=90, context=self.context) as response:
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


def replace_managed_block(documentation: str | None, block: str) -> str:
    current = documentation or ""
    if HISTORY_BLOCK_START in current and HISTORY_BLOCK_END in current:
        before = current.split(HISTORY_BLOCK_START, 1)[0].rstrip()
        after = current.split(HISTORY_BLOCK_END, 1)[1].lstrip()
        return "\n\n".join(part for part in (before, block, after) if part)
    return "\n\n".join(part for part in (current.rstrip(), block) if part)


def sync(client: TcmClient) -> dict[str, Any]:
    report: dict[str, Any] = {
        "baseUrl": client.base_url,
        "features": {"created": [], "updated": []},
        "acceptanceCriteria": {"created": [], "updated": []},
        "testCases": {"created": [], "updated": [], "deprecated": []},
        "verification": {},
    }

    for feature in FEATURES:
        feature_id = feature["featureId"]
        path = f"/api/ai/projects/{PROJECT_ID}/features/{urllib.parse.quote(feature_id, safe='')}"
        existing = client.get_optional(path)
        if existing is None:
            client.request("POST", f"/api/ai/projects/{PROJECT_ID}/features", feature)
            report["features"]["created"].append(feature_id)
        else:
            client.request("PUT", path, feature)
            report["features"]["updated"].append(feature_id)

    history_path = f"/api/ai/projects/{PROJECT_ID}/features/REQ-OPER-HIST"
    history_feature = client.request("GET", history_path)
    history_docs = replace_managed_block(history_feature.get("documentation"), HISTORY_DOCUMENTATION_BLOCK)
    client.request("PUT", history_path, {
        "title": history_feature["title"],
        "description": history_feature.get("description"),
        "documentation": history_docs,
    })
    report["features"]["updated"].append("REQ-OPER-HIST")

    ac_ids: dict[tuple[str, str], int] = {}
    for feature_id, criteria in ACCEPTANCE_CRITERIA.items():
        feature = client.request("GET", f"/api/ai/projects/{PROJECT_ID}/features/{urllib.parse.quote(feature_id, safe='')}")
        existing_by_key = {ac["acId"]: ac for ac in feature.get("acceptanceCriteria", [])}
        for ac_key, text in criteria:
            existing = existing_by_key.get(ac_key)
            if existing:
                updated = client.request(
                    "PUT",
                    f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria/{existing['id']}",
                    {"featureId": feature_id, "text": text},
                )
                report["acceptanceCriteria"]["updated"].append(f"{feature_id}/{ac_key}")
            else:
                updated = client.request(
                    "POST",
                    f"/api/ai/projects/{PROJECT_ID}/acceptance-criteria",
                    {"featureId": feature_id, "acKey": ac_key, "text": text},
                )
                report["acceptanceCriteria"]["created"].append(f"{feature_id}/{ac_key}")
            ac_ids[(feature_id, ac_key)] = int(updated["id"])

    for payload in CASES:
        test_id = payload["testId"]
        path = f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}"
        existing = client.get_optional(path)
        write_payload = dict(payload)
        write_payload["acceptanceCriterionId"] = ac_ids[(payload["featureId"], payload["acKey"])]
        if existing is None:
            client.request("POST", f"/api/ai/projects/{PROJECT_ID}/test-cases", write_payload)
            report["testCases"]["created"].append(test_id)
        else:
            client.request("PUT", path, write_payload)
            report["testCases"]["updated"].append(test_id)

    for test_id, deprecated_title in DEPRECATED_CASES.items():
        path = f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}"
        existing = client.get_optional(path)
        if existing is None:
            continue
        client.request("PUT", path, {"title": deprecated_title, "status": "DEPRECATED"})
        report["testCases"]["deprecated"].append(test_id)

    feature_checks: dict[str, Any] = {}
    for feature_id in [f["featureId"] for f in FEATURES] + ["REQ-OPER-HIST"]:
        read_back = client.request("GET", f"/api/ai/projects/{PROJECT_ID}/features/{urllib.parse.quote(feature_id, safe='')}")
        required_ac = {key for key, _ in ACCEPTANCE_CRITERIA.get(feature_id, [])}
        actual_ac = {ac["acId"] for ac in read_back.get("acceptanceCriteria", [])}
        feature_checks[feature_id] = {
            "title": read_back["title"],
            "status": read_back.get("status"),
            "requiredAcPresent": sorted(required_ac) == sorted(required_ac & actual_ac),
            "acCount": len(actual_ac),
            "documentationSha256": hashlib.sha256((read_back.get("documentation") or "").encode("utf-8")).hexdigest(),
        }

    case_checks: dict[str, Any] = {}
    for payload in CASES:
        test_id = payload["testId"]
        read_back = client.request("GET", f"/api/ai/projects/{PROJECT_ID}/test-cases/{urllib.parse.quote(test_id, safe='')}")
        case_checks[test_id] = {
            "featureId": read_back["featureId"],
            "acId": read_back["acId"],
            "status": read_back["status"],
            "apiAutomationIds": read_back.get("apiAutomationIds") or [],
            "uiAutomationIds": read_back.get("uiAutomationIds") or [],
            "stepCount": len(read_back.get("steps") or []),
        }
    report["verification"] = {
        "features": feature_checks,
        "testCases": case_checks,
        "allCasesPresent": len(case_checks) == len(CASES),
        "managedCaseCount": len(CASES),
        "automatedCaseCount": sum(bool(c["apiAutomationIds"] or c["uiAutomationIds"]) for c in CASES),
        "manualCaseCount": sum(not bool(c["apiAutomationIds"] or c["uiAutomationIds"]) for c in CASES),
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--token", help=argparse.SUPPRESS)
    parser.add_argument("--insecure", action="store_true", help="Allow the internal lab certificate")
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
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "result": report["result"],
        "baseUrl": report["baseUrl"],
        "features": {key: len(value) for key, value in report["features"].items()},
        "acceptanceCriteria": {key: len(value) for key, value in report["acceptanceCriteria"].items()},
        "testCases": {key: len(value) for key, value in report["testCases"].items()},
        "verification": {
            "allCasesPresent": report["verification"]["allCasesPresent"],
            "managedCaseCount": report["verification"]["managedCaseCount"],
            "automatedCaseCount": report["verification"]["automatedCaseCount"],
            "manualCaseCount": report["verification"]["manualCaseCount"],
        },
        "report": str(args.report),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
