# Інвентаризація під час видачі — перевірка фікса

17.10.2026, 15:54–15:56 Europe/Kyiv. Середовище: dev.

**Фікс підтверджено: 7 PASS / 0 FAIL / 0 SKIP** — 4 API та 3 UI-сценарії.
TCM: [фіча REQ-WMS-003](https://tcm.lab/projects/1?feature=10),
[прогін №125](https://tcm.lab/test-runs/125). Імпорт зіставив усі сім
automation IDs; unmatched/outOfScope порожні. Результат перевірено повторним
читанням прогону. Документація доповнена AC-21/AC-22 і сімома кейсами.

## Підтверджена поведінка

1. A=10/B=20/C=30; у відкритій формі змінено A=15, після цього видано B=7/C=30.
2. Застарілий PUT повертає 412, актуальний snapshot і конфлікти тільки B/C.
   Залишки A=10/B=13/C=0, інвентаризаційна історія незмінена, процес OPEN.
3. Після прийняття актуальних кількостей і повторного збереження:
   **A=15/B=13/C=0**, закритий процес має рівно **A:+5**. Видані B/C не
   повертаються на залишки й не додаються до інвентаризаційної історії.
4. Ще одна видача B=2 після узгодження викликає новий 412; після повторного
   узгодження B=11 і diff все ще тільки A:+5.
5. Видача редагованого A=4 теж викликає конфлікт. Після явного вибору
   «Залишити моє» A=15, diff **+9 від поточного 6**.

Усі три UI-сценарії виконали справжню видачу в другій вкладці. Перевірено
«Прийняти всі актуальні», окреме узгодження, явне «Залишити моє»,
блокування submit, збереження коментаря та чернетки, таблицю залишків,
запис у «Закритих» і reload. Дві вкладки використовували адміністратора
в одному browser context з незалежними snapshots.

## Зміни та перевірки

- Додано `InventoryConcurrentIssueApiTest`, `InventoryConcurrentIssueUiTest`
  та ізольовану `InventoryConcurrentIssueFixture`.
- DTO підтримують `version` та `knownResourceIds`; page object — узгодження
  конфліктів. Наявні запити без версій збережено для сумісності.
- Набір `inventory-concurrent-issue`; класи включені до `inventory`,
  `inventory-process`, `regression`, відповідно API до `functional`, UI до `ui`.
- `mvn -o test-compile -DskipTests` — BUILD SUCCESS.
- `RegressionSuiteCompletenessTest` — 1 PASS / 0 FAIL / 0 SKIP;
  наявність нових класів у регресії підтверджена.
- Фінальний dev-прогін — BUILD SUCCESS, 7/7 PASS; cleanup без відмов.
  Завершені видачі лишилися в незмінюваній історії; власні ресурси й склади
  очищені та архівовані. Глобальний sweep вимкнено.

Перший sandbox-прогін не дістався ERP через DNS/Chromium restrictions.
У другій спробі виправлено тестову підготовку: `batches: null` замінено
порожнім списком, а відмінну частину назв перенесено на початок, бо таблиця
скорочує довгі назви. Фінальний прогін повторив усі сім тестів після цих змін.
Backend `tk` і frontend `tk-ui` не змінювалися.

## Артефакти

- [Контракт та сценарії](../../REQ-WMS-003-concurrent-issue.md).
- [Фактичні результати з Allure](inventory-concurrent-issue-dev-results.json).
- [TCM payload](inventory-concurrent-issue-tcm-payload.json),
  [перевірка прив’язок](inventory-concurrent-issue-tcm-readback.json),
  [імпорт і повторне читання прогону](inventory-concurrent-issue-tcm-run.json).
- [Конфлікти часткової та повної видачі](inventory-concurrent-issue-evidence/mixed-issue-conflicts.png).
- [Конфлікт редагованого ресурсу](inventory-concurrent-issue-evidence/edited-resource-conflict.png).
- [Вкладка закритих інвентаризацій](inventory-concurrent-issue-evidence/closed-process.png).
- Логи: `target/inventory-concurrent-compile-final.log`,
  `target/inventory-concurrent-dev-final.log`, `target/inventory-concurrent-framework.log`.
- TestNG XML фінального прогону: `target/inventory-concurrent-final/`;
  попередні спроби: `target/inventory-concurrent-attempt1/`,
  `target/inventory-concurrent-attempt2/`.

```powershell
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'
mvn test -Denv=dev -Dsuite=inventory-concurrent-issue -Dtcm.enabled=false -Dgoogle.sheets.enabled=false -Dsuite.artifact.sweep=false
```
