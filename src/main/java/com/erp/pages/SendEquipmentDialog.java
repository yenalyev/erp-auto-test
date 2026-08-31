package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendEquipmentDialog extends BasePage {

    private static final String TITLE = "Передати обладнання";
    private static final String RECIPIENT_PLACEHOLDER = "Оберіть локацію...";
    private static final String SUBMIT = "Передати";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

    public SendEquipmentDialog(Page page) {
        super(page);
    }

    public SendEquipmentDialog waitForOpen() {
        dialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        dialog().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        recipientInput().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public SendEquipmentDialog selectRecipient(String storageName) {
        Locator input = recipientInput();
        input.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        input.click();
        waitForRecipientOptionsLoaded();
        input.fill(storageName);
        waitForComboboxOptionsSettled();
        pickRecipientOption(storageName);
        dismissComboboxOverlay();
        // «Видав» is required and is only prefilled from the Keycloak profile name, which test
        // users do not have — fill it before gating on «Передати», otherwise the button can never
        // become enabled and the wait always times out.
        ensureIssuerFilled();
        page.waitForCondition(this::isSubmitEnabled,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public SendEquipmentDialog ensureIssuerFilled() {
        Locator issuer = dialog().locator("input[name='sendingPersonName']");
        if (issuer.count() > 0 && issuer.inputValue().isBlank()) {
            issuer.fill("Test");
        }
        return this;
    }

    public EquipmentListPage confirmSend() {
        dismissComboboxOverlay();
        ensureIssuerFilled();
        Locator submit = dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(SUBMIT))
                .last();
        submit.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForCondition(submit::isEnabled, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        var response = page.waitForResponse(
                r -> r.url().contains("/relocations/equipment/send")
                        && "POST".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> submit.click(new Locator.ClickOptions().setForce(true)));
        if (response.status() < 200 || response.status() >= 300) {
            attachScreenshot("POST equipment send failed — status " + response.status());
            throw new IllegalStateException(
                    "POST /relocations/equipment/send failed with status " + response.status());
        }
        dialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return new EquipmentListPage(page).waitForTableSettled();
    }

    /**
     * Recipient combobox: placeholder may sit outside {@code role=dialog} (portaled popup /
     * Base UI ComboboxInput). Prefer the input-group control, then placeholder in or out of dialog.
     */
    private Locator recipientInput() {
        return dialog().locator("[data-slot='input-group-control']")
                .or(dialog().getByPlaceholder(RECIPIENT_PLACEHOLDER))
                .or(page.getByPlaceholder(RECIPIENT_PLACEHOLDER))
                .first();
    }

    /** Wait until the recipient list has items — ignore empty «Не знайдено» while names are loading. */
    private void waitForRecipientOptionsLoaded() {
        page.waitForCondition(this::recipientListOpen,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private void pickRecipientOption(String storageName) {
        Locator inPopup = page.locator("[data-slot='combobox-content']")
                .locator("[data-slot='combobox-item']")
                .filter(new Locator.FilterOptions().setHasText(storageName));
        try {
            inPopup.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
            inPopup.first().click(new Locator.ClickOptions().setForce(true));
        } catch (Exception e) {
            log.debug("Recipient option not in popup: {}", e.getMessage());
            Locator option = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(storageName));
            if (option.count() > 0) {
                option.first().click(new Locator.ClickOptions().setForce(true));
            } else {
                page.keyboard().press("Enter");
            }
        }
        if (recipientListOpen()) {
            page.keyboard().press("Enter");
        }
    }

    /** Options still on screen means the click did not commit a selection. */
    private boolean recipientListOpen() {
        return page.locator(COMBOBOX_ITEM_SELECTOR).count() > 0
                || page.getByRole(AriaRole.OPTION).count() > 0;
    }

    private boolean isSubmitEnabled() {
        Locator submit = dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(SUBMIT))
                .last();
        return submit.count() > 0 && submit.isEnabled();
    }

    private Locator dialog() {
        return page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))));
    }
}
