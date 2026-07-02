package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Page Object for inventory conduct form.
 * URL: /inventory/{storageId}
 */
@Slf4j
public class InventoryEditPage extends BasePage {

    private static final String TITLE_PREFIX = "Інвентаризація";
    private static final String RESOURCE_COMBO_PLACEHOLDER = "Оберіть ресурс";
    private static final String SEARCH_PLACEHOLDER = "Пошук...";
    private static final String AUTOCOMPLETE_OPTION_SELECTOR = "[cmdk-item], [role='option']";

    public InventoryEditPage(Page page) {
        super(page);
    }

    public InventoryEditPage open(long storageId) {
        String url = ConfigProvider.getBaseUrl() + "/inventory/" + storageId;
        navigateTo(url, "Інвентаризація (/inventory/" + storageId + ")");
        return waitForLoaded();
    }

    public InventoryEditPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByText(TITLE_PREFIX).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public InventoryEditPage updateAmountForResource(String resourceName, String amount) {
        resourceRow(resourceName).locator("input[type='number']").fill(amount);
        return this;
    }

    public InventoryEditPage removeResource(String resourceName) {
        resourceRow(resourceName).getByRole(AriaRole.BUTTON).last().click();
        return this;
    }

    public boolean isResourceListed(String resourceName) {
        return resourceRow(resourceName).count() > 0;
    }

    public String getResourceAmountInputValue(String resourceName) {
        return resourceRow(resourceName).locator("input[type='number']").inputValue();
    }

    public List<String> searchAddResourceAutocomplete(String searchToken) {
        addResourceAutocompleteTrigger().click();
        Locator search = page.getByPlaceholder(SEARCH_PLACEHOLDER).last();
        page.waitForResponse(
                response -> response.url().contains("/resources/autocomplete")
                        && "GET".equals(response.request().method())
                        && response.status() == 200,
                () -> fillAutocompleteSearch(search, searchToken));
        waitForAutocompleteOptionsSettled();
        return readAutocompleteOptionNames();
    }

    public boolean isAddResourceOptionVisible(String resourceName) {
        String normalizedName = resourceName.trim().replaceAll("\\s+", " ");
        return searchAddResourceAutocomplete(extractSearchPrefix(normalizedName)).stream()
                .anyMatch(option -> option.contains(normalizedName));
    }

    public InventoryEditPage closeAddResourceAutocomplete() {
        page.keyboard().press("Escape");
        return this;
    }

    public InventoryEditPage addResource(String resourceName, String amount) {
        String normalizedName = resourceName.trim().replaceAll("\\s+", " ");
        addResourceAutocompleteTrigger().click();
        String searchTerm = extractSearchPrefix(normalizedName);
        Locator search = page.getByPlaceholder(SEARCH_PLACEHOLDER).last();
        page.waitForResponse(
                response -> response.url().contains("/resources/autocomplete")
                        && "GET".equals(response.request().method())
                        && response.status() == 200,
                () -> fillAutocompleteSearch(search, searchTerm));
        waitForAutocompleteOptionsSettled();
        Locator option = autocompleteOptions()
                .filter(new Locator.FilterOptions().setHasText(normalizedName))
                .first();
        option.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        option.click();
        page.locator("input[placeholder='0']").last().fill(amount);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Додати")).click();
        return this;
    }

    public InventoryEditPage save() {
        page.waitForResponse(
                response -> response.url().contains("/inventory")
                        && !response.url().contains("/status")
                        && "PUT".equals(response.request().method())
                        && response.status() == 200,
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Зберегти")).click());
        page.waitForURL("**/unit-management**", new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public InventoryEditPage goBack() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Назад")).click();
        return this;
    }

    public boolean hasSaveError() {
        return page.locator("[role='alert']").isVisible();
    }

    private void fillAutocompleteSearch(Locator search, String searchToken) {
        search.click();
        search.fill("");
        search.fill(searchToken);
    }

    private Locator addResourceAutocompleteTrigger() {
        return page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(RESOURCE_COMBO_PLACEHOLDER));
    }

    private Locator autocompleteOptions() {
        return page.locator(AUTOCOMPLETE_OPTION_SELECTOR);
    }

    private void waitForAutocompleteOptionsSettled() {
        page.waitForCondition(() -> {
            Locator loading = page.locator(".animate-spin");
            if (loading.count() > 0 && loading.last().isVisible()) {
                return false;
            }
            Locator hint = page.getByText("Введіть мінімум 2 символи");
            if (hint.count() > 0 && hint.isVisible()) {
                return false;
            }
            return autocompleteOptions().count() > 0
                    || page.getByText("Нічого не знайдено.").isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private List<String> readAutocompleteOptionNames() {
        List<String> names = new ArrayList<>();
        Locator options = autocompleteOptions();
        int count = options.count();
        for (int i = 0; i < count; i++) {
            String text = options.nth(i).innerText().trim();
            if (!text.isBlank()) {
                names.add(text);
            }
        }
        return names;
    }

    private static String extractSearchPrefix(String resourceName) {
        int underscore = resourceName.lastIndexOf('_');
        if (underscore > 0) {
            return resourceName.substring(0, underscore);
        }
        return resourceName.length() > 8 ? resourceName.substring(0, 8) : resourceName;
    }

    private Locator resourceRow(String resourceName) {
        return page.locator("div.border-b")
                .filter(new Locator.FilterOptions().setHasText(resourceName.trim()));
    }
}
