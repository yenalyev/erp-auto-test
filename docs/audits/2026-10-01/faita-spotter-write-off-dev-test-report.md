# Dev прогін: списання з точки спотерів — 2026-10-01

## Результат

Перша suite: `mvn surefire:test '-Denv=dev' '-Dsuite=faita-spotter-write-off' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dfaita.spotter.sync.enabled=true'` після успішної `test-compile`: **5 тестів, 4 PASS, 1 FAIL**.

- `TC-FAITA-SPOT-001` — PASS: одна точка спотера втратила 2 одиниці, його CREW не змінився.
- `TC-FAITA-SPOT-002` — PASS: перша точка 0, дебет 2 одиниці з другої.
- `TC-FAITA-SPOT-003` — PASS: обидві точки мали ресурс, дебет 2 одиниці лише з першої.
- `TC-FAITA-SPOT-004` — PASS: обидві точки без ресурсу, статус `FAILED`, дебету немає.
- `TC-FAITA-SPOT-005` — FAIL/блоковано на імпорті: `POST /api/v1/integrations/faita/syncTeams` повернув **500**, body `Something went wrong`. Перевірка порядку source `spotter_team_id` і повторного sync не досягнута. Такий самий 500 був у попередніх FAITA dev-прогонах.

Адресний прогін після доповнення тестів: `mvn test '-Denv=dev' '-Dtest=FaitaSpotterWriteOffTest#secondSpotterIsUsedWhenFirstStockIsInsufficient+journalPositionDoesNotOverrideSpotterPoint' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false'`: **2 тести, 1 PASS, 1 FAIL**.

- `TC-FAITA-SPOT-002B` — PASS: `P1=1` при потребі 2, `P2=5`; перша незмінна, друга втратила 2.
- `TC-FAITA-SPOT-007` — FAIL: за наявності окремої позиції вильоту `P0` точка спотера `P1` мала delta **0 замість −2**, хоча completion повернув 200/`COMPLETED`. Див. [опис дефекту](../../bugs/FAITA_SPOTTER_POINT_OVERRIDDEN_BY_POSITION.md).

Фінальний read-only аудит `TC-FAITA-SPOT-CLEANUP-001` після обох прогонів — **PASS**: нуль test-owned Fight logs, нуль test-owned `crew.team` mapping для спотерів і нуль test-owned import write-off. Завершені списання прямого completion-сценарію залишені як історія; framework прибрав зареєстровані тестові storage/region.

## Межі висновку

П'ять успішних completion-сценаріїв використовували контрольований FLIGHT write-off з `storage_id/alternative_storage_ids`; вони перевіряють вибір точки й stock delta, але не імпорт `imp.ammo_agg_v2`. Через 500 немає підтвердження порядку `spotter_team_id` у створеному write-off. У backend `array_agg` спотерів без `ORDER BY` робить цей порядок недетермінованим; це окремий ризик для правила «в обох — з першої».

Наступний dev gate: відновити `syncTeams`, виправити пріоритет `flyPointStorage` для spotter event і стабілізувати порядок масиву; повторити повну suite та source E2E. Наразі вимога **не підтверджена** на dev.
