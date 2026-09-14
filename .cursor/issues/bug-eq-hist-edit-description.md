## Опис

На вкладці «В дорозі» можна відредагувати видачу обладнання (форма «Редагування видачі», поле «Примітки»). PUT `/relocations/equipment/{id}/send` проходить успішно, але в «Історія операцій» і в «Історія» діалогу одиниці показується стандартне i18n-повідомлення (`equipment.history.sent`), а не оновлений текст з поля «Примітки».

Очікувана поведінка: REQ-OPER-HIST AC-12, REQ-EQU-004 AC-03.

**Корінь (бекенд):** `EquipmentHistoryService.onEquipmentSent` зберігає `message` з шаблону `equipment.history.sent`, а не `relocation.description`; update send не синхронізує історію.

## Steps to Reproduce

1. Авторизуватися під роллю Owner 1.
2. Обрати склад відправника.
3. Створити обладнання і передати його на інший склад (статус «В дорозі»).
4. Перейти в «Видати/Отримати» → «В дорозі».
5. Натиснути редагування видачі для цього переміщення.
6. Заповнити поле «Примітки» унікальним текстом (наприклад, `ui-eq-hist-edit-12345`).
7. Заповнити обов’язкове поле «Видав», якщо воно порожнє.
8. Натиснути «Підтвердити».
9. Відкрити `/history` на складі відправника або діалог одиниці на `/equipment`.

## Expected Result

У таблиці історії обладнання видно оновлений опис з кроку 6 (у колонці «Повідомлення» / текст операції «Відправлено»).

## Actual Result

Опис не змінюється; лишається шаблонне повідомлення на кшталт «Основний засіб … інв.номер … відправлено на локацію …», без тексту з «Примітки».

## Автотести (erp-auto-test)

- `TC-UI-HIST-EQ-005` — `EquipmentHistoryOnOperationsUiTest.editedEquipmentSendShowsUpdatedDescriptionOnOperationHistory`
- `TC-UI-EQ-HIST-005` — `EquipmentUnitHistoryUiTest.unitHistoryShowsEditedSendDescription`

Запуск: `mvn test -Denv=dev -Dsuite=equipment-history`
