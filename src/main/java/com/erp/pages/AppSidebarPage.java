package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the authenticated app sidebar (user menu and logout).
 * Selectors align with tk-ui {@code NavUser} / Radix sidebar ({@code data-sidebar="menu-button"}).
 */
@Slf4j
public class AppSidebarPage extends BasePage {

    private static final String USER_MENU_SELECTOR = "[data-sidebar='menu-button']";
    private static final String LOGOUT_ITEM_TEXT   = "Вийти";
    private static final int    LOGOUT_TIMEOUT_MS  = 30_000;

    public AppSidebarPage(Page page) {
        super(page);
    }

    /** True when the sidebar footer shows the given username. */
    public boolean isUserMenuVisible(String username) {
        Locator menu = userMenuButton(username);
        return menu.count() > 0 && menu.first().isVisible();
    }

    /** Open the user dropdown in the sidebar footer. */
    public AppSidebarPage openUserMenu(String username) {
        log.debug("Opening user menu for: {}", username);
        userMenuButton(username).first().click();
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(LOGOUT_ITEM_TEXT))
                .waitFor(new Locator.WaitForOptions().setTimeout(5_000));
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

    private Locator userMenuButton(String username) {
        return page.locator(USER_MENU_SELECTOR).filter(new Locator.FilterOptions().setHasText(username));
    }

    private static boolean isUnauthenticatedUrl(String url) {
        return url.contains("/realms/") || url.contains("/login");
    }
}
