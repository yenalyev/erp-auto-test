package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.time.YearMonth;
import java.util.List;
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
    private static final String TOTAL_PRODUCED_PCS_LABEL = "Загалом зроблено, шт";

    /** tk-ui CPMA-587: filter toggle on the «Виконання» tab (product requirement: «Тільки обрані»). */
    private static final String FAVOURITES_ONLY_BUTTON_TEXT = "Лише обрані";
    /** tk-ui CPMA-587: opens the manage-favourites dialog (product requirement: «Налаштувати обрані»). */
    private static final String MANAGE_FAVOURITES_BUTTON_PREFIX = "Керувати обраними";
    private static final String MANAGE_FAVOURITES_DIALOG_TITLE = "Керування обраними ресурсами";
    private static final String FAVOURITES_EMPTY_STATE_TEXT = "Немає обраних ресурсів за цей місяць";
    private static final String MANAGE_FAVOURITES_SAVE_PREFIX = "Зберегти";
    private static final String MANAGE_FAVOURITES_CANCEL_TEXT = "Скасувати";

    private static final String DISASSEMBLE_HEADING = "Розбір";

    public static final String NEEDED_TAB_TEXT = "Потрібні ресурси";
    public static final String NEEDED_EMPTY_TEXT = "Немає потреби в додаткових ресурсах";
    public static final String NEEDED_FILTER_EMPTY_TEXT = "Немає ресурсів за обраними фільтрами";
    public static final String NEEDED_PAST_MONTH_TOOLTIP =
            "Потреба в ресурсах недоступна для завершеного місяця";
    public static final String NEEDED_PRODUCED_BADGE = "виробляється";
    public static final String NEEDED_SOURCES_LABEL = "Потрібно для виробів:";
    public static final String INCLUDE_STOCK_LABEL = "Враховувати залишки";
    public static final String INCLUDE_PRODUCED_LABEL = "Враховувати виготовлене";
    public static final String ONLY_SHORTAGES_LABEL = "Лише дефіцитні";
    public static final String NEEDED_COPIED_FEEDBACK = "Скопійовано";

    /** Stems that match both date-fns standalone (серпень) and Java CLDR genitive (серпня). */
    private static final String[] UK_MONTH_STEMS = {
            "січ", "лют", "берез", "квіт", "трав", "черв",
            "липень", "серп", "верес", "жовт", "листоп", "груд"
    };

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
                r -> r.url().contains("/statistics/execution")
                        && !r.url().contains("execution-periods-with-plan")
                        && "POST".equals(r.request().method()),
                () -> page.waitForResponse(
                        r -> r.url().contains("execution-periods-with-plan")
                                && "GET".equals(r.request().method()),
                        () -> navigateTo(url, "Виконання плану (/plan-execution)")));
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

    /** Navigate without waiting for {@code POST /statistics/execution} (e.g. «Всі локації»). */
    public PlanExecutionPage openWithoutExecutionFetch() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        navigateTo(url, "Виконання плану (/plan-execution)");
        return waitForLoaded();
    }

    public PlanExecutionPage openNeededResourcesTab() {
        Locator tab = neededTab();
        tab.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        if (!"active".equalsIgnoreCase(tab.getAttribute("data-state"))) {
            tab.click();
        }
        return waitForNeededSettled();
    }

    public PlanExecutionPage waitForNeededSettled() {
        page.waitForCondition(
                () -> getNeededRowCount() > 0
                        || isNeededEmptyVisible()
                        || isNeededFilterEmptyVisible()
                        || isAllLocationsGuardVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isNeededTabEnabled() {
        Locator tab = neededTab();
        return tab.count() > 0 && tab.first().isEnabled();
    }

    public boolean isNeededTabDisabled() {
        Locator tab = neededTab();
        return tab.count() > 0 && !tab.first().isEnabled();
    }

    public boolean isNeededTabVisible() {
        Locator tab = neededTab();
        return tab.count() > 0 && tab.first().isVisible();
    }

    public String getNeededPastMonthTooltip() {
        Locator trigger = page.locator("span.cursor-help").filter(
                new Locator.FilterOptions().setHas(neededTab()));
        if (trigger.count() == 0) {
            neededTab().hover();
        } else {
            trigger.first().hover();
        }
        Locator tooltip = page.getByText(NEEDED_PAST_MONTH_TOOLTIP);
        tooltip.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return tooltip.innerText().trim();
    }

    public int getNeededRowCount() {
        return neededDataRows().count();
    }

    public boolean isNeededRowVisible(String resourceName) {
        Locator row = neededRow(resourceName);
        return row.count() > 0 && row.first().isVisible();
    }

    public boolean isNeededProducedBadgeVisible(String resourceName) {
        Locator badge = neededRow(resourceName).getByText(NEEDED_PRODUCED_BADGE);
        return badge.count() > 0 && badge.first().isVisible();
    }

    public boolean isNeededEmptyVisible() {
        Locator empty = page.getByText(NEEDED_EMPTY_TEXT);
        return empty.count() > 0 && empty.first().isVisible();
    }

    public boolean isNeededFilterEmptyVisible() {
        Locator empty = page.getByText(NEEDED_FILTER_EMPTY_TEXT);
        return empty.count() > 0 && empty.first().isVisible();
    }

    public boolean isNeededSourcesVisible() {
        Locator label = page.getByText(NEEDED_SOURCES_LABEL);
        return label.count() > 0 && label.first().isVisible();
    }

    public String getNeededSourcesText() {
        Locator block = page.locator("tr").filter(
                new Locator.FilterOptions().setHasText(NEEDED_SOURCES_LABEL));
        if (block.count() == 0) {
            return "";
        }
        String text = block.first().innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    public PlanExecutionPage expandNeededRow(String resourceName) {
        if (!isNeededSourcesVisible()) {
            neededRow(resourceName).first().click();
        }
        page.waitForCondition(
                this::isNeededSourcesVisible,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public PlanExecutionPage collapseNeededRow(String resourceName) {
        if (isNeededSourcesVisible()) {
            neededRow(resourceName).first().click();
        }
        page.waitForCondition(
                () -> !isNeededSourcesVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public PlanExecutionPage setIncludeStock(boolean enabled) {
        return toggleNeededCheckbox(INCLUDE_STOCK_LABEL, enabled);
    }

    public PlanExecutionPage setIncludeProduced(boolean enabled) {
        return toggleNeededCheckbox(INCLUDE_PRODUCED_LABEL, enabled);
    }

    public PlanExecutionPage setOnlyShortages(boolean enabled) {
        Locator checkbox = neededCheckbox(ONLY_SHORTAGES_LABEL);
        if (checkbox.isChecked() != enabled) {
            checkbox.click();
        }
        return waitForNeededSettled();
    }

    public boolean isIncludeStockChecked() {
        return neededCheckbox(INCLUDE_STOCK_LABEL).isChecked();
    }

    public PlanExecutionPage selectNeededCategory(String categoryName) {
        page.getByPlaceholder("Категорії").click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(categoryName))
                .first()
                .click();
        page.keyboard().press("Escape");
        return waitForNeededSettled();
    }

    public PlanExecutionPage clickNeededSort(String header) {
        neededTable().locator("thead button")
                .filter(new Locator.FilterOptions().setHasText(header))
                .first()
                .click();
        return this;
    }

    public List<String> neededResourceNames() {
        int count = neededDataRows().count();
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(neededDataRows().nth(i).locator("td").nth(1).innerText().trim().split("\\n")[0].trim());
        }
        return names;
    }

    public double getNeededAmount(String resourceName) {
        return parseLeadingNumber(
                neededRow(resourceName).locator("td").nth(neededColumnIndex("Потрібно")).innerText());
    }

    public boolean isNeededHeaderVisible(String header) {
        Locator cell = neededTable().locator("thead th")
                .filter(new Locator.FilterOptions().setHasText(header));
        return cell.count() > 0;
    }

    public PlanExecutionPage clickCopyNeeded() {
        copyButton().click();
        return this;
    }

    public PlanExecutionPage waitForNeededCopiedFeedback() {
        page.getByText(NEEDED_COPIED_FEEDBACK)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public PlanExecutionPage selectPeriod(String periodLabel) {
        return selectPeriodOption(periodLabel);
    }

    /**
     * Opens the period popover from the current label and picks {@code targetPeriodLabel}.
     */
    public PlanExecutionPage selectPeriodFrom(String currentPeriodLabel, String targetPeriodLabel) {
        return selectPeriod(targetPeriodLabel);
    }

    /** Picks a period in the date-fns uk {@code LLLL yyyy} popover (nominative or genitive month). */
    public PlanExecutionPage selectPeriod(YearMonth period) {
        String stem = UK_MONTH_STEMS[period.getMonthValue() - 1];
        String year = String.valueOf(period.getYear());
        periodTriggerButton().click();
        Locator option = periodOptionButtons()
                .filter(new Locator.FilterOptions().setHasText(
                        Pattern.compile(stem, Pattern.CASE_INSENSITIVE)))
                .filter(new Locator.FilterOptions().setHasText(year))
                .last();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                r -> r.url().contains("/statistics/execution")
                        && !r.url().contains("execution-periods-with-plan")
                        && "POST".equals(r.request().method()),
                option::click);
        return waitForExecutionDataSettled();
    }

    /**
     * Picks a period by index in the open picker (backend returns newest first; 0 is typically
     * the current month, 1 the previous month that has a plan).
     */
    public PlanExecutionPage selectPeriodAt(int index) {
        periodTriggerButton().click();
        Locator options = periodOptionButtons();
        options.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForCondition(
                () -> options.count() > index,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                r -> r.url().contains("/statistics/execution")
                        && !r.url().contains("execution-periods-with-plan")
                        && "POST".equals(r.request().method()),
                () -> options.nth(index).click());
        return waitForExecutionDataSettled();
    }

    private PlanExecutionPage selectPeriodOption(String optionText) {
        periodTriggerButton().click();
        Locator option = periodOptionButtons()
                .filter(new Locator.FilterOptions().setHasText(optionText))
                .last();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                r -> r.url().contains("/statistics/execution")
                        && !r.url().contains("execution-periods-with-plan")
                        && "POST".equals(r.request().method()),
                option::click);
        return waitForExecutionDataSettled();
    }

    private Locator periodTriggerButton() {
        return page.locator("button")
                .filter(new Locator.FilterOptions().setHas(page.locator("svg")))
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("20\\d{2}")))
                .first();
    }

    private Locator periodOptionButtons() {
        return page.locator("[data-radix-popper-content-wrapper] button");
    }

    private PlanExecutionPage toggleNeededCheckbox(String label, boolean enabled) {
        Locator checkbox = neededCheckbox(label);
        if (checkbox.isChecked() == enabled) {
            return this;
        }
        page.waitForResponse(
                r -> r.url().contains("/statistics/needed-resources") && "POST".equals(r.request().method()),
                checkbox::click);
        return waitForNeededSettled();
    }

    private Locator neededCheckbox(String label) {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(label))
                .locator("input[type='checkbox']")
                .first();
    }

    private Locator neededTab() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(NEEDED_TAB_TEXT));
    }

    private Locator neededTable() {
        return page.locator("table").first();
    }

    private Locator neededDataRows() {
        return neededTable().locator("tbody tr").filter(
                new Locator.FilterOptions().setHasNotText(NEEDED_EMPTY_TEXT)
                        .setHasNotText(NEEDED_FILTER_EMPTY_TEXT)
                        .setHasNotText(NEEDED_SOURCES_LABEL));
    }

    private Locator neededRow(String resourceName) {
        return neededDataRows().filter(new Locator.FilterOptions().setHasText(resourceName));
    }

    private int neededColumnIndex(String headerText) {
        Locator headers = neededTable().locator("thead th");
        int count = headers.count();
        for (int i = 0; i < count; i++) {
            String text = headers.nth(i).innerText();
            if (text != null && text.contains(headerText)) {
                return i;
            }
        }
        throw new IllegalStateException(
                "Column «" + headerText + "» not found in the needed-resources table header. Present: "
                        + headers.allInnerTexts());
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

    /** Value of a summary stat card below the execution table (e.g. «Загалом зроблено, шт»). */
    public String getSummaryStatValue(String label) {
        Locator card = summaryStatCard(label);
        card.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        Locator value = card.locator("p.text-xl.font-black");
        if (value.count() == 0) {
            value = card.locator("p.font-black").last();
        }
        String text = value.innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    /** Shortcut for the «Загалом зроблено, шт» summary card. */
    public String getTotalProducedPiecesSummary() {
        return getSummaryStatValue(TOTAL_PRODUCED_PCS_LABEL);
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

    /**
     * Summary stat card whose label matches {@code label}
     * (tk-ui shadcn Card with {@code data-slot="card"} in the stats grid below the table).
     */
    private Locator summaryStatCard(String label) {
        return page.locator("[data-slot='card']")
                .filter(new Locator.FilterOptions().setHasText(label))
                .first();
    }
}
