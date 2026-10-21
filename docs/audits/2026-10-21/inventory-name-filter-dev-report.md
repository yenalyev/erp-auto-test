# Інвентаризація — фільтр по назві: dev, 21.10.2026

**9 PASS / 0 FAIL / 0 SKIP**: 5 API + 4 UI в одному завершеному запуску.
Компіляція, перевірки незмінності історії/залишків і cleanup завершилися успішно.
Час suite: 09:42–09:44 Europe/Kiev (UTC+03:00). Maven: BUILD SUCCESS, exit 0.

Перевірено вкладку **Інвентаризація → Закриті** (`/inventory-processes`):

- Видиме поле назви, повний збіг і підрядок, українські назви, різний регістр та пробіли навколо UI-запиту.
- Точні ID процесів і лише відповідні ресурси всередині розбіжностей одного процесу з трьома ресурсами.
- Зміна пошуку, результат без збігів та відновлення повних даних після очищення.
- Пагінація процесів і перетин назви з автором, станом та сумісною категорією.
- Excel через API та справжню кнопку UI: точні process/resource/delta, дробові числові розбіжності, порожня книга й очищення фільтра.

Тести створили власні локації 4503/4504 і по три ресурси на кожній.
Наприкінці власні залишки прибрані, ресурси й локації архівовані, помилок конфігурації немає.
Історія інвентаризацій залишається аудитом архівованих тестових локацій.
Backend і frontend не змінювалися.

## TCM

Документація [REQ-INV-NAME](../../REQ-INV-NAME.md), 3 AC та 9 кейсів з automation ID
збережені під `REQ-WMS-003` в обох TCM. Документація, кроки, очікування та automation links
перевірені повторним читанням; DB ID фіч незалежні: production 171, stage/local 167.

- [Production run 145](http://tcm.lab/test-runs/145): COMPLETED, environment=dev, 9 PASS.
- [Stage run 210](http://localhost:18100/test-runs/210): COMPLETED, environment=dev, 9 PASS.

В обох імпортах matched=9, unmatched/outOfScope порожні. Часи фактичних виконань взято
з Allure та передано як локальний час Europe/Kiev. Stage/local доступний на 18100;
налаштований `tcm-ai` використовував застарілий 8100, тому виконано прямий підтримуваний
API sync із чинним AI token без зміни глобальної конфігурації. Старіший stage імпортував
результати через `/api/autotest/runs` після отримання AUTO plan.

## Артефакти й відтворення

- [Фактичні 9 результатів](inventory-name-filter-dev-results.json).
- [Production sync](inventory-name-filter-tcm-prod-sync.json), [readback прогону](inventory-name-filter-tcm-prod-readback.json).
- [Stage sync](inventory-name-filter-tcm-stage-sync.json), [прогін](inventory-name-filter-tcm-stage-run.json).
- `target/inventory-name-filter-final-20261021/maven.log` — єдиний успішний dev suite.
- `target/inventory-name-filter-final-20261021/dev-testng-results.xml` — збережений TestNG результат 9 тестів і всіх configuration methods.
- `target/inventory-name-filter-final-20261021/allure-results` — скриншоти, XLSX і результати; `downloads` — файли з реальної кнопки.
- `target/inventory-name-filter-final-20261021/framework.log` — окрема перевірка `RegressionSuiteCompletenessTest`: 1 PASS.

Повтор dev: `scripts/run_inventory_name_filter_dev.ps1` або команда в документації вимог.
Кейси включені в `inventory-name-filter`, `inventory`, `inventory-process`, `regression`;
API також у `functional`, UI також у `ui`.
Скрипти `sync_tcm_inventory_name_filter.py` та `report_inventory_name_filter.py` відтворюють
payload/синхронізацію і формують результати тільки з фактичного завершеного прогону.

Межі: не перевірено цим suite фільтри дат, UI-вибір категорії, RBAC та межу Excel у 500 процесів.
