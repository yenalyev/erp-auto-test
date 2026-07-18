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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String NOTES_COLUMN_HEADER = "Примітки";
    private static final String INTERCHANGEABLE_COLUMN_HEADER = "Взаємозамінні";
    private static final String NOTES_EDIT_BUTTON_LABEL = "Редагувати примітки";
    private static final String NOTES_DIALOG_PLACEHOLDER = "Введіть примітки...";
    private static final String NOTES_SAVE_BUTTON = "Зберегти";
    private static final int SEARCH_DEBOUNCE_BUFFER_MS = 450;
    private static final Pattern TAG_BADGE_PATTERN = Pattern.compile("(#\\S+) \\((\\d+)\\)");

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
        try {
            waitForTechMapsDuring(() -> {
                fillAction.run();
                page.waitForTimeout(SEARCH_DEBOUNCE_BUFFER_MS);
            });
        } catch (com.microsoft.playwright.PlaywrightException e) {
            log.debug("Tech maps filter did not trigger API response, applying filter anyway: {}", e.getMessage());
            fillAction.run();
            page.waitForTimeout(SEARCH_DEBOUNCE_BUFFER_MS);
        }
        waitForTableSettled();
    }

    private void waitForTechMapsDuring(Runnable action) {
        page.waitForResponse(
                response -> {
                    String url = response.url();
                    return url.contains("/technological-maps")
                            && !url.contains("/technological-maps/mode")
                            && !url.contains("/technological-maps/output-resources")
                            && !url.contains("/tag-statistics")
                            && "GET".equals(response.request().method())
                            && response.status() < 500;
                },
                action);
    }

    public int findRowIndexByTechMapName(String techMapName) {
        Locator rows = page.locator("table tbody tr");
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            String name = textContent(rows.nth(i).locator("td").first());
            if (techMapName.equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Tech map row not found: " + techMapName);
    }

    public String getNotesTextForRow(int rowIndex) {
        int notesColumnIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
        Locator notesCell = page.locator("table tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(notesColumnIndex);
        Locator taggedText = notesCell.locator("[data-slot='tagged-text']");
        if (taggedText.count() > 0) {
            return textContent(taggedText.first());
        }
        return textContent(notesCell);
    }

    public List<String> getHighlightedTagsForRow(int rowIndex) {
        int notesColumnIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
        Locator tags = page.locator("table tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(notesColumnIndex)
                .locator("[data-slot='tagged-text-tag']");
        List<String> result = new ArrayList<>();
        int count = tags.count();
        for (int i = 0; i < count; i++) {
            result.add(textContent(tags.nth(i)));
        }
        return result;
    }

    public TechnologicalMapsListPage openNotesEditorForTechMapName(String techMapName) {
        return openNotesEditorForRow(findRowIndexByTechMapName(techMapName));
    }

    public TechnologicalMapsListPage openNotesEditorForRow(int rowIndex) {
        int notesColumnIndex = columnIndexByHeader(NOTES_COLUMN_HEADER);
        page.locator("table tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(notesColumnIndex)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(NOTES_EDIT_BUTTON_LABEL))
                .click();
        page.getByRole(AriaRole.DIALOG)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public TechnologicalMapsListPage fillNotesDialog(String text) {
        page.getByPlaceholder(NOTES_DIALOG_PLACEHOLDER).fill(text);
        return this;
    }

    public TechnologicalMapsListPage saveNotesDialog() {
        page.waitForResponse(
                response -> response.url().contains("/technological-maps/")
                        && response.url().contains("/notes")
                        && "PATCH".equals(response.request().method()),
                () -> page.getByRole(AriaRole.DIALOG)
                        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(NOTES_SAVE_BUTTON))
                        .click());
        page.getByRole(AriaRole.DIALOG)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.HIDDEN)
                        .setTimeout(uiTimeoutMs()));
        waitForTableSettled();
        return this;
    }

    public boolean isNotesColumnVisible() {
        try {
            columnIndexByHeader(NOTES_COLUMN_HEADER);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Re-applies product filter to refresh tag-statistics toolbar after notes change. */
    public TechnologicalMapsListPage refreshTagStatistics(String productTerm) {
        if (productTerm == null || productTerm.isBlank()) {
            return this;
        }
        filterByProduct("");
        return filterByProduct(productTerm);
    }

    public List<String> getVisibleTagBadges() {
        List<String> badges = new ArrayList<>();
        Locator buttons = page.locator("button").filter(new Locator.FilterOptions().setHasText("#"));
        int count = buttons.count();
        for (int i = 0; i < count; i++) {
            String label = textContent(buttons.nth(i));
            Matcher matcher = TAG_BADGE_PATTERN.matcher(label);
            if (matcher.matches()) {
                badges.add(matcher.group(1));
            }
        }
        return badges;
    }

    public TechnologicalMapsListPage clickTagFilterBadge(String tag) {
        Locator badge = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tag + " (")).first();
        badge.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        waitForTechMapsDuring(badge::click);
        waitForTableSettled();
        return this;
    }

    public boolean isTagBadgeVisible(String tag) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tag + " (")).count() > 0;
    }

    public boolean isTagBadgeSelected(String tag) {
        Locator badge = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tag + " (")).first();
        if (badge.count() == 0) {
            return false;
        }
        String className = badge.getAttribute("class");
        return className != null && className.contains("ring-green-700");
    }

    public boolean rowWithTechMapNameIsVisible(String techMapName) {
        try {
            findRowIndexByTechMapName(techMapName);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public String getInterchangeableColumnTextForTechMap(String techMapName) {
        int rowIndex = findRowIndexByTechMapName(techMapName);
        int columnIndex = columnIndexByHeader(INTERCHANGEABLE_COLUMN_HEADER);
        return textContent(page.locator("table tbody tr")
                .nth(rowIndex)
                .locator("td")
                .nth(columnIndex));
    }

    private int columnIndexByHeader(String headerText) {
        Locator headers = page.locator("table thead th");
        int count = headers.count();
        for (int i = 0; i < count; i++) {
            if (headerText.equals(textContent(headers.nth(i)))) {
                return i;
            }
        }
        throw new IllegalStateException("Column header not found: " + headerText);
    }

    private static String textContent(Locator cell) {
        String text = cell.innerText();
        return text != null ? text.trim().replaceAll("\\s+", " ") : "";
    }

    private Locator productSearchInput() {
        return page.getByPlaceholder(PRODUCT_PLACEHOLDER);
    }

    private Locator ingredientSearchInput() {
        return page.getByPlaceholder(INGREDIENT_PLACEHOLDER);
    }
}
