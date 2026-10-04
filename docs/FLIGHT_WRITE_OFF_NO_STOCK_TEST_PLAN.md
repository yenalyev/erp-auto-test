# Списання згідно ЖБД: статуси та відсутній залишок

## Мета і межі

Перевірити життєвий цикл запису `source=FLIGHT` від журналу ЖБД до журналу списань ERP: стан, фактичний дебет, повторну обробку та видимість результату через API/UI. Цей план доповнює [план FAITA/crew sync](FAITA_CREW_SYNC_TEST_PLAN.md). Окремо перевіряємо нульовий і недостатній залишок на **фактичному складі списання**: `FLY_POINT` для прив'язаного екіпажа, `CREW` для екіпажа без точки, явний `flyPointStorage`/alternative storage за їхнім пріоритетом.

## Контракт, який треба зафіксувати

Уточнений контракт: якщо на фактичному складі немає залишку для ресурсу ЖБД, **запис списання не створюється в журналі**. Немає дебету та операції в history. Це правило перевіряється на етапі імпорту source події; прямий `INSERT` у `storage_item_write_off` не може довести його. Для часткового залишку (`0 < stock < amount`) критерій пропуску поки не зафіксований: кейс позначено як питання до вимоги.

### Поточна реалізація на момент аналізу

- `StorageItemWriteOffStatus`: `PENDING`, `COMPLETED`, `FAILED`, `REJECTED_USER`.
- `InventoryWriteOffService.complete` переводить mapped запис без достатнього stock у `FAILED` і не робить дебет. Повторна поставка може перевести `FAILED` назад у `PENDING`.
- `InventoryWriteOffAutoCompleteProcess` обирає тільки `PENDING` із зіставленим ресурсом, викликає той самий `complete`.
- `SyncTeamProcess.createNewInventoryWriteOff` створює рядки для основного ресурсу, ініціатора та implicit ресурсів до автозавершення; перевірки залишку на етапі імпорту немає. Тому `TC-FLIGHT-NOSTOCK-001` очікувано виявить розбіжність, коли dev sync увімкнений.
- Dashboard запит рахує `REJECTED`, тоді як enum містить `REJECTED_USER`; його показник skipped потребує окремої перевірки.

## Критерії приймання

1. Для відомої події ЖБД один `sourceId` створює один логічний набір списань; повторний sync не дублює рядків і дебету.
2. Достатній залишок: `COMPLETED` (або узгоджений auto status), дебет рівно `amount` на правильному складі, інші склади без змін.
3. Нульовий залишок: source подія оброблена, у `storage_item_write_off` і API-журналі немає рядка для неї, stock та history незмінні.
4. `PENDING`, `COMPLETED`, `FAILED`, `REJECTED_USER` мають однозначні назви/лічильники у API та UI; явне відхилення відрізняється від автоматичного пропуску.
5. Повторний sync не додає проігнорованого запису заднім числом без окремо погодженого правила; поведінку після поставки треба уточнити.

## Тестові дані та ізоляція

Унікальні `team_id`, `sourceId`, зовнішній ID і ERP ресурс на кожен сценарій. Матриця stock: `0`, `amount-1`, `amount`, `amount+1`; mapped/unmapped; екіпаж із точкою та без. Для E2E тесту контрольовано сіємо `crew.team` і `crew.team_flight_log`, викликаємо dev `/api/v1/integrations/faita/syncTeams`, звіряємо DB та API журнал і очищаємо source fixtures. Якщо dev sync bean вимкнений, source log залишиться `NEW`, що є невиконаною передумовою, а не успіхом тесту. DB seed готового write-off допустимий лише для окремого status-кейсу. Перед/після фіксувати API stock, рядки журналу і resource operation history.

## Пріоритет і покриття

| ID | Пріоритет | Сценарій | Очікування | Автоматизація |
| --- | --- | --- | --- | --- |
| TC-FLIGHT-NOSTOCK-001 | P0 | `FLIGHT` source event, mapped, stock=0, sync | Source оброблено; рядка списання немає; stock незмінний | `FlightWriteOffNoStockTest` (source DB seed + sync API) |
| TC-FLIGHT-STATUS-002 | P1 | Явне reject із `PENDING` | `REJECTED_USER`, stock незмінний | `FlightWriteOffNoStockTest` |
| TC-FLIGHT-003 | P0 | Подія ЖБД, stock=`amount` на FLY_POINT | Один запис, terminal success, дебет `amount` тільки з точки | Потрібен E2E import |
| TC-FLIGHT-004 | P0 | Подія ЖБД, stock=0 на точці, stock>0 на CREW | Рядка списання немає; CREW не використано як fallback | Потрібен E2E import |
| TC-FLIGHT-005 | P0 | Подія ЖБД, stock=`amount-1` | Без часткового дебету; створювати чи пропускати рядок — уточнити | Потрібен E2E import |
| TC-FLIGHT-006 | P1 | Поставка/повторний sync після проігнорованого event | Правило повторної обробки треба уточнити; дублі неприпустимі | Потрібен E2E import |
| TC-FLIGHT-007 | P1 | Unmapped ресурс, stock є | Чіткий статус, нульовий дебет до mapping | Потрібен E2E import |
| TC-FLIGHT-008 | P1 | Один event з основним, ініціатором та implicit ресурсом; один без stock | Незалежні стани, жодних дублів/часткового дебету в окремому рядку | Потрібен E2E import |
| TC-FLIGHT-009 | P1 | Фільтри й лічильники статусів журналу | API, UI і dashboard узгоджені | Потрібен API/UI тест |

## Виконання

```powershell
mvn test '-Denv=dev' '-Dsuite=flight-write-off' '-Duse.database=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false'
```

Умови: доступ до dev API, адміністраторський акаунт та dev DB; `syncTeams` має бути увімкнений. `TC-FLIGHT-NOSTOCK-001` перевіряє саме source log → sync → відсутність write-off, `TC-FLIGHT-STATUS-002` — окремий статус через DB seed. Release gate доповнити `003–009` після уточнення решти контракту.
