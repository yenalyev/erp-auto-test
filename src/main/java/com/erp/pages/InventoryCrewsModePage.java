package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for /inventory with crews mode (?mode=crews).
 */
@Slf4j
public class InventoryCrewsModePage extends BasePage {

    private static final String PATH = "/inventory";
    private static final String PAGE_TITLE_TEXT = "Управління запасами";
    private static final String CREWS_MODE_LABEL = "Запаси екіпажів";
    private static final String OPEN_INVENTORY_BUTTON = "Відкрити інвентаризацію";
    private static final String CLOSE_INVENTORY_BUTTON = "Закрити інвентаризацію";
    private static final String CONDUCT_INVENTORY_BUTTON = "Провести інвентаризацію";
    private static final String UNIT_PLACEHOLDER = "Оберіть підрозділ...";
    private static final String CREW_PLACEHOLDER = "Оберіть екіпаж...";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

    public InventoryCrewsModePage(Page page) {
        super(page);
    }

    public InventoryCrewsModePage openForStorage(long storageId) {
        page.navigate(ConfigProvider.getBaseUrl() + PATH);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
        page.reload();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public InventoryCrewsModePage openCrewsMode(long storageId) {
        page.navigate(ConfigProvider.getBaseUrl() + PATH + "?mode=crews");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
        page.reload();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForCrewsModeLoaded();
    }

    public InventoryCrewsModePage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByText(PAGE_TITLE_TEXT)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public InventoryCrewsModePage waitForCrewsModeLoaded() {
        waitForLoaded();
        page.waitForCondition(
                () -> page.url().contains("mode=crews") && unitComboboxTrigger().isVisible(),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isCrewsModeActive() {
        return page.url().contains("mode=crews") && unitComboboxTrigger().isVisible();
    }

    public boolean isCrewsModeRadioVisible() {
        return page.getByText(CREWS_MODE_LABEL, new Page.GetByTextOptions().setExact(true)).isVisible();
    }

    private Locator unitComboboxTrigger() {
        return page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(UNIT_PLACEHOLDER));
    }

    public InventoryCrewsModePage selectUnitByName(String unitName) {
        unitComboboxTrigger().click();
        page.locator("[data-radix-popper-content-wrapper] button")
                .filter(new Locator.FilterOptions().setHasText(unitName))
                .first()
                .click();
        page.waitForCondition(
                () -> page.url().contains("unit="),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public InventoryCrewsModePage selectCrewByName(String crewName) {
        Locator crewInput = page.getByPlaceholder(CREW_PLACEHOLDER);
        crewInput.waitFor();
        crewInput.click();
        crewInput.fill(crewName);
        waitForComboboxOptionsSettled();
        Locator option = page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(crewName))
                .first();
        option.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        option.click();
        page.waitForCondition(
                () -> page.url().contains("crew="),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isOpenInventoryButtonVisible() {
        return isInventorySessionToggleVisible();
    }

    public boolean isInventorySessionToggleVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(OPEN_INVENTORY_BUTTON))
                .isVisible()
                || page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CLOSE_INVENTORY_BUTTON))
                .isVisible();
    }

    public boolean isConductInventoryButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CONDUCT_INVENTORY_BUTTON))
                .isVisible();
    }

    public boolean isConductInventoryButtonDisabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CONDUCT_INVENTORY_BUTTON))
                .isDisabled();
    }

    public boolean tableContainsResource(String resourceName) {
        try {
            page.waitForCondition(
                    () -> page.locator("[data-slot='table-container'] tbody tr")
                            .filter(new Locator.FilterOptions().setHasText(resourceName))
                            .count() > 0,
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }
}
