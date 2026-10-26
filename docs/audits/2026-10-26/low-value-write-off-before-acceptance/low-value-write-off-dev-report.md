# Списання ресурсів МШП — dev, 26.10.2026

Виконання: 30 PASS / 2 FAIL / 0 BLOCKED / 0 SKIP.
Кейси TCM: 17 PASS / 2 FAIL / 0 BLOCKED. Configuration/cleanup failures: 0.

Команда: `powershell -ExecutionPolicy Bypass -File scripts/run_low_value_write_off_dev.ps1`.
Каталог виконання: `target/low-value-write-off-dev-20261026-final`. Час Europe/Kyiv (UTC+02:00).

Перевірені невідповідності вимогам:

- **UI-002:** у редагуванні ресурсу немає повної підказки МШП.
- **UI-004:** у словнику немає чекбокса «МШП»; реалізовано select. Його функціональна фільтрація проходить окремий UI-003.

Усі 21 API-виконання пройшли; UI — 9 PASS / 2 FAIL. Maven завершується з кодом 1 через ці дві перевірки вимог.
Cleanup пройшов: власні списання й отримання видалені, нульові рядки прибрані, локації/ресурси архівовані, актори деактивовані. Дані попереднього прогону також очищені за явним manifest.

## Фактичні результати

- TC-MSHP-API-001: **PASS**, виконань 1.
- TC-MSHP-API-002: **PASS**, виконань 1.
- TC-MSHP-API-003: **PASS**, виконань 1.
- TC-MSHP-API-004: **PASS**, виконань 13.
- TC-MSHP-API-005: **PASS**, виконань 1.
- TC-MSHP-API-006: **PASS**, виконань 1.
- TC-MSHP-API-007: **PASS**, виконань 1.
- TC-MSHP-API-008: **PASS**, виконань 1.
- TC-MSHP-API-009: **PASS**, виконань 1.
- TC-MSHP-UI-001: **PASS**, виконань 1.
- TC-MSHP-UI-002: **FAIL**, виконань 1.

```text
parameters=[{"name": "URL", "value": "https://dev.cukerochka.sbs/resources/update/5654"}]
Locator expected to be visible
Call log:
  - Locator.expect with timeout 5000ms
  - waiting for getByText("МШП (малоцінні та швидкозношувані предмети) - засоби, що використовують у процесі виробництва продукції і підпадають під списання", new Page.GetByTextOptions().setExact(true))
```

- TC-MSHP-UI-003: **PASS**, виконань 1.
- TC-MSHP-UI-004: **FAIL**, виконань 1.

```text
parameters=[]
Locator expected to be visible
Call log:
  - Locator.expect with timeout 5000ms
  - waiting for getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("МШП").setExact(true))
```

- TC-MSHP-UI-005: **PASS**, виконань 1.
- TC-MSHP-UI-006: **PASS**, виконань 1.
- TC-MSHP-UI-007: **PASS**, виконань 1.
- TC-MSHP-UI-008: **PASS**, виконань 2.
- TC-MSHP-UI-009: **PASS**, виконань 1.
- TC-MSHP-UI-010: **PASS**, виконань 1.

## Артефакти

- [Вимоги та 19 кейсів](../../REQ-MSHP-resource-write-off.md).
- [Усі виконання та TCM payload](low-value-write-off-dev-results.json).
- [TestNG](low-value-write-off-evidence/testng-results.xml).
- Скріншоти — `low-value-write-off-evidence/`; HTTP attachments — Allure каталогу виконання.
- [TCM prod](http://tcm.lab/projects/1?feature=180), [TCM stage](http://localhost:18100/projects/1?feature=175).

- [Прогін dev у TCM prod: 168](http://tcm.lab/test-runs/168).
- [Прогін dev у TCM stage: 229](http://localhost:18100/test-runs/229).