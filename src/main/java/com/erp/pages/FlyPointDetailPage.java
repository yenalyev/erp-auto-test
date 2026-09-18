package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** Детальна сторінка точки вильоту {@code /fly-points/{id}}. */
public class FlyPointDetailPage extends BasePage {

    public static final String STOCKS_TAB = "Залишки";
    public static final String INCOMING_TAB = "Надходження";
    public static final String USAGE_TAB = "Використання";

    public FlyPointDetailPage(Page page) {
        super(page);
    }

    public FlyPointDetailPage waitForLoaded() {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                        .setName("Точка вильоту")
                        .setExact(false))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        tab(STOCKS_TAB).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public String heading() {
        return page.locator("h1").first().innerText().trim();
    }

    public FlyPointDetailPage waitForHeading(String flyPointName) {
        String expected = "Точка вильоту " + flyPointName;
        page.waitForCondition(() -> expected.equals(heading()),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isTabVisible(String label) {
        Locator tab = tab(label);
        return tab.count() > 0 && tab.first().isVisible();
    }

    public boolean isTabSelected(String label) {
        Locator tab = tab(label).first();
        return "active".equalsIgnoreCase(tab.getAttribute("data-state"))
                || "true".equalsIgnoreCase(tab.getAttribute("aria-selected"));
    }

    public FlyPointDetailPage openTab(String label) {
        tab(label).click();
        return this;
    }

    public FlyPointDetailPage openStocks(long flyPointId) {
        tab(STOCKS_TAB).click();
        page.waitForURL(url -> url.contains("/inventory?storageId=" + flyPointId),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    private Locator tab(String label) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions()
                .setName(label)
                .setExact(true)).first();
    }
}
