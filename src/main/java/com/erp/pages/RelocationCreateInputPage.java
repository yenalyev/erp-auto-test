package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
public class RelocationCreateInputPage extends BasePage {

    public static final String PATH = "/relocation/create-input";
    private static final String TITLE = "Отримано";
    private static final String SUBMIT = "Підтвердити";
    private static final String ADD_POSITION = "Додати позицію";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String RESOURCE_SEARCH_PLACEHOLDER = "Пошук...";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";
    private static final String ACCOUNTING_SEARCH_PLACEHOLDER = "Пошук або нова назва...";

    public RelocationCreateInputPage(Page page) {
        super(page);
    }

    public RelocationCreateInputPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor();
        return this;
    }

    public RelocationCreateInputPage fillInvoiceNumber(String invoiceNumber) {
        page.locator("div.space-y-2")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("№ Накладної"))
                .locator("input")
                .fill(invoiceNumber);
        return this;
    }

    public RelocationCreateInputPage fillDescription(String description) {
        page.getByLabel("Примітки").fill(description);
        return this;
    }

    public RelocationCreateInputPage selectResourceByName(String resourceNamePart) {
        return selectResourceByName(0, resourceNamePart);
    }

    public RelocationCreateInputPage selectResourceByName(int rowIndex, String resourceNamePart) {
        String normalizedName = resourceNamePart.trim();
        String searchTerm = normalizedName.length() > 12
                ? normalizedName.substring(0, 12)
                : normalizedName;
        Locator resourceTrigger = productRow(rowIndex)
                .getByRole(AriaRole.COMBOBOX)
                .first();
        resourceTrigger.click();
        page.locator("input[placeholder='" + RESOURCE_SEARCH_PLACEHOLDER + "']:visible")
                .last()
                .fill(searchTerm);
        waitForComboboxOptionsSettled();
        Locator matching = page.getByRole(AriaRole.OPTION)
                .filter(new Locator.FilterOptions().setHasText(normalizedName));
        matching.first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        matching.first().click();
        return this;
    }

    public RelocationCreateInputPage fillQuantity(int rowIndex, String amount) {
        page.getByPlaceholder(QUANTITY_PLACEHOLDER).nth(rowIndex).fill(amount);
        return this;
    }

    public RelocationCreateInputPage fillPaidAmount(int rowIndex, String amount) {
        paidAmountInput(rowIndex).fill(amount);
        return this;
    }

    public boolean isPaidAmountVisible(int rowIndex) {
        return paidAmountInput(rowIndex).isVisible();
    }

    public String paidAmountMin(int rowIndex) {
        return paidAmountInput(rowIndex).getAttribute("min");
    }

    public String paidAmountStep(int rowIndex) {
        return paidAmountInput(rowIndex).getAttribute("step");
    }

    public boolean isAccountingSelectVisible(int rowIndex) {
        return accountingSelect(rowIndex).isVisible();
    }

    public RelocationCreateInputPage selectAccountingName(int rowIndex, String accountingName) {
        accountingSelect(rowIndex).click();
        Locator search = page.getByPlaceholder(ACCOUNTING_SEARCH_PLACEHOLDER);
        search.fill(accountingName);
        waitForComboboxOptionsSettled();
        page.getByRole(AriaRole.OPTION)
                .filter(new Locator.FilterOptions().setHasText(accountingName))
                .first()
                .click();
        return this;
    }

    public RelocationCreateInputPage clickAddPosition() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_POSITION)).click();
        return this;
    }

    public int productRowCount() {
        return productRowNumberSpans().count();
    }

    public boolean isSubmitEnabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).isEnabled();
    }

    public boolean isDuplicateAccountingErrorVisible() {
        Locator error = page.getByText(Pattern.compile(
                "Ця бухгалтерська назва вже є в ряд(?:ку|ках).*Оберіть іншу\\."));
        return error.count() > 0 && error.first().isVisible();
    }

    public boolean isTotalCostVisible() {
        Locator total = page.getByText(Pattern.compile("Загальна вартість", Pattern.CASE_INSENSITIVE));
        return total.count() > 0 && total.first().isVisible();
    }

    public String totalCostText() {
        return page.getByText(Pattern.compile("Загальна вартість", Pattern.CASE_INSENSITIVE))
                .first()
                .innerText()
                .trim();
    }

    public RelocationPage submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click();
        return new RelocationPage(page);
    }

    private Locator paidAmountInput(int rowIndex) {
        return page.getByTestId("relocation-item-" + rowIndex + "-paid-amount");
    }

    private Locator accountingSelect(int rowIndex) {
        return page.getByTestId("relocation-item-" + rowIndex + "-acc-resource");
    }

    private Locator productRow(int rowIndex) {
        return productRowNumberSpans().nth(rowIndex).locator("xpath=parent::div");
    }

    private Locator productRowNumberSpans() {
        return page.locator("span.text-sm.text-gray-400")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\d+\\.$")));
    }
}
