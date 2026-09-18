package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

/**
 * Сторінка «Точки вильоту» для комірника батальйону.
 * URL списку: {@code /fly-points}; картка точки: {@code /fly-points/{id}}.
 */
public class FlyPointsPage extends BasePage {

    public static final String PATH = "/fly-points";
    public static final String POINTS_TAB = "Точки вильоту";
    public static final String UNASSIGNED_CREWS_TAB = "Екіпажі без точок";
    public static final String ACTIVE_STATUS = "Активні";
    public static final String ALL_STATUSES = "Всі";
    public static final String NAME_ASC = "Назва (А → Я)";
    public static final String NAME_DESC = "Назва (Я → А)";

    private static final String SEARCH_PLACEHOLDER = "Пошук за назвою";
    private static final String STATUS_TRIGGER = "#fly-points-active";
    private static final String SORT_TRIGGER = "#fly-points-sort";

    public FlyPointsPage(Page page) {
        super(page);
    }

    public FlyPointsPage openViaSidebar() {
        ensureAppShell();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.TAB_FLY_POINTS);
        page.waitForURL(url -> url.contains(PATH),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return waitForLoaded();
    }

    public FlyPointsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, POINTS_TAB);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public FlyPointsPage waitForLoaded() {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                        .setName(POINTS_TAB)
                        .setExact(false))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        loading().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public String heading() {
        return page.locator("h1").first().innerText().trim();
    }

    public boolean isSidebarEntryVisible() {
        return new AppSidebarPage(page).isNavItemVisible(AppSidebarPage.TAB_FLY_POINTS);
    }

    public boolean isSidebarEntryActive() {
        return new AppSidebarPage(page).isNavItemActive(AppSidebarPage.TAB_FLY_POINTS);
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

    public boolean areFiltersVisible() {
        return page.getByPlaceholder(SEARCH_PLACEHOLDER).isVisible()
                && page.locator(STATUS_TRIGGER).isVisible()
                && page.locator(SORT_TRIGGER).isVisible();
    }

    public String selectedStatus() {
        return page.locator(STATUS_TRIGGER).innerText().trim();
    }

    public String selectedSort() {
        return page.locator(SORT_TRIGGER).innerText().trim();
    }

    public FlyPointsPage search(String value) {
        page.getByPlaceholder(SEARCH_PLACEHOLDER).fill(value);
        return this;
    }

    public FlyPointsPage selectStatus(String label) {
        select(STATUS_TRIGGER, label);
        return this;
    }

    public FlyPointsPage selectSort(String label) {
        select(SORT_TRIGGER, label);
        return this;
    }

    public FlyPointsPage openUnassignedCrews() {
        tab(UNASSIGNED_CREWS_TAB).click();
        page.waitForURL(url -> url.contains("/fly-points") && url.contains("view=crews"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        waitForLoadingHidden();
        return this;
    }

    public boolean hasPointCard(long id) {
        Locator card = pointCard(id);
        return card.count() > 0 && card.first().isVisible();
    }

    public boolean hasCrewCard(long id) {
        Locator card = crewCard(id);
        return card.count() > 0 && card.first().isVisible();
    }

    public String pointCardText(long id) {
        return pointCard(id).innerText().trim();
    }

    public String crewCardText(long id) {
        return crewCard(id).innerText().trim();
    }

    public String writeOffIssueTitle(long id) {
        Locator issue = pointCard(id).locator("[title^='Проблеми зі списанням:']");
        return issue.count() > 0 ? issue.first().getAttribute("title") : null;
    }

    public List<String> visiblePointNames() {
        return pointCards().locator("p[title]").allTextContents().stream()
                .map(String::trim)
                .toList();
    }

    public FlyPointDetailPage openPoint(long id) {
        pointCard(id).click();
        page.waitForURL(url -> url.contains(PATH + "/" + id),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new FlyPointDetailPage(page).waitForLoaded();
    }

    public void waitForPointCards(int count) {
        page.waitForCondition(() -> pointCards().count() == count,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    public void waitForCrewCards(int count) {
        page.waitForCondition(() -> crewCards().count() == count,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private void select(String triggerSelector, String label) {
        page.locator(triggerSelector).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions()
                        .setName(label)
                        .setExact(true))
                .click();
    }

    private Locator tab(String label) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions()
                .setName(label)
                .setExact(true)).first();
    }

    private Locator pointCard(long id) {
        return page.locator("a[href='/fly-points/" + id + "']").first();
    }

    private Locator crewCard(long id) {
        return page.locator("a[href='/fly-points/crews/" + id + "']").first();
    }

    private Locator pointCards() {
        return page.locator("a[href^='/fly-points/']:not([href^='/fly-points/crews/'])");
    }

    private Locator crewCards() {
        return page.locator("a[href^='/fly-points/crews/']");
    }

    private Locator loading() {
        return page.getByText("Завантаження...", new Page.GetByTextOptions().setExact(true)).first();
    }

    private void waitForLoadingHidden() {
        loading().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
    }

    private void ensureAppShell() {
        AppSidebarPage sidebar = new AppSidebarPage(page);
        if (!sidebar.isSidebarVisible()) {
            navigateTo(ConfigProvider.getBaseUrl(), "Home");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            sidebar.waitForSidebarLoaded();
        }
    }
}
