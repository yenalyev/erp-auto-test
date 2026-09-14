# Історія операцій проєктного виробництва

Дії проєктного виробництва, що змінюють залишки, мають бути видимі в історії операцій відповідного складу (`GET /api/v1/statistics/resource-operation-history?storageIds=...&from=...&to=...`). Картки підсумків не замінюють окремих рядків у `operationHistoryList`.

| Дія | Очікуваний запис |
| --- | --- |
| Додати стадію з `amountUsed > 0` | `USED` для списаного ресурсу; `amount` дорівнює `amountUsed` |
| Завершити виробництво типу `CREATION` | `PRODUCED` для ресурсу готового продукту; `amount` дорівнює кількості створеної партії |

Перевірки `TC-PROJ-HIST-001` і `TC-PROJ-HIST-002` порівнюють історію до й після дії за ID ресурсу, типом операції та кількістю. Вони входять до `project-production.xml` і `regression.xml`. Запуск окремо: `mvn test -Denv=dev -Dtest=ProjectProductionOperationHistoryTest`.

UI-перевірка `TC-UI-PROJ-HIST-001` запускається під ізольованим користувачем типу `BUSINESS_UNIT_AND_PROJECT_OWNER`: Keycloak-ролі `Business_Unit_Owner-ROLE` і `Project-Production-ROLE`, доступ до складу власника. Тест перевіряє, що користувач відкриває журнал проєктного виробництва й бачить створений проєкт, потім відкриває `/history` і перевіряє, що сировина є в картці «Використано» та рядку таблиці, а готовий продукт — у картці «Вироблено» та рядку таблиці. Він також перевіряє доступність рядків після фільтрації картками. Запуск: `mvn test -Denv=dev -Dtest=ProjectProductionHistoryUiTest`.
