# Експорт даних — Переміщення: погоджені назви груп, 2026-10-22

Погоджені 22.10.2026 точні значення: ПМ без Цукрарня, СБС без ПМ, Цукрарня, Інші. Скорочення очікувань до ПМ/СБС видалено; попередні FAIL збережені як історія.

Повний окремий dev-прогін: Java 21, Chrome, read-only JDBC; SSH вимкнено. Джерело: `target\rel-columns-approved-groups-20261022`.

Результат: {"PASS": 8}. Конфігураційних помилок: 0.

- TC-REL-COL-API-001: **PASS**.
- TC-REL-COL-API-002: **PASS**.
- TC-REL-COL-API-003: **PASS**.
- TC-REL-COL-API-004: **PASS**.
- TC-REL-COL-API-005: **PASS**.
- TC-REL-COL-API-006: **PASS**.
- TC-REL-COL-UI-001: **PASS**.
- TC-REL-COL-UI-002: **PASS**.

Перевірено порядок 20 колонок, три kind, матрицю 4 групи × 3 kind, незалежність груп відправника/отримувача, повну пагінацію Видано/Отримано, відсутність зайвих позицій із двома партіями, легітимні однакові передачі, повторний експорт, період та реальний UI download.

Cleanup фікстур очищує власні залишки та архівує ресурси й локації; завершена історія залишається за правилами ERP. Конфігураційні помилки враховано вище.

- [Контракт і кейси](../../REQ-REL-COL.md).
- [Машинні результати](relocation-export-columns-dev-results.json).
- [TestNG XML](relocation-export-columns-dev-testng.xml).
- XLSX і скриншоти: `relocation-export-columns-evidence/`.

Результати позначені середовищем dev у prod та stage TCM.

- [Prod TCM](http://tcm.lab/projects/1?feature=176): прогін **159**, 8 PASS; [повторне читання](relocation-export-columns-tcm-prod-run.json).
- [Stage TCM](http://localhost:18100/projects/1?feature=171): прогін **220**, 8 PASS; [повторне читання](relocation-export-columns-tcm-stage-run.json).
- Документація, 6 критеріїв, усі 8 кейсів та automation ID звірені з файлами репозиторію в обох TCM.
