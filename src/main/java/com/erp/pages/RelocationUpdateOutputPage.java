package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationUpdateOutputPage extends BasePage {

    private static final String TITLE = "Редагування видачі";
    private static final String SUBMIT = "Підтвердити";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";

    public RelocationUpdateOutputPage(Page page) {
        super(page);
    }

    public RelocationUpdateOutputPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        confirmButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationUpdateOutputPage fillDescription(String description) {
        Locator notes = page.locator("#description");
        notes.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        notes.fill(description);
        return this;
    }

    /**
     * Submit equipment send edit. UI sends the current optimistic-lock {@code version}
     * from the form payload ({@code PUT /relocations/equipment/{id}/send}).
     */
    public RelocationPage submitVersionedEquipmentSend() {
        ensureIssuerFilled();
        var response = page.waitForResponse(
                r -> r.url().contains("/relocations/equipment/")
                        && r.url().contains("/send")
                        && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> confirmButton().click());
        if (response.status() < 200 || response.status() >= 300) {
            attachScreenshot("PUT equipment send edit failed — status " + response.status());
            throw new IllegalStateException(
                    "PUT /relocations/equipment/{id}/send failed with status " + response.status());
        }
        return new RelocationPage(page).waitForLoaded();
    }

    /**
     * «Видав» is a {@code required} input that the form only prefills from the Keycloak profile name,
     * which test users lack. Left empty, native constraint validation swallows the click and no request
     * is ever sent.
     */
    public RelocationUpdateOutputPage ensureIssuerFilled() {
        Locator issuer = page.locator("input[name='sendingPersonName']");
        if (issuer.count() > 0 && issuer.first().inputValue().isBlank()) {
            issuer.first().fill("Test");
        }
        return this;
    }

    public RelocationUpdateOutputPage waitForBookedLimitHint() {
        page.getByText("заброньовано")
                .first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Fills the first enabled quantity field (row total, or a batch amount when the total is locked).
     */
    public RelocationUpdateOutputPage fillProductAmount(double amount) {
        Locator qty = page.getByPlaceholder(QUANTITY_PLACEHOLDER);
        qty.first().waitFor();
        Locator target = qty.first();
        for (int i = 0; i < qty.count(); i++) {
            if (qty.nth(i).isEnabled()) {
                target = qty.nth(i);
                break;
            }
        }
        target.fill(String.valueOf(amount));
        target.press("Tab");
        page.waitForCondition(
                () -> confirmButton().isDisabled() || showsAmountOverAvailable(),
                new Page.WaitForConditionOptions().setTimeout(5_000));
        return this;
    }

    public boolean hasBookedUnavailableHint() {
        return page.getByText("заброньовано").count() > 0;
    }

    public boolean showsAmountOverAvailable() {
        return page.getByText("Макс:").count() > 0
                || page.getByText("разом").count() > 0
                || page.locator("input.border-red-500, input.text-red-600").count() > 0;
    }

    public boolean isConfirmDisabled() {
        return confirmButton().isDisabled();
    }

    public RelocationPage submit() {
        confirmButton().click();
        return new RelocationPage(page);
    }

    private Locator confirmButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT));
    }
}
