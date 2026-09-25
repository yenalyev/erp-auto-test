#!/usr/bin/env python3
"""Synchronize CPMA-825 tech-map editing documentation and cases with TCM."""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_tcm_relocation_batch_accounting as core  # noqa: E402

core.FEATURE_ID = "REQ-GLOBAL-PLAN"
core.FEATURE_PARENT_ID = "REQ-PLN"
core.FEATURE_TITLE = "Глобальний план"
core.FEATURE_DESCRIPTION = ""
core.FEATURE_MODULE = "GLOBAL"
core.FEATURE_PRIORITY = "MEDIUM"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-GLOBAL-PLAN-TECH-MAP-EDIT.md"
core.ACCEPTANCE_CRITERIA = [
    (
        "AC-GP-16",
        "Техкарту з decomposition snapshot актуального глобального плану можна перейменувати без "
        "зміни версії або структурно відредагувати зі створенням нової активної version+1. Старий id "
        "лишається у snapshot, тому UI показує пов'язані поточні/майбутні плани, просить підтвердження "
        "structural edit і вимагає повторного призначення та generate. Архівація карти актуального "
        "snapshot лишається забороненою для OWNER і ADMIN; DELETE є soft-archive. Минулий план або "
        "відсутній snapshot не блокують edit/archive, а replacement-карту можна використати в новому GP.",
    ),
]


def steps(*rows: tuple[str, str]) -> list[dict[str, Any]]:
    return [
        {"stepOrder": index, "actionText": action, "expectedText": expected}
        for index, (action, expected) in enumerate(rows, start=1)
    ]


def case(
    test_id: str,
    title: str,
    description: str,
    preconditions: str,
    expected: str,
    case_steps: list[dict[str, Any]],
) -> dict[str, Any]:
    is_ui = "-UI-" in test_id
    return {
        "featureId": core.FEATURE_ID,
        "acKey": "AC-GP-16",
        "testId": test_id,
        "title": title,
        "description": description,
        "priority": "CRITICAL",
        "severity": "MAJOR",
        "status": "ACTIVE",
        "testType": "UI" if is_ui else "FUNCTIONAL",
        "preconditions": preconditions,
        "expectedResult": expected,
        "tags": "global-plan,tech-map,edit,versioning,automated",
        "apiAutomationIds": [] if is_ui else [test_id],
        "uiAutomationIds": [test_id] if is_ui else [],
        "steps": case_steps,
    }


core.CASES = [
    case(
        "TC-GP-046",
        "Архівація техкарти з майбутнього global-plan snapshot заборонена",
        "DELETE є soft-archive, але точний id карти у generated snapshot актуального GP захищений guard.",
        "OWNER_1 або ADMIN; EDIT_ALLOWED; майбутній GP після generate містить M1 у decomposition snapshot.",
        "HTTP 400 з описом пов'язаного GP; M1 лишається активною; кількість активних карт не змінюється.",
        steps(
            ("Створити майбутній GP, виконати decompose і generate з M1.", "Snapshot містить id M1."),
            ("Виконати DELETE M1 з storageId від OWNER_1 та ADMIN.", "HTTP 400 з посиланням на GP."),
            ("Запитати активні карти після кожної відмови.", "M1 активна; active count не змінився."),
        ),
    ),
    case(
        "TC-GP-049",
        "Архівація техкарти дозволена, якщо global plan не має snapshot",
        "Створення GP без generate не формує decomposition snapshot і не активує archive guard.",
        "OWNER_1 або ADMIN; EDIT_ALLOWED; ізольована M1; GP створено, але decompose/generate не виконано.",
        "DELETE повертає 200; M1 деактивована й відсутня у списку активних карт.",
        steps(
            ("Створити ізольовану M1 та глобальний план без generate.", "У GP decomposition=null."),
            ("Виконати DELETE M1 з storageId.", "HTTP 200."),
            ("Запитати активні карти за назвою M1.", "Active count зменшився на 1; M1 архівована."),
        ),
    ),
    case(
        "TC-GP-053",
        "Зміна назви техкарти з майбутнього GP без нової версії",
        "Name-only PUT карти з decomposition snapshot майбутнього GP не є structural edit.",
        "OWNER_1 або ADMIN; EDIT_ALLOWED; майбутній GP після generate містить M1 у snapshot.",
        "HTTP 200; id/version без змін; нова назва збережена.",
        steps(
            ("Створити й згенерувати майбутній GP зі snapshot M1.", "Snapshot містить id M1."),
            ("PUT M1 зі зміненою лише назвою.", "HTTP 200; id і version ті самі."),
            ("GET M1.", "Повертається нова назва."),
        ),
    ),
    case(
        "TC-GP-054",
        "Structural edit техкарти з майбутнього GP створює version+1",
        "Structural PUT дозволений попри актуальний snapshot; snapshot не мігрує автоматично.",
        "OWNER_1 або ADMIN; EDIT_ALLOWED; ізольована M1 у snapshot майбутнього GP.",
        "HTTP 200; новий id/version+1 активний; M1 неактивна; snapshot продовжує містити id M1.",
        steps(
            ("Створити й згенерувати майбутній GP зі snapshot M1.", "Snapshot містить id M1."),
            ("PUT M1 зі зміненою нормою input.", "HTTP 200; створено M2 з version+1 і новим id."),
            ("GET M1, M2 і GP.", "M1 immutable/inactive; M2 active; snapshot містить M1, не M2."),
        ),
    ),
    case(
        "TC-GP-056",
        "Архівація техкарти дозволена для суто історичного snapshot",
        "Snapshot минулого періоду зберігає історичний id, але не блокує soft-archive карти.",
        "ADMIN; EDIT_ALLOWED; ізольована M1 є лише у generated snapshot GP минулого місяця.",
        "DELETE повертає 200; M1 неактивна; історичний snapshot продовжує містити id M1.",
        steps(
            ("Створити generated GP з M1 і перевести план та location plan у минулий період.", "M1 є лише в historical snapshot."),
            ("Виконати DELETE M1 з storageId.", "HTTP 200; M1 архівована."),
            ("GET історичний GP та active-list техкарт.", "Snapshot містить id M1; active-list M1 не містить."),
        ),
    ),
    case(
        "TC-GP-058",
        "Зміна назви техкарти з GP поточного місяця без нової версії",
        "Name-only PUT поточного місяця має ту саму семантику, що й майбутній період.",
        "OWNER_1 або ADMIN; EDIT_ALLOWED; вільний поточний місяць; M1 у generated snapshot.",
        "HTTP 200; id/version без змін; нова назва збережена.",
        steps(
            ("Створити GP поточного місяця й generate з M1.", "Snapshot містить M1."),
            ("PUT — змінити лише назву.", "HTTP 200; id/version без змін."),
        ),
    ),
    case(
        "TC-GP-059",
        "Structural edit техкарти з GP поточного місяця створює version+1",
        "Поточний місяць не блокує structural PUT; тест не змінює чужий план, якщо період зайнятий.",
        "ADMIN; EDIT_ALLOWED; вільний поточний місяць; ізольована M1 у generated snapshot.",
        "HTTP 200; M2 має новий id/version+1; snapshot продовжує містити M1.",
        steps(
            ("Створити GP поточного місяця й generate з M1.", "Snapshot містить M1."),
            ("PUT M1 зі зміненою нормою input.", "HTTP 200; створено активну M2."),
            ("GET M1, M2 і GP.", "M1 immutable/inactive; snapshot не переписаний."),
        ),
    ),
    case(
        "TC-GP-060",
        "Replacement-техкарта після архівації працює в новому глобальному плані",
        "Дозволений archive без live snapshot не повинен ламати створення карти на той самий output та новий planning flow.",
        "ADMIN; EDIT_ALLOWED; ізольована M1 без snapshot актуального GP.",
        "M1 архівована; M2 активна; decompose/generate нового GP повертають 200; snapshot містить M2, не M1.",
        steps(
            ("Створити ізольовану M1 та виконати DELETE.", "HTTP 200; M1 відсутня в active-list."),
            ("Створити M2 на той самий output-продукт.", "M2 має новий id та є активною; M1 лишається неактивною."),
            ("Створити новий майбутній GP і призначити M2 під час decompose.", "Decompose complete=true, HTTP 200."),
            ("Виконати generate з тією самою decomposition.", "HTTP 200; location plans створено."),
            ("GET нового GP.", "Snapshot містить id M2 і не містить id M1."),
        ),
    ),
    case(
        "TC-GP-063",
        "Endpoint актуальних глобальних планів техкарти",
        "GET для форми редагування повертає current/future plan references і порожній список для unused map.",
        "ADMIN; generated майбутній GP зі snapshot M1; окрема невикористана M-unused.",
        "Для M1 повернуто id/description/from/to плану; для M-unused повернуто [].",
        steps(
            ("GET /technological-maps/{M1}/global-plans.", "Один reference з точними полями GP."),
            ("GET /technological-maps/{M-unused}/global-plans.", "Порожній список."),
        ),
    ),
    case(
        "TC-GP-UI-063",
        "UI warning і confirmation для structural edit карти з GP",
        "Форма показує пов'язані live GP і виконує structural PUT лише після явного підтвердження.",
        "ADMIN; EDIT_ALLOWED; ізольована M1 у snapshot майбутнього GP.",
        "Warning містить GP/link; modal пояснює reassignment; PUT=200; version+1 створена; snapshot не змінений.",
        steps(
            ("Відкрити update M1.", "Warning містить опис і link на GP."),
            ("Змінити input amount і натиснути «Зберегти».", "Відкрито modal «Створити нову версію техкарти?»."),
            ("Підтвердити збереження.", "PUT=200; M2 active version+1; snapshot лишив id M1."),
        ),
    ),
    case(
        "TC-GP-UI-064",
        "Confirmation popup під час редагування техкарти з глобального плану",
        "Після structural edit і натискання «Зберегти» форма показує popup про versioning і повторний розподіл.",
        "ADMIN; EDIT_ALLOWED; ізольована M1 у generated snapshot майбутнього GP.",
        "Popup має точні title/description та кнопки; «Скасувати» не виконує PUT і не створює version+1.",
        steps(
            ("Створити й згенерувати майбутній GP зі snapshot M1.", "Snapshot містить id M1."),
            ("Відкрити M1, змінити input amount і натиснути «Зберегти».", "Відкрито confirmation popup."),
            (
                "Перевірити title, description з назвою GP та кнопки popup.",
                "Текст збігається дослівно; доступні «Скасувати» і «Зберегти».",
            ),
            ("Натиснути «Скасувати» і повторно прочитати M1 через API.", "Popup закрито; id/version/input amount не змінилися."),
        ),
    ),
]


if __name__ == "__main__":
    raise SystemExit(core.main())
