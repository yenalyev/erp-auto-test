package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for production create wizard (tk-ui {@code ProductionCreateProductMainPage}).
 * URL: /production/createProduction
 */
@Slf4j
public class ProductionCreateFormPage extends BasePage {

    public static final String PATH = "/production/createProduction";

    private static final String PAGE_HEADING = "Запис про виготовлення";
    private static final String ALT_SECTION_TITLE = "Альтернативні ресурси";
    private static final String PRODUCT_LABEL = "Оберіть продукт...";
    private static final String TECH_MAP_LABEL = "Тех. карта";
    private static final String AMOUNT_LABEL = "Кількість";
    private static final String SHIFT_LABEL = "Зміна";
    private static final String EMPTY_ALT_OPTION = "Оберіть ресурс...";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

    public ProductionCreateFormPage(Page page) {
        super(page);
    }

    public ProductionCreateFormPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Запис про виготовлення");
        return waitForLoaded();
    }

    public ProductionCreateFormPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_HEADING))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.waitForTimeout(500);
        return this;
    }

    public ProductionCreateFormPage ensureShiftSelected() {
        page.waitForCondition(() -> !page.getByText("Завантаження змін...").isVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));

        Locator shiftTrigger = page.locator("[data-slot='select-trigger']")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("xpath=ancestor::div[.//label[contains(.,'" + SHIFT_LABEL + "')]]")))
                .first();
        if (shiftTrigger.count() == 0) {
            shiftTrigger = page.getByText(SHIFT_LABEL).locator("xpath=following::button[@data-slot='select-trigger'][1]");
        }
        if (shiftTrigger.count() > 0 && shiftTrigger.isVisible()) {
            String value = shiftTrigger.locator("[data-slot='select-value']").innerText().trim();
            if (value.isEmpty() || value.contains("Оберіть зміну")) {
                shiftTrigger.click();
                page.getByRole(AriaRole.OPTION)
                        .filter(new Locator.FilterOptions().setHasNotText("Оберіть зміну"))
                        .first()
                        .click();
                page.waitForTimeout(300);
            }
        }
        return this;
    }

    public ProductionCreateFormPage selectProduct(String productName) {
        String trimmed = productName.trim();
        String term = trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);

        Locator productInput = page.getByPlaceholder(PRODUCT_LABEL);
        productInput.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.waitForCondition(
                () -> productInput.isEnabled(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        productInput.click();
        productInput.fill(term);
        waitForComboboxOptionsSettled();

        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(trimmed))
                .first()
                .click();

        // After productId is set, techMapList loads; single-map products auto-select and stay disabled.
        page.waitForCondition(
                () -> {
                    Locator select = techMapSelect();
                    if (select.count() == 0) {
                        return false;
                    }
                    String value = select.first().inputValue();
                    return value != null && !value.isBlank();
                },
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Selects tech map by label. When the product has exactly one map, tk-ui {@code SelectField}
     * auto-selects it and keeps the control disabled — then this is a no-op wait for load.
     */
    public ProductionCreateFormPage selectTechMap(String techMapName) {
        Locator select = techMapSelect();
        page.waitForCondition(
                () -> {
                    if (select.count() == 0) {
                        return false;
                    }
                    Locator first = select.first();
                    if (first.isEnabled()) {
                        return true;
                    }
                    String value = first.inputValue();
                    return value != null && !value.isBlank();
                },
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));

        if (select.first().isEnabled()) {
            select.first().selectOption(new SelectOption().setLabel(techMapName));
        }
        // Wait until tech-map GET populates groups (alt section) or fixed inputs.
        page.waitForCondition(
                () -> isAlternativeResourcesSectionVisible()
                        || page.getByText("Витрати ресурсів").count() > 0
                        && page.getByText("Витрати ресурсів").first().isVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    private Locator amountInput() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(AMOUNT_LABEL))
                .locator("xpath=following-sibling::input[1]");
    }

    private Locator techMapSelect() {
        Locator byLabel = page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(TECH_MAP_LABEL))
                .locator("xpath=following-sibling::select[1]");
        if (byLabel.count() > 0) {
            return byLabel;
        }
        return page.locator("select").filter(new Locator.FilterOptions()
                .setHas(page.locator("option").filter(new Locator.FilterOptions()
                        .setHasText("Оберіть тех. карту"))));
    }

    public ProductionCreateFormPage fillAmount(String amount) {
        amountInput().first().fill(amount);
        return this;
    }

    public boolean isAlternativeResourcesSectionVisible() {
        Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(ALT_SECTION_TITLE));
        if (heading.count() > 0 && heading.first().isVisible()) {
            return true;
        }
        Locator h4 = page.locator("h4").filter(new Locator.FilterOptions().setHasText(ALT_SECTION_TITLE));
        return h4.count() > 0 && h4.first().isVisible();
    }

    public String getSelectedAlternativeResourceLabel(String groupName) {
        Locator select = alternativeSelectForGroup(groupName);
        return select.locator("option:checked").innerText().trim();
    }

    public ProductionCreateFormPage selectAlternativeResource(String groupName, String optionLabelSubstring) {
        Locator select = alternativeSelectForGroup(groupName);
        Locator option = select.locator("option").filter(new Locator.FilterOptions()
                .setHasText(optionLabelSubstring));
        String value = option.first().getAttribute("value");
        select.selectOption(value);
        page.waitForTimeout(200);
        return this;
    }

    public ProductionCreateFormPage clearAlternativeSelection(String groupName) {
        Locator select = alternativeSelectForGroup(groupName);
        Locator option = select.locator("option").filter(new Locator.FilterOptions()
                .setHasText(EMPTY_ALT_OPTION));
        String value = option.first().getAttribute("value");
        select.selectOption(value != null ? value : "");
        page.waitForTimeout(200);
        return this;
    }

    public boolean isSubmitEnabled() {
        Locator button = submitButton();
        if (!button.isVisible()) {
            return false;
        }
        return button.isEnabled();
    }

    public ProductionCreateFormPage submit() {
        submitButton().click();
        page.waitForTimeout(1000);
        return this;
    }

    public boolean isOnProductionJournal() {
        return page.url().contains("/production") && !page.url().contains("createProduction");
    }

    private Locator submitButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Зберегти всі"));
    }

    private Locator alternativeSelectForGroup(String groupName) {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(groupName))
                .locator("xpath=following-sibling::select[1]");
    }
}
