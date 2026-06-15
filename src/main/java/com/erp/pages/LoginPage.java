package com.erp.pages;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Page Object for the Keycloak login form.
 * Selectors are kept in sync with PlaywrightSessionProvider, which uses the same IDs.
 */
@Slf4j
public class LoginPage extends BasePage {

    private static final String USERNAME_SELECTOR = "#username";
    private static final String PASSWORD_SELECTOR = "#password";
    private static final String SUBMIT_SELECTOR   = "#kc-login";

    public LoginPage(Page page) {
        super(page);
    }

    /**
     * Navigate to the backend login endpoint which redirects to the Keycloak form.
     * Post-login redirect target defaults to {@code /}.
     */
    public LoginPage open(String baseUrl) {
        return open(baseUrl, "/");
    }

    /**
     * @param backendUrl backend root (e.g. https://host/server) — OAuth login endpoint
     * @param redirectUri post-login redirect target (frontend URL or path)
     */
    public LoginPage open(String backendUrl, String redirectUri) {
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        navigateTo(backendUrl + "/login?redirectUri=" + encodedRedirect, "Сторінка логіну");
        log.debug("Opened login page (redirectUri={}), waiting for Keycloak form...", redirectUri);
        waitForVisible(USERNAME_SELECTOR, uiTimeoutMs());
        return this;
    }

    public LoginPage enterUsername(String username) {
        page.fill(USERNAME_SELECTOR, username);
        return this;
    }

    public LoginPage enterPassword(String password) {
        page.fill(PASSWORD_SELECTOR, password);
        return this;
    }

    /**
     * Submit credentials and wait until the browser leaves Keycloak and lands on the app.
     *
     * @return the URL the browser landed on after a successful login
     */
    public String submitAndWaitForRedirect() {
        return submitAndWaitForRedirect("/");
    }

    /**
     * @param expectedPath path segment the final URL must contain (e.g. {@code /production})
     */
    public String submitAndWaitForRedirect(String expectedPath) {
        page.click(SUBMIT_SELECTOR);
        log.debug("Credentials submitted, waiting for redirect to path containing: {}", expectedPath);
        page.waitForURL(
                url -> !url.contains("/realms/") && url.contains(expectedPath),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs())
        );
        String landingUrl = page.url();
        log.info("Login redirect completed — landed on: {}", landingUrl);
        attachCurrentUrlLink("Після логіну");
        return landingUrl;
    }

    /**
     * Convenience: fill in credentials and submit in one call.
     */
    public String login(String username, String password) {
        return login(username, password, "/");
    }

    public String login(String username, String password, String expectedPath) {
        return enterUsername(username)
                .enterPassword(password)
                .submitAndWaitForRedirect(expectedPath);
    }

    /** Check whether the Keycloak login form is currently visible. */
    public boolean isLoginFormVisible() {
        return page.isVisible(USERNAME_SELECTOR) && page.isVisible(SUBMIT_SELECTOR);
    }

    /** Return the text of the first Keycloak error message, or null if none is shown. */
    public String getErrorMessage() {
        String errorSelector = "#input-error, .alert-error, [class*='error']";
        if (page.isVisible(errorSelector)) {
            return page.textContent(errorSelector).trim();
        }
        return null;
    }
}
