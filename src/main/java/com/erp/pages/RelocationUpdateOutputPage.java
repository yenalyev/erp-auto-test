package com.erp.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationUpdateOutputPage extends BasePage {

    private static final String TITLE = "Редагування видачі";
    private static final String SUBMIT = "Підтвердити";

    public RelocationUpdateOutputPage(Page page) {
        super(page);
    }

    public RelocationUpdateOutputPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor();
        return this;
    }

    public RelocationPage submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click();
        return new RelocationPage(page);
    }
}
