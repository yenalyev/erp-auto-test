package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Header notification bell ({@code NotificationBell} in tk-ui): popover + «Перейти».
 */
@Slf4j
public class NotificationBellPage extends BasePage {

    private static final String GO_BUTTON = "Перейти";
    private static final String BELL_NAME_PREFIX = "Сповіщення";

    public NotificationBellPage(Page page) {
        super(page);
    }

    public NotificationBellPage openBell() {
        bellButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        bellButton().click();
        page.getByText("Сповіщення", new Page.GetByTextOptions().setExact(true))
                .first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean hasNotificationContaining(String text) {
        return notificationItem(text).count() > 0 && notificationItem(text).first().isVisible();
    }

    public NotificationBellPage clickGoOnNotificationContaining(String text) {
        Locator item = notificationItem(text).first();
        item.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        item.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(GO_BUTTON)).click();
        return this;
    }

    public NotificationBellPage clickFirstGo() {
        Locator go = popover().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(GO_BUTTON));
        go.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        go.first().click();
        return this;
    }

    private Locator bellButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(BELL_NAME_PREFIX));
    }

    /** Radix portal — must not match sidebar {@code li} that reuse the same storage names. */
    private Locator popover() {
        return page.locator("[data-radix-popper-content-wrapper]")
                .filter(new Locator.FilterOptions().setHasText("Сповіщення"));
    }

    private Locator notificationItem(String text) {
        return popover().locator("li").filter(new Locator.FilterOptions().setHasText(text));
    }
}
