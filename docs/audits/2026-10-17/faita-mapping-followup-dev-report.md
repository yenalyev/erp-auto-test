# Повторний dev-прогін: погоджені назва/пошук і некоректні aliases

Дата: 17.10.2026, Europe/Kyiv. Dev: https://dev.cukerochka.sbs/faita-mapping.

Виконано **31 сценарій**: **6 FAIL**, **25 PASS**.

## Погоджені зміни

Користувач погодив назву «Амуніція» та окремі поля «Назва Файти»/«Ресурс». Оновлено АС, TCM-кейси й автотести. Перевіряються кожне поле, AND, порожній результат та очищення одного поля зі збереженням іншого.

Заміну чекбокса «Неспівставлені» перемикачем режимів користувач не погоджував. TC-FMAP-UI-008/009 зберігають початкове очікування; їх результати відокремлено від перевірок повноти даних черги.

Початковий звіт і TCM run 196 збережено як історію попередніх вимог. Цей звіт містить новий повний прогін; попередні результати не використано замість повторного виконання.

## Артефакти

- [Документація й АС](../../REQ-FAITA-MAP.md)
- [Покрокові тест-кейси](../../FAITA_EXPENSE_MAPPING_TEST_CASES.md)
- [Машинні результати](faita-mapping-followup-dev-results.json)
- [TCM payload](faita-mapping-followup-tcm-payload.json)

4 фічі, 15 АС, 31 автоматизований сценарій; усі класи входять у faita-expense-mapping.xml та regression.xml.

## Перевірка alias із некоректним ресурсом

Для кожного словника перевірено NULL-посилання і посилання на ресурс, відсутність якого доведена SELECT до вставки. Target і контроль взяті з реальної черги /unmapped. Створюється лише власний alias до чинного FAITA ID; SQL перевіряє нерозв'язне посилання, GET aliases — resource=null, GET /unmapped — видимість target і контролю. Alias видаляється у finally до фінального асерту; перевіряються повернення target і відсутність власних aliases.

- **TC-FMAP-API-009 — FAIL**: Амуніція, ресурс не заданий; FAITA ID=3197, alias ID=2802, resource_id=NULL. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-followup-evidence/TC-FMAP-API-009-evidence.json).
- **TC-FMAP-API-010 — FAIL**: Ініціатори, ресурс не заданий; FAITA ID=301, alias ID=245, resource_id=NULL. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-followup-evidence/TC-FMAP-API-010-evidence.json).
- **TC-FMAP-API-011 — FAIL**: Амуніція, ресурс за посиланням відсутній; FAITA ID=3197, alias ID=2801, resource_id=9396507096. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-followup-evidence/TC-FMAP-API-011-evidence.json).
- **TC-FMAP-API-012 — FAIL**: Ініціатори, ресурс за посиланням відсутній; FAITA ID=301, alias ID=244, resource_id=10215344748. Позиція в черзі: до — так, з некоректним alias — ні, після очищення — так. Контроль видимий під час/після: так/так; власні aliases видалені: так. [JSON-доказ](faita-mapping-followup-evidence/TC-FMAP-API-012-evidence.json).

**У 4 із 4 випадків позиція зникла з /unmapped після створення некоректного alias**, хоча GET aliases повертав resource=null. Фінальний регресійний асерт перевіряє очікування, що непридатний мапінг залишає позицію доступною для виправлення; фактичний результат зафіксовано окремо від відсутності чекбокса.

Вихідний код пояснює результат: NOT EXISTS виключає позицію за самою наявністю alias із FAITA ID, без перевірки придатності resource_id. Див. [умову для амуніції](C:/Users/gigam/IdeaProjects/tk/src/main/java/org/pm/tk/dao/faitamapping/AmmoUsageRepository.java:53) та [умову для ініціаторів](C:/Users/gigam/IdeaProjects/tk/src/main/java/org/pm/tk/dao/faitamapping/AmmoUsageRepository.java:93). Підстава функціонального висновку — відтворена на dev втрата доступної для виправлення позиції при нерозв'язному ресурсі.

## Ізоляція й межі доказу

Вихідні довідники та події Файти, materialized view й stock не змінювалися. Тести тимчасово додавали власні aliases до вже наявних неспівставлених FAITA ID; усі власні записи прибрано, контрольна позиція залишалася видимою, target відновився. Для звичайних UI/API сценаріїв власні ресурси деактивовано. У before/after Allure fixtures немає помилок setup/cleanup.

Міграційні alias ALTER statements V156 перевірені на TEMP legacy-таблицях із rollback; історична незмінність усіх dev-записів без знімка до деплою не доводиться. Нові integrity-сценарії перевіряють API, яке живить UI; окремих UI-кліків для цих чотирьох випадків не виконувалося.

Framework suite: **90 PASS, 0 FAIL, 0 SKIP**, включно з RegressionSuiteCompletenessTest. Лог: target/faita-mapping-followup-framework.log.

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
  Причина: [AMMO source 3197 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=AMMO, scenario=NULL_RESOURCE_ID, sourceId=3197, controlSourceId=4538, targetVisibleBefore=true, aliasId=2802, storedResourceId=null, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-API-010 — FAIL**: Ініціатори: некоректний alias — resource_id=NULL.
  Причина: [INITIATORS source 301 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=INITIATORS, scenario=NULL_RESOURCE_ID, sourceId=301, controlSourceId=330, targetVisibleBefore=true, aliasId=245, storedResourceId=null, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-API-011 — FAIL**: Амуніція: некоректний alias — resource_id посилається на перевірено відсутній ERP-ресурс.
  Причина: [AMMO source 3197 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=AMMO, scenario=DANGLING_RESOURCE_ID, sourceId=3197, controlSourceId=4538, targetVisibleBefore=true, aliasId=2801, storedResourceId=9396507096, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-API-012 — FAIL**: Ініціатори: некоректний alias — resource_id посилається на перевірено відсутній ERP-ресурс.
  Причина: [INITIATORS source 301 has no usable ERP resource and must remain in the unmapped queue; evidence={kind=INITIATORS, scenario=DANGLING_RESOURCE_ID, sourceId=301, controlSourceId=330, targetVisibleBefore=true, aliasId=244, storedResourceId=10215344748, aliasListedWithNullResource=true, targetVisibleWithInvalidAlias=false, controlVisibleDuring=true, targetVisibleAfterCleanup=true, controlVisibleAfterCleanup=true, ownedAliasesRemoved=true}]
- **TC-FMAP-DB-001 — PASS**: БЧ: поле updated_by на dev.
- **TC-FMAP-DB-002 — PASS**: Ініціатори: поле updated_by на dev.
- **TC-FMAP-DB-003 — PASS**: БЧ: V156 заповнює legacy автора system.
- **TC-FMAP-DB-004 — PASS**: Ініціатори: V156 заповнює legacy автора system.
- **TC-FMAP-UI-001 — PASS**: Навігація та назви вкладок. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-001.png).
- **TC-FMAP-UI-002 — PASS**: Амуніція: Колонки і джерело даних. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-002.png).
- **TC-FMAP-UI-003 — PASS**: Ініціатори: Колонки і джерело даних. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-003.png).
- **TC-FMAP-UI-004 — PASS**: Амуніція: Окреме поле Назва Файти та незалежне очищення. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-004.png).
- **TC-FMAP-UI-005 — PASS**: Ініціатори: Окреме поле Назва Файти та незалежне очищення. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-005.png).
- **TC-FMAP-UI-006 — PASS**: Амуніція: Окреме поле Ресурс, AND та скидання. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-006.png).
- **TC-FMAP-UI-007 — PASS**: Ініціатори: Окреме поле Ресурс, AND та скидання. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-007.png).
- **TC-FMAP-UI-008 — FAIL**: Амуніція: Неспівставлені та пошук. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-008.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-009 — FAIL**: Ініціатори: Неспівставлені та пошук. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-009.png).
  Причина: Locator expected to be visible
- **TC-FMAP-UI-010 — PASS**: Амуніція: Назва Файти: ASC/DESC і другий ключ. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-010.png).
- **TC-FMAP-UI-011 — PASS**: Ініціатори: Назва Файти: ASC/DESC і другий ключ. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-011.png).
- **TC-FMAP-UI-012 — PASS**: Амуніція: Ресурс: ASC/DESC і другий ключ. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-012.png).
- **TC-FMAP-UI-013 — PASS**: Ініціатори: Ресурс: ASC/DESC і другий ключ. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-013.png).
- **TC-FMAP-UI-014 — PASS**: Амуніція: Олівець відкриває редактор. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-014.png).
- **TC-FMAP-UI-015 — PASS**: Ініціатори: Олівець відкриває редактор. [Скріншот](faita-mapping-followup-evidence/TC-FMAP-UI-015.png).

## Відтворення

```powershell
mvn test '-Denv=dev' '-Dsuite=faita-expense-mapping' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dsuite.artifact.sweep=false'
```

Лог повного прогону: target/faita-mapping-followup-dev.log. Всі наведені статуси отримані реальним виконанням; продуктові FAIL не перетворено на PASS.

## Local TCM

Оновлені фічі, АС, документація й кейси синхронізуються з local TCM http://localhost:18100, project 1. Production TCM у цьому уточненні не змінювався.

Фактичний прогін імпортовано: **run 197, matched=31**; усі статуси перечитано й звірено з результатами Allure. Новий remoteRunId не змінює run 196.
