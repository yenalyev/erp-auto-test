package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for technological map create/update form (tk-ui {@code TechnologicalMapForm.tsx}).
 * URLs: /technological-maps/create, /technological-maps/update/{id}
 */
@Slf4j
public class TechnologicalMapFormPage extends BasePage {

    public static final String PATH_CREATE = "/technological-maps/create";
    public static final String TYPE_PRODUCTION = "Виготовлення";
    public static final String TYPE_DISASSEMBLE = "Розбирання";

    private static final String INPUT_SECTION_TITLE = "Вхідні ресурси (Витрати)";
    private static final String OUTPUT_SECTION_TITLE = "Вихідні ресурси (Готова продукція)";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String SEARCH_PLACEHOLDER = "Пошук...";
    private static final String SUBMIT_BUTTON_TEXT = "Зберегти";
    private static final String NAME_PLACEHOLDER = "Наприклад: ПТМ-3 (з ТГА)";

    public TechnologicalMapFormPage(Page page) {
        super(page);
    }

    public TechnologicalMapFormPage openCreate() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH_CREATE, "Нова технологічна карта");
        return waitForLoaded();
    }

    public TechnologicalMapFormPage openUpdate(long techMapId) {
        String path = "/technological-maps/update/" + techMapId;
        navigateTo(ConfigProvider.getBaseUrl() + path, "Редагування тех. карти");
        return waitForUpdateLoaded();
    }

    public TechnologicalMapFormPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Нова технологічна карта"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.getByPlaceholder(NAME_PLACEHOLDER).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public TechnologicalMapFormPage waitForUpdateLoaded() {
        page.waitForURL("**/technological-maps/update/**",
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Створіть нову версію тех. карти"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public TechnologicalMapFormPage selectType(String typeLabel) {
        page.getByRole(AriaRole.COMBOBOX)
                .filter(new Locator.FilterOptions().setHasText("Оберіть тип"))
                .first()
                .click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(typeLabel).setExact(true))
                .click();
        page.waitForTimeout(300);
        return this;
    }

    public TechnologicalMapFormPage fillName(String name) {
        page.getByPlaceholder(NAME_PLACEHOLDER).fill(name);
        return this;
    }

    public TechnologicalMapFormPage clickAddInputRow() {
        sectionCard(INPUT_SECTION_TITLE)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Додати"))
                .click();
        page.waitForTimeout(200);
        return this;
    }

    public TechnologicalMapFormPage selectInputResource(int rowIndex, String resourceName) {
        selectResourceInSection(INPUT_SECTION_TITLE, rowIndex, resourceName);
        return this;
    }

    public TechnologicalMapFormPage selectOutputResource(int rowIndex, String resourceName) {
        selectResourceInSection(OUTPUT_SECTION_TITLE, rowIndex, resourceName);
        return this;
    }

    public TechnologicalMapFormPage fillInputAmount(int rowIndex, String amount) {
        fillAmountInSection(INPUT_SECTION_TITLE, rowIndex, amount);
        return this;
    }

    public TechnologicalMapFormPage fillOutputAmount(int rowIndex, String amount) {
        fillAmountInSection(OUTPUT_SECTION_TITLE, rowIndex, amount);
        return this;
    }

    public TechnologicalMapFormPage submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT_BUTTON_TEXT))
                .click();
        page.waitForTimeout(500);
        return this;
    }

    public boolean isErrorVisible() {
        return page.locator(".bg-red-50").isVisible();
    }

    public String getErrorText() {
        return page.locator(".bg-red-50 span").innerText().trim();
    }

    public boolean isOnCreatePage() {
        return page.url().contains(PATH_CREATE);
    }

    public boolean isOnUpdatePage() {
        return page.url().contains("/technological-maps/update/");
    }

    private Locator sectionCard(String sectionTitle) {
        return page.locator("h3")
                .filter(new Locator.FilterOptions().setHasText(sectionTitle))
                .locator("xpath=ancestor::div[contains(@class,'rounded-xl')][1]");
    }

    private void selectResourceInSection(String sectionTitle, int rowIndex, String resourceName) {
        Locator section = sectionCard(sectionTitle);
        Locator combobox = section.getByRole(AriaRole.COMBOBOX).nth(rowIndex);
        combobox.click();

        String term = autocompleteSearchTerm(resourceName);
        Locator searchInput = page.getByPlaceholder(SEARCH_PLACEHOLDER);
        searchInput.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        searchInput.fill(term);
        waitForComboboxOptionsSettled();

        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(resourceName.trim()))
                .first()
                .click();
        page.waitForTimeout(200);
    }

    private void fillAmountInSection(String sectionTitle, int rowIndex, String amount) {
        Locator section = sectionCard(sectionTitle);
        Locator amountInput = section.locator("input[type='number']").nth(rowIndex);
        amountInput.fill(amount);
        page.waitForTimeout(200);
    }

    private static String autocompleteSearchTerm(String resourceName) {
        String trimmed = resourceName.trim();
        if (trimmed.length() <= 12) {
            return trimmed;
        }
        return trimmed.substring(0, 12);
    }
}
