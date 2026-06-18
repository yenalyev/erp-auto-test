package com.erp.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationCreateInputPage extends BasePage {

    public static final String PATH = "/relocation/create-input";
    private static final String TITLE = "Отримано";
    private static final String SUBMIT = "Підтвердити";

    public RelocationCreateInputPage(Page page) {
        super(page);
    }

    public RelocationCreateInputPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor();
        return this;
    }

    public RelocationCreateInputPage fillInvoiceNumber(String invoiceNumber) {
        page.getByLabel("№ накладної").fill(invoiceNumber);
        return this;
    }

    public RelocationCreateInputPage fillDescription(String description) {
        page.getByLabel("Примітки").fill(description);
        return this;
    }

    public RelocationPage submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click();
        return new RelocationPage(page);
    }
}
