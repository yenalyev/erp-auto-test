package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.erp.models.common.RelocationJournalRow;
import com.erp.models.query.RelocationJournalQuery;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RelocationPage extends BasePage {

    public static final String PATH = "/relocations";
    public static final String LOGISTICS_PATH = "/logistics";

    private static final String RECEIVE_BUTTON = "Отримати";
    private static final String SEND_BUTTON = "Видати";
    private static final String ISSUE_TO_CREW_BUTTON = "Видати на екіпаж";
    private static final String RECEIVE_FROM_CREW_BUTTON = "Отримати від екіпажа";
    private static final String HISTORY_RECEIVED_TAB = "Отримано";
    private static final String IN_TRANSIT_TAB = "В дорозі";
    private static final String SENT_TAB = "Видано";
    private static final String LOST_TAB = "Втрачено";
    private static final String BUNDLES_TAB = "Комплекти для видачі";
    private static final String CREATE_INCIDENT_BUTTON = "Створити інцидент";
    private static final String INCIDENT_DETAILS_BUTTON = "Деталі інциденту";
    private static final String DELETE_INCIDENT_BUTTON = "Видалити інцидент";
    private static final String CATEGORY_LABEL_TEXT = "Категорія";
    private static final String PRODUCT_LABEL_TEXT = "Продукт";
    private static final String EQUIPMENT_LABEL_TEXT = "Обладнання";
    private static final String LOADING_TEXT = "Завантаження...";
    private static final String TABLE_CONTAINER_SELECTOR = "[data-slot='table-container']";
    private static final String PAGE_SIZE_STORAGE_PREFIX = "pageSize_";

    public RelocationPage(Page page) {
        super(page);
    }

    public RelocationPage open() {
        page.waitForResponse(
                response -> response.url().contains("/creation-options") && response.ok(),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> navigateTo(ConfigProvider.getBaseUrl() + PATH, "Журнал переміщень"));
        return waitForLoaded();
    }

    public RelocationPage openLogistics() {
        navigateTo(ConfigProvider.getBaseUrl() + LOGISTICS_PATH, "Логістика");
        return waitForLoaded();
    }

    public boolean isIssueToCrewButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ISSUE_TO_CREW_BUTTON))
                .isVisible();
    }

    public RelocationCreateOutputCrewPage clickIssueToCrew() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ISSUE_TO_CREW_BUTTON)).click();
        return new RelocationCreateOutputCrewPage(page).waitForLoaded();
    }

    public boolean isReceiveFromCrewButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RECEIVE_FROM_CREW_BUTTON))
                .isVisible();
    }

    public RelocationCreateInputCrewPage clickReceiveFromCrew() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RECEIVE_FROM_CREW_BUTTON)).click();
        return new RelocationCreateInputCrewPage(page).waitForLoaded();
    }

    public RelocationPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(IN_TRANSIT_TAB))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationPage waitForJournalDataSettled() {
        page.waitForCondition(() -> {
            if (isJournalLoadErrorVisible()) {
                return true;
            }
            Locator loading = page.getByText(LOADING_TEXT);
            if (loading.count() > 0 && loading.isVisible()) {
                return false;
            }
            return journalTableWrapper().count() > 0;
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isReceiveButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RECEIVE_BUTTON)).isVisible();
    }

    public boolean isSendButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SEND_BUTTON)).first().isVisible();
    }

    public boolean isCategoryFilterVisible() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(CATEGORY_LABEL_TEXT))
                .count() > 0;
    }

    public boolean isJournalTableVisible() {
        Locator wrapper = journalTableWrapper();
        return wrapper.count() > 0 && wrapper.isVisible();
    }

    public boolean isJournalLoadErrorVisible() {
        Locator alert = page.locator("[role='alert'].border-red-200, [role='alert'][class*='red']");
        return alert.count() > 0 && alert.first().isVisible();
    }

    public int getDisplayedRowCount() {
        return journalTableWrapper().locator("tbody tr").count();
    }

    public List<RelocationJournalRow> getDisplayedJournalRows() {
        Locator rows = journalTableWrapper().locator("tbody tr");
        int count = rows.count();
        List<RelocationJournalRow> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(parseJournalRow(rows.nth(i)));
        }
        return result;
    }

    public static String pageSizeStorageKey(String tableId) {
        return PAGE_SIZE_STORAGE_PREFIX + tableId;
    }

    public RelocationPage filterByCategory(String categoryName) {
        runJournalFilterAction(() -> {
            categoryTrigger().click();
            page.locator("[data-radix-popper-content-wrapper] button, [role='dialog'] button, [role='menuitem']")
                    .filter(new Locator.FilterOptions().setHasText(categoryName))
                    .first()
                    .click();
        });
        return this;
    }

    public RelocationPage filterByProduct(String productSearchTerm) {
        runJournalFilterAction(() -> selectAutocompleteFilterOption(productFilterTrigger(), productSearchTerm));
        return this;
    }

    public RelocationPage filterByEquipment(String equipmentSearchTerm) {
        runJournalFilterAction(() -> selectAutocompleteFilterOption(equipmentFilterTrigger(), equipmentSearchTerm));
        return this;
    }

    public boolean isEquipmentFilterVisible() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(EQUIPMENT_LABEL_TEXT))
                .count() > 0;
    }

    public boolean isProductFilterVisible() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(PRODUCT_LABEL_TEXT))
                .count() > 0;
    }

    /**
     * Change journal page size via {@code PageSizeSelect} (values: 10, 20, 50, …).
     */
    public RelocationPage selectPageSize(int pageSize) {
        String size = String.valueOf(pageSize);
        runJournalFilterAction(() -> {
            Locator trigger = pageSizeSelectTrigger();
            trigger.click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(size).setExact(true))
                    .click();
        });
        return this;
    }

    public int getSelectedPageSize() {
        Locator trigger = pageSizeSelectTrigger();
        if (trigger.count() == 0 || !trigger.isVisible()) {
            return RelocationJournalQuery.DEFAULT_UI_PAGE_SIZE;
        }
        String value = trigger.innerText();
        if (value == null || value.isBlank()) {
            return RelocationJournalQuery.DEFAULT_UI_PAGE_SIZE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Cannot parse relocation page size from UI value '{}'", value);
            return RelocationJournalQuery.DEFAULT_UI_PAGE_SIZE;
        }
    }

    /** Row identity texts for pagination stability checks (full row text, trimmed). */
    public List<String> getDisplayedRowIdentityTexts() {
        Locator rows = journalTableWrapper().locator("tbody tr");
        int count = rows.count();
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String text = rows.nth(i).innerText();
            if (text != null) {
                result.add(text.trim().replaceAll("\\s+", " "));
            }
        }
        return result;
    }

    private void selectAutocompleteFilterOption(Locator trigger, String searchTerm) {
        String term = searchTerm == null ? "" : searchTerm.trim();
        String shortTerm = term.length() > 16 ? term.substring(0, 16) : term;
        trigger.click();
        Locator searchInput = page.locator(
                "[data-slot='command-input'], [cmdk-input], input[placeholder*='Оберіть'], input[placeholder*='Пошук']");
        searchInput.first().fill(shortTerm);
        Locator option = page.locator("[data-slot='command-item'], [cmdk-item], [role='option']")
                .filter(new Locator.FilterOptions().setHasText(shortTerm))
                .first();
        option.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        option.click();
    }

    public RelocationPage sortByColumn(String columnHeader) {
        runJournalFilterAction(() -> journalTableWrapper()
                .locator("thead th")
                .filter(new Locator.FilterOptions().setHasText(columnHeader))
                .locator("button")
                .first()
                .click());
        return this;
    }

    public boolean isRowWithTextVisible(String text) {
        return journalTableWrapper().locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(text))
                .count() > 0;
    }

    public boolean isInvoiceLinkVisible(String invoiceNumber) {
        return invoiceLink(invoiceNumber).count() > 0;
    }

    /**
     * Клік по № накладної → axios blob + програмний {@code <a download>}; чекаємо Playwright {@link Download}.
     */
    public InvoiceUiDownloadResult clickInvoiceLinkAndWaitForDownload(String invoiceNumber) {
        Locator link = invoiceLink(invoiceNumber);
        link.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        Download download = page.waitForDownload(link::click);
        String suggestedFilename = download.suggestedFilename();
        Path path = download.path();
        long sizeBytes = path != null ? path.toFile().length() : 0L;
        log.info("Invoice UI download: {} ({} bytes, path={})", suggestedFilename, sizeBytes, path);
        return new InvoiceUiDownloadResult(suggestedFilename, sizeBytes, path);
    }

    /**
     * Клік по посиланню не повинен ініціювати завантаження (наприклад, UNIT-відправник на «В дорозі»).
     */
    public void clickInvoiceLinkAndAssertNoDownload(String invoiceNumber, int noDownloadTimeoutMs) {
        Locator link = invoiceLink(invoiceNumber);
        link.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        try {
            Download unexpected = page.waitForDownload(
                    new Page.WaitForDownloadOptions().setTimeout(noDownloadTimeoutMs),
                    link::click);
            throw new AssertionError(
                    "Unexpected file download after invoice link click: " + unexpected.suggestedFilename());
        } catch (TimeoutError expected) {
            log.info("Invoice link click produced no download within {}ms (expected)", noDownloadTimeoutMs);
        }
    }

    public boolean isInvoiceNumberVisibleInRow(String rowMarker, String invoiceNumber) {
        return journalTableWrapper().locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(rowMarker))
                .filter(new Locator.FilterOptions().setHasText(invoiceNumber))
                .count() > 0;
    }

    public RelocationCreateInputPage clickReceive() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RECEIVE_BUTTON)).click();
        return new RelocationCreateInputPage(page).waitForLoaded();
    }

    public RelocationCreateOutputPage clickSend() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SEND_BUTTON))
                .first()
                .click();
        return new RelocationCreateOutputPage(page).waitForLoaded();
    }

    public RelocationPage openReceivedHistoryTab() {
        return openReceivedTab();
    }

    public RelocationPage openReceivedTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HISTORY_RECEIVED_TAB)).click();
        waitForJournalDataSettled();
        return this;
    }

    public RelocationPage openActiveTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(IN_TRANSIT_TAB)).click();
        waitForJournalDataSettled();
        return this;
    }

    public RelocationPage openHistoryTab() {
        return openSentTab();
    }

    public RelocationPage openSentTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(SENT_TAB)).click();
        waitForJournalDataSettled();
        return this;
    }

    public RelocationPage openInTransitTab() {
        return openActiveTab();
    }

    public RelocationPage openLostTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(LOST_TAB)).click();
        waitForJournalDataSettled();
        return this;
    }

    public boolean isBundlesTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(BUNDLES_TAB)).isVisible();
    }

    public RelocationBundlesTabPage openBundlesTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(BUNDLES_TAB)).click();
        return new RelocationBundlesTabPage(page).waitForLoaded();
    }

    public boolean isLostTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(LOST_TAB)).isVisible();
    }

    public boolean isActiveTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(IN_TRANSIT_TAB)).isVisible();
    }

    public RelocationCreateIncidentPage clickCreateIncidentInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CREATE_INCIDENT_BUTTON)).click();
        return new RelocationCreateIncidentPage(page).waitForLoaded();
    }

    public boolean isCreateIncidentButtonVisibleInRow(String rowText) {
        return rowContainingText(rowText)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CREATE_INCIDENT_BUTTON))
                .count() > 0;
    }

    public RelocationPage openIncidentDetailsInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(INCIDENT_DETAILS_BUTTON)).click();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Інцидент"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        // Wait until incident payload is rendered inside the dialog (not the journal tab «Втрачено»).
        incidentDialog().getByText("Опис", new Locator.GetByTextOptions().setExact(true))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isIncidentDescriptionVisible(String description) {
        return incidentDialog().getByText(description).count() > 0;
    }

    /** True when incident details dialog shows partial-delivery column/header. */
    public boolean isPartialDeliveryDetailsVisible(String deliveryStorageName) {
        Locator dialog = incidentDialog();
        if (dialog.getByText("Частково доставлено до " + deliveryStorageName).count() > 0) {
            return true;
        }
        return dialog.getByText("Частково доставлено до").count() > 0;
    }

    /**
     * Amount shown next to {@code resourceName} in a journal row (Lost tab «Ресурси» cell).
     * Expected contract: WRITE_OFF / lost qty, not the original relocation sent total.
     */
    public double getResourceAmountInRow(String rowMarker, String resourceName) {
        Locator row = rowContainingText(rowMarker);
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        String text = row.innerText();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(resourceName) + "\\s*:\\s*([\\d.,]+)")
                .matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Resource «" + resourceName + "» amount not found in row for marker «" + rowMarker
                            + "». Row text: " + text);
        }
        return Double.parseDouble(matcher.group(1).replace(',', '.'));
    }

    private Locator incidentDialog() {
        return page.locator("[role='dialog']").filter(
                new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Інцидент"))));
    }

    public RelocationPage deleteIncidentFromDetails() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DELETE_INCIDENT_BUTTON)).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Видалити")).last().click();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Інцидент"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.HIDDEN)
                        .setTimeout(uiTimeoutMs()));
        waitForJournalDataSettled();
        return this;
    }

    public void clickResolveInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Завершити")).click();
    }

    public void clickRejectInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Відхилити")).click();
    }

    public void clickReturnInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Повернути")).click();
    }

    public Locator rowContainingText(String text) {
        return page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(text)).first();
    }

    public boolean isEditButtonVisibleInRow(String rowText) {
        return rowContainingText(rowText)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Редагувати"))
                .count() > 0;
    }

    public boolean isDeleteButtonVisibleInRow(String rowText) {
        return rowContainingText(rowText)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Видалити"))
                .count() > 0;
    }

    public RelocationUpdateInputPage clickEditInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Редагувати")).click();
        return new RelocationUpdateInputPage(page).waitForLoaded();
    }

    public void clickDeleteInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Видалити")).click();
    }

    public void confirmDeleteDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Видалити")).last().click();
    }

    private void runJournalFilterAction(Runnable action) {
        page.waitForResponse(
                response -> response.url().contains("/relocations")
                        && !response.url().contains("/creation-options")
                        && "GET".equals(response.request().method()),
                action);
        waitForJournalDataSettled();
    }

    private Locator journalTableWrapper() {
        return page.locator(TABLE_CONTAINER_SELECTOR).last();
    }

    private Locator categoryTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(CATEGORY_LABEL_TEXT))
                .locator("xpath=following::button[1]")
                .first();
    }

    private Locator productFilterTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(PRODUCT_LABEL_TEXT))
                .locator("xpath=following::button[1]")
                .first();
    }

    private Locator equipmentFilterTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(EQUIPMENT_LABEL_TEXT))
                .locator("xpath=following::button[1]")
                .first();
    }

    private Locator pageSizeSelectTrigger() {
        // TablePagination: PageSizeSelect is the first select in the pagination footer.
        Locator inPagination = page.locator("div.flex.items-center.gap-2")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("[data-slot='select-trigger']")))
                .last()
                .locator("[data-slot='select-trigger']")
                .first();
        if (inPagination.count() > 0) {
            return inPagination;
        }
        return page.locator("[data-slot='select-trigger']").last();
    }

    /**
     * Sent-tab columns: Дата, Від, До, Ресурси, № накладної, Статус, Примітки, [actions].
     */
    private RelocationJournalRow parseJournalRow(Locator row) {
        Locator cells = row.locator("td");
        return RelocationJournalRow.builder()
                .senderName(cellText(cells, 1))
                .recipientName(cellText(cells, 2))
                .invoiceNumber(cellText(cells, 4))
                .description(cellText(cells, 6))
                .build();
    }

    private Locator invoiceLink(String invoiceNumber) {
        return journalTableWrapper().locator("a")
                .filter(new Locator.FilterOptions().setHasText(invoiceNumber));
    }

    private static String cellText(Locator cells, int index) {
        if (cells.count() <= index) {
            return null;
        }
        String text = cells.nth(index).innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : null;
    }

    public record InvoiceUiDownloadResult(String suggestedFilename, long sizeBytes, Path path) {}
}
