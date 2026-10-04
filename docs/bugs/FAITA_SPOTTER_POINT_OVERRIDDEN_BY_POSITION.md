# Дефект: позиція вильоту перехоплює списання у спотера

**Середовище:** dev, 2026-10-01. **Тест:** `TC-FAITA-SPOT-007`, `FaitaSpotterWriteOffTest.journalPositionDoesNotOverrideSpotterPoint`.

## Відтворення

1. Створити ресурс `R` та дві точки: спотера `P1` і позицію вильоту `P0`; на кожній по 5 одиниць `R`.
2. Створити FLIGHT write-off на 2 одиниці зі `storage_id=CREW` спотера (parent `P1`) та `fly_point_storage_id=P0`.
3. Викликати `PUT /api/v1/storages/inventory/write-off/complete` і прочитати залишки обох точок.

**Очікувано:** `P1: 5 → 3`, `P0: 5 → 5`, статус `COMPLETED`.

**Фактично:** API повернув 200, статус `COMPLETED`, але delta `P1=0` (5 → 5); тест упав на очікуваному дебеті `−2`. За поточною гілкою `InventoryWriteOffService.primaryStorageInWriteOff`, якщо `flyPointStorage != null`, сервіс вибирає його до точки спотера. Повний Fight import на dev не вдався через HTTP 500 від `syncTeams`, тому цей тест доводить помилку пріоритету completion-service, але не фактичне створення такого рядка імпортером на dev.

## Причина в коді та очікуване виправлення

`tk/SyncTeamProcess.createNewInventoryWriteOff` передає одночасно `storageId` зі `spotterStorageId[0]` і `flyPointId`, знайдений за `flyPointName` вильоту. `tk/InventoryWriteOffService.primaryStorageInWriteOff` безумовно бере `flyPointStorage`, якщо він заданий. Для події зі спотерами цільовим складом має бути точка першого спотера з достатнім залишком; позиція вильоту має лишатися окремими даними журналу. Після виправлення повторити `TC-FAITA-SPOT-007`, а потім повний source E2E.
