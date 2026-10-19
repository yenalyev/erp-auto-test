# Словники Файти: створення та видалення зіставлень

Дата: 17.10.2026. ERP dev: https://dev.cukerochka.sbs/faita-mapping.

Новий фактичний прогін після перегляду: **31 кейс**, **31 PASS**. Framework: **90 PASS**; помилок setup/cleanup немає.

## Обсяг і вилучення

4 фічі, 19 АС; 4 DB + 19 UI + 8 API. TC-FMAP-API-009…012 вилучено за рішенням користувача як несуттєві. Видалено тестовий клас, fault-fixture helper і реєстрації в suites. У TCM ці записи DEPRECATED без API/UI automation IDs; старі результати не перетворено на PASS. Попередні runs dev 198 / prod 120 збережено з усіма 31 результатами.

## Нове покриття створення й видалення

- UI-016/017: створення звичайного зіставлення для амуніції й ініціаторів через реальний UI. Без ресурсу збереження недоступне; скасування не створює alias; збереження перевірене через POST, GET, SQL, чергу та reload.
- UI-018/019: власний валідний alias підготовлений штатним POST; UI-підтвердження показує правильну назву й ресурс. Скасування зберігає повний SQL snapshot; підтвердження відправляє DELETE саме alias ID. GET, SQL і reload підтверджують відсутність.
- Перевірено FAITA ID, ресурс, created_by/updated_by, для амуніції multiplier=1.75 і matchByName=false; у кожному сценарії сусідній alias незмінний.
- Використано наявні неспівставлені FAITA ID та лише власні UUID ERP-ресурси. Guard не допускає takeover спільного name-only alias. Після методу cleanup видаляє тільки пару source ID + власний resource ID і перевіряє її відсутність; власні ресурси деактивовано.
- Події та довідники Файти, stock та materialized views не змінювалися. Перерахунок витрат і повернення до черги після наступного refresh цим набором не заявляються.

Частини складеної амуніції за назвою, лічильники, фільтр типу амуніції, пагінація, RBAC, повне UI-редагування зі збереженням та фактичне списання залишаються поза чинним набором. Вони не заявляються як автоматизовано перевірені. Нових продуктових дефектів не робимо висновком лише з відсутності такого покриття.

## Актуальні артефакти

- [Документація, АС і зв’язки з тестами](../../REQ-FAITA-MAP.md)
- [31 покроковий кейс](../../FAITA_EXPENSE_MAPPING_TEST_CASES.md)
- [Нові результати](faita-mapping-crud-dev-results.json)
- [Чинний TCM payload](faita-mapping-crud-tcm-payload.json)
- [TCM dev: синхронізація та вилучення](faita-mapping-crud-tcm-local-sync.json)
- [TCM prod: синхронізація та вилучення](faita-mapping-crud-tcm-prod-sync.json)
- [Фінальна документація: перевірка обох TCM і SHA256 джерела](faita-mapping-crud-tcm-documents-final.json)
- [Попередній набір 27 кейсів](faita-mapping-current-scope-report.md)
- [Попередній повний прогін з 31 кейсом](faita-mapping-toggle-dev-report.md)
- [SELECT-аудит наявних даних без фікстур](faita-mapping-existing-data-audit.json)

TCM dev: **run 200**, перевірено всі 31 статус. ERP-середовище виконання — dev.

TCM prod: **run 122**, перевірено всі 31 статус. ERP-середовище виконання — dev.

## Статуси сценаріїв

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
- **TC-FMAP-UI-001 — PASS**: Навігація та назви вкладок.
- **TC-FMAP-UI-002 — PASS**: Амуніція: Колонки і джерело даних.
- **TC-FMAP-UI-003 — PASS**: Ініціатори: Колонки і джерело даних.
- **TC-FMAP-UI-004 — PASS**: Амуніція: Окреме поле Назва Файти та незалежне очищення.
- **TC-FMAP-UI-005 — PASS**: Ініціатори: Окреме поле Назва Файти та незалежне очищення.
- **TC-FMAP-UI-006 — PASS**: Амуніція: Окреме поле Ресурс, AND та скидання.
- **TC-FMAP-UI-007 — PASS**: Ініціатори: Окреме поле Ресурс, AND та скидання.
- **TC-FMAP-UI-008 — PASS**: Амуніція: Перемикач режимів, пошук і повернення до aliases.
- **TC-FMAP-UI-009 — PASS**: Ініціатори: Перемикач режимів, пошук і повернення до aliases.
- **TC-FMAP-UI-010 — PASS**: Амуніція: Назва Файти: ASC/DESC і другий ключ.
- **TC-FMAP-UI-011 — PASS**: Ініціатори: Назва Файти: ASC/DESC і другий ключ.
- **TC-FMAP-UI-012 — PASS**: Амуніція: Ресурс: ASC/DESC і другий ключ.
- **TC-FMAP-UI-013 — PASS**: Ініціатори: Ресурс: ASC/DESC і другий ключ.
- **TC-FMAP-UI-014 — PASS**: Амуніція: Олівець відкриває редактор.
- **TC-FMAP-UI-015 — PASS**: Ініціатори: Олівець відкриває редактор.
- **TC-FMAP-UI-016 — PASS**: Амуніція: створення зіставлення, скасування й збереження. [JSON-доказ](faita-mapping-crud-evidence/TC-FMAP-UI-016-lifecycle.json).
- **TC-FMAP-UI-017 — PASS**: Ініціатори: створення зіставлення, скасування й збереження. [JSON-доказ](faita-mapping-crud-evidence/TC-FMAP-UI-017-lifecycle.json).
- **TC-FMAP-UI-018 — PASS**: Амуніція: видалення зіставлення, скасування й підтвердження. [JSON-доказ](faita-mapping-crud-evidence/TC-FMAP-UI-018-lifecycle.json).
- **TC-FMAP-UI-019 — PASS**: Ініціатори: видалення зіставлення, скасування й підтвердження. [JSON-доказ](faita-mapping-crud-evidence/TC-FMAP-UI-019-lifecycle.json).

Логи: target/faita-mapping-crud-final-dev.log; target/faita-mapping-crud-final-framework.log. Повторно виконано тільки чинний набір; вилучені кейси не запускалися. Початкову спробу з помилкою локатора автокомпліту збережено в target/faita-mapping-crud-dev.log; її результати не підмішано до фінального прогону.
