package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GlobalPlansPage extends BasePage {

    private static final String PATH = "/global-plans";
    private static final String LIST_TAB = "Глобальні плани";
    private static final String CREATE_BUTTON_TEXT = "Новий Глобальний план";
    private static final String DELETE_BUTTON_TITLE = "Видалити";
    private static final String LIST_VIEW_TEST_ID = "global-plans-view-toggle-list";
    private static final String RESOURCES_VIEW_TEST_ID = "global-plans-view-toggle-resources";
    private static final String CATEGORY_FILTER_TEST_ID = "global-plans-resources-category-filter";

    public GlobalPlansPage(Page page) {
        super(page);
    }

    public GlobalPlansPage open() {
        return openPath(PATH);
    }

    public GlobalPlansPage openResourcesView() {
        return openPath(PATH + "?view=resources");
    }

    private GlobalPlansPage openPath(String path) {
        String url = ConfigProvider.getBaseUrl() + path;
        navigateTo(url, "Глобальні плани (/global-plans)");
        return waitForLoaded();
    }

    public GlobalPlansPage openFromSidebar() {
        new AppSidebarPage(page)
                .navigateToGroupedPage(AppSidebarPage.GROUP_PLANS, AppSidebarPage.TAB_GLOBAL_PLANS);
        return waitForLoaded();
    }

    public GlobalPlansPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        Locator ready = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(LIST_TAB))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT)))
                .or(page.locator("table").first())
                .first();
        ready.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return waitForPlansLoaded();
    }

    /** Wait for the async GET /global-plans render, not only for the static page tabs/CTA. */
    public GlobalPlansPage waitForPlansLoaded() {
        page.waitForCondition(
                () -> !isPlansSpinnerVisible()
                        && (page.locator("table").count() > 0
                        || page.getByText("Немає глобальних планів").count() > 0
                        || page.getByText("Немає ресурсів для відображення").count() > 0
                        || page.getByText("Не вдалося завантажити глобальні плани").count() > 0),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Глобальні плани: перемкнутися на pivot «Ресурси»")
    public GlobalPlansPage switchToResourcesView() {
        viewToggle(RESOURCES_VIEW_TEST_ID).click();
        page.waitForCondition(
                () -> page.url().contains("view=resources") && isResourcesViewSelected(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return waitForPlansLoaded();
    }

    @Step("Глобальні плани: перемкнутися на «Список»")
    public GlobalPlansPage switchToListView() {
        viewToggle(LIST_VIEW_TEST_ID).click();
        page.waitForCondition(
                () -> !page.url().contains("view=") && isListViewSelected(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return waitForPlansLoaded();
    }

    public boolean isListViewSelected() {
        return isToggleSelected(viewToggle(LIST_VIEW_TEST_ID));
    }

    public boolean isResourcesViewSelected() {
        return isToggleSelected(viewToggle(RESOURCES_VIEW_TEST_ID));
    }

    public boolean isResourcesViewVisible() {
        return categoryFilter().isVisible() && pivotTable().isVisible();
    }

    public List<String> getPivotPeriodHeaders() {
        List<String> headers = pivotTable().locator("thead th").allInnerTexts();
        return headers.stream().skip(1).map(String::trim).toList();
    }

    public String getPivotAmount(Long resourceId, String periodLabel) {
        List<String> headers = pivotTable().locator("thead th").allInnerTexts().stream()
                .map(String::trim)
                .toList();
        int columnIndex = headers.indexOf(periodLabel);
        if (columnIndex < 1) {
            throw new IllegalArgumentException("Pivot period column not found: " + periodLabel + "; headers=" + headers);
        }
        return pivotResourceRow(resourceId).locator("td").nth(columnIndex).innerText().trim();
    }

    public String getPivotResourceRowText(Long resourceId) {
        return pivotResourceRow(resourceId).innerText().trim().replaceAll("\\s+", " ");
    }

    public int getPivotResourceRowCount(Long resourceId) {
        return pivotResourceRow(resourceId).count();
    }

    public boolean isPivotResourceVisible(Long resourceId) {
        Locator row = pivotResourceRow(resourceId);
        return row.count() > 0 && row.first().isVisible();
    }

    @Step("Глобальні плани pivot: перемкнути категорію «{categoryName}»")
    public GlobalPlansPage togglePivotCategory(String categoryName) {
        Locator input = categoryFilter().locator("input").first();
        input.click();
        page.getByRole(AriaRole.OPTION,
                        new Page.GetByRoleOptions().setName(categoryName).setExact(true))
                .click();
        page.keyboard().press("Escape");
        return this;
    }

    public GlobalPlansPage waitForPivotResourceVisibility(Long resourceId, boolean visible) {
        page.waitForCondition(
                () -> isPivotResourceVisible(resourceId) == visible,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public GlobalPlanWizardPage clickCreatePlan() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT))
                .click();
        return new GlobalPlanWizardPage(page).waitForLoaded();
    }

    /** True when the «Глобальні плани» PageTab (or create CTA) is visible — list h1 was removed. */
    public boolean isListHeadingVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(LIST_TAB)).isVisible()
                || page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON_TEXT))
                .isVisible();
    }

    public boolean isPlanVisibleInList(String descriptionFragment) {
        return planRow(descriptionFragment).count() > 0;
    }

    @Step("Список: дочекатися появи плану «{descriptionFragment}»")
    public GlobalPlansPage waitForPlanVisible(String descriptionFragment) {
        Locator spinner = page.locator("i.fa-spinner.fa-spin");
        if (spinner.count() > 0) {
            spinner.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
        planRow(descriptionFragment).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Список: дочекатися відсутності плану «{descriptionFragment}»")
    public GlobalPlansPage waitForPlanAbsent(String descriptionFragment) {
        Locator row = planRow(descriptionFragment);
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.DETACHED)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Список: видалити план «{descriptionFragment}»")
    public GlobalPlansPage deletePlanAndConfirm(String descriptionFragment) {
        log.info("Global plans list — delete plan matching: {}", descriptionFragment);
        page.onceDialog(Dialog::accept);
        planRow(descriptionFragment)
                .getByTitle(DELETE_BUTTON_TITLE)
                .click();
        page.waitForTimeout(500);
        return this;
    }

    private Locator planRow(String descriptionFragment) {
        return page.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(descriptionFragment));
    }

    private boolean isPlansSpinnerVisible() {
        Locator spinner = page.locator("i.fa-spinner.fa-spin");
        return spinner.count() > 0 && spinner.first().isVisible();
    }

    private Locator viewToggle(String testId) {
        return page.getByTestId(testId);
    }

    private boolean isToggleSelected(Locator toggle) {
        return "on".equalsIgnoreCase(toggle.getAttribute("data-state"))
                || Boolean.parseBoolean(toggle.getAttribute("aria-pressed"));
    }

    private Locator categoryFilter() {
        return page.getByTestId(CATEGORY_FILTER_TEST_ID);
    }

    private Locator pivotTable() {
        return page.locator("table")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("thead th").filter(new Locator.FilterOptions().setHasText("Ресурс"))))
                .first();
    }

    private Locator pivotResourceRow(Long resourceId) {
        return page.getByTestId("global-plans-resources-row-" + resourceId);
    }
}
