package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationPage extends BasePage {

    public static final String PATH = "/relocations";

    private static final String RECEIVE_BUTTON = "Отримати";
    private static final String SEND_BUTTON = "Видати";
    private static final String HISTORY_RECEIVED_TAB = "Отримано";
    private static final String ACTIVE_TAB = "Активні";
    private static final String HISTORY_TAB = "Історія";

    public RelocationPage(Page page) {
        super(page);
    }

    public RelocationPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Журнал переміщень");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return this;
    }

    public boolean isReceiveButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RECEIVE_BUTTON)).isVisible();
    }

    public boolean isSendButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SEND_BUTTON)).first().isVisible();
    }

    public RelocationCreateInputPage clickReceive() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RECEIVE_BUTTON)).click();
        return new RelocationCreateInputPage(page).waitForLoaded();
    }

    public RelocationCreateOutputPage clickSend() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SEND_BUTTON)).first().click();
        return new RelocationCreateOutputPage(page).waitForLoaded();
    }

    public RelocationPage openReceivedHistoryTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HISTORY_RECEIVED_TAB)).click();
        return this;
    }

    public RelocationPage openActiveTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(ACTIVE_TAB)).click();
        return this;
    }

    public RelocationPage openHistoryTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(HISTORY_TAB)).click();
        return this;
    }

    public boolean isActiveTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(ACTIVE_TAB)).isVisible();
    }

    public void clickResolveInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Завершити")).click();
    }

    public void clickRejectInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Відхилити")).click();
    }

    public void clickReturnInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Повернути")).click();
    }

    public Locator rowContainingText(String text) {
        return page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(text)).first();
    }

    public RelocationUpdateInputPage clickEditInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Редагувати")).click();
        return new RelocationUpdateInputPage(page).waitForLoaded();
    }

    public void clickDeleteInRow(String rowText) {
        Locator row = rowContainingText(rowText);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Видалити")).click();
    }

    public void confirmDeleteDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Видалити")).last().click();
    }
}
