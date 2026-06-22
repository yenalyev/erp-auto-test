package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalPlanWizardPage extends BasePage {

    private static final String WIZARD_HEADING = "Декомпозиція виробничого плану";
    private static final String TAB_1 = "1. Заплановано";
    private static final String TAB_3 = "3. Потрібно ресурсів";
    private static final String TAB_4 = "4. Плани на локації";

    public GlobalPlanWizardPage(Page page) {
        super(page);
    }

    public GlobalPlanWizardPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(WIZARD_HEADING))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isWizardHeadingVisible() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(WIZARD_HEADING))
                .isVisible();
    }

    public boolean isTabVisible(String tabLabel) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabLabel)).isVisible();
    }

    public boolean isTabDisabled(String tabLabel) {
        Locator tab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabLabel));
        if (tab.count() == 0) {
            return false;
        }
        String disabled = tab.first().getAttribute("disabled");
        String dataDisabled = tab.first().getAttribute("data-disabled");
        return "true".equals(disabled) || tab.first().isDisabled() || "true".equals(dataDisabled);
    }

    public boolean areLateTabsDisabledOnFreshCreate() {
        return isTabDisabled(TAB_3) && isTabDisabled(TAB_4);
    }

    public boolean isFirstTabVisible() {
        return isTabVisible(TAB_1);
    }
}
