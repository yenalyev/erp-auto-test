package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

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
    private static final String PRODUCED_HEADER = "Зроблено";
    private static final String GOAL_HEADER = "Ціль";
    private static final String COPY_BUTTON_TEXT = "Скопіювати";
    private static final String COPIED_FEEDBACK_TEXT = "Скопійовано зроблене";

    /** tk-ui CPMA-587: filter toggle on the «Виконання» tab (product requirement: «Тільки обрані»). */
    private static final String FAVOURITES_ONLY_BUTTON_TEXT = "Лише обрані";
    /** tk-ui CPMA-587: opens the manage-favourites dialog (product requirement: «Налаштувати обрані»). */
    private static final String MANAGE_FAVOURITES_BUTTON_PREFIX = "Керувати обраними";
    private static final String MANAGE_FAVOURITES_DIALOG_TITLE = "Керування обраними ресурсами";
    private static final String FAVOURITES_EMPTY_STATE_TEXT = "Немає обраних ресурсів за цей місяць";
    private static final String MANAGE_FAVOURITES_SAVE_PREFIX = "Зберегти";
    private static final String MANAGE_FAVOURITES_CANCEL_TEXT = "Скасувати";

    private static final String DISASSEMBLE_HEADING = "Розбір";

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
        Locator ready = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HEADING_TEXT))
                .or(page.getByText(ALL_LOCATIONS_GUARD_TEXT))
                .or(page.getByText(EMPTY_STATE_TEXT))
                .or(page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(FAVOURITES_ONLY_BUTTON_TEXT)))
                .or(page.locator("table").first())
                .first();
        ready.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        waitForExecutionDataSettled();
        log.info("Plan Execution page loaded — url: {}", page.url());
        return this;
    }

    /** Wait until the execution tab finishes loading (rows, empty state, or all-locations guard rendered). */
    public PlanExecutionPage waitForExecutionDataSettled() {
        page.waitForCondition(
                () -> getProductRowCount() > 0
                        || isEmptyStateVisible()
                        || isFavouritesEmptyStateVisible()
                        || isAllLocationsGuardVisible(),
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
        return cellText(productName, columnIndexByHeader(GOAL_HEADER));
    }

    /** «Зроблено» cell text for the given product row. */
    public String getProducedCellText(String productName) {
        return cellText(productName, columnIndexByHeader(PRODUCED_HEADER));
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

    // -------------------------------------------------------------------
    // Favourite products filter (CPMA-587)
    // -------------------------------------------------------------------

    public boolean isFavouritesOnlyButtonVisible() {
        Locator button = favouritesOnlyButton();
        return button.count() > 0 && button.first().isVisible();
    }

    /**
     * True when the «Лише обрані» toggle can be activated. Product requirement expects the control
     * disabled while no favourites are configured; current tk-ui keeps it always enabled and shows
     * an empty state instead — assert the expected disabled state so the regression stays red until
     * the UI matches the requirement.
     */
    public boolean isFavouritesOnlyButtonEnabled() {
        return favouritesOnlyButton().isEnabled();
    }

    public boolean isFavouritesOnlyPressed() {
        String pressed = favouritesOnlyButton().getAttribute("aria-pressed");
        return "true".equalsIgnoreCase(pressed);
    }

    public PlanExecutionPage clickFavouritesOnly() {
        page.waitForResponse(
                r -> r.url().contains("/statistics/execution") && "POST".equals(r.request().method()),
                () -> favouritesOnlyButton().click());
        return waitForExecutionDataSettled();
    }

    /**
     * Toggles «Лише обрані» and returns the {@code POST /statistics/execution} request body so
     * tests can assert that {@code resourceIds} were actually sent to the backend.
     */
    public String clickFavouritesOnlyAndCaptureExecutionRequestBody() {
        var response = page.waitForResponse(
                r -> r.url().contains("/statistics/execution") && "POST".equals(r.request().method()),
                () -> favouritesOnlyButton().click());
        waitForExecutionDataSettled();
        return response.request().postData();
    }

    public boolean isManageFavouritesButtonVisible() {
        Locator button = manageFavouritesButton();
        return button.count() > 0 && button.first().isVisible();
    }

    public String getManageFavouritesButtonText() {
        String text = manageFavouritesButton().innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    public PlanExecutionPage openManageFavouritesDialog() {
        manageFavouritesButton().click();
        manageFavouritesDialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return waitForManageDialogReady();
    }

    /**
     * Filters the manage-dialog resource table by name (placeholder «Фільтр за назвою») and waits
     * until a matching row is visible.
     *
     * <p>tk-ui debounces catalog reload by 250&nbsp;ms ({@code ManageFavoriteResourcesDialog});
     * a fixed short sleep after {@code fill()} is flaky on staging — wait for
     * {@code GET /resources/with-technological-map} then for the row.
     */
    public PlanExecutionPage filterManageDialogByName(String nameFragment) {
        Locator input = manageFavouritesDialog().getByPlaceholder("Фільтр за назвою");
        page.waitForResponse(
                r -> r.url().contains("/resources/with-technological-map")
                        && "GET".equals(r.request().method()),
                () -> input.fill(nameFragment));
        manageDialogProductRow(nameFragment).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    /** Clears the name filter and types {@code nameFragment} without waiting for a matching row. */
    public PlanExecutionPage typeManageDialogNameFilter(String nameFragment) {
        manageFavouritesDialog().getByPlaceholder("Фільтр за назвою").fill(nameFragment);
        return this;
    }

    public boolean isManageFavouritesDialogVisible() {
        Locator dialog = manageFavouritesDialog();
        return dialog.count() > 0 && dialog.first().isVisible();
    }

    /** Waits until the manage-dialog loading spinner is gone and content has settled. */
    public PlanExecutionPage waitForManageDialogReady() {
        page.waitForCondition(() -> {
            Locator spinner = manageFavouritesDialog().locator("svg.animate-spin");
            boolean loading = spinner.count() > 0 && spinner.first().isVisible();
            if (loading) {
                return false;
            }
            return manageFavouritesDialog().locator("tbody tr").count() > 0
                    || manageFavouritesDialog().getByText("Немає записів").count() > 0;
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Toggles the in-dialog «Лише обрані» checkbox (distinct from the page-level filter button).
     * When checked, the dialog shows rows seeded from already-saved favourites (resourceInfo),
     * so existing favourites remain editable even if GET /resources/with-technological-map is empty.
     */
    public PlanExecutionPage setManageDialogOnlyFavourites(boolean onlyFavourites) {
        boolean checked = isManageDialogOnlyFavouritesChecked();
        if (checked != onlyFavourites) {
            // Click the label text — more reliable than the radix checkbox button itself.
            manageFavouritesDialog()
                    .locator("label")
                    .filter(new Locator.FilterOptions().setHasText("Лише обрані"))
                    .click();
        }
        page.waitForCondition(
                () -> isManageDialogOnlyFavouritesChecked() == onlyFavourites,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        if (onlyFavourites) {
            page.waitForCondition(
                    () -> manageFavouritesDialog().locator("tbody tr").count() > 0
                            || manageFavouritesDialog().getByText("Немає записів").count() > 0,
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        } else {
            waitForManageDialogReady();
        }
        return this;
    }

    public boolean isManageDialogOnlyFavouritesChecked() {
        Locator checkbox = manageDialogOnlyFavouritesCheckbox();
        String state = checkbox.getAttribute("data-state");
        if (state != null) {
            return "checked".equalsIgnoreCase(state);
        }
        return checkbox.isChecked();
    }

    /** True when the manage dialog lists a row whose name cell contains {@code productName}. */
    public boolean isProductListedInManageDialog(String productName) {
        return manageDialogProductRow(productName).count() > 0
                && manageDialogProductRow(productName).first().isVisible();
    }

    public boolean isProductFavouritedInManageDialog(String productName) {
        Locator row = manageDialogProductRow(productName).first();
        if (row.count() == 0) {
            return false;
        }
        Locator removeBtn = row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("Прибрати з обраного"));
        return removeBtn.count() > 0 && removeBtn.first().isVisible();
    }

    public PlanExecutionPage toggleFavouriteInManageDialog(String productName) {
        manageDialogFavouriteStar(productName).first().click();
        return this;
    }

    public PlanExecutionPage saveManageFavouritesDialog() {
        Locator save = manageFavouritesDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("^" + MANAGE_FAVOURITES_SAVE_PREFIX)));
        page.waitForResponse(
                r -> r.url().contains("/app-config/favourite-resources") && "PUT".equals(r.request().method()),
                save::click);
        manageFavouritesDialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public PlanExecutionPage cancelManageFavouritesDialog() {
        manageFavouritesDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(MANAGE_FAVOURITES_CANCEL_TEXT))
                .click();
        manageFavouritesDialog().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    private Locator manageDialogOnlyFavouritesCheckbox() {
        return manageFavouritesDialog()
                .locator("label")
                .filter(new Locator.FilterOptions().setHasText("Лише обрані"))
                .locator("[role='checkbox'], button[role='checkbox'], input[type='checkbox']")
                .first();
    }

    private Locator manageDialogFavouriteStar(String productName) {
        return manageDialogProductRow(productName)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("Прибрати з обраного|Додати в обране")));
    }

    public boolean isFavouritesEmptyStateVisible() {
        Locator empty = page.getByText(FAVOURITES_EMPTY_STATE_TEXT);
        return empty.count() > 0 && empty.first().isVisible();
    }

    /**
     * Product rows on the «Виконання» tab, excluding the totals footer row
     * («Виготовлено за …»). Prefer this over {@link #getProductRowCount()} when asserting
     * favourites filtering — the footer stays rendered even for a filtered empty/partial list.
     */
    public int getNamedProductRowCount() {
        return executionTableRows()
                .filter(new Locator.FilterOptions().setHasNotText("Виготовлено за"))
                .count();
    }

    /** True when the «Розбір» block under the execution tab is rendered. */
    public boolean isDisassembleSectionVisible() {
        Locator heading = disassembleHeading();
        return heading.count() > 0 && heading.first().isVisible();
    }

    /**
     * Day amount for the disassembled input resource in the «Розбір» table
     * (column «Розібрано за …»: {@code N шт ResourceName}).
     */
    public double getDisassembleDayInputAmount(String inputResourceName) {
        Locator row = disassembleTableRows()
                .filter(new Locator.FilterOptions().setHasText(inputResourceName))
                .first();
        row.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        String text = row.locator("td").first().innerText();
        return parseLeadingNumber(text);
    }

    /**
     * Day amount for an output resource in the «Розбір» table
     * (column «Отримано за …»: {@code N unit ResourceName}).
     */
    public double getDisassembleDayOutputAmount(String outputResourceName) {
        Locator row = disassembleTableRows()
                .filter(new Locator.FilterOptions().setHasText(outputResourceName))
                .first();
        row.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        Locator outputLine = row.locator("li")
                .filter(new Locator.FilterOptions().setHasText(outputResourceName))
                .first();
        String text = outputLine.innerText();
        return parseLeadingNumber(text);
    }

    public PlanExecutionPage waitForDisassembleSection() {
        disassembleHeading().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForCondition(
                () -> disassembleTableRows().count() > 0,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    private Locator disassembleHeading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(DISASSEMBLE_HEADING));
    }

    /**
     * Desktop «Розбір» {@code DataTable} is the first table after the h3 heading
     * (execution table is earlier in the DOM while the Виконання tab is active).
     */
    private Locator disassembleTableRows() {
        return page.locator("h3")
                .filter(new Locator.FilterOptions().setHasText(DISASSEMBLE_HEADING))
                .locator("xpath=following::table[1]")
                .locator("tbody tr");
    }

    private static double parseLeadingNumber(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Cannot parse amount from empty text");
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\d.,]+)").matcher(text.trim());
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse amount from: " + text);
        }
        return Double.parseDouble(matcher.group(1).replace(',', '.'));
    }

    private Locator favouritesOnlyButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(FAVOURITES_ONLY_BUTTON_TEXT));
    }

    private Locator manageFavouritesButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + MANAGE_FAVOURITES_BUTTON_PREFIX)));
    }

    private Locator manageFavouritesDialog() {
        return page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHasText(MANAGE_FAVOURITES_DIALOG_TITLE));
    }

    private Locator manageDialogProductRow(String productName) {
        return manageFavouritesDialog().locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(productName));
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

    /**
     * Resolves a body-cell index from a summary header label («Зроблено», «Ціль», …).
     *
     * <p>The execution table has a two-row header: the first row holds «Продукт» (rowSpan=2),
     * the «Динаміка по днях» group and the «Станом на поточний день» group; the second row holds
     * one {@code th} per day of the sliding window followed by the summary columns. A body row
     * therefore starts with the «Продукт» cell that the second header row does not repeat, so the
     * cell index is the header's position in the second row shifted by one. Resolving this at
     * runtime keeps the page object correct when the day-window size changes.
     */
    private int columnIndexByHeader(String headerText) {
        Locator headers = executionTable().locator("thead tr").nth(1).locator("th");
        int count = headers.count();
        for (int i = 0; i < count; i++) {
            String text = headers.nth(i).innerText();
            if (text != null && headerText.equals(text.trim().replaceAll("\\s+", " "))) {
                return i + 1;
            }
        }
        throw new IllegalStateException(
                "Column «" + headerText + "» not found in the plan execution table header. Present: "
                        + headers.allInnerTexts());
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
        return executionTable().locator("tbody tr");
    }

    private Locator executionTable() {
        return page.locator("table").first();
    }
}
