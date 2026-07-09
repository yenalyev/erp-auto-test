package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Page Object for technological maps list (tk-ui {@code TechnologicalMapsListPage.tsx}).
 * URL: /technological-maps
 */
@Slf4j
public class TechnologicalMapsListPage extends BasePage {

    public static final String PATH = "/technological-maps";

    private static final String PAGE_TITLE = "Перегляд тех. карт";
    private static final String PRODUCT_PLACEHOLDER = "Введіть назву продукту...";
    private static final String INGREDIENT_PLACEHOLDER = "Введіть назву сировини...";
    private static final String LOADING_TEXT = "Завантаження...";
    private static final String EMPTY_TEXT = "Немає даних";
    private static final int SEARCH_DEBOUNCE_BUFFER_MS = 450;

    public TechnologicalMapsListPage(Page page) {
        super(page);
    }

    public TechnologicalMapsListPage openForStorage(long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH;
        page.navigate(url);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
        waitForTechMapsDuring(page::reload);
        return waitForLoaded();
    }

    public TechnologicalMapsListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        page.getByPlaceholder(PRODUCT_PLACEHOLDER)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForTableSettled();
        return this;
    }

    public TechnologicalMapsListPage filterByProduct(String productTerm) {
        runSearchFilterAction(() -> productSearchInput().fill(productTerm));
        return this;
    }

    public TechnologicalMapsListPage filterByIngredient(String ingredientTerm) {
        runSearchFilterAction(() -> ingredientSearchInput().fill(ingredientTerm));
        return this;
    }

    public boolean isTechMapNameVisible(String techMapName) {
        return page.getByText(techMapName, new Page.GetByTextOptions().setExact(true)).count() > 0;
    }

    public List<String> getDisplayedTechMapNames() {
        List<String> names = new ArrayList<>();
        Locator rows = page.locator("table tbody tr");
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            String name = rows.nth(i).locator("td").first().innerText().trim();
            if (!name.isBlank()) {
                names.add(name.replaceAll("\\s+", " "));
            }
        }
        return names;
    }

    public boolean isEmptyStateVisible() {
        Locator empty = page.getByText(EMPTY_TEXT);
        return empty.count() > 0 && empty.first().isVisible();
    }

    public TechnologicalMapsListPage waitForTableSettled() {
        page.waitForCondition(() -> {
            Locator loading = page.getByText(LOADING_TEXT);
            if (loading.count() > 0 && loading.first().isVisible()) {
                return false;
            }
            Locator empty = page.getByText(EMPTY_TEXT);
            if (empty.count() > 0 && empty.first().isVisible()) {
                return true;
            }
            return page.locator("table tbody tr").count() > 0
                    || page.locator("table").count() > 0;
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    private void runSearchFilterAction(Runnable fillAction) {
        waitForTechMapsDuring(() -> {
            fillAction.run();
            page.waitForTimeout(SEARCH_DEBOUNCE_BUFFER_MS);
        });
        waitForTableSettled();
    }

    private void waitForTechMapsDuring(Runnable action) {
        page.waitForResponse(
                response -> {
                    String url = response.url();
                    return url.contains("/technological-maps")
                            && !url.contains("/technological-maps/mode")
                            && !url.contains("/technological-maps/output-resources")
                            && "GET".equals(response.request().method())
                            && response.status() < 500;
                },
                action);
    }

    private Locator productSearchInput() {
        return page.getByPlaceholder(PRODUCT_PLACEHOLDER);
    }

    private Locator ingredientSearchInput() {
        return page.getByPlaceholder(INGREDIENT_PLACEHOLDER);
    }
}
