# Підказки редагування техкарт результати dev

> Історичний звіт за первісною редакцією вимог. Після цього прогону користувач погодив помаранчевий бейдж і фактичний формат підказки; чинні вимоги наведено в [REQ-GP-HINT](../../REQ-GP-HINT.md). Наведені нижче FAIL збережено як фактичні результати попередніх запусків.

15 жовтня 2026 року. Середовище: https://dev.cukerochka.sbs. Повний ізольований запуск завершився о 13:53, цільовий повтор бейджа — о 13:57 за Europe/Kiev. Використано встановлений Chrome, справжні сесії та API; відповіді ERP не підмінялися.

## Результат

**Консолідований результат 10 сценаріїв: 8 PASS, 2 FAIL, 0 SKIP; configuration failures — 0.** Невідповідності: помаранчевий колір бейджа (TC-GP-HINT-UI-004) та текст підказки/формат назв планів (TC-GP-HINT-UI-006). Критерії AC-02 і AC-03 повністю не виконані.

Повний прогін спочатку дав 9 PASS/1 FAIL. Після візуальної перевірки скріншота критерій кольору TC-004 уточнено: перевірка переваги червоного RGB-каналу сама по собі пропускала помаранчевий. Повтор лише TC-004 з перевіркою відтінку дав FAIL і замінює його початковий PASS. Логіка решти дев'яти сценаріїв не змінювалася.

- **TC-GP-HINT-UI-001 — PASS.** Редактор глобальних планів бачить підказку і може скасувати модалку.
- **TC-GP-HINT-UI-002 — PASS.** Користувач без global-plan update не бачить підказку редагування.
- **TC-GP-HINT-UI-003 — PASS.** Збереження техкарти без global-plan update не відкриває модалку.
- **TC-GP-HINT-UI-004 — FAIL.** Редактор бачить червоний знак оклику замість числового бейджа.
- **TC-GP-HINT-UI-005 — PASS.** Користувач без global-plan update не бачить знак оклику.
- **TC-GP-HINT-UI-006 — FAIL.** Жовта підказка перелічує змінені карти та актуальні плани.
- **TC-GP-HINT-UI-007 — PASS.** Перейменування техкарти не створює повідомлення про недійсний розподіл.
- **TC-GP-HINT-UI-008 — PASS.** Перегенерація прибирає підказку лише для виправленого плану.
- **TC-GP-HINT-UI-009 — PASS.** Невикористана техкарта зберігається без попередження та модалки.
- **TC-GP-HINT-UI-010 — PASS.** Дозвіл працює незалежно від admin ролі та після відкликання.

Пройшли перевірки реального `global-plan::update` у ADMIN, його відсутності у OWNER_1 та надання/відкликання права в окремого non-admin. Підтверджено `!` без цифр, але його колір не відповідає вимозі. Також успішно перевірено скасування модалки без змін, збереження без модалки для користувача без update, перейменування без нової версії та відсутність попередження для невикористаної карти.

Повторне призначення й generate прибрали повідомлення тільки для виправленого плану; другий лишався недійсним до свого generate. Після кожного сценарію cleanup завершився без помилок: власні GP/location plans видалено, власні активні версії карт і ресурси архівовано, початковий режим редагування відновлено. Тестового non-admin деактивовано, його grants відкликано.

## Невідповідність кольору бейджа

**Сценарій:** TC-GP-HINT-UI-004, `GlobalPlanTechMapHintsUiTest#updaterSeesRedExclamationInsteadOfCount`.

Після structural edit карти у generated live GP користувач із global-plan::update бачить один знак `!` замість числа у пункті «Виробничі плани». Бейдж має бути червоним, фактичний фон — **rgb(254, 154, 0)**, помаранчевий.

Відтінок у просторі RGB — 36,38° від червоного; уточнений assert допускає до 15°. Computed CSS oklch() нормалізується браузером у RGB, тому перевірка не залежить від способу запису CSS-кольору.

[Скріншот фактичного бейджа](global-plan-tech-map-hints-evidence/TC-GP-HINT-UI-004-1.png).

## Невідповідність підказки глобальних планів

**Сценарій:** TC-GP-HINT-UI-006, `GlobalPlanTechMapHintsUiTest#globalPlansHintListsAffectedPlansAndMaps`.

1. Створено два майбутні generated GP з окремими техкартами та третій generated GP із незміненою картою.
2. Структурно змінено перші дві карти.
3. Відкрито «Глобальні плани» та розгорнуто список змінених карт.

**Очікувано:** назва плану за `planLabel` включає місяць і рік у дужках, якщо опис відрізняється від періоду. Повідомлення відповідає заданому тексту:

> Розподіл у глобальних планах … став недійсним для тех карт …. Відкрийте кожен план, призначте ресурс знову й повторно згенеруйте плани на локації.

**Фактично:**

- `GP-1/2027-1792061354712` показано без `(Січень 2027)`; другий план також без `(Лютий 2027)`.
- Показано `став недійсним для техкарт:`, далі — маркований список. Немає заданого переходу `. Відкрийте кожен план…`.
- Назви обох змінених карт присутні після розгортання списку. Незмінений третій план не входить до повідомлення на скріншоті.
- Інструкція повторного призначення та генерації присутня.

[Скріншот розгорнутої підказки](global-plan-tech-map-hints-evidence/TC-GP-HINT-UI-006-1.png).

Тест залишає очікування за вимогою. Оскільки assert тексту падає раніше, наступний CSS-assert жовтого фону цього кейсу не виконується; підказку видно на скріншоті.

## Перевірки фреймворку

Останній запуск `framework`: **80 PASS, 1 FAIL, 0 SKIP із 81**.

- `RegressionSuiteCompletenessTest` пройшов; новий клас присутній у `regression`, `ui`, `global-plans` і власному наборі.
- `HttpClientInfrastructureTest#allRequestTypesRespectTheConfiguredTimeout(JSON)` не пройшов часову межу: 5800 мс замість <5000 мс. Тип помилки SocketTimeoutException відповідає очікуванню.
- У попередньому ізольованому запуску той самий часовий assert показав 9687 мс. Повтор без компіляції також не пройшов; поріг і тест не змінювалися. Причину цієї окремої інфраструктурної проблеми не встановлено.

Повна ERP-регресія в межах цієї роботи не запускалася. До фінального прогону були спроби з відсутнім Chromium cache та конфліктом спільного каталогу компіляції; вони не використані як результат фічі. Фінальний прогін має власні classes, TestNG XML та Allure results.

## Докази

- [Права non-admin надані](global-plan-tech-map-hints-evidence/TC-GP-HINT-UI-010-1.png).
- [Після відкликання grant і нового входу](global-plan-tech-map-hints-evidence/TC-GP-HINT-UI-010-2.png).
- [Фактичний колір бейджа !](global-plan-tech-map-hints-evidence/TC-GP-HINT-UI-004-1.png).
- [Модалка підтвердження](global-plan-tech-map-hints-evidence/TC-GP-HINT-UI-001-1.png).
- [Машинні результати](global-plan-tech-map-hints-dev-results.json).
- [Тест-план і кроки](../../GLOBAL_PLAN_TECH_MAP_HINTS_TEST_PLAN.md).
- [Вимоги](../../REQ-GP-HINT.md).

Локальні артефакти повного прогону: `target/global-plan-hints-dev-final/surefire-reports/testng-results-full.xml`, `target/global-plan-hints-dev-final/allure-results`, `target/global-plan-tech-map-hints-dev-final.log`. Повтор бейджа: `target/global-plan-hints-dev-final/surefire-reports/testng-results-badge.xml`, `target/global-plan-hints-badge-verify/allure-results`, `target/global-plan-tech-map-hints-badge-verify.log`. Поточний `testng-results.xml` містить останній цільовий повтор; підсумок усіх десяти сценаріїв збережено в машинних результатах вище. Framework XML: `target/global-plan-hints-framework-final/surefire-reports/testng-results.xml`; останній лог: `target/global-plan-tech-map-hints-framework-rerun.log`.

## Відтворення

```powershell
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'
mvn -o test '-Denv=dev' '-Dsuite=global-plan-tech-map-hints' '-Dframework.browser.channel=chrome' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dtest.output.directory=target/global-plan-hints-dev-final' '-Dallure.results.directory=target/global-plan-hints-dev-final/allure-results'
mvn -o test '-Dsuite=framework' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false' '-Dtest.output.directory=target/global-plan-hints-framework-final'
```

Цільовий повтор використовував `-Dtest=GlobalPlanTechMapHintsUiTest#updaterSeesRedExclamationInsteadOfCount`, той самий build directory та окремий `allure.results.directory`. Після повтору XML скопійовано в `testng-results-badge.xml`; повний XML збережено до повтору.

Для наступних одночасних запусків використовувати окремі каталоги `test.output.directory`. Повтор framework без компіляції виконувався через `mvn -o surefire:test` з тими самими параметрами.

## Публікація в TCM

Production TCM не змінено. Підготовлено [пакет фічі, 4 критеріїв і 10 кейсів](global-plan-tech-map-hints-tcm-payload.json); Java IDs і IDs пакета збігаються.

Автоматична перевірка відхилила створення постійної фічі в production TCM без окремої згоди користувача, зокрема через відсутність API видалення фічі та критеріїв. Тести виконано з вимкненою зовнішньою публікацією результатів.
