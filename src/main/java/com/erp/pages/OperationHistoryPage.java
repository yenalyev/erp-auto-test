package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/** Page Object for /history (operation history) */
@Slf4j
public class OperationHistoryPage extends BasePage {

    private static final String PATH = "/history";

    public OperationHistoryPage(Page page) {
        super(page);
    }

    public OperationHistoryPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        waitForHistoryDuring(() -> navigateTo(url, "Історія операцій (/history)"));
        return waitForLoaded();
    }

    public OperationHistoryPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName("Історія операцій"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.getByText("Додано (Інвентаризація)")
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isLoaded() {
        return page.url().contains("/history")
                && page.getByText("Додано (Інвентаризація)").isVisible();
    }

    private void waitForHistoryDuring(Runnable action) {
        try {
            page.waitForResponse(
                    response -> response.url().contains("resource-operation-history")
                            && "GET".equals(response.request().method())
                            && response.status() == 200,
                    action);
        } catch (Exception e) {
            log.warn("Operation history response wait timed out: {}", e.getMessage());
            action.run();
            page.waitForTimeout(2000);
        }
    }

    public boolean containsInventoryOperationMarker() {
        String content = page.locator("body").innerText();
        return content.contains("ADDED_INV") || content.contains("REMOVED_INV")
                || content.contains("Додано (Інвентаризація)")
                || content.contains("Видалено (Інвентаризація)")
                || content.toLowerCase().contains("інвентар");
    }

    /**
     * Reads aggregated amount from a summary card (e.g. «Вироблено») for the given resource name.
     */
    public double getSummaryCardAmountForResource(String cardTitle, String resourceName) {
        Locator card = summaryCard(cardTitle);
        card.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));

        Locator row = card.locator("li").filter(new Locator.FilterOptions().setHasText(resourceName));
        if (row.count() == 0) {
            throw new IllegalStateException(
                    "Resource «" + resourceName + "» not found in summary card «" + cardTitle + "»");
        }

        String amountText = row.locator("span.font-medium").innerText().trim();
        return parseLeadingNumber(amountText);
    }

    public boolean isProducedSummaryVisible() {
        return summaryCardTitle("Вироблено").isVisible();
    }

    private Locator summaryCard(String cardTitle) {
        return page.locator("[data-slot='card']")
                .filter(new Locator.FilterOptions().setHas(summaryCardTitle(cardTitle)));
    }

    private Locator summaryCardTitle(String cardTitle) {
        return page.locator("[data-slot='card-title']")
                .filter(new Locator.FilterOptions().setHasText(cardTitle));
    }

    private static double parseLeadingNumber(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\d.,]+)").matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse amount from: " + text);
        }
        return Double.parseDouble(matcher.group(1).replace(',', '.'));
    }
}
