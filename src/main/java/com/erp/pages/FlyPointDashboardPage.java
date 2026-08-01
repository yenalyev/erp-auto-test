package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Дашборд точок взлету — deep-link до інвентаризації FLY_POINT.
 * URL: /fly-point-dashboard
 */
@Slf4j
public class FlyPointDashboardPage extends BasePage {

    private static final String PATH = "/fly-point-dashboard";
    private static final String STOCKS_TAB = "Залишки на точках взлету";
    private static final String RESOURCE_FILTER_PLACEHOLDER = "Пошук по назві...";

    public FlyPointDashboardPage(Page page) {
        super(page);
    }

    public FlyPointDashboardPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        waitForFlyPointStocksDuring(() -> navigateTo(url, "Точки взлету"));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public FlyPointDashboardPage openWithFlyPointId(long flyPointId) {
        String url = ConfigProvider.getBaseUrl() + PATH + "?flyPointId=" + flyPointId;
        waitForFlyPointStocksDuring(() -> navigateTo(url, "Точки взлету (flyPointId)"));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public FlyPointDashboardPage waitForLoaded() {
        page.getByText(STOCKS_TAB).first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public FlyPointDashboardPage openStocksTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(STOCKS_TAB)).click();
        waitForLoadingHidden();
        return this;
    }

    public FlyPointDashboardPage filterByResourceName(String resourceName) {
        Locator input = page.getByPlaceholder(RESOURCE_FILTER_PLACEHOLDER).first();
        input.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        waitForFlyPointStocksDuring(() -> {
            input.fill("");
            input.fill(resourceName.trim());
        });
        return this;
    }

    /** Клік назви точки → /inventory?storageId=fpId */
    public FlyPointDashboardPage clickFlyPointInventoryLink(long flyPointId) {
        Locator link = flyPointInventoryLink(flyPointId);
        link.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        link.click();
        page.waitForURL(
                url -> url.contains("/inventory?storageId=" + flyPointId),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean hasFlyPointInventoryLink(long flyPointId) {
        return flyPointInventoryLink(flyPointId).count() > 0;
    }

    private Locator flyPointInventoryLink(long flyPointId) {
        return page.locator("a[href*='/inventory?storageId=" + flyPointId + "']").first();
    }

    private void waitForFlyPointStocksDuring(Runnable action) {
        page.waitForResponse(
                response -> response.url().contains("/fly-points/stocks")
                        && "GET".equals(response.request().method())
                        && response.status() == 200,
                action);
        waitForLoadingHidden();
    }

    private void waitForLoadingHidden() {
        Locator loading = page.getByText("Завантаження...").first();
        try {
            loading.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        } catch (RuntimeException ignored) {
            // already hidden / not present
        }
    }
}
