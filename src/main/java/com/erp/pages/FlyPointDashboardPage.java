package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Дашборд точок взлету — deep-link до інвентаризації FLY_POINT і вкладка «Залишки».
 * URL: /fly-point-dashboard
 */
@Slf4j
public class FlyPointDashboardPage extends BasePage {

    private static final String PATH = "/fly-point-dashboard";
    private static final String STOCKS_TAB = "Залишки";
    private static final String RELOCATIONS_TAB = "Надходження";
    private static final String WRITE_OFFS_TAB = "Використання";
    private static final String TURN_OVER_TAB = "Зведені обороти";
    private static final String RESOURCE_FILTER_PLACEHOLDER = "Пошук по назві...";
    private static final String LOADING_TEXT = "Завантаження...";

    public FlyPointDashboardPage(Page page) {
        super(page);
    }

    public FlyPointDashboardPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        waitForDashboardApis(true, () -> {
            navigateTo(url, "Точки взлету");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        });
        return waitForLoaded();
    }

    public FlyPointDashboardPage openWithFlyPointId(long flyPointId) {
        String url = ConfigProvider.getBaseUrl() + PATH + "?flyPointId=" + flyPointId;
        waitForDashboardApis(true, () -> {
            navigateTo(url, "Точки взлету (flyPointId)");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        });
        return waitForLoaded();
    }

    /**
     * Sidebar «Екіпажі» → PageTab «Точки взлету». Не чекає short-stats дефолтної локації
     * (у ADMIN це часто великий дерево і саме воно тримає спінер). Далі треба
     * {@link #selectCrewLocation(String, long, boolean)}.
     */
    public FlyPointDashboardPage openViaSidebar() {
        ensureAppShell();
        new AppSidebarPage(page)
                .waitForSidebarLoaded()
                .navigateToGroupedPage(AppSidebarPage.GROUP_CREW, AppSidebarPage.TAB_FLY_POINTS);
        waitForStocksTabVisible();
        return this;
    }

    public FlyPointDashboardPage waitForLoaded() {
        waitForStocksTabVisible();
        assertLoadingHidden();
        return this;
    }

    public FlyPointDashboardPage openStocksTab() {
        return openSubTab(STOCKS_TAB);
    }

    public FlyPointDashboardPage openRelocationsTab() {
        return openSubTab(RELOCATIONS_TAB);
    }

    public FlyPointDashboardPage openWriteOffsTab() {
        return openSubTab(WRITE_OFFS_TAB);
    }

    public FlyPointDashboardPage openTurnOverTab() {
        return openSubTab(TURN_OVER_TAB);
    }

    public FlyPointDashboardPage filterByResourceName(String resourceName) {
        Locator input = page.locator("input[placeholder='" + RESOURCE_FILTER_PLACEHOLDER + "']:visible")
                .first();
        input.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        waitForStocksDuring(() -> {
            input.fill("");
            input.fill(resourceName.trim());
        });
        return this;
    }

    /**
     * Селектор «Локація:» (ADMIN має кілька CREWS locations). Чекає GET з {@code parentId}
     * обраного UNIT — не перший 403 без parentId.
     */
    public FlyPointDashboardPage selectCrewLocation(String locationName, long locationId, boolean waitShortStats) {
        waitForLocationPicker();
        Locator trigger = locationRow().getByRole(AriaRole.COMBOBOX).first();
        String current = trigger.innerText();
        if (current != null && current.contains(locationName.trim())) {
            if (waitShortStats) {
                assertLoadingHidden();
            }
            return this;
        }
        waitForDashboardApis(locationId, waitShortStats, () -> {
            trigger.click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(locationName.trim()))
                    .first()
                    .click();
        });
        if (waitShortStats) {
            assertLoadingHidden();
        } else {
            waitForStocksTabVisible();
        }
        return this;
    }

    public FlyPointDashboardPage expandAllStockCategories() {
        Locator toggle = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ресурс"));
        if (toggle.count() > 0 && toggle.first().isEnabled()) {
            toggle.first().click();
        }
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

    public boolean isPageLoadingVisible() {
        Locator loading = page.getByText(LOADING_TEXT);
        return loading.count() > 0 && loading.first().isVisible();
    }

    public boolean hasStocksTable() {
        Locator table = stocksTable();
        return table.count() > 0
                && table.first().isVisible()
                && table.getByText("Підрозділ").count() > 0
                && table.getByText("Точка взлету").count() > 0
                && table.getByText("Ресурс").count() > 0
                && table.getByText("Кількість").count() > 0
                && table.getByText("Оновлено").count() > 0;
    }

    public boolean stocksTableContains(String text) {
        Locator table = stocksTable();
        if (table.count() == 0) {
            return page.getByText(text).count() > 0;
        }
        return table.getByText(text).count() > 0;
    }

    public boolean areStockFiltersUsable() {
        Locator resourceFilter = page.locator("input[placeholder='" + RESOURCE_FILTER_PLACEHOLDER + "']:visible")
                .first();
        Locator flyPointFilter = page.getByText("Точка взлету", new Page.GetByTextOptions().setExact(true));
        Locator categoryFilter = page.getByText("Категорія", new Page.GetByTextOptions().setExact(true));
        return resourceFilter.count() > 0
                && resourceFilter.isEnabled()
                && flyPointFilter.count() > 0
                && flyPointFilter.first().isVisible()
                && categoryFilter.count() > 0
                && categoryFilter.first().isVisible();
    }

    public boolean isStocksContentSettled() {
        if (hasStocksTable()) {
            return true;
        }
        Locator empty = page.getByText("Записів не знайдено");
        return empty.count() > 0 && empty.last().isVisible();
    }

    public FlyPointDashboardPage assertLoadingHidden() {
        page.waitForCondition(
                () -> !isPageLoadingVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    private FlyPointDashboardPage openSubTab(String tabName) {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabName)).click();
        waitForLoadingHidden();
        return this;
    }

    private void waitForStocksTabVisible() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(STOCKS_TAB))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
    }

    private void waitForLocationPicker() {
        page.waitForCondition(
                () -> locationRow().count() > 0 && locationRow().first().isVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator locationRow() {
        return page.locator("div.flex-wrap")
                .filter(new Locator.FilterOptions().setHasText("Локація:"))
                .first();
    }

    private void ensureAppShell() {
        AppSidebarPage sidebar = new AppSidebarPage(page);
        if (!sidebar.isSidebarVisible()) {
            navigateTo(ConfigProvider.getBaseUrl(), "Home");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            sidebar.waitForSidebarLoaded();
        }
    }

    private void waitForDashboardApis(boolean includeShortStats, Runnable action) {
        waitForDashboardApis(null, includeShortStats, action);
    }

    private void waitForDashboardApis(Long parentId, boolean includeShortStats, Runnable action) {
        Page.WaitForResponseOptions timeout = new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs());
        if (includeShortStats) {
            page.waitForResponse(r -> isFlyPointStocksGet(r, parentId), timeout, () ->
                    page.waitForResponse(r -> isFlyPointShortStatsGet(r, parentId),
                            new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                            action));
        } else {
            page.waitForResponse(r -> isFlyPointStocksGet(r, parentId), timeout, action);
        }
    }

    private void waitForStocksDuring(Runnable action) {
        page.waitForResponse(
                r -> isFlyPointStocksGet(r, null),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                action);
        waitForLoadingHidden();
    }

    private boolean isFlyPointStocksGet(Response response, Long parentId) {
        return isGetWithParent(response, "/fly-points/stocks", parentId);
    }

    private boolean isFlyPointShortStatsGet(Response response, Long parentId) {
        return isGetWithParent(response, "/fly-points/short-stats", parentId);
    }

    private static boolean isGetWithParent(Response response, String pathFragment, Long parentId) {
        if (!"GET".equals(response.request().method())) {
            return false;
        }
        String url = response.url();
        if (!url.contains(pathFragment) || !hasQueryParam(url, "parentId")) {
            return false;
        }
        return parentId == null || hasQueryParam(url, "parentId", parentId);
    }

    private static boolean hasQueryParam(String url, String name) {
        return url.contains(name + "=");
    }

    private static boolean hasQueryParam(String url, String name, long value) {
        String token = name + "=" + value;
        return url.contains(token + "&") || url.endsWith(token);
    }

    private Locator stocksTable() {
        return page.locator("table")
                .filter(new Locator.FilterOptions().setHasText("Підрозділ"))
                .first();
    }

    private Locator flyPointInventoryLink(long flyPointId) {
        return page.locator("a[href*='/inventory?storageId=" + flyPointId + "']").first();
    }

    private void waitForLoadingHidden() {
        Locator loading = page.getByText(LOADING_TEXT).first();
        try {
            loading.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        } catch (RuntimeException ignored) {
            // already hidden / not present — kept for callers that must not fail on a stuck overlay
        }
    }
}
