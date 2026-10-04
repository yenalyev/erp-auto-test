# Dev прогін: списання ЖБД без залишку — 2026-09-30

## Контракт

Підтверджено користувачем: за відсутності залишку **запис списання не створюється в журналі**. Тести стосуються лише автотестового репозиторію; логіка backend/frontend не змінювалася.

## Фінальний прогін

Команда: `mvn surefire:test -Denv=dev -Dsuite=flight-write-off -Duse.database=true -Dtcm.enabled=false -Dgoogle.sheets.enabled=false` після окремої компіляції нового класу `javac --release 21`.

| Кейс | Результат | Факт |
| --- | --- | --- |
| `TC-FLIGHT-STATUS-002` | **PASS** | `PUT /write-off/reject` повернув 200, API-журнал показав `REJECTED_USER`, залишок не змінився. |
| `TC-FLIGHT-NOSTOCK-001` | **BLOCKED / тест FAIL** | Підготовлено mapped ресурс, CREW з нульовим залишком і source flight log. `POST /api/v1/integrations/faita/syncTeams` повернув **500**, body `Something went wrong`. Перевірки відсутності write-off після успішного sync не виконалися. Точковий повтор (`flight-write-off-import.xml`) відтворив той самий 500. |

Surefire фінального повного suite: **2 tests, 1 passed, 1 failed, 0 skipped**. Це не підтвердження дефекту no-stock правила: імпортний endpoint впав раніше за цільові assertions. Тестові source log/team/write-off fixtures видаляються в `@AfterMethod`; reconciliation і ресурс — у `@AfterClass`; storage/region cleanup виконав наявний framework.

## Збірка і попередні спроби

- Стандартний `mvn test` не дійшов до запуску: `testCompile` блокує інший незбережений UI-тест `FaitaCrewSyncPositionUiTest.java:26` — `injectRoleSession` не знайдено. Цей файл не змінювали. Ізольована компіляція нового класу пройшла.
- Перший запуск suite зупинився на тимчасовому Playwright timeout форми входу; подальші запуски успішно авторизувалися.
- Після уточнення контракту тест було переписано з перевірки `PENDING` на source log → sync → відсутність запису. Виправлено фікстуру під поточну dev DB схему. Перші помилки фікстури не є результатом фічі.

## Що потрібно для завершення dev перевірки

Відновити `POST /integrations/faita/syncTeams` на dev або надати інший керований спосіб виконати імпорт source event. Потім повторити `flight-write-off-import.xml`, щоб отримати результат власне правила no-stock. Стандартний `mvn test` окремо потребує виправлення непов'язаного UI-класу. Код логіки застосунку в межах цієї роботи не змінювався.
