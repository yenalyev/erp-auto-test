# Історія операцій проєктного виробництва

Проєктне виробництво використовує два окремі журнали операцій:

- спожиті матеріали записуються як `USED` у resource operation history;
- створена одиниця обладнання записується як `PRODUCED` у equipment history;
- для користувача з `production::view` сторінка `/history` показує окрему картку
  **«Обладнання (виготовлено)»** для операцій `PRODUCED`;
- завершення `MODIFICATION` не створює нового обладнання і не додає `PRODUCED`.

`TC-PROJ-HIST-001` перевіряє `USED` та кількість списаного ресурсу для `CREATION`. `TC-PROJ-HIST-003` виконує таку саму перевірку для `MODIFICATION`. `TC-PROJ-HIST-002` отримує створене обладнання з project-production response і перевіряє його операцію `PRODUCED` через `GET /api/v1/equipment/{id}/history`. Тести входять до `project-production.xml` і `regression.xml`.

UI-перевірки запускаються під ізольованим користувачем типу `BUSINESS_UNIT_AND_PROJECT_OWNER`:

- `TC-UI-PROJ-HIST-001` після завершення `CREATION` перевіряє точний заголовок картки
  **«Обладнання (виготовлено)»**, наявність створеної одиниці в equipment-таблиці за її
  унікальним інвентарним номером та операцію «Виготовлено» в тому самому рядку;
- `TC-UI-PROJ-HIST-002` перевіряє ресурс `MODIFICATION` у картці та таблиці «Використано».

RBAC-перевірки `TC-UI-HIST-CARD-001` і `TC-UI-HIST-CARD-002` підтверджують, що картка
**«Обладнання (виготовлено)»** видима для ролі з доступом до виробництва та прихована для
ролі без `production::view`. Locator equipment-картки використовує точний збіг заголовка,
тому ресурсна картка «Вироблено» не може дати хибнопозитивний результат.

Запуск основного сценарію:

`mvn test -Denv=dev -Dtest=ProjectProductionOperationHistoryTest,ProjectProductionHistoryUiTest`

Запуск UI та RBAC-покриття картки:

`mvn test -Denv=dev -Dtest=ProjectProductionHistoryUiTest,OperationHistoryCardsVisibilityUiTest`

## TCM

Idempotent sync документації, `REQ-OPER-HIST / AC-18` і кейсу
`TC-UI-PROJ-HIST-001` виконує `scripts/sync_tcm_project_production.py` окремо для dev та
production TCM. Production TCM містить нормативну документацію; це не означає запуск
автотесту на production ERP.

Підтверджений результат на dev від 25.09.2026: `TC-UI-PROJ-HIST-001` — **1/1 passed**.
