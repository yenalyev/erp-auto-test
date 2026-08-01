package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationCreateInputCrewPage extends BasePage {

    public static final String PATH = "/relocation/create-input-crew";
    private static final String TITLE = "Отримання від екіпажа";
    private static final String SUBMIT = "Підтвердити";
    private static final String UNIT_PLACEHOLDER = "Оберіть підрозділ...";
    private static final String CREW_PLACEHOLDER = "Оберіть екіпаж...";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

    public RelocationCreateInputCrewPage(Page page) {
        super(page);
    }

    public RelocationCreateInputCrewPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor();
        waitForCrewFormBootstrap();
        page.getByPlaceholder(CREW_PLACEHOLDER).waitFor();
        return this;
    }

    private void waitForCrewFormBootstrap() {
        page.waitForCondition(() -> {
            Locator loading = page.getByText("Завантаження...");
            if (loading.count() > 0 && loading.isVisible()) {
                return false;
            }
            return unitComboboxTrigger().isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator unitComboboxTrigger() {
        return page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(UNIT_PLACEHOLDER));
    }

    public boolean isLoaded() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE)).isVisible();
    }

    public RelocationCreateInputCrewPage selectUnitByName(String unitName) {
        unitComboboxTrigger().click();
        page.locator("[data-radix-popper-content-wrapper] button")
                .filter(new Locator.FilterOptions().setHasText(unitName))
                .first()
                .click();
        page.waitForTimeout(500);
        return this;
    }

    public RelocationCreateInputCrewPage selectCrewByName(String crewName) {
        Locator crewInput = page.getByPlaceholder(CREW_PLACEHOLDER);
        crewInput.waitFor();
        crewInput.click();
        crewInput.fill(crewName);
        waitForComboboxOptionsSettled();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(crewName))
                .first()
                .click();
        return this;
    }

    public RelocationCreateInputCrewPage selectResourceByName(String resourceNamePart) {
        String trimmed = resourceNamePart.trim();
        // Prefer unique suffix/token so autocomplete hits the fixture resource on dirty staging.
        final String searchTerm;
        int underscore = trimmed.lastIndexOf('_');
        if (underscore > 0 && underscore < trimmed.length() - 1) {
            String after = trimmed.substring(underscore + 1);
            searchTerm = after.length() >= 4
                    ? after
                    : (trimmed.length() > 16 ? trimmed.substring(trimmed.length() - 16) : trimmed);
        } else if (trimmed.length() > 16) {
            searchTerm = trimmed.substring(trimmed.length() - 16);
        } else {
            searchTerm = trimmed;
        }
        // Receive form uses ResourceAutocomplete (combobox trigger + «Пошук...» popover).
        Locator trigger = page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(RESOURCE_PLACEHOLDER))
                .first();
        trigger.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        trigger.scrollIntoViewIfNeeded();
        trigger.click();

        Locator searchInput = page.getByPlaceholder("Пошук...");
        searchInput.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                response -> response.url().contains("/resources/autocomplete")
                        && "GET".equals(response.request().method()),
                () -> searchInput.fill(searchTerm));
        waitForComboboxOptionsSettled();

        page.getByRole(AriaRole.OPTION)
                .filter(new Locator.FilterOptions().setHasText(trimmed))
                .first()
                .click();
        return this;
    }

    public RelocationCreateInputCrewPage fillQuantity(String amount) {
        page.getByPlaceholder(QUANTITY_PLACEHOLDER).fill(amount);
        return this;
    }

    public RelocationCreateInputCrewPage fillDescription(String description) {
        page.locator("#description").fill(description);
        return this;
    }

    public boolean isSubmitDisabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).isDisabled();
    }

    public RelocationPage submitAndWaitForJournal() {
        var response = page.waitForResponse(
                r -> r.url().contains("/relocations/receive")
                        && "POST".equals(r.request().method()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click());
        if (response.status() >= 400) {
            Locator alert = page.locator("[role='alert']").first();
            String alertText = alert.count() > 0 && alert.isVisible() ? alert.innerText() : "(no alert)";
            String body;
            try {
                body = response.text();
            } catch (RuntimeException e) {
                body = "(body unreadable: " + e.getMessage() + ")";
            }
            throw new AssertionError("POST /relocations/receive failed: HTTP "
                    + response.status() + " — UI alert: " + alertText + " — body: " + body);
        }
        page.waitForURL(
                url -> url.contains("/relocations") && !url.contains("create-input"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new RelocationPage(page).waitForLoaded();
    }

    public RelocationCreateInputCrewPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Отримання від екіпажа");
        return waitForLoaded();
    }
}
