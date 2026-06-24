package com.erp.pages;

import com.erp.models.common.ProductionJournalRow;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page Object for the Production Journal page.
 * URL: /production
 */
@Slf4j
public class ProductionPage extends BasePage {

    private static final String PATH = "/production";

    private static final String MANUFACTURING_BUTTON_TEXT = "Виготовлення";
    private static final String DISASSEMBLE_BUTTON_TEXT = "Розбір";
    private static final String PRODUCT_LABEL_TEXT = "Продукт";
    private static final String CATEGORY_LABEL_TEXT = "Категорія";
    private static final String WORK_TYPE_LABEL_TEXT = "Тип робіт";
    private static final String PRODUCT_INPUT_SELECTOR = "input[placeholder='Пошук...']";
    private static final String DATE_INPUT_SELECTOR = "input[type='date']";
    private static final String CLEAR_BUTTON_TEXT = "Очистити";
    private static final String EMPTY_STATE_TEXT = "Нічого не знайдено";
    private static final String LOADING_TEXT = "Завантаження...";
    private static final String PRODUCTION_TABLE_WRAPPER_SELECTOR =
            "div.rounded-xl.border.border-gray-200.bg-white";
    private static final String PAGE_SIZE_STORAGE_KEY = "pageSize_production-list";
    private static final DateTimeFormatter UI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter UI_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})(?:\\s+(\\d{2}:\\d{2}))?");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*од");

    public ProductionPage(Page page) {
        super(page);
    }

    public ProductionPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Production page: {}", url);
        navigateTo(url, "Журнал виробництва (/production)");
        return waitForLoaded();
    }

    /** Wait until the production journal is rendered (SPA load + key UI elements). */
    public ProductionPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached within timeout — proceeding: {}", e.getMessage());
        }

        Locator pageReady = page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(MANUFACTURING_BUTTON_TEXT))
                .or(page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName(MANUFACTURING_BUTTON_TEXT)))
                .or(page.getByLabel(PRODUCT_LABEL_TEXT))
                .or(page.locator(PRODUCT_INPUT_SELECTOR))
                .or(page.locator(PRODUCTION_TABLE_WRAPPER_SELECTOR))
                .first();

        pageReady.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));

        waitForJournalDataSettled();
        log.info("Production page loaded — url: {}", page.url());
        return this;
    }

    /** Wait until the production journal finishes loading (rows or empty state rendered). */
    public ProductionPage waitForJournalDataSettled() {
        int timeoutMs = uiTimeoutMs();
        page.waitForCondition(() -> {
            Locator wrapper = productionTableWrapper();
            if (wrapper.count() == 0 || !wrapper.isVisible()) {
                return false;
            }
            Locator loading = wrapper.getByText(LOADING_TEXT);
            if (loading.count() > 0 && loading.isVisible()) {
                return false;
            }
            return getProductionRecordCount() > 0 || isEmptyStateVisible();
        }, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        return this;
    }

    public boolean isManufacturingButtonVisible() {
        return isNamedActionVisible(MANUFACTURING_BUTTON_TEXT);
    }

    public boolean isDisassembleButtonVisible() {
        return isNamedActionVisible(DISASSEMBLE_BUTTON_TEXT);
    }

    /** True when any key production journal element is visible. */
    public boolean isLoaded() {
        return isManufacturingButtonVisible()
                || isProductFilterVisible()
                || isProductionTableVisible();
    }

    public boolean isProductFilterVisible() {
        Locator byLabel = page.getByLabel(PRODUCT_LABEL_TEXT);
        if (byLabel.count() > 0 && byLabel.first().isVisible()) {
            return true;
        }
        Locator input = page.locator(PRODUCT_INPUT_SELECTOR);
        return input.count() > 0 && input.first().isVisible();
    }

    public boolean isCategoryFilterVisible() {
        return isFilterLabelVisible(CATEGORY_LABEL_TEXT);
    }

    public boolean isWorkTypeFilterVisible() {
        return isFilterLabelVisible(WORK_TYPE_LABEL_TEXT);
    }

    public boolean isDateFromVisible() {
        return page.locator(DATE_INPUT_SELECTOR).nth(0).isVisible();
    }

    public boolean isDateToVisible() {
        return page.locator(DATE_INPUT_SELECTOR).nth(1).isVisible();
    }

    public boolean isClearButtonVisible() {
        Locator button = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(CLEAR_BUTTON_TEXT));
        return button.count() > 0 && button.first().isVisible();
    }

    public boolean isProductionTableVisible() {
        Locator wrapper = productionTableWrapper();
        return wrapper.count() > 0 && wrapper.isVisible();
    }

    /** True when the journal shows the empty-state message instead of data rows. */
    public boolean isEmptyStateVisible() {
        Locator emptyState = productionTableWrapper().getByText(EMPTY_STATE_TEXT);
        return emptyState.count() > 0 && emptyState.isVisible();
    }

    /** True when a destructive alert is shown after a failed journal API call. */
    public boolean isJournalLoadErrorVisible() {
        Locator alert = page.locator("[role='alert'].border-destructive, [role='alert'][class*='destructive']");
        return alert.count() > 0 && alert.first().isVisible();
    }

    public int getProductionRecordCount() {
        return productionTableWrapper().locator("tbody tr").count();
    }

    /** True when at least one production record row is rendered and empty state is hidden. */
    public boolean hasProductionRecords() {
        return getProductionRecordCount() > 0 && !isEmptyStateVisible();
    }

    /** Parsed journal rows from the current table page (top to bottom = API sort order). */
    public List<ProductionJournalRow> getDisplayedJournalRows() {
        Locator rows = productionTableWrapper().locator("tbody tr");
        int count = rows.count();
        List<ProductionJournalRow> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(parseJournalRow(rows.nth(i)));
        }
        return result;
    }

    /**
     * Page size selected in the journal pagination control.
     * Falls back to {@link com.erp.models.query.ProductionJournalQuery#DEFAULT_UI_PAGE_SIZE}.
     */
    public int getSelectedPageSize() {
        Locator pageSizeTrigger = page.locator("span")
                .filter(new Locator.FilterOptions().setHasText("Показувати по:"))
                .locator("xpath=following-sibling::*[1]//button")
                .first();
        if (pageSizeTrigger.count() == 0 || !pageSizeTrigger.isVisible()) {
            return com.erp.models.query.ProductionJournalQuery.DEFAULT_UI_PAGE_SIZE;
        }
        String value = pageSizeTrigger.textContent();
        if (value == null || value.isBlank()) {
            return com.erp.models.query.ProductionJournalQuery.DEFAULT_UI_PAGE_SIZE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Cannot parse page size from UI value '{}'", value);
            return com.erp.models.query.ProductionJournalQuery.DEFAULT_UI_PAGE_SIZE;
        }
    }

    public static String pageSizeStorageKey() {
        return PAGE_SIZE_STORAGE_KEY;
    }

    /** Current value of the «Продукт» search input. */
    public String getProductFilterValue() {
        return productFilterInput().inputValue();
    }

    /** ISO date (yyyy-MM-dd) from the «З» date picker, or empty when unset. */
    public String getDateFromValue() {
        return dateFromInput().inputValue();
    }

    /** ISO date (yyyy-MM-dd) from the «По» date picker, or empty when unset. */
    public String getDateToValue() {
        return dateToInput().inputValue();
    }

    /** Visible label on the category dropdown trigger (selected category or placeholder). */
    public String getSelectedCategoryLabel() {
        Locator trigger = categoryTrigger();
        if (trigger.count() == 0) {
            return "";
        }
        String text = trigger.innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    public ProductionPage filterByProduct(String productTerm) {
        productFilterInput().fill(productTerm);
        page.waitForTimeout(400);
        waitForJournalDataSettled();
        return this;
    }

    public ProductionPage filterByDateFrom(LocalDate date) {
        runJournalFilterAction(() -> dateFromInput().fill(date.toString()));
        return this;
    }

    public ProductionPage filterByDateTo(LocalDate date) {
        runJournalFilterAction(() -> dateToInput().fill(date.toString()));
        return this;
    }

    public ProductionPage filterByCategory(String categoryName) {
        runJournalFilterAction(() -> {
            categoryTrigger().click();
            page.locator("[data-radix-popper-content-wrapper] button")
                    .filter(new Locator.FilterOptions().setHasText(categoryName))
                    .first()
                    .click();
        });
        return this;
    }

    public ProductionPage applyFilters(ProductionJournalFilterState filters) {
        if (filters.productTerm() != null) {
            filterByProduct(filters.productTerm());
        }
        if (filters.categoryName() != null) {
            filterByCategory(filters.categoryName());
        }
        if (filters.startDate() != null) {
            filterByDateFrom(filters.startDate());
        }
        if (filters.endDate() != null) {
            filterByDateTo(filters.endDate());
        }
        return this;
    }

    public ProductionPage clearFilters() {
        runJournalFilterAction(() -> page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(CLEAR_BUTTON_TEXT)).click());
        return this;
    }

    private void runJournalFilterAction(Runnable action) {
        page.waitForResponse(
                response -> response.url().contains("/productions")
                        && "GET".equals(response.request().method()),
                action);
        waitForJournalDataSettled();
    }

    private Locator productFilterInput() {
        Locator byLabel = page.getByLabel(PRODUCT_LABEL_TEXT);
        if (byLabel.count() > 0) {
            return byLabel.first();
        }
        return page.locator(PRODUCT_INPUT_SELECTOR).first();
    }

    private Locator dateFromInput() {
        return page.locator(DATE_INPUT_SELECTOR).nth(0);
    }

    private Locator dateToInput() {
        return page.locator(DATE_INPUT_SELECTOR).nth(1);
    }

    private Locator categoryTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(CATEGORY_LABEL_TEXT))
                .locator("xpath=following::button[1]")
                .first();
    }

    public record ProductionJournalFilterState(String productTerm,
                                               String categoryName,
                                               LocalDate startDate,
                                               LocalDate endDate) {}

    private ProductionJournalRow parseJournalRow(Locator row) {
        Locator cells = row.locator("td");
        String dateCellText = textContent(cells.nth(0));
        ParsedDateTime parsedDateTime = parseDateTime(dateCellText);

        return ProductionJournalRow.builder()
                .date(parsedDateTime.date())
                .time(parsedDateTime.time())
                .productName(textContent(cells.nth(1)))
                .amount(parseAmount(textContent(cells.nth(2))))
                .technologicalMapName(textContent(cells.nth(3)))
                .batchNumber(textContent(cells.nth(4)))
                .build();
    }

    private static String textContent(Locator cell) {
        String text = cell.innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    private static ParsedDateTime parseDateTime(String value) {
        Matcher matcher = DATE_TIME_PATTERN.matcher(value);
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse production journal date from: " + value);
        }
        LocalDate date = LocalDate.parse(matcher.group(1), UI_DATE_FORMAT);
        LocalTime time = null;
        if (matcher.group(2) != null) {
            time = LocalTime.parse(matcher.group(2), UI_TIME_FORMAT);
        }
        return new ParsedDateTime(date, time);
    }

    private static double parseAmount(String value) {
        Matcher matcher = AMOUNT_PATTERN.matcher(value);
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse production amount from: " + value);
        }
        return Double.parseDouble(matcher.group(1).replace(',', '.'));
    }

    private record ParsedDateTime(LocalDate date, LocalTime time) {}

    private Locator productionTableWrapper() {
        return page.locator(PRODUCTION_TABLE_WRAPPER_SELECTOR).first();
    }

    private boolean isFilterLabelVisible(String labelText) {
        Locator label = page.getByText(labelText, new Page.GetByTextOptions().setExact(true));
        return label.count() > 0 && label.first().isVisible();
    }

    private boolean isNamedActionVisible(String name) {
        Locator asLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name));
        if (asLink.count() > 0 && asLink.first().isVisible()) {
            return true;
        }
        Locator asButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
        return asButton.count() > 0 && asButton.first().isVisible();
    }
}
