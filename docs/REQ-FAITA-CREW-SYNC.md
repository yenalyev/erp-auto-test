# REQ-FAITA-CREW-SYNC — екіпажі, точки та списання з Файти

## Мета й межі

Синхронізувати екіпажі Файти з Цукерочкою за `team_id`, визначити їхню поточну точку за `watch_schedule.team_code` і обробити записи використання ресурсів. Кожне списання має зберігати окремий історичний текст «Позиція з ЖБД».

Джерела: `imp.vw_cs_teams`, `imp.vw_cs_watch_schedule`, `imp.ammo_agg_v2`. Цільові сутності: `crew.team`, storage типу `CREW` / `FLY_POINT`, `crew.team_flight_log`, `storage_item_write_off`, API та журнал `/inventory-write-off`.

## Правила приймання

1. `team_id` є ключем ідентичності. Новий ID створює один team і один пов'язаний CREW storage; повторний sync не створює дублікати. Для наявного ID синхронізуються `team_name` і `storage.name`; стан storage відповідає активності екіпажа, включно з повторною активацією.
2. Актуальний запис `watch_schedule` для `team_code` задає точку. CREW є дочірнім storage цієї точки, а точка належить підрозділу екіпажа. За відсутності запису екіпаж перебуває без точки під підрозділом. Перенесення й відв'язка не дублюють залишки.
3. Запис використання Файти створює логічний набір списань основного, ініціатора й додаткових ресурсів. Повторна синхронізація не створює дублікати.
4. Якщо в екіпажа є точка й ресурс доступний у ній у достатній кількості, debit відбувається лише з точки. Якщо точки немає, debit відбувається з екіпажа. Якщо ресурсу бракує, списання залишається pending, без часткового debit і без fallback з точки на екіпаж.
5. «Позиція з ЖБД» — snapshot `imp.ammo_agg_v2.position_name` саме події, а не поточне ім'я точки або `flyPointStorage.name`. Вона однакова для всіх списань однієї події, зберігається після зміни графіка й повертається в API окремим полем. Робоча назва API — `positionName`, БД — `position_name`, UI — «Позиція з ЖБД». Старі записи та source `NULL` мають `null` у API і `—` в UI.
6. Для успішного автоматичного завершення у поточному backend фактичний стан — `COMPLETED`; вимога використовує слово `autofinished`. Остаточне рішення щодо назви статусу потрібне до приймання реалізації. Для недостатнього залишку очікуваний стан за вимогою — `PENDING`.

## Відкриті продуктові рішення

- Підтвердити назви поля `positionName` / `position_name`, джерело `ammo_agg_v2.position_name` і поверхні показу: глобальний журнал, detail точки, detail екіпажа.
- Підтвердити `COMPLETED` як API-відповідник `autofinished` або ввести окремий статус.
- Зафіксувати правило вибору графіка, коли є кілька записів одного дня, та поведінку за порожнього `position_name`.
- Уточнити, чи відсутність mapping і недостатній stock однаково лишають `PENDING`.

## Відомі розбіжності поточної реалізації

Станом на огляд коду 2026-09-30 backend `SyncTeamProcess` використовує `team.flyPointName` для `flyPointId` і не передає сире `crew.team_flight_log.fly_point_name` у `InventoryWriteOffCreateRequest`. `InventoryWriteOffResponse` не має `positionName`; UI також не має окремої колонки. Недостатній залишок зараз може дати `FAILED`, а повторна активація storage та відв'язка точки мають відомі прогалини. Деталі — у [плані](FAITA_CREW_SYNC_TEST_PLAN.md).

## Перевірка

- Детальні кроки й очікування: [тест кейси](FAITA_CREW_SYNC_TEST_CASES.md).
- Автоматизований read-only контракт API/UI: `src/test/resources/suites/faita-crew-sync.xml`.
- Контрольований dev log → sync → списання: `src/test/resources/suites/faita-crew-sync-e2e.xml`, окремий opt-in з `use.database=true` та `faita.sync.e2e.enabled=true`. Запуск загального sync на спільному dev потребує явного схвалення впливу на сторонні дані.
- Дозволений прогін 2026-10-01 зупинився на HTTP 500 від `syncTeams`; [звіт](audits/2026-09-30/faita-crew-sync-dev-test-report.md) містить перевірку адресного cleanup.
- Команда dev: `mvn test -Denv=dev -Dsuite=faita-crew-sync -Dtcm.enabled=false -Dgoogle.sheets.enabled=false`.
- Для повного release gate потрібні керовані source fixtures і backend integration tests із перевіркою stock delta. Наявний read-only suite перевіряє лише наявність нового поля у FLIGHT API та колонки в UI; він не доводить походження значення.
