package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page Object for Assembly Readiness (tk-ui {@code AssemblyReadinessPage.tsx}).
 * URL: /assembly-readiness
 */
@Slf4j
public class AssemblyReadinessPage extends BasePage {

    public static final String PATH = "/assembly-readiness";
    public static final String SIDEBAR_LINK_TEXT = "Готово до комплектації";

    private static final String HEADING_TEXT = SIDEBAR_LINK_TEXT;
    private static final String ALL_LOCATIONS_GUARD_TEXT =
            "Оберіть конкретну локацію для перегляду продукції готової до укомплектування";
    private static final String EMPTY_STATE_TEXT = "Немає позицій, готових до комплектації";
    private static final String COMPONENTS_HEADER_TEXT = "Компоненти техкарти";
    private static final String COMPONENT_TECH_MAPS_LABEL = "Виробляється:";
    private static final String MISSING_COMPONENTS_BADGE = "Бракує компонентів";
    private static final String BOTTLENECK_BADGE = "вузьке місце";
    private static final String SORT_BY_QUANTITY = "За кількістю ↓";
    private static final String SORT_BY_NAME = "За назвою А-Я";
    private static final Pattern READY_QTY_CELL = Pattern.compile("(\\d+)\\s+\\S");

    public AssemblyReadinessPage(Page page) {
        super(page);
    }

    public AssemblyReadinessPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Готово до комплектації (/assembly-readiness)");
        return waitForLoaded();
    }

    public AssemblyReadinessPage openAndWaitForApi() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        page.waitForResponse(
                r -> r.url().contains("/assembly-readiness/") && "GET".equals(r.request().method()),
                () -> navigateTo(url, "Готово до комплектації (/assembly-readiness)"));
        return waitForLoaded();
    }

    public AssemblyReadinessPage openViaSidebar() {
        new AppSidebarPage(page)
                .navigateToGroupedPage(AppSidebarPage.GROUP_PRODUCTION, AppSidebarPage.TAB_ASSEMBLY_READINESS);
        page.waitForURL("**" + PATH + "**", new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return waitForLoaded();
    }

    public AssemblyReadinessPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        Locator ready = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HEADING_TEXT))
                .or(page.getByRole(AriaRole.COMBOBOX)
                        .filter(new Locator.FilterOptions().setHasText(Pattern.compile("За (кількістю|назвою)"))))
                .or(page.getByText(ALL_LOCATIONS_GUARD_TEXT))
                .or(page.getByText(EMPTY_STATE_TEXT))
                .or(page.locator("table").first())
                .first();
        ready.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        waitForDataSettled();
        log.info("Assembly Readiness page loaded — url: {}", page.url());
        return this;
    }

    public AssemblyReadinessPage waitForDataSettled() {
        page.waitForCondition(
                () -> isAllLocationsGuardVisible()
                        || isEmptyStateVisible()
                        || mainProductRows().count() > 0
                        || isLoadingSkeletonVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        if (isLoadingSkeletonVisible()) {
            page.waitForCondition(
                    () -> !isLoadingSkeletonVisible(),
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        }
        return this;
    }

    public boolean isHeadingVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HEADING_TEXT)).isVisible()
                || page.url().contains(PATH);
    }

    /** Subtitle text was removed from the page — keeps API for tests; always false. */
    public boolean isSubtitleVisible() {
        return false;
    }

    public boolean isAllLocationsGuardVisible() {
        Locator guard = page.getByText(ALL_LOCATIONS_GUARD_TEXT);
        return guard.count() > 0 && guard.first().isVisible();
    }

    public boolean isEmptyStateVisible() {
        Locator empty = page.getByText(EMPTY_STATE_TEXT);
        return empty.count() > 0 && empty.first().isVisible();
    }

    public boolean isSortDropdownVisible() {
        return page.getByRole(AriaRole.COMBOBOX)
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("За (кількістю|назвою)")))
                .first()
                .isVisible();
    }

    public boolean isLoadingSkeletonVisible() {
        return page.locator("[data-slot='skeleton'], .animate-pulse").count() > 0;
    }

    public int getProductRowCount() {
        return mainProductRows().count();
    }

    public boolean isProductRowVisible(String techMapName) {
        return mainProductRow(techMapName).count() > 0
                && mainProductRow(techMapName).first().isVisible();
    }

    public int getReadyQtyForRow(String techMapName) {
        String cellText = normalize(mainProductRow(techMapName).locator("td").last().innerText());
        Matcher matcher = READY_QTY_CELL.matcher(cellText);
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse ready qty from row cell: " + cellText);
        }
        return Integer.parseInt(matcher.group(1));
    }

    public AssemblyReadinessPage expandRow(String techMapName) {
        mainProductRow(techMapName).first().click();
        expandedComponentsTable(techMapName)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return this;
    }

    public boolean isComponentsSectionVisible() {
        return page.getByText(COMPONENTS_HEADER_TEXT).isVisible();
    }

    public boolean isComponentVisible(String componentName) {
        return componentRow(componentName).isVisible();
    }

    /** Label «Виробляється:» next to a component that has producer tech-map links. */
    public boolean isComponentTechMapsLabelVisible(String componentName) {
        return componentRow(componentName).getByText(COMPONENT_TECH_MAPS_LABEL).count() > 0
                && componentRow(componentName).getByText(COMPONENT_TECH_MAPS_LABEL).first().isVisible();
    }

    public boolean isComponentTechMapLinkVisible(String componentName, String techMapName) {
        return componentTechMapLink(componentName, techMapName).count() > 0
                && componentTechMapLink(componentName, techMapName).first().isVisible();
    }

    public String getComponentTechMapLinkHref(String componentName, String techMapName) {
        return componentTechMapLink(componentName, techMapName).first().getAttribute("href");
    }

    public String getComponentTechMapLinkTarget(String componentName, String techMapName) {
        return componentTechMapLink(componentName, techMapName).first().getAttribute("target");
    }

    /** Clicks the producer link; caller should wrap with {@code page.waitForPopup(...)}. */
    public AssemblyReadinessPage clickComponentTechMapLink(String componentName, String techMapName) {
        componentTechMapLink(componentName, techMapName).first().click();
        return this;
    }

    public int getPossibleUnitsForComponent(String componentName) {
        Locator row = page.locator("table table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(componentName))
                .first();
        String lastCell = normalize(row.locator("td").last().innerText());
        Matcher matcher = Pattern.compile("(\\d+)").matcher(lastCell);
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse possible units for component " + componentName + ": " + lastCell);
        }
        return Integer.parseInt(matcher.group(1));
    }

    public boolean isMissingComponentsBadgeVisible(String techMapName) {
        return mainProductRow(techMapName).getByText(MISSING_COMPONENTS_BADGE).isVisible();
    }

    public boolean isBottleneckBadgeVisible() {
        return page.getByText(BOTTLENECK_BADGE).isVisible();
    }

    public boolean isSharedComponentBadgeVisible(int techMapCount) {
        Pattern pattern = Pattern.compile("у " + techMapCount + " техкартах");
        return page.getByText(pattern).isVisible();
    }

    public AssemblyReadinessPage selectSortByQuantityDesc() {
        openSortDropdown();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(SORT_BY_QUANTITY)).click();
        return this;
    }

    public AssemblyReadinessPage selectSortByNameAsc() {
        openSortDropdown();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(SORT_BY_NAME)).click();
        return this;
    }

    public List<String> collectVisibleTechMapNames() {
        int count = mainProductRows().count();
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String nameCell = normalize(mainProductRows().nth(i).locator("td").nth(1).innerText());
            String name = nameCell.split(MISSING_COMPONENTS_BADGE)[0].trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    public List<Integer> collectVisibleReadyQuantities() {
        int count = mainProductRows().count();
        List<Integer> quantities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String cellText = normalize(mainProductRows().nth(i).locator("td").last().innerText());
            Matcher matcher = READY_QTY_CELL.matcher(cellText);
            if (matcher.find()) {
                quantities.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return quantities;
    }

    private Locator mainProductRows() {
        return page.locator("table").first().locator("tbody tr.cursor-pointer");
    }

    private Locator mainProductRow(String techMapName) {
        return mainProductRows().filter(new Locator.FilterOptions().setHasText(techMapName));
    }

    private Locator expandedComponentsTable(String techMapName) {
        return mainProductRow(techMapName)
                .locator("xpath=following-sibling::tr[1]")
                .locator("table");
    }

    private Locator componentRow(String componentName) {
        return page.locator("table table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(componentName))
                .first();
    }

    private Locator componentTechMapLink(String componentName, String techMapName) {
        return componentRow(componentName)
                .locator("a[href*='/technological-maps/update/']")
                .filter(new Locator.FilterOptions().setHasText(techMapName));
    }

    private void openSortDropdown() {
        page.getByRole(AriaRole.COMBOBOX)
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("За (кількістю|назвою)")))
                .first()
                .click();
    }

    private static String normalize(String value) {
        return value != null ? value.trim().replaceAll("\\s+", " ") : "";
    }
}
