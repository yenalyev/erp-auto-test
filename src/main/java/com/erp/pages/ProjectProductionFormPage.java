package com.erp.pages;

import com.erp.pages.components.ProjectProductionStagesSection;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for create/edit project production form.
 * <p>
 * Stage interactions go through {@link ProjectProductionStagesSection}
 * (shared DOM with the template form: stage cards, then «Додати етап»).
 */
@Slf4j
public class ProjectProductionFormPage extends BasePage {

    private static final String TITLE_CREATE = "Створення проєктного виробництва";
    private static final String TITLE_EDIT = "Редагування проєктного виробництва";
    private static final String CREATE_BUTTON = "Створити проєкт";
    private static final String FINISH_BUTTON = "Завершити проєкт";
    private static final String SERIAL_PLACEHOLDER = "Серійний номер...";
    private static final String CATEGORY_PLACEHOLDER = "Оберіть категорію...";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String AUTOCOMPLETE_SEARCH_PLACEHOLDER = "Пошук...";

    private final ProjectProductionStagesSection stages;

    public ProjectProductionFormPage(Page page) {
        super(page);
        this.stages = new ProjectProductionStagesSection(page, uiTimeoutMs());
    }

    /** Shared stages section — reusable by template form page objects. */
    public ProjectProductionStagesSection stages() {
        return stages;
    }

    public ProjectProductionFormPage waitForCreateLoaded() {
        waitForFormHeading(TITLE_CREATE);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        stages.root().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public ProjectProductionFormPage waitForEditLoaded() {
        page.waitForURL("**/project-production/update/**",
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        waitForFormHeading(TITLE_EDIT);
        stages.root().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    private void waitForFormHeading(String title) {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached — proceeding: {}", e.getMessage());
        }
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(title))
                .first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
    }

    public ProjectProductionFormPage selectCategory(String categoryName) {
        Locator input = page.getByPlaceholder(CATEGORY_PLACEHOLDER);
        input.click();
        input.fill(categoryName);
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(categoryName))
                .first()
                .click();
        return this;
    }

    public ProjectProductionFormPage selectProduct(String productName) {
        page.locator("[data-slot='select-trigger']").first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(productName))
                .first()
                .click();
        return this;
    }

    public ProjectProductionFormPage fillSerialNumber(String serialNumber) {
        page.getByPlaceholder(SERIAL_PLACEHOLDER).fill(serialNumber);
        return this;
    }

    public Locator stagesSection() {
        return stages.root();
    }

    public Locator addStageButton() {
        return stages.addStageButton();
    }

    public boolean isAddStageButtonAfterAllStages() {
        return stages.isAddStageButtonAfterAllStages();
    }

    public String describeAddStageButtonPlacement() {
        return stages.describeAddStageButtonPlacement();
    }

    public int stageCardCount() {
        return stages.stageCardCount();
    }

    /**
     * Clicks «Додати етап» at the end of the stages list.
     * Create mode: appends a local stage card. Edit mode: opens «Новий етап» inline form.
     */
    public ProjectProductionFormPage clickAddStage() {
        stages.clickAddStage();
        return this;
    }

    public Locator stageCard(int order) {
        return stages.stageCard(order);
    }

    public Locator newStageForm() {
        return stages.newStageForm();
    }

    public ProjectProductionFormPage ensureStageExpanded(int order) {
        stages.ensureStageExpanded(order);
        return this;
    }

    /**
     * Fills percentage / optional resource on the given 1-based stage card (create mode).
     * Resource picker is tk-ui Autocomplete (combobox trigger + «Пошук...» popover).
     */
    public ProjectProductionFormPage configureStage(int order, String resourceName, double amountNeeded) {
        return configureStage(order, 100, resourceName, amountNeeded);
    }

    public ProjectProductionFormPage configureStage(int order, int executionPercentage,
                                                     String resourceName, double amountNeeded) {
        stages.fillExecutionPercentage(order, executionPercentage);

        if (resourceName != null && !resourceName.isBlank()) {
            selectResourceViaAutocomplete(stages.stageCard(order), resourceName);
            stages.amountInput(order).fill(String.valueOf(amountNeeded));
        } else {
            stages.removeFirstUsageRow(order);
        }
        return this;
    }

    /**
     * Configures the first create-stage card (default empty stage rendered on create).
     * Prefer {@link #configureStage(int, String, double)} when working with multiple stages.
     */
    public ProjectProductionFormPage configureDefaultStage(String resourceName, double amountNeeded) {
        return configureStage(1, resourceName, amountNeeded);
    }

    /**
     * Create-mode helper: add a stage via «Додати етап» (after existing cards) and configure it.
     */
    public ProjectProductionFormPage addAndConfigureStage(String resourceName, double amountNeeded) {
        int before = stageCardCount();
        clickAddStage();
        page.waitForCondition(() -> stageCardCount() > before,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return configureStage(before + 1, resourceName, amountNeeded);
    }

    private void selectResourceViaAutocomplete(Locator scope, String resourceName) {
        String trimmed = resourceName.trim();
        String term = trimmed.length() > 12 ? trimmed.substring(0, 12) : trimmed;

        Locator trigger = scope.getByRole(AriaRole.COMBOBOX)
                .filter(new Locator.FilterOptions().setHasText(RESOURCE_PLACEHOLDER))
                .first();
        trigger.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        trigger.click();

        Locator search = page.getByPlaceholder(AUTOCOMPLETE_SEARCH_PLACEHOLDER);
        search.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                response -> response.url().contains("/resources/autocomplete")
                        && "GET".equals(response.request().method()),
                () -> search.fill(term));
        waitForComboboxOptionsSettled();

        page.getByRole(AriaRole.OPTION)
                .filter(new Locator.FilterOptions().setHasText(term))
                .first()
                .click();
    }

    public ProjectProductionListPage createProject() {
        Locator createBtn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_BUTTON));
        createBtn.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && !createBtn.isEnabled()) {
            page.waitForTimeout(200);
        }
        createBtn.click();
        waitUntilOnListPage();
        return new ProjectProductionListPage(page).waitForLoaded();
    }

    public ProjectProductionListPage finishProject() {
        // Pre-CPMA-646: window.confirm; post-CPMA-646: React ConfirmDialog (alertdialog)
        page.onceDialog(dialog -> dialog.accept());

        Locator finishBtn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(FINISH_BUTTON));
        finishBtn.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline && finishBtn.isDisabled()) {
            page.waitForTimeout(200);
        }
        if (finishBtn.isDisabled()) {
            throw new IllegalStateException(
                    "«Завершити проєкт» disabled — сума відсотків етапів має бути 100%");
        }
        finishBtn.click();

        Locator dialogConfirm = page.locator("[role='alertdialog']")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(FINISH_BUTTON));
        try {
            dialogConfirm.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(3_000));
            dialogConfirm.click();
        } catch (Exception e) {
            log.debug("No ConfirmDialog — relying on native confirm handler: {}", e.getMessage());
        }
        waitUntilOnListPage();
        return new ProjectProductionListPage(page).waitForLoaded();
    }

    private void waitUntilOnListPage() {
        page.waitForURL(
                url -> {
                    String u = url.toString();
                    return u.contains(ProjectProductionListPage.PATH)
                            && !u.contains("/create")
                            && !u.contains("/update");
                },
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
    }

    public ProjectProductionFormPage openEdit(Long productionId) {
        String url = ConfigProvider.getBaseUrl() + "/project-production/update/" + productionId;
        navigateTo(url, TITLE_EDIT);
        return waitForEditLoaded();
    }
}
