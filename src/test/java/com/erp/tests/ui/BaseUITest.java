package com.erp.tests.ui;

import com.erp.tests.BaseTest;
import com.erp.utils.auth.PlaywrightSessionProvider;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.util.Map;

/**
 * Base class for all UI (end-to-end) tests.
 *
 * Lifecycle:
 *  - Suite-level Browser is shared via BaseTest → PlaywrightSessionProvider (single Chromium process).
 *  - Each test class gets its own BrowserContext (isolated cookies / storage).
 *  - Each test method gets its own Page, closed in @AfterMethod.
 *  - On test failure a full-page screenshot is attached to the Allure report automatically.
 */
@Slf4j
public abstract class BaseUITest extends BaseTest {

    protected BrowserContext browserContext;
    protected Page page;

    /** UI tests authenticate via Playwright browser flow — no DB pre-flight needed. */
    @Override
    protected boolean shouldInitializeDatabase() {
        return false;
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        log.info("Setting up UI test context for: {}", this.getClass().getSimpleName());

        PlaywrightSessionProvider provider = getPlaywrightSessionProvider();
        if (provider == null) {
            throw new IllegalStateException(
                    "PlaywrightSessionProvider is not initialised. " +
                    "Make sure Chromium is installed: mvn exec:java@install-chromium");
        }

        Browser browser = provider.getBrowser();
        browserContext = browser.newContext(
                new Browser.NewContextOptions().setIgnoreHTTPSErrors(true)
        );
        log.info("BrowserContext created for: {}", this.getClass().getSimpleName());
    }

    @AfterClass(alwaysRun = true)
    @Override
    public void classTeardown() {
        if (browserContext != null) {
            try {
                browserContext.close();
                log.info("BrowserContext closed for: {}", this.getClass().getSimpleName());
            } catch (Exception e) {
                log.warn("Error closing BrowserContext: {}", e.getMessage());
            }
        }
        super.classTeardown();
    }

    /**
     * Open a fresh page before each test. Subclasses can override and call super
     * if they need additional per-method setup.
     */
    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        super.testSetup();
        page = browserContext.newPage();
        int timeoutMs = com.erp.utils.config.ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
        log.debug("New Page created with UI timeout {}ms", timeoutMs);
    }

    /**
     * Close the page after each test and attach a screenshot to Allure on failure.
     */
    @AfterMethod(alwaysRun = true)
    public void uiTestTeardown(ITestResult result) {
        if (page != null) {
            if (!result.isSuccess()) {
                attachScreenshotOnFailure(result.getName());
            }
            try {
                page.close();
            } catch (Exception e) {
                log.warn("Error closing Page: {}", e.getMessage());
            }
            page = null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Inject session cookies into the current BrowserContext so that subsequent
     * page navigations are already authenticated (skips the login form).
     */
    protected void injectSessionCookies(Map<String, String> cookies, String domain) {
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            browserContext.addCookies(java.util.List.of(
                    new com.microsoft.playwright.options.Cookie(entry.getKey(), entry.getValue())
                            .setDomain(domain)
                            .setPath("/")
            ));
        }
        log.debug("Injected {} cookie(s) into BrowserContext", cookies.size());
    }

    /** Global plans and cross-location flows need tech maps from all permitted storages. */
    protected void injectAllLocationsView() {
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
        log.debug("BrowserContext init script: selectedStorageId=all");
    }

    private void attachScreenshotOnFailure(String testName) {
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment("Screenshot on failure — " + testName,
                    "image/png", new java.io.ByteArrayInputStream(screenshot), ".png");
            log.info("Screenshot attached to Allure for failed test: {}", testName);
        } catch (Exception e) {
            log.warn("Could not capture failure screenshot for '{}': {}", testName, e.getMessage());
        }
    }
}
