# Dev-звіт: словник мапінгу Файта — Цукерка для витрат

Дата: 17.10.2026, Europe/Kyiv. Dev: https://dev.cukerochka.sbs/faita-mapping.

Виконано **27 унікальних сценаріїв**: **7 FAIL**, **20 PASS**.

Фіча реалізована на dev, але відповідність початковій постановці неповна. Відмінності UI не підмінено новими очікуваннями.

## Підготовлені артефакти

4 фічі, 15 АС, 27 тест-кейсів із Java-автоматизацією. Suite: faita-expense-mapping; усі класи зареєстровано в regression.xml.

- [Документація і тест-план](../../REQ-FAITA-MAP.md)
- [Покрокові тест-кейси](../../FAITA_EXPENSE_MAPPING_TEST_CASES.md)
- [Машинні результати](faita-mapping-dev-results.json)
- [TCM payload](faita-mapping-tcm-payload.json)

## Відмінності від постановки

1. У UI вкладка «Амуніція», тоді як у постановці — «БЧ» (TC-FMAP-UI-001).
2. Замість одного фільтра «Пошук» є два окремі поля «Назва Файти» та «Ресурс» (TC-FMAP-UI-004…007). API підтримує окремі faitaName/resourceName як AND; це не підтверджує вимогу єдиного пошуку як OR.
3. Замість чекбокса «Неспівставлені» є режими «Неспівставлені/Співставлені»; неспівставлені беруться з витрат Файти, а не лише з aliases із NULL resource_id (TC-FMAP-UI-008/009). Повна семантика заданого чекбокса не підтверджена.

Остаточний результат навігації та кожного сценарію наведено нижче; окремі падіння спільного контролу не трактуються як незалежні дефекти даних або сортування.

## Межі доказу та дані

DB-перевірки читають deployed схему й авторів. Реальні alias ALTER statements V156 виконані на TEMP legacy-таблицях PostgreSQL із rollback. Перевірено system і збереження бізнес-полів двох старих mapped/unmapped рядків; повна історична незмінність dev без знімка до деплою не доводиться. Sequence repairs та індекси V156 не запускалися.

Для API/UI створювалися aliases із власним UUID marker і власні ERP-ресурси. Cleanup видаляє aliases лише за ID та marker і деактивує власні ресурси. Події Файти, stock і записи витрат не створювалися. У Allure configuration results перевірено before/after fixtures усіх підсумкових прогонів: помилок setup/cleanup немає.

## Перевірка тестового framework

Framework suite: **90 PASS, 0 FAIL, 0 SKIP**, включно з RegressionSuiteCompletenessTest. Лог: target/faita-mapping-framework.log.

## Результати сценаріїв

- **TC-FMAP-API-001 — PASS**: БЧ: API: значення та типове сортування.
- **TC-FMAP-API-002 — PASS**: Ініціатори: API: значення та типове сортування.
- **TC-FMAP-API-003 — PASS**: БЧ: API: окремі пошуки та AND.
- **TC-FMAP-API-004 — PASS**: Ініціатори: API: окремі пошуки та AND.
- **TC-FMAP-API-005 — PASS**: БЧ: API: обидва ключі й напрями.
- **TC-FMAP-API-006 — PASS**: Ініціатори: API: обидва ключі й напрями.
- **TC-FMAP-API-007 — PASS**: БЧ: API: зміна мапінгу, автор та ізоляція.
- **TC-FMAP-API-008 — PASS**: Ініціатори: API: зміна мапінгу, автор та ізоляція.
- **TC-FMAP-DB-001 — PASS**: БЧ: поле updated_by на dev.
- **TC-FMAP-DB-002 — PASS**: Ініціатори: поле updated_by на dev.
- **TC-FMAP-DB-003 — PASS**: БЧ: V156 заповнює legacy автора system.
- **TC-FMAP-DB-004 — PASS**: Ініціатори: V156 заповнює legacy автора system.
- **TC-FMAP-UI-001 — FAIL**: Навігація та назви вкладок. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-001.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-002 — PASS**: БЧ: Колонки і джерело даних. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-002.png).
- **TC-FMAP-UI-003 — PASS**: Ініціатори: Колонки і джерело даних. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-003.png).
- **TC-FMAP-UI-004 — FAIL**: БЧ: Єдиний пошук: назва Файти. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-004.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-005 — FAIL**: Ініціатори: Єдиний пошук: назва Файти. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-005.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-006 — FAIL**: БЧ: Єдиний пошук: ресурс. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-006.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-007 — FAIL**: Ініціатори: Єдиний пошук: ресурс. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-007.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-008 — FAIL**: БЧ: Неспівставлені та пошук. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-008.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-009 — FAIL**: Ініціатори: Неспівставлені та пошук. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-009.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-010 — PASS**: БЧ: Назва Файти: ASC/DESC і другий ключ. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-010.png).
- **TC-FMAP-UI-011 — PASS**: Ініціатори: Назва Файти: ASC/DESC і другий ключ. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-011.png).
- **TC-FMAP-UI-012 — PASS**: БЧ: Ресурс: ASC/DESC і другий ключ. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-012.png).
- **TC-FMAP-UI-013 — PASS**: Ініціатори: Ресурс: ASC/DESC і другий ключ. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-013.png).
- **TC-FMAP-UI-014 — PASS**: БЧ: Олівець відкриває редактор. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-014.png).
- **TC-FMAP-UI-015 — PASS**: Ініціатори: Олівець відкриває редактор. [Скріншот](faita-mapping-evidence/TC-FMAP-UI-015.png).

## Виконання та відтворення

Підсумок обирає останній реальний результат за кожним ID: повний suite із 27 кейсів, повтор UI, чотирьох сортувань після виправлення локаторів та одного API PUT після уточнення name-only payload. Діагностичні попередні спроби не входять у підсумок. Статус broken у Allure відображається як FAIL у TCM; оригінальний статус збережено в JSON. В остаточних результатах усі FAIL є невиконаними асертами, а не помилками інфраструктури.

```powershell
mvn test '-Denv=dev' '-Dsuite=faita-expense-mapping' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dsuite.artifact.sweep=false'
```

## TCM

Документацію, фічі, АС і кейси збережено та перечитано в local TCM: http://localhost:18100, project 1. Production MCP tcm-ai-prod повертає fetch failed; публікацію в production TCM не підтверджено. Local token не використовувався для production.

Фактичні результати імпортовано в local TCM з перевіркою matched/unmatched та повторним читанням run:

- REQ-FAITA-MAP: run 196, matched=27.
