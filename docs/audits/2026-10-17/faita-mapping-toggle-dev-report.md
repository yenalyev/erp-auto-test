# Dev-прогін після погодження перемикача режимів

Дата: 17.10.2026, Europe/Kyiv. Dev: https://dev.cukerochka.sbs/faita-mapping.

Виконано **31 сценарій**: **4 FAIL**, **27 PASS**.

## Уточнення впливу: наявні дані та імпорт

Окремий SELECT-аудит без фікстур: **2026-10-17 12:52:22.134936+03**, транзакція READ ONLY / REPEATABLE READ, завершена rollback. Імпорт не запускався.

- Амуніція: 2364 aliases; NULL resource_id — 0; посилання на відсутній ресурс — 0.
- Ініціатори: 106 aliases; NULL resource_id — 0; посилання на відсутній ресурс — 0.

Штатний FaitaImportJob копіює ammo_agg_v3, ammunition та initiators і оновлює два materialized views. Переглянутий код не записує aliases і не видаляє public.resource. На перевірених таблицях імпорту, aliases та resource немає користувацьких тригерів. Імпорт може зберегти вже некоректний alias, але шляху створення NULL/нерозв'язного ERP resource_id цим імпортом не знайдено. Зникнення вихідної амуніції/ініціатора після імпорту є іншою прив'язкою та не дорівнює відсутності ERP-ресурсу.

Створення/редагування через UI/API вимагає наявного ресурсу за переглянутим кодом; звичайне видалення ERP-ресурсу є soft delete. Деактивовані наявні ресурси не враховані як некоректні посилання. У БД nullable resource_id без FK дозволяє створити такий стан поза штатною валідацією.

**Уточнений опис багу:** за наявності неконсистентного alias із NULL або відсутнім ERP-ресурсом черга виключає незавершене зіставлення. **Запропонований пріоритет: LOW; severity: MINOR; не блокер релізу за поточними доказами.** На наявних dev-даних дефект не проявляється; підтверджений сценарій використовував спеціально створені JDBC-фікстури. Попередня оцінка High/Major була передчасною. Чотири FAIL залишено як фактичні результати перевірок стійкості, без перетворення на PASS.

Перевірка імпорту є аналізом поточного локального SUT-коду й каталогу dev БД, не виконанням імпорту та не підтвердженням ідентичності deployed binary. Зовнішні ETL, SQL-скрипти, відновлення з дампа та історичне походження даних цим аудитом не перевірені; висновок не поширюється на ERP prod.

[SQL, знімок даних і каталог БД](faita-mapping-existing-data-audit.json). [Розбір імпорту, класифікація й SHA256 джерел](faita-mapping-import-review.json).

## Погоджені зміни

Користувач погодив назву «Амуніція» та окремі поля «Назва Файти»/«Ресурс». Оновлено АС, TCM-кейси й автотести. Перевіряються кожне поле, AND, порожній результат та очищення одного поля зі збереженням іншого.

Користувач погодив заміну чекбокса взаємовиключними режимами «Неспівставлені» / «Співставлені». TC-FMAP-UI-008/009 оновлено: перевіряються режими, реальна позиція з /unmapped, пошук назви, порожній результат, очищення та повернення до aliases з ресурсним пошуком. Чотири регресії повноти черги залишаються незалежними перевірками дефекту.

Попередні звіти й TCM runs 196/197 збережено як історію попередніх вимог. Цей звіт містить новий повний прогін; попередні результати не використано замість повторного виконання.

## Артефакти

- [Документація й АС](../../REQ-FAITA-MAP.md)
- [Покрокові тест-кейси](../../FAITA_EXPENSE_MAPPING_TEST_CASES.md)
- [Машинні результати](faita-mapping-toggle-dev-results.json)
- [TCM payload](faita-mapping-toggle-tcm-payload.json)

4 фічі, 15 АС, 31 автоматизований сценарій; усі класи входять у faita-expense-mapping.xml та regression.xml.

## Перевірка alias із некоректним ресурсом

Для кожного словника перевірено NULL-посилання і посилання на ресурс, відсутність якого доведена SELECT до вставки. Target і контроль взяті з реальної черги /unmapped. Створюється лише власний alias до чинного FAITA ID; SQL перевіряє нерозв'язне посилання, GET aliases — resource=null, GET /unmapped — видимість target і контролю. Alias видаляється у finally до фінального асерту; перевіряються повернення target і відсутність власних aliases.

- **TC-FMAP-API-009 — FAIL**: Амуніція, ресурс не заданий; FAITA ID=3197, alias ID=2886, resource_id=NULL. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-toggle-evidence/TC-FMAP-API-009-evidence.json).
- **TC-FMAP-API-010 — FAIL**: Ініціатори, ресурс не заданий; FAITA ID=301, alias ID=329, resource_id=NULL. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-toggle-evidence/TC-FMAP-API-010-evidence.json).
- **TC-FMAP-API-011 — FAIL**: Амуніція, ресурс за посиланням відсутній; FAITA ID=3197, alias ID=2885, resource_id=9049429428. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-toggle-evidence/TC-FMAP-API-011-evidence.json).
- **TC-FMAP-API-012 — FAIL**: Ініціатори, ресурс за посиланням відсутній; FAITA ID=301, alias ID=328, resource_id=10024437524. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-toggle-evidence/TC-FMAP-API-012-evidence.json).

**У 4 із 4 випадків позиція зникла з /unmapped після створення некоректного alias**, хоча GET aliases повертав resource=null. Фінальний регресійний асерт перевіряє очікування, що непридатний мапінг залишає позицію доступною для виправлення; фактичний результат зафіксовано окремо від погодженої заміни чекбокса.

Вихідний код пояснює результат: NOT EXISTS виключає позицію за самою наявністю alias із FAITA ID, без перевірки придатності resource_id. Див. [умову для амуніції](C:/Users/gigam/IdeaProjects/tk/src/main/java/org/pm/tk/dao/faitamapping/AmmoUsageRepository.java:53) та [умову для ініціаторів](C:/Users/gigam/IdeaProjects/tk/src/main/java/org/pm/tk/dao/faitamapping/AmmoUsageRepository.java:93). Підстава функціонального висновку — відтворена на dev втрата доступної для виправлення позиції при нерозв'язному ресурсі.

## Ізоляція й межі доказу

Вихідні довідники та події Файти, materialized view й stock не змінювалися. Тести тимчасово додавали власні aliases до вже наявних неспівставлених FAITA ID; усі власні записи прибрано, контрольна позиція залишалася видимою, target відновився. Для звичайних UI/API сценаріїв власні ресурси деактивовано. У before/after Allure fixtures немає помилок setup/cleanup.

Міграційні alias ALTER statements V156 перевірені на TEMP legacy-таблицях із rollback; історична незмінність усіх dev-записів без знімка до деплою не доводиться. Нові integrity-сценарії перевіряють API, яке живить UI; окремих UI-кліків для цих чотирьох випадків не виконувалося.

Framework suite: **90 PASS, 0 FAIL, 0 SKIP**, включно з RegressionSuiteCompletenessTest. Лог: target/faita-mapping-toggle-framework.log.

## Результати сценаріїв

- **TC-FMAP-API-001 — PASS**: БЧ: API: значення та типове сортування.
- **TC-FMAP-API-002 — PASS**: Ініціатори: API: значення та типове сортування.
- **TC-FMAP-API-003 — PASS**: БЧ: API: окремі пошуки та AND.
- **TC-FMAP-API-004 — PASS**: Ініціатори: API: окремі пошуки та AND.
- **TC-FMAP-API-005 — PASS**: БЧ: API: обидва ключі й напрями.
- **TC-FMAP-API-006 — PASS**: Ініціатори: API: обидва ключі й напрями.
- **TC-FMAP-API-007 — PASS**: БЧ: API: зміна мапінгу, автор та ізоляція.
- **TC-FMAP-API-008 — PASS**: Ініціатори: API: зміна мапінгу, автор та ізоляція.
- **TC-FMAP-API-009 — FAIL**: Амуніція: некоректний alias — resource_id=NULL.
  Причина: [AMMO source 3197 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=AMMO, scenario=NULL_RESOURCE_ID, sourceId=3197, controlSourceId=4538, targetVisibleBefore=true, aliasId=2886, storedResourceId=null, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-API-010 — FAIL**: Ініціатори: некоректний alias — resource_id=NULL.
  Причина: [INITIATORS source 301 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=INITIATORS, scenario=NULL_RESOURCE_ID, sourceId=301, controlSourceId=330, targetVisibleBefore=true, aliasId=329, storedResourceId=null, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-API-011 — FAIL**: Амуніція: некоректний alias — resource_id посилається на перевірено відсутній ERP-ресурс.
  Причина: [AMMO source 3197 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=AMMO, scenario=DANGLING_RESOURCE_ID, sourceId=3197, controlSourceId=4538, targetVisibleBefore=true, aliasId=2885, storedResourceId=9049429428, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-API-012 — FAIL**: Ініціатори: некоректний alias — resource_id посилається на перевірено відсутній ERP-ресурс.
  Причина: [INITIATORS source 301 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=INITIATORS, scenario=DANGLING_RESOURCE_ID, sourceId=301, controlSourceId=330, targetVisibleBefore=true, aliasId=328, storedResourceId=10024437524, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-DB-001 — PASS**: БЧ: поле updated_by на dev.
- **TC-FMAP-DB-002 — PASS**: Ініціатори: поле updated_by на dev.
- **TC-FMAP-DB-003 — PASS**: БЧ: V156 заповнює legacy автора system.
- **TC-FMAP-DB-004 — PASS**: Ініціатори: V156 заповнює legacy автора system.
- **TC-FMAP-UI-001 — PASS**: Навігація та назви вкладок. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-001.png).
- **TC-FMAP-UI-002 — PASS**: Амуніція: Колонки і джерело даних. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-002.png).
- **TC-FMAP-UI-003 — PASS**: Ініціатори: Колонки і джерело даних. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-003.png).
- **TC-FMAP-UI-004 — PASS**: Амуніція: Окреме поле Назва Файти та незалежне очищення. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-004.png).
- **TC-FMAP-UI-005 — PASS**: Ініціатори: Окреме поле Назва Файти та незалежне очищення. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-005.png).
- **TC-FMAP-UI-006 — PASS**: Амуніція: Окреме поле Ресурс, AND та скидання. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-006.png).
- **TC-FMAP-UI-007 — PASS**: Ініціатори: Окреме поле Ресурс, AND та скидання. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-007.png).
- **TC-FMAP-UI-008 — PASS**: Амуніція: Перемикач режимів, пошук і повернення до aliases. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-008.png).
- **TC-FMAP-UI-009 — PASS**: Ініціатори: Перемикач режимів, пошук і повернення до aliases. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-009.png).
- **TC-FMAP-UI-010 — PASS**: Амуніція: Назва Файти: ASC/DESC і другий ключ. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-010.png).
- **TC-FMAP-UI-011 — PASS**: Ініціатори: Назва Файти: ASC/DESC і другий ключ. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-011.png).
- **TC-FMAP-UI-012 — PASS**: Амуніція: Ресурс: ASC/DESC і другий ключ. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-012.png).
- **TC-FMAP-UI-013 — PASS**: Ініціатори: Ресурс: ASC/DESC і другий ключ. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-013.png).
- **TC-FMAP-UI-014 — PASS**: Амуніція: Олівець відкриває редактор. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-014.png).
- **TC-FMAP-UI-015 — PASS**: Ініціатори: Олівець відкриває редактор. [Скріншот](faita-mapping-toggle-evidence/TC-FMAP-UI-015.png).

## Відтворення

```powershell
mvn test '-Denv=dev' '-Dsuite=faita-expense-mapping' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dsuite.artifact.sweep=false'
```

Лог повного прогону: target/faita-mapping-toggle-final-dev.log. Всі наведені статуси отримані реальним виконанням; продуктові FAIL не перетворено на PASS. Попередню спробу з гонкою очікування debounce збережено у target/faita-mapping-toggle-dev.log; її результати не підмішано до фінального прогону.

## TCM dev / prod

Оновлені фічі, АС, документація й кейси синхронізуються з dev TCM http://localhost:18100 та prod TCM http://tcm.lab, project 1. Перевірку збережених об’єктів зафіксовано в окремих JSON-звітах синхронізації. Середовище виконання ERP-тестів — dev.

TCM dev: **run 198, matched=31**; усі статуси перечитано й звірено з Allure. Новий remoteRunId не змінює runs 196/197.

TCM prod: **run 120, matched=31**; усі 31 статус перечитано й звірено. Це результати ERP dev, імпортовані в production TCM; ERP prod не запускався.

- [TCM dev: синхронізація](faita-mapping-toggle-tcm-local-sync.json)
- [TCM dev: повна перевірка полів і кроків](faita-mapping-toggle-tcm-readback.json)
- [TCM prod: перевірені фічі, АС і кейси](faita-mapping-toggle-tcm-prod-sync.json)
- [TCM dev: результати](faita-mapping-toggle-tcm-local-run.json)
- [TCM prod: результати](faita-mapping-toggle-tcm-prod-run.json)
