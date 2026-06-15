package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for all Page Objects.
 * Provides shared navigation helpers, waiting utilities and screenshot capture for Allure.
 */
@Slf4j
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** Playwright wait budget for UI element interactions (from {@code ui.timeout} config). */
    protected int uiTimeoutMs() {
        return ConfigProvider.getUiTimeoutSeconds() * 1000;
    }

    /**
     * Navigate to a full URL, wrap it in an Allure step, and attach it as
     * a clickable link in the report (both in the step tree and in Links section).
     */
    public void navigateTo(String url, String label) {
        Allure.step("Перехід: " + label, () -> {
            log.info("Navigating to [{}]: {}", label, url);
            page.navigate(url);
            Allure.link(label, url);
            Allure.parameter("URL", url);
        });
    }

    /** Navigate to the given path without Allure reporting. */
    public void navigateTo(String path) {
        log.debug("Navigating to: {}", path);
        page.navigate(path);
    }

    /**
     * Attach the current page URL as a link in the Allure report.
     * Call this after a redirect has completed.
     */
    public void attachCurrentUrlLink(String label) {
        String url = page.url();
        log.info("Attaching current URL link [{}]: {}", label, url);
        Allure.link(label, url);
        Allure.parameter(label, url);
    }

    /** Wait for a CSS selector to be visible. */
    public void waitForVisible(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE));
    }

    /** Wait for a CSS selector to be visible, with a custom timeout in ms. */
    public void waitForVisible(String selector, int timeoutMs) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
    }

    /** Return the current page URL. */
    public String currentUrl() {
        return page.url();
    }

    /** Return the page title. */
    public String title() {
        return page.title();
    }

    /**
     * Capture a full-page screenshot and attach it to the current Allure report step.
     *
     * @param name label shown in the Allure attachment panel
     */
    public void attachScreenshot(String name) {
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment(name, "image/png", new java.io.ByteArrayInputStream(screenshot), ".png");
            log.debug("Screenshot attached to Allure: {}", name);
        } catch (Exception e) {
            log.warn("Could not capture screenshot '{}': {}", name, e.getMessage());
        }
    }

    /** Save a screenshot to disk and return the path (useful for quick local debugging). */
    public Path saveScreenshot(String filename) {
        Path target = Paths.get("target", "screenshots", filename + ".png");
        target.getParent().toFile().mkdirs();
        page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(true));
        log.info("Screenshot saved: {}", target.toAbsolutePath());
        return target;
    }
}
