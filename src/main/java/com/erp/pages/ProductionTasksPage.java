package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/** Queue shared by production tasks and order relocation requests. */
public class ProductionTasksPage extends BasePage {

    public static final String PATH = "/production-tasks";

    public ProductionTasksPage(Page page) {
        super(page);
    }

    public ProductionTasksPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Завдання");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Завдання"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationCreateOutputPage openRelocationRequest(long taskId) {
        String url = ConfigProvider.getBaseUrl()
                + RelocationCreateOutputPage.PATH
                + "?relocationTaskId=" + taskId;
        navigateTo(url, "Видача за завданням на переміщення #" + taskId);
        return new RelocationCreateOutputPage(page).waitForRelocationTaskLoaded();
    }

    public ProductionCreateFormPage openProductionTask(long productionOrderId, String resourceName) {
        Locator row = page.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("a[href*='productionOrderId=" + productionOrderId + "']")))
                .first();
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Виробити"))
                .click();
        return new ProductionCreateFormPage(page).waitForLoaded();
    }
}
