package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

@Slf4j
public class RelocationCreateOutputCrewPage extends BasePage {

    public static final String PATH = "/relocation/create-output-crew";
    private static final String TITLE = "Видача на екіпаж";
    private static final String SUBMIT = "Підтвердити";
    private static final String CREW_PLACEHOLDER = "Оберіть екіпаж...";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";
    private static final String PRODUCT_LIST_LABEL = "Список продукції";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

    public RelocationCreateOutputCrewPage(Page page) {
        super(page);
    }

    public RelocationCreateOutputCrewPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor();
        waitForCrewFormBootstrap();
        page.getByPlaceholder(CREW_PLACEHOLDER).waitFor();
        return this;
    }

    private void waitForCrewFormBootstrap() {
        page.waitForCondition(() -> {
            Locator loading = page.getByText("Завантаження...");
            if (loading.count() > 0 && loading.isVisible()) {
                return false;
            }
            return unitComboboxTrigger().isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator unitComboboxTrigger() {
        return page.getByText("Підрозділ чи точка вильоту", new Page.GetByTextOptions().setExact(true))
                .locator("xpath=following::button[@role='combobox'][1]");
    }

    public boolean isLoaded() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE)).isVisible();
    }

    public RelocationCreateOutputCrewPage selectUnitByName(String unitName) {
        unitComboboxTrigger().click();
        page.locator("[data-radix-popper-content-wrapper] button")
                .filter(new Locator.FilterOptions().setHasText(unitName))
                .first()
                .click();
        page.waitForTimeout(500);
        return this;
    }

    public RelocationCreateOutputCrewPage selectCrewByName(String crewName) {
        Locator crewInput = page.getByPlaceholder(CREW_PLACEHOLDER);
        crewInput.waitFor();
        crewInput.click();
        crewInput.fill(crewName);
        waitForComboboxOptionsSettled();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(crewName))
                .first()
                .click();
        return this;
    }

    public boolean isCrewComboboxEmpty() {
        Locator crewInput = page.getByPlaceholder(CREW_PLACEHOLDER);
        crewInput.click();
        waitForComboboxOptionsSettled();
        boolean empty = page.getByText("Не знайдено").isVisible()
                || page.locator(COMBOBOX_ITEM_SELECTOR).count() == 0;
        page.keyboard().press("Escape");
        return empty;
    }

    public RelocationCreateOutputCrewPage selectResourceByName(String resourceNamePart) {
        String searchTerm = resourceNamePart.length() > 12
                ? resourceNamePart.substring(0, 12)
                : resourceNamePart;
        Locator resourceInput = page.getByPlaceholder(RESOURCE_PLACEHOLDER);
        resourceInput.click();
        resourceInput.fill(searchTerm);
        waitForComboboxOptionsSettled();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(resourceNamePart))
                .first()
                .click();
        return this;
    }

    public RelocationCreateOutputCrewPage fillQuantity(String amount) {
        page.getByPlaceholder(QUANTITY_PLACEHOLDER).fill(amount);
        return this;
    }

    public String getQuantityValue(int rowIndex) {
        return page.getByPlaceholder(QUANTITY_PLACEHOLDER).nth(rowIndex).inputValue();
    }

    public boolean isBundleBadgeVisible(String bundleName) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(bundleName)).isVisible();
    }

    public RelocationCreateOutputCrewPage clickBundleBadge(String bundleName) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(bundleName)).click();
        return this;
    }

    public RelocationCreateOutputCrewPage waitForToast(String text) {
        page.getByText(text).waitFor();
        return this;
    }

    public int productRowCount() {
        return page.getByPlaceholder(RESOURCE_PLACEHOLDER).count();
    }

    /**
     * Visible product-list heading, including the selected crew's fly-point note.
     * The UI renders the base label and note as sibling elements, so join them in
     * visual order to assert the user-facing text as one normalized label.
     */
    public String getProductListLabel() {
        Locator baseLabel = page.getByText(
                PRODUCT_LIST_LABEL,
                new Page.GetByTextOptions().setExact(true));
        baseLabel.waitFor();
        return baseLabel.locator("xpath=..").locator(":scope > div").allInnerTexts().stream()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" "));
    }

    public RelocationCreateOutputCrewPage fillIssuer(String name, String rank) {
        page.getByText("Видав Ім'я та Прізвище")
                .locator("xpath=following::input[1]")
                .fill(name);
        page.getByText("Звання (того, хто видав)")
                .locator("xpath=following::input[1]")
                .fill(rank);
        return this;
    }

    public RelocationCreateOutputCrewPage fillDescription(String description) {
        page.locator("#description").fill(description);
        return this;
    }

    public boolean isSubmitDisabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).isDisabled();
    }

    public RelocationPage submitAndWaitForJournal() {
        page.waitForResponse(
                response -> response.url().contains("/relocations/send")
                        && "POST".equals(response.request().method()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click());
        return new RelocationPage(page).waitForLoaded();
    }

    public RelocationCreateOutputCrewPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Видача на екіпаж");
        return waitForLoaded();
    }
}
