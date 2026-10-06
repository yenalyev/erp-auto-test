package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/** Admin page for inventory requests, open processes and completed history. */
public class InventoryProcessesPage extends BasePage {

    public InventoryProcessesPage(Page page) {
        super(page);
    }

    public InventoryProcessesPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + "/inventory-processes", "Інвентаризація");
        return waitForLoaded();
    }

    public InventoryProcessesPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        requestedTab().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public String requestedTabText() {
        return requestedTab().innerText().trim();
    }

    public String openTabText() {
        return page.getByTestId("inventory-processes-tab-open").innerText().trim();
    }

    public String completedTabText() {
        return page.getByTestId("inventory-processes-tab-closed").innerText().trim();
    }

    public boolean isRequestRowVisible(long processId) {
        Locator row = page.getByTestId("inventory-process-row-" + processId);
        return row.count() > 0 && row.first().isVisible();
    }

    public InventoryProcessesPage approve(long processId, String comment) {
        page.getByTestId("inventory-process-row-" + processId + "-open").click();
        page.getByTestId("inventory-process-open-comment").fill(comment);
        page.waitForResponse(
                response -> response.url().contains("/storages/inventory")
                        && "PUT".equals(response.request().method())
                        && response.status() >= 200
                        && response.status() < 300,
                () -> page.getByTestId("inventory-process-open-submit").click());
        return this;
    }

    public InventoryProcessesPage reject(long processId, String explanation) {
        page.getByTestId("inventory-process-row-" + processId + "-reject").click();
        page.getByTestId("inventory-process-reject-comment").fill(explanation);
        page.waitForResponse(
                response -> response.url().contains("/storages/inventory")
                        && "DELETE".equals(response.request().method())
                        && response.status() >= 200
                        && response.status() < 300,
                () -> page.getByTestId("inventory-process-reject-submit").click());
        return this;
    }

    private Locator requestedTab() {
        return page.getByTestId("inventory-processes-tab-requested");
    }
}
