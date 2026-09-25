package com.erp.pages;

import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Аналітика виробництва — {@code /analytics/production}.
 * DateRangePicker sits inside PeriodStepper (prev/next chevrons around the trigger).
 */
@Slf4j
public class ProductionAnalyticsPage extends BasePage {

    private static final String PATH = "/analytics/production";
    public static final String TAB_DAILY = "Поденне";
    public static final String TAB_STATISTICS = "Статистика";
    public static final String TAB_EXPENSES = "Витрати";
    public static final String TAB_PRODUCTIVITY = "Продуктивність";
    public static final String TAB_BY_CATEGORIES = "За категоріями";
    public static final String TAB_BY_TAGS = "За тегами";
    public static final String TAB_BY_PRODUCTS = "За виробами";
    public static final String TAB_ASSEMBLY = "Виготовлення";
    public static final String TAB_DISASSEMBLY = "Розбір";
    public static final String TAB_NON_SERIES = "Несерійне виробництво";
    private static final String PERIOD_LABEL = "Період";
    private static final String EMPTY_STATE = "Немає даних для відображення";

    public ProductionAnalyticsPage(Page page) {
        super(page);
    }

    public ProductionAnalyticsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Аналітика виробництва (/analytics/production)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public ProductionAnalyticsPage waitForLoaded() {
        try {
            page.waitForCondition(
                    () -> isDailyTabVisible() || isAccessForbidden() || isLoginFormVisible(),
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        } catch (TimeoutError e) {
            throw new AssertionError(pageDump("Сторінка /analytics/production не завантажилась"), e);
        }
        if (isLoginFormVisible()) {
            throw new AssertionError(pageDump("Немає сесії — редірект на Keycloak login"));
        }
        if (isAccessForbidden()) {
            throw new AssertionError(pageDump("403 на /analytics/production"));
        }
        periodLabel().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isDailyTabVisible() {
        return dailyTab().count() > 0 && dailyTab().first().isVisible();
    }

    public boolean isStatisticsTabVisible() {
        return tab(TAB_STATISTICS).count() > 0 && tab(TAB_STATISTICS).first().isVisible();
    }

    public boolean isTabSelected(String name) {
        Locator selectedTab = tab(name);
        return "active".equals(selectedTab.getAttribute("data-state"))
                || "true".equals(selectedTab.getAttribute("aria-selected"));
    }

    public ProductionAnalyticsPage openDailyTab() {
        tab(TAB_DAILY).click();
        periodLabel().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public ProductionAnalyticsPage openStatisticsTab() {
        tab(TAB_STATISTICS).click();
        periodLabel().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public ProductionAnalyticsPage openNonSeriesExpenses() {
        openStatisticsTab();
        tab(TAB_EXPENSES).click();
        tab(TAB_NON_SERIES).click();
        return this;
    }

    public ProductionAnalyticsPage openStatisticsProductionType(String productionType) {
        tab(productionType).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        tab(productionType).click();
        return this;
    }

    public ProductionAnalyticsPage selectDailyFilterOption(String label, String optionName) {
        Locator trigger = filterGroup(label).getByRole(AriaRole.COMBOBOX).first();
        trigger.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        trigger.click();

        Locator search = page.getByPlaceholder("Пошук...");
        if (search.count() > 0 && search.last().isVisible()) {
            search.last().fill(optionName);
        }
        Locator option = page.getByRole(AriaRole.OPTION,
                        new Page.GetByRoleOptions().setName(optionName).setExact(true))
                .or(page.locator("[cmdk-item], [data-slot='command-item']")
                        .filter(new Locator.FilterOptions().setHasText(optionName)))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();
        page.keyboard().press("Escape");
        return this;
    }

    public String dailyMetricValueText(String label) {
        Locator labelNode = page.getByText(label).first();
        labelNode.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        String[] lines = labelNode.locator("xpath=parent::*").innerText().split("\\R");
        for (String line : lines) {
            String value = line.trim().replace('\u00a0', ' ');
            if (value.matches("-?\\d+(?:[.,]\\d+)?")) {
                return value;
            }
        }
        throw new AssertionError("Numeric value not found for daily metric «" + label + "»");
    }

    public boolean hasDailyFilters() {
        return labelsVisible("Локації", "Категорії", "Вироби");
    }

    public boolean isDailyContentReady() {
        Locator empty = page.getByText(EMPTY_STATE, new Page.GetByTextOptions().setExact(true));
        Locator chart = page.locator(".recharts-responsive-container, svg.recharts-surface, canvas");
        return firstVisible(empty) || firstVisible(chart);
    }

    public boolean hasLoadError() {
        String body = page.locator("body").innerText();
        return body.contains("Не вдалося завантажити")
                || body.contains("Помилка завантаження")
                || body.contains("Щось пішло не так");
    }

    public ProductionAnalyticsPage waitForExpenseResource(String resourceName) {
        page.getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public String expenseResourceRowText(String resourceName) {
        return page.getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .first()
                .innerText()
                .trim();
    }

    public String statisticValueText(String label) {
        Locator labelNode = page.getByText(label, new Page.GetByTextOptions().setExact(true)).first();
        labelNode.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return labelNode.locator("xpath=parent::*").innerText().trim();
    }

    /** Captures the XLSX body even when the frontend uses fetch/blob before emitting a download. */
    public ExportDownloadResult exportStatisticsToExcel() {
        List<Download> downloads = Collections.synchronizedList(new ArrayList<>());
        Consumer<Download> listener = downloads::add;
        page.onDownload(listener);
        try {
            Response response = page.waitForResponse(
                    ProductionAnalyticsPage::isSpreadsheetResponse,
                    new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                    () -> page.getByRole(AriaRole.BUTTON,
                            new Page.GetByRoleOptions().setName("Експорт в Excel").setExact(true)).click());
            try {
                page.waitForCondition(() -> !downloads.isEmpty(),
                        new Page.WaitForConditionOptions().setTimeout(1_500));
            } catch (RuntimeException ignored) {
                // Axios/fetch blob responses do not always surface as a Chromium download event.
            }
            if (!downloads.isEmpty()) {
                Download download = downloads.getFirst();
                Path path = download.path();
                return new ExportDownloadResult(download.suggestedFilename(), path.toFile().length(), path);
            }
            byte[] body = response.body();
            Path path = Files.createTempFile("erp-production-analytics-", ".xlsx");
            Files.write(path, body);
            return new ExportDownloadResult("production-analytics.xlsx", body.length, path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist production analytics export", e);
        } finally {
            page.offDownload(listener);
        }
    }

    public DateRangePickerComponent periodPicker() {
        return new DateRangePickerComponent(page, periodFilterGroup(), uiTimeoutMs());
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

    public ProductionAnalyticsPage waitUntilTriggerChanges(String previousText) {
        try {
            page.waitForCondition(
                    () -> !previousText.equals(periodPicker().getTriggerText()),
                    new Page.WaitForConditionOptions().setTimeout(5_000));
        } catch (TimeoutError e) {
            log.warn("DateRangePicker trigger did not change after preset (was '{}')", previousText);
        }
        return this;
    }

    private Locator dailyTab() {
        return tab(TAB_DAILY);
    }

    private Locator tab(String name) {
        return page.locator("[data-slot='tabs-trigger']")
                .filter(new Locator.FilterOptions().setHasText(name))
                .first();
    }

    private Locator periodLabel() {
        return page.getByText(PERIOD_LABEL, new Page.GetByTextOptions().setExact(true));
    }

    private Locator periodFilterGroup() {
        return periodLabel().locator("xpath=ancestor::div[1]");
    }

    private Locator filterGroup(String label) {
        return page.getByText(label, new Page.GetByTextOptions().setExact(true))
                .first()
                .locator("xpath=ancestor::div[contains(@class,'flex-col')][1]");
    }

    private boolean isAccessForbidden() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("403")).count() > 0
                || page.getByText(AccessForbiddenPage.FORBIDDEN_MESSAGE).count() > 0;
    }

    private boolean isLoginFormVisible() {
        return page.locator("#username").count() > 0 && page.locator("#kc-login").count() > 0;
    }

    private boolean labelsVisible(String... labels) {
        for (String label : labels) {
            Locator locator = page.getByText(label, new Page.GetByTextOptions().setExact(true));
            if (locator.count() == 0 || !locator.first().isVisible()) {
                return false;
            }
        }
        return true;
    }

    private boolean tabsVisible(String... labels) {
        for (String label : labels) {
            Locator locator = tab(label);
            if (locator.count() == 0 || !locator.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static boolean firstVisible(Locator locator) {
        return locator.count() > 0 && locator.first().isVisible();
    }

    private static boolean isSpreadsheetResponse(Response response) {
        if (response.status() != 200) {
            return false;
        }
        String contentType = response.headerValue("content-type");
        String disposition = response.headerValue("content-disposition");
        String type = contentType != null ? contentType.toLowerCase() : "";
        String filename = disposition != null ? disposition.toLowerCase() : "";
        return type.contains("spreadsheet")
                || type.contains("excel")
                || type.contains("octet-stream")
                || filename.contains(".xlsx");
    }

    private String pageDump(String reason) {
        List<String> tabs = page.locator("[data-slot='tabs-trigger']").allTextContents();
        return "%s. url=%s heading='%s' tabs=%s".formatted(
                reason,
                page.url(),
                page.locator("h1").first().count() > 0 ? page.locator("h1").first().innerText().trim() : "",
                tabs);
    }

    public record ExportDownloadResult(String suggestedFilename, long sizeBytes, Path path) {}
}
