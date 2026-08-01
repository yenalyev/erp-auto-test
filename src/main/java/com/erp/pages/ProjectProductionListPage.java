package com.erp.pages;

import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for Project Production list ({@code /project-production}).
 */
@Slf4j
public class ProjectProductionListPage extends BasePage {

    public static final String PATH = "/project-production";
    public static final String PATH_CREATE = "/project-production/create";

    private static final String TAB_LABEL = "Проєктне виробництво";
    private static final String NEW_PROJECT_BUTTON = "Новий проєкт";

    public ProjectProductionListPage(Page page) {
        super(page);
    }

    public ProjectProductionListPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Project Production list: {}", url);
        navigateTo(url, TAB_LABEL);
        return waitForLoaded();
    }

    public ProjectProductionListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached — proceeding: {}", e.getMessage());
        }

        Locator pageReady = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(TAB_LABEL))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_PROJECT_BUTTON)))
                .or(page.locator("table").first())
                .first();
        pageReady.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isOnListPage() {
        return page.url().contains(PATH) && !page.url().contains("/create") && !page.url().contains("/update");
    }

    public ProjectProductionFormPage clickNewProject() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_PROJECT_BUTTON)).click();
        return new ProjectProductionFormPage(page).waitForCreateLoaded();
    }

    public ProjectProductionFormPage clickEditBySerialNumber(String serialNumber) {
        Locator row = page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(serialNumber));
        row.getByRole(AriaRole.BUTTON).first().click();
        return new ProjectProductionFormPage(page).waitForEditLoaded();
    }

    public boolean hasRowWithSerialNumber(String serialNumber) {
        return findRowBySerial(serialNumber).count() > 0;
    }

    public boolean rowShowsStatus(String serialNumber, String statusLabel) {
        Locator row = findRowBySerial(serialNumber);
        if (row.count() == 0) {
            return false;
        }
        return row.first().getByText(statusLabel, new Locator.GetByTextOptions().setExact(false)).count() > 0;
    }

    /**
     * Clears «Період» so freshly finished/created rows are visible regardless of stored date preset.
     */
    public ProjectProductionListPage clearPeriodFilter() {
        try {
            new DateRangePickerComponent(page, uiTimeoutMs()).clear();
        } catch (Exception e) {
            log.debug("Could not clear period filter: {}", e.getMessage());
        }
        return waitForLoaded();
    }

    public ProjectProductionListPage waitForRowWithSerial(String serialNumber) {
        page.waitForCondition(
                () -> hasRowWithSerialNumber(serialNumber),
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    private Locator findRowBySerial(String serialNumber) {
        return page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(serialNumber));
    }
}
