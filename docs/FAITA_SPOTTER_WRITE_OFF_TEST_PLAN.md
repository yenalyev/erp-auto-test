# Списання з Файти з точки спотерів — тест-план

Бізнес-правило та відкриті питання: [REQ-FAITA-SPOTTER-WRITE-OFF](REQ-FAITA-SPOTTER-WRITE-OFF.md).

## Вимога та межі

Поле `imp.ammo_agg_v2.spotter_team_id` є впорядкованим масивом ID команд спотерів для вильоту. Якщо масив містить команду спотерів, ресурс списується з її точки (`FLY_POINT`). Для кількох команд перевіряються точки в порядку ID в масиві: обрати першу з достатнім залишком **усього обсягу**. Якщо ресурс є в обох, списати з першої. Списання не має змішувати залишки різних точок або переходити до точки бойового екіпажу, коли задано спотерів.

Технічний ланцюг: `imp.ammo_agg_v2.spotter_team_id → crew.team_flight_log.spotter_team_id → crew.team.team_storage_id → storage_item_write_off.storage_id/alternative_storage_ids → FLY_POINT за parent CREW → stock/history/API`. Для відсутнього або порожнього масиву діє звичайний маршрут бойового екіпажу з [основного плану](FAITA_CREW_SYNC_TEST_PLAN.md).

## Передумови й дані

- Dev, тестовий ADMIN та OWNER_1, `use.database=true`, доступний Fight sync, активний бойовий екіпаж та контрольовані команди спотерів.
- Окремий ресурс і reconciliation `FLIGHT`, тестові `UNIT → FLY_POINT → CREW`, унікальні `team_id` та flight marker. Залишки точок фіксуються до й після.
- Для перевірки маршруту імпорту `TC-FAITA-SPOT-005` потрібен `-Dfaita.spotter.sync.enabled=true`: виклик `/integrations/faita/syncTeams` запускає загальну dev-синхронізацію. Тест сіє лише власний рядок `crew.team_flight_log`; шлях `imp.ammo_agg_v2 → log` вимагає окремої керованої source-фікстури.

## Критерії приймання

1. Один спотер: `storage_id` посилається на CREW спотера, фактичний дебет — на його FLY_POINT. Залишок CREW незмінний. History потребує окремої перевірки.
2. Два спотери, перша точка без ресурсу: повний дебет із другої; перша незмінна.
3. Ресурс є в обох: повний дебет із першої, друга незмінна. Порядок `spotter_team_id` збережений у `storage_id/alternative_storage_ids`.
4. На жодній точці немає достатнього залишку: жодного часткового дебету. Поточний completion-service ставить `FAILED`; поведінку створення запису при нульовому stock слід узгодити з окремою вимогою [no-stock](FLIGHT_WRITE_OFF_NO_STOCK_TEST_PLAN.md).
5. Повторна обробка source flight не створює другого списання чи повторного дебету.

## Ризики реалізації, які перевіряють тести

- У `tk/TeamRepository.streamNewJournalEntriesForCrews` `array_agg(spotter.team_storage_id)` не має `ORDER BY` за ordinality з `unnest`. Отже порядок масиву з Файти не гарантований. `TC-FAITA-SPOT-005` має це виявляти, а не приймати будь-яку перестановку.
- `SyncTeamProcess.createNewInventoryWriteOff` передає `flyPointId` із позиції вильоту. `InventoryWriteOffService.primaryStorageInWriteOff` надає цьому полю пріоритет над точкою спотера. Потрібно перевірити event, де позиція вильоту відрізняється від точки спотера; прямий completion-тест цього не доводить.
- Dev `syncTeams` у попередньому прогоні повертав HTTP 500. У такому разі висновок про маршрутизацію імпорту неможливий, але прямі тести completion можуть перевірити вибір точки за залишком.

## Автоматизація та прогін

Suite: `src/test/resources/suites/faita-spotter-write-off.xml`; клас: `FaitaSpotterWriteOffTest`. `001–004`, `002B` і `007` створюють FLIGHT write-off з контрольованими `storage_id/alternative_storage_ids/fly_point_storage_id` та завершують через API. `005` перевіряє route із `spotter_team_id` після dev sync. Прямі тести не доводять імпорт із `imp.ammo_agg_v2`.

```powershell
mvn test '-Denv=dev' '-Dsuite=faita-spotter-write-off' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dfaita.spotter.sync.enabled=true'
```

Власні `NEW/PENDING/FAILED` source записи та команди видаляються за ID; завершені списання й пов'язана історія залишаються для аудиту. Framework прибирає зареєстровані storage/region-фікстури. Release gate: P0 сценарії зелені на dev, перевірені stock delta та порядок ID; окремо підтверджений імпорт source `imp` і повторний sync.
