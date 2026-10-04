package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** Archive workflow at {@code /fly-points/close/{id}}. */
public class FlyPointClosePage extends BasePage {

    public FlyPointClosePage(Page page) {
        super(page);
    }

    public FlyPointClosePage open(long id) {
        navigateTo(ConfigProvider.getBaseUrl() + "/fly-points/close/" + id,
                "Архівування точки вильоту");
        page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Архівування точки вильоту"))
                .waitFor();
        return waitForReady();
    }

    public FlyPointClosePage waitForReady() {
        page.getByTestId("fly-point-close-cancel").waitFor();
        return this;
    }

    public String content() {
        return page.locator("main").count() > 0
                ? page.locator("main").innerText()
                : page.locator("body").innerText();
    }

    public boolean hasAction(String testId) {
        return page.getByTestId(testId).count() > 0
                && page.getByTestId(testId).isVisible();
    }

    public FlyPointClosePage cancel() {
        page.getByTestId("fly-point-close-cancel").click();
        return this;
    }

    public FlyPointClosePage confirmArchive() {
        page.getByTestId("fly-point-close-confirm-archive").click();
        page.waitForURL(url -> url.endsWith("/fly-points"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public FlyPointClosePage openCrews() {
        page.getByTestId("fly-point-close-move-crews").click();
        page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Екіпажі точки").setExact(false))
                .waitFor();
        return this;
    }

    public FlyPointClosePage moveCrew(long crewId, String destinationName) {
        page.getByTestId("fly-point-close-crew-" + crewId + "-parent-select").click();
        page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(destinationName).setExact(true)).click();
        page.getByTestId("fly-point-close-crew-" + crewId + "-move").click();
        page.getByTestId("fly-point-close-crew-" + crewId)
                .getByText("Переміщено").waitFor();
        return this;
    }

    public FlyPointClosePage doneWithCrews() {
        page.getByTestId("fly-point-close-crews-done").click();
        page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Архівування точки вильоту"))
                .waitFor();
        return this;
    }

    public FlyPointClosePage openRelocation() {
        page.getByTestId("fly-point-close-relocate-resources").click();
        page.waitForURL(url -> url.contains("/relocation/create-output-fly-point"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public FlyPointClosePage openIncident() {
        page.getByTestId("fly-point-close-create-incident").click();
        page.waitForURL(url -> url.contains("/inventory/create-incident/"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }
}
