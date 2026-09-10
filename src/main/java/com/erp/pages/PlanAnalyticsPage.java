package com.erp.pages;

import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Аналітика для плану — {@code /analytics/plan} (sidebar «Аналітика → Для плану»).
 */
@Slf4j
public class PlanAnalyticsPage extends BasePage {

    private static final String PATH = "/analytics/plan";
    public static final String EMPTY_STATE = "Оберіть ресурс або категорію, щоб побачити дані";
    public static final String RESOURCES_PLACEHOLDER = "Оберіть ресурси";
    public static final String STOCK_IN_ROOT_LABEL = "Залишок (Цукрарня)";
    public static final String ROOT_GROUP = "Цукрарня";
    public static final String OTHER_GROUP = "Інші локації";
    public static final List<String> TOTAL_LABELS = List.of(
            "Вироблено", "Відвантажено", "Використано", "Залишок усього", STOCK_IN_ROOT_LABEL);
    public static final List<String> COLUMN_HEADERS = List.of(
            "Ресурс", "Категорія", "Од.", "Вироблено", "Відвантажено", "Використано",
            "Залишок усього", STOCK_IN_ROOT_LABEL);

    private static final String PERIOD_LABEL = "Період";

    public PlanAnalyticsPage(Page page) {
        super(page);
    }

    public PlanAnalyticsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Аналітика для плану (/analytics/plan)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public PlanAnalyticsPage waitForLoaded() {
        try {
            page.waitForCondition(
                    () -> periodLabel().count() > 0 || isAccessForbidden() || isLoginFormVisible(),
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        } catch (TimeoutError e) {
            throw new AssertionError(pageDump("Сторінка /analytics/plan не завантажилась"), e);
        }
        if (isLoginFormVisible()) {
            throw new AssertionError(pageDump("Немає сесії — редірект на Keycloak login"));
        }
        if (isAccessForbidden()) {
            throw new AssertionError(pageDump("403 на /analytics/plan"));
        }
        periodLabel().first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public DateRangePickerComponent periodPicker() {
        return new DateRangePickerComponent(page, periodFilterGroup(), uiTimeoutMs());
    }

    public boolean isEmptyStateVisible() {
        return page.getByText(EMPTY_STATE).count() > 0 && page.getByText(EMPTY_STATE).first().isVisible();
    }

    public boolean hasTotals() {
        return TOTAL_LABELS.stream().allMatch(label ->
                page.getByText(label, new Page.GetByTextOptions().setExact(true)).count() > 0);
    }

    public boolean hasColumnHeaders() {
        return COLUMN_HEADERS.stream().allMatch(header ->
                page.getByRole(AriaRole.COLUMNHEADER, new Page.GetByRoleOptions().setName(header)).count() > 0
                        || page.getByText(header, new Page.GetByTextOptions().setExact(true)).count() > 0);
    }

    public int dataRowCount() {
        Locator rows = tableBodyRows();
        return rows.count();
    }

    @Step("Фільтр ресурсів: обрати перший доступний")
    public PlanAnalyticsPage selectFirstResource() {
        openResourcePicker();
        Locator option = resourceOptions().first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();
        page.keyboard().press("Escape");
        waitUntilTotalsOrEmptyTable();
        return this;
    }

    @Step("Фільтр ресурсів: обрати «{resourceName}»")
    public PlanAnalyticsPage selectResourceByName(String resourceName) {
        openResourcePicker();
        Locator search = page.getByPlaceholder("Пошук...");
        if (search.count() > 0) {
            search.last().fill(resourceName);
        }
        Locator option = resourceOptions()
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();
        page.keyboard().press("Escape");
        waitUntilTotalsOrEmptyTable();
        return this;
    }

    @Step("Розгорнути перший рядок таблиці")
    public PlanAnalyticsPage expandFirstRow() {
        Locator expand = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Розгорнути"))
                .first();
        expand.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        expand.click();
        page.getByText(ROOT_GROUP, new Page.GetByTextOptions().setExact(true))
                .or(page.getByText("Залишків немає"))
                .or(page.getByText("Завантаження..."))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.waitForCondition(
                () -> page.getByText("Завантаження...").count() == 0
                        || !page.getByText("Завантаження...").first().isVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean hasStockLocationGroups() {
        boolean root = page.getByText(ROOT_GROUP, new Page.GetByTextOptions().setExact(true)).count() > 0;
        boolean other = page.getByText(OTHER_GROUP, new Page.GetByTextOptions().setExact(true)).count() > 0;
        boolean empty = page.getByText("Залишків немає").count() > 0;
        return (root && other) || empty;
    }

    public PlanAnalyticsPage waitUntilTriggerChanges(String previousText) {
        try {
            page.waitForCondition(
                    () -> !previousText.equals(periodPicker().getTriggerText()),
                    new Page.WaitForConditionOptions().setTimeout(5_000));
        } catch (TimeoutError e) {
            log.warn("DateRangePicker trigger did not change after preset (was '{}')", previousText);
        }
        return this;
    }

    public LocalDate browserToday() {
        String iso = (String) page.evaluate("""
                () => {
                  const d = new Date();
                  const month = String(d.getMonth() + 1).padStart(2, '0');
                  const day = String(d.getDate()).padStart(2, '0');
                  return `${d.getFullYear()}-${month}-${day}`;
                }
                """);
        return LocalDate.parse(iso);
    }

    /** Last completed calendar month (tk-ui {@code monthsBack(1)}). */
    public static LocalDate lastFullMonthFrom(LocalDate today) {
        return YearMonth.from(today).minusMonths(1).atDay(1);
    }

    public static LocalDate lastFullMonthTo(LocalDate today) {
        return YearMonth.from(today).minusMonths(1).atEndOfMonth();
    }

    /** Three completed calendar months ending with the last full one (tk-ui {@code monthsBack(3)}). */
    public static LocalDate threeFullMonthsFrom(LocalDate today) {
        return YearMonth.from(today).minusMonths(3).atDay(1);
    }

    private void openResourcePicker() {
        Locator trigger = page.getByRole(AriaRole.COMBOBOX)
                .filter(new Locator.FilterOptions().setHasText(RESOURCES_PLACEHOLDER))
                .first();
        trigger.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        trigger.click();
        resourceOptions().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
    }

    private Locator resourceOptions() {
        return page.locator("[cmdk-item], [data-slot='command-item']");
    }

    private void waitUntilTotalsOrEmptyTable() {
        try {
            page.waitForCondition(
                    this::hasTotals,
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        } catch (TimeoutError e) {
            log.warn("Totals did not appear after resource selection: {}", pageDump("no totals"));
        }
    }

    private Locator tableBodyRows() {
        return page.locator("table tbody tr, [role='table'] [role='row']")
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Розгорнути"))
                                .or(page.getByRole(AriaRole.BUTTON,
                                        new Page.GetByRoleOptions().setName("Згорнути")))));
    }

    private Locator periodLabel() {
        return page.getByText(PERIOD_LABEL, new Page.GetByTextOptions().setExact(true));
    }

    private Locator periodFilterGroup() {
        return periodLabel().locator("xpath=ancestor::div[contains(@class,'flex-col')][1]");
    }

    private boolean isAccessForbidden() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("403")).count() > 0
                || page.getByText(AccessForbiddenPage.FORBIDDEN_MESSAGE).count() > 0;
    }

    private boolean isLoginFormVisible() {
        return page.locator("#username").count() > 0 && page.locator("#kc-login").count() > 0;
    }

    private String pageDump(String reason) {
        return "%s. url=%s empty=%s totals=%s".formatted(
                reason, page.url(), isEmptyStateVisible(), hasTotals());
    }
}
