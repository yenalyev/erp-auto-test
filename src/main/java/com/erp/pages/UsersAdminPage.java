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
 * Page Object for admin user management: /users, /users/create, /users/:id.
 */
@Slf4j
public class UsersAdminPage extends BasePage {

    public static final String LIST_PATH = "/users";
    public static final String CREATE_PATH = "/users/create";
    public static final String PAGE_TITLE = "Довідники: Користувачі та ролі";
    public static final String CREATE_PAGE_TITLE = "Новий користувач";
    public static final String USERS_TAB = "Користувачі";
    public static final String ROLES_TAB = "Ролі";
    public static final String NEW_USER_BUTTON = "Новий користувач";
    public static final String SEARCH_PLACEHOLDER = "Пошук за логіном";
    public static final String STORAGE_FILTER_PLACEHOLDER = "Всі локації...";
    public static final String CLEAR_FILTERS_BUTTON = "Очистити";
    public static final String CREATE_SUBMIT_BUTTON = "Створити";
    public static final String SAVE_BUTTON = "Зберегти";
    public static final String DONE_BUTTON = "Готово";
    public static final String CREDENTIALS_DIALOG_TITLE = "Користувача створено";
    public static final String LOADING_TEXT = "Завантаження...";
    public static final String ADMINISTRATOR_ROLE = "Administrator-ROLE";

    private static final List<String> USER_TABLE_HEADERS = List.of("Логін", "Ім'я", "Прізвище", "Локації");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public UsersAdminPage(Page page) {
        super(page);
    }

    public UsersAdminPage open() {
        String url = ConfigProvider.getBaseUrl() + LIST_PATH;
        log.info("Opening users admin page: {}", url);
        navigateTo(url, "Користувачі та ролі (/users)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForListLoaded();
    }

    public UsersAdminPage openCreate() {
        String url = ConfigProvider.getBaseUrl() + CREATE_PATH;
        navigateTo(url, "Новий користувач (/users/create)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForCreateLoaded();
    }

    public UsersAdminPage waitForListLoaded() {
        waitForPageReady();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForLoadingFinished();
        return this;
    }

    public UsersAdminPage waitForCreateLoaded() {
        waitForPageReady();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(CREATE_PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public UsersAdminPage waitForUserDetailLoaded(String username) {
        waitForPageReady();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(username))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isListPageLoaded() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE)).isVisible();
    }

    public boolean isUsersTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(USERS_TAB)).isVisible();
    }

    public boolean isNewUserButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_USER_BUTTON)).isVisible()
                || page.locator("button").filter(new Locator.FilterOptions().setHasText(NEW_USER_BUTTON)).isVisible();
    }

    public boolean areUserTableHeadersVisible() {
        Locator thead = page.locator("table thead");
        if (thead.count() == 0) {
            return false;
        }
        for (String header : USER_TABLE_HEADERS) {
            if (!thead.getByText(header, new Locator.GetByTextOptions().setExact(true)).isVisible()) {
                return false;
            }
        }
        return true;
    }

    public UsersAdminPage searchByUsername(String text) {
        Locator input = page.getByPlaceholder(SEARCH_PLACEHOLDER);
        input.click();
        input.fill(text);
        page.waitForTimeout(350);
        waitForLoadingFinished();
        return this;
    }

    public UsersAdminPage selectFirstStorageFilter() {
        page.getByPlaceholder(STORAGE_FILTER_PLACEHOLDER).click();
        waitForComboboxOptionsSettled();
        Locator item = page.locator("[data-slot='combobox-item']").first();
        if (item.count() == 0) {
            item = page.getByRole(AriaRole.OPTION).first();
        }
        item.click();
        waitForLoadingFinished();
        return this;
    }

    public boolean isUsernameVisibleInTable(String username) {
        Locator link = page.locator("table tbody").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(username));
        if (link.count() > 0 && link.first().isVisible()) {
            return true;
        }
        return page.locator("table tbody").getByText(username, new Locator.GetByTextOptions().setExact(true)).isVisible();
    }

    public UsersAdminPage clickNewUser() {
        Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_USER_BUTTON));
        if (button.count() == 0) {
            button = page.locator("button").filter(new Locator.FilterOptions().setHasText(NEW_USER_BUTTON));
        }
        button.first().click();
        return waitForCreateLoaded();
    }

    public UsersAdminPage fillCreateForm(String username, String firstName, String lastName) {
        Locator inputs = formTextInputs();
        inputs.nth(0).fill(username);
        inputs.nth(1).fill(firstName);
        inputs.nth(2).fill(lastName);
        return this;
    }

    public UsersAdminPage submitCreate() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_SUBMIT_BUTTON)).click();
        return this;
    }

    public UsersAdminPage assertCredentialsDialogVisible() {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(CREDENTIALS_DIALOG_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean credentialsDialogShowsUsername(String username) {
        return page.getByText("Логін:").locator("xpath=..").getByText(username).isVisible();
    }

    public UsersAdminPage dismissCredentialsDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DONE_BUTTON)).click();
        return waitForListLoaded();
    }

    public UsersAdminPage openRolesTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(ROLES_TAB)).click();
        waitForLoadingFinished();
        return this;
    }

    public UsersAdminPage clickRoleName(String roleName) {
        page.locator("table tbody button").filter(new Locator.FilterOptions().setHasText(roleName)).first().click();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Дозволи ролі «" + roleName + "»"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForRolePermissionsLoaded();
        return this;
    }

    public List<String> getVisibleRolePermissions() {
        List<String> permissions = new ArrayList<>();
        Locator items = page.locator("li");
        for (int i = 0; i < items.count(); i++) {
            String text = normalize(items.nth(i).innerText());
            if (text.startsWith("perm_")) {
                permissions.add(text);
            }
        }
        return permissions;
    }

    public UsersAdminPage clickUsernameLink(String username) {
        page.locator("table tbody").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(username)).click();
        return waitForUserDetailLoaded(username);
    }

    public boolean isFirstNameFieldEditable() {
        Locator inputs = formTextInputs();
        return inputs.count() > 1 && inputs.nth(1).isVisible() && inputs.nth(1).isEnabled();
    }

    public String getFirstNameFieldValue() {
        Locator inputs = formTextInputs();
        if (inputs.count() > 1 && inputs.nth(1).isVisible()) {
            return inputs.nth(1).inputValue();
        }
        Locator readOnly = page.locator("label").filter(new Locator.FilterOptions().setHasText("Ім'я"))
                .locator("xpath=following-sibling::div[1]");
        return readOnly.count() > 0 ? normalize(readOnly.first().innerText()) : "";
    }

    public UsersAdminPage updateFirstName(String firstName) {
        Locator input = formTextInputs().nth(1);
        input.click();
        input.fill(firstName);
        return this;
    }

    public UsersAdminPage saveUser() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_BUTTON)).click();
        return waitForListLoaded();
    }

    /**
     * True when Locations ({@code MultiStorageSelector}) has selected chips.
     * Empty state keeps placeholder «Виберіть підрозділи»; with selection the placeholder is cleared.
     */
    public boolean hasSelectedLocationChips() {
        Locator placeholder = page.locator("form").getByPlaceholder("Виберіть підрозділи");
        if (placeholder.count() == 0) {
            return true;
        }
        Locator first = placeholder.first();
        if (!first.isVisible()) {
            return true;
        }
        String attr = first.getAttribute("placeholder");
        return attr == null || attr.isBlank();
    }

    public boolean isOnUsersListPath() {
        return currentUrl().contains(LIST_PATH) && !currentUrl().contains("/create");
    }

    private void waitForRolePermissionsLoaded() {
        Locator loading = page.locator("div.flex.justify-center").filter(new Locator.FilterOptions().setHasText(LOADING_TEXT));
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
        page.waitForCondition(() -> {
            Locator items = page.locator("li");
            for (int i = 0; i < items.count(); i++) {
                if (items.nth(i).innerText().startsWith("perm_")) {
                    return true;
                }
            }
            Locator empty = page.getByText("Дозволів немає");
            return empty.count() > 0 && empty.isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator formTextInputs() {
        return page.locator("form input:not([type='hidden'])");
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
        Locator loading = page.getByText(LOADING_TEXT);
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
    }

    private static String normalize(String value) {
        return value != null ? WHITESPACE.matcher(value.trim()).replaceAll(" ") : "";
    }
}
