# Списання ресурсів МШП — dev, 26.10.2026

Виконання: 32 PASS / 0 FAIL / 0 BLOCKED / 0 SKIP.
Кейси TCM: 19 PASS / 0 FAIL / 0 BLOCKED. Configuration/cleanup failures: 0.

Команда: `powershell -ExecutionPolicy Bypass -File scripts/run_low_value_write_off_dev.ps1`.
Каталог виконання: `target/low-value-write-off-dev-20261026-accepted`. Час Europe/Kyiv (UTC+02:00).

Cleanup пройшов: власні списання й отримання видалені, нульові рядки прибрані, локації/ресурси архівовані, актори деактивовані.

Погоджений контракт від 26.10.2026:

- Повна підказка МШП у редагуванні ресурсу необов’язкова; UI-002 перевіряє пояснення на формі списання.
- Select замість чекбокса у словнику прийнято як штатну поведінку; UI-004 перевіряє цей елемент, UI-003 — фільтрацію та скидання.
- Попередні прогони 168/229 перевіряли попередню редакцію очікувань. Їхні фактичні результати збережені в історії; поточний звіт містить нове виконання.

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
- TC-MSHP-UI-002: **PASS**, виконань 1.
- TC-MSHP-UI-003: **PASS**, виконань 1.
- TC-MSHP-UI-004: **PASS**, виконань 1.
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

- [Прогін dev у TCM prod: 169](http://tcm.lab/test-runs/169).
- [Прогін dev у TCM stage: 230](http://localhost:18100/test-runs/230).