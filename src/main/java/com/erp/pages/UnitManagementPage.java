package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page Object for the Unit Management (Залишки) page.
 * URL: /inventory
 */
@Slf4j
public class UnitManagementPage extends BasePage {

    private static final String PATH = "/inventory";
    private static final String PAGE_TITLE_TEXT = "Управління запасами";
    private static final String OPEN_INVENTORY_BUTTON_TEXT = "Відкрити інвентаризацію";
    private static final String CLOSE_INVENTORY_BUTTON_TEXT = "Закрити інвентаризацію";
    private static final String CONDUCT_INVENTORY_BUTTON_TEXT = "Провести інвентаризацію";
    private static final String EXPORT_TO_EXCEL_BUTTON_TEXT = "Експорт в Excel";
    private static final String COPY_BUTTON_TEXT = "Скопіювати";
    private static final String COPIED_FEEDBACK_TEXT = "Скопійовано";
    private static final String SEARCH_PLACEHOLDER = "Пошук...";
    /** Quantity column of the stock table — «Кількість» only labels the per-batch detail table. */
    private static final String AMOUNT_HEADER = "Вільна к-сть";
    private static final String ALL_LOCATIONS_TOOLTIP = "Оберіть конкретну локацію для виконання дії";
    private static final String ADMIN_CONDUCT_TOOLTIP = "Зверніться до адміністратора для проведення інвентаризації";
    private static final String EXPORT_API_FRAGMENT = "/export-analytics/";
    private static final int DOWNLOAD_EVENT_GRACE_MS = 5_000;
    private static final Pattern CONTENT_DISPOSITION_UTF8 =
            Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_DISPOSITION_PLAIN =
            Pattern.compile("filename=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    public UnitManagementPage(Page page) {
        super(page);
    }

    public UnitManagementPage open() {
        return openForStorage(null);
    }

    public UnitManagementPage openForAllLocations() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Unit Management page in all-locations mode: {}", url);
        page.navigate(url);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.evaluate("localStorage.setItem('selectedStorageId', 'all');");
        waitForInventoryTableDuring(() -> page.reload());
        return waitForLoaded();
    }

    public UnitManagementPage openForStorage(Long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Unit Management page: {}", url);
        if (storageId != null) {
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
            waitForInventoryTableDuring(() -> page.reload());
        } else {
            waitForInventoryTableDuring(() -> navigateTo(url, "Залишки (/inventory)"));
        }
        return waitForLoaded();
    }

    /** Deep-link зі analytics: /inventory?storageId=… (CREW / FLY_POINT поза sidebar tree). */
    public UnitManagementPage openWithStorageIdQuery(long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH + "?storageId=" + storageId;
        log.info("Opening Unit Management via storageId query: {}", url);
        waitForInventoryTableDuring(() -> navigateTo(url, "Залишки (?storageId)"));
        return waitForLoaded();
    }

    public UnitManagementPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByText(PAGE_TITLE_TEXT)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    /** Reloads the page and waits for the inventory list GET to complete. */
    public UnitManagementPage refreshInventoryTable() {
        waitForInventoryTableDuring(() -> page.reload());
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByText(PAGE_TITLE_TEXT)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isOpenInventoryButtonVisible() {
        return inventoryToggleButton(OPEN_INVENTORY_BUTTON_TEXT).isVisible();
    }

    public boolean isCloseInventoryButtonVisible() {
        return inventoryToggleButton(CLOSE_INVENTORY_BUTTON_TEXT).isVisible();
    }

    public boolean isConductInventoryButtonEnabled() {
        return conductButton().isEnabled();
    }

    public boolean isConductInventoryButtonVisible() {
        return conductButton().count() > 0 && conductButton().first().isVisible();
    }

    public boolean isExportToExcelButtonVisible() {
        return exportToExcelButton().isVisible();
    }

    public boolean isExportToExcelButtonEnabled() {
        return exportToExcelButton().isEnabled();
    }

    /** True when the «Скопіювати» button is visible (specific location, not «Всі локації»). */
    public boolean isCopyButtonVisible() {
        Locator button = copyButton();
        return button.count() > 0 && button.first().isVisible();
    }

    public boolean isCopyButtonEnabled() {
        return copyButton().isEnabled();
    }

    /**
     * Stubs {@code navigator.clipboard.writeText} so the payload can be read back without OS
     * clipboard permissions (reliable in headless CI). Call before {@link #clickCopyRemainders()}.
     */
    public UnitManagementPage installClipboardCapture() {
        page.evaluate("""
                () => {
                  window.__erpClipboardText = undefined;
                  navigator.clipboard.writeText = async (text) => {
                    window.__erpClipboardText = text;
                  };
                }
                """);
        return this;
    }

    /** Waits until «Скопіювати» is visible (e.g. after the «Скопійовано» feedback clears). */
    public UnitManagementPage waitForCopyButtonReady() {
        copyButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitManagementPage clickCopyRemainders() {
        waitForCopyButtonReady();
        copyButton().click();
        return this;
    }

    public UnitManagementPage waitForCopiedFeedback() {
        page.getByText(COPIED_FEEDBACK_TEXT)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    /** Text captured by {@link #installClipboardCapture()}, or empty when nothing was written. */
    public String getCapturedClipboardText() {
        page.waitForCondition(
                () -> Boolean.TRUE.equals(page.evaluate("() => window.__erpClipboardText !== undefined")),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        Object value = page.evaluate("() => window.__erpClipboardText");
        return value != null ? value.toString() : "";
    }

    /**
     * Clicks «Експорт в Excel» and returns the exported file.
     *
     * <p>tk-ui fetches the file as a blob and triggers it through a synthetic {@code <a download>}
     * that is removed and revoked immediately, so the Chromium download event is unreliable.
     * The export response itself is the dependable signal; the download event is still observed
     * (registered before the click) and preferred when it does arrive, otherwise the response
     * payload is written to a temp file so callers can inspect the real bytes either way.
     */
    public ExportDownloadResult clickExportToExcelAndDownload() {
        List<Download> downloads = Collections.synchronizedList(new ArrayList<>());
        Consumer<Download> downloadListener = downloads::add;
        page.onDownload(downloadListener);
        try {
            com.microsoft.playwright.Response response = page.waitForResponse(
                    r -> r.url().contains(EXPORT_API_FRAGMENT),
                    new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                    () -> exportToExcelButton().click());

            try {
                page.waitForCondition(() -> !downloads.isEmpty(),
                        new Page.WaitForConditionOptions().setTimeout(DOWNLOAD_EVENT_GRACE_MS));
            } catch (PlaywrightException e) {
                log.debug("No browser download event for the export — using the response payload");
            }

            if (!downloads.isEmpty()) {
                Download download = downloads.getFirst();
                Path path = download.path();
                long sizeBytes = path != null ? path.toFile().length() : 0L;
                log.info("Unit management export download: {} ({} bytes, path={})",
                        download.suggestedFilename(), sizeBytes, path);
                return new ExportDownloadResult(download.suggestedFilename(), sizeBytes, path);
            }

            byte[] body = response.body();
            Path path = Files.createTempFile("erp-inventory-export-", ".xlsx");
            Files.write(path, body);
            log.info("Unit management export captured from response: {} bytes, path={}", body.length, path);
            return new ExportDownloadResult(exportFilename(response), body.length, path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist the inventory export payload", e);
        } finally {
            page.offDownload(downloadListener);
        }
    }

    private static String exportFilename(com.microsoft.playwright.Response response) {
        String disposition = response.headerValue("content-disposition");
        if (disposition == null) {
            return "";
        }
        Matcher utf8 = CONTENT_DISPOSITION_UTF8.matcher(disposition);
        if (utf8.find()) {
            return URLDecoder.decode(utf8.group(1).trim(), StandardCharsets.UTF_8);
        }
        Matcher plain = CONTENT_DISPOSITION_PLAIN.matcher(disposition);
        return plain.find() ? plain.group(1).trim() : "";
    }

    public UnitManagementPage clickOpenInventory() {
        Locator openBtn = inventoryToggleButton(OPEN_INVENTORY_BUTTON_TEXT);
        openBtn.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        if (!openBtn.isEnabled()) {
            throw new IllegalStateException("Кнопка «" + OPEN_INVENTORY_BUTTON_TEXT + "» недоступна");
        }
        openBtn.click();
        inventoryToggleButton(CLOSE_INVENTORY_BUTTON_TEXT)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitManagementPage clickCloseInventory() {
        waitForSessionOpenState(true);
        Locator closeBtn = inventoryToggleButton(CLOSE_INVENTORY_BUTTON_TEXT);
        if (!closeBtn.isEnabled()) {
            throw new IllegalStateException("Кнопка «" + CLOSE_INVENTORY_BUTTON_TEXT + "» недоступна");
        }
        closeBtn.click();
        inventoryToggleButton(OPEN_INVENTORY_BUTTON_TEXT)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitManagementPage clickConductInventory() {
        waitForConductButtonEnabled();
        conductButton().click();
        page.waitForURL("**/inventory/**", new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitManagementPage waitForSessionOpenState(boolean open) {
        String label = open ? CLOSE_INVENTORY_BUTTON_TEXT : OPEN_INVENTORY_BUTTON_TEXT;
        inventoryToggleButton(label).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitManagementPage waitForConductButtonEnabled() {
        conductButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForCondition(
                () -> conductButton().isEnabled(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitManagementPage assertInventorySessionOpen() {
        if (!isCloseInventoryButtonVisible()) {
            throw new AssertionError("Очікувалась кнопка «" + CLOSE_INVENTORY_BUTTON_TEXT + "»");
        }
        return this;
    }

    public UnitManagementPage search(String query) {
        Locator searchInput = page.getByPlaceholder(SEARCH_PLACEHOLDER).first();
        waitForInventoryTableDuring(() -> searchInput.fill(query));
        return this;
    }

    public UnitManagementPage waitForResourceInTable(String resourceName) {
        try {
            resourceRow(resourceName).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        } catch (RuntimeException e) {
            log.warn("Resource «{}» not found in table (url={})", resourceName, page.url());
            attachScreenshot("Resource not in table — " + resourceName);
            throw e;
        }
        return this;
    }

    public UnitManagementPage searchAndWaitForResource(String query, String resourceName) {
        try {
            search(query);
            waitForResourceInTable(resourceName);
        } catch (RuntimeException e) {
            log.warn("Search «{}» did not surface resource «{}» (url={})", query, resourceName, page.url());
            attachScreenshot("Search failed — " + resourceName);
            throw e;
        }
        return this;
    }

    public boolean isResourceVisibleInTable(String resourceName) {
        return resourceRow(resourceName).count() > 0;
    }

    public UnitManagementPage waitForTagBadge(String tag) {
        page.waitForCondition(
                () -> isTagBadgeVisible(tag),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public UnitManagementPage clickTagFilterBadge(String tag) {
        Locator badge = tagFilterBadge(tag);
        badge.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        try {
            page.waitForResponse(
                    response -> response.url().contains("/inventory")
                            && !response.url().contains("/status")
                            && !response.url().contains("/batches")
                            && !response.url().contains("/tag-statistics")
                            && "GET".equals(response.request().method())
                            && response.status() == 200,
                    badge::click);
        } catch (Exception e) {
            log.warn("Inventory tag filter response wait timed out: {}", e.getMessage());
            page.waitForTimeout(2000);
        }
        return this;
    }

    public boolean isTagBadgeVisible(String tag) {
        return tagFilterBadge(tag).count() > 0;
    }

    public boolean isTagBadgeSelected(String tag) {
        Locator badge = tagFilterBadge(tag);
        if (badge.count() == 0) {
            return false;
        }
        String className = badge.getAttribute("class");
        return className != null && className.contains("ring-green-700");
    }

    private Locator tagFilterBadge(String tag) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tag + " (")).first();
    }

    public UnitManagementPage clickResourceAmountLink(String resourceName) {
        Locator row = resourceRow(resourceName).first();
        row.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        row.locator("button.text-blue-600, button[class*='text-blue-600']").first().click();
        page.getByText("Партії ресурсу", new Page.GetByTextOptions().setExact(false))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isAllLocationsTableVisible() {
        return isMultiLocationTableVisible();
    }

    /** Multi-location table (колонка «Локація») — «Всі локації» або «По всій ієрархії». */
    public boolean isMultiLocationTableVisible() {
        Locator header = page.locator("table thead th")
                .filter(new Locator.FilterOptions().setHasText("Локація"));
        try {
            header.first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
            return header.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSingleLocationStatusColumnVisible() {
        Locator header = page.locator("table thead th")
                .filter(new Locator.FilterOptions().setHasText("Статус"));
        try {
            header.first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
            return header.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHierarchyCheckboxVisible() {
        return hierarchyCheckbox().count() > 0 && hierarchyCheckbox().isVisible();
    }

    public boolean isHierarchyCheckboxChecked() {
        return hierarchyCheckbox().isChecked();
    }

    public boolean isHierarchyCheckboxEnabled() {
        return hierarchyCheckbox().isEnabled();
    }

    public UnitManagementPage enableHierarchyView() {
        Locator checkbox = hierarchyCheckbox();
        checkbox.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        if (!checkbox.isChecked()) {
            waitForInventoryTableDuring(() -> checkbox.check());
        }
        return this;
    }

    public UnitManagementPage disableHierarchyView() {
        Locator checkbox = hierarchyCheckbox();
        checkbox.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        if (checkbox.isChecked() && checkbox.isEnabled()) {
            waitForInventoryTableDuring(() -> checkbox.uncheck());
        }
        return this;
    }

    private Locator hierarchyCheckbox() {
        return page.locator("#hierarchy-view");
    }

    public boolean isBatchDialogVisible() {
        return page.getByText("Партії ресурсу", new Page.GetByTextOptions().setExact(false)).isVisible()
                || page.getByText("Номер партії").isVisible();
    }

    public boolean hasStockRows() {
        return stockTableBodyRows().count() > 0;
    }

    public int stockRowCount() {
        return stockTableBodyRows().count();
    }

    /** Waits until at least one data row appears in the main stock table. */
    public UnitManagementPage waitForStockRows() {
        page.waitForCondition(
                () -> stockTableBodyRows().count() > 0,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Asserts the stock table has rows; attaches a screenshot to Allure before failing
     * so diagnostics are available even when the assertion runs inside an Allure step.
     */
    public UnitManagementPage assertHasStockRows() {
        try {
            waitForStockRows();
        } catch (RuntimeException e) {
            int count = stockTableBodyRows().count();
            log.warn("Stock table empty after wait (count={}, url={})", count, page.url());
            attachScreenshot("Empty stock table");
            throw new AssertionError(String.format(
                    "Таблиця «Залишки» не містить рядків (count=%d, url=%s)", count, page.url()), e);
        }
        return this;
    }

    public UnitManagementPage assertTableHeadersVisible() {
        page.locator("table thead th")
                .filter(new Locator.FilterOptions().setHasText("Ресурс"))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.locator("table thead th")
                .filter(new Locator.FilterOptions().setHasText(AMOUNT_HEADER))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    /** Raw text from the quantity cell (e.g. "50 шт" or "50.5 кг"). */
    public String getResourceAmountText(String resourceName) {
        Locator row = resourceRow(resourceName).first();
        row.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        Locator amountCell = row.locator("td").nth(columnIndexByHeader(AMOUNT_HEADER));
        return amountCell.innerText().trim().replaceAll("\\s+", " ");
    }

    /** Resolves a body-cell index from a stock table header label, so column order can shift. */
    private int columnIndexByHeader(String headerText) {
        Locator headers = stockTable().locator("thead th");
        int count = headers.count();
        for (int i = 0; i < count; i++) {
            String text = headers.nth(i).innerText();
            if (text != null && headerText.equals(text.trim().replaceAll("\\s+", " "))) {
                return i;
            }
        }
        throw new IllegalStateException(
                "Column «" + headerText + "» not found in the stock table header. Present: "
                        + headers.allInnerTexts());
    }

    /** Parses numeric amount from the quantity cell. */
    public double getResourceAmount(String resourceName) {
        return parseAmountFromCellText(getResourceAmountText(resourceName));
    }

    public UnitManagementPage assertResourceAmountVisible(String resourceName, double expected) {
        double actual = getResourceAmount(resourceName);
        if (Math.abs(actual - expected) > 0.01) {
            throw new AssertionError(String.format(
                    "Очікувалась кількість %.2f для «%s», на UI: %.2f (%s)",
                    expected, resourceName, actual, getResourceAmountText(resourceName)));
        }
        return this;
    }

    static double parseAmountFromCellText(String cellText) {
        if (cellText == null || cellText.isBlank()) {
            return 0.0;
        }
        String normalized = cellText.trim().replace(',', '.');
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(-?\\d+(?:\\.\\d+)?)")
                .matcher(normalized);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        throw new IllegalArgumentException("Cannot parse amount from: " + cellText);
    }

    public boolean isOpenInventoryButtonDisabled() {
        Locator btn = inventoryToggleButton(OPEN_INVENTORY_BUTTON_TEXT);
        return btn.isVisible() && btn.isDisabled();
    }

    /**
     * In «Всі локації» mode the session toggle is either hidden (no concrete storage)
     * or rendered disabled — both block opening a session.
     */
    public boolean isInventorySessionToggleBlocked() {
        if (!isOpenInventoryButtonVisible()) {
            return true;
        }
        return inventoryToggleButton(OPEN_INVENTORY_BUTTON_TEXT).isDisabled();
    }

    public String conductButtonTooltip() {
        conductButton().hover();
        Locator tip = page.locator("[role='tooltip']").filter(new Locator.FilterOptions()
                .setHasText(ADMIN_CONDUCT_TOOLTIP));
        if (tip.count() > 0 && tip.first().isVisible()) {
            return tip.first().innerText();
        }
        return "";
    }

    private Locator inventoryToggleButton(String label) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label));
    }

    private Locator conductButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CONDUCT_INVENTORY_BUTTON_TEXT));
    }

    private Locator exportToExcelButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(EXPORT_TO_EXCEL_BUTTON_TEXT));
    }

    private Locator copyButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(COPY_BUTTON_TEXT));
    }

    public record ExportDownloadResult(String suggestedFilename, long sizeBytes, Path path) {}

    private Locator stockTableBodyRows() {
        return stockTable().locator("tbody tr");
    }

    private Locator stockTable() {
        return page.locator("table").filter(new Locator.FilterOptions()
                .setHas(page.locator("thead th").filter(new Locator.FilterOptions().setHasText("Ресурс"))));
    }

    private Locator resourceRow(String resourceName) {
        String needle = resourceName.trim();
        String rowFilter = needle.length() > 24 ? needle.substring(0, 24) : needle;
        return stockTableBodyRows().filter(new Locator.FilterOptions().setHasText(rowFilter));
    }

    private void waitForInventoryTableDuring(Runnable action) {
        try {
            page.waitForResponse(
                    response -> response.url().contains("/inventory")
                            && !response.url().contains("/status")
                            && !response.url().contains("/batches")
                            && !response.url().contains("/tag-statistics")
                            && "GET".equals(response.request().method())
                            && response.status() == 200,
                    action);
        } catch (Exception e) {
            log.warn("Inventory table response wait timed out: {}", e.getMessage());
            action.run();
            page.waitForTimeout(2000);
        }
    }
}
