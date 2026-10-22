# Архівація моделі обладнання на dev

22.10.2026: **14 PASS / 0 FAIL / 0 SKIP**. BUILD SUCCESS; помилок setup/cleanup: 0.

Suite: Equipment Model Archive. Початок: 2026-10-22T11:16:06 EEST; завершення: 2026-10-22T11:19:06 EEST (Europe/Kyiv). 6 API-кейсів мають 7 виконань через AVAILABLE/IN_REPAIR; 7 UI-кейсів мають 7 виконань.

## Перевірене покриття

Архівація без несписаного обладнання; атомарна заборона для залишку й активної видачі; списання останньої одиниці; відмова повторної архівації; збереження метаданих та історичної одиниці; статус із назвою, категорією та пагінацією; початковий ACTIVE; скасування й підтвердження діалогу; reload; disabled-кнопка з поясненням; справжній автокомпліт і позитивний вибір активної моделі; повторна серверна валідація після появи залишку.

## Результати кейсів

- TC-EQA-API-003: PASS; виконань 1; 6560 мс.
- TC-EQA-API-001: PASS; виконань 1; 4142 мс.
- TC-EQA-API-004: PASS; виконань 1; 4580 мс.
- TC-EQA-API-005: PASS; виконань 1; 5259 мс.
- TC-EQA-API-002: PASS; виконань 2; 6074 мс.
- TC-EQA-API-006: PASS; виконань 1; 4685 мс.
- TC-EQA-UI-006: PASS; виконань 1; 9596 мс.
- TC-EQA-UI-003: PASS; виконань 1; 5373 мс.
- TC-EQA-UI-004: PASS; виконань 1; 8018 мс.
- TC-EQA-UI-001: PASS; виконань 1; 5902 мс.
- TC-EQA-UI-007: PASS; виконань 1; 6950 мс.
- TC-EQA-UI-005: PASS; виконань 1; 6675 мс.
- TC-EQA-UI-002: PASS; виконань 1; 9829 мс.

## Регресія довідника

**13 PASS / 0 FAIL / 0 SKIP**, BUILD SUCCESS. Попередній регресійний прогін: 2026-10-22T10:24:19 EEST. Перевірено старі пошук, категорії, сортування, пагінацію та метадані з новими колонками. Кейси TC-EQD-UI-002 і TC-EQD-UI-003 актуалізовано в коді та обох TCM.

## Очищення та попередні спроби

Перша спроба мала 14 PASS, але завершилася помилкою cleanup: CREATED внутрішню видачу не можна видалити. Cleanup виправлено: завершити видачу, списати одиницю, видалити незалежні отримання, архівувати моделі й власні локації. Історія FINISHED, її отримання та категорія зберігаються через правила API. Усі моделі cleanup перевіряє як ARCHIVED з equipmentCount=0. Попередні спроби recovery із configuration failure та SKIP не імпортовано як успішні прогони. Маніфест recovery обмежений двома власними переміщеннями 25709 і 25727; IDs звірено з фактичними відповідями API.

## TCM та докази

Документація, 4 AC та 13 кейсів з automation links прочитані назад після публікації: [prod TCM](http://tcm.lab/projects/1?feature=177), [stage TCM](http://localhost:18100/projects/1?feature=172). 13 підсумкових run items відображають 14 фактичних виконань; для двох AVAILABLE/IN_REPAIR зберігається гірший статус і сумарний час. Прогони мають environment=dev.

Cleanup після попереднього 503 завершено: видалено 6 одиниць, 6 моделей архівовано з equipmentCount=0; категорію 39 видалено, локації 4840/4841 деактивовано. Значення перевірено повторними GET. [Доказ cleanup](equipment-model-archive-cleanup-recovery-result.json).

Setup і cleanup поточного API/UI прогону завершені без помилок.

- Prod TCM: Test Run 163; імпорт і readback перевірені.
- Stage TCM: Test Run 224; імпорт і readback перевірені.

[Результати JSON](equipment-model-archive-dev-results.json), [TestNG XML](equipment-model-archive-dev-testng.xml), [регресія](equipment-model-archive-dictionary-regression.json), [скриншоти й cleanup](equipment-model-archive-evidence/equipment-model-archive-dev-20261022-recovered/).

Локальні Maven/Surefire/Allure: `C:\Users\gigam\IdeaProjects\erp-auto-test\target\equipment-model-archive-dev-20261022-recovered`. Повторний запуск: `powershell -ExecutionPolicy Bypass -File scripts/run_equipment_model_archive_dev.ps1`.
