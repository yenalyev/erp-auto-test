package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Аналітика підрозділів.
 * URL: /analytics/unit-analytics
 */
@Slf4j
public class UnitAnalyticsPage extends BasePage {

    private static final String PATH = "/analytics/unit-analytics";
    public static final String TAB_TURNOVER = "Обороти";
    public static final String TAB_STOCK = "Залишки";
    public static final String TAB_PERIOD = "За період";
    public static final String TAB_BY_MONTH = "По місяцях";
    public static final String TAB_BY_RESOURCE = "По ресурсах";

    private static final String PERIOD_HEADING = "Використано та отримано";
    private static final String MONTH_HEADER = "Рік/Місяць";
    private static final String RESOURCE_SEARCH_PLACEHOLDER = "Пошук за назвою ресурсу";
    private static final String LOADING_TEXT = "Завантаження...";
    private static final String EMPTY_DATA_TEXT = "Немає даних для відображення";
    private static final String EMPTY_CATEGORY_TEXT = "Оберіть хоча б одну категорію у фільтрах вище";
    private static final String TABLE_ERROR_TEXT = "Не вдалося завантажити дані";
    private static final String TOAST_ERROR_FRAGMENT = "Не вдалося завантажити аналітику";
    private static final String TOTALS_ROW = "Разом";
    private static final String UNIT_COLUMN = "Підрозділ";

    public UnitAnalyticsPage(Page page) {
        super(page);
    }

    public UnitAnalyticsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Аналітика підрозділів (/analytics/unit-analytics)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public UnitAnalyticsPage waitForLoaded() {
        try {
            page.waitForCondition(
                    () -> isTurnoverTabVisible() || isAccessForbidden(),
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        } catch (PlaywrightException e) {
            throw new AssertionError(pageDump("Вкладка «Обороти» не з'явилась"), e);
        }
        if (isAccessForbidden()) {
            throw new AssertionError(pageDump(
                    "403 на /analytics/unit-analytics: RouteGuard вимагає unit-analytics::view, "
                            + "бекенд API — unit-analytics::read"));
        }
        // Inner tabs («За період» / «По місяцях» / «По ресурсах») render only inside
        // TabsContent value="turnover". Ensure the outer tab is selected first.
        if (!periodTab().isVisible()) {
            turnoverTab().click();
        }
        periodTab().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        waitUntil(() -> isPeriodContentReady() || hasLoadError());
        return this;
    }

    public UnitAnalyticsPage openTurnoverTab() {
        clickTab(TAB_TURNOVER);
        periodTab().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitAnalyticsPage openPeriodTab() {
        clickTab(TAB_PERIOD);
        waitUntil(() -> isPeriodContentReady() || hasLoadError());
        return this;
    }

    public UnitAnalyticsPage openByMonthTab() {
        clickTab(TAB_BY_MONTH);
        waitUntil(() -> isByMonthContentReady() || hasLoadError());
        return this;
    }

    public UnitAnalyticsPage openByResourceTab() {
        clickTab(TAB_BY_RESOURCE);
        waitUntil(() -> isByResourceContentReady() || hasLoadError());
        return this;
    }

    public boolean isSidebarLinkVisible() {
        AppSidebarPage sidebar = new AppSidebarPage(page);
        return sidebar.isNavItemVisible(AppSidebarPage.NAV_UNIT_ANALYTICS)
                || sidebar.isPageTabVisible(AppSidebarPage.NAV_UNIT_ANALYTICS);
    }

    public boolean isTurnoverTabVisible() {
        return turnoverTab().isVisible();
    }

    public boolean isPeriodTabVisible() {
        return periodTab().isVisible();
    }

    public boolean isByMonthTabVisible() {
        return byMonthTab().isVisible();
    }

    public boolean isByResourceTabVisible() {
        return byResourceTab().isVisible();
    }

    public boolean isPeriodContentReady() {
        if (!page.getByText(PERIOD_HEADING).first().isVisible()) {
            return false;
        }
        return hasPeriodTable() || hasEmptyState();
    }

    public boolean isByMonthContentReady() {
        return hasMonthTable() || hasEmptyState();
    }

    public boolean isByResourceContentReady() {
        return page.getByPlaceholder(RESOURCE_SEARCH_PLACEHOLDER).first().isVisible()
                && !isLoadingVisible()
                && !hasTableLoadError();
    }

    public boolean hasLoadError() {
        return hasTableLoadError() || hasErrorToast();
    }

    /** Імена з колонки «Підрозділ» вкладки «За період» (без рядка «Разом»). */
    public List<String> collectPeriodUnitNames() {
        if (!hasPeriodTable()) {
            return List.of();
        }
        Locator heading = page.getByText(PERIOD_HEADING).first();
        Locator table = heading.locator("xpath=ancestor::div[contains(@class,'p-4')][1]").locator("table");
        return collectUnitNames(table, false);
    }

    /** Імена з колонки «Підрозділ» вкладки «По місяцях» (ураховує rowspan місяця). */
    public List<String> collectMonthlyUnitNames() {
        if (!hasMonthTable()) {
            return List.of();
        }
        Locator table = page.getByText(MONTH_HEADER, new Page.GetByTextOptions().setExact(true))
                .first()
                .locator("xpath=ancestor::table[1]");
        return collectUnitNames(table, true);
    }

    /** Імена з колонки «Підрозділ» вкладки «По ресурсах». */
    public List<String> collectByResourceUnitNames() {
        Locator table = page.locator("table").filter(
                new Locator.FilterOptions().setHasText(UNIT_COLUMN));
        if (table.count() == 0) {
            return List.of();
        }
        return collectUnitNames(table.first(), false);
    }

    private List<String> collectUnitNames(Locator table, boolean monthRowspan) {
        Locator rows = table.locator("tbody tr");
        Set<String> names = new LinkedHashSet<>();
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            Locator cells = rows.nth(i).locator("td");
            if (cells.count() == 0) {
                continue;
            }
            String name;
            if (monthRowspan && cells.first().getAttribute("rowspan") != null) {
                name = cells.count() > 1 ? normalize(cells.nth(1).innerText()) : "";
            } else {
                name = normalize(cells.first().innerText());
            }
            if (name.isBlank() || TOTALS_ROW.equals(name) || UNIT_COLUMN.equals(name)) {
                continue;
            }
            names.add(name);
        }
        return new ArrayList<>(names);
    }

    private static String normalize(String value) {
        return value != null ? value.trim().replaceAll("\\s+", " ") : "";
    }

    private boolean hasPeriodTable() {
        Locator heading = page.getByText(PERIOD_HEADING).first();
        if (heading.count() == 0) {
            return false;
        }
        Locator card = heading.locator("xpath=ancestor::div[contains(@class,'p-4')][1]");
        return card.getByText("Підрозділ", new Locator.GetByTextOptions().setExact(true)).count() > 0;
    }

    private boolean hasMonthTable() {
        return page.getByText(MONTH_HEADER, new Page.GetByTextOptions().setExact(true)).count() > 0
                && page.getByText(MONTH_HEADER, new Page.GetByTextOptions().setExact(true)).first().isVisible();
    }

    private boolean hasEmptyState() {
        Locator emptyData = page.getByText(EMPTY_DATA_TEXT);
        Locator emptyCategory = page.getByText(EMPTY_CATEGORY_TEXT);
        return (emptyData.count() > 0 && emptyData.first().isVisible())
                || (emptyCategory.count() > 0 && emptyCategory.first().isVisible());
    }

    private boolean hasTableLoadError() {
        Locator error = page.getByText(TABLE_ERROR_TEXT);
        return error.count() > 0 && error.first().isVisible();
    }

    private boolean hasErrorToast() {
        Locator toast = page.locator("[data-sonner-toast], [role='status']")
                .filter(new Locator.FilterOptions().setHasText(TOAST_ERROR_FRAGMENT));
        return toast.count() > 0 && toast.first().isVisible();
    }

    private void clickTab(String name) {
        tab(name).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        try {
            page.waitForResponse(
                    response -> response.url().contains("unit-analytics")
                            && "GET".equals(response.request().method()),
                    new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                    () -> tab(name).click());
        } catch (Exception e) {
            log.warn("unit-analytics response wait on tab «{}»: {}", name, e.getMessage());
        }
    }

    private Locator tab(String name) {
        return page.locator("[data-slot='tabs-trigger']")
                .filter(new Locator.FilterOptions().setHasText(name))
                .first();
    }

    private boolean isAccessForbidden() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("403")).count() > 0
                || page.getByText("У вас немає прав для перегляду цієї сторінки").count() > 0;
    }

    private String pageDump(String reason) {
        List<String> tabs = page.locator("[data-slot='tabs-trigger']").allTextContents();
        return "%s. url=%s heading='%s' tabs=%s".formatted(
                reason,
                page.url(),
                page.locator("h1").first().count() > 0 ? page.locator("h1").first().innerText().trim() : "",
                tabs);
    }

    private Locator turnoverTab() {
        return tab(TAB_TURNOVER);
    }

    private Locator periodTab() {
        return tab(TAB_PERIOD);
    }

    private Locator byMonthTab() {
        return tab(TAB_BY_MONTH);
    }

    private Locator byResourceTab() {
        return tab(TAB_BY_RESOURCE);
    }

    private boolean isLoadingVisible() {
        Locator loading = page.getByText(LOADING_TEXT);
        return loading.count() > 0 && loading.first().isVisible();
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) {
        page.waitForCondition(condition::getAsBoolean,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }
}
