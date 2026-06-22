package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalPlansPage extends BasePage {

    private static final String PATH = "/global-plans";
    private static final String LIST_HEADING = "Глобальні плани";
    private static final String CREATE_BUTTON_TEXT = "Створити план";
    private static final String SIDEBAR_LINK_TEXT = "Глобальні плани";

    public GlobalPlansPage(Page page) {
        super(page);
    }

    public GlobalPlansPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        navigateTo(url, "Глобальні плани (/global-plans)");
        return waitForLoaded();
    }

    public GlobalPlansPage openFromSidebar() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(SIDEBAR_LINK_TEXT))
                .first()
                .click();
        return waitForLoaded();
    }

    public GlobalPlansPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(LIST_HEADING))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public GlobalPlanWizardPage clickCreatePlan() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT))
                .click();
        return new GlobalPlanWizardPage(page).waitForLoaded();
    }

    public boolean isListHeadingVisible() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(LIST_HEADING))
                .isVisible();
    }
}
