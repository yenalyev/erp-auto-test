package com.erp.utils.auth;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Performs OAuth2 session login using a real headless Chromium browser (Playwright).
 * Login URL and API base URI both target {@link ConfigProvider#getBackendUrl()}.
 */
@Slf4j
public class PlaywrightSessionProvider implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;
    private final String backendUrl;

    public PlaywrightSessionProvider(String backendUrl) {
        this.backendUrl = backendUrl.replaceAll("/+$", "");
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
        log.info("🎭 Playwright browser initialized (headless Chromium), backend={}", this.backendUrl);
    }

    public Browser getBrowser() {
        return browser;
    }

    /**
     * Browser OAuth2 login → JSESSIONID cookies → verify {@code GET /api/v1/users/me}.
     */
    public Map<String, String> getSession(String username, String password) {
        log.info("🎭 Starting browser-based OAuth2 login for user: {}", username);

        String redirectTarget = ConfigProvider.getBaseUrl();
        String loginUrl = backendUrl + "/login?redirectUri="
                + URLEncoder.encode(redirectTarget, StandardCharsets.UTF_8);

        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {

            int timeoutMs = ConfigProvider.getTimeout() * 1000;

            Page page = context.newPage();
            page.setDefaultTimeout(timeoutMs);
            page.setDefaultNavigationTimeout(timeoutMs);

            page.navigate(loginUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(timeoutMs));
            log.debug("🎭 Navigated to {}, waiting for Keycloak form...", loginUrl);

            page.waitForSelector("#username", new Page.WaitForSelectorOptions().setTimeout(timeoutMs));
            page.fill("#username", username);
            page.fill("#password", password);
            // noWaitAfter: avoid hanging on "waiting for scheduled navigations" after OAuth redirect
            page.click("#kc-login", new Page.ClickOptions().setNoWaitAfter(true));

            Map<String, String> cookies = waitForAuthenticatedSession(context, page, username, timeoutMs);

            log.info("✅ Browser session acquired for user: {} (JSESSIONID: {}...)",
                    username, cookies.get("JSESSIONID").substring(0, Math.min(8, cookies.get("JSESSIONID").length())));

            return cookies;
        }
    }

    /**
     * Poll until {@code GET /api/v1/users/me} succeeds — JSESSIONID from Keycloak alone is not enough.
     */
    private Map<String, String> waitForAuthenticatedSession(
            BrowserContext context, Page page, String username, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        IllegalStateException lastError = null;

        while (System.currentTimeMillis() < deadline) {
            Map<String, String> cookies = toCookieMap(context);
            if (cookies.containsKey("JSESSIONID")) {
                try {
                    verifyApiSession(cookies, username);
                    return cookies;
                } catch (IllegalStateException e) {
                    lastError = e;
                }
            }
            page.waitForTimeout(500);
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException(
                "OAuth login timed out after " + timeoutMs + "ms — no authenticated session");
    }

    private static Map<String, String> toCookieMap(BrowserContext context) {
        Map<String, String> cookies = new HashMap<>();
        for (Cookie c : context.cookies()) {
            cookies.put(c.name, c.value);
        }
        return cookies;
    }

    private void verifyApiSession(Map<String, String> cookies, String username) {
        var response = given()
                .baseUri(backendUrl)
                .cookies(cookies)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/users/me")
                .then()
                .extract()
                .response();

        if (response.statusCode() != 200) {
            throw new IllegalStateException(String.format(
                    "Session login for '%s' failed API check GET /api/v1/users/me — status %d, body: %s",
                    username, response.statusCode(),
                    response.getBody().asString().substring(0, Math.min(200, response.getBody().asString().length()))));
        }
        log.info("✅ Session verified via /api/v1/users/me for user: {}", username);
    }

    @Override
    public void close() {
        try {
            browser.close();
        } catch (Exception e) {
            log.warn("⚠️  Error closing Playwright browser: {}", e.getMessage());
        }
        try {
            playwright.close();
        } catch (Exception e) {
            log.warn("⚠️  Error closing Playwright instance: {}", e.getMessage());
        }
        log.info("🎭 Playwright browser closed");
    }
}
