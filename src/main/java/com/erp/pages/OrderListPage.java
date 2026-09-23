package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
    private static final String RESOURCE_FILTER_PLACEHOLDER = "Всі ресурси";
    private static final String RESOURCE_FILTER_SEARCH = "Пошук...";
    private static final String TAKE_TO_WORK_BUTTON = "Взяти в роботу";
    private static final String CANCEL_ORDER_BUTTON = "Скасувати";
    private static final String CONFIRM_BUTTON = "Підтвердити";
    private static final String BOOKING_PANEL_TITLE = "Збір замовлення";
    private static final String BOOK_ALL_BUTTON = "Забронювати все";
    private static final String SEND_ORDER_BUTTON = "Відправити замовлення";
    private static final String READY_TO_DELIVER_BUTTON = "Готово до доставки";
    private static final String NEW_ORDER_DIALOG_TITLE = "Нове замовлення";
    private static final String CREATE_SUBMIT = "Створити";
    private static final String LINES_VALIDATION = "Додайте хоча б один ресурс";
    private static final Pattern DELIVERY_STORAGE_PLACEHOLDER = Pattern.compile(
            "Оберіть (склад|локацію)( доставки)?(?:\\.\\.\\.|…)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELIVERY_STORAGE_LABEL = Pattern.compile(
            "Куди доставити|Локація доставки|Склад доставки|Місце доставки", Pattern.CASE_INSENSITIVE);
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
        String needle = text == null ? "" : text;
        Locator resourceFilter = page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(RESOURCE_FILTER_PLACEHOLDER))
                .first();
        resourceFilter.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        resourceFilter.click();
        Locator search = page.getByPlaceholder(RESOURCE_FILTER_SEARCH);
        search.fill(needle);
        Locator option = page.locator("[data-slot='combobox-item'], [cmdk-item], [role='option']")
                .filter(new Locator.FilterOptions().setHasText(needle))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                response -> response.url().contains("/orders") && "GET".equals(response.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                option::click);
        waitForJournalDataSettled();
        return this;
    }

    public OrderListPage clearFilters() {
        Locator resetButton = page.locator("button:has(svg.lucide-filter-x)").first();
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

    /** Submit the create form and return the id from the detail dialog opened by the UI. */
    public long submitCreateDialogAndGetOrderId() {
        Locator submit = orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CREATE_SUBMIT));
        Response response = page.waitForResponse(
                r -> r.url().contains("/orders") && "POST".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                submit::click);
        requireSuccess(response, "Create order");

        Locator heading = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Замовлення #\\d+$")));
        heading.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        java.util.regex.Matcher matcher = Pattern.compile("#(\\d+)").matcher(heading.innerText());
        if (!matcher.find()) {
            throw new IllegalStateException("Created order id is absent from heading: " + heading.innerText());
        }
        return Long.parseLong(matcher.group(1));
    }

    public boolean isCreateValidationVisible() {
        return orderDialog().getByText(LINES_VALIDATION).isVisible();
    }

    public boolean isDeliveryStorageSelectorVisible() {
        Locator control = deliveryStorageControl();
        return control.count() > 0 && control.first().isVisible();
    }

    public OrderListPage openDeliveryStorageSelector() {
        Locator control = deliveryStorageControl();
        control.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        control.click();
        waitForComboboxOptionsSettled();
        return this;
    }

    public List<String> collectDeliveryStorageOptionLabels() {
        waitForComboboxOptionsSettled();
        Locator items = page.locator("[data-slot='combobox-item'], [cmdk-item], [role='option']");
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < items.count(); i++) {
            Locator item = items.nth(i);
            if (!item.isVisible()) {
                continue;
            }
            String label = item.innerText().trim();
            if (!label.isBlank()) {
                labels.add(label);
            }
        }
        return labels;
    }

    public List<String> searchAndCollectDeliveryStorageOptions(String searchTerm) {
        dismissComboboxOverlay();
        Locator control = deliveryStorageControl();
        control.click();
        waitForComboboxOptionsSettled();
        List<String> labels = collectDeliveryStorageOptionLabels();
        if (searchTerm == null || searchTerm.isBlank()) {
            return labels;
        }
        String normalizedSearchTerm = searchTerm.toLowerCase();
        return labels.stream()
                .filter(label -> label.toLowerCase().contains(normalizedSearchTerm))
                .toList();
    }

    public OrderListPage selectDeliveryStorageByName(String storageName) {
        searchAndCollectDeliveryStorageOptions(storageName);
        Locator matching = page.locator("[data-slot='combobox-item'], [cmdk-item], [role='option']")
                .filter(new Locator.FilterOptions().setHasText(storageName));
        if (matching.count() == 0) {
            throw new AssertionError("Delivery location «" + storageName + "» is absent from selector");
        }
        matching.first().click();
        return this;
    }

    /** Keep the auto-selected single location, otherwise choose the requested delivery location. */
    public OrderListPage ensureDeliveryStorageSelected(String storageName) {
        String selected = getSelectedDeliveryStorageLabel();
        if (selected != null && selected.contains(storageName)) {
            return this;
        }
        return selectDeliveryStorageByName(storageName);
    }

    public String getSelectedDeliveryStorageLabel() {
        Locator control = deliveryStorageControl();
        return "INPUT".equalsIgnoreCase(control.evaluate("element => element.tagName").toString())
                ? control.inputValue().trim()
                : control.innerText().trim();
    }

    public OrderListPage fillCreateResourceLine(String resourceNamePart, String quantity) {
        // The shared Autocomplete is a <button role="combobox">.  Its visible
        // placeholder is not exposed as an accessible name in every browser
        // build used on dev, so match the actual trigger text as well.
        Locator resourceTrigger = orderDialog().locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(RESOURCE_COMBO_PLACEHOLDER));
        if (resourceTrigger.count() == 0) {
            resourceTrigger = orderDialog().locator("button[role='combobox']")
                    .filter(new Locator.FilterOptions().setHasText("Оберіть ресурс"));
        }
        resourceTrigger.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        String searchTerm = resourceNamePart.length() > 16
                ? resourceNamePart.substring(0, 16)
                : resourceNamePart;
        resourceTrigger.first().click();
        Locator search = page.getByPlaceholder("Пошук...").last();
        search.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        search.fill(searchTerm);
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
        Response response = page.waitForResponse(
                r -> r.url().contains("/take-to-work") && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> confirmActionModal()
                        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CONFIRM_BUTTON))
                        .click());
        requireSuccess(response, "Take order to work");
        waitForBookingPanel();
        return this;
    }

    public OrderListPage selectGatheringStorage(String storageName) {
        Locator dialog = orderDialog();
        Locator candidate = dialog.locator("button")
                .filter(new Locator.FilterOptions().setHasText(storageName))
                .first();
        candidate.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        candidate.click();
        Locator submit = dialog.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Обрати локацію збору"));
        Response response = page.waitForResponse(
                r -> r.url().contains("/gathering-storage") && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                submit::click);
        requireSuccess(response, "Select gathering storage");
        // Label and value are rendered by separate siblings, so a regexp over
        // their concatenated text does not match.  The selected value itself
        // is repeated in the summary and booking panel.
        dialog.getByText(storageName, new Locator.GetByTextOptions().setExact(true)).first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public OrderListPage bookResource(String resourceName, double amount) {
        Locator row = bookingRowForResource(resourceName);
        row.locator("input[type='number']").fill(formatAmount(amount));
        Locator button = row.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Забронювати"));
        Response response = page.waitForResponse(
                r -> r.url().contains("/bookings") && "POST".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                button::click);
        requireSuccess(response, "Book order resource");
        orderDialog().getByText("Активні броні",
                        new Locator.GetByTextOptions().setExact(true))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isBookAllVisible() {
        Locator button = bookAllButton();
        return button.count() > 0 && button.first().isVisible();
    }

    public boolean isBookAllEnabled() {
        Locator button = bookAllButton();
        return button.count() > 0 && button.first().isVisible() && button.first().isEnabled();
    }

    public OrderListPage scrollBookAllIntoView() {
        bookAllButton().scrollIntoViewIfNeeded();
        return this;
    }

    /** Book every currently uncovered order line using the bulk UI action. */
    public OrderListPage bookAllResources() {
        Locator button = bookAllButton();
        button.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        button.click();
        return this;
    }

    public OrderListPage markBookingPrepared(String resourceName) {
        Locator button = orderDialog().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Підготовлено").setExact(true)).first();
        Response response = page.waitForResponse(
                r -> r.url().contains("/prepared") && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                button::click);
        requireSuccess(response, "Mark booking prepared");
        page.waitForCondition(this::isSendOrderEnabled,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public long createRelocationTask(String sourceStorageName, String resourceName, double amount) {
        return createRelocationTask(sourceStorageName, resourceName, amount, null);
    }

    public long createRelocationTaskCheckingMaximum(String sourceStorageName, String resourceName,
                                                     double amount, double expectedMaximum) {
        return createRelocationTask(sourceStorageName, resourceName, amount, expectedMaximum);
    }

    private long createRelocationTask(String sourceStorageName, String resourceName, double amount,
                                      Double expectedMaximum) {
        orderDialog().getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName("Замовити переміщення"))
                .click();
        Locator taskDialog = page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName("Запит на переміщення"))));
        taskDialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        taskDialog.locator("button")
                .filter(new Locator.FilterOptions().setHasText(sourceStorageName))
                .first()
                .click();
        Locator resourceLabel = taskDialog.getByText(resourceName,
                new Locator.GetByTextOptions().setExact(true));
        Locator line = resourceLabel.first().locator("xpath=..");
        Locator amountInput = line.locator("input[type='number']");
        if (expectedMaximum != null) {
            String actualMaximum = amountInput.getAttribute("max");
            if (actualMaximum == null
                    || Double.compare(Double.parseDouble(actualMaximum), expectedMaximum) != 0) {
                throw new AssertionError("Relocation task maximum for '" + resourceName
                        + "' must be " + formatAmount(expectedMaximum)
                        + ", but input max is " + actualMaximum);
            }
        }
        amountInput.fill(formatAmount(amount));
        Locator submit = taskDialog.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Створити запит"));
        Response response = page.waitForResponse(
                r -> r.url().contains("/relocation-tasks") && "POST".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                submit::click);
        requireSuccess(response, "Create order relocation task");
        java.util.regex.Matcher id = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)").matcher(response.text());
        if (!id.find()) {
            throw new IllegalStateException("Relocation task id is absent from response: " + response.text());
        }
        long taskId = Long.parseLong(id.group(1));
        orderDialog().getByText("Запит №" + taskId)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return taskId;
    }

    public OrderListPage cancelRelocationTask(long taskId) {
        Locator taskCard = orderDialog().locator("div")
                .filter(new Locator.FilterOptions().setHasText("Запит №" + taskId))
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.BUTTON,
                                new Page.GetByRoleOptions().setName("Скасувати").setExact(true))))
                .last();
        taskCard.getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName("Скасувати").setExact(true))
                .click();
        Locator confirm = page.getByRole(AriaRole.ALERTDIALOG);
        Locator cancel = confirm.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Скасувати запит"));
        cancel.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        Response response = page.waitForResponse(
                r -> r.url().contains("/relocation-tasks/" + taskId + "/cancel")
                        && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                // The alert action is animated and briefly re-mounted on dev.
                // Dispatching the DOM click avoids Playwright waiting for a
                // stability window on a button that is replaced mid-animation.
                () -> cancel.evaluate("element => element.click()"));
        requireSuccess(response, "Cancel order relocation task");
        return this;
    }

    public OrderListPage openProductionShortfallDialog() {
        orderDialog().getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName("Замовити виробництво"))
                .click();
        page.getByRole(AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName("Виробництво нестачі"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isNewProductionOrderEnabled() {
        Locator button = page.getByRole(AriaRole.DIALOG)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Нове ВЗ"));
        return button.count() > 0 && button.isEnabled();
    }

    public ProductionOrderWizardPage clickNewProductionOrder() {
        page.getByRole(AriaRole.DIALOG)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Нове ВЗ"))
                .click();
        return new ProductionOrderWizardPage(page).waitForCreateLoaded();
    }

    public OrderListPage clickCancelOrder() {
        orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CANCEL_ORDER_BUTTON))
                .first()
                .click();
        Locator confirmation = confirmActionModal();
        confirmation.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        Locator confirmButton = confirmation
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CONFIRM_BUTTON));
        Response response = page.waitForResponse(
                r -> r.url().contains("/orders/")
                        && r.url().contains("/cancel")
                        && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                confirmButton::click);
        requireSuccess(response, "Cancel order");
        waitForOrderState("Скасовано");
        return this;
    }

    public boolean isBookingPanelVisible() {
        return orderDialog().getByText(BOOKING_PANEL_TITLE).isVisible();
    }

    /** Wait for the booking card's own asynchronous API requests to finish. */
    public OrderListPage waitForBookingPanel() {
        waitForConditionTolerant(this::isBookingPanelVisible, "order booking panel");
        return this;
    }

    public boolean isSendOrderEnabled() {
        Locator button = orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(SEND_ORDER_BUTTON));
        return button.count() > 0 && button.isEnabled();
    }

    public boolean isReadyToDeliverVisible() {
        Locator button = orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(READY_TO_DELIVER_BUTTON));
        return button.count() > 0 && button.first().isVisible();
    }

    public OrderListPage markReadyToDeliver() {
        orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(READY_TO_DELIVER_BUTTON))
                .click();
        Locator continueButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Продовжити"));
        Response response = page.waitForResponse(
                r -> r.url().contains("/ready-to-deliver") && "PUT".equals(r.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                continueButton::click);
        requireSuccess(response, "Mark order ready to deliver");
        waitForOrderState("Готово до доставки");
        return this;
    }

    public OrderListPage waitForOrderState(String stateLabel) {
        page.waitForCondition(
                () -> isOrderStateVisible(stateLabel),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isOrderStateVisible(String stateLabel) {
        Locator label = orderDialog().getByText(stateLabel,
                new Locator.GetByTextOptions().setExact(true));
        return label.count() > 0 && label.first().isVisible();
    }

    public boolean isSendOrderVisible() {
        Locator button = orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(SEND_ORDER_BUTTON));
        return button.count() > 0 && button.first().isVisible();
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

    public boolean commentShowsAuthor(String text, String authorName) {
        Locator textNode = orderDialog().getByText(
                text, new Locator.GetByTextOptions().setExact(true));
        if (textNode.count() == 0) {
            return false;
        }
        Locator commentCard = textNode.first().locator("..");
        Locator author = commentCard.getByText(
                authorName, new Locator.GetByTextOptions().setExact(true));
        return author.count() > 0 && author.first().isVisible();
    }

    public OrderListPage selectPageSize(int size) {
        Locator trigger = page.locator("[data-slot='select-trigger']").filter(
                new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^(25|100|200|500)$")));
        if (trigger.count() == 0) {
            trigger = page.locator("[data-slot='select-trigger']").last();
        }
        trigger.first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(String.valueOf(size))).click();
        waitForJournalDataSettled();
        return this;
    }

    public boolean isPageSizeOptionVisible(int size) {
        Locator trigger = page.locator("[data-slot='select-trigger']").last();
        if (trigger.count() == 0 || !trigger.isVisible()) {
            return false;
        }
        trigger.click();
        boolean visible = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(String.valueOf(size)))
                .count() > 0;
        page.keyboard().press("Escape");
        return visible;
    }

    public boolean isSaveButtonVisible() {
        return orderDialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зберегти")).count() > 0
                && orderDialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зберегти")).isVisible();
    }

    public boolean isEditOrderVisible() {
        Locator editButton = orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Редагувати"));
        return editButton.count() > 0 && editButton.first().isVisible();
    }

    public boolean isMarkDoneVisible() {
        return orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Позначити виконаним"))
                .isVisible();
    }

    public boolean isCancelOrderVisible() {
        Locator cancelButton = orderDialog()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CANCEL_ORDER_BUTTON));
        return cancelButton.count() > 0 && cancelButton.first().isVisible();
    }

    public boolean isCommentComposerVisible() {
        return orderDialog().getByPlaceholder(COMMENT_PLACEHOLDER).isVisible();
    }

    public boolean isAvailabilityHintVisible() {
        return orderDialog().getByText("Наявність на локаціях").count() > 0
                || orderDialog().getByText("заброньовано").count() > 0;
    }

    public boolean isGathererEmptyBookingsVisible() {
        return orderDialog().getByText("ще немає броней").count() > 0
                || page.getByText("ще немає броней").count() > 0;
    }

    public boolean isPreparedProgressVisible() {
        return page.getByText(java.util.regex.Pattern.compile("Підготовлено")).count() > 0;
    }

    public boolean isBookingNeedFreeTableVisible() {
        Locator dialog = orderDialog();
        return dialog.getByText("Потрібно").count() > 0
                && (dialog.getByText("Заброньовано").count() > 0 || dialog.getByText("Вільно").count() > 0);
    }

    public boolean isReleaseBookingVisible() {
        return orderDialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зняти бронь")).count() > 0
                || orderDialog().getByText("Зняти бронь").count() > 0;
    }

    private Locator createOrderButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON));
    }

    private Locator bookAllButton() {
        return orderDialog().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(BOOK_ALL_BUTTON).setExact(true));
    }

    private Locator bookingRowForResource(String resourceName) {
        return orderDialog().locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(resourceName))
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.BUTTON,
                                new Page.GetByRoleOptions().setName("Забронювати"))))
                .first();
    }

    private static String formatAmount(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    private static void requireSuccess(Response response, String action) {
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException(action + " failed: HTTP " + response.status()
                    + ", body=" + response.text());
        }
    }

    private Locator deliveryStorageControl() {
        Locator byPlaceholder = orderDialog().getByPlaceholder(DELIVERY_STORAGE_PLACEHOLDER);
        if (byPlaceholder.count() > 0) {
            return byPlaceholder.first();
        }
        Locator labelledField = orderDialog().locator("label")
                .filter(new Locator.FilterOptions().setHasText(DELIVERY_STORAGE_LABEL));
        if (labelledField.count() > 0) {
            Locator labelledControl = labelledField.first()
                    .locator("xpath=following::*[@data-slot='select-trigger' or @role='combobox'][1]");
            if (labelledControl.count() > 0) {
                return labelledControl;
            }
        }
        return orderDialog().locator("[data-slot='select-trigger'], [role='combobox']").first();
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
