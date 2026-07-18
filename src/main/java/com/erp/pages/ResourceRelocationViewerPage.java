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
 * Page Object for resource tracking journal.
 * URL: /resources-viewer/relocation
 */
@Slf4j
public class ResourceRelocationViewerPage extends BasePage {

    private static final String PATH = "/resources-viewer/relocation";
    private static final String PAGE_TITLE = "Журнал переміщень ресурсів";
    private static final String CATEGORY_LABEL = "Категорії";
    private static final String RESOURCE_FILTER_LABEL = "Ресурси для відстеження";
    private static final String CLEAR_BUTTON_TEXT = "Очистити";
    private static final String SEARCH_PLACEHOLDER = "Пошук...";
    private static final String AUTOCOMPLETE_OPTION_SELECTOR = "[cmdk-item], [role='option']";

    public ResourceRelocationViewerPage(Page page) {
        super(page);
    }

    public ResourceRelocationViewerPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        navigateTo(url, "Відстеження ресурсів (/resources-viewer/relocation)");
        return waitForLoaded();
    }

    public ResourceRelocationViewerPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        log.info("Resource relocation viewer loaded — url: {}", page.url());
        return this;
    }

    public boolean isLoaded() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE)).isVisible();
    }

    public ResourceRelocationViewerPage clearFilters() {
        Locator clearButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(CLEAR_BUTTON_TEXT));
        if (clearButton.count() > 0 && clearButton.isVisible()) {
            clearButton.click();
        }
        return this;
    }

    public ResourceRelocationViewerPage selectResource(String resourceName) {
        resourceAutocompleteTrigger().click();
        Locator searchInput = page.getByPlaceholder(SEARCH_PLACEHOLDER).last();
        String searchToken = extractSearchPrefix(resourceName);
        page.waitForResponse(
                response -> response.url().contains("/resources/autocomplete")
                        && response.status() == 200,
                () -> searchInput.fill(searchToken));
        waitForAutocompleteOptionsSettled();
        popoverOptions()
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .first()
                .click();
        page.keyboard().press("Escape");
        return this;
    }

    public ResourceRelocationViewerPage enableOthersReceivers() {
        Locator checkbox = page.locator("#isOthers");
        if (!checkbox.isChecked()) {
            checkbox.check();
        }
        return this;
    }

    public ResourceRelocationViewerPage search() {
        page.waitForResponse(
                response -> response.url().contains("/resources-viewer/relocations")
                        && response.status() == 200,
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Шукати"))
                        .click());
        return this;
    }

    public boolean isSummaryCardVisible() {
        return page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Сумарно переміщено")).isVisible();
    }

    public Double summaryAmountForResource(String resourceName) {
        Locator row = page.locator("div.flex.items-baseline.justify-between")
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .first();
        if (row.count() == 0 || !row.isVisible()) {
            return null;
        }
        String text = row.locator("span.font-semibold").innerText().trim();
        String number = text.replace('\u00a0', ' ').replace(" ", "").replace(',', '.');
        number = number.replaceAll("[^0-9.]", "");
        if (number.isBlank()) {
            return null;
        }
        return Double.parseDouble(number);
    }

    public boolean tableContainsText(String text) {
        return page.getByText(text, new Page.GetByTextOptions().setExact(false)).count() > 0;
    }

    public ResourceRelocationViewerPage selectCategory(String categoryName) {
        categoryFilterTrigger().click();
        popoverOptions()
                .filter(new Locator.FilterOptions().setHasText(categoryName))
                .first()
                .click();
        return this;
    }

    public List<String> searchResourcesAndCollectOptionNames(String searchToken) {
        resourceAutocompleteTrigger().click();
        Locator searchInput = page.getByPlaceholder(SEARCH_PLACEHOLDER).last();
        page.waitForResponse(
                response -> response.url().contains("/resources/autocomplete")
                        && response.status() == 200,
                () -> searchInput.fill(searchToken));

        waitForAutocompleteOptionsSettled();
        return readAutocompleteOptionNames();
    }

    public boolean isResourceOptionVisible(String resourceName) {
        return searchResourcesAndCollectOptionNames(extractSearchPrefix(resourceName)).stream()
                .anyMatch(option -> option.contains(resourceName.trim()));
    }

    public ResourceRelocationViewerPage closeResourceAutocomplete() {
        page.keyboard().press("Escape");
        return this;
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
            return popoverOptions().count() > 0
                    || page.getByText("Нічого не знайдено.").isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private List<String> readAutocompleteOptionNames() {
        List<String> names = new ArrayList<>();
        Locator options = popoverOptions();
        int count = options.count();
        for (int i = 0; i < count; i++) {
            String text = options.nth(i).innerText().trim();
            if (!text.isBlank()) {
                names.add(text);
            }
        }
        return names;
    }

    private Locator categoryFilterTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(CATEGORY_LABEL))
                .locator("xpath=following::input[1]")
                .first();
    }

    private Locator resourceAutocompleteTrigger() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(RESOURCE_FILTER_LABEL))
                .locator("xpath=following::button[@role='combobox'][1]")
                .first();
    }

    private Locator popoverOptions() {
        return page.locator(AUTOCOMPLETE_OPTION_SELECTOR);
    }

    private static String extractSearchPrefix(String resourceName) {
        int underscore = resourceName.lastIndexOf('_');
        if (underscore > 0) {
            return resourceName.substring(0, underscore);
        }
        return resourceName.length() > 8 ? resourceName.substring(0, 8) : resourceName;
    }
}
