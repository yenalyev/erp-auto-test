package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for stock-threshold settings.
 * URL: /alerts/{storageId}
 */
@Slf4j
public class StorageAlertsPage extends BasePage {

    public static final String PATH_PREFIX = "/alerts/";
    public static final String CONFIGURE_BUTTON = "Налаштувати сповіщення";

    public StorageAlertsPage(Page page) {
        super(page);
    }

    public StorageAlertsPage open(long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH_PREFIX + storageId;
        log.info("Opening storage alerts page: {}", url);
        waitForAlertsResponse(storageId, () -> navigateTo(url, "Сповіщення по залишках (/alerts/" + storageId + ")"));
        return waitForLoaded();
    }

    public StorageAlertsPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        waitForLoadingFinished();
        return this;
    }

    public boolean isOnAlertsPath(long storageId) {
        String url = page.url();
        return url.contains(PATH_PREFIX + storageId);
    }

    /**
     * True when the configured resource is visible on /alerts/{id}.
     * Uses a tolerant wait so an empty UNIT page (the known defect) becomes an assertion, not a timeout.
     */
    public boolean showsResource(String resourceName) {
        String needle = rowNeedle(resourceName);
        waitForConditionTolerant(
                () -> visibleText(needle),
                "resource «" + needle + "» on /alerts");
        return visibleText(needle);
    }

    public boolean showsThreshold(String resourceName, double limit) {
        String expected = stripTrailingZero(limit);
        Locator row = resourceBlock(resourceName);
        if (row.count() == 0) {
            return page.getByText(expected).count() > 0 && page.getByText(expected).first().isVisible();
        }
        Locator input = row.locator("input[type='number']");
        if (input.count() > 0) {
            String value = input.first().inputValue();
            return value != null && (value.equals(expected) || value.equals(String.valueOf((int) limit)));
        }
        String text = row.innerText();
        return text.contains(expected) || text.contains(String.valueOf((int) limit));
    }

    public boolean isEmptyOfResources(String resourceName) {
        return !showsResource(resourceName);
    }

    private void waitForAlertsResponse(long storageId, Runnable action) {
        waitForResponseTolerant(
                response -> {
                    String url = response.url();
                    return url.contains("/alerts/storage/" + storageId) || url.contains("/api/v1/alerts");
                },
                action,
                "GET alerts for storage " + storageId);
    }

    private void waitForLoadingFinished() {
        Locator loading = page.getByText("Завантаження...");
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
    }

    private Locator resourceBlock(String resourceName) {
        String needle = rowNeedle(resourceName);
        Locator row = page.locator("tr").filter(new Locator.FilterOptions().setHasText(needle));
        if (row.count() > 0) {
            return row.first();
        }
        return page.locator("body").filter(new Locator.FilterOptions().setHasText(needle));
    }

    private boolean visibleText(String needle) {
        Locator byRole = page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName(needle));
        if (byRole.count() > 0 && byRole.first().isVisible()) {
            return true;
        }
        Locator text = page.getByText(needle);
        return text.count() > 0 && text.first().isVisible();
    }

    private static String rowNeedle(String resourceName) {
        String needle = resourceName == null ? "" : resourceName.trim();
        return needle.length() > 24 ? needle.substring(0, 24) : needle;
    }

    private static String stripTrailingZero(double limit) {
        if (limit == Math.rint(limit)) {
            return String.valueOf((int) limit);
        }
        return String.valueOf(limit);
    }
}
