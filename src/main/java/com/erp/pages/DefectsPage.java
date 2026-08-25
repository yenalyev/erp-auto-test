package com.erp.pages;

import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the Defect ("Брак") list page and its row-level dialogs
 * (write-off, write-off history). URL: /defects.
 *
 * <p>Selectors mirror tk-ui {@code DefectListPage.tsx} / {@code DefectWriteOffDialog.tsx} —
 * no {@code data-testid} attributes exist in this module, so we rely on Ukrainian text + roles.
 */
@Slf4j
public class DefectsPage extends BasePage {

    public static final String PATH = "/defects";

    private static final String HEADING_TEXT = "Брак";
    private static final String CREATE_BUTTON_TEXT = "Додати запис";
    private static final String LOADING_TEXT = "Завантаження...";
    private static final String EMPTY_STATE_TEXT = "Записів не знайдено";
    private static final String WRITE_OFF_DIALOG_TITLE = "Списати брак";
    private static final String WRITE_OFF_SAVE_BUTTON = "Зберегти";
    private static final String WRITE_OFF_CANCEL_BUTTON = "Скасувати";
    private static final String WRITE_OFF_FILTER_LABEL = "Списання";
    private static final String RESOURCE_SEARCH_PLACEHOLDER = "Назва ресурсу...";
    private static final String AMOUNT_HEADER = "Брак";
    private static final String WRITE_OFF_HEADER = "Списано";

    /**
     * Tooltip when row actions are frozen because the defect already has write-offs
     * ({@code DEFECT_HAS_WRITE_OFFS} in tk-ui {@code message-constants.ts}).
     */
    public static final String DELETE_BLOCKED_BY_WRITE_OFF = "Дія недоступна для дефекту зі списаннями";

    /** Filter option: no write-off filter ({@code isWriteOff} omitted). */
    public static final String WRITE_OFF_FILTER_ALL = "Всі";
    /** Filter option: only fully written-off records ({@code amount = 0}). */
    public static final String WRITE_OFF_FILTER_WRITTEN = "Списано";
    /** Filter option: only not fully written-off records ({@code amount > 0}). Default per AC. */
    public static final String WRITE_OFF_FILTER_NOT_WRITTEN = "Не списано";

    public DefectsPage(Page page) {
        super(page);
    }

    public DefectsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Брак (/defects)");
        return waitForLoaded();
    }

    public DefectsPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached within timeout — proceeding: {}", e.getMessage());
        }
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HEADING_TEXT))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT)))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        waitForDataSettled();
        return this;
    }

    /**
     * Clears persisted «Період» (localStorage preset can hide today's API-created rows)
     * and filters the journal by resource name so the row is on the first page.
     */
    public DefectsPage revealResource(String resourceName) {
        clearPeriodFilter();
        searchByResource(resourceName);
        Locator row = rowByResource(resourceName).first();
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public DefectsPage clearPeriodFilter() {
        DateRangePickerComponent picker = new DateRangePickerComponent(page, uiTimeoutMs());
        if (!picker.isVisible() || picker.getDisplayedRange().isEmpty()) {
            return this;
        }
        try {
            waitForDefectsReload(picker::clear);
        } catch (RuntimeException e) {
            log.debug("Period clear did not trigger /defects reload: {}", e.getMessage());
            picker.clear();
        }
        waitForDataSettled();
        return this;
    }

    /** Wait until the defect table finishes loading (rows or empty state rendered). */
    public DefectsPage waitForDataSettled() {
        page.waitForCondition(() -> {
            Locator loading = page.getByText(LOADING_TEXT);
            if (loading.count() > 0 && loading.isVisible()) {
                return false;
            }
            return rowCount() > 0 || isEmptyStateVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isHeadingVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HEADING_TEXT)).isVisible()
                || page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT))
                .isVisible();
    }

    public boolean isEmptyStateVisible() {
        Locator empty = page.getByText(EMPTY_STATE_TEXT);
        return empty.count() > 0 && empty.isVisible();
    }

    public boolean isCreateButtonVisible() {
        Locator btn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT));
        return btn.count() > 0 && btn.first().isVisible();
    }

    public boolean isCreateButtonDisabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT)).isDisabled();
    }

    public DefectFormPage clickCreate() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT)).click();
        return new DefectFormPage(page).waitForLoaded();
    }

    public int rowCount() {
        return tableBody().locator("tr").count();
    }

    public boolean isRowWithResourceVisible(String resourceName) {
        Locator row = rowByResource(resourceName);
        return row.count() > 0 && row.first().isVisible();
    }

    /** Value of the «Брак» (remaining) column for the row matching the given resource name. */
    public String getRemainingAmount(String resourceName) {
        return cellText(rowByResource(resourceName).first(), columnIndexByHeader(AMOUNT_HEADER));
    }

    /** Value of the «Списано» column for the row matching the given resource name. */
    public String getWrittenOffAmount(String resourceName) {
        return cellText(rowByResource(resourceName).first(), columnIndexByHeader(WRITE_OFF_HEADER));
    }

    // -------------------------------------------------------------------
    // Filters («Списання» / resource search)
    // -------------------------------------------------------------------

    /** Current value shown on the «Списання» select trigger. */
    public String getWriteOffFilterValue() {
        return writeOffFilterTrigger().innerText().trim();
    }

    /**
     * Selects an option on the «Списання» filter (Всі / Списано / Не списано)
     * and waits for the list to reload. No-op if the option is already selected.
     */
    public DefectsPage selectWriteOffFilter(String optionLabel) {
        if (optionLabel.equals(getWriteOffFilterValue())) {
            return this;
        }
        waitForDefectsReload(() -> {
            writeOffFilterTrigger().click();
            page.getByRole(AriaRole.OPTION,
                            new Page.GetByRoleOptions().setName(optionLabel).setExact(true))
                    .click();
        });
        waitForDataSettled();
        return this;
    }

    /**
     * Debounced resource search ({@code SearchInput}, ~300&nbsp;ms). Waits for list reload.
     */
    public DefectsPage searchByResource(String resourceName) {
        waitForDefectsReload(() -> {
            page.getByPlaceholder(RESOURCE_SEARCH_PLACEHOLDER).fill(resourceName);
            page.waitForTimeout(400);
        });
        waitForDataSettled();
        return this;
    }

    // -------------------------------------------------------------------
    // Row actions
    // -------------------------------------------------------------------

    public DefectsPage openWriteOffDialog(String resourceName) {
        revealResource(resourceName);
        writeOffButton(resourceName).click();
        writeOffDialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public DefectsPage openHistoryDialog(String resourceName) {
        revealResource(resourceName);
        rowActionButton(resourceName, "lucide-history").click();
        return this;
    }

    /**
     * Whether «Видалити» is disabled for the row (e.g. defect has write-offs —
     * tk-ui {@code actionBlockedReason = DEFECT_HAS_WRITE_OFFS}).
     */
    public boolean isDeleteButtonDisabled(String resourceName) {
        revealResource(resourceName);
        return deleteButton(resourceName).isDisabled();
    }

    /**
     * Hover «Видалити» and return the Radix tooltip text that explains why the action is blocked.
     */
    public String deleteBlockedTooltip(String resourceName) {
        Locator btn = deleteButton(resourceName);
        // TooltipTrigger wraps the button in <span>; hover the wrapper so the tooltip opens for disabled buttons.
        btn.locator("xpath=ancestor::span[1]").or(btn).first().hover();
        Locator tip = page.locator("[role='tooltip']").filter(new Locator.FilterOptions()
                .setHasText(DELETE_BLOCKED_BY_WRITE_OFF));
        tip.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return tip.first().innerText().trim();
    }

    /** Clicks «Видалити» and accepts the native confirm() dialog (normal, no write-offs). */
    public DefectsPage clickDeleteAndConfirm(String resourceName) {
        page.onceDialog(com.microsoft.playwright.Dialog::accept);
        deleteButton(resourceName).click();
        waitForDataSettled();
        return this;
    }

    public boolean isDeleteButtonVisible(String resourceName) {
        Locator btn = deleteButton(resourceName);
        return btn.count() > 0 && btn.isVisible();
    }

    private Locator deleteButton(String resourceName) {
        return rowActionButton(resourceName, "lucide-trash-2");
    }

    /**
     * tk-ui row actions are icon-only (tooltip text is not the accessible name).
     * Lucide classes: scissors = write-off, trash-2 = delete, history, pencil.
     */
    private Locator writeOffButton(String resourceName) {
        return rowActionButton(resourceName, "lucide-scissors");
    }

    private Locator rowActionButton(String resourceName, String lucideClass) {
        return rowByResource(resourceName).first()
                .locator("button")
                .filter(new Locator.FilterOptions().setHas(page.locator("svg." + lucideClass)))
                .first();
    }

    // -------------------------------------------------------------------
    // Write-off dialog («Списати брак»)
    // -------------------------------------------------------------------

    private Locator writeOffDialog() {
        return page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHasText(WRITE_OFF_DIALOG_TITLE));
    }

    public boolean isWriteOffDialogVisible() {
        Locator dialog = writeOffDialog();
        return dialog.count() > 0 && dialog.isVisible();
    }

    /**
     * Fills the write-off quantity. Works for both dialog modes: the plain «Кількість» input
     * (no batches) and the per-batch table input — both render as the first
     * {@code input[type='number']} inside the dialog.
     */
    public DefectsPage fillWriteOffQuantity(String amount) {
        writeOffDialog().locator("input[type='number']").first().fill(amount);
        page.waitForTimeout(300);
        return this;
    }

    public boolean isWriteOffSaveDisabled() {
        return writeOffDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(WRITE_OFF_SAVE_BUTTON))
                .isDisabled();
    }

    public DefectsPage saveWriteOff() {
        writeOffDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(WRITE_OFF_SAVE_BUTTON))
                .click();
        writeOffDialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        waitForDataSettled();
        return this;
    }

    public DefectsPage cancelWriteOffDialog() {
        writeOffDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(WRITE_OFF_CANCEL_BUTTON))
                .click();
        writeOffDialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    // -------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------

    private Locator writeOffFilterTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(WRITE_OFF_FILTER_LABEL))
                .locator("xpath=following::button[@role='combobox'][1]")
                .first();
    }

    private void waitForDefectsReload(Runnable action) {
        page.waitForResponse(
                response -> response.url().contains("/defects")
                        && "GET".equals(response.request().method())
                        && !response.url().contains("/write-off")
                        && !response.url().contains("/linked-"),
                action);
    }

    private Locator tableBody() {
        return page.locator("table tbody").first();
    }

    private Locator rowByResource(String resourceName) {
        return tableBody().locator("tr").filter(new Locator.FilterOptions().setHasText(resourceName));
    }

    /**
     * Resolves a body-cell index from a column header label. Columns in tk-ui
     * {@code DefectListPage.tsx} are toggled individually (including a leading, unlabelled
     * attachment column), so positions shift with the user's column settings and must not be
     * hardcoded.
     */
    private int columnIndexByHeader(String headerText) {
        Locator headers = page.locator("table thead tr").first().locator("th");
        int count = headers.count();
        for (int i = 0; i < count; i++) {
            String text = headers.nth(i).innerText();
            if (text != null && headerText.equals(text.trim())) {
                return i;
            }
        }
        throw new IllegalStateException(
                "Column «" + headerText + "» not found in the defect table header. Present: "
                        + headers.allInnerTexts());
    }

    private static String cellText(Locator row, int index) {
        String text = row.locator("td").nth(index).innerText();
        return text != null ? text.trim() : "";
    }
}
