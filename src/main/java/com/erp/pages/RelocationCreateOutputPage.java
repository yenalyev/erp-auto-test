package com.erp.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationCreateOutputPage extends BasePage {

    private static final String SUBMIT = "Підтвердити";

    public RelocationCreateOutputPage(Page page) {
        super(page);
    }

    public RelocationCreateOutputPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT))
                .waitFor();
        return this;
    }

    public boolean isSubmitVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).isVisible();
    }
}
