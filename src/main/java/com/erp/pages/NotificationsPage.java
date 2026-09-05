package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for admin notifications hub: /notifications.
 */
@Slf4j
public class NotificationsPage extends BasePage {

    public static final String LIST_PATH = "/notifications";
    public static final String PAGE_TITLE = "Сповіщення";
    public static final String TAB_RECIPIENTS = "Отримувачі";
    public static final String TAB_TEMPLATES = "Шаблони";
    public static final String TAB_SUBSCRIPTIONS = "Підписки";
    public static final String TAB_LOG = "Журнал сповіщень";
    public static final String NEW_RECIPIENT_BUTTON = "Новий отримувач";
    public static final String DIALOG_TITLE = "Новий отримувач";
    public static final String SAVE_BUTTON = "Зберегти";

    public NotificationsPage(Page page) {
        super(page);
    }

    public NotificationsPage open() {
        String url = ConfigProvider.getBaseUrl() + LIST_PATH;
        log.info("Opening notifications page: {}", url);
        navigateTo(url, "Сповіщення (/notifications)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public NotificationsPage waitForLoaded() {
        waitForPageReady();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForLoadingFinished();
        return this;
    }

    public boolean isPageLoaded() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE)).isVisible();
    }

    public boolean areTabsVisible() {
        return isTabVisible(TAB_RECIPIENTS)
                && isTabVisible(TAB_TEMPLATES)
                && isTabVisible(TAB_SUBSCRIPTIONS)
                && isTabVisible(TAB_LOG);
    }

    public boolean isTabVisible(String name) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(name)).isVisible();
    }

    public NotificationsPage openTab(String name) {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(name)).click();
        waitForLoadingFinished();
        return this;
    }

    public NotificationsPage openRecipientsTab() {
        return openTab(TAB_RECIPIENTS);
    }

    public NotificationsPage openTemplatesTab() {
        return openTab(TAB_TEMPLATES);
    }

    public NotificationsPage openLogTab() {
        return openTab(TAB_LOG);
    }

    public boolean isNewRecipientButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_RECIPIENT_BUTTON)).isVisible();
    }

    public NotificationsPage openCreateRecipientDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_RECIPIENT_BUTTON)).click();
        page.getByRole(AriaRole.DIALOG).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public NotificationsPage fillRecipientForm(String caption, String addressInfo) {
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        dialog.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Назва"))
                .locator("xpath=following::input[1]")
                .fill(caption);
        dialog.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Адреса"))
                .locator("xpath=following::input[1]")
                .fill(addressInfo);
        return this;
    }

    public NotificationsPage saveRecipientDialog() {
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(SAVE_BUTTON)).click();
        dialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        waitForLoadingFinished();
        return this;
    }

    public boolean isCaptionVisibleInTable(String caption) {
        Locator cell = page.getByText(caption, new Page.GetByTextOptions().setExact(true));
        try {
            cell.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
            return true;
        } catch (RuntimeException e) {
            log.warn("Recipient caption «{}» not on page: {}", caption, e.getMessage());
            return false;
        }
    }

    public boolean hasTemplateRows() {
        Locator rows = page.locator("table tbody tr");
        return rows.count() > 0 && !page.getByText("Немає шаблонів").isVisible();
    }

    public boolean isLogTableRendered() {
        Locator table = page.locator("table");
        if (table.count() == 0) {
            return false;
        }
        Locator thead = table.first().locator("thead");
        return thead.getByText("Статус", new Locator.GetByTextOptions().setExact(true)).isVisible()
                && thead.getByText("Спроба", new Locator.GetByTextOptions().setExact(true)).isVisible();
    }

    private void waitForPageReady() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached: {}", e.getMessage());
        }
    }

    private void waitForLoadingFinished() {
        Locator loading = page.getByText("Завантаження...");
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
    }
}
