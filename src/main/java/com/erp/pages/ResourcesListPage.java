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
import java.util.regex.Pattern;

/**
 * Page Object for the Resources dictionary page.
 * URL: /resources
 */
@Slf4j
public class ResourcesListPage extends BasePage {

    private static final String PATH = "/resources";
    private static final String SEARCH_PLACEHOLDER = "Пошук по назві...";
    private static final String NEW_RESOURCE_BUTTON = "Новий ресурс";
    private static final String LOADING_TEXT = "Завантаження...";
    private static final String EMPTY_STATE_TEXT = "Ресурсів не знайдено";
    private static final String NAME_COLUMN_HEADER = "Назва";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public ResourcesListPage(Page page) {
        super(page);
    }

    public ResourcesListPage open(long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Resources page for storageId={}: {}", storageId, url);
        navigateTo(url, "Словник ресурсів (/resources)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
        page.reload();
        return waitForLoaded();
    }

    public ResourcesListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached — proceeding: {}", e.getMessage());
        }

        Locator ready = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_RESOURCE_BUTTON))
                .or(page.getByPlaceholder(SEARCH_PLACEHOLDER))
                .or(page.getByText(EMPTY_STATE_TEXT));
        ready.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        waitForLoadingFinished();
        return this;
    }

    public ResourcesListPage searchByName(String text) {
        Locator input = page.getByPlaceholder(SEARCH_PLACEHOLDER);
        input.click();
        input.fill(text);
        waitForLoadingFinished();
        return this;
    }

    public List<String> getVisibleResourceNames() {
        Locator rows = resourceNameCells();
        List<String> names = new ArrayList<>();
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            String text = rows.nth(i).innerText().trim();
            if (!text.isBlank()) {
                names.add(normalizeName(text));
            }
        }
        return names;
    }

    public boolean isResourceVisible(String resourceName) {
        String normalized = normalizeName(resourceName);
        return getVisibleResourceNames().stream()
                .anyMatch(name -> name.equals(normalized) || name.contains(normalized));
    }

    public boolean isEmptyStateVisible() {
        return page.getByText(EMPTY_STATE_TEXT).isVisible();
    }

    private Locator resourceNameCells() {
        Locator table = page.locator("table");
        if (table.count() == 0) {
            return page.locator("[data-placeholder]");
        }
        Locator headerCells = table.locator("thead th");
        int nameColumnIndex = -1;
        for (int i = 0; i < headerCells.count(); i++) {
            if (NAME_COLUMN_HEADER.equals(headerCells.nth(i).innerText().trim())) {
                nameColumnIndex = i;
                break;
            }
        }
        if (nameColumnIndex < 0) {
            return table.locator("tbody tr td:nth-child(2)");
        }
        return table.locator("tbody tr td:nth-child(" + (nameColumnIndex + 1) + ")");
    }

    private void waitForLoadingFinished() {
        Locator loading = page.getByText(LOADING_TEXT);
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
    }

    private static String normalizeName(String value) {
        return WHITESPACE.matcher(value.trim()).replaceAll(" ");
    }
}
