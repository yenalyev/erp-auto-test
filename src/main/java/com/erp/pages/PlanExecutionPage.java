package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the Plan Execution page (tk-ui {@code PlanExecutionPage.tsx}).
 * URL: /plan-execution
 *
 * <p>The lead/lag card ("Випередження" / "Відставання") is rendered only when
 * {@code resourcePlanExecutionList.length > 0} — i.e. at least one product with an active
 * PRODUCTION tech map on the selected storage has either a current-month plan goal or
 * current-month production. See tk-ui lines ~211-215 and ~475-499.
 */
@Slf4j
public class PlanExecutionPage extends BasePage {

    private static final String PATH = "/plan-execution";

    private static final String HEADING_TEXT = "Виконання плану";
    private static final String EMPTY_STATE_TEXT = "Дані про виконання плану за цей місяць відсутні";
    private static final String ALL_LOCATIONS_GUARD_TEXT =
            "Оберіть конкретну локацію, щоб переглянути виконання плану";
    private static final String LEAD_TEXT = "Випередження";
    private static final String LAG_TEXT = "Відставання";
    private static final String GOAL_PLACEHOLDER = "—";
    private static final String COPY_BUTTON_TEXT = "Скопіювати";
    private static final String COPIED_FEEDBACK_TEXT = "Скопійовано зроблене";

    public PlanExecutionPage(Page page) {
        super(page);
    }

    /**
     * Opens the page and waits for the {@code POST .../statistics/execution} response that feeds
     * both the table and the lead/lag card — a DOM-only heuristic (e.g. "any table row present")
     * is unreliable here because the page also renders an unrelated "Розбір" (disassembly)
     * {@code DataTable} below the tabs that can populate its own rows before the execution fetch
     * resolves.
     */
    public PlanExecutionPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        page.waitForResponse(
                r -> r.url().contains("/statistics/execution") && "POST".equals(r.request().method()),
                () -> navigateTo(url, "Виконання плану (/plan-execution)"));
        return waitForLoaded();
    }

    public PlanExecutionPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING_TEXT))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForExecutionDataSettled();
        log.info("Plan Execution page loaded — url: {}", page.url());
        return this;
    }

    /** Wait until the execution tab finishes loading (rows, empty state, or all-locations guard rendered). */
    public PlanExecutionPage waitForExecutionDataSettled() {
        page.waitForCondition(
                () -> getProductRowCount() > 0 || isEmptyStateVisible() || isAllLocationsGuardVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isAllLocationsGuardVisible() {
        Locator guard = page.getByText(ALL_LOCATIONS_GUARD_TEXT);
        return guard.count() > 0 && guard.first().isVisible();
    }

    public boolean isEmptyStateVisible() {
        Locator empty = page.getByText(EMPTY_STATE_TEXT);
        return empty.count() > 0 && empty.first().isVisible();
    }

    /** True when either the "Випередження" or "Відставання" summary card is rendered. */
    public boolean isLeadLagCardVisible() {
        return leadLagCardLocator().count() > 0 && leadLagCardLocator().first().isVisible();
    }

    /** Text of the lead/lag card (e.g. "Відставання" / "-9.7%"), or empty when not rendered. */
    public String getLeadLagCardText() {
        if (!isLeadLagCardVisible()) {
            return "";
        }
        String text = leadLagCardLocator().first().innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    public int getProductRowCount() {
        return executionTableRows().count();
    }

    public boolean isProductRowVisible(String productName) {
        return productRow(productName).count() > 0 && productRow(productName).first().isVisible();
    }

    /** «Ціль» cell text for the given product row, or {@code GOAL_PLACEHOLDER} ("—") when no plan targets it. */
    public String getGoalCellText(String productName) {
        return cellText(productName, 3);
    }

    /** «Зроблено» cell text for the given product row. */
    public String getProducedCellText(String productName) {
        return cellText(productName, 2);
    }

    public boolean isGoalAbsent(String productName) {
        return GOAL_PLACEHOLDER.equals(getGoalCellText(productName));
    }

    /** True when the «Скопіювати» button is visible on the «Виконання» tab. */
    public boolean isCopyButtonVisible() {
        Locator button = copyButton();
        return button.count() > 0 && button.first().isVisible();
    }

    public boolean isCopyButtonEnabled() {
        return copyButton().isEnabled();
    }

    /**
     * Stubs {@code navigator.clipboard.writeText} so the payload can be read back without OS
     * clipboard permissions (reliable in headless CI). Call before {@link #clickCopyProduced()}.
     */
    public PlanExecutionPage installClipboardCapture() {
        page.evaluate("""
                () => {
                  window.__erpClipboardText = undefined;
                  navigator.clipboard.writeText = async (text) => {
                    window.__erpClipboardText = text;
                  };
                }
                """);
        return this;
    }

    public PlanExecutionPage clickCopyProduced() {
        copyButton().click();
        return this;
    }

    public PlanExecutionPage waitForCopiedFeedback() {
        page.getByText(COPIED_FEEDBACK_TEXT)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    /** Text captured by {@link #installClipboardCapture()}, or empty when nothing was written. */
    public String getCapturedClipboardText() {
        page.waitForCondition(
                () -> Boolean.TRUE.equals(page.evaluate("() => window.__erpClipboardText !== undefined")),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        Object value = page.evaluate("() => window.__erpClipboardText");
        return value != null ? value.toString() : "";
    }

    private Locator copyButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(COPY_BUTTON_TEXT));
    }

    private String cellText(String productName, int columnIndex) {
        Locator row = productRow(productName).first();
        Locator cell = row.locator("td").nth(columnIndex);
        String text = cell.innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    private Locator productRow(String productName) {
        return executionTableRows()
                .filter(new Locator.FilterOptions().setHasText(productName));
    }

    private Locator leadLagCardLocator() {
        return page.getByText(LEAD_TEXT).or(page.getByText(LAG_TEXT));
    }

    /**
     * Rows of the "Виконання" tab's execution table. The page also renders an unrelated
     * "Розбір" (disassembly) {@code DataTable} below the tabs, so row lookups must be scoped to
     * the execution table specifically — otherwise a settled/populated disassembly table could be
     * mistaken for the execution table having finished loading. The execution table is always the
     * first {@code <table>} in the DOM while the default "Виконання" tab is active (Radix Tabs
     * only mounts the active tab's content).
     */
    private Locator executionTableRows() {
        return page.locator("table").first().locator("tbody tr");
    }
}
