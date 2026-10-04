# Dev-прогін: ініціатори FAITA — 2026-09-30

## Що запущено

Цільовий TestNG suite `faita-initiators` на `env=dev`, із `use.database=true`, без TCM і Google Sheets. Спроба `mvn test` зупинилася під час компіляції іншого незакоміченого класу `FaitaCrewSyncPositionUiTest`: він викликає відсутній `injectRoleSession(UserRole,long)`. Цей клас не входить у цільовий suite. Після успішної попередньої компіляції цільового класу виконано `mvn surefire:test` для ізоляції запуску.

## Результат

Перший `mvn surefire:test`: **FAIL**, TestNG: **1 configuration failure, 2 skipped, 0 кейсів із виконаними assertions**. `FaitaInitiatorWriteOffTest.setUp` не отримав браузерну OAuth-сесію: Playwright чекав `#username` 60 секунд і завершився timeout.

Після додавання опції runner `auth.browser.disabled=true` повторено цільовий suite через HTTP OAuth. **Фінальний dev-прогін: PASS, 3 passed, 0 failed, 0 skipped** (`target/surefire-reports/testng-results.xml`, 17:53–17:54 EEST). Точки вильоту ізольовані за кейсами. Підтверджено:

- `TC-FAITA-INIT-001`: зіставлений зовнішній ініціатор завершився за нульового залишку, залишок точки не змінився;
- `TC-FAITA-INIT-003`: зіставлений зовнішній ініціатор завершився за позитивного залишку, залишок точки не змінився;
- `TC-FAITA-INIT-002`: ініціатор `ПМ Цукрарня` завершився зі списанням із точки рівно на кількість FAITA.

Команда фінального прогону:

```powershell
mvn surefire:test '-Denv=dev' '-Dsuite=faita-initiators' '-Duse.database=true' '-Dauth.browser.disabled=true' '-Dtcm.enabled=false' '-Dgoogle.sheets.enabled=false'
```

`surefire:test` використано, бо сторонній незакомічений UI-клас блокує загальну компіляцію. Цільовий клас та залежності окремо скомпільовані Java 21 перед прогоном. Read-only HTTP-перевірка підтвердила, що `/server/login` і OAuth-форма доступні. Причина тайм-ауту Playwright лишається невстановленою; HTTP OAuth завершився успішно.

## Додатковий розрив продуктового контракту

Доступний backend переводить записи у `COMPLETED`; поточний UI відображає цей статус як «Проведено». У вимозі вказано «Проведено автоматично». Потрібно узгодити, чи змінювати видимий підпис і чи відрізняти автоматичне проведення від ручного. Новий API/DB suite перевіряє перехід у `COMPLETED` і зміну залишків, але не UI-підпис, імпорт події FAITA та плановий запуск процесу.
