package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the authenticated app sidebar (navigation, workspace selector, user menu).
 * Selectors align with tk-ui {@code AppSidebar} / Radix sidebar ({@code data-sidebar="menu-button"}).
 *
 * <p>Grouped entries (production, plans, resources, …) render as a single sidebar link to the
 * first permitted child; sibling routes are switched via in-page {@code PageTabs}
 * ({@code role="tab"} / {@code data-slot="tabs-trigger"}).
 */
@Slf4j
public class AppSidebarPage extends BasePage {

    private static final String SIDEBAR_SELECTOR = "[data-sidebar='sidebar']";
    private static final String WORKSPACE_LABEL_TEXT = "Робочий простір";
    private static final String DICTIONARIES_LABEL_TEXT = "Словники";
    private static final String ALL_LOCATIONS_TEXT = "Всі локації";
    private static final String USER_MENU_SELECTOR = "[data-sidebar='menu-button']";
    private static final String LOGOUT_ITEM_TEXT   = "Вийти";
    private static final int    LOGOUT_TIMEOUT_MS  = 30_000;

    public static final String GROUP_PRODUCTION = "Виробництво";
    public static final String GROUP_PLANS = "Виробничі плани";
    public static final String GROUP_RESOURCES = "Довідники ресурсів";
    public static final String GROUP_EQUIPMENT = "Обладнання";
    public static final String GROUP_STORAGE = "Локації/Організми";
    public static final String GROUP_PROJECT_PRODUCTION = "Проєктне виробництво";
    public static final String GROUP_ORDERS = "Замовлення";
    public static final String GROUP_AUDIT = "Аудит";

    public static final String TAB_NON_SERIES = "Несерійне виробництво";
    public static final String TAB_ASSEMBLY_READINESS = "Готово до комплектації";
    public static final String TAB_SHIFTS = "Виробнича зміна";
    public static final String TAB_DEFECTS = "Брак";
    public static final String TAB_PRODUCTION_ANALYTICS = "Аналітика";
    public static final String TAB_DAILY_REPORT = "Денний звіт";
    public static final String TAB_GLOBAL_PLANS = "Глобальні плани";
    public static final String TAB_PLANS = "Виробничі плани";
    public static final String TAB_PLAN_EXECUTION = "Виконання плану";
    public static final String TAB_RESOURCES_DICT = "Словник ресурсів";
    public static final String TAB_PRICES = "Ціни";
    public static final String TAB_RESOURCE_CATEGORIES = "Категорії ресурсів";
    public static final String TAB_MEASUREMENT_UNITS = "Одиниці вимірювання";
    public static final String TAB_EQUIPMENT_CATEGORIES = "Категорії обладнання";
    public static final String TAB_EMPLOYEES = "Співробітники";
    public static final String TAB_ORDERS = "Замовлення";
    public static final String TAB_PRODUCTION_ORDERS = "Виробничі замовлення";
    public static final String TAB_ORDERS_ANALYTICS = "Аналітика";
    public static final String TAB_PRODUCTION_TASKS = "Завдання на локації";
    public static final String TAB_AUDIT_LOG = "Журнал аудиту";
    public static final String TAB_AUDIT_SESSIONS = "Сесії користувачів";
    public static final String TAB_PROJECT_PRODUCTION = "Проєктне виробництво";
    public static final String TAB_PROJECT_TEMPLATES = "Шаблони проєктного виробництва";
    public static final String TAB_PROJECT_CATEGORIES = "Категорії";
    public static final String TAB_PROJECT_PRODUCTS = "Продукти";
    public static final String NAV_UNIT_ANALYTICS = "Аналітика Підрозділів";

    public AppSidebarPage(Page page) {
        super(page);
    }

    public AppSidebarPage waitForSidebarLoaded() {
        waitForVisible(SIDEBAR_SELECTOR, uiTimeoutMs());
        return this;
    }

    /** True when the sidebar root is rendered. */
    public boolean isSidebarVisible() {
        Locator sidebar = page.locator(SIDEBAR_SELECTOR);
        return sidebar.count() > 0 && sidebar.first().isVisible();
    }

    /** Click a sidebar group (or ungrouped) link by its visible label. */
    public AppSidebarPage openGroup(String groupLabel) {
        Locator link = sidebarNavLink(groupLabel).first();
        link.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        link.click();
        return this;
    }

    /** Click an in-page {@code PageTabs} trigger by label. */
    public AppSidebarPage openPageTab(String tabLabel) {
        waitForPageTab(tabLabel);
        pageTab(tabLabel).first().click();
        return this;
    }

    /** Open a sidebar group, then switch to a PageTabs child. */
    public AppSidebarPage navigateToGroupedPage(String groupLabel, String tabLabel) {
        openGroup(groupLabel);
        if (!groupLabel.equals(tabLabel)) {
            openPageTab(tabLabel);
        }
        return this;
    }

    /** Waits until a PageTabs trigger with the given label is visible. */
    public AppSidebarPage waitForPageTab(String tabLabel) {
        pageTab(tabLabel).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    /** True when a PageTabs trigger with the given label is visible. */
    public boolean isPageTabVisible(String tabLabel) {
        Locator tab = pageTab(tabLabel);
        return tab.count() > 0 && tab.first().isVisible();
    }

    /** True when the «Робочий простір» storage selector block is visible. */
    public boolean isWorkspaceSelectorVisible() {
        if (!page.getByText(WORKSPACE_LABEL_TEXT).isVisible()) {
            return false;
        }
        return workspaceSelectorTrigger().isVisible();
    }

    /** Visible label on the workspace selector trigger (selected storage name). */
    public String getSelectedLocationName() {
        return normalizeText(workspaceSelectorTrigger().innerText());
    }

    /**
     * Opens the workspace dropdown and returns the first selectable storage name
     * (skips «Всі локації» and hierarchy group-only nodes).
     */
    public String getFirstAvailableLocationName() {
        workspaceSelectorTrigger().click();
        String name = firstSelectableWorkspaceLabel();
        page.keyboard().press("Escape");
        return name;
    }

    /** Opens workspace dropdown and returns all visible selectable location labels. */
    public java.util.List<String> collectWorkspaceLocationLabels() {
        workspaceSelectorTrigger().click();
        workspaceOptionButtons().first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        expandAllWorkspaceNodes();
        java.util.List<String> labels = collectSelectableWorkspaceLabels();
        page.keyboard().press("Escape");
        return labels;
    }

    public AppSidebarPage selectWorkspaceByName(String locationName) {
        workspaceSelectorTrigger().click();
        workspaceOptionButtons().first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        expandAllWorkspaceNodes();
        Locator search = page.locator("[data-radix-popper-content-wrapper] input[placeholder='Пошук...']");
        if (search.count() > 0) {
            search.first().fill(locationName);
            page.waitForTimeout(400);
        }
        Locator option = workspaceOptionButtons()
                .filter(new Locator.FilterOptions().setHasText(locationName))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();
        return this;
    }

    /** True if workspace tree search finds a selectable option containing {@code nameFragment}. */
    public boolean isWorkspaceOptionVisible(String nameFragment) {
        workspaceSelectorTrigger().click();
        workspaceOptionButtons().first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        expandAllWorkspaceNodes();
        Locator search = page.locator("[data-radix-popper-content-wrapper] input[placeholder='Пошук...']");
        if (search.count() > 0) {
            search.first().fill(nameFragment.trim());
            page.waitForTimeout(400);
        }
        Locator match = workspaceOptionButtons()
                .filter(new Locator.FilterOptions().setHasText(nameFragment.trim()));
        boolean found = false;
        try {
            match.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(Math.min(5_000, uiTimeoutMs())));
            found = match.count() > 0;
        } catch (RuntimeException ignored) {
            found = match.count() > 0;
        }
        page.keyboard().press("Escape");
        return found;
    }

    public AppSidebarPage selectAllLocations() {
        workspaceSelectorTrigger().click();
        workspaceOptionButtons()
                .filter(new Locator.FilterOptions().setHasText(ALL_LOCATIONS_TEXT))
                .first()
                .click();
        return this;
    }

    /** True when a sidebar nav link with the given label is visible. */
    public boolean isNavItemVisible(String label) {
        Locator link = sidebarNavLink(label);
        return link.count() > 0 && link.first().isVisible();
    }

    /** True when the sidebar nav link is marked active ({@code data-active="true"}). */
    public boolean isNavItemActive(String label) {
        Locator activeButton = page.locator("[data-sidebar='menu-button'][data-active='true']")
                .filter(new Locator.FilterOptions().setHasText(label));
        return activeButton.count() > 0 && activeButton.first().isVisible();
    }

    /** True when the «Словники» section header is visible. */
    public boolean isDictionariesSectionVisible() {
        return page.getByText(DICTIONARIES_LABEL_TEXT, new Page.GetByTextOptions().setExact(true)).isVisible();
    }

    /** True when a dictionary sidebar link with the given label is visible. */
    public boolean isDictionaryItemVisible(String label) {
        return isNavItemVisible(label);
    }

    /** True when the sidebar footer user menu trigger is visible. */
    public boolean isUserMenuVisible() {
        Locator menu = userMenuButtonInFooter();
        return menu.count() > 0 && menu.first().isVisible();
    }

    /** True when the sidebar footer shows the given username. */
    public boolean isUserMenuVisible(String username) {
        Locator menu = userMenuButtonInFooter(username);
        return menu.count() > 0 && menu.first().isVisible();
    }

    /** Open the user dropdown in the sidebar footer (footer menu button). */
    public AppSidebarPage openUserMenu() {
        log.debug("Opening sidebar footer user menu");
        userMenuButtonInFooter().first().click();
        waitForLogoutMenuItem();
        return this;
    }

    /** Open the user dropdown in the sidebar footer when username is shown in the trigger. */
    public AppSidebarPage openUserMenu(String username) {
        log.debug("Opening user menu for: {}", username);
        Locator menu = userMenuButtonInFooter(username);
        if (menu.count() > 0) {
            menu.first().click();
        } else {
            openUserMenu();
        }
        waitForLogoutMenuItem();
        return this;
    }

    /** Click "Вийти" in the open user dropdown (triggers backend /logout redirect chain). */
    public AppSidebarPage logout() {
        log.debug("Clicking logout menu item");
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(LOGOUT_ITEM_TEXT)).click();
        return this;
    }

    /**
     * Wait until the browser reaches an unauthenticated state (Keycloak or login URL).
     * Falls back to navigating to a protected route if the redirect chain stalls on the SPA.
     */
    public AppSidebarPage waitForLoggedOut() {
        try {
            page.waitForURL(
                    AppSidebarPage::isUnauthenticatedUrl,
                    new Page.WaitForURLOptions().setTimeout(LOGOUT_TIMEOUT_MS)
            );
        } catch (Exception e) {
            log.debug("Direct logout redirect not detected — navigating to protected route: {}", e.getMessage());
            page.navigate(ConfigProvider.getBaseUrl() + "/production");
            page.waitForURL(
                    AppSidebarPage::isUnauthenticatedUrl,
                    new Page.WaitForURLOptions().setTimeout(LOGOUT_TIMEOUT_MS)
            );
        }
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("Logout completed — current URL: {}", page.url());
        attachCurrentUrlLink("Після logout");
        return this;
    }

    private Locator userMenuButtonInFooter() {
        return page.locator("[data-sidebar='footer']")
                .locator(USER_MENU_SELECTOR)
                .last();
    }

    private Locator userMenuButtonInFooter(String username) {
        return page.locator("[data-sidebar='footer']")
                .locator(USER_MENU_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(username));
    }

    private void waitForLogoutMenuItem() {
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(LOGOUT_ITEM_TEXT))
                .waitFor(new Locator.WaitForOptions().setTimeout(5_000));
    }

    private Locator workspaceSelectorTrigger() {
        return page.locator(SIDEBAR_SELECTOR)
                .getByRole(AriaRole.COMBOBOX)
                .first();
    }

    /** StorageTreeSelect options are plain {@code <button>} rows (not role=option). */
    private Locator workspaceOptionButtons() {
        return page.locator("[data-radix-popper-content-wrapper]")
                .locator("button[type='button']");
    }

    private void expandAllWorkspaceNodes() {
        for (int round = 0; round < 20; round++) {
            Locator chevrons = page.locator("[data-radix-popper-content-wrapper] span[role='button']");
            int before = workspaceOptionButtons().count();
            boolean clicked = false;
            for (int i = 0; i < chevrons.count(); i++) {
                Locator svg = chevrons.nth(i).locator("svg");
                String cls = svg.count() > 0 ? svg.first().getAttribute("class") : "";
                if (cls == null || !cls.contains("rotate-90")) {
                    chevrons.nth(i).click();
                    clicked = true;
                    break;
                }
            }
            if (!clicked || workspaceOptionButtons().count() == before) {
                break;
            }
        }
    }

    private String firstSelectableWorkspaceLabel() {
        Locator options = workspaceOptionButtons();
        options.first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        for (int i = 0; i < options.count(); i++) {
            Locator option = options.nth(i);
            String text = normalizeText(option.innerText());
            if (text.isBlank() || ALL_LOCATIONS_TEXT.equals(text)) {
                continue;
            }
            String title = option.getAttribute("title");
            if (title != null && title.contains("групування")) {
                continue;
            }
            return text;
        }
        throw new IllegalStateException("No selectable workspace location found in StorageTreeSelect");
    }

    private java.util.List<String> collectSelectableWorkspaceLabels() {
        Locator options = workspaceOptionButtons();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < options.count(); i++) {
            Locator option = options.nth(i);
            String text = normalizeText(option.innerText());
            if (text.isBlank() || ALL_LOCATIONS_TEXT.equals(text)) {
                continue;
            }
            String title = option.getAttribute("title");
            if (title != null && title.contains("групування")) {
                continue;
            }
            labels.add(text);
        }
        return labels;
    }

    private Locator sidebarNavLink(String label) {
        return page.locator(SIDEBAR_SELECTOR)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(label));
    }

    private Locator pageTab(String tabLabel) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabLabel))
                .or(page.locator("[data-slot='tabs-trigger']")
                        .filter(new Locator.FilterOptions().setHasText(tabLabel)));
    }

    private static String normalizeText(String value) {
        return value != null ? value.trim().replaceAll("\\s+", " ") : "";
    }

    private static boolean isUnauthenticatedUrl(String url) {
        return url.contains("/realms/") || url.contains("/login");
    }
}
