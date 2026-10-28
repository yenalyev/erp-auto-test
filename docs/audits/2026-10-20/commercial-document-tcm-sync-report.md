# Синхронізація тестів обов'язкових полів комерційного документа

20 жовтня 2026 року в project `1` production і local TCM додано **8 ACTIVE кейсів**,
**6 критеріїв приймання**, API/UI automation ID та документацію фічі `REQ-CD-REQ`.
Батьківська фіча — `REQ-DOCFLOW`, «Документообіг переміщень».
Повторне читання перевірило тексти, кроки, очікувані результати, прив'язки до АС,
`automation=true` й однаковий текст документації з `docs/REQ-CD-REQ.md`.

- **Production TCM:** [фіча DB ID 168](http://tcm.lab/projects/1?feature=168),
  [прогін 140](http://tcm.lab/test-runs/140), **8/8 PASS**.
  [Перевірка збережених об'єктів](commercial-document-tcm-prod-sync.json).
- **Local TCM:** [фіча DB ID 165](http://localhost:18100/projects/1?feature=165),
  [прогін 207](http://localhost:18100/test-runs/207), **8/8 PASS**.
  [Перевірка синхронізації](commercial-document-tcm-local-sync.json),
  [імпорт і повторне читання прогону](commercial-document-tcm-local-run.json).

## Покриття та результати

`TC-CD-REQ-API-001..004` — чотири API-кейси з 9, 9, 2 та 1 виконанням.
`TC-CD-REQ-UI-001..004` — чотири UI-кейси з одним виконанням кожен.
Разом **21 API + 4 UI = 25 PASS**, які агреговано у вісім результатів TCM-кейсів.
PASS параметризованого кейсу обчислюється тільки після перевірки всіх його варіантів.
Часи виконання взято з Allure та передано в часовій зоні Europe/Kiev, UTC+03:00
для 20.10.2026. Середовище виконання — ERP **dev**.

API-джерело — `target/commercial-document-final/allure-results`, тільки API ID.
UI-джерело — `target/commercial-document-final/ui-verified-allure-results`.
Початкові невдалі UI-спроби виправлено в Page Object; фінальні UI-результати взято
з окремого успішного повтору. Зелений повний suite одним запуском не заявляється.
[25 фактичних виконань і 8 агрегованих результатів](commercial-document-dev-results.json).

Framework попереднього етапу — **100 PASS**, включно з
`RegressionSuiteCompletenessTest`. Під час синхронізації змінювалися документація
й допоміжні Python-скрипти; ERP та Java-тести повторно не запускалися.
Фінальний UI-прогін завершив cleanup без залишків у черзі suite.

## Перевірки синхронізації

Production зміни та імпорт виконано через `tcm-ai-prod` MCP.
Local — через `http://localhost:18100`: налаштований `tcm-ai` вказував на старий порт
8100, глобальна конфігурація залишена без змін. Старіший local TCM не має AI endpoint
імпорту прогонів, тому використано підтримуваний `/api/autotest/runs` із попереднім
отриманням AUTO test plan. Повторні спроби використовували той самий `remoteRunId`
`commercial-document-required-fields-dev-20261020`.

Обидва завершені прогони містять рівно вісім потрібних automation ID,
без unmatched чи out-of-scope результатів. Поточний `executionStatus` кожного
кейсу після імпорту — PASS. DB ID фіч та АС незалежні в local і production TCM.

Для повтору синхронізації є `scripts/sync_tcm_commercial_document_required_fields.py`;
для відтворення агрегації з Allure — `scripts/report_commercial_document_required_fields.py`.
README містить посилання на вимоги та команду запуску окремого suite.
