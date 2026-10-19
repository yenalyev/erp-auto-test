# Dev-прогін після погодження тексту підказки

19.10.2026, Europe/Kyiv. Chromium; dev ERP https://dev.cukerochka.sbs; ADMIN та OWNER_1; JDBC вимкнений.

**9 PASS, 0 FAIL, 0 SKIP**, 9 автоматичних перевірок; конфігураційних помилок немає.

## Погоджена вимога

Користувач підтвердив поточний текст як правильний. AC-02 і TC-EQ-HINT-UI-003 тепер перевіряють точний текст правила, обидва приклади та звернення до адміністратора. Додаткова фраза про внесення в ресурси не вимагається. Ідентифікатори збережені; тест не вимкнено.

Попередній FAIL збережений у старих прогонах prod 129 / dev 201 як історія попередньої інтерпретації вимоги. Запис EQUIPMENT_KIND_HINT_RESOURCE_ROUTING.md закритий як погоджена поведінка. Цей звіт спирається на нове реальне виконання.

## Результати

- **TC-EQ-HINT-UI-001 — PASS**, ownerSeesAdviceBeforeEnteringEquipment, 2123 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-001-1.png).
- **TC-EQ-HINT-UI-002 — PASS**, adminCanReadExamplesAndContact, 13010 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-002-1.png).
- **TC-EQ-HINT-UI-003 — PASS**, ownerReadsApprovedClassificationText, 2846 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-003-1.png).
- **TC-EQ-HINT-UI-004 — PASS**, repeatedTogglingPreservesInputAndDoesNotSubmit, 2706 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-004-1.png).
- **TC-EQ-HINT-UI-005 — PASS**, batchItemsHaveIndependentAdvice, 7723 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-005-1.png).
- **TC-EQ-HINT-UI-006 — PASS**, keyboardActivatesBothAdviceControls, 2799 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-006-1.png).
- **TC-EQ-HINT-UI-007 — PASS**, expandedAdviceIsReadableAtDesktopAndNarrowViewport, 3335 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-007-1.png).
- **TC-EQ-HINT-UI-008 — PASS**, cancellationDoesNotCreateEquipmentAndReopenRestoresAdvice, 4467 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-EQ-HINT-UI-008-1.png).
- **TC-UI-EQ-002 — PASS**, createSeveralEquipmentInOneRelocation, 9425 ms.
  [Скріншот](equipment-kind-hint-approved-evidence/TC-UI-EQ-002-1.png).

## Дані та перевірка

Вісім кейсів підказки не створюють обладнання. Регресія TC-UI-EQ-002 створює дві власні одиниці на ізольованій дочірній локації під ADMIN, перевіряє фактичну локацію, журнал та одне EQUIPMENT-отримання. Cleanup видаляє тільки отримання з підтвердженими ID обох одиниць, перевіряє GET кожного ID=404 і архівує власну локацію. Усі cleanup-конфігурації завершилися PASS.

Окрема деталізація бухгалтерської категоризації залишається TC-EQ-HINT-MAN-001 DRAFT/NOT_RUN. Вона не блокує прийняття погодженого тексту.

Команда: `mvn test -Denv=dev -Dsuite=equipment-kind-hint -Duse.database=false -Dtcm.enabled=false -Dgoogle.sheets.enabled=false -Dsuite.artifact.sweep=false -Dtest.output.directory=target/equipment-kind-hint-dev-final -Dallure.results.directory=target/equipment-kind-hint-approved/allure-results`.

Лог: target/equipment-kind-hint-approved.log. Allure: target/equipment-kind-hint-approved/allure-results. Результати JSON та скріншоти збережені поруч із цим звітом.

## Синхронізація TCM

Погоджені документація REQ-EQU-HINT, AC-02 та кейс TC-EQ-HINT-UI-003 збережені в prod і dev TCM; ID фічі, AC і кейса збережені. У ручному DRAFT уточнено, що погодження поточного тексту вже завершене, а окрема деталізація категоризації лишається відкритою.

- Prod TCM: [підказка — run 131](https://tcm.lab/test-runs/131), [створення — run 132](https://tcm.lab/test-runs/132).
- Dev TCM: [підказка — run 203](http://localhost:18100/test-runs/203), [створення — run 204](http://localhost:18100/test-runs/204).

Усі результати мають environment=dev; 8+1 статусів PASS перечитані та звірені в кожному TCM. Немає unmatched або out-of-scope результатів. Історичні прогони збережені.
