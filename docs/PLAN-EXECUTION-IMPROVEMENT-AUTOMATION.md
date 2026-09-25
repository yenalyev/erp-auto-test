# Автоматизація improvement `/plan-execution`

## Обсяг

Suite `plan-execution-improvement` перевіряє узгоджений контракт сторінки виконання плану:

- мультиселект категорій та його перетин із пошуком і «Лише обрані»;
- Excel поточних видимих рядків і всіх трьох аркушів: чинні шість колонок `За планом`, `Од. виміру` після `Категорія` у `Поза планом`, незмінні `Продукт | Кількість` у `Розбір`, поточну дату в `На складі` та згорнутий блок `Поза планом`;
- `Обрати все` / `Зняти все` у popup в межах поточного фільтра та перемикач `Активні` / `Архівні`;
- суму `Поза планом` лише для `шт/комп` і лише видимих рядків;
- фінальний стан production records після update/delete та незникнення історії після архівування техкарти;
- актуальну назву й категорію ресурсу без поділу рядка при їх зміні;
- один агрегований рядок на ресурс: production до й після зміни unit сумуються та показуються в останній одиниці ресурсу;
- фактичну можливість ADMIN змінити одиницю ресурсу.

Повна бізнес-специфікація й ручні edge cases: [REQ-PLAN-EXECUTION-IMPROVEMENT.md](REQ-PLAN-EXECUTION-IMPROVEMENT.md).

## Реалізація

- API: `PlanExecutionImprovementApiTest` — 5 сценаріїв.
- UI: `PlanExecutionImprovementUiTest` — 8 сценаріїв.
- TestNG suite: `src/test/resources/suites/plan-execution-improvement.xml`.
- XLSX читається Apache POI через `XlsxWorkbookReader`, а не пошуком тексту в ZIP/XML.
- TCM sync: `scripts/sync_tcm_plan_execution_improvement.py`.

## Запуск на dev

```powershell
mvn -o test "-Dsuite=plan-execution-improvement" "-Denv=dev" `
  "-Dsuite.artifact.sweep=false" "-Dgoogle.sheets.enabled=false" `
  "-Dtcm.enabled=false" "-Dmaven.repo.local=C:\Users\gigam\.m2\repository"
```

TCM синхронізація не публікує результатів тестового запуску. Вона ідемпотентно створює/оновлює feature, AC і test cases окремо в dev та prod TCM.

## Повнота

Усі 21 новий TCM-кейс автоматизовані. Зокрема, suite сам створює parent/child категорії,
перехоплює 500 для негативного export-path та керовано архівує/розархівує техкарти й ресурси,
щоб перевірити planned/out-of-plan, Active/Archived catalog, комбінацію категорії з `Архівні`, XLSX і metadata invariants. UI-сценарії використовують динамічно створеного користувача замість `OWNER_1`; тестові дані створюються під ADMIN.

## Результат DEV-прогону 24.09.2026

- Фінальний повний suite: **14 test methods, 11 passed, 3 product failures, 0 errors/skipped/BLOCKED/INCONCLUSIVE**; test time 352.2 с, Maven time 5 хв 59 с. Детальний звіт: `docs/audits/2026-09-23/plan-execution-improvement-dev-test-report.md`.
- Актуальний API-прогін після погодження агрегації unit: 5 tests, 4 passed, 1 failed.
- Підтверджений API-дефект: аркуш `Поза планом` має `Продукт | Категорія | Зроблено | На складі (<дата>)` без `Од. виміру`. Агрегація зміни unit (`2 шт + 3 кг` → один рядок `5 кг`) відповідає погодженому правилу.
- Поточний workbook DEV: `За планом` уже відповідає прийнятому контракту; `Розбір` лишається `Продукт | Кількість`.
- Automation blockers виправлені: category multiselect, filtered search, out-of-plan accordion/total та Active/Archived synchronization стабільно дійшли до бізнес-перевірок. Popup helper відстежує появу й завершення спінера через DOM observer. У фінальному прогоні немає `BLOCKED`, `INCONCLUSIVE`, locator timeout або network race.
- `TC-UI-PLANEXEC-023/029` пройшов: `Активні` знаходить лише активний resource, `Архівні` — archived output, прив'язаний до чинної техкарти; обидва exact-name запити та `isActive` перевірені.
- `TC-UI-PLANEXEC-022` пройшов за уточненою бізнес-логікою: category filter самої сторінки працює за exact ID, тому parent не включає child-resource автоматично.
- `TC-UI-PLANEXEC-025/026/027/028` використовує динамічно створеного користувача, а створення даних та архівацію виконує під ADMIN. Output-resource спочатку замінюється в техкарті окремим динамічним replacement-resource, потім повністю видаляється з inventory snapshot; окрема assertion перевіряє відсутність навіть із `showZeroStock=true`. Targeted DEV recheck пройшов: 1 test, 0 failures, 0 errors, 0 skipped.
- `TC-UI-PLANEXEC-015` стабільно падає через відсутню `Од. виміру` в `Поза планом`.
- `TC-UI-PLANEXEC-016` відокремлено від header assertion: після category + search + «Лише обрані» export містить hidden-by-search ресурс. Вимога «Excel = лише поточні видимі рядки» на DEV не виконана.
- Machine-readable результат фінального повного suite: `target/surefire-reports/TEST-TestSuite.xml`; stack traces: `target/surefire-reports/TestSuite.txt`.
