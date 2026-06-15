package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the Non-Series Production list page.
 * URL: /non-series-production
 */
@Slf4j
public class NonSeriesProductionListPage extends BasePage {

    public static final String PATH = "/non-series-production";
    public static final String PATH_CREATE = "/non-series-production/create";

    private static final String TITLE_TEXT = "Несерійне виробництво";
    private static final String NEW_ITEM_BUTTON = "Новий виріб";
    private static final String PRODUCT_FILTER_PLACEHOLDER = "Назва продукту...";
    private static final String STATUS_DONE = "Завершено";
    private static final String STATUS_IN_PROGRESS = "В роботі";

    public NonSeriesProductionListPage(Page page) {
        super(page);
    }

    public NonSeriesProductionListPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Non-Series Production list: {}", url);
        navigateTo(url, TITLE_TEXT);
        return waitForLoaded();
    }

    public NonSeriesProductionListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached — proceeding: {}", e.getMessage());
        }

        Locator pageReady = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE_TEXT))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_ITEM_BUTTON)))
                .or(page.locator("table").first())
                .first();

        pageReady.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public NonSeriesProductionFormPage clickNewItem() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_ITEM_BUTTON)).click();
        return new NonSeriesProductionFormPage(page).waitForLoaded();
    }

    public NonSeriesProductionListPage filterByProduct(String productName) {
        page.getByPlaceholder(PRODUCT_FILTER_PLACEHOLDER).fill(productName);
        waitForFilterResult(productName);
        return this;
    }

    private void waitForFilterResult(String productName) {
        int timeoutMs = uiTimeoutMs();
        log.debug("Waiting up to {}s for filter result: {}", timeoutMs / 1000, productName);

        rowForProduct(productName).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
    }

    public NonSeriesProductionFormPage clickEditForProduct(String productName) {
        rowForProduct(productName)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Редагувати"))
                .click();
        return new NonSeriesProductionFormPage(page).waitForEditLoaded();
    }

    public boolean isProductVisible(String productName) {
        return rowForProduct(productName).count() > 0;
    }

    public boolean isStatusVisibleForProduct(String productName, String statusLabel) {
        Locator row = page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(productName));
        return row.count() > 0 && row.first().getByText(statusLabel).isVisible();
    }

    public boolean isDoneStatusVisibleForProduct(String productName) {
        return isStatusVisibleForProduct(productName, STATUS_DONE);
    }

    public boolean isInProgressStatusVisibleForProduct(String productName) {
        return isStatusVisibleForProduct(productName, STATUS_IN_PROGRESS);
    }

    public boolean isOnListPage() {
        return page.url().contains(PATH) && !page.url().contains("/create") && !page.url().contains("/update");
    }

    private Locator rowForProduct(String productName) {
        return page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(productName));
    }
}
