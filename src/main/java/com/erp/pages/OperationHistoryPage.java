package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

/** Page Object for /history (operation history) */
@Slf4j
public class OperationHistoryPage extends BasePage {

    private static final String PATH = "/history";

    public OperationHistoryPage(Page page) {
        super(page);
    }

    public static final String EQUIPMENT_SENT_CARD = "Відправлено (Обладнання)";
    public static final String EQUIPMENT_RECEIVED_CARD = "Отримано (Обладнання)";
    public static final String EQUIPMENT_OP_SENT = "Відправлено";

    public OperationHistoryPage open() {
        LocalDate today = LocalDate.now();
        String url = ConfigProvider.getBaseUrl() + PATH
                + "?startDate=" + today
                + "&endDate=" + today.plusDays(1);
        waitForHistoryDuring(() -> waitForEquipmentHistoryDuring(
                () -> navigateTo(url, "Історія операцій (/history)")));
        waitForLoaded();
        return waitForSummaryCardsRendered();
    }

    public OperationHistoryPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName("Історія операцій"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Resource summary cards mount only after {@code resource-operation-history} is committed.
     * CPMA-729 also renders equipment cards independently — waiting for any
     * {@code [data-slot=card-title]} races and can return before «Видано»/«Вироблено» exist.
     * Roles with no resource-card permissions never get those cards — timeout is acceptable.
     */
    public OperationHistoryPage waitForSummaryCardsRendered() {
        Locator resourceCardTitle = page.locator("[data-slot='card-title']")
                .filter(new Locator.FilterOptions().setHasNotText("Обладнання"))
                .first();
        try {
            resourceCardTitle.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.warn("No resource summary cards rendered within timeout (ok for restricted roles): {}",
                    e.getMessage());
        }
        return this;
    }

    public boolean isLoaded() {
        return page.url().contains("/history")
                && page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName("Історія операцій"))
                .isVisible();
    }

    private void waitForHistoryDuring(Runnable action) {
        // Action already ran; a late request may still complete — waitForSummaryCardsRendered handles settle.
        waitForResponseTolerant(
                response -> response.url().contains("resource-operation-history")
                        && "GET".equals(response.request().method())
                        && response.status() == 200,
                action,
                "GET resource-operation-history");
    }

    private void waitForEquipmentHistoryDuring(Runnable action) {
        waitForResponseTolerant(
                response -> response.url().contains("/equipment/history")
                        && "GET".equals(response.request().method())
                        && response.status() == 200,
                action,
                "GET /equipment/history");
    }

    /**
     * Equipment summary cards («Відправлено/Отримано (Обладнання)») — do not use
     * {@link #isSummaryCardVisible} which excludes titles containing «Обладнання».
     */
    public boolean isEquipmentSummaryCardVisible(String cardTitle) {
        Locator title = equipmentSummaryCardTitle(cardTitle);
        try {
            title.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
            return true;
        } catch (Exception e) {
            return title.count() > 0 && title.first().isVisible();
        }
    }

    public boolean equipmentHistoryContains(String text) {
        Locator match = page.locator("[data-slot='table'], [data-slot='card']")
                .filter(new Locator.FilterOptions().setHasText("Обладнання"))
                .filter(new Locator.FilterOptions().setHasText(text));
        if (match.count() > 0) {
            return true;
        }
        return page.getByText(text, new Page.GetByTextOptions().setExact(true)).count() > 0
                || page.locator("body").innerText().contains(text);
    }

    /**
     * True when the equipment operations table has a row that identifies the unit
     * and shows the given operation badge (e.g. «Відправлено»).
     */
    public boolean equipmentTableHasOperation(String equipmentText, String operationLabel) {
        Locator rows = page.locator("[data-slot='table'] tbody tr");
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            String rowText = rows.nth(i).innerText();
            if (rowText.contains(equipmentText) && rowText.contains(operationLabel)) {
                return true;
            }
        }
        return false;
    }

    private Locator equipmentSummaryCardTitle(String cardTitle) {
        return page.locator("[data-slot='card-title']")
                .filter(new Locator.FilterOptions().setHasText(cardTitle));
    }

    public boolean containsInventoryOperationMarker() {
        String content = page.locator("body").innerText();
        return content.contains("ADDED_INV") || content.contains("REMOVED_INV")
                || content.contains("Додано(Інв.)")
                || content.contains("Видалено(Інв.)")
                || content.contains("Додано (Інвентаризація)")
                || content.contains("Видалено (Інвентаризація)")
                || content.toLowerCase().contains("інвентар");
    }

    public boolean tableContainsComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return false;
        }
        Locator rows = page.locator("table tbody tr");
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            String rowText = rows.nth(i).innerText();
            if (rowText.contains(comment)) {
                return true;
            }
        }
        return page.locator("body").innerText().contains(comment);
    }

    public boolean tableContainsCommentForResource(String resourceName, String comment) {
        Locator rows = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(resourceName.trim()));
        if (rows.count() == 0) {
            return false;
        }
        return rows.filter(new Locator.FilterOptions().setHasText(comment)).count() > 0;
    }

    /** True when UI shows incident write-off markers (summary card and/or table label). */
    public boolean containsIncidentOperationMarker() {
        String content = page.locator("body").innerText();
        return content.contains("INCIDENT_WRITE_OFF")
                || content.contains("Надзвичайна подія: втрата")
                || content.contains("Надзвичайні події");
    }

    public boolean isIncidentSummaryVisible() {
        return isSummaryCardVisible("Надзвичайні події");
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

    /** Same as {@link #getSummaryCardAmountForResource} but returns 0 when the resource row is absent. */
    public double getSummaryCardAmountForResourceOrZero(String cardTitle, String resourceName) {
        if (!isSummaryCardVisible(cardTitle)) {
            return 0.0;
        }
        Locator row = summaryCard(cardTitle).locator("li")
                .filter(new Locator.FilterOptions().setHasText(resourceName));
        if (row.count() == 0) {
            return 0.0;
        }
        String amountText = row.first().locator("span.font-medium").innerText().trim();
        return parseLeadingNumber(amountText);
    }

    public boolean isProducedSummaryVisible() {
        return summaryCardTitle("Вироблено").isVisible();
    }

    public boolean isSummaryCardVisible(String cardTitle) {
        Locator title = summaryCardTitle(cardTitle);
        try {
            title.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
            return true;
        } catch (Exception e) {
            return title.count() > 0 && title.first().isVisible();
        }
    }

    private Locator summaryCard(String cardTitle) {
        return page.locator("[data-slot='card']")
                .filter(new Locator.FilterOptions().setHas(summaryCardTitle(cardTitle)));
    }

    private Locator summaryCardTitle(String cardTitle) {
        return page.locator("[data-slot='card-title']")
                .filter(new Locator.FilterOptions().setHasNotText("Обладнання"))
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
