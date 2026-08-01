package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationBundlesTabPage extends BasePage {

    public static final String TAB_NAME = "Комплекти для видачі";
    private static final String NEW_BUNDLE = "Новий Комплект";
    private static final String SAVE = "Зберегти";
    private static final String CANCEL = "Скасувати";
    private static final String DELETE = "Видалити";
    private static final String NAME_PLACEHOLDER = "Введіть назву...";
    private static final String RESOURCES_PLACEHOLDER = "Оберіть ресурси...";
    private static final String SEARCH_PLACEHOLDER = "Пошук...";
    private static final String EMPTY_STATE = "Комплектів не знайдено";
    private static final String DUPLICATE_ERROR = "Комплект з такою назвою вже існує";
    private static final String COMBOBOX_ITEM = "[data-slot='command-item'], [cmdk-item]";

    public RelocationBundlesTabPage(Page page) {
        super(page);
    }

    public RelocationBundlesTabPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_BUNDLE)).waitFor();
        page.waitForCondition(() ->
                page.getByText("Завантаження...").count() == 0
                        || !page.getByText("Завантаження...").first().isVisible());
        return this;
    }

    public boolean isNewBundleButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_BUNDLE)).isVisible();
    }

    public boolean isEmptyStateVisible() {
        return page.getByText(EMPTY_STATE).isVisible();
    }

    public boolean isBundleRowVisible(String bundleName) {
        return bundleRow(bundleName).count() > 0 && bundleRow(bundleName).first().isVisible();
    }

    public RelocationBundlesTabPage openCreateDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_BUNDLE)).click();
        page.getByPlaceholder(NAME_PLACEHOLDER).waitFor();
        return this;
    }

    public RelocationBundlesTabPage fillBundleName(String name) {
        Locator input = page.locator("#bundle-name");
        input.waitFor();
        input.fill(name);
        return this;
    }

    public boolean isBundleNameDisabled() {
        return page.locator("#bundle-name").isDisabled();
    }

    public boolean isDuplicateNameErrorVisible() {
        return page.getByText(DUPLICATE_ERROR).isVisible();
    }

    public boolean isSaveEnabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE)).isEnabled();
    }

    public RelocationBundlesTabPage selectResourceByName(String resourceNamePart) {
        String searchTerm = resourceNamePart.length() > 12
                ? resourceNamePart.substring(0, 12)
                : resourceNamePart;
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RESOURCES_PLACEHOLDER))
                .or(page.getByRole(AriaRole.COMBOBOX).filter(
                        new Locator.FilterOptions().setHasText(RESOURCES_PLACEHOLDER)))
                .first()
                .click();
        Locator search = page.getByPlaceholder(SEARCH_PLACEHOLDER);
        search.waitFor();
        search.fill(searchTerm);
        page.waitForCondition(() -> page.locator(COMBOBOX_ITEM).count() > 0
                || page.getByText("Нічого не знайдено.").isVisible());
        Locator match = page.locator(COMBOBOX_ITEM)
                .filter(new Locator.FilterOptions().setHasText(resourceNamePart));
        if (match.count() == 0) {
            match = page.locator(COMBOBOX_ITEM)
                    .filter(new Locator.FilterOptions().setHasText(searchTerm));
        }
        match.first().click();
        // close popover if still open
        page.keyboard().press("Escape");
        return this;
    }

    public RelocationBundlesTabPage saveDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE)).click();
        return this;
    }

    public RelocationBundlesTabPage cancelDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CANCEL)).click();
        return this;
    }

    public RelocationBundlesTabPage openEdit(String bundleName) {
        bundleRow(bundleName).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Редагувати"))
                .click();
        page.locator("#bundle-name").waitFor();
        return this;
    }

    public RelocationBundlesTabPage openDelete(String bundleName) {
        bundleRow(bundleName).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Видалити"))
                .click();
        page.getByText("Видалити Комплект?").waitFor();
        return this;
    }

    public RelocationBundlesTabPage confirmDelete() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DELETE)).last().click();
        page.waitForCondition(() -> page.getByText("Видалити Комплект?").count() == 0
                || !page.getByText("Видалити Комплект?").first().isVisible());
        return this;
    }

    public RelocationBundlesTabPage waitForBundleVisible(String bundleName) {
        page.waitForCondition(() -> isBundleRowVisible(bundleName));
        return this;
    }

    public RelocationBundlesTabPage waitForBundleGone(String bundleName) {
        page.waitForCondition(() -> !isBundleRowVisible(bundleName));
        return this;
    }

    private Locator bundleRow(String bundleName) {
        return page.locator("[data-slot='table-row'], tr")
                .filter(new Locator.FilterOptions().setHasText(bundleName));
    }
}
