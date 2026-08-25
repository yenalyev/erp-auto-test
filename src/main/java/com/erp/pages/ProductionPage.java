package com.erp.pages;

import com.erp.models.common.ProductionJournalRow;
import com.erp.pages.components.DateRangePickerComponent;
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
    private static final String NOTES_COLUMN_HEADER = "Примітки";
    private static final String NOTES_EDIT_BUTTON_LABEL = "Редагувати примітки";
    private static final String NOTES_DIALOG_PLACEHOLDER = "Введіть примітки...";
    private static final String NOTES_SAVE_BUTTON = "Зберегти";
    private static final Pattern TAG_BADGE_PATTERN = Pattern.compile("(#\\S+) \\((\\d+)\\)");

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

    /** True when «Виготовлення» is visible and enabled (not aria-disabled / disabled). */
    public boolean isManufacturingButtonEnabled() {
        Locator action = namedActionLocator(MANUFACTURING_BUTTON_TEXT);
        if (action.count() == 0 || !action.first().isVisible()) {
            return false;
        }
        Locator first = action.first();
        if (first.isDisabled()) {
            return false;
        }
        String ariaDisabled = first.getAttribute("aria-disabled");
        return ariaDisabled == null || !"true".equalsIgnoreCase(ariaDisabled);
    }

    /** Wait until «Виготовлення» matches {@code enabled} after a workspace switch. */
    public ProductionPage waitForManufacturingButtonEnabled(boolean enabled) {
        page.waitForCondition(
                () -> isManufacturingButtonEnabled() == enabled,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public ProductionPage clickManufacturing() {
        namedActionLocator(MANUFACTURING_BUTTON_TEXT).first().click();
        return this;
    }

    public boolean isNotesEditVisibleForRow(int rowIndex) {
        int notesColumnIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
        Locator button = productionTableWrapper().locator("tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(notesColumnIndex)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(NOTES_EDIT_BUTTON_LABEL));
        return button.count() > 0 && button.first().isVisible() && button.first().isEnabled();
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

    /** True when the «Період» DateRangePicker trigger is visible (replaces dual «З»/«По» inputs). */
    public boolean isPeriodFilterVisible() {
        return dateRangePicker().isVisible();
    }

    /** @deprecated use {@link #isPeriodFilterVisible()} — kept for existing assertions */
    public boolean isDateFromVisible() {
        return isPeriodFilterVisible();
    }

    /** @deprecated use {@link #isPeriodFilterVisible()} — kept for existing assertions */
    public boolean isDateToVisible() {
        return isPeriodFilterVisible();
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

    /** ISO date (yyyy-MM-dd) start of the «Період» range, or empty when unset. */
    public String getDateFromValue() {
        return dateRangePicker().getFromIso();
    }

    /** ISO date (yyyy-MM-dd) end of the «Період» range, or empty when unset. */
    public String getDateToValue() {
        return dateRangePicker().getToIso();
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
        runJournalFilterAction(() -> {
            DateRangePickerComponent picker = dateRangePicker();
            String toIso = picker.getToIso();
            if (toIso.isBlank()) {
                picker.setFromOnly(date);
            } else {
                picker.setRange(date, LocalDate.parse(toIso));
            }
        });
        return this;
    }

    public ProductionPage filterByDateTo(LocalDate date) {
        runJournalFilterAction(() -> {
            DateRangePickerComponent picker = dateRangePicker();
            String fromIso = picker.getFromIso();
            if (fromIso.isBlank()) {
                // End-only is not supported by DateRangePicker — same-day range.
                picker.setRange(date, date);
            } else {
                picker.setRange(LocalDate.parse(fromIso), date);
            }
        });
        return this;
    }

    public ProductionPage filterByDateRange(LocalDate from, LocalDate to) {
        runJournalFilterAction(() -> dateRangePicker().setRange(from, to));
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
        if (filters.startDate() != null && filters.endDate() != null) {
            filterByDateRange(filters.startDate(), filters.endDate());
        } else if (filters.startDate() != null) {
            filterByDateFrom(filters.startDate());
        } else if (filters.endDate() != null) {
            filterByDateTo(filters.endDate());
        }
        return this;
    }

    public ProductionPage clearFilters() {
        runJournalFilterAction(() -> page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(CLEAR_BUTTON_TEXT)).click());
        return this;
    }

    public int findRowIndexByBatchNumber(String batchNumber) {
        Locator rows = productionTableWrapper().locator("tbody tr");
        int batchColumnIndex = columnIndexByHeader("Номер партії");
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            String batch = textContent(rows.nth(i).locator("td").nth(batchColumnIndex));
            if (batchNumber.equals(batch)) {
                return i;
            }
        }
        throw new IllegalStateException("Production row with batch " + batchNumber + " not found on UI");
    }

    public String getNotesTextForRow(int rowIndex) {
        int notesColumnIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
        Locator notesCell = productionTableWrapper().locator("tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(notesColumnIndex);
        Locator taggedText = notesCell.locator("[data-slot='tagged-text']");
        if (taggedText.count() > 0) {
            return textContent(taggedText.first());
        }
        return textContent(notesCell);
    }

    public List<String> getHighlightedTagsForRow(int rowIndex) {
        int notesColumnIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
        Locator tags = productionTableWrapper().locator("tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(notesColumnIndex)
                .locator("[data-slot='tagged-text-tag']");
        List<String> result = new ArrayList<>();
        int count = tags.count();
        for (int i = 0; i < count; i++) {
            result.add(textContent(tags.nth(i)));
        }
        return result;
    }

    public ProductionPage openNotesEditorForRow(int rowIndex) {
        int notesColumnIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
        productionTableWrapper().locator("tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(notesColumnIndex)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(NOTES_EDIT_BUTTON_LABEL))
                .click();
        page.getByRole(AriaRole.DIALOG)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public ProductionPage openNotesEditorForBatch(String batchNumber) {
        return openNotesEditorForRow(findRowIndexByBatchNumber(batchNumber));
    }

    public ProductionPage fillNotesDialog(String text) {
        page.getByPlaceholder(NOTES_DIALOG_PLACEHOLDER).fill(text);
        return this;
    }

    public ProductionPage saveNotesDialog() {
        page.waitForResponse(
                response -> response.url().contains("/productions/")
                        && response.url().contains("/notes")
                        && "PATCH".equals(response.request().method()),
                () -> page.getByRole(AriaRole.DIALOG)
                        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(NOTES_SAVE_BUTTON))
                        .click());
        page.getByRole(AriaRole.DIALOG)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.HIDDEN)
                        .setTimeout(uiTimeoutMs()));
        waitForJournalDataSettled();
        return this;
    }

    /** Re-applies product filter to refresh tag-statistics toolbar after notes change. */
    public ProductionPage refreshTagStatistics(String productTerm) {
        if (productTerm == null || productTerm.isBlank()) {
            return this;
        }
        filterByProduct("");
        return filterByProduct(productTerm);
    }

    public List<String> getVisibleTagBadges() {
        List<String> badges = new ArrayList<>();
        Locator buttons = page.locator("button").filter(new Locator.FilterOptions().setHasText("#"));
        int count = buttons.count();
        for (int i = 0; i < count; i++) {
            String label = textContent(buttons.nth(i));
            Matcher matcher = TAG_BADGE_PATTERN.matcher(label);
            if (matcher.matches()) {
                badges.add(matcher.group(1));
            }
        }
        return badges;
    }

    public ProductionPage clickTagFilterBadge(String tag) {
        Locator badge = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tag + " (")).first();
        badge.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                response -> response.url().contains("/productions")
                        && "GET".equals(response.request().method()),
                badge::click);
        waitForJournalDataSettled();
        return this;
    }

    public boolean isTagBadgeVisible(String tag) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tag + " (")).count() > 0;
    }

    public boolean isTagBadgeSelected(String tag) {
        Locator badge = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tag + " (")).first();
        if (badge.count() == 0) {
            return false;
        }
        String className = badge.getAttribute("class");
        return className != null && className.contains("ring-green-700");
    }

    public boolean rowWithBatchIsVisible(String batchNumber) {
        try {
            findRowIndexByBatchNumber(batchNumber);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
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

    private DateRangePickerComponent dateRangePicker() {
        return new DateRangePickerComponent(page, uiTimeoutMs());
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
        int dateIndex = columnIndexByHeader("Дата");
        int productIndex = columnIndexByHeader("Продукт");
        int amountIndex = columnIndexByHeader("Об'єм");
        int techMapIndex = columnIndexByHeader("Тех. карта");
        int batchIndex = columnIndexByHeader("Номер партії");

        String dateCellText = textContent(cells.nth(dateIndex));
        ParsedDateTime parsedDateTime = parseDateTime(dateCellText);

        String notes = null;
        try {
            int notesIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
            notes = getNotesTextFromCell(cells.nth(notesIndex));
        } catch (IllegalStateException ignored) {
            // notes column may be absent in some layouts
        }

        return ProductionJournalRow.builder()
                .date(parsedDateTime.date())
                .time(parsedDateTime.time())
                .productName(textContent(cells.nth(productIndex)))
                .amount(parseAmount(textContent(cells.nth(amountIndex))))
                .technologicalMapName(textContent(cells.nth(techMapIndex)))
                .batchNumber(textContent(cells.nth(batchIndex)))
                .notes(notes)
                .build();
    }

    private static String getNotesTextFromCell(Locator notesCell) {
        Locator taggedText = notesCell.locator("[data-slot='tagged-text']");
        if (taggedText.count() > 0) {
            return textContent(taggedText.first());
        }
        return textContent(notesCell);
    }

    private int columnIndexByHeader(String headerText) {
        Locator headers = productionTableWrapper().locator("thead th");
        int count = headers.count();
        for (int i = 0; i < count; i++) {
            if (headerText.equals(textContent(headers.nth(i)))) {
                return i;
            }
        }
        throw new IllegalStateException("Column header not found: " + headerText);
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

    private Locator namedActionLocator(String name) {
        Locator asLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name));
        if (asLink.count() > 0) {
            return asLink;
        }
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }
}
