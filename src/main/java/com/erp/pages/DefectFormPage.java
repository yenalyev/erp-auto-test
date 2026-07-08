package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the Defect create/edit form (tk-ui {@code DefectFormPage.tsx}).
 * URL: /defects/create (create) or /defects/update/{id} (edit).
 */
@Slf4j
public class DefectFormPage extends BasePage {

    public static final String PATH = "/defects/create";

    public static final String TYPE_STORAGE = "Склад";
    public static final String TYPE_RELOCATION = "Переміщення";
    public static final String TYPE_RELOCATION_FROM_UNIT = "Переміщення з підрозділів";
    public static final String TYPE_PRODUCTION = "Виробництво";

    private static final String TYPE_LABEL = "Тип браку";
    private static final String SENDER_LABEL = "Відправник";
    private static final String DESCRIPTION_LABEL = "Опис";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String SENDER_PLACEHOLDER = "Оберіть відправника...";
    private static final String AUTOCOMPLETE_SEARCH_PLACEHOLDER = "Пошук...";
    private static final String AMOUNT_PLACEHOLDER = "0.00";
    private static final String SUBMIT_BUTTON_TEXT = "Зберегти";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";
    /** Shown when a produced relocation batch is fully consumed (tk-ui {@code relocationBlocked}). */
    public static final String USED_BATCH_BLOCKED_TEXT =
            "Партія повністю використана — створити брак для неї неможливо";

    public DefectFormPage(Page page) {
        super(page);
    }

    public DefectFormPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Новий запис браку (/defects/create)");
        return waitForLoaded();
    }

    public DefectFormPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        typeSelectTrigger().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public DefectFormPage selectType(String typeLabel) {
        typeSelectTrigger().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(typeLabel).setExact(true))
                .first()
                .click();
        page.waitForTimeout(300);
        return this;
    }

    public DefectFormPage selectSenderByName(String senderName) {
        Locator input = page.getByPlaceholder(SENDER_PLACEHOLDER);
        input.click();
        input.fill(senderName);
        waitForComboboxOptionsSettled();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(senderName))
                .first()
                .click();
        return this;
    }

    /**
     * Selects a resource by name. The STORAGE defect type renders a directly-visible
     * {@code ComboboxInput} (placeholder «Оберіть ресурс...»); other types (RELOCATION,
     * RELOCATION_FROM_UNIT, PRODUCTION) render an {@code Autocomplete} — a button trigger
     * that opens a popover containing a separate «Пошук...» search input and {@code cmdk}
     * options — see tk-ui {@code components/ui/autocomplete.tsx}.
     */
    public DefectFormPage selectResourceByName(String resourceName) {
        String term = resourceName.trim().length() > 12
                ? resourceName.trim().substring(0, 12)
                : resourceName.trim();
        Locator directInput = page.getByPlaceholder(RESOURCE_PLACEHOLDER);
        if (directInput.count() > 0) {
            directInput.click();
            directInput.fill(term);
            waitForComboboxOptionsSettled();
            page.locator(COMBOBOX_ITEM_SELECTOR)
                    .filter(new Locator.FilterOptions().setHasText(resourceName.trim()))
                    .first()
                    .click();
            return this;
        }

        resourceAutocompleteTrigger().click();
        Locator searchInput = page.getByPlaceholder(AUTOCOMPLETE_SEARCH_PLACEHOLDER);
        searchInput.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        searchInput.fill(term);
        waitForComboboxOptionsSettled();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(resourceName.trim()))
                .first()
                .click();
        return this;
    }

    private Locator resourceAutocompleteTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Ресурс"))
                .locator("xpath=following::button[1]")
                .first();
    }

    public DefectFormPage fillAmount(String amount) {
        page.getByPlaceholder(AMOUNT_PLACEHOLDER).fill(amount);
        page.waitForTimeout(300);
        return this;
    }

    public DefectFormPage fillDescription(String description) {
        descriptionInput().fill(description);
        return this;
    }

    public boolean isSubmitDisabled() {
        return submitButton().isDisabled();
    }

    /** Number of selectable rows currently rendered in the relocation/production picker table. */
    public int getSourceTableRowCount() {
        Locator wrapper = page.locator("table tbody").first();
        return wrapper.count() > 0 ? wrapper.locator("tr").count() : 0;
    }

    public boolean isSourceTableEmptyStateVisible(String emptyStateText) {
        Locator empty = page.getByText(emptyStateText);
        return empty.count() > 0 && empty.isVisible();
    }

    /** True when the relocation/production picker table has a row containing the given text. */
    public boolean sourceTableContainsText(String text) {
        Locator rows = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(text));
        return rows.count() > 0;
    }

    /**
     * Selects a relocation row by invoice number in the picker table
     * ({@code RelocationTable} — column «Накладна»).
     */
    public DefectFormPage selectRelocationByInvoice(String invoiceNumber) {
        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(invoiceNumber))
                .first();
        row.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        row.click();
        return this;
    }

    /**
     * Waits for the client-side guard that blocks create when a produced relocation batch
     * is no longer present on stock (see tk-ui {@code DefectFormPage} {@code relocationBlocked}).
     */
    public DefectFormPage waitForUsedBatchBlockedAlert() {
        page.getByText(USED_BATCH_BLOCKED_TEXT)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isUsedBatchBlockedAlertVisible() {
        Locator alert = page.getByText(USED_BATCH_BLOCKED_TEXT);
        return alert.count() > 0 && alert.first().isVisible();
    }

    public DefectsPage submitAndWaitForList() {
        page.waitForResponse(
                r -> r.url().contains("/defects") && "POST".equals(r.request().method()),
                () -> submitButton().click());
        page.waitForURL(url -> url.contains("/defects") && !url.contains("/create"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new DefectsPage(page).waitForLoaded();
    }

    private Locator submitButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT_BUTTON_TEXT));
    }

    private Locator typeSelectTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(TYPE_LABEL))
                .locator("xpath=following::button[1]")
                .first();
    }

    private Locator descriptionInput() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(DESCRIPTION_LABEL))
                .locator("xpath=following::textarea[1]")
                .first();
    }
}
