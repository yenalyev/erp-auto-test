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
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
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
}
