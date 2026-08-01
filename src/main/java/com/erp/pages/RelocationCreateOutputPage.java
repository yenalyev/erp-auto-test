package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RelocationCreateOutputPage extends BasePage {

    public static final String PATH = "/relocation/create-output";
    private static final String TITLE = "Видача";
    private static final String SUBMIT = "Підтвердити";
    private static final String ADD_POSITION = "Додати позицію";
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

    public RelocationCreateOutputPage openWithOrderId(Long orderId) {
        String url = ConfigProvider.getBaseUrl() + PATH + "?orderId=" + orderId;
        navigateTo(url, "Видача за замовленням #" + orderId);
        return waitForOrderIssuanceLoaded(orderId);
    }

    /** Order issuance flow opens the same create-output form with {@code orderId} query. */
    public RelocationCreateOutputPage waitForOrderIssuanceLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(
                        java.util.regex.Pattern.compile("Видача за замовленням #\\d+|" + TITLE)))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationCreateOutputPage waitForOrderIssuanceLoaded(Long orderId) {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Видача за замовленням #" + orderId))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isOrderIssuanceHeadingVisible(Long orderId) {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Видача за замовленням #" + orderId))
                .isVisible();
    }

    /** When opened via {@code ?orderId=}, sender/recipient are read-only labels, not comboboxes. */
    public void assertFixedSenderRecipient(String expectedSenderName, String expectedRecipientName) {
        Locator senderBlock = page.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Хто відправляє"))
                .locator("xpath=following-sibling::p[1]");
        Locator recipientBlock = page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(RECIPIENT_LABEL))
                .locator("xpath=following-sibling::p[1]");
        senderBlock.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        recipientBlock.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        if (expectedSenderName != null && !senderBlock.innerText().contains(expectedSenderName)) {
            throw new AssertionError("Expected fixed sender «" + expectedSenderName
                    + "», got «" + senderBlock.innerText().trim() + "»");
        }
        if (expectedRecipientName != null && !recipientBlock.innerText().contains(expectedRecipientName)) {
            throw new AssertionError("Expected fixed recipient «" + expectedRecipientName
                    + "», got «" + recipientBlock.innerText().trim() + "»");
        }
        if (recipientInput().count() > 0 && recipientInput().isVisible()) {
            throw new AssertionError("Recipient combobox should be hidden for order issuance form");
        }
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
        return selectOutputResourceByName(0, resourceNamePart);
    }

    public RelocationCreateOutputPage selectOutputResourceByName(int rowIndex, String resourceNamePart) {
        String searchTerm = resourceNamePart.length() > 12
                ? resourceNamePart.substring(0, 12)
                : resourceNamePart;
        Locator resourceInput = page.getByPlaceholder(RESOURCE_PLACEHOLDER).nth(rowIndex);
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

    /** Open «Список продукції» combobox and collect visible option labels (optionally filtered). */
    public List<String> searchAndCollectResourceOptions(String searchTerm) {
        return searchAndCollectResourceOptions(0, searchTerm);
    }

    public List<String> searchAndCollectResourceOptions(int rowIndex, String searchTerm) {
        Locator resourceInput = page.getByPlaceholder(RESOURCE_PLACEHOLDER).nth(rowIndex);
        resourceInput.click();
        if (searchTerm != null && !searchTerm.isBlank()) {
            resourceInput.fill(searchTerm);
        }
        waitForComboboxOptionsSettled();
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

    public RelocationCreateOutputPage fillOutputQuantity(String amount) {
        return fillOutputQuantity(0, amount);
    }

    public RelocationCreateOutputPage fillOutputQuantity(int rowIndex, String amount) {
        page.getByPlaceholder(QUANTITY_PLACEHOLDER).nth(rowIndex).fill(amount);
        return this;
    }

    public String getOutputQuantityValue(int rowIndex) {
        return page.getByPlaceholder(QUANTITY_PLACEHOLDER).nth(rowIndex).inputValue();
    }

    public boolean isBundleBadgeVisible(String bundleName) {
        return bundleBadge(bundleName).count() > 0 && bundleBadge(bundleName).first().isVisible();
    }

    public RelocationCreateOutputPage clickBundleBadge(String bundleName) {
        bundleBadge(bundleName).first().click();
        return this;
    }

    public RelocationCreateOutputPage hoverBundleBadge(String bundleName) {
        bundleBadge(bundleName).first().hover();
        return this;
    }

    public RelocationCreateOutputPage waitForToast(String text) {
        page.getByText(text).waitFor();
        return this;
    }

    public boolean isToastVisible(String text) {
        return page.getByText(text).isVisible();
    }

    public String getSelectedResourceValue(int rowIndex) {
        return page.getByPlaceholder(RESOURCE_PLACEHOLDER).nth(rowIndex).inputValue();
    }

    private Locator bundleBadge(String bundleName) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(bundleName));
    }

    /** Visible row numbers in «Список продукції» ({@code 1.}, {@code 2.}, …). */
    public int productRowCount() {
        return productRowNumberSpans().count();
    }

    public String getProductRowNumberText(int zeroBasedIndex) {
        return productRowNumberSpans().nth(zeroBasedIndex).innerText().trim();
    }

    public RelocationCreateOutputPage clickAddPosition() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_POSITION)).click();
        return this;
    }

    public boolean isAddPositionEnabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_POSITION)).isEnabled();
    }

    public RelocationCreateOutputPage waitForAvailableBatchesToggle(int rowIndex) {
        availableBatchesToggle(rowIndex).waitFor();
        return this;
    }

    public String getAvailableBatchesToggleText(int rowIndex) {
        return availableBatchesToggle(rowIndex).innerText().trim();
    }

    public RelocationCreateOutputPage toggleAvailableBatches(int rowIndex) {
        availableBatchesToggle(rowIndex).click();
        return this;
    }

    /** True when chevron is not rotated (default expanded state). */
    public boolean isAvailableBatchesExpanded(int rowIndex) {
        String cls = availableBatchesToggle(rowIndex).locator("svg").first().getAttribute("class");
        return cls == null || !cls.contains("-rotate-90");
    }

    /**
     * Чипи доступних партій у розгорнутому блоці (не враховує кнопку «Доступні партії»).
     * Коли згорнуто — контейнер відсутній у DOM → 0.
     */
    public int visibleAvailableBatchChipCount(int rowIndex) {
        Locator chips = availableBatchChipsContainer(rowIndex);
        if (chips.count() == 0) {
            return 0;
        }
        Locator first = chips.first();
        if (!first.isVisible()) {
            return 0;
        }
        return first.locator("button").count();
    }

    public RelocationCreateOutputPage waitForAvailableBatchChips(int rowIndex) {
        page.waitForCondition(() -> visibleAvailableBatchChipCount(rowIndex) > 0);
        return this;
    }

    private Locator productRowNumberSpans() {
        return page.locator("span.text-sm.text-gray-400")
                .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\d+\\.$")));
    }

    private Locator availableBatchesToggle(int rowIndex) {
        return page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(java.util.regex.Pattern.compile("Доступні партії \\(\\d+\\)")))
                .nth(rowIndex);
    }

    private Locator availableBatchChipsContainer(int rowIndex) {
        return availableBatchesToggle(rowIndex)
                .locator("xpath=following-sibling::div[contains(@class,'flex-wrap')]");
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
