# «Постачальник» — синхронізація TCM і перевірка коміту, 20.10.2026

Документацію `REQ-REL-SUPPLIER`, 8 критеріїв приймання та 12 автоматизованих кейсів
синхронізовано в project `1` обох TCM. Повторне читання підтвердило однакові вимоги,
кроки, очікувані результати й apiAutomationIds/uiAutomationIds.

- **Dev TCM:** `http://localhost:18100`, фіча DB ID **163**, створена під `REQ-REL-FILTERS`.
  [Перевірка документації та всіх полів кейсів](relocation-supplier-tcm-dev-sync.json).
- **Production TCM:** `http://tcm.lab`, фіча DB ID **166**, документація оновлена,
  наявні 12 кейсів уже відповідали джерелам. [Підтвердження](relocation-supplier-tcm-prod-sync.json).

Dev MCP був налаштований на старий порт 8100. Використано прямий локальний AI API на 18100
з локальним token, як вимагає правило `tcm-environments.mdc`; глобальну конфігурацію не змінено.
Історичний ERP dev-прогін 19.10.2026 залишається в production TCM run 139.
[Його результати та докази](../2026-10-19/relocation-supplier-dev-report.md).

## Перевірка саме вмісту коміту

Знімок створено з Git-бази `5c2b75e` та лише відібраних змін цієї фічі, без інших локальних правок.
Повна компіляція main/test успішна. Framework: **85 PASS / 0 FAIL / 0 SKIP**,
завершено 20.10.2026 о **09:37:47 Europe/Kiev**.
[Фактичний TestNG XML](relocation-supplier-commit-check-testng.xml).

Чиста перевірка виявила шість уже наявних класів без реєстрації у базовому master regression.
Додано тільки записи suite: `EquipmentUnitHistoryApiTest`, `ManualBatchCrewReceiveApiTest`,
`ResourceViewerNamedListsApiTest`, `ResourceViewerPm414ApiTest`, `RelocationFutureDateUiTest`,
`ResourceViewerNamedListsUiTest`. Перевірка повноти regression після цього — PASS.

До коміту включено два нові supplier-класи, їхні необхідні спільні fixture/Page Object,
equipment model DTO/схему/GET endpoint, поле `removed` для безпечного cleanup інвентаризації,
окремий suite, реєстрацію тестів, документацію, скрипти й збережені докази.
Спільні README, endpoint enum та suite-файли відібрано частково; інші локальні зміни збережено.
