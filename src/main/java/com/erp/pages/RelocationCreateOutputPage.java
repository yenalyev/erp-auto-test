package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RelocationCreateOutputPage extends BasePage {

    public static final String PATH = "/relocation/create-output";
    private static final String TITLE = "Видача";
    private static final String SUBMIT = "Підтвердити";
    private static final String RECIPIENT_LABEL = "Кому відправляю";
    private static final String RECIPIENT_PLACEHOLDER = "Оберіть склад...";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";
    /** Дані для накладної — «Видав» / «Звання (хто видав)». */
    public static final String DEFAULT_ISSUER_NAME = "тестовий користувач";
    public static final String DEFAULT_ISSUER_RANK = "тестова посада";

    public RelocationCreateOutputPage(Page page) {
        super(page);
    }

    public RelocationCreateOutputPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor();
        page.getByText(RECIPIENT_LABEL)
                .waitFor();
        recipientInput().waitFor();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT))
                .waitFor();
        return this;
    }

    public boolean isSubmitVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).isVisible();
    }

    public boolean isRecipientDropdownEnabled() {
        return recipientInput().isEnabled();
    }

    public RelocationCreateOutputPage openRecipientDropdown() {
        recipientInput().click();
        waitForRecipientOptionsSettled();
        return this;
    }

    public List<String> collectRecipientOptionLabels() {
        waitForRecipientOptionsSettled();
        Locator items = page.locator(COMBOBOX_ITEM_SELECTOR);
        int count = items.count();
        List<String> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String text = items.nth(i).innerText().trim();
            if (!text.isBlank()) {
                labels.add(text);
            }
        }
        return labels;
    }

    public List<String> searchAndCollectRecipientOptions(String searchTerm) {
        recipientInput().click();
        recipientInput().fill(searchTerm);
        waitForRecipientOptionsSettled();
        return collectRecipientOptionLabels();
    }

    public RelocationCreateOutputPage selectRecipientByLabel(String label) {
        openRecipientDropdown();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(label))
                .first()
                .click();
        return this;
    }

    public String getSelectedRecipientLabel() {
        return recipientInput().inputValue();
    }

    public RelocationCreateOutputPage fillDescription(String description) {
        page.locator("#description").fill(description);
        return this;
    }

    public RelocationCreateOutputPage selectOutputResourceByName(String resourceNamePart) {
        String searchTerm = resourceNamePart.length() > 12
                ? resourceNamePart.substring(0, 12)
                : resourceNamePart;
        Locator resourceInput = page.getByPlaceholder(RESOURCE_PLACEHOLDER);
        resourceInput.click();
        resourceInput.fill(searchTerm);
        waitForComboboxOptionsSettled();
        Locator matching = page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(resourceNamePart));
        if (matching.count() == 0) {
            matching = page.locator(COMBOBOX_ITEM_SELECTOR)
                    .filter(new Locator.FilterOptions().setHasText(searchTerm));
        }
        matching.first().click();
        return this;
    }

    public RelocationCreateOutputPage fillOutputQuantity(String amount) {
        page.getByPlaceholder(QUANTITY_PLACEHOLDER).fill(amount);
        return this;
    }

    public RelocationCreateOutputPage fillInvoiceIssuer(String name, String rank) {
        Locator issuer = issuerNameInput();
        Locator rankField = issuerRankInput();
        issuer.scrollIntoViewIfNeeded();
        issuer.fill(name);
        rankField.scrollIntoViewIfNeeded();
        rankField.fill(rank);
        return this;
    }

    public RelocationCreateOutputPage fillInvoiceIssuerDefaults() {
        return fillInvoiceIssuer(DEFAULT_ISSUER_NAME, DEFAULT_ISSUER_RANK);
    }

    public RelocationPage confirmSend() {
        submitSendExpectSuccess();
        page.waitForURL("**/relocations**", new Page.WaitForURLOptions().setTimeout(90_000));
        return new RelocationPage(page);
    }

    public void submitSendExpectSuccess() {
        Response response = page.waitForResponse(
                r -> r.url().contains("/relocations/send")
                        && "POST".equals(r.request().method()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click());
        if (response.status() != 200) {
            attachScreenshot("POST /relocations/send failed — status " + response.status());
            throw new IllegalStateException("POST /relocations/send failed with status " + response.status());
        }
    }

    private void waitForRecipientOptionsSettled() {
        waitForComboboxOptionsSettled();
    }

    private Locator recipientInput() {
        return page.getByPlaceholder(RECIPIENT_PLACEHOLDER);
    }

    private Locator invoicePartiesSection() {
        return page.locator("div.rounded-lg.border")
                .filter(new Locator.FilterOptions().setHasText("Дані для накладної"));
    }

    private Locator issuerNameInput() {
        return invoicePartiesSection()
                .locator("label[data-slot='label'][data-required='true']")
                .filter(new Locator.FilterOptions().setHasText("Видав"))
                .locator("..")
                .locator("input[data-slot='input']");
    }

    private Locator issuerRankInput() {
        return invoicePartiesSection()
                .locator("label[data-slot='label']")
                .filter(new Locator.FilterOptions().setHasText("Звання (хто видав)"))
                .locator("..")
                .locator("input[data-slot='input']");
    }
}
