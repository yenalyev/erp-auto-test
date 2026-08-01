package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the orders journal and detail/create dialogs.
 * URL: /orders
 */
@Slf4j
public class OrderListPage extends BasePage {

    public static final String PATH = "/orders";

    private static final String PAGE_TITLE = "Замовлення";
    private static final String CREATE_BUTTON = "Створити замовлення";
    private static final String EMPTY_STATE = "Замовлень не знайдено";
    private static final String RESOURCE_SEARCH_PLACEHOLDER = "Назва ресурсу...";
    private static final String TAKE_TO_WORK_BUTTON = "Взяти в роботу";
    private static final String CANCEL_ORDER_BUTTON = "Скасувати";
    private static final String CONFIRM_BUTTON = "Підтвердити";
    private static final String BOOKING_PANEL_TITLE = "Збір замовлення";
    private static final String SEND_ORDER_BUTTON = "Відправити замовлення";
    private static final String NEW_ORDER_DIALOG_TITLE = "Нове замовлення";
    private static final String CREATE_SUBMIT = "Створити";
    private static final String LINES_VALIDATION = "Додайте хоча б один ресурс";
    private static final String RESOURCE_COMBO_PLACEHOLDER = "Оберіть ресурс...";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";
    private static final String COMMENT_PLACEHOLDER = "Додати коментар...";
    private static final String ADD_COMMENT_BUTTON = "Додати";
    private static final String ALL_LOCATIONS_TOOLTIP = "Оберіть конкретну локацію для виконання дії";
    private static final String LOADING_TEXT = "Завантаження...";
    private static final String TABLE_CONTAINER_SELECTOR = "[data-slot='table-container'], table";

    public OrderListPage(Page page) {
        super(page);
    }

    public OrderListPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, PAGE_TITLE);
        return waitForLoaded();
    }

    public OrderListPage openDeepLink(long orderId) {
        String url = ConfigProvider.getBaseUrl() + PATH + "?orderId=" + orderId;
        navigateTo(url, PAGE_TITLE + " #" + orderId);
        waitForLoaded();
        waitForOrderDialog(orderId);
        return this;
    }

    public OrderListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForJournalDataSettled();
        return this;
    }

    public OrderListPage waitForJournalDataSettled() {
        page.waitForCondition(() -> {
            Locator loading = page.getByText(LOADING_TEXT);
            if (loading.count() > 0 && loading.first().isVisible()) {
                return false;
            }
            return journalTableWrapper().count() > 0
                    || page.getByText(EMPTY_STATE).isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public OrderListPage clickCreateOrder() {
        createOrderButton().click();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(NEW_ORDER_DIALOG_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isCreateButtonVisible() {
        Locator button = createOrderButton();
        return button.count() > 0 && button.isVisible();
    }

    public boolean isCreateDisabled() {
        return createOrderButton().isDisabled();
    }

    public String getCreateTooltip() {
        Locator button = createOrderButton();
        if (!button.isDisabled()) {
            return null;
        }
        button.hover(new Locator.HoverOptions().setForce(true));
        Locator tooltip = page.locator("[role='tooltip']")
                .filter(new Locator.FilterOptions().setHasText(ALL_LOCATIONS_TOOLTIP));
        tooltip.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(5_000));
        return tooltip.innerText().trim();
    }

    public boolean hasEmptyState() {
        return page.getByText(EMPTY_STATE).isVisible();
    }

    public boolean isJournalTableVisible() {
        Locator wrapper = journalTableWrapper();
        return wrapper.count() > 0 && wrapper.isVisible();
    }

    public OrderListPage filterByResourceSearch(String text) {
        Locator input = page.getByPlaceholder(RESOURCE_SEARCH_PLACEHOLDER);
        page.waitForResponse(
                response -> response.url().contains("/orders") && "GET".equals(response.request().method()),
                () -> input.fill(text == null ? "" : text));
        waitForJournalDataSettled();
        return this;
    }

    public OrderListPage clearFilters() {
        Locator filterCard = page.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Пошук ресурсу"))
                .locator("xpath=ancestor::div[contains(@class,'p-5')]");
        Locator resetButton = filterCard.locator("button").last();
        if (resetButton.count() == 0) {
            log.warn("Order filter reset button not found");
            return this;
        }
        page.waitForResponse(
                response -> response.url().contains("/orders") && "GET".equals(response.request().method()),
                resetButton::click);
        waitForJournalDataSettled();
        return this;
    }

    public OrderListPage openFirstOrderRow() {
        Locator row = journalTableWrapper().locator("tbody tr").first();
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        row.click();
        return this;
    }

    public OrderListPage openOrderByMarker(String rowMarker) {
        journalTableWrapper().locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(rowMarker))
                .first()
                .click();
        return this;
    }

    // --- Create dialog ---

    public OrderListPage waitForCreateDialog() {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(NEW_ORDER_DIALOG_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public OrderListPage submitCreateDialog() {
        orderDialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CREATE_SUBMIT)).click();
        return this;
    }

    public boolean isCreateValidationVisible() {
        return orderDialog().getByText(LINES_VALIDATION).isVisible();
    }

    public OrderListPage fillCreateResourceLine(String resourceNamePart, String quantity) {
        Locator resourceInput = orderDialog().getByPlaceholder(RESOURCE_COMBO_PLACEHOLDER).first();
        String searchTerm = resourceNamePart.length() > 16
                ? resourceNamePart.substring(0, 16)
                : resourceNamePart;
        resourceInput.click();
        resourceInput.fill(searchTerm);
        waitForComboboxOptionsSettled();
        page.locator("[data-slot='combobox-item'], [cmdk-item], [role='option']")
                .filter(new Locator.FilterOptions().setHasText(resourceNamePart))
                .first()
                .click();
        orderDialog().getByPlaceholder(QUANTITY_PLACEHOLDER).first().fill(quantity);
        return this;
    }

    // --- Detail dialog ---

    public OrderListPage waitForOrderDialog(long orderId) {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Замовлення #" + orderId))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isOrderDialogVisible(long orderId) {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Замовлення #" + orderId))
                .isVisible();
    }

    public boolean isTakeToWorkVisible() {
        return orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(TAKE_TO_WORK_BUTTON))
                .isVisible();
    }

    public OrderListPage clickTakeToWork() {
        orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(TAKE_TO_WORK_BUTTON))
                .click();
        confirmActionModal().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        confirmActionModal()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CONFIRM_BUTTON))
                .click();
        return this;
    }

    public OrderListPage clickCancelOrder() {
        orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CANCEL_ORDER_BUTTON))
                .first()
                .click();
        confirmActionModal().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        confirmActionModal()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CONFIRM_BUTTON))
                .click();
        return this;
    }

    public boolean isBookingPanelVisible() {
        return orderDialog().getByText(BOOKING_PANEL_TITLE).isVisible();
    }

    public boolean isSendOrderEnabled() {
        Locator button = orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(SEND_ORDER_BUTTON));
        return button.count() > 0 && button.isEnabled();
    }

    public RelocationCreateOutputPage clickSendOrder() {
        orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(SEND_ORDER_BUTTON))
                .click();
        return new RelocationCreateOutputPage(page).waitForOrderIssuanceLoaded();
    }

    public OrderListPage addComment(String text) {
        Locator dialog = orderDialog();
        dialog.getByPlaceholder(COMMENT_PLACEHOLDER).fill(text);
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_COMMENT_BUTTON)).click();
        dialog.getByText(text).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    private Locator createOrderButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON));
    }

    private Locator journalTableWrapper() {
        Locator tableContainer = page.locator(TABLE_CONTAINER_SELECTOR);
        if (tableContainer.count() > 0) {
            return tableContainer.first();
        }
        return page.locator("table").first();
    }

    private Locator orderDialog() {
        return page.locator("[role='dialog']").filter(new Locator.FilterOptions().setHas(
                page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(java.util.regex.Pattern.compile("Замовлення #|Нове замовлення")))));
    }

    private Locator confirmActionModal() {
        return page.locator("[role='alertdialog']");
    }
}
