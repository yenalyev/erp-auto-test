# DEV-звіт: зміна процесу інвентаризації

Дата: 06.10.2026

Середовище: `dev`
Вимога і покриття: [INVENTORY_PROCESS_TEST_PLAN.md](../../INVENTORY_PROCESS_TEST_PLAN.md)

## Що оновлено

- API endpoint-и й моделі переведено зі старого status-toggle на процес `REQUESTED → OPEN → CLOSED/REJECTED`.
- Fixture підтримує створення запиту, погодження, відхилення, читання state та history.
- Старе очікування `OPEN` після проведення замінено на автоматичний `CLOSED`.
- Додано перевірки обов'язкової причини, одноразового проведення, автора/дат/коментарів і history diff.
- Основні UI-сценарії `InventoryUiTest` переведено на owner request та admin approve/reject.
- Legacy deep-link сценарій `CrewFlyPointInventoryUiTest` більше не очікує старі кнопки open/close.
- Додано `InventoryProcessesPage` і focused suite `inventory-process.xml`.

## Компіляція

Команда:

```bash
mvn -o -q -DskipTests test-compile "-Dmaven.repo.local=C:\Users\gigam\.m2\repository"
```

Результат: **успішно**.

## API-прогін

Команда:

```bash
mvn -o test -Denv=dev -Dsuite=inventory-process -Dsuite.artifact.sweep=false -Dgoogle.sheets.enabled=false -Dtcm.enabled=false "-Dmaven.repo.local=C:\Users\gigam\.m2\repository"
```

Результат: **14 tests, 12 passed, 2 failed, 0 errors, 0 skipped**. Час TestNG: 135.3 с; Maven: 2 хв 22 с.

Пройшло:

- owner створює запит із причиною, автором і датою;
- admin погоджує або відхиляє запит, рішення зберігається;
- owner не може сам погодити запит;
- після першого `Зберегти` процес автоматично стає `CLOSED`;
- повторне проведення повертає `403`;
- зміна, додавання й видалення залишків працюють;
- admin може провести інвентаризацію зі збереженою причиною;
- сторонній owner не може провести інвентаризацію локації.

Падіння вимог:

1. `TC-INV-PROC-005` — `POST /api/v1/storages/inventory` із `comment: "   "` повернув `200` і створив `REQUESTED`; очікується validation error `400`.
2. `TC-INV-PROC-007` — history diff повернув `{"resourceId":1600,"resourceName":"...","diffAmount":3.0}` без значень `beforeAmount` і `afterAmount`; неможливо відобразити повний результат `було / що змінилося / стало`.

## UI-прогін

Команда:

```bash
mvn -o test "-Dtest=InventoryUiTest#ownerRequestsInventoryWithReasonUi+adminApprovesInventoryRequestUi+ownerSeesRejectedInventoryRequestUi+allLocationsDisablesSessionButtonUi" -Denv=dev -Dsuite.artifact.sweep=false -Dgoogle.sheets.enabled=false -Dtcm.enabled=false "-Dmaven.repo.local=C:\Users\gigam\.m2\repository"
```

Результат: **4 tests, 3 passed, 1 failed, 0 errors, 0 skipped**. Час TestNG: 100.9 с; Maven: 1 хв 48 с.

Пройшло:

- owner не може submit-нути UI-форму без причини, запит із причиною переходить у pending;
- admin бачить створений запит на `/inventory-processes` і погоджує його;
- у режимі `Всі локації` запит недоступний.

Падіння вимоги:

- `TC-INV-PROC-UI-003` — після відхилення немає червоної кнопки `Запит на інвентаризацію відхилено`. Dev показує звичайну кнопку `Запросити інвентаризацію` та окремий жовтий текст із tooltip.

## Додатковий аудит dev frontend

Актуальний bundle `index-C3rQvbpE.js` підтверджує ще дві UI-розбіжності:

- вкладки називаються `Запити / Відкриті / Закриті`, тоді як вимога задає `Запит на інвентаризацію / Відкриті інвентаризації / Проведені інвентаризації`; `Закриті` об'єднує `CLOSED` і `REJECTED`;
- результат показується inline як `resourceName + diffAmount`; окремої кнопки/popup та значень `було/стало` немає.

## Fly-point deep-link

Команда:

```bash
mvn -o test -Dtest=CrewFlyPointInventoryUiTest#adminUsesOpenedInventoryProcessOnFlyPointDeepLink -Denv=dev -Dsuite.artifact.sweep=false -Dgoogle.sheets.enabled=false -Dtcm.enabled=false "-Dmaven.repo.local=C:\Users\gigam\.m2\repository"
```

Результат: **1 test, 0 passed, 1 failed, 0 errors, 0 skipped**.

Погоджений процес відкривається на fly-point deep-link, і кнопка проведення доступна. Після завершення процесу кнопка проведення вимикається, але admin не отримує дії для початку наступної інвентаризації з обов'язковою причиною. Це також узгоджується з dev frontend: у модальному вікні admin для відкриття інвентаризації `commentRequired=false`, хоча за вимогою причина обов'язкова.

## Висновок

Основний state workflow, RBAC, автоматичне завершення та одноразовість на dev працюють. Реліз вимоги не можна вважати повністю готовим до виправлення підтверджених розбіжностей: backend validation причини, повний history result, UX відхиленого запиту та обов'язкова причина для інвентаризації, яку ініціює admin. Назви/структуру admin history також потрібно привести до погодженого контракту.
