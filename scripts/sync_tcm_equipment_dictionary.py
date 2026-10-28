#!/usr/bin/env python3
"""Synchronize equipment dictionary documentation/cases; never publishes run results.

Use an environment-specific TCM_AI_TOKEN. Production changes use tcm-ai-prod MCP;
local endpoint is http://localhost:18100. Read-before-write and read-back come from core.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
core.PROJECT_ID = 1
core.FEATURE_ID = "REQ-EQU-DICT"
core.FEATURE_PARENT_ID = None
core.FEATURE_TITLE = "Довідники — Обладнання"
core.FEATURE_DESCRIPTION = "Довідник моделей обладнання: категорії, метадані, сортування та пошук за назвою й категоріями."
core.FEATURE_MODULE = "EQU"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs" / "REQ-EQU-DICT.md"
core.ACCEPTANCE_CRITERIA = json.loads(r'''[
  [
    "AC-01",
    "У «Довідниках ресурсів» доступна вкладка «Обладнання», що відкриває довідник моделей equipment_model (/equipment-models)."
  ],
  [
    "AC-02",
    "Таблиця містить колонки «Назва моделі», «Категорії», «Кількість», «Створено», «Автор» і колонку дій архівації; дата й автор належать моделі."
  ],
  [
    "AC-03",
    "Кожна модель має один рядок. Категорії — усі унікальні equipment_category одиниць equipment з цією equipment_model, без сторонніх категорій; фільтрація не обрізає агрегат."
  ],
  [
    "AC-04",
    "Доступне сортування за назвою та датою створення ASC/DESC; типово український А–Я. Категорії та автор не сортуються. Пагінація зберігає глобальний порядок."
  ],
  [
    "AC-05",
    "Пошук за назвою знаходить повну назву й підрядок без урахування регістру; відсутній збіг дає порожній результат, очищення відновлює список."
  ],
  [
    "AC-06",
    "Вибір категорії залишає лише моделі з обладнанням цієї категорії; кілька категорій працюють як OR, назва з категоріями — AND; скидання повертає відповідні записи."
  ]
]''')
core.CASES = json.loads(r'''[
  {
    "testId": "TC-EQD-API-001",
    "acKey": "AC-03",
    "title": "Унікальні категорії моделі та метадані",
    "description": "Автоматизація: EquipmentDictionaryApiTest#modelAggregatesAllDistinctCategoriesAndMetadata. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Виконати GET без sort з modelName=marker. Перевірити кожен рядок і категорії за підготовленим графом.",
        "expectedText": "Рівно 4 різні моделі; А має A+B по одному разу, Ґ лише A, Є і Я лише B. Назви категорій правильні, createdAt валідний, createdBy заповнений."
      }
    ],
    "expectedResult": "Рівно 4 різні моделі; А має A+B по одному разу, Ґ лише A, Є і Я лише B. Назви категорій правильні, createdAt валідний, createdBy заповнений.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [
      "TC-EQD-API-001"
    ],
    "uiAutomationIds": []
  },
  {
    "testId": "TC-EQD-API-002",
    "acKey": "AC-04",
    "title": "Типове українське сортування А–Я",
    "description": "Автоматизація: EquipmentDictionaryApiTest#defaultsToUkrainianNameAscending. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Надіслати GET із modelName=marker без sort.",
        "expectedText": "Порядок А, Ґ, Є, Я незалежно від порядку створення."
      }
    ],
    "expectedResult": "Порядок А, Ґ, Є, Я незалежно від порядку створення.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [
      "TC-EQD-API-002"
    ],
    "uiAutomationIds": []
  },
  {
    "testId": "TC-EQD-API-003",
    "acKey": "AC-04",
    "title": "Назва та дата ASC/DESC",
    "description": "Автоматизація: EquipmentDictionaryApiTest#supportsBothDirectionsForNameAndCreationDate. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Послідовно виконати GET із sort=modelName,ASC; modelName,DESC; createdAt,ASC; createdAt,DESC.",
        "expectedText": "Назви мають очікуваний український порядок і зворотний; дати монотонні у відповідному напрямі, різних дат більше однієї; склад моделей незмінний."
      }
    ],
    "expectedResult": "Назви мають очікуваний український порядок і зворотний; дати монотонні у відповідному напрямі, різних дат більше однієї; склад моделей незмінний.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [
      "TC-EQD-API-003"
    ],
    "uiAutomationIds": []
  },
  {
    "testId": "TC-EQD-API-004",
    "acKey": "AC-05",
    "title": "Пошук назви, регістр, відсутній збіг та порожній фільтр",
    "description": "Автоматизація: EquipmentDictionaryApiTest#searchesNameAndHandlesNoMatches. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Знайти повну назву А; marker у верхньому регістрі; marker-missing; порівняти modelName='' з GET без modelName.",
        "expectedText": "Повна назва дає лише А; підрядок незалежно від регістру — 4 моделі; відсутній збіг — content=[] і totalElements=0; порожній фільтр має ті самі ID і total, що й запит без фільтра."
      }
    ],
    "expectedResult": "Повна назва дає лише А; підрядок незалежно від регістру — 4 моделі; відсутній збіг — content=[] і totalElements=0; порожній фільтр має ті самі ID і total, що й запит без фільтра.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [
      "TC-EQD-API-004"
    ],
    "uiAutomationIds": []
  },
  {
    "testId": "TC-EQD-API-005",
    "acKey": "AC-06",
    "title": "Категорії OR та повнота агрегату",
    "description": "Автоматизація: EquipmentDictionaryApiTest#filtersCategoriesWithoutTruncatingAggregates. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Застосувати категорію A, B, обидві та unused; у результаті B перевірити всі категорії моделі А.",
        "expectedText": "A → А+Ґ; B → А+Є+Я, причому А зберігає A+B; A OR B → усі 4 без дублів; unused → порожній список і total=0."
      }
    ],
    "expectedResult": "A → А+Ґ; B → А+Є+Я, причому А зберігає A+B; A OR B → усі 4 без дублів; unused → порожній список і total=0.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [
      "TC-EQD-API-005"
    ],
    "uiAutomationIds": []
  },
  {
    "testId": "TC-EQD-API-006",
    "acKey": "AC-06",
    "title": "Назва AND категорія та скидання",
    "description": "Автоматизація: EquipmentDictionaryApiTest#combinesAndClearsFilters. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Знайти Ґ з A, потім Ґ з B; прибрати категорію, потім назву.",
        "expectedText": "Ґ+A → Ґ; Ґ+B → порожньо; без категорії → Ґ; без фільтрів fixture → усі 4."
      }
    ],
    "expectedResult": "Ґ+A → Ґ; Ґ+B → порожньо; без категорії → Ґ; без фільтрів fixture → усі 4.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [
      "TC-EQD-API-006"
    ],
    "uiAutomationIds": []
  },
  {
    "testId": "TC-EQD-API-007",
    "acKey": "AC-04",
    "title": "Пагінація після сортування та фільтрації",
    "description": "Автоматизація: EquipmentDictionaryApiTest#paginatesSortedFilteredModelsWithoutDuplicates. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Отримати page=0 і page=1 з size=2. Потім categoryIds=A, size=1, page=1.",
        "expectedText": "Кожна сторінка має правильний number, size, totalElements=4, totalPages=2; об’єднання А,Ґ,Є,Я без повторів. Для фільтра A: total=2 і на другій сторінці Ґ."
      }
    ],
    "expectedResult": "Кожна сторінка має правильний number, size, totalElements=4, totalPages=2; об’єднання А,Ґ,Є,Я без повторів. Для фільтра A: total=2 і на другій сторінці Ґ.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "FUNCTIONAL",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [
      "TC-EQD-API-007"
    ],
    "uiAutomationIds": []
  },
  {
    "testId": "TC-EQD-UI-001",
    "acKey": "AC-01",
    "title": "Відкриття вкладки з довідників ресурсів",
    "description": "Автоматизація: EquipmentDictionaryUiTest#opensEquipmentTabFromResourceDictionaries. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Відкрити /resources; натиснути вкладку «Обладнання»; знайти marker.",
        "expectedText": "URL /equipment-models; успішне завантаження API та всі 4 контрольні моделі."
      }
    ],
    "expectedResult": "URL /equipment-models; успішне завантаження API та всі 4 контрольні моделі.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "UI",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": [
      "TC-EQD-UI-001"
    ]
  },
  {
    "testId": "TC-EQD-UI-002",
    "acKey": "AC-02",
    "title": "Точні назви колонок",
    "description": "Автоматизація: EquipmentDictionaryUiTest#showsRequiredColumnLabels. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Відкрити довідник і прочитати заголовки таблиці.",
        "expectedText": "«Назва моделі», «Категорії», «Кількість», «Створено», «Автор» і порожній заголовок колонки дій у цьому порядку."
      }
    ],
    "expectedResult": "«Назва моделі», «Категорії», «Кількість», «Створено», «Автор» і порожній заголовок колонки дій у цьому порядку.",
    "priority": "HIGH",
    "severity": "MINOR",
    "status": "ACTIVE",
    "testType": "UI",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": [
      "TC-EQD-UI-002"
    ]
  },
  {
    "testId": "TC-EQD-UI-003",
    "acKey": "AC-03",
    "title": "Відображення категорій, дати й автора моделі",
    "description": "Автоматизація: EquipmentDictionaryUiTest#rendersCategoriesAndModelAuditFields. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Знайти marker. Для кожної моделі перевірити назву й категорії за fixture, дату й автора за API, відсутність кнопок сортування категорій та автора.",
        "expectedText": "Один рядок на модель, правильні категорії без дублів і сторонніх значень. Кількість збігається з equipmentCount API. Дата dd.MM.yyyy HH:mm у timezone браузера та автор збігаються з API. Категорії й автор не сортуються."
      }
    ],
    "expectedResult": "Один рядок на модель, правильні категорії без дублів і сторонніх значень. Кількість збігається з equipmentCount API. Дата dd.MM.yyyy HH:mm у timezone браузера та автор збігаються з API. Категорії й автор не сортуються.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "UI",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": [
      "TC-EQD-UI-003"
    ]
  },
  {
    "testId": "TC-EQD-UI-004",
    "acKey": "AC-04",
    "title": "Типове та інтерактивне сортування",
    "description": "Автоматизація: EquipmentDictionaryUiTest#sortsNamesAndCreationDatesInBothDirections. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Знайти marker; перевірити початковий порядок; переключити сортування назви та дати у двох напрямках.",
        "expectedText": "Типово А,Ґ,Є,Я; DESC — Я,Є,Ґ,А; після повернення ASC — початковий порядок. Дата ASC/DESC відповідає API й зберігає 4 моделі."
      }
    ],
    "expectedResult": "Типово А,Ґ,Є,Я; DESC — Я,Є,Ґ,А; після повернення ASC — початковий порядок. Дата ASC/DESC відповідає API й зберігає 4 моделі.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "UI",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": [
      "TC-EQD-UI-004"
    ]
  },
  {
    "testId": "TC-EQD-UI-005",
    "acKey": "AC-05",
    "title": "Пошук назви та відновлення після порожнього результату",
    "description": "Автоматизація: EquipmentDictionaryUiTest#searchesNameAndClearsEmptyResult. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Ввести повну назву А, marker у верхньому регістрі, marker-missing; очистити поле; знову ввести marker.",
        "expectedText": "Лише А; потім 4 моделі; потім жодного рядка і «Немає записів»; очищення відновлює непорожній список; marker повертає всі 4."
      }
    ],
    "expectedResult": "Лише А; потім 4 моделі; потім жодного рядка і «Немає записів»; очищення відновлює непорожній список; marker повертає всі 4.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "UI",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": [
      "TC-EQD-UI-005"
    ]
  },
  {
    "testId": "TC-EQD-UI-006",
    "acKey": "AC-06",
    "title": "Категорії, перетин із назвою та скидання",
    "description": "Автоматизація: EquipmentDictionaryUiTest#filtersCategoriesAndCombinesWithName. Тест-план: docs/REQ-EQU-DICT.md.",
    "preconditions": "ADMIN, доступний dev ERP; EquipmentDictionaryFixture підготував власну локацію, моделі та категорії.",
    "testData": "А: 2 одиниці A + 1 B; Ґ: A; Є: B; Я: B; unused без обладнання; унікальний marker.",
    "steps": [
      {
        "stepOrder": 1,
        "actionText": "Вибрати A, додати B, скинути; вибрати unused і скинути; знайти Ґ з B, скинути категорії, вибрати A.",
        "expectedText": "A → А+Ґ; A+B → усі 4; unused → порожньо; скидання відновлює моделі. Ґ+B → порожньо, скидання → Ґ, Ґ+A → Ґ."
      }
    ],
    "expectedResult": "A → А+Ґ; A+B → усі 4; unused → порожньо; скидання відновлює моделі. Ґ+B → порожньо, скидання → Ґ, Ґ+A → Ґ.",
    "priority": "HIGH",
    "severity": "MAJOR",
    "status": "ACTIVE",
    "testType": "UI",
    "tags": "equipment,dictionary,regression,automated",
    "apiAutomationIds": [],
    "uiAutomationIds": [
      "TC-EQD-UI-006"
    ]
  }
]''')

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token-env", default="TCM_AI_TOKEN")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    token = os.environ.get(args.token_env)
    if not token:
        raise SystemExit(f"Set {args.token_env} for the selected environment")
    report = core.sync(core.TcmClient(args.base_url, token, False))
    check = report["verification"]
    expected = {c["testId"]: c for c in core.CASES}
    assert check["documentationMatchesSource"] and check["requiredAcPresent"]
    for test_id, saved in check["testCases"].items():
        source = expected[test_id]
        assert saved["featureId"] == core.FEATURE_ID
        assert saved["acId"] == source["acKey"]
        assert saved["apiAutomationIds"] == source["apiAutomationIds"]
        assert saved["uiAutomationIds"] == source["uiAutomationIds"]
        assert saved["status"] == "ACTIVE" and saved["stepCount"] == len(source["steps"])
    assert check["allCasesPresent"] and check["managedCaseCount"] == 13
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        "# Синхронізація TCM — Довідники: Обладнання\n\n"
        f"Endpoint: {args.base_url}; project 1.\n\n"
        "REQ-EQU-DICT: документація перевірена повторним читанням.\n"
        "6 критеріїв і 13 ACTIVE кейсів; усі automation links та кроки перевірені.\n"
        "Результати виконання не публікувалися.\n\n"
        + "\n".join(f"- {key}: {value['acId']}; API={value['apiAutomationIds']}; UI={value['uiAutomationIds']}"
                    for key, value in check["testCases"].items()) + "\n",
        encoding="utf-8",
    )
    print(f"Verified {core.FEATURE_ID}: 6 criteria, 13 cases; {args.base_url}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
