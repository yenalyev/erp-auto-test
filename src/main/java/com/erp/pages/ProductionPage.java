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
 * URL: /production
 */
@Slf4j
public class ProductionPage extends BasePage {

    private static final String PATH = "/production";

    private static final String MANUFACTURING_BUTTON_TEXT = "Виготовлення";
    private static final String DISASSEMBLE_BUTTON_TEXT = "Розбір";
    private static final String PRODUCT_LABEL_TEXT = "Продукт";
    private static final String PRODUCT_INPUT_SELECTOR = "input[placeholder='Пошук...']";
    private static final String DATE_INPUT_SELECTOR = "input[type='date']";
    private static final String CLEAR_BUTTON_TEXT = "Очистити";
    private static final String PRODUCTION_TABLE_WRAPPER_SELECTOR =
            "div.rounded-xl.border.border-gray-200.bg-white";

    public ProductionPage(Page page) {
        super(page);
    }

    public ProductionPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Production page: {}", url);
        navigateTo(url, "Журнал виробництва (/production)");
        return waitForLoaded();
    }

    /** Wait until the production journal is rendered (SPA load + key UI elements). */
    public ProductionPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached within timeout — proceeding: {}", e.getMessage());
        }

        Locator pageReady = page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(MANUFACTURING_BUTTON_TEXT))
                .or(page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName(MANUFACTURING_BUTTON_TEXT)))
                .or(page.getByLabel(PRODUCT_LABEL_TEXT))
                .or(page.locator(PRODUCT_INPUT_SELECTOR))
                .or(page.locator(PRODUCTION_TABLE_WRAPPER_SELECTOR))
                .first();

        pageReady.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));

        log.info("Production page loaded — url: {}", page.url());
        return this;
    }

    public boolean isManufacturingButtonVisible() {
        return isNamedActionVisible(MANUFACTURING_BUTTON_TEXT);
    }

    public boolean isDisassembleButtonVisible() {
        return isNamedActionVisible(DISASSEMBLE_BUTTON_TEXT);
    }

    /** True when any key production journal element is visible. */
    public boolean isLoaded() {
        return isManufacturingButtonVisible()
                || isProductFilterVisible()
                || isProductionTableVisible();
    }

    public boolean isProductFilterVisible() {
        Locator byLabel = page.getByLabel(PRODUCT_LABEL_TEXT);
        if (byLabel.count() > 0 && byLabel.first().isVisible()) {
            return true;
        }
        Locator input = page.locator(PRODUCT_INPUT_SELECTOR);
        return input.count() > 0 && input.first().isVisible();
    }

    public boolean isDateFromVisible() {
        return page.locator(DATE_INPUT_SELECTOR).nth(0).isVisible();
    }

    public boolean isDateToVisible() {
        return page.locator(DATE_INPUT_SELECTOR).nth(1).isVisible();
    }

    public boolean isClearButtonVisible() {
        Locator button = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(CLEAR_BUTTON_TEXT));
        return button.count() > 0 && button.first().isVisible();
    }

    public boolean isProductionTableVisible() {
        Locator wrapper = page.locator(PRODUCTION_TABLE_WRAPPER_SELECTOR).first();
        return wrapper.count() > 0 && wrapper.isVisible();
    }

    private boolean isNamedActionVisible(String name) {
        Locator asLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name));
        if (asLink.count() > 0 && asLink.first().isVisible()) {
            return true;
        }
        Locator asButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
        return asButton.count() > 0 && asButton.first().isVisible();
    }
}
