package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalPlansPage extends BasePage {

    private static final String PATH = "/global-plans";
    private static final String LIST_HEADING = "Глобальні плани";
    private static final String CREATE_BUTTON_TEXT = "Створити план";
    private static final String DELETE_BUTTON_TITLE = "Видалити";
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

    public boolean isPlanVisibleInList(String descriptionFragment) {
        return planRow(descriptionFragment).count() > 0;
    }

    @Step("Список: дочекатися появи плану «{descriptionFragment}»")
    public GlobalPlansPage waitForPlanVisible(String descriptionFragment) {
        Locator spinner = page.locator("i.fa-spinner.fa-spin");
        if (spinner.count() > 0) {
            spinner.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
        planRow(descriptionFragment).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Список: дочекатися відсутності плану «{descriptionFragment}»")
    public GlobalPlansPage waitForPlanAbsent(String descriptionFragment) {
        Locator row = planRow(descriptionFragment);
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.DETACHED)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Список: видалити план «{descriptionFragment}»")
    public GlobalPlansPage deletePlanAndConfirm(String descriptionFragment) {
        log.info("Global plans list — delete plan matching: {}", descriptionFragment);
        page.onceDialog(Dialog::accept);
        planRow(descriptionFragment)
                .getByTitle(DELETE_BUTTON_TITLE)
                .click();
        page.waitForTimeout(500);
        return this;
    }

    private Locator planRow(String descriptionFragment) {
        return page.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(descriptionFragment));
    }
}
