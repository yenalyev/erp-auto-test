package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the RBAC forbidden screen shown by {@code RouteGuard}.
 */
@Slf4j
public class AccessForbiddenPage extends BasePage {

    public static final String FORBIDDEN_MESSAGE = "У вас немає прав для перегляду цієї сторінки.";

    public AccessForbiddenPage(Page page) {
        super(page);
    }

    public AccessForbiddenPage open(String path) {
        String url = ConfigProvider.getBaseUrl() + path;
        log.info("Opening forbidden-route probe: {}", url);
        navigateTo(url, "Access probe " + path);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public AccessForbiddenPage waitForLoaded() {
        page.getByText(FORBIDDEN_MESSAGE)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isForbiddenMessageVisible() {
        return page.getByText(FORBIDDEN_MESSAGE).isVisible();
    }
}
