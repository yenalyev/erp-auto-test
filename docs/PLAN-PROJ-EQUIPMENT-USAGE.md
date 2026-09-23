# Проектне виробництво: обладнання як результат створення та модифікації

## Уточнена суть фічі

Проектне виробництво й надалі може **споживати матеріальні ресурси на етапах**, але результатом проекту більше не є ресурс або партія ресурсу.

- `CREATION` створює нову одиницю обладнання після завершення проекту.
- `MODIFICATION` працює з уже наявною одиницею обладнання та залишає результат у цій самій одиниці.
- Матеріальні ресурси у `projectProductionStageResourceUsages` є вхідними компонентами, а не результатом виробництва.
- Для обладнання використовується окрема історія операцій. У ресурсній історії залишаються тільки операції зі спожитими матеріалами.

## Перевірений стан backend

Перевірено код `C:\Users\gigam\IdeaProjects\tk`, поточна реалізація CPMA-858.

### Модель даних

Міграція `V122__project_production_produces_equipment.sql`:

- видаляє старий зв'язок `project_production_equipment`, у якому обладнання трактувалося як інструмент проекту;
- додає до проектного виробництва `equipment_category_id`, `equipment_model_id`, `equipment_id` і `parameters`;
- видаляє `project_category_id`, `project_product_id` та старі каталоги проектних продуктів;
- очищає старі project-production записи, які створювали ресурсні партії;
- прямо визначає нову модель: проектне виробництво виробляє обладнання замість resource batch.

`ProjectProduction` містить:

- обов'язкові `equipmentCategory` і `equipmentModel`;
- один nullable `equipment`;
- `equipment` створюється при завершенні `CREATION` або вибирається на початку `MODIFICATION`;
- параметри майбутнього або модифікованого обладнання зберігаються у JSON `parameters`.

### Потік CREATION

Під час create/update проект зберігає категорію, існуючу чи нову модель, серійний номер, параметри обладнання, етапи та матеріальні ресурси. До завершення `equipment` у відповіді дорівнює `null`.

Під час `PUT /project-production/{id}/finish-project` backend:

1. перевіряє серійний номер;
2. створює новий `Equipment`;
3. переносить категорію, модель, серійний номер, опис і параметри;
4. генерує інвентарний номер формату `EQ-<year>-<sequence>`;
5. встановлює склад проекту та статус `AVAILABLE`;
6. записує створене обладнання у `projectProduction.equipment`;
7. публікує `EquipmentProduced`;
8. створює equipment-history операцію `PRODUCED`;
9. переводить проект і всі його етапи у `DONE`.

Ресурсна партія готової продукції більше не створюється.

### Скасування завершення CREATION

Під час cancel finish backend повертає проект у `IN_PROGRESS`, відв'язує та видаляє вироблене обладнання.

Скасування блокується, якщо:

- на основі обладнання вже існує проект `MODIFICATION`;
- обладнання не `AVAILABLE`;
- обладнання вже не на складі проекту;
- обладнання брало участь у переміщенні.

Закрите assignment та equipment history видаляються разом з обладнанням відповідно до поточної реалізації.

### Потік MODIFICATION

Create/update проекту типу `MODIFICATION` вимагає `equipmentId`.

Backend перевіряє, що обладнання:

- існує;
- знаходиться на складі проекту;
- має статус `AVAILABLE`, якщо це не вже вибране обладнання поточного проекту;
- не використовується в іншій незавершеній модифікації.

Після вибору обладнання:

- проект посилається на ту саму одиницю `Equipment`;
- серійний номер проекту копіюється з обладнання;
- передані параметри одразу записуються в обладнання під час create/update;
- finish не створює нове обладнання, а тільки переводить проект та етапи у `DONE`;
- cancel finish повертає проект у `IN_PROGRESS`, але не відкочує параметри обладнання;
- rename serial number змінює серійний номер і в проекті, і в обладнанні.

### Історія операцій

- Витрата матеріалів на етапах залишається `USED` у resource operation history.
- Результат `CREATION` записується як `PRODUCED` в equipment history.
- `MODIFICATION` не створює другий `Equipment` і не повинна створювати equipment `PRODUCED`.
- Скасування завершення `CREATION` видаляє історію виробленої одиниці разом із самою одиницею.

## Перевірений стан frontend

Перевірено код `C:\Users\gigam\IdeaProjects\tk-ui`, реалізація CPMA-858.

Frontend відповідає новому API-контракту:

- типи проекту: `CREATION` і `MODIFICATION`;
- форма використовує категорію та модель обладнання замість project category/product;
- для `CREATION` обов'язковий серійний номер;
- для `MODIFICATION` показується селектор «Виріб для модифікації»;
- selector отримує доступне обладнання за складом, категорією та моделлю;
- після вибору обладнання завантажуються його параметри;
- після завершення `CREATION` форма показує «Виготовлено» з серійним та інвентарним номерами;
- confirmation dialog пояснює, що `CREATION` додає обладнання до обліку, а `MODIFICATION` залишає змінені параметри на обладнанні;
- cancel finish для `CREATION` повідомляє про видалення виробленого обладнання;
- cancel finish для `MODIFICATION` повідомляє, що змінені параметри залишаються;
- таблиця проектів показує категорію, модель, серійний номер і вид проекту.

Матеріальні ресурси залишаються окремим блоком етапів і окремим діалогом «Ресурси».

## Виявлені ризики поточної реалізації

### P0: finish MODIFICATION зі шаблону без обладнання має бути заблокований

Backend створює проект із modification template з `equipment = null`. `validateFinish` для типу `MODIFICATION` не перевіряє наявність обладнання, а одразу завершує валідацію. `finish` після цього лише ставить `DONE`.

Frontend після створення проекту зі шаблону не відкриває форму вибору обладнання. Кнопка finish у формі блокується за відсотком етапів, але не за `mainInfoFilled` або відсутнім `equipmentId`.

Очікувана корекція продукту:

- finish будь-якої `MODIFICATION` має вимагати `projectProduction.equipment != null`;
- UI має блокувати finish, доки виріб не вибраний;
- бажано після створення modification-проекту зі шаблону відкривати його форму або вимагати вибір виробу в діалозі створення.

При цьому проект без обладнання є валідною незавершеною чернеткою. Користувач повинен мати можливість відмовитися від неї. У поточній state model немає окремого `CANCELLED`, тому «відмінити проект» означає видалити незавершений проект через delete flow. Це видалення має залишатися дозволеним і, якщо етапи вже списали ресурси, виконувати погоджений rollback. `cancel-finished-project` тут не застосовується, бо проект ще не переходив у `DONE`.

### P0: сума відсотків етапів може перевищувати 100%

UI обчислює суму `executionPercentage` і показує попередження на кшталт `105% / 100% — Перевищено на 5%`, але це лише індикація:

- create form блокує submit, якщо сума не дорівнює 100%;
- edit form дозволяє зберегти проект із сумою понад 100%;
- template edit form дозволяє зберегти шаблон із сумою понад 100%;
- окремі add/update stage requests також не захищені агрегатною валідацією;
- backend validators не перевіряють ані діапазон одного `executionPercentage`, ані суму етапів;
- backend finish не перевіряє сумарні 100%, тому обмеження можна обійти прямим API request.

Мінімальний інваріант: жоден create/update/add-stage/update-stage request не повинен залишати проект або шаблон із сумою понад 100%. Перед finish сума має дорівнювати рівно 100%. Для помилки потрібен стабільний `400` із field-level validation message.

### P1: активна модифікація не резервує обладнання для інших доменів

Обладнання під час незавершеної модифікації залишається у статусі `AVAILABLE`. Project-production validator забороняє лише другу незавершену модифікацію. У relocation та assignment коді не знайдено перевірки активного modification-проекту.

Потрібне бізнес-рішення:

- або relocation/assignment/repair блокуються до завершення модифікації;
- або ці операції дозволені, а finish має перевіряти фактичний статус і склад обладнання;
- або вводиться окремий стан/ознака резервування.

### P1: зміна обладнання в існуючій MODIFICATION

UI дозволяє змінити вибраний виріб до завершення. Параметри записуються в обладнання одразу, тому попередня одиниця може зберегти вже застосовані зміни після перемикання на іншу.

Детальний сценарій дефекту:

1. Створюється `MODIFICATION` для обладнання A, наприклад із параметром `Firmware: 1.0 → 2.0`.
2. Під час create backend одразу записує `Firmware = 2.0` і в JSON параметрів проекту, і в Equipment A. Зміна не чекає `finish`.
3. Користувач повторно відкриває проект і в UI вибирає обладнання B.
4. Update request надсилає `equipmentId` обладнання B. Backend дозволяє заміну, якщо B доступне, знаходиться на потрібному складі та не зайняте іншою активною модифікацією.
5. Backend переприв'язує проект до B, копіює в проект серійний номер B і застосовує параметри request до B.
6. Значення Equipment A не відкочуються: воно вже має `Firmware = 2.0`, хоча активний проект більше на нього не посилається.
7. Після `finish` проект документує модифікацію B. Для зміни A немає окремого завершеного проекту, rollback snapshot або зрозумілого бізнес-аудиту.

Наслідки:

- одна `MODIFICATION` фактично змінює дві чи більше одиниць;
- видалення або відміна проекту не повертає A до початкового стану;
- історія проекту та фактичні параметри обладнання розходяться;
- послідовне перемикання A → B → C залишає зміни на кожній попередній одиниці;
- разом зі зміною category/model виникає додатковий ризик, що метадані проекту не відповідають жодній реально зміненій одиниці.

Погоджений контракт: `equipmentId` незмінний після створення проекту. Update з іншим ID повертає `400` і не змінює ані A, ані B. Це узгоджується з правилом «один проект модифікує одну одиницю», не потребує знімків параметрів і робить reservation однозначним. Після вибору A UI має показувати обладнання read-only; для переходу на B користувач відміняє/видаляє поточний незавершений проект і створює новий.

Regression test `TC-PROJ-MOD-006`:

1. Створити Equipment A і B з різними параметрами.
2. Створити активну `MODIFICATION` для A та підтвердити застосування параметрів до A.
3. Виконати update тієї самої modification з `equipmentId = B.id`.
4. Очікувати `400` на полі `equipmentId`.
5. Перевірити, що проект усе ще посилається на A, параметри A не отримали додаткових змін, а B повністю залишилося без змін.

### P1: категорія/модель проекту та фактичного обладнання при MODIFICATION

Backend оновлює категорію/модель проекту, але для вже вибраного `Equipment` змінює лише parameters і serial number. Категорія та модель самої одиниці не оновлюються.

Погоджений контракт:

- `MODIFICATION` не змінює category або model самого Equipment;
- після вибору Equipment category/model проекту визначаються цією одиницею і повністю read-only;
- update з іншими `equipmentCategoryId` або `equipmentModelId` повертає `400` і не змінює ані проект, ані Equipment;
- дія «Нова модель» для створеної `MODIFICATION` недоступна;
- окрема операція перекласифікації та її аудит не потрібні.

Контракт покрито API-регресією `TC-PROJ-MOD-007` та UI-регресією `TC-UI-PROJ-MOD-001`.

### P2: create from template не переходить до створеного проекту

Після створення проекту зі шаблону UI лише оновлює список шаблонів. Для `MODIFICATION` це приховує обов'язковий наступний крок — вибір конкретного виробу.

## Розрив у поточному erp-auto-test

Станом на 22.09.2026 міграцію automation-контракту до моделі V122 виконано. Нижче збережено перелік початкових розривів як журнал обсягу робіт.

## Стан реалізації автотестів

Виконано:

- request/response DTO, factories, fixtures та JSON schemas переведено з project category/product і resource batch на equipment category/model/equipment/parameters;
- видалено automation-моделі, endpoints, schemas і suite reference старого project catalog;
- `ProjectProductionTest` перевіряє, що `CREATION` створює `AVAILABLE` equipment, а не ресурсну партію;
- `ProjectProductionOperationHistoryTest` розділяє resource `USED` і equipment `PRODUCED`;
- `ProjectProductionTemplateTest` містить blocker-регресію `TC-PROJ-TPL-004`: `MODIFICATION` зі шаблону без обладнання не можна завершити;
- `ProjectProductionTest` містить `TC-PROJ-VAL-001`: проект із сумою етапів `105%` має бути відхилений;
- `ProjectProductionTemplateTest` містить `TC-PROJ-TPL-005`: шаблон із сумою етапів `105%` має бути відхилений;
- додано `ProjectProductionModificationTest`:
  - `TC-PROJ-MOD-001` — модифікується і завершується та сама одиниця;
  - `TC-PROJ-MOD-002` — друга активна модифікація тієї самої одиниці відхиляється;
  - `TC-PROJ-MOD-003` — активна модифікація блокує переміщення;
  - `TC-PROJ-MOD-004` — активна модифікація блокує assignment;
  - `TC-PROJ-MOD-005` — активна модифікація блокує переведення в ремонт;
  - `TC-PROJ-MOD-006` — update не може змінити equipment A на B і не залишає side effects на жодній одиниці;
  - `TC-PROJ-MOD-007` — update не може змінити category/model вибраного Equipment;
- page objects та UI scenarios переведено на категорію/модель обладнання й блок «Виготовлено»;
- `TC-UI-PROJ-MOD-001` перевіряє read-only category/model/equipment і відсутність дії «Нова модель»;
- новий modification-клас додано до `project-production.xml` і `regression.xml`.

Відкритих продуктових рішень щодо identity та classification `MODIFICATION` більше немає.

Dev-перевірка: Maven main/test compilation проходить. Повний API-прогін потребує справної OAuth-авторизації стенда; два повтори для `ADMIN` завершилися timeout до появи `#username`, тобто до першого API request сценарію. Окремо зафіксовано, що старі project-production users/roles стенда більше не відповідають новій granular permission model.

### Застарілі DTO та schemas

Потрібно прибрати:

- `projectCategoryId`, `projectProductId`;
- `projectCategory`, `projectProduct`;
- `specificProperties`;
- список `equipments` у старому значенні;
- DTO старих project category/product каталогів.

Потрібно додати:

- `equipmentCategoryId`, `equipmentModelId`, `equipmentModelName`, `equipmentId`;
- `parameters`;
- одиничний response-об'єкт `equipment` з `id`, `modelName`, `serialNumber`, `inventoryNumber`;
- `equipmentCategory` та `equipmentModel` у response і template response.

### Застарілі endpoints

Потрібно видалити з automation catalog і RBAC coverage:

- `/project-category/**`;
- `/project-product/**`;
- `/project-production/products`.

Потрібно додати:

- `GET /project-production/equipment-categories`;
- `GET /project-production/equipment-models`;
- `GET /project-production/equipments`;
- `GET /project-production/parameters`;
- `PUT /project-production/product/rename-serial-number`;
- file endpoints проектів і шаблонів, якщо вони ще не описані в automation catalog.

### Застарілі тести

- `ProjectCatalogTest` перевіряє видалені backend-контролери та має бути видалений із suites і TCM.
- `ProjectProductionTest` очікує resource batch після finish; його треба переписати на перевірку `Equipment`.
- `ProjectProductionOperationHistoryTest` очікує resource `PRODUCED`; тепер треба перевіряти equipment history `PRODUCED`.
- `ProjectProductionTemplateTest` використовує project category/product і не покриває семантику category/model обладнання.
- `ProjectProductionUITest` працює зі старою формою та resource-oriented fixture.
- JSON schemas проектного виробництва описують старий response contract.

## План редагування документації

### 1. Основна вимога

Створити `docs/REQ-PROJ-EQUIPMENT-OUTPUT.md` із двома окремими потоками.

#### CREATION

- category/model/serial/parameters описують майбутню одиницю;
- до finish обладнання не існує;
- finish створює одну одиницю `Equipment` на складі проекту;
- інвентарний номер генерує backend;
- статус після finish — `AVAILABLE`;
- створюється equipment history `PRODUCED`;
- cancel finish видаляє одиницю лише за безпечних умов.

#### MODIFICATION

- проект працює з однією існуючою одиницею;
- одиниця має бути на тому самому складі та доступна;
- серійний номер береться з обладнання;
- треба чітко визначити момент застосування параметрів: поточний код застосовує їх одразу;
- finish не створює нової одиниці;
- cancel finish не відкочує параметри;
- необхідно визначити правила зміни category/model та переключення equipmentId.

### 2. Історія операцій

Оновити `docs/REQ-PROJ-OPER-HIST.md`:

- залишити `USED` для матеріалів етапу;
- замінити resource `PRODUCED` на equipment `PRODUCED` для `CREATION`;
- зафіксувати відсутність `PRODUCED` для `MODIFICATION`;
- описати видалення equipment history після успішного cancel finish.

### 3. Валідації

Документувати точні validation fields/messages:

- відсутня category/model;
- відсутній equipmentId у `MODIFICATION`;
- equipment не існує, має інший склад або недоступний status;
- інша активна modification;
- serial number already used project/equipment;
- cancel blocked by modification/status/storage/relocation;
- finish modification without equipment, включно зі template flow.

### 4. Міграційні наслідки

Зафіксувати, що V122 видаляє старі project-production та template дані й старі каталоги. Якщо це допустимо лише для середовищ без реальних даних, додати release note та перевірку міграції.

## План автоматизованого покриття

### Етап 1. Вирівняти test infrastructure з контрактом

1. Оновити request/response DTO.
2. Оновити project-production JSON schemas.
3. Додати endpoints category/model/equipment/parameters/rename/file.
4. Прибрати старі project category/product endpoints і моделі.
5. Переробити `ProjectProductionDataFactory` під category/model/parameters/equipmentId.
6. Переробити `ProjectProductionFixture` так, щоб він використовував `EquipmentFixture`.
7. Додати typed `EquipmentHistoryResponse` і schema для equipment history.
8. Оновити page objects під фактичні поля та українські labels frontend.

### Етап 2. P0 API — CREATION

1. Create з category, existing model, serial, parameters і stage resources.
2. До finish: `equipment == null`, ресурс уже списаний лише відповідно до `amountUsed`.
3. Finish створює рівно одну одиницю Equipment.
4. Перевірити category, model, serial, generated inventory number, storage, `AVAILABLE`, description і parameters.
5. GET project після finish повертає ту саму одиницю в `equipment`.
6. Equipment history містить `PRODUCED`.
7. Resource history містить `USED` для input materials і не містить resource `PRODUCED` для результату.
8. Повторний finish не створює дубль або повертає погоджену помилку.
9. Blank/duplicate serial та serial, зайнятий існуючим equipment, повертають `400`.
10. Cancel finish видаляє equipment, history і зв'язок, проект стає `IN_PROGRESS`.
11. Cancel блокується після assignment, relocation, зміни storage/status або створення modification.

### Етап 3. P0 API — MODIFICATION

1. Selector повертає тільки `AVAILABLE` equipment потрібного storage/category/model.
2. Create без equipmentId повертає `400`.
3. Create з nonexistent, wrong-storage або non-available equipment повертає `400`.
4. Друга незавершена modification для тієї самої одиниці повертає `400`.
5. Успішний create повертає те саме equipment та успадкований serial number.
6. Parameters із request одразу збережені в Equipment.
7. Update без parameters не очищає наявні значення.
8. Update з parameters змінює equipment values.
9. Rename serial змінює project та Equipment і перевіряє uniqueness.
10. Finish переводить project/stages у `DONE`, не створює іншу одиницю і не створює `PRODUCED`.
11. Cancel finish повертає `IN_PROGRESS`, не видаляє Equipment і не відкочує parameters.
12. Delete незавершеної modification не видаляє Equipment; поведінка параметрів відповідає задокументованому контракту.

### Етап 4. P0 template flow

1. Creation template переносить category/model/stages/files, але не створює Equipment до finish.
2. Modification template створює проект без конкретного equipment лише як чернетку.
3. Finish modification draft без equipment має бути заборонений.
4. Після вибору equipment modification draft проходить звичайний modification flow.
5. Тест має бути доданий до автоматизації навіть до product fix як known-gap regression.

### Етап 5. P1 cross-domain

Після погодження бізнес-правила перевірити:

- assignment під час активної modification;
- relocation під час активної modification;
- repair/write-off під час активної modification;
- зміна equipmentId у створеній modification;
- нова category/model у modification;
- конкурентні create двох modification для одного equipment;
- finish і cancel одночасними запитами.

### Етап 6. UI

1. Перемикання `CREATION` / `MODIFICATION` у create form.
2. CREATION: category, existing/new model, serial, parameters, create, finish, блок «Виготовлено».
3. MODIFICATION: фільтрація selector, пошук за serial/inventory, завантаження parameters.
4. MODIFICATION: save, finish, cancel та незмінність equipment identity.
5. Error messages для equipmentId, serial і cancel restrictions.
6. Modification from template: finish disabled до вибору виробу.
7. List page: category, model, serial, type та state.
8. Operation history UI: вироблене обладнання видно як `PRODUCED`.

## Рекомендована структура тестів

- Переписати `ProjectProductionTest` як базовий CREATION lifecycle.
- Створити `ProjectProductionModificationTest`.
- Створити `ProjectProductionValidationTest` для негативних контрактів.
- Переписати `ProjectProductionOperationHistoryTest` на resource-input та equipment-output history.
- Переробити `ProjectProductionTemplateTest` під category/model та modification draft.
- Видалити `ProjectCatalogTest` і його suite references.
- Розділити UI на `ProjectProductionCreationUITest` і `ProjectProductionModificationUITest`, якщо чинний клас стане надто великим.

## Порядок виконання

1. Зафіксувати вимогу окремо для `CREATION` і `MODIFICATION`.
2. Зафіксувати погоджені правила reservation, незмінності equipmentId та read-only category/model.
3. Зареєструвати P0 defect для finish modification template без equipment.
4. Оновити automation DTO, endpoints і schemas.
5. Прибрати старий project catalog automation.
6. Реалізувати CREATION lifecycle та equipment history.
7. Реалізувати MODIFICATION lifecycle.
8. Реалізувати template regression.
9. Оновити UI page objects і UI tests.
10. Запустити `project-production`, `equipment`, `equipment-history`, `relocations` і regression suites.

## Definition of Done

- Автотести не використовують видалені project category/product API.
- `CREATION` перевіряє створення Equipment, а не resource batch.
- `MODIFICATION` перевіряє зміну існуючого Equipment без створення дубля.
- Resource history перевіряє лише input consumption.
- Equipment history перевіряє `PRODUCED` для `CREATION`.
- Cancel finish покритий позитивними та всіма захисними сценаріями.
- Modification template не можна завершити без обладнання.
- Cross-domain поведінка активної modification документована та автоматизована.
- JSON schemas відповідають backend DTO CPMA-858.
- `project-production.xml` і `regression.xml` не містять застарілого `ProjectCatalogTest`.
