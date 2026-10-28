# Інвентаризація для адміна — dev, 27.10.2026

Результат: 7 PASS / 1 FAIL / 0 SKIP.
Configuration/cleanup failures: 0. Скріншотів: 13.

Набір `inventory-admin`: 2 API + 6 UI, дві окремі ролі з глобальним scope ALL.
Команда: `powershell -ExecutionPolicy Bypass -File scripts/run_inventory_admin_dev.ps1`.
Каталог виконання: `target/inventory-admin-dev-20261027-final`.

## Фактичні результати

- TC-INV-ADMIN-API-001: **PASS** (8266 ms).
- TC-INV-ADMIN-API-002: **PASS** (7939 ms).
- TC-INV-ADMIN-UI-001: **PASS** (5609 ms).
- TC-INV-ADMIN-UI-002: **PASS** (5133 ms).
- TC-INV-ADMIN-UI-003: **PASS** (7444 ms).
- TC-INV-ADMIN-UI-004: **PASS** (6810 ms).
- TC-INV-ADMIN-UI-005: **FAIL** (33979 ms).

```text
Timeout 30000ms exceeded
```

- TC-INV-ADMIN-UI-006: **PASS** (12525 ms).

## Висновок і межі перевірки

Основний сценарій вимоги проходить: обидві ролі бачать «Інвентаризація» у просторі «Всі локації» та точний глобальний REQUESTED-список. API перевіряє всі сторінки по 2 рядки; UI читає всі фактичні сторінки таблиці й звіряє назви локацій та причини запитів. Попередня недоступність розділу від 24.10 на поточному dev не відтворилась.

Уточнення вимоги від 27.10.2026: таблиця адміністратора має залишатися глобальною після вибору конкретної локації. Спостереження `TC-INV-ADMIN-UI-005` прийняте як очікувана поведінка, а попередня класифікація як регресії скасована. За вибраної A на скріншоті видно глобальні запити, включно з незалежною C. Історичний FAIL спричинило старе очікування GET з `parentStorageId=A` за 30 секунд. Результат 7 PASS / 1 FAIL і записи TCM збережено як факт виконання попередньої версії тесту; повторного прогону за оновленими кроками не було. Повернення до глобального простору в цьому кейсі не виконано; той самий повний перехід для ролі перегляду проходить (`UI-006`).

Виправлення тестів: назва локації береться з GET журналу (POST на dev повертає лише ID); перемикання очікує точний параметр scope, завершення відповіді й оновлення рядків React. Очікування бізнес-поведінки збережено.

Cleanup обох dev-прогонів завершився успішно: по п'ять власних локацій архівовано, по два власних користувачі деактивовано, active=false/enabled=false перевірено через GET. Перший запуск у sandbox зупинився на DNS до створення даних. Його результати й початкові помилки автотестів збережені окремо та не входять у фінальні 7/1.

Результати ERP dev імпортовано й перевірено в [TCM prod, run 175](http://tcm.lab/test-runs/175) та [TCM stage, run 236](http://localhost:18100/test-runs/236): 8/8 зіставлень, unmatched/outOfScope порожні.

Framework: **112 PASS / 0 FAIL / 0 SKIP**, `BUILD SUCCESS`, включно з `RegressionSuiteCompletenessTest`. [Машинний підсумок](inventory-admin-framework-results.json), [TestNG](inventory-admin-framework-testng-results.xml). Виконання `inventory-admin-framework-20261027-verified` проведено поза sandbox після зависання початкового sandbox-прогону на локальних інфраструктурних перевірках.

## Артефакти

- [Вимоги, 3 AC та 8 кейсів](../../REQ-INV-ADMIN.md).
- [Машинні результати](inventory-admin-dev-results.json).
- [TestNG](inventory-admin-evidence/testng-results.xml).
- Скріншоти — `inventory-admin-evidence/`; повні HTTP attachments — Allure каталогу виконання.
