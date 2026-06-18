package com.erp.pages;

import com.erp.exceptions.ProductBugException;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page Object for create/edit non-series production form.
 */
@Slf4j
public class NonSeriesProductionFormPage extends BasePage {

    private static final String FORM_TITLE_CREATE = "Створення виробу";
    private static final String FORM_TITLE_EDIT = "Редагування виробу";
    private static final String SAVE_BUTTON = "Зберегти виріб";
    /**
     * Max wait for «Зберегти виріб» to become enabled after form changes.
     * Client-side validation should settle within a few seconds; longer disabled state = product bug.
     */
    private static final int SAVE_BUTTON_ENABLE_TIMEOUT_MS = 5_000;
    private static final String ADD_RESOURCE_BUTTON = "Додати";
    private static final String RESOURCE_COMBO_PLACEHOLDER = "Оберіть сировину...";
    private static final String RAW_MATERIALS_SECTION = "Використана сировина на одиницю";
    private static final String RESOURCE_COMBO_SELECTOR =
            "[data-slot='input-group-control'][role='combobox']";
    private static final String DETAILS_SECTION = "Деталі";
    private static final String STATUS_DONE = "Завершено";
    private static final String STATUS_IN_PROGRESS = "В роботі";
    private static final Pattern AVAILABLE_QUANTITY_PATTERN =
            Pattern.compile("доступно:\\s*([\\d.,]+)");

    public NonSeriesProductionFormPage(Page page) {
        super(page);
    }

    public NonSeriesProductionFormPage openCreate() {
        String url = ConfigProvider.getBaseUrl() + NonSeriesProductionListPage.PATH_CREATE;
        log.info("Opening Non-Series Production create form: {}", url);
        navigateTo(url, FORM_TITLE_CREATE);
        return waitForLoaded();
    }

    public NonSeriesProductionFormPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached — proceeding: {}", e.getMessage());
        }

        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(FORM_TITLE_CREATE))
                .or(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(FORM_TITLE_EDIT)))
                .first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_BUTTON))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));

        return this;
    }

    public NonSeriesProductionFormPage waitForEditLoaded() {
        page.waitForURL("**/non-series-production/update/**",
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        waitForLoaded();
        waitForResourceComboPopulated();
        return this;
    }

    private void waitForResourceComboPopulated() {
        Locator combos = rawMaterialsCard().locator(RESOURCE_COMBO_SELECTOR);
        combos.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));

        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            int count = combos.count();
            for (int i = 0; i < count; i++) {
                String value = combos.nth(i).inputValue();
                if (value != null && value.contains("доступно:")) {
                    return;
                }
            }
            page.waitForTimeout(200);
        }
        log.warn("Resource combobox value with «доступно:» not populated within timeout");
    }

    public boolean isEditMode() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(FORM_TITLE_EDIT))
                .isVisible();
    }

    public NonSeriesProductionFormPage fillProductName(String name) {
        inputNearLabel("Назва виробу").fill(name);
        return this;
    }

    public NonSeriesProductionFormPage setProductAmount(int amount) {
        inputNearLabel("Кількість (од.)").fill(String.valueOf(amount));
        return this;
    }

    public NonSeriesProductionFormPage setWorkerQty(int qty) {
        inputNearLabel("Робоча сила").fill(String.valueOf(qty));
        return this;
    }

    public NonSeriesProductionFormPage selectStatus(String statusLabel) {
        detailsCard()
                .getByRole(AriaRole.COMBOBOX)
                .click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(statusLabel)).click();
        return this;
    }

    public NonSeriesProductionFormPage selectStatusInProgress() {
        return selectStatus(STATUS_IN_PROGRESS);
    }

    public NonSeriesProductionFormPage selectStatusDone() {
        return selectStatus(STATUS_DONE);
    }

    public NonSeriesProductionFormPage addResourceUsage(String resourceName, double amountPerUnit) {
        return selectResourceInLastRow(resourceName)
                .setUsageAmountInLastRow(amountPerUnit);
    }

    public NonSeriesProductionFormPage selectResourceInLastRow(String resourceName) {
        String trimmedName = resourceName == null ? "" : resourceName.trim();

        rawMaterialsCard()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_RESOURCE_BUTTON))
                .click();

        Locator resourceCombo = rawMaterialsCard()
                .locator(RESOURCE_COMBO_SELECTOR + "[placeholder='" + RESOURCE_COMBO_PLACEHOLDER + "']")
                .last();
        resourceCombo.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        resourceCombo.click();
        resourceCombo.fill(trimmedName);

        Locator option = page.locator("[data-slot='combobox-item']")
                .filter(new Locator.FilterOptions().setHasText(trimmedName))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();
        return this;
    }

    public NonSeriesProductionFormPage setUsageAmountInLastRow(double amountPerUnit) {
        rawMaterialsCard()
                .locator("input[placeholder='0.00']")
                .last()
                .fill(String.valueOf(amountPerUnit));
        return this;
    }

    public double getDisplayedAvailableQuantity(String resourceName) {
        String trimmedName = resourceName == null ? "" : resourceName.trim();
        Locator resourceCombo = waitForResourceComboWithName(trimmedName);
        String displayText = resourceCombo.inputValue();
        assertThat(displayText)
                .as("Поле сировини має містити назву ресурсу «%s»", trimmedName)
                .contains(trimmedName);
        assertThat(displayText)
                .as("Поле сировини має показувати доступний залишок (формат «… - доступно: Nод.»)")
                .contains("доступно:");
        return parseAvailableQuantity(displayText);
    }

    /**
     * UI «доступно» = витрата на це несерійне виробництво + залишок з {@code GET /storages/{id}/inventory}.
     */
    public NonSeriesProductionFormPage assertDisplayedAvailableMatchesConsumedPlusInventory(
            String resourceName,
            double consumedForProduction,
            double apiInventoryAmount) {
        double expectedAvailable = consumedForProduction + apiInventoryAmount;
        double displayed = getDisplayedAvailableQuantity(resourceName);
        if (Math.abs(displayed - expectedAvailable) <= 0.01) {
            return this;
        }

        attachScreenshot("PRODUCT BUG — UI «доступно» ≠ витрата + API залишок");
        throw new ProductBugException(String.format(
                "[PRODUCT BUG] UI «доступно» (%.2f) не збігається з очікуваним "
                        + "(витрата %.2f + API залишок %.2f = %.2f), різниця %.2f. URL: %s",
                displayed, consumedForProduction, apiInventoryAmount, expectedAvailable,
                displayed - expectedAvailable, page.url()));
    }

    private Locator waitForResourceComboWithName(String resourceName) {
        Locator combos = rawMaterialsCard().locator(RESOURCE_COMBO_SELECTOR);
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            int count = combos.count();
            for (int i = 0; i < count; i++) {
                Locator combo = combos.nth(i);
                String value = combo.inputValue();
                if (value != null && value.contains(resourceName)) {
                    combo.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(1_000));
                    return combo;
                }
            }
            page.waitForTimeout(200);
        }
        throw new AssertionError(String.format(
                "Combobox для ресурсу «%s» не знайдено у блоці «%s» протягом %d мс",
                resourceName, RAW_MATERIALS_SECTION, uiTimeoutMs()));
    }

    private static double parseAvailableQuantity(String displayText) {
        Matcher matcher = AVAILABLE_QUANTITY_PATTERN.matcher(displayText);
        assertThat(matcher.find())
                .as("Не вдалося розпарсити доступну кількість з тексту: %s", displayText)
                .isTrue();
        return Double.parseDouble(matcher.group(1).replace(',', '.'));
    }

    public NonSeriesProductionFormPage assertSaveButtonPresent() {
        assertThat(saveButton().count())
                .as("Кнопка «%s» має бути на формі", SAVE_BUTTON)
                .isGreaterThan(0);
        assertThat(saveButton().isVisible())
                .as("Кнопка «%s» має бути видимою", SAVE_BUTTON)
                .isTrue();
        return this;
    }

    /**
     * Waits until save is clickable. If the button stays disabled past {@code timeoutMs},
     * fails with {@link ProductBugException} (application defect, not a flaky wait).
     */
    public NonSeriesProductionFormPage assertSaveButtonEnabledWithin(int timeoutMs) {
        assertSaveButtonPresent();

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (saveButton().isEnabled()) {
                return this;
            }
            page.waitForTimeout(200);
        }

        attachScreenshot("PRODUCT BUG — «" + SAVE_BUTTON + "» disabled");
        throw new ProductBugException(String.format(
                "[PRODUCT BUG] Кнопка «%s» залишається неактивною більш ніж %d с після зміни форми. URL: %s",
                SAVE_BUTTON, timeoutMs / 1000, page.url()));
    }

    public NonSeriesProductionListPage saveProduct() {
        assertSaveButtonEnabledWithin(SAVE_BUTTON_ENABLE_TIMEOUT_MS);
        saveButton().click();
        page.waitForURL("**" + NonSeriesProductionListPage.PATH,
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new NonSeriesProductionListPage(page).waitForLoaded();
    }

    private Locator saveButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_BUTTON));
    }

    private Locator rawMaterialsCard() {
        return page.locator("[data-slot='card']")
                .filter(new Locator.FilterOptions().setHasText("Використана сировина"));
    }

    private Locator detailsCard() {
        return page.locator("[data-slot='card']")
                .filter(new Locator.FilterOptions().setHasText(DETAILS_SECTION));
    }

    private Locator inputNearLabel(String labelText) {
        return page.getByText(labelText, new Page.GetByTextOptions().setExact(true))
                .locator("xpath=ancestor::div[contains(@class,'space-y')][1]")
                .locator("input, textarea")
                .first();
    }
}
