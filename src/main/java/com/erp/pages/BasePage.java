package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureScreenshots;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

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

    /**
     * Runs {@code action} and waits for a matching backend response, but does not fail the test
     * when that response never arrives. Use it for "page is warming up" requests where the
     * subsequent {@code waitForLoaded()} assertion is the real gate — a request served from the
     * react-query cache, denied by RBAC, or answered with an error must not turn into a
     * {@link TimeoutError} far away from the actual assertion.
     */
    protected void waitForResponseTolerant(Predicate<Response> predicate, Runnable action, String label) {
        try {
            page.waitForResponse(predicate,
                    new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                    action);
        } catch (TimeoutError e) {
            log.warn("Response wait timed out [{}]: {}", label, e.getMessage());
        }
    }

    /**
     * Waits for a UI condition without turning a miss into a framework error: the caller's own
     * assertion must report the final state, otherwise a product bug surfaces as a {@link TimeoutError}
     * with no screenshot of what the page actually showed.
     */
    protected void waitForConditionTolerant(BooleanSupplier condition, String label) {
        try {
            page.waitForCondition(condition,
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        } catch (TimeoutError e) {
            log.warn("Condition wait timed out [{}]: {}", label, e.getMessage());
        }
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
            AllureScreenshots.attachPng(name, screenshot);
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

    /**
     * Waits until a combobox/autocomplete popover has finished loading options.
     * Covers both shadcn Combobox ({@code data-slot=combobox-item}, empty «Не знайдено»)
     * and Autocomplete/cmdk ({@code role=option}, empty «Нічого не знайдено»).
     */
    protected void waitForComboboxOptionsSettled() {
        page.waitForCondition(() -> {
            Locator items = page.locator("[data-slot='combobox-item']");
            if (items.count() > 0) {
                return true;
            }
            Locator options = page.getByRole(AriaRole.OPTION);
            if (options.count() > 0) {
                return true;
            }
            Locator empty = page.getByText("Не знайдено");
            if (empty.count() > 0 && empty.isVisible()) {
                return true;
            }
            Locator emptyAlt = page.getByText("Нічого не знайдено");
            return emptyAlt.count() > 0 && emptyAlt.isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    /**
     * Closes an open combobox/cmdk overlay so it cannot intercept clicks on dialog buttons.
     * Uses Tab (blur) rather than Escape — Escape closes the parent Radix dialog.
     */
    protected void dismissComboboxOverlay() {
        if (!comboboxOverlayVisible()) {
            return;
        }
        page.keyboard().press("Tab");
        try {
            page.waitForCondition(() -> !comboboxOverlayVisible(),
                    new Page.WaitForConditionOptions().setTimeout(5_000));
        } catch (Exception e) {
            log.debug("Combobox overlay still present after Tab: {}", e.getMessage());
        }
    }

    private boolean comboboxOverlayVisible() {
        return isAnyVisible(page.locator("[data-slot='combobox-item']"))
                || isAnyVisible(page.locator("[cmdk-item]"))
                || isAnyVisible(page.getByRole(AriaRole.OPTION));
    }

    private static boolean isAnyVisible(Locator locator) {
        int count = locator.count();
        for (int i = 0; i < count; i++) {
            if (locator.nth(i).isVisible()) {
                return true;
            }
        }
        return false;
    }
}
