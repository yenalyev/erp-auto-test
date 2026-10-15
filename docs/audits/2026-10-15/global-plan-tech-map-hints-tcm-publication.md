# Публікація підказок техкарт у TCM dev і prod

15 жовтня 2026 року за запитом користувача до обох TCM додано фічу `REQ-GP-HINT` під `REQ-GLOBAL-PLAN`.

- [TCM dev](http://localhost:18100/projects/1?feature=155): документація, AC-01–AC-04, TC-GP-HINT-UI-001–010.
- [TCM prod](http://tcm.lab/projects/1?feature=155): такий самий погоджений пакет. Production змінено через `tcm-ai-prod` MCP.

Документація містить чинні вимоги, тест-план, результати повного прогону ERP dev (**10/10 PASS**) та опис виправлення хибного падіння HTTP-timeout. Framework — **85/85 PASS**, окремий HTTP-клас у свіжій JVM — **11/11 PASS**. Це результати ERP dev і локальних інфраструктурних перевірок; ERP production не запускався. Окремий Test Run у TCM під час публікації документації не створювався.

Перед створенням перевірено відсутність фічі та наявність батьківського розділу. Після запису повторно прочитано й звірено повний текст документації, тексти чотирьох критеріїв, усі десять кейсів, їхні кроки, очікування та прив'язки до автоматизації. Усі кейси ACTIVE та automation=true; UI automation ID відповідає Java `@TestCaseId`.

Звіти звірки: [dev](global-plan-tech-map-hints-tcm-dev-sync.json), [prod](global-plan-tech-map-hints-tcm-prod-sync.json). [Вихідний пакет](global-plan-tech-map-hints-tcm-payload.json). [Звіт виконання тестів](global-plan-tech-map-hints-full-dev-report.md).

Для повторної локальної синхронізації використовується `scripts/sync_tcm_global_plan_hints.py --base-url http://localhost:18100 --report <шлях.json>` із токеном у `TCM_AI_TOKEN`. Скрипт приймає лише локальний endpoint; production обслуговується MCP. Токени до звітів і коміту не додаються.

У коміт входять лише файли фічі, виправлення `HttpClientInfrastructureTest`, документація й докази перевірок. У спільних README, regression.xml та ui.xml вибірково індексуються тільки рядки цієї фічі; сторонні зміни лишаються у робочому дереві.
