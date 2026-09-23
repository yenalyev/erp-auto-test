# Автоматизація improvement `/plan-execution`

## Обсяг

Suite `plan-execution-improvement` перевіряє узгоджений контракт сторінки виконання плану:

- мультиселект категорій та його перетин із пошуком і «Лише обрані»;
- Excel поточних видимих рядків і всіх трьох аркушів: чинні шість колонок `За планом`, `Од. виміру` після `Категорія` у `Поза планом`, незмінні `Продукт | Кількість` у `Розбір`, поточну дату в `На складі` та згорнутий блок `Поза планом`;
- `Обрати все` / `Зняти все` у popup в межах поточного фільтра та перемикач `Активні` / `Архівні`;
- суму `Поза планом` лише для `шт/комп` і лише видимих рядків;
- фінальний стан production records після update/delete та незникнення історії після архівування техкарти;
- актуальну назву й категорію ресурсу без поділу рядка при їх зміні;
- поділ одного ресурсу на різні рядки за snapshot-одиницею; стара одиниця не закриває ціль у новій;
- фактичну можливість ADMIN змінити одиницю ресурсу.

Повна бізнес-специфікація й ручні edge cases: [REQ-PLAN-EXECUTION-IMPROVEMENT.md](REQ-PLAN-EXECUTION-IMPROVEMENT.md).

## Реалізація

- API: `PlanExecutionImprovementApiTest` — 5 сценаріїв.
- UI: `PlanExecutionImprovementUiTest` — 7 сценаріїв.
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

Усі 20 нових TCM-кейсів автоматизовані. Зокрема, suite сам створює parent/child категорії,
перехоплює 500 для негативного export-path та керовано архівує/розархівує техкарти й ресурси,
щоб перевірити planned/out-of-plan, Active/Archived catalog, XLSX і metadata invariants.

## Результат DEV-прогону 23.09.2026

- Повний suite: 12 test methods, 1 passed, 11 failed. Падіння зафіксували відсутні improvement-контроли/поведінку на поточному DEV; негативний retry export пройшов.
- Повторний API-прогін після виправлення тестового мапінгу `producedByDay`: 5 tests, 3 passed, 2 failed.
- Підтверджені API-дефекти: аркуш `Поза планом` має `Продукт | Категорія | Зроблено | На складі (<дата>)` без `Од. виміру`; після зміни unit execution зливає стару й нову production-кількість в один рядок з новою одиницею замість окремих bucket (у контрольному кейсі `2 шт + 3 кг` повернулося як `5 кг`).
- Поточний workbook DEV: `За планом` уже відповідає прийнятому контракту; `Розбір` лишається `Продукт | Кількість`.
- Дві фінальні повторні спроби повного suite не дали нового продуктового результату: одна отримала `503 no available server`, інша — timeout сторінки OAuth під час class setup. Cleanup зроблено null-safe, після чого примусова повна компіляція завершилась `BUILD SUCCESS`.
- Повний лог: `target/plan-execution-improvement-dev.log`; контрольний API-лог: `target/plan-execution-improvement-api-dev.log`.
