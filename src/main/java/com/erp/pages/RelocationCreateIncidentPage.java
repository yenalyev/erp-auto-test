package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class RelocationCreateIncidentPage extends BasePage {

    private static final String HEADING = "Надзвичайна подія під час переміщення";
    private static final String DESCRIPTION_PLACEHOLDER = "Додайте короткий опис події";
    private static final String SAVE_BUTTON = "Зберегти";
    private static final String PARTIAL_DELIVERY_LABEL = "Часткова доставка";

    public RelocationCreateIncidentPage(Page page) {
        super(page);
    }

    public RelocationCreateIncidentPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationCreateIncidentPage fillDescription(String description) {
        page.getByPlaceholder(DESCRIPTION_PLACEHOLDER).fill(description);
        return this;
    }

    public RelocationCreateIncidentPage selectPartialDelivery() {
        page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(PARTIAL_DELIVERY_LABEL))
                .click();
        deliveryStorageCombobox()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationCreateIncidentPage selectDeliveryStorage(String storageName) {
        deliveryStorageCombobox().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(storageName))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(storageName)).click();
        return this;
    }

    /**
     * Delivery-storage Select ({@code data-slot=select-trigger}), not the header DateRangePicker
     * which also exposes {@code role=combobox}.
     */
    private Locator deliveryStorageCombobox() {
        return page.getByRole(AriaRole.MAIN)
                .locator("[data-slot='select-trigger']")
                .first();
    }

    public RelocationCreateIncidentPage setDeliveredAmount(String resourceName, String amount) {
        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .first();
        row.locator("input[type='number']").fill(amount);
        return this;
    }

    public boolean isSaveDisabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_BUTTON))
                .isDisabled();
    }

    public boolean isExceedingMessageVisible() {
        return page.getByText("Перевищує кількість у переміщенні").count() > 0
                && page.getByText("Перевищує кількість у переміщенні").first().isVisible();
    }

    public boolean isOnCreateIncidentPage() {
        return page.url().contains("create-incident")
                && page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING)).isVisible();
    }

    public RelocationPage saveAndReturnToJournal() {
        page.waitForResponse(
                response -> response.url().contains("/incidents/relocations") && response.request().method().equals("POST")
                        && response.ok(),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_BUTTON)).click());
        page.waitForURL(
                url -> url.contains("/relocations") && !url.contains("create-incident"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new RelocationPage(page).waitForLoaded();
    }
}
