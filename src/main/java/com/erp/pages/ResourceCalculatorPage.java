package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.UiDownloadAssertions;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Page Object for tk-ui {@code ResourceCalculatorPage}.
 * URL: /technological-maps/resource-calculator
 */
@Slf4j
public class ResourceCalculatorPage extends BasePage {

    public static final String PATH = "/technological-maps/resource-calculator";
    public static final String PAGE_TITLE = "Калькулятор розхідників";
    public static final String ALL_LOCATIONS_BANNER = "Оберіть конкретну локацію для розрахунку";
    public static final String EMPTY_PROMPT = "Оберіть тех. карту та вкажіть кількість продукції";
    public static final String NO_INPUTS = "У тех. карти немає вхідних ресурсів";

    private static final String TECH_MAP_PLACEHOLDER = "Оберіть тех. карту";
    private static final String TECH_MAP_LOADING = "Завантаження...";
    private static final String CALCULATE_BUTTON = "Розрахувати";
    private static final String EXPORT_BUTTON = "Експорт в Excel";
    private static final String ONLY_MY_LOCATION = "Тільки моя локація";
    private static final String TREE_TAB = "Дерево";
    private static final String SUMMARY_TAB = "Зведення";
    private static final String COMBOBOX_ITEM = "[data-slot='combobox-item']";
    private static final String CALCULATE_API = "/technological-maps/calculate-resource-usage";
    private static final String EXPORT_API = "/technological-maps/calculate-resource-usage/export";
    private static final int DOWNLOAD_EVENT_GRACE_MS = 3_000;

    public ResourceCalculatorPage(Page page) {
        super(page);
    }

    public ResourceCalculatorPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Калькулятор розхідників");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return this;
    }

    public ResourceCalculatorPage waitForTitle() {
        heading().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isTitleVisible() {
        return heading().count() > 0 && heading().first().isVisible();
    }

    public boolean isAllLocationsBannerVisible() {
        Locator banner = page.getByText(ALL_LOCATIONS_BANNER);
        return banner.count() > 0 && banner.first().isVisible();
    }

    public boolean isCalculateFormVisible() {
        return calculateButton().count() > 0 && calculateButton().isVisible();
    }

    public ResourceCalculatorPage selectTechMap(String nameFragment) {
        waitForTechMapComboboxReady();
        Locator input = techMapInput();
        input.click();
        input.fill(nameFragment);
        page.waitForCondition(
                () -> page.locator(COMBOBOX_ITEM).filter(
                        new Locator.FilterOptions().setHasText(nameFragment)).count() > 0,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        page.locator(COMBOBOX_ITEM)
                .filter(new Locator.FilterOptions().setHasText(nameFragment))
                .first()
                .click();
        return this;
    }

    public ResourceCalculatorPage setAmount(String amount) {
        amountInput().fill(amount);
        return this;
    }

    public ResourceCalculatorPage calculate() {
        waitForResponseTolerant(
                response -> response.url().contains(CALCULATE_API)
                        && !response.url().contains("/export")
                        && "GET".equals(response.request().method())
                        && response.status() < 500,
                () -> calculateButton().click(),
                "calculate-resource-usage");
        page.waitForTimeout(400);
        return this;
    }

    public ResourceCalculatorPage openSummaryTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(SUMMARY_TAB)).click();
        return this;
    }

    public ResourceCalculatorPage openTreeTab() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(TREE_TAB)).click();
        return this;
    }

    public boolean isTreeTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(TREE_TAB)).count() > 0;
    }

    public boolean isSummaryTabVisible() {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(SUMMARY_TAB)).count() > 0;
    }

    public ResourceCalculatorPage setOnlyMyLocation(boolean checked) {
        Locator checkbox = page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(ONLY_MY_LOCATION));
        checkbox.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        if (checkbox.isChecked() != checked) {
            checkbox.click();
        }
        return this;
    }

    public boolean isOnlyMyLocationVisible() {
        return page.getByText(ONLY_MY_LOCATION, new Page.GetByTextOptions().setExact(true)).count() > 0;
    }

    public boolean isResourceVisible(String resourceName) {
        Locator match = page.getByText(resourceName, new Page.GetByTextOptions().setExact(true));
        return match.count() > 0 && match.first().isVisible();
    }

    public boolean isAmountVisible(String formattedAmount) {
        return page.getByText(formattedAmount).count() > 0;
    }

    public boolean isExportEnabled() {
        Locator button = exportButton();
        return button.count() > 0 && button.isEnabled();
    }

    public Path exportToExcel() {
        List<Download> downloads = Collections.synchronizedList(new ArrayList<>());
        Consumer<Download> downloadListener = downloads::add;
        page.onDownload(downloadListener);
        try {
            com.microsoft.playwright.Response response = page.waitForResponse(
                    r -> r.url().contains(EXPORT_API) && "POST".equals(r.request().method()),
                    new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                    () -> exportButton().click());
            try {
                page.waitForCondition(() -> !downloads.isEmpty(),
                        new Page.WaitForConditionOptions().setTimeout(DOWNLOAD_EVENT_GRACE_MS));
            } catch (PlaywrightException e) {
                log.debug("No browser download event for calculator export — using response payload");
            }
            if (!downloads.isEmpty()) {
                Path path = downloads.getFirst().path();
                long size = path != null ? path.toFile().length() : 0L;
                UiDownloadAssertions.assertNonEmptyXlsx(path, size, "Калькулятор Excel");
                return path;
            }
            byte[] body = response.body();
            Path path = Files.createTempFile("erp-calc-export-", ".xlsx");
            Files.write(path, body);
            UiDownloadAssertions.assertNonEmptyXlsx(path, body.length, "Калькулятор Excel (response)");
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist calculator export", e);
        } finally {
            page.offDownload(downloadListener);
        }
    }

    public ResourceCalculatorPage waitUntilResourceVisible(String resourceName) {
        waitForConditionTolerant(
                () -> isResourceVisible(resourceName),
                "resource visible: " + resourceName);
        return this;
    }

    private void waitForTechMapComboboxReady() {
        page.waitForCondition(
                () -> {
                    Locator ready = page.getByPlaceholder(TECH_MAP_PLACEHOLDER);
                    Locator loading = page.getByPlaceholder(TECH_MAP_LOADING);
                    return ready.count() > 0 && ready.first().isEnabled()
                            && (loading.count() == 0 || !loading.first().isVisible());
                },
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator heading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE));
    }

    private Locator techMapInput() {
        return page.getByPlaceholder(TECH_MAP_PLACEHOLDER)
                .or(page.getByPlaceholder(TECH_MAP_LOADING))
                .first();
    }

    private Locator amountInput() {
        return page.locator("input[inputmode='decimal']").first();
    }

    private Locator calculateButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CALCULATE_BUTTON));
    }

    private Locator exportButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(EXPORT_BUTTON));
    }
}
