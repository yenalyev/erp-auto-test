# Звіт повного DEV-прогону: `/plan-execution` improvement

Дата первинного прогону: 23.09.2026
Актуалізовано після повторного прогону: 24.09.2026
Середовище: DEV (`https://dev.cukerochka.sbs`)
Suite: `src/test/resources/suites/plan-execution-improvement.xml`
Команда: `mvn -o test -Dsuite=plan-execution-improvement -Denv=dev -Dsuite.artifact.sweep=false -Dgoogle.sheets.enabled=false -Dtcm.enabled=false`
Результат: **FAIL**

## Підсумок

| Метрика | Результат |
|---|---:|
| Test methods | 14 |
| Passed | 11 |
| Failed | 3 |
| Errors | 0 |
| Skipped | 0 |
| Тривалість тестів | 352.2 с |
| Повна тривалість Maven | 5 хв 59 с |
| TCM-кейсів у suite | 21 |
| TCM ID зі статусом PASS | 18 |
| TCM ID зі статусом FAIL | 3 |
| BLOCKED / INCONCLUSIVE через automation | 0 |

Один Java method може покривати кілька TCM ID. Якщо method упав до пізніших перевірок, усі прикріплені ID технічно мають failed-статус, але нижче окремо вказано, які assertions фактично були виконані.

## Результати API

| TCM ID | Test method | Статус | Результат |
|---|---|---|---|
| `TC-API-PLANEXEC-001` | `categoryFilterUsesLatestResourceCategory` | PASS | Фільтр використовує актуальну категорію; записи агрегуються в один рядок. |
| `TC-API-PLANEXEC-002` | `exportHasRequiredColumnsAndFilteredRows` | FAIL | На аркуші `Поза планом` відсутня колонка `Од. виміру`. |
| `TC-API-PLANEXEC-003` | `archivedTechMapDoesNotRemoveProductionHistory` | PASS | Production лишається в execution після архівації техкарти. |
| `TC-API-PLANEXEC-004` | `executionUsesFinalProductionState` | PASS | Update `5 → 7` і delete враховують лише фінальний стан. |
| `TC-API-RES-007` | `unitChangeKeepsOneAggregatedExecutionRow` | PASS | Після зміни unit повертається один рядок: `2 + 3 = 5 кг`. |

Фактична структура workbook:

- `За планом`: `Продукт | Категорія | Ціль | Од. вимір | Зроблено | На складі (23.09.2026)` — відповідає контракту.
- `Поза планом`: `Продукт | Категорія | Зроблено | На складі (23.09.2026)` — **немає `Од. виміру` після `Категорія`**.
- `Розбір`: `Продукт | Кількість` — відповідає контракту.

## Результати UI

| TCM ID | Test method | Статус | Результат |
|---|---|---|---|
| `TC-UI-PLANEXEC-014`, `021` | `categoryMultiselectCombinesWithOtherFilters` | PASS | Мультиселект категорій працює разом із пошуком і `Лише обрані`; очікування синхронізоване з React-render. |
| `TC-UI-PLANEXEC-015` | `exportHasRequiredWorkbookStructure` | FAIL | Повторно підтверджено відсутність `Од. виміру` у `Поза планом`. |
| `TC-UI-PLANEXEC-016` | `exportUsesCurrentVisibleRows` | FAIL | Після category + search + «Лише обрані» файл містить hidden-by-search ресурс; перевірка більше не блокується header assertion. |
| `TC-UI-PLANEXEC-017`, `018` | `manageSelectedResourcesBulkActionsRespectFilters` | PASS | `Обрати все → Зберегти (1)`, `Зняти все → Зберегти (0)` і Active/Archived межі bulk-дій пройшли. |
| `TC-UI-PLANEXEC-019`, `020` | `outOfPlanTotalUsesOnlyPcsAndKits` | PASS | Total знайдено в актуальному `tr`; підтверджено сумування лише `шт/комп` та виключення інших одиниць. |
| `TC-UI-PLANEXEC-022` | `parentCategoryUsesExactMatchOnExecutionPage` | PASS | Уточнена бізнес-логіка підтверджена: parent на сторінці не включає child автоматично; child треба вибрати окремо. |
| `TC-UI-PLANEXEC-024` | `exportErrorDoesNotCreateFileAndAllowsRetry` | PASS | Error feedback показано, файл не створено, export-кнопка доступна повторно. |
| `TC-UI-PLANEXEC-023`, `029` | `nameSearchSeparatesActiveAndArchivedResources` | PASS | Helper дочікується повного lifecycle спінера; Active/Archived exact-name search і межі `isActive=true/false` пройшли з валідним rebound-tech-map fixture. |
| `TC-UI-PLANEXEC-025`, `026`, `027`, `028` | `archiveUnarchivePreservesExecutionHistoryAndMetadata` | PASS | Пройшов у фінальному повному suite: динамічні ресурси й користувач, штатна архівація під ADMIN, planned/out-of-plan history, metadata, Excel та інваріант після розархівації. |

## Підтверджені дефекти продукту

### PE-DEF-01 — Excel `Поза планом` без одиниці виміру

Очікується:

`Продукт | Категорія | Од. виміру | Зроблено | На складі (<поточна дата>)`

Фактично:

`Продукт | Категорія | Зроблено | На складі (23.09.2026)`

Дефект відтворений незалежно API та UI методами.

### PE-DEF-02 — Excel не дорівнює поточним видимим рядкам

- На сторінці застосовано category + exact-name search + «Лише обрані».
- У UI лишився один видимий out-of-plan resource.
- Excel містить цей resource, але також містить інший resource тієї самої категорії, прихований пошуком і favourites.
- Отже backend export враховує category, але не повний клієнтський visible-row scope.

### Уточнена поведінка, не дефект

Category filter самої сторінки `/plan-execution` працює за exact ID. Parent не включає child автоматично — це погоджена бізнес-логіка. Active/Archived exact-name search також пройшов після коректного очікування спінера та валідної catalog-передумови.

## Виправлення автоматизації

1. Category multiselect відкривається один раз; після першого вибору helper не шукає placeholder, який уже зник.
2. Після категорійного response додано очікування settled-state і React commit; пошуковий assertion чекає фактичне приховування другого рядка.
3. Accordion `Поза планом` використовує свіжий locator, guard на відсутність елемента та стабільне очікування `aria-expanded`.
4. Total підтримує актуальну DOM-структуру: рядок `Разом` може бути `tr`, а не лише `tfoot`.
5. Active/Archived selector чекає відповідний request `isActive=true/false`; name-search чекає точне URL-encoded значення `name` і повний lifecycle спінера через DOM observer.
6. Archive fixture використовує лише динамічні ресурси; перед архівацією output замінюється у техкарті, а inventory-row повністю видаляється, включно з amount `0`.
7. `TC-UI-PLANEXEC-023` відокремлено від archive/history method і приєднано до прямого Active/Archived search-сценарію, щоб дефект пошуку не блокував history assertions.

Фінальний повний suite завершився з 11 PASS і 3 продуктовими FAIL; результат не містить `BLOCKED`, `INCONCLUSIVE`, locator timeout або недосяжних assertions.

## Release gate

**Не пройдено.** Мінімальні блокери:

1. Додати `Од. виміру` до `Поза планом` в Excel.
2. Передавати в export повний visible-row scope після category/search/«Лише обрані», а не лише categoryIds.

## Артефакти

- `target/surefire-reports/TEST-TestSuite.xml` — machine-readable результат фінального повного suite.
- `target/surefire-reports/TestSuite.txt` — stack traces трьох продуктових падінь.
- `target/allure-results/` — Allure results і screenshots, прикріплені listener-ом для UI failures.

## Доповнення після корекції archive fixture

Після аналізу `400` уточнено штатну передумову архівації ресурсу. Нульовий залишок недостатній: ресурс треба повністю прибрати з поточної техкарти та inventory, включно з zero-row. Автотест тепер:

1. створює всі продукти й replacement-ресурси динамічно під ADMIN;
2. замінює output у кожній техкарті на окремий replacement-resource;
3. виключає історичний ресурс із повного inventory payload;
4. повторним GET із `showZeroStock=true` перевіряє, що рядок справді відсутній;
5. лише після цього архівує техкарту й ресурс.

Після відновлення DEV targeted recheck `archiveUnarchivePreservesExecutionHistoryAndMetadata` завершився `BUILD SUCCESS`: 1 test, 0 failures, 0 errors, 0 skipped. Архівація обох output-resources повернула успіх, execution history лишилася доступною в UI та Excel, а cleanup видалив створені productions, plans, tech maps, inventory rows, resources, storage і динамічних користувачів.

## Фінальний verdict

- **PASS:** 11 із 14 test methods; 18 із 21 TCM IDs.
- **FAIL:** 3 test methods; 3 TCM IDs — два підтверджені Excel-дефекти `PE-DEF-01…02`.
- **BLOCKED / INCONCLUSIVE / SKIPPED:** 0.
