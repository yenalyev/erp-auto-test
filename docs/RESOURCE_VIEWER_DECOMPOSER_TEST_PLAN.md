# План тестування декомпозера Resource Viewer

## Область

Фіча охоплює дві різні семантики пошуку:

1. Якщо вибрана лише категорія, пов'язані рівні BOM мають згортатися, щоб одне фізичне переміщення не дублювалося у журналі й не враховувалося двічі в підсумках.
2. Якщо користувач явно вибрав кінцевий виріб і його напівфабрикат, обидва вибрані рівні мають залишитися у результаті.

## Перевірений стан реалізації

- Backend застосовує групування, тільки коли `categoryIds` не порожній, а `resourceIds` порожній.
- `groupByLowestComponents=true` залишає нижній компонент; `false` залишає верхній рівень.
- За явного вибору ресурсів групування вимикається.
- Frontend передає `groupByLowestComponents`, вимикає перемикач за наявності явно вибраних ресурсів і показує маркер `Згруповано` для `groupedFrom`.
- Backend і frontend unit-тести не входять до цього плану та не запускаються в межах перевірки на dev.

## Виявлені прогалини

- `REQ-RVW-FILTER / AC-11` заявляє унікальність `relocationId` без обмеження category-only і конфліктує з режимом явного вибору ресурсів.
- Немає наскрізного API-тесту для `resourceIds=[кінцевий виріб, напівфабрикат]`.
- Наявний `TC-RVW-API-018` перевіряє лише простий ланцюг з одним компонентом.
- DTO і JSON Schema автотестів не містять `groupedFrom`.
- UI page object не підтримує перевірку перемикача групування, badge і tooltip.
- Export-тест не перевіряє дедуплікацію та колонку `Згруповано з`.
- Одна relocation з кількома позиціями може створити кілька рядків з однаковим `relocationId`; цей контракт треба перевірити окремо.

## Тестові дані

Базовий ланцюг:

- кінцевий виріб `A`, кількість переміщення — 5;
- напівфабрикат `B`, норма — 2 на `A`;
- сировина `C`, норма — 3 на `B`;
- очікувано `B=10`, `C=30`;
- `A`, `B`, `C` належать до однієї категорії.

Додаткові набори:

- два незалежні компоненти однієї категорії;
- дві позиції в одній relocation;
- циклічний BOM;
- кілька relocation на різних сторінках;
- direct і nested переміщення одного ресурсу.

## P0 — Backend та API

### Category-only, групування до нижнього рівня

- Запит: `categoryIds=[category]`, без `resourceIds`, `groupByLowestComponents=true` або параметр відсутній.
- У `content` relocation присутня один раз.
- У рядку присутній `C=30`.
- `groupedFrom` містить згорнуті верхні компоненти.
- У `sums` немає подвійного врахування `A`, `B`, `C`.

### Category-only, групування до верхнього рівня

- Запит з `groupByLowestComponents=false`.
- У `content` relocation присутня один раз.
- Залишається верхній відстежуваний рівень.
- Нижні компоненти перелічені в `groupedFrom`.

### Явний вибір кінцевого виробу і напівфабрикату

- Запит: `resourceIds=[A,B]`.
- Групування не застосовується незалежно від `groupByLowestComponents`.
- У результаті присутні обидва явно вибрані рівні.
- Кількості: `A=5`, `B=10`.
- `groupedFrom` порожній.
- Окремо перевірити контрольні запити `[A]` і `[B]`.

### Категорія разом з explicit resources

- Запит: `categoryIds=[category]`, `resourceIds=[A,B]`.
- Фільтри працюють як AND.
- Через наявність `resourceIds` жоден явно вибраний ресурс не згортається.

### Унікальність журналу

- Перевірити унікальність `relocationId` не лише для цільового продукту, а для всього `content` category-only відповіді.
- Повторити перевірку для багаторівневого BOM, sibling-компонентів і багатопозиційної relocation.
- Перевірити `page.totalElements`, `totalPages` і перехід між сторінками.
- Зафіксувати окремий очікуваний контракт для repeated `relocationId` в explicit-режимі.

### Підсумки

- Journal та `sums` використовують однакову семантику згортання.
- Пряме і вкладене переміщення агрегуються без втрати та без double count.
- Зміна lowest/highest змінює представлення, але не додає зайву фізичну кількість.

## P1 — Frontend

- Для category-only перемикач `Групувати по нижніх компонентах` активний і за замовчуванням увімкнений.
- Перемикання checkbox надсилає відповідне значення `groupByLowestComponents`.
- Обидва стани повертають один рядок на relocation.
- Badge `Згруповано` і tooltip показують правильні назви компонентів.
- Після явного вибору `A` і `B` checkbox disabled.
- UI показує обидва явно вибрані рівні з правильними кількостями.
- Для explicit-режиму badge групування відсутній.
- Результат і суми UI збігаються з API.
- Перевірити очищення фільтрів і відновлення стану з `localStorage`.

## P1 — Excel export

- Category-only export не дублює фізичне переміщення.
- Значення і згортання відповідають journal API.
- Колонка `Згруповано з` містить ті самі назви, що й `groupedFrom`.
- Explicit `A+B` експортує обидва вибрані рівні.
- Порожні mandatory filters повертають чинний guard-result.

## Автоматизація

Додати або розширити:

- backend `ResourceViewerServiceBomTest` — assertions для рядків explicit `A+B`, multi-item relocation і глобальної унікальності;
- backend `ResourceViewerFacadeTest` — category-only та explicit export;
- `TC-RVW-API-018` — багаторівневий category-only сценарій;
- новий `TC-RVW-API-019` — explicit кінцевий виріб + напівфабрикат;
- новий API-кейс для lowest/highest і пагінації;
- `ResourceRelocationViewerResponse` та schema — поле `groupedFrom`;
- `ResourceRelocationViewerPage` — методи для checkbox, badge і tooltip;
- нові UI-кейси для category-only та explicit-режиму.

## Документація

- Звузити AC-11 до category-only пошуку.
- Додати окремий AC для explicit resource selection.
- Описати `groupByLowestComponents`, `groupedFrom` і поведінку Excel.
- Визначити допустимість repeated `relocationId` у режимі explicit.
- Синхронізувати generator, TCM workbook, DTO і JSON Schema.

## Критерії завершення

- Профільні API та UI автотести зелені на dev; backend/frontend unit-тести поза межами прогону.
- Suite `resource-viewer` зелений на dev і staging.
- Для category-only journal, pagination, sums та export не мають double count.
- Для explicit `A+B` обидва рівні присутні з точними кількостями.
- UI та Excel відповідають API.
- TCM, generator, DTO і schema описують однаковий контракт.

## Реалізація та результати — 2026-09-22

### Реалізовано

- DTO журналу та підсумків доповнено полем `groupedFrom`; JSON Schema приймають масив назв або `null`.
- `TC-RVW-API-018` посилено перевірками одного рядка за `relocationId`, `groupedFrom` та відсутності подвійної суми.
- Додано `TC-RVW-API-019` для явного вибору готового виробу й напівфабрикату.
- Додано `TC-RVW-API-021` для `groupByLowestComponents=false`.
- Page Object доповнено перевірками стану перемикача, маркера `Згруповано`, кількості рядків і відновлення фільтрів.
- Додано `TC-UI-RVW-004` для category-only групування та `TC-UI-RVW-005` для explicit-вибору двох рівнів.
- Наявні `TC-UI-RVW-002/003` переведено на детерміноване відновлення фільтрів, оскільки autocomplete динамічного ресурсу на dev не повертав створений fixture.
- Генератор TCM-імпорту синхронізовано з AC-11..13, UI AC-04..06 і новими тест-кейсами.

### TCM

- Prod TCM: оновлено `REQ-RVW`, `REQ-RVW-FILTER`, `REQ-RVW-UI`; створено AC-12, AC-13, UI AC-05, UI AC-06; оновлено `TC-RVW-API-018`; створено `TC-RVW-API-019`, `TC-RVW-API-021`, `TC-UI-RVW-004`, `TC-UI-RVW-005`.
- Dev TCM: виконано ту саму синхронізацію; контрольне читання підтвердило три вимоги, потрібні AC та всі п'ять automation links.
- Prod і dev перевірено повторним читанням після запису.

### Прогони на dev

- Backend/frontend unit-тести не запускалися і не враховуються у звіті.
- Нові/змінені API: `TC-RVW-API-018`, `TC-RVW-API-019`, `TC-RVW-API-021` — 3/3 пройшли.
- UI: у фінальній редакції `TC-UI-RVW-001/002/003/005` пройшли в одному class-run; після виправлення auto-fetch `TC-UI-RVW-004` пройшов окремим контрольним запуском — сумарно 5/5 сценаріїв підтверджено.
- Компіляція тестів — успішна.
- Повний suite `resource-viewer`: 37 виконано, 6 failures, 2 skipped. Дві UI-помилки autocomplete усунено й повторно перевірено; залишилися 4 розбіжності наявних BOM-сценаріїв dev, не пов'язані з доданим category-only/explicit контрактом:
  - `testMixedProducedAndExternalProductUseIndependentAlgorithms`: очікувано 10, фактично 0;
  - `testSharedBatchUsesLatestProductionRecord`: очікувано 40, фактично 32;
  - `testMovementBeforeFirstTechMapRemainsAtomic`: компонент несподівано присутній;
  - `testExternalBatchMovedBeforeFirstTechMapRemainsAtomic`: компонент несподівано присутній.

### Залишкові перевірки

- Виправити або погодити контракт чотирьох наявних BOM-сценаріїв і повторити повний suite.
- Окремо автоматизувати export-перевірки колонки `Згруповано з` та explicit `A+B` у XLSX.
- Повторити регресію на staging після стабілізації dev suite.
