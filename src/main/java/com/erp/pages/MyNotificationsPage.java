package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

/** Personal notification settings opened from the sidebar user menu. */
public class MyNotificationsPage extends BasePage {

    public static final String MENU_ITEM = "Мої сповіщення";

    public MyNotificationsPage(Page page) {
        super(page);
    }

    public MyNotificationsPage waitForLoaded() {
        page.getByText(MENU_ITEM, new Page.GetByTextOptions().setExact(true)).last()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isNotificationVisible(String description) {
        Locator text = page.getByText(description, new Page.GetByTextOptions().setExact(true));
        try {
            text.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean isNotificationEnabled(String description) {
        Locator control = notificationControl(description);
        String ariaChecked = control.getAttribute("aria-checked");
        if (ariaChecked != null) {
            return Boolean.parseBoolean(ariaChecked);
        }
        return control.isChecked();
    }

    public MyNotificationsPage setNotificationEnabled(String description, boolean enabled) {
        Locator control = notificationControl(description);
        if (isNotificationEnabled(description) != enabled) {
            control.click();
        }
        return this;
    }

    private Locator notificationControl(String description) {
        Locator text = page.getByText(description, new Page.GetByTextOptions().setExact(true));
        Locator container = text.locator(
                "xpath=ancestor::*[.//*[@role='switch'] or .//input[@type='checkbox']][1]");
        Locator roleSwitch = container.getByRole(AriaRole.SWITCH);
        if (roleSwitch.count() > 0) {
            return roleSwitch.first();
        }
        Locator checkbox = container.locator("input[type='checkbox']");
        if (checkbox.count() > 0) {
            return checkbox.first();
        }
        throw new IllegalStateException("No subscription control for «" + description + "»");
    }
}
