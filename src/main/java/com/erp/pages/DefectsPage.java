package com.erp.pages;

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
    private static final String WRITE_OFF_BUTTON_TEXT = "Списати";
    private static final String HISTORY_BUTTON_TEXT = "Списання";
    private static final String DELETE_BUTTON_TEXT = "Видалити";
    private static final String EDIT_BUTTON_TEXT = "Редагувати";
    private static final String WRITE_OFF_DIALOG_TITLE = "Списати брак";
    private static final String WRITE_OFF_SAVE_BUTTON = "Зберегти";
    private static final String WRITE_OFF_CANCEL_BUTTON = "Скасувати";

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
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING_TEXT))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
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
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING_TEXT)).isVisible();
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

    /** Value of the «Кількість» (remaining) column for the row matching the given resource name. */
    public String getRemainingAmount(String resourceName) {
        return cellText(rowByResource(resourceName).first(), 3);
    }

    /** Value of the «Списано» column for the row matching the given resource name. */
    public String getWrittenOffAmount(String resourceName) {
        return cellText(rowByResource(resourceName).first(), 4);
    }

    // -------------------------------------------------------------------
    // Row actions
    // -------------------------------------------------------------------

    public DefectsPage openWriteOffDialog(String resourceName) {
        rowByResource(resourceName).first()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(WRITE_OFF_BUTTON_TEXT))
                .click();
        writeOffDialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public DefectsPage openHistoryDialog(String resourceName) {
        rowByResource(resourceName).first()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(HISTORY_BUTTON_TEXT))
                .click();
        return this;
    }

    /**
     * Clicks «Видалити» expecting the client-side blocking {@code alert(...)} that tk-ui shows
     * when the defect has a non-zero {@code writeOffAmount} (see {@code DefectListPage.handleDelete}).
     * The dialog is dismissed automatically and its message text is returned.
     */
    public String clickDeleteExpectingBlockAlert(String resourceName) {
        StringBuilder captured = new StringBuilder();
        page.onceDialog(dialog -> {
            captured.append(dialog.message());
            dialog.accept();
        });
        rowByResource(resourceName).first()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(DELETE_BUTTON_TEXT))
                .click();
        page.waitForTimeout(500);
        return captured.toString();
    }

    /** Clicks «Видалити» and accepts the native confirm() dialog (normal, no write-offs). */
    public DefectsPage clickDeleteAndConfirm(String resourceName) {
        page.onceDialog(com.microsoft.playwright.Dialog::accept);
        rowByResource(resourceName).first()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(DELETE_BUTTON_TEXT))
                .click();
        waitForDataSettled();
        return this;
    }

    public boolean isDeleteButtonVisible(String resourceName) {
        Locator btn = rowByResource(resourceName).first()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(DELETE_BUTTON_TEXT));
        return btn.count() > 0 && btn.isVisible();
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

    private Locator tableBody() {
        return page.locator("table tbody").first();
    }

    private Locator rowByResource(String resourceName) {
        return tableBody().locator("tr").filter(new Locator.FilterOptions().setHasText(resourceName));
    }

    private static String cellText(Locator row, int index) {
        String text = row.locator("td").nth(index).innerText();
        return text != null ? text.trim() : "";
    }
}
