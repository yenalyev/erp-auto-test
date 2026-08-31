package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.PollUtils;
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
 * Page Object for the Equipment list page.
 * URL: /equipment
 */
@Slf4j
public class EquipmentListPage extends BasePage {

    public static final String PATH = "/equipment";
    private static final String PAGE_TITLE = "Обладнання";
    private static final String SEARCH_PLACEHOLDER = "Пошук за назвою, інв.№, S/N";
    private static final String ALL_EMPLOYEES_OPTION_PATTERN = "[УВ]сі співробітники";
    private static final String TABLE_CONTAINER_SELECTOR = "[data-slot='table-container']";
    private static final String LOADING_TEXT = "Завантаження...";

    public EquipmentListPage(Page page) {
        super(page);
    }

    public EquipmentListPage open() {
        return openForStorage(null);
    }

    public EquipmentListPage openForStorage(Long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH;
        if (storageId != null) {
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
            waitForGroupedEquipmentDuring(() -> page.reload());
        } else {
            waitForGroupedEquipmentDuring(() -> navigateTo(url, "Обладнання (/equipment)"));
        }
        return waitForLoaded();
    }

    public EquipmentListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        Locator ready = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .or(page.getByPlaceholder(SEARCH_PLACEHOLDER))
                .first();
        ready.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.getByPlaceholder(SEARCH_PLACEHOLDER)
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        waitForEmployeesLoaded();
        waitForTableSettled();
        return this;
    }

    private void waitForEmployeesLoaded() {
        page.waitForCondition(
                () -> assigneeFilterTrigger().innerText().matches(".*співробітник.*"),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    public EquipmentListPage waitForTableSettled() {
        page.waitForCondition(() -> {
            Locator loading = page.getByText(LOADING_TEXT);
            if (loading.count() > 0 && loading.isVisible()) {
                return false;
            }
            return tableContainer().count() > 0;
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public List<String> readAssigneeFilterOptions() {
        assigneeFilterTrigger().click();
        page.getByRole(AriaRole.OPTION).first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));

        List<String> options = new ArrayList<>();
        Locator optionLocators = page.getByRole(AriaRole.OPTION);
        int count = optionLocators.count();
        for (int i = 0; i < count; i++) {
            String text = optionLocators.nth(i).innerText().trim();
            if (!text.isBlank()) {
                options.add(text.replaceAll("\\s+", " "));
            }
        }
        page.keyboard().press("Escape");
        return options;
    }

    public EquipmentListPage filterByAssignee(String assigneeCallSign) {
        runGroupedEquipmentFilterAction(() -> {
            assigneeFilterTrigger().click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(assigneeCallSign)).click();
        });
        return this;
    }

    public boolean isEquipmentNameVisible(String equipmentName) {
        return page.locator(TABLE_CONTAINER_SELECTOR)
                .getByText(equipmentName, new Locator.GetByTextOptions().setExact(true))
                .count() > 0;
    }

    public EquipmentListPage filterBySearch(String searchTerm) {
        runGroupedEquipmentFilterAction(() ->
                page.getByPlaceholder(SEARCH_PLACEHOLDER).fill(searchTerm));
        return this;
    }

    /** Adds a status to the «Статус» faceted filter (default list excludes IN_TRANSIT). */
    public EquipmentListPage includeStatus(String statusLabel) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Статус")).click();
        Locator option = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(statusLabel));
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        runGroupedEquipmentFilterAction(option::click);
        page.keyboard().press("Escape");
        return this;
    }

    /**
     * Search by group/unit name, expand the group, click the inventory-number (or name)
     * button, and wait for the unit history dialog.
     */
    public EquipmentDetailDialog openUnitDialog(String groupName) {
        return openUnitDialog(groupName, null);
    }

    public EquipmentListPage selectGroupByName(String groupName) {
        filterBySearch(groupName);
        Locator row = groupRow(groupName);
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        Locator checkbox = row.getByLabel("Вибрати");
        checkbox.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        if (!checkbox.isChecked()) {
            checkbox.click();
        }
        return this;
    }

    public SendEquipmentDialog clickSendSelected() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("Передати вибране")))
                .click();
        return new SendEquipmentDialog(page).waitForOpen();
    }

    public EquipmentListPage sendSelectedTo(String recipientStorageName) {
        return clickSendSelected()
                .selectRecipient(recipientStorageName)
                .confirmSend();
    }

    public EquipmentDetailDialog openUnitDialog(String groupName, String inventoryNumber) {
        filterBySearch(groupName);
        page.getByText(groupName, new Page.GetByTextOptions().setExact(true))
                .first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        if (hasExpandToggle(groupName)) {
            expandGroup(groupName);
        }
        waitForUnitHistoryDuring(() -> clickUnitOpener(groupName, inventoryNumber));
        return new EquipmentDetailDialog(page).waitForOpen();
    }

    private boolean hasExpandToggle(String groupName) {
        return expandToggle(groupName).count() > 0;
    }

    private Locator expandToggle(String groupName) {
        return groupRow(groupName).locator("button[aria-label='Розгорнути'], button[aria-label='Згорнути']");
    }

    private void clickUnitOpener(String groupName, String inventoryNumber) {
        if (inventoryNumber != null && !inventoryNumber.isBlank()) {
            Locator invButton = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(inventoryNumber).setExact(true));
            if (invButton.count() > 0) {
                invButton.first().click();
                return;
            }
        }
        Locator expanded = isGroupVisible(groupName) ? expandedGroupSubTable(groupName) : tableContainer();
        Locator invCell = expanded.locator("td button.font-medium").first();
        if (invCell.count() > 0) {
            invCell.click();
            return;
        }
        Locator nameButton = tableContainer()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(groupName).setExact(true));
        if (nameButton.count() > 0) {
            nameButton.first().click();
            return;
        }
        Locator nameText = tableContainer()
                .getByText(groupName, new Locator.GetByTextOptions().setExact(true));
        if (nameText.count() > 0) {
            nameText.first().click();
            return;
        }
        groupRow(groupName).click();
    }

    private void waitForUnitHistoryDuring(Runnable action) {
        try {
            page.waitForResponse(
                    response -> response.url().matches(".*/equipment/\\d+/history.*")
                            && "GET".equals(response.request().method()),
                    new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                    action);
        } catch (Exception e) {
            log.warn("Equipment unit history response wait timed out: {}", e.getMessage());
        }
    }

    public EquipmentListPage expandGroup(String groupName) {
        Locator toggle = expandToggle(groupName);
        if (toggle.count() == 0) {
            return this;
        }
        if ("Розгорнути".equals(toggle.getAttribute("aria-label"))) {
            toggle.click();
            expandedGroupSubTable(groupName)
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(uiTimeoutMs()));
        }
        return this;
    }

    public EquipmentListPage sortExpandedGroupByColumn(String groupName, String columnHeader) {
        Locator header = expandedGroupSubTable(groupName)
                .locator("thead th")
                .filter(new Locator.FilterOptions().setHasText(columnHeader));
        Locator sortButton = header.locator("button");
        if (sortButton.count() > 0) {
            sortButton.first().click();
        } else {
            header.first().click();
        }
        return this;
    }

    public List<String> readExpandedGroupInventoryNumbers(String groupName) {
        PollUtils.waitUntilTrue(
                () -> expandedGroupSubTable(groupName).locator("tbody tr").count() > 0,
                uiTimeoutMs(),
                "Expanded group rows for " + groupName);
        List<String> numbers = new ArrayList<>();
        Locator rows = expandedGroupSubTable(groupName).locator("tbody tr");
        int rowCount = rows.count();
        for (int i = 0; i < rowCount; i++) {
            Locator inventoryCell = rows.nth(i).locator("td button.font-medium").first();
            if (inventoryCell.count() == 0) {
                inventoryCell = rows.nth(i).locator("td").nth(inventoryColumnIndex(groupName));
            }
            String text = inventoryCell.innerText().trim();
            if (!text.isBlank()) {
                numbers.add(text);
            }
        }
        return numbers;
    }

    public boolean isGroupVisible(String groupName) {
        return groupRow(groupName).count() > 0;
    }

    public List<String> getDisplayedEquipmentNames() {
        List<String> names = new ArrayList<>();
        Locator nameButtons = tableContainer().locator("tbody button");
        int buttonCount = nameButtons.count();
        for (int i = 0; i < buttonCount; i++) {
            String text = nameButtons.nth(i).innerText().trim();
            if (!text.isBlank()) {
                names.add(text);
            }
        }
        if (!names.isEmpty()) {
            return names;
        }

        Locator nameSpans = tableContainer().locator("tbody span.font-medium");
        int spanCount = nameSpans.count();
        for (int i = 0; i < spanCount; i++) {
            String text = nameSpans.nth(i).innerText().trim();
            if (!text.isBlank()) {
                names.add(text);
            }
        }
        return names;
    }

    public boolean hasAllEmployeesDefaultOption(List<String> options) {
        Pattern pattern = Pattern.compile(ALL_EMPLOYEES_OPTION_PATTERN);
        return options.stream().anyMatch(option -> pattern.matcher(option).matches());
    }

    private void runGroupedEquipmentFilterAction(Runnable action) {
        waitForGroupedEquipmentDuring(action);
        waitForTableSettled();
    }

    private void waitForGroupedEquipmentDuring(Runnable action) {
        page.waitForResponse(
                response -> response.url().contains("/equipment/grouped")
                        && "GET".equals(response.request().method()),
                action);
    }

    private Locator assigneeFilterTrigger() {
        return filterBar().locator("button[role='combobox']").nth(1);
    }

    private Locator filterBar() {
        return page.getByPlaceholder(SEARCH_PLACEHOLDER)
                .locator("xpath=ancestor::div[contains(@class,'flex-wrap')][1]");
    }

    private Locator tableContainer() {
        return page.locator(TABLE_CONTAINER_SELECTOR).first();
    }

    private Locator groupRow(String groupName) {
        return tableContainer()
                .locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(groupName))
                .first();
    }

    private Locator expandedGroupSubTable(String groupName) {
        return groupRow(groupName)
                .locator("xpath=following-sibling::tr[@data-state='expanded'][1]//table");
    }

    private int inventoryColumnIndex(String groupName) {
        Locator headers = expandedGroupSubTable(groupName).locator("thead th");
        int count = headers.count();
        for (int i = 0; i < count; i++) {
            if (headers.nth(i).innerText().contains("Інв")) {
                return i;
            }
        }
        return 2;
    }
}
