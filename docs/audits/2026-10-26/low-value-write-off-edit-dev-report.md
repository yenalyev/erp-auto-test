# Списання ресурсів МШП — редагування — dev, 26.10.2026

Виконання: 4 PASS / 3 FAIL / 0 BLOCKED / 0 SKIP.
Кейси TCM: 2 PASS / 1 FAIL / 0 BLOCKED. Configuration/cleanup failures: 0.

Команда: `powershell -ExecutionPolicy Bypass -File scripts/run_low_value_write_off_dev.ps1 -Suite low-value-write-off-edit`.
Каталог виконання: `target/low-value-write-off-edit-dev-20261026-recheck`. Час Europe/Kyiv (UTC+02:00).

Cleanup пройшов: власні списання й отримання видалені, нульові рядки прибрані, локації/ресурси архівовані.

Регресія ручного дефекту [BUG-MSHP-EDIT-FIFO](../../BUG-MSHP-EDIT-FIFO.md): початкові 10, списано 6, залишок 4; UI має дозволяти 4/6/10. API та попартійний UI — незалежні позитивні контролі.

## Фактичні результати

- TC-MSHP-API-010: **PASS**, виконань 3.
- TC-MSHP-UI-011: **FAIL**, виконань 3.

```text
parameters=[{"name": "amount", "value": "6"}]
Locator expected to be enabled
Call log:
  - Locator.expect with timeout 5000ms
  - waiting for locator("internal:attr=[data-testid=\"low-value-write-off-submit\"]")
    9 × locator resolved to <button disabled type="submit" data-slot="button" data-size="default" data-variant="default" data-testid="low-value-write-off-submit" class="cursor-pointer focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:aria-invalid:border-destructive/50 rounded-[5px] border border-transparent bg-clip-padding text-sm font-medium focus-visible:ring-[3px] aria-invalid:ring-[3px] [&_svg:not([class*='size-'])]…>Підтвердити</button>
      - unexpected value "disabled"

parameters=[{"name": "amount", "value": "4"}]
Locator expected to be enabled
Call log:
  - Locator.expect with timeout 5000ms
  - waiting for locator("internal:attr=[data-testid=\"low-value-write-off-submit\"]")
    9 × locator resolved to <button disabled type="submit" data-slot="button" data-size="default" data-variant="default" data-testid="low-value-write-off-submit" class="cursor-pointer focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:aria-invalid:border-destructive/50 rounded-[5px] border border-transparent bg-clip-padding text-sm font-medium focus-visible:ring-[3px] aria-invalid:ring-[3px] [&_svg:not([class*='size-'])]…>Підтвердити</button>
      - unexpected value "disabled"

parameters=[{"name": "amount", "value": "10"}]
Locator expected to be enabled
Call log:
  - Locator.expect with timeout 5000ms
  - waiting for locator("internal:attr=[data-testid=\"low-value-write-off-submit\"]")
    9 × locator resolved to <button disabled type="submit" data-slot="button" data-size="default" data-variant="default" data-testid="low-value-write-off-submit" class="cursor-pointer focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:aria-invalid:border-destructive/50 rounded-[5px] border border-transparent bg-clip-padding text-sm font-medium focus-visible:ring-[3px] aria-invalid:ring-[3px] [&_svg:not([class*='size-'])]…>Підтвердити</button>
      - unexpected value "disabled"
```

- TC-MSHP-UI-012: **PASS**, виконань 1.

## Артефакти

- [Вимоги та кейси](../../REQ-MSHP-resource-write-off.md).
- [Усі виконання та TCM payload](low-value-write-off-edit-dev-results.json).
- [TestNG](low-value-write-off-edit-evidence/testng-results.xml).
- Скріншоти — `low-value-write-off-edit-evidence/`; HTTP attachments — Allure каталогу виконання.
- [TCM prod](http://tcm.lab/projects/1?feature=180), [TCM stage](http://localhost:18100/projects/1?feature=175).

- [Прогін dev у TCM prod: 170](http://tcm.lab/test-runs/170).
- [Прогін dev у TCM stage: 231](http://localhost:18100/test-runs/231).