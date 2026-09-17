package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.regex.Pattern;

/** Minimal page object for the production-order wizard used by the order E2E flow. */
public class ProductionOrderWizardPage extends BasePage {

    public static final String CREATE_PATH = "/production-orders/create";

    public ProductionOrderWizardPage(Page page) {
        super(page);
    }

    public ProductionOrderWizardPage openForOrder(long orderId) {
        navigateTo(ConfigProvider.getBaseUrl() + CREATE_PATH + "?orderId=" + orderId,
                "Нове виробниче замовлення");
        return waitForCreateLoaded();
    }

    public ProductionOrderWizardPage waitForCreateLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName("Нове виробниче замовлення"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    /** The order shortfall pre-fills target/output; submit it and return the new production-order id. */
    public long createPrefilledOrder() {
        Locator submit = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Створити замовлення").setExact(true));
        page.waitForCondition(submit::isEnabled,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        Response response = page.waitForResponse(
                r -> "POST".equals(r.request().method())
                        && r.url().matches(".*/production-orders(?:\\?.*)?$"),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                submit::click);
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("Create production order failed: HTTP " + response.status()
                    + ", body=" + response.text());
        }
        java.util.regex.Matcher matcher = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)")
                .matcher(response.text());
        if (!matcher.find()) {
            throw new IllegalStateException("Production order id is absent from response: " + response.text());
        }
        long id = Long.parseLong(matcher.group(1));
        page.getByRole(AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName("Виробниче замовлення №" + id))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return id;
    }
}
