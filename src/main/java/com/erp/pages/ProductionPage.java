package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the Production Journal page.
 * URL: /production (без GET-параметрів)
 */
@Slf4j
public class ProductionPage extends BasePage {

    private static final String PATH = "/production";

    // Text-based locators (most resilient — tied to visible content)
    private static final String TITLE_TEXT      = "Журнал виготовленої продукції";
    private static final String ADD_BUTTON_TEXT = "Додати";

    // Filter block: identified by the unique combination of bg-white + rounded-xl + border-gray-200
    private static final String FILTER_BLOCK_SELECTOR  = "div.bg-white.rounded-xl.border.border-gray-200";
    // Filter inputs — identified by stable attributes from the actual DOM
    private static final String PRODUCT_INPUT_SELECTOR = "input[placeholder='Пошук...']";
    // Both date inputs are in separate parent divs, so CSS :nth-of-type counts within parent
    // and both are :nth-of-type(1). Use Playwright .nth() which counts across the whole document.
    private static final String DATE_INPUT_SELECTOR    = "input[type='date']";
    private static final String CLEAR_BUTTON_TEXT      = "Очистити";

    private static final String PRODUCTION_TABLE_SELECTOR = "table, [role='table'], [role='grid']";

    public ProductionPage(Page page) {
        super(page);
    }

    /**
     * Navigate directly to the production journal (/production, без GET-параметрів).
     * Waits until the page title is visible before returning.
     */
    public ProductionPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Production page: {}", url);
        navigateTo(url, "Журнал виготовленої продукції");
        return waitForLoaded();
    }

    /** Wait until the production journal is rendered (SPA load + key UI elements). */
    public ProductionPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(15_000));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached within timeout — proceeding: {}", e.getMessage());
        }

        Locator pageReady = page.getByText(TITLE_TEXT)
                .or(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADD_BUTTON_TEXT)))
                .or(page.locator(FILTER_BLOCK_SELECTOR))
                .or(page.locator(PRODUCTION_TABLE_SELECTOR))
                .first();

        pageReady.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(30_000));

        log.info("Production page loaded — url: {}", page.url());
        return this;
    }

    // -------------------------------------------------------------------------
    // Visibility checks
    // -------------------------------------------------------------------------

    public boolean isTitleVisible() {
        Locator title = page.getByText(TITLE_TEXT);
        return title.count() > 0 && title.first().isVisible();
    }

    /** True when any key production journal element is visible. */
    public boolean isLoaded() {
        return isTitleVisible()
                || isAddButtonVisible()
                || isFilterBlockVisible()
                || isProductionTableVisible();
    }

    public boolean isAddButtonVisible() {
        // The "Додати" element is an <a> tag styled as a button (data-slot="button"),
        // so AriaRole.LINK is correct. The SVG icon has aria-hidden="true" and is excluded
        // from the accessible name, so Playwright resolves the name to "Додати".
        Locator button = page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName(ADD_BUTTON_TEXT));
        return button.count() > 0 && button.first().isVisible();
    }

    /** Весь блок фільтрів (обгортка) */
    public boolean isFilterBlockVisible() {
        return page.locator(FILTER_BLOCK_SELECTOR).first().isVisible();
    }

    /** Поле пошуку "Продукт" */
    public boolean isProductInputVisible() {
        return page.locator(PRODUCT_INPUT_SELECTOR).isVisible();
    }

    /** Датапікер "З" — перший input[type='date'] на сторінці */
    public boolean isDateFromVisible() {
        return page.locator(DATE_INPUT_SELECTOR).nth(0).isVisible();
    }

    /** Датапікер "По" — другий input[type='date'] на сторінці */
    public boolean isDateToVisible() {
        return page.locator(DATE_INPUT_SELECTOR).nth(1).isVisible();
    }

    /** Кнопка "Очистити" */
    public boolean isClearButtonVisible() {
        return page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(CLEAR_BUTTON_TEXT)).isVisible();
    }

    public boolean isProductionTableVisible() {
        Locator table = page.locator(PRODUCTION_TABLE_SELECTOR).first();
        return table.count() > 0 && table.isVisible();
    }
}
