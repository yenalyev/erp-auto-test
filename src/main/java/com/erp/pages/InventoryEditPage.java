package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for inventory conduct form.
 * URL: /inventory/{storageId}
 */
@Slf4j
public class InventoryEditPage extends BasePage {

    private static final String TITLE_PREFIX = "Інвентаризація";

    public InventoryEditPage(Page page) {
        super(page);
    }

    public InventoryEditPage open(long storageId) {
        String url = ConfigProvider.getBaseUrl() + "/inventory/" + storageId;
        navigateTo(url, "Інвентаризація (/inventory/" + storageId + ")");
        return waitForLoaded();
    }

    public InventoryEditPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByText(TITLE_PREFIX).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public InventoryEditPage updateAmountForResource(String resourceName, String amount) {
        resourceRow(resourceName).locator("input[type='number']").fill(amount);
        return this;
    }

    public InventoryEditPage removeResource(String resourceName) {
        resourceRow(resourceName).getByRole(AriaRole.BUTTON).last().click();
        return this;
    }

    public boolean isResourceListed(String resourceName) {
        return resourceRow(resourceName).count() > 0;
    }

    public String getResourceAmountInputValue(String resourceName) {
        return resourceRow(resourceName).locator("input[type='number']").inputValue();
    }

    public InventoryEditPage addResource(String resourceName, String amount) {
        page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText("Оберіть ресурс"))
                .click();
        String searchTerm = resourceName.trim();
        if (searchTerm.length() < 2) {
            searchTerm = resourceName;
        } else {
            searchTerm = searchTerm.substring(0, Math.min(searchTerm.length(), 8));
        }
        page.getByPlaceholder("Пошук...").last().fill(searchTerm);
        page.waitForTimeout(500);
        Locator option = page.locator("[cmdk-item], [role='option'], [data-slot='command-item']")
                .filter(new Locator.FilterOptions().setHasText(resourceName.trim()))
                .first();
        option.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        option.click();
        page.locator("input[placeholder='0']").last().fill(amount);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Додати")).click();
        return this;
    }

    public InventoryEditPage save() {
        page.waitForResponse(
                response -> response.url().contains("/inventory")
                        && !response.url().contains("/status")
                        && "PUT".equals(response.request().method())
                        && response.status() == 200,
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Зберегти")).click());
        page.waitForURL("**/unit-management**", new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public InventoryEditPage goBack() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Назад")).click();
        return this;
    }

    public boolean hasSaveError() {
        return page.locator("[role='alert']").isVisible();
    }

    private Locator resourceRow(String resourceName) {
        return page.locator("div.border-b")
                .filter(new Locator.FilterOptions().setHasText(resourceName.trim()));
    }
}
