# Експорт даних — Переміщення: dev, 2026-10-21 — 2026-10-22

ERP https://dev.cukerochka.sbs; Java 21, Chrome, прямий read-only JDBC через VPN; SSH вимкнено.

Фактичні виконання: {"PASS": 6, "FAIL": 2}.
TCM (пропуски через setup → BLOCKED): {"PASS": 6, "FAIL": 2}.
Активних конфігураційних помилок: 0. Історичних: 2.

Історичні помилки закрито перевіреним cleanup recovery та успішним повтором залежних кейсів. Повтор завершився без конфігураційних помилок.

Після відновлення dev виконано API-006 і обидва UI-кейси в `target\rel-columns-recovery-20261022`. У підсумку використано ці останні реальні результати; попередні BLOCKED/помилки збережено як supersededInvocations.

- TC-REL-COL-API-001: **PASS**.
- TC-REL-COL-API-002: **PASS**.
- TC-REL-COL-API-003: **FAIL**.

Multiple Failures (12 failures)

-- failure 1 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595607206_T1 (Локація)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 2 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595607206_T1 (Локація)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 3 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595612919_T1 (Екіпаж)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 4 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595612919_T1 (Екіпаж)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 5 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595617370_T1 (Точка вильоту)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 6 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595617370_T1 (Точка вильоту)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 7 --

[rel-columns--loc_1792595621850_T1 → rel-columns--loc_1792595622312_T1 (Локація)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 8 --

[rel-columns--loc_1792595621850_T1 → rel-columns--loc_1792595622312_T1 (Локація)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 9 --

[rel-columns--loc_1792595621850_T1 → rel-columns--loc_1792595626698_T1 (Екіпаж)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 10 --

[rel-columns--loc_1792595621850_T1 → rel-columns--loc_1792595626698_T1 (Екіпаж)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExport
- TC-REL-COL-API-004: **FAIL**.

Multiple Failures (12 failures)

-- failure 1 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595607206_T1 (Локація)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 2 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595607206_T1 (Локація)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 3 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595612919_T1 (Екіпаж)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 4 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595612919_T1 (Екіпаж)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 5 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595617370_T1 (Точка вильоту)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 6 --

[rel-columns--loc_1792595606655_T1 → rel-columns--loc_1792595617370_T1 (Точка вильоту)]

expected: "СБС"

 but was: "СБС без ПМ"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 7 --

[rel-columns--loc_1792595650463_T1 → rel-columns--loc_1792595650961_T1 (Локація)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 8 --

[rel-columns--loc_1792595650463_T1 → rel-columns--loc_1792595650961_T1 (Локація)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 9 --

[rel-columns--loc_1792595650463_T1 → rel-columns--loc_1792595655382_T1 (Екіпаж)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsAssertions.java:64)

-- failure 10 --

[rel-columns--loc_1792595650463_T1 → rel-columns--loc_1792595655382_T1 (Екіпаж)]

expected: "ПМ"

 but was: "ПМ без Цукрарня"

at RelocationExportColumnsAssertions.assertGroups(RelocationExportColumnsA
- TC-REL-COL-API-005: **PASS**.
- TC-REL-COL-API-006: **PASS**.
- TC-REL-COL-UI-001: **PASS**.
- TC-REL-COL-UI-002: **PASS**.

[Контракт і кейси](../../REQ-REL-COL.md). [JSON результатів](relocation-export-columns-dev-results.json).
[TestNG](relocation-export-columns-dev-testng.xml). XLSX та скриншоти: `relocation-export-columns-evidence/`.

Перший запуск `target/rel-columns-initial-20261021` зупинився через мережеві обмеження sandbox (DNS БД/Chrome), пропущені тести не вважаються виконаними. Поточний звіт стосується target\rel-columns-verified-20261021.

TCM зберігає результати середовища dev в обох системах; ERP prod/stage цим запуском не перевірявся.

Документація, 6 AC, 8 кейсів і всі 8 automation-зв’язків перевірені повторним читанням:

- [Prod TCM](http://tcm.lab/projects/1?feature=176): прогін **156**, 8/8 статусів збігаються з JSON; [readback](relocation-export-columns-tcm-prod-final-run.json).
- [Stage TCM](http://localhost:18100/projects/1?feature=171): прогін **217**, 8/8 статусів збігаються з JSON; [readback](relocation-export-columns-tcm-stage-run.json).
- Попередній prod-прогін 155 з мережевими BLOCKED збережений як історія; остаточний результат — 156.

Проміжний запуск `target/rel-columns-dev-20261021` виявив застаріле очікування локального формату XLSX; читання виправлено під фактичний dev (аркуш Переміщення, 20 колонок). Розбіжність підписів груп ПМ/СБС залишена як FAIL за наданою вимогою.

Попередні cleanup намагалися видалити завершені передачі; dev забороняє це через API. Власні залишки прибрано, ресурси й локації архівовано. Поточна фікстура залишає завершену історію з ID в Allure, очищує залишки й архівує власні сутності. Конфігураційні результати фінальних запусків наведені вище без приховування помилок.

[Опис розбіжності груп](relocation-export-columns-group-label-mismatch.md).

## Cleanup після відновлення доступу

22.10.2026 усі 20 власних локацій перевірено за точними ID й назвами: залишків немає, active=false. Всі 4 ресурси архівовано. Завершена історія лишається за правилами API. [Перевірений recovery](relocation-export-columns-cleanup-recovery.json). Нові дані повторного прогону очищені його власною фікстурою; конфігурація повтору врахована вище.
