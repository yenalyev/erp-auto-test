package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class RelocationCreateIncidentPage extends BasePage {

    private static final String HEADING = "Надзвичайна подія підчас переміщення";
    private static final String DESCRIPTION_PLACEHOLDER = "Добавте короткий опис події";
    private static final String SAVE_BUTTON = "Зберегти";

    public RelocationCreateIncidentPage(Page page) {
        super(page);
    }

    public RelocationCreateIncidentPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationCreateIncidentPage fillDescription(String description) {
        page.getByPlaceholder(DESCRIPTION_PLACEHOLDER).fill(description);
        return this;
    }

    public RelocationPage saveAndReturnToJournal() {
        page.waitForResponse(
                response -> response.url().contains("/incidents/relocations") && response.request().method().equals("POST")
                        && response.ok(),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_BUTTON)).click());
        page.waitForURL(
                url -> url.contains("/relocations") && !url.contains("create-incident"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new RelocationPage(page).waitForLoaded();
    }
}
