package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmployeeListPage extends BasePage {

    public static final String PATH = "/employees";
    private static final String SEARCH_PLACEHOLDER = "Пошук за позивним";
    private static final String CREATE_BUTTON = "Новий співробітник";
    private static final String CREATE_TITLE = "Новий співробітник";
    private static final String STORAGE_PLACEHOLDER = "Виберіть підрозділи";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

    public EmployeeListPage(Page page) {
        super(page);
    }

    public EmployeeListPage openForStorage(Long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH;
        page.navigate(url);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        if (storageId != null) {
            page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
            page.reload();
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        }
        return waitForLoaded();
    }

    public EmployeeListPage waitForLoaded() {
        page.getByPlaceholder(SEARCH_PLACEHOLDER)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public EmployeeListPage createEmployee(String callSign, String storageName) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON)).click();
        Locator dialog = createDialog();
        dialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        Locator callSignInput = dialog.locator("input").first();
        callSignInput.fill(callSign);
        selectStorageIfNeeded(dialog, storageName);
        callSignInput.click();
        dismissComboboxOverlay();
        Locator save = dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зберегти"));
        save.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        var response = page.waitForResponse(
                r -> r.url().contains("/employees")
                        && "POST".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> save.click(new Locator.ClickOptions().setForce(true)));
        if (response.status() < 200 || response.status() >= 300) {
            attachScreenshot("POST employee failed — status " + response.status());
            throw new IllegalStateException("POST /employees failed with status " + response.status());
        }
        dialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    private void selectStorageIfNeeded(Locator dialog, String storageName) {
        Locator storageInput = dialog.getByPlaceholder(STORAGE_PLACEHOLDER);
        if (storageInput.count() == 0 || !storageInput.first().isVisible()) {
            return;
        }
        storageInput.first().click();
        storageInput.first().fill(storageName);
        waitForComboboxOptionsSettled();
        Locator item = page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(storageName));
        if (item.count() == 0) {
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(storageName)).first().click();
        } else {
            item.first().click();
        }
        dismissComboboxOverlay();
    }

    private Locator createDialog() {
        return page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName(CREATE_TITLE))));
    }
}
