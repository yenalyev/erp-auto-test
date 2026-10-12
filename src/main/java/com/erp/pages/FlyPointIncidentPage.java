package com.erp.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** Storage-incident form opened from the fly-point archiving workflow. */
public class FlyPointIncidentPage extends BasePage {

    private static final String HEADING = "Надзвичайна подія на точці вильоту";

    public FlyPointIncidentPage(Page page) {
        super(page);
    }

    public FlyPointIncidentPage waitForLoaded() {
        page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName(HEADING)).waitFor();
        page.getByRole(AriaRole.RADIO,
                new Page.GetByRoleOptions().setName("Втрачено всі ресурси")).waitFor();
        return this;
    }

    public FlyPointIncidentPage selectFullStock(String resourceName) {
        page.getByRole(AriaRole.CELL,
                new Page.GetByRoleOptions().setName(resourceName).setExact(true)).waitFor();
        page.getByRole(AriaRole.RADIO,
                new Page.GetByRoleOptions().setName("Втрачено всі ресурси")).check();
        return this;
    }

    public FlyPointIncidentPage fillDescription(String description) {
        page.getByPlaceholder("Додайте короткий опис події").fill(description);
        return this;
    }

    public void submitAndWaitForReturn() {
        String formUrl = page.url();
        page.waitForResponse(
                response -> response.url().contains("/incidents/storages")
                        && "POST".equals(response.request().method())
                        && response.ok(),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Зберегти")).click());
        page.waitForURL(url -> !url.equals(formUrl),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
    }
}
