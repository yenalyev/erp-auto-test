# Історія операцій проєктного виробництва

Проєктне виробництво використовує два окремі журнали операцій:

- спожиті матеріали записуються як `USED` у resource operation history;
- створена одиниця обладнання записується як `PRODUCED` у equipment history;
- завершення `MODIFICATION` не створює нового обладнання і не додає `PRODUCED`.

`TC-PROJ-HIST-001` перевіряє `USED` та кількість списаного ресурсу для `CREATION`. `TC-PROJ-HIST-003` виконує таку саму перевірку для `MODIFICATION`. `TC-PROJ-HIST-002` отримує створене обладнання з project-production response і перевіряє його операцію `PRODUCED` через `GET /api/v1/equipment/{id}/history`. Тести входять до `project-production.xml` і `regression.xml`.

UI-перевірки запускаються під ізольованим користувачем типу `BUSINESS_UNIT_AND_PROJECT_OWNER`:

- `TC-UI-PROJ-HIST-001` перевіряє ресурс `CREATION` у картці та таблиці «Використано», а створене обладнання — в equipment-таблиці з операцією «Виготовлено»;
- `TC-UI-PROJ-HIST-002` перевіряє ресурс `MODIFICATION` у картці та таблиці «Використано».

Окрема summary-картка для виробленого обладнання поки відсутня, тому UI-тест перевіряє саме equipment-таблицю, а не картку.

Запуск: `mvn test -Denv=dev -Dtest=ProjectProductionOperationHistoryTest,ProjectProductionHistoryUiTest`.
