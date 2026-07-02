package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the authenticated app sidebar (navigation, workspace selector, user menu).
 * Selectors align with tk-ui {@code AppSidebar} / Radix sidebar ({@code data-sidebar="menu-button"}).
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

    public AppSidebarPage(Page page) {
        super(page);
    }

    /** True when the sidebar root is rendered. */
    public boolean isSidebarVisible() {
        Locator sidebar = page.locator(SIDEBAR_SELECTOR);
        return sidebar.count() > 0 && sidebar.first().isVisible();
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
     * Opens the workspace dropdown and returns the first concrete storage name
     * (skips «Всі локації» when present).
     */
    public String getFirstAvailableLocationName() {
        workspaceSelectorTrigger().click();
        Locator firstStorage = page.locator("[data-radix-popper-content-wrapper] [role='option']")
                .filter(new Locator.FilterOptions().setHasNotText(ALL_LOCATIONS_TEXT))
                .first();
        firstStorage.waitFor(new Locator.WaitForOptions().setTimeout(5_000));
        String name = normalizeText(firstStorage.innerText());
        page.keyboard().press("Escape");
        return name;
    }

    /** Opens workspace dropdown and returns all visible location labels. */
    public java.util.List<String> collectWorkspaceLocationLabels() {
        workspaceSelectorTrigger().click();
        Locator options = page.locator("[data-radix-popper-content-wrapper] [role='option']");
        options.first().waitFor(new Locator.WaitForOptions().setTimeout(5_000));
        int count = options.count();
        java.util.List<String> labels = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String text = normalizeText(options.nth(i).innerText());
            if (!text.isBlank() && !ALL_LOCATIONS_TEXT.equals(text)) {
                labels.add(text);
            }
        }
        page.keyboard().press("Escape");
        return labels;
    }

    public AppSidebarPage selectWorkspaceByName(String locationName) {
        workspaceSelectorTrigger().click();
        page.locator("[data-radix-popper-content-wrapper] [role='option']")
                .filter(new Locator.FilterOptions().setHasText(locationName))
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

    private Locator sidebarNavLink(String label) {
        return page.locator(SIDEBAR_SELECTOR)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(label));
    }

    private static String normalizeText(String value) {
        return value != null ? value.trim().replaceAll("\\s+", " ") : "";
    }

    private static boolean isUnauthenticatedUrl(String url) {
        return url.contains("/realms/") || url.contains("/login");
    }
}
