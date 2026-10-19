# Dev-звіт — підказка класифікації обладнання

> Історичний прогін до погодження тексту. 19.10.2026 користувач прийняв поточний текст; очікування TC-EQ-HINT-UI-003 оновлено. Актуальний повторний прогін: [звіт](equipment-kind-hint-approved-dev-report.md).

19.10.2026, Europe/Kyiv. ERP: https://dev.cukerochka.sbs. Chromium, ADMIN та OWNER_1; JDBC вимкнений.

Фінальний реальний прогін: **8 PASS, 1 FAIL, 0 SKIP**; 9 автотестів. Окремий TC-EQ-HINT-MAN-001 залишається DRAFT/NOT_RUN до погодження бізнес-правил.

## Виявлена розбіжність

TC-EQ-HINT-UI-003: текст містить правило, окуляри, ноутбук та контакт адміністратора, але не містить пояснення внесення через адміністратора в ресурси. Початкова задача вимагає такий маршрут. Цей FAIL збережено без послаблення очікування. Точні категорії «Основні засоби» / «Розхідники» ще потребують погодження.

## Результати

- **TC-EQ-HINT-UI-001 — PASS**; ownerSeesAdviceBeforeEnteringEquipment; 2745 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-001-1.png).
- **TC-EQ-HINT-UI-002 — PASS**; adminCanReadExamplesAndContact; 10144 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-002-1.png).
- **TC-EQ-HINT-UI-003 — FAIL**; adviceExplainsResourceEntryThroughAdministrator; 11309 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-003-1.png).
  Причина: підказка не містить очікуваного тексту «ресурс». Повна помилка — у JSON результатів.
- **TC-EQ-HINT-UI-004 — PASS**; repeatedTogglingPreservesInputAndDoesNotSubmit; 3174 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-004-1.png).
- **TC-EQ-HINT-UI-005 — PASS**; batchItemsHaveIndependentAdvice; 3652 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-005-1.png).
- **TC-EQ-HINT-UI-006 — PASS**; keyboardActivatesBothAdviceControls; 2128 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-006-1.png).
- **TC-EQ-HINT-UI-007 — PASS**; expandedAdviceIsReadableAtDesktopAndNarrowViewport; 2828 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-007-1.png).
- **TC-EQ-HINT-UI-008 — PASS**; cancellationDoesNotCreateEquipmentAndReopenRestoresAdvice; 4387 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-EQ-HINT-UI-008-1.png).
- **TC-UI-EQ-002 — PASS**; createSeveralEquipmentInOneRelocation; 9398 ms.
  [Скріншот](equipment-kind-hint-evidence/TC-UI-EQ-002-1.png).

## Прибирання та межі

Перший технічний прогін: 6 PASS / 3 FAIL (два недоліки тестових перевірок та недоступний OWNER_1 вибір дочірньої локації). Після виправлень повторний прогін: 8 PASS / 1 FAIL. Фінальний прогін додатково перевіряє автоматичне прибирання UI-створень. Попередні спроби не змішуються з фінальними результатами TCM.

Чотири одиниці двох попередніх спроб прибрано лише за точними назвами та підтвердженими ID своїх отримань; для кожного GET by ID повернув 404. Доказ: equipment-kind-hint-attempts-cleanup.json. Фінальний тест сам видалив своє отримання та перевірив 404 обох одиниць перед архівацією локації; конфігураційних помилок cleanup немає.

Перевірки клавіатури та ширини 390/1280 px стосуються підказки; повний аудит доступності/браузерів не проводився. ERP prod не змінювався. Непогоджена категоризація не автоматизована як вигадане правило.

## Відтворення

```powershell
mvn test '-Denv=dev' '-Dsuite=equipment-kind-hint' '-Duse.database=false' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dsuite.artifact.sweep=false' '-Dtest.output.directory=target/equipment-kind-hint-dev-final' '-Dallure.results.directory=target/equipment-kind-hint-dev-verified/allure-results'
```

Лог: target/equipment-kind-hint-dev-verified.log. Сирі результати: target/equipment-kind-hint-dev-verified/allure-results. Перенесені скріншоти й структуровані результати знаходяться поруч із цим звітом.

## Збереження в TCM

У prod та dev TCM збережені REQ-EQU-HINT, 4 АС, 8 ACTIVE автокейсів і 1 ручний DRAFT; наявний TC-UI-EQ-002 оновлений без зміни його фічі та automation ID.

- Prod: [підказка — run 129](https://tcm.lab/test-runs/129), [створення — run 130](https://tcm.lab/test-runs/130).
- Dev: [підказка — run 201](http://localhost:18100/test-runs/201), [створення — run 202](http://localhost:18100/test-runs/202).

Усі 9 фактичних статусів перечитані та звірені; environment=dev. Немає unmatched чи out-of-scope результатів. Ручний DRAFT не позначено пройденим. Детальна розбіжність: docs/bugs/EQUIPMENT_KIND_HINT_RESOURCE_ROUTING.md.
