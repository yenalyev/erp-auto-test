package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

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

    private void waitForRecipientOptionsSettled() {
        page.waitForCondition(() -> {
            Locator items = page.locator(COMBOBOX_ITEM_SELECTOR);
            if (items.count() > 0) {
                return true;
            }
            Locator empty = page.getByText("Не знайдено");
            return empty.count() > 0 && empty.isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator recipientInput() {
        return page.getByPlaceholder(RECIPIENT_PLACEHOLDER);
    }
}
