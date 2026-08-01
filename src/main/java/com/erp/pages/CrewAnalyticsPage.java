package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Аналітика екіпажів — вхід до інвентаризації CREW / FLY_POINT.
 * URL: /crew-analytics
 */
@Slf4j
public class CrewAnalyticsPage extends BasePage {

    private static final String PATH = "/crew-analytics";
    private static final String STOCKS_TAB = "Залишки на екіпажах";
    private static final String INCLUDE_FP_LABEL = "Враховувати залишки на точках взльоту";
    private static final String RESOURCE_FILTER_PLACEHOLDER = "Пошук по назві...";
    private static final String CREW_FILTER_LABEL = "Екіпаж";

    public CrewAnalyticsPage(Page page) {
        super(page);
    }

    public CrewAnalyticsPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        waitForCrewStocksDuring(() -> navigateTo(url, "Аналітика екіпажів"));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public CrewAnalyticsPage waitForLoaded() {
        page.getByText(STOCKS_TAB).first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public CrewAnalyticsPage openStocksTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(STOCKS_TAB)).click();
        waitForCrewStocksSettled();
        return this;
    }

    public CrewAnalyticsPage setIncludeFlyPointStocks(boolean include) {
        Locator checkbox = page.locator("#includeFlyPointStocks");
        checkbox.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        boolean checked = checkbox.isChecked();
        if (checked != include) {
            waitForCrewStocksDuring(() -> page.getByText(INCLUDE_FP_LABEL).click());
        } else {
            waitForCrewStocksSettled();
        }
        return this;
    }

    /** Toggle checkbox and return the /crews/stocks request URL (for includeFlyPointStocks assert). */
    public String setIncludeFlyPointStocksAndCaptureStocksUrl(boolean include) {
        Locator checkbox = page.locator("#includeFlyPointStocks");
        checkbox.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        if (checkbox.isChecked() == include) {
            return "";
        }
        var response = page.waitForResponse(
                r -> r.url().contains("/crews/stocks")
                        && !r.url().contains("stocks-aggregated")
                        && "GET".equals(r.request().method())
                        && r.status() == 200,
                () -> page.getByText(INCLUDE_FP_LABEL).click());
        waitForLoadingHidden();
        return response.url();
    }

    public boolean isIncludeFlyPointStocksChecked() {
        return page.locator("#includeFlyPointStocks").isChecked();
    }

    /** Фільтр «Ресурс» на вкладці залишків — звужує таблицю до тестового ресурсу. */
    public CrewAnalyticsPage filterByResourceName(String resourceName) {
        Locator input = page.getByPlaceholder(RESOURCE_FILTER_PLACEHOLDER).first();
        input.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        waitForCrewStocksDuring(() -> {
            input.fill("");
            input.fill(resourceName.trim());
        });
        return this;
    }

    /**
     * Обрати CREW / FLY_POINT у StorageTreeSelect «Екіпаж» (пошук по назві).
     */
    public CrewAnalyticsPage selectCrewFilterByName(String name) {
        Locator filterCard = page.getByText(CREW_FILTER_LABEL, new Page.GetByTextOptions().setExact(true))
                .locator("xpath=ancestor::div[contains(@class,'space-y-1')][1]");
        Locator trigger = filterCard.getByRole(AriaRole.COMBOBOX).first();
        trigger.click();
        Locator search = page.locator("[data-radix-popper-content-wrapper] input[placeholder='Пошук...']");
        search.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        search.fill(name.trim());
        Locator option = page.locator("[data-radix-popper-content-wrapper] button[type='button']")
                .filter(new Locator.FilterOptions().setHasText(name.trim()))
                .first();
        option.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        waitForCrewStocksDuring(option::click);
        return this;
    }

    /** Клік по лінку unattached екіпажу → /inventory?storageId=crewId */
    public CrewAnalyticsPage clickCrewInventoryLink(long crewId) {
        Locator link = crewInventoryLink(crewId);
        link.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        link.click();
        page.waitForURL(
                url -> url.contains("/inventory?storageId=" + crewId),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean hasCrewInventoryLink(long crewId) {
        return crewInventoryLink(crewId).count() > 0;
    }

    public boolean hasFlyPointDashboardLink(long flyPointId) {
        return flyPointDashboardLink(flyPointId).count() > 0;
    }

    public CrewAnalyticsPage clickFlyPointDashboardLink(long flyPointId) {
        Locator link = flyPointDashboardLink(flyPointId);
        link.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        link.click();
        page.waitForURL(
                url -> url.contains("/fly-point-dashboard") && url.contains("flyPointId=" + flyPointId),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean hasAttachedArrowRow(String crewName, String flyPointName) {
        Locator row = page.locator("tr, [role='row']")
                .filter(new Locator.FilterOptions().setHasText(crewName.trim()))
                .filter(new Locator.FilterOptions().setHasText(flyPointName.trim()));
        return row.count() > 0;
    }

    private Locator crewInventoryLink(long crewId) {
        return page.locator("a[href*='/inventory?storageId=" + crewId + "']").first();
    }

    private Locator flyPointDashboardLink(long flyPointId) {
        return page.locator("a[href*='/fly-point-dashboard?flyPointId=" + flyPointId + "']").first();
    }

    private void waitForCrewStocksDuring(Runnable action) {
        page.waitForResponse(
                response -> response.url().contains("/crews/stocks")
                        && !response.url().contains("stocks-aggregated")
                        && "GET".equals(response.request().method())
                        && response.status() == 200,
                action);
        waitForLoadingHidden();
    }

    private void waitForCrewStocksSettled() {
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
