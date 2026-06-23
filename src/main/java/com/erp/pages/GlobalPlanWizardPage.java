package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GlobalPlanWizardPage extends BasePage {

    private static final String WIZARD_HEADING = "Декомпозиція виробничого плану";
    private static final String TAB_1 = "1. Заплановано";
    private static final String TAB_2 = "2. Хто буде виробляти?";
    private static final String TAB_3 = "3. Потрібно ресурсів";
    private static final String TAB_4 = "4. Плани на локації";
    private static final String CREATE_PLAN_BUTTON = "Створити план";
    private static final String DISTRIBUTE_BUTTON = "Розподілити по локаціям";
    private static final String TAB3_NEXT_BUTTON = "Далі";
    private static final String GENERATE_BUTTON = "Створити плани по локаціям";
    private static final String DONE_BUTTON = "Готово";
    private static final String DECOMPOSITION_SPINNER = "Розрахунок декомпозиції";
    private static final String ASSIGN_DIALOG_TITLE_PREFIX = "Призначення виробництва";
    private static final String NO_TECH_MAP_BADGE = "Немає доступної техкарти";
    private static final String TECH_MAP_FIELD_LABEL = "Технологічна карта";
    private static final String RESOURCE_COMBO_PLACEHOLDER = "Оберіть зі списку...";
    private static final String AMOUNT_PLACEHOLDER = "Введіть кількість...";
    private static final Pattern PLAN_ID_IN_URL = Pattern.compile("/global-plans/(\\d+)");
    private static final Pattern PLAN_ID_IN_JSON = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    private Long lastCreatedPlanId;

    private static final String[] UKRAINIAN_MONTHS = {
            "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
            "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"
    };

    public record ProductionAssignment(String storageName, String mapName, String amount) {}

    public GlobalPlanWizardPage(Page page) {
        super(page);
    }

    public GlobalPlanWizardPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(WIZARD_HEADING))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isWizardHeadingVisible() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(WIZARD_HEADING))
                .isVisible();
    }

    public boolean isTabVisible(String tabLabel) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabLabel)).isVisible();
    }

    public boolean isTabDisabled(String tabLabel) {
        Locator tab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabLabel));
        if (tab.count() == 0) {
            return false;
        }
        String disabled = tab.first().getAttribute("disabled");
        String dataDisabled = tab.first().getAttribute("data-disabled");
        return "true".equals(disabled) || tab.first().isDisabled() || "true".equals(dataDisabled);
    }

    public boolean areLateTabsDisabledOnFreshCreate() {
        return isTabDisabled(TAB_3) && isTabDisabled(TAB_4);
    }

    public boolean isFirstTabVisible() {
        return isTabVisible(TAB_1);
    }

    @Step("Tab 1: заповнити опис «{description}»")
    public GlobalPlanWizardPage fillDescription(String description) {
        log.info("Global plan wizard — description: {}", description);
        page.locator("div.space-y-2")
                .filter(new Locator.FilterOptions().setHasText("Опис плану"))
                .locator("input")
                .fill(description);
        return this;
    }

    @Step("Tab 1: обрати період {month}/{year}")
    public GlobalPlanWizardPage selectPeriod(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be 1..12, got " + month);
        }
        log.info("Global plan wizard — period: {}/{}", month, year);
        selectComboboxNearLabel("Місяць", UKRAINIAN_MONTHS[month - 1]);
        selectComboboxNearLabel("Рік", String.valueOf(year));
        return this;
    }

    @Step("Tab 1: обрати виріб «{resourceName}» кількість {amount}")
    public GlobalPlanWizardPage fillOutputProduct(String resourceName, String amount) {
        String trimmed = resourceName.trim();
        log.info("Global plan wizard — output: {} x {}", trimmed, amount);
        Locator combo = page.getByPlaceholder(RESOURCE_COMBO_PLACEHOLDER).first();
        combo.click();
        combo.fill(trimmed);

        Locator option = page.locator("[data-slot='combobox-item']")
                .filter(new Locator.FilterOptions().setHasText(trimmed))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();

        Locator amountField = page.getByPlaceholder(AMOUNT_PLACEHOLDER).first();
        amountField.fill(amount);
        waitForCreatePlanEnabled();
        return this;
    }

    @Step("Tab 1: дочекатися активної кнопки «Створити план»")
    public GlobalPlanWizardPage waitForCreatePlanEnabled() {
        Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_PLAN_BUTTON));
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (button.isEnabled()) {
                return this;
            }
            page.waitForTimeout(200);
        }
        if (page.getByText("вже існує").isVisible()) {
            throw new IllegalStateException("Глобальний план на обраний період вже існує");
        }
        throw new IllegalStateException(
                "«Створити план» не активувалась — перевірте вибір виробу (техкарта PRODUCTION) та кількість");
    }

    @Step("Tab 1: натиснути «Створити план»")
    public GlobalPlanWizardPage submitCreatePlan() {
        log.info("Global plan wizard — submitting Tab 1");
        waitForCreatePlanEnabled();
        Response createResponse = page.waitForResponse(
                response -> response.url().contains("/global-plans")
                        && "POST".equals(response.request().method()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CREATE_PLAN_BUTTON))
                        .click());
        if (createResponse.status() < 200 || createResponse.status() >= 300) {
            throw new IllegalStateException(
                    "Create global plan failed: HTTP " + createResponse.status() + " — " + createResponse.text());
        }
        lastCreatedPlanId = parsePlanIdFromResponse(createResponse.text());
        waitForTab2AfterCreate();
        return this;
    }

    public Long extractPlanIdFromUrl() {
        Matcher matcher = PLAN_ID_IN_URL.matcher(page.url());
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        if (lastCreatedPlanId != null) {
            return lastCreatedPlanId;
        }
        throw new IllegalStateException("Global plan id not found in URL or create response: " + page.url());
    }

    private void waitForTab2AfterCreate() {
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (isTabEnabled(TAB_2)) {
                return;
            }
            page.waitForTimeout(200);
        }
        throw new IllegalStateException("Tab 2 did not unlock after plan creation");
    }

    private Long parsePlanIdFromResponse(String body) {
        Matcher matcher = PLAN_ID_IN_JSON.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("Plan id not found in create response: " + body);
        }
        return Long.parseLong(matcher.group(1));
    }

    @Step("Tab 2: дочекатися завершення розрахунку декомпозиції")
    public GlobalPlanWizardPage waitForDecompositionIdle() {
        log.info("Global plan wizard — waiting for decomposition to settle");
        final long[] lastDecomposeAt = {0};
        page.onResponse(response -> {
            if (response.url().contains("/decompose") && response.status() >= 200 && response.status() < 300) {
                lastDecomposeAt[0] = System.currentTimeMillis();
            }
        });

        Locator spinner = page.getByText(DECOMPOSITION_SPINNER);
        if (spinner.count() > 0 && spinner.first().isVisible()) {
            spinner.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (page.getByText("Рівень").count() > 0 || isDistributeButtonVisible()) {
                waitForDecomposeNetworkQuiet(lastDecomposeAt, 1_000);
                return this;
            }
            page.waitForTimeout(200);
        }
        throw new IllegalStateException("Tab 2 decomposition content did not appear within timeout");
    }

    @Step("Tab 2: розгорнути рівень декомпозиції для «{resourceName}»")
    public GlobalPlanWizardPage expandDecompositionForResource(String resourceName) {
        Locator row = decompositionItemRow(resourceName);
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (row.count() > 0 && row.isVisible()) {
                return this;
            }
            Locator closedLevel = page.locator("[data-state='closed']")
                    .filter(new Locator.FilterOptions().setHasText("Рівень"))
                    .first();
            if (closedLevel.count() == 0) {
                break;
            }
            closedLevel.click();
            page.waitForTimeout(200);
        }
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isAssignButtonVisibleForResource(String resourceName) {
        return resourceRow(resourceName)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Призначити"))
                .or(resourceRow(resourceName)
                        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Змінити")))
                .isVisible();
    }

    public boolean hasMissingTechMapForResource(String resourceName) {
        expandDecompositionForResource(resourceName);
        return resourceRow(resourceName)
                .getByText(NO_TECH_MAP_BADGE)
                .isVisible();
    }

    public boolean isAutoAssignedForResource(String resourceName) {
        return resourceRow(resourceName)
                .getByText("авто", new Locator.GetByTextOptions().setExact(true))
                .isVisible();
    }

    @Step("Tab 2: дочекатися призначення виробництва для «{resourceName}»")
    public GlobalPlanWizardPage waitForResourceProductionSettled(String resourceName) {
        log.info("Global plan wizard — waiting for production assignment: {}", resourceName);
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            Locator spinner = page.getByText(DECOMPOSITION_SPINNER);
            if (spinner.count() > 0 && spinner.first().isVisible()) {
                page.waitForTimeout(200);
                continue;
            }
            if (hasMissingTechMapForResource(resourceName)) {
                throw new IllegalStateException("Немає доступної техкарти для «" + resourceName + "»");
            }
            if (isProductionAssignedForResource(resourceName)) {
                return this;
            }
            page.waitForTimeout(300);
        }
        throw new IllegalStateException("Виробництво не призначено для «" + resourceName + "» після декомпозиції");
    }

    public boolean isProductionAssignedForResource(String resourceName) {
        expandDecompositionForResource(resourceName);
        Locator row = decompositionItemRow(resourceName);
        if (row.count() == 0) {
            return false;
        }
        return row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Змінити")).isVisible()
                || row.getByText("авто", new Locator.GetByTextOptions().setExact(true)).isVisible();
    }

    @Step("Tab 2: перевірити техкарту «{expectedMapName}» для «{resourceName}»")
    public GlobalPlanWizardPage verifyAssignedTechMap(String resourceName, String storageName, String expectedMapName) {
        log.info("Global plan wizard — verify tech map {} for {}", expectedMapName, resourceName);
        openAssignmentDialogForResource(resourceName);
        Locator dialog = assignmentDialog();
        Locator assignmentRow = assignmentDialogRows().first();
        Locator mapField = techMapFieldInDialog(dialog);

        String displayed = mapField.innerText();
        if (!displayed.contains(expectedMapName)) {
            selectDialogCombobox(assignmentRow, 0, storageName);
            mapField = techMapFieldInDialog(dialog);
            displayed = mapField.innerText();
            if (!displayed.contains(expectedMapName)) {
                mapField.click();
                boolean optionVisible = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(expectedMapName))
                        .or(page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions()
                                .setName(Pattern.compile(".*" + Pattern.quote(expectedMapName) + ".*"))))
                        .first()
                        .isVisible();
                if (!optionVisible) {
                    throw new AssertionError(String.format(
                            "Очікувалась техкарта «%s» для «%s» на локації «%s», у діалозі: «%s»",
                            expectedMapName, resourceName, storageName, displayed));
                }
            }
        }
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Скасувати"))
                .click();
        dialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Tab 2: призначити виробництво «{resourceName}»")
    public GlobalPlanWizardPage assignProduction(String resourceName, List<ProductionAssignment> assignments) {
        log.info("Global plan wizard — assign {}: {}", resourceName, assignments);
        openAssignmentDialogForResource(resourceName);

        Locator dialog = assignmentDialog();
        dialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));

        for (int i = 0; i < assignments.size(); i++) {
            if (i > 0) {
                dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Додати локацію"))
                        .click();
            }
            ProductionAssignment line = assignments.get(i);
            Locator row = assignmentDialogRows().nth(i);
            selectDialogCombobox(row, 0, line.storageName());
            if (line.mapName() != null && !line.mapName().isBlank()) {
                Locator mapCombo = row.getByRole(AriaRole.COMBOBOX).nth(1);
                if (mapCombo.isEnabled()) {
                    selectDialogCombobox(row, 1, line.mapName());
                } else {
                    String lockedMap = mapCombo.innerText();
                    if (!lockedMap.contains(line.mapName())) {
                        throw new IllegalStateException(String.format(
                                "Очікувалась техкарта «%s», у полі: «%s»", line.mapName(), lockedMap));
                    }
                }
            }
            row.locator("input[type='number']").fill(line.amount());
        }

        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Зберегти"))
                .click();
        dialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return waitForDecompositionIdle();
    }

    @Step("Tab 2: дочекатися активної кнопки «Розподілити по локаціям»")
    public GlobalPlanWizardPage waitForDistributeEnabled() {
        log.info("Global plan wizard — waiting for distribute button");
        Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DISTRIBUTE_BUTTON));
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            waitForDecompositionIdleQuiet();
            if (button.isVisible() && button.isEnabled()) {
                return this;
            }
            page.waitForTimeout(300);
        }
        throw new IllegalStateException("«Розподілити по локаціям» did not become enabled");
    }

    public boolean isDistributeButtonVisible() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DISTRIBUTE_BUTTON))
                .isVisible();
    }

    @Step("Tab 2: натиснути «Розподілити по локаціям»")
    public GlobalPlanWizardPage clickDistributeToLocations() {
        log.info("Global plan wizard — distributing to locations");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DISTRIBUTE_BUTTON))
                .click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(TAB3_NEXT_BUTTON))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Tab 3: перейти до планів на локаціях")
    public GlobalPlanWizardPage proceedFromRequirementsTab() {
        log.info("Global plan wizard — Tab 3 next");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(TAB3_NEXT_BUTTON))
                .click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GENERATE_BUTTON))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isRequirementsTabVisible() {
        return page.getByText("Напівфабрикати").isVisible()
                || page.getByText("Сировина (не виробляється)").isVisible();
    }

    @Step("Tab 4: створити плани по локаціях")
    public GlobalPlanWizardPage generateLocationPlans() {
        log.info("Global plan wizard — generating location plans");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GENERATE_BUTTON))
                .click();
        waitForGenerationResult();
        return this;
    }

    @Step("Tab 4: дочекатися результату генерації")
    public GlobalPlanWizardPage waitForGenerationResult() {
        Locator createdBadge = page.getByText("Створено", new Page.GetByTextOptions().setExact(true));
        Locator replacedBadge = page.getByText("Замінено", new Page.GetByTextOptions().setExact(true));
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (createdBadge.count() > 0 || replacedBadge.count() > 0) {
                return this;
            }
            page.waitForTimeout(300);
        }
        throw new IllegalStateException("Location plan generation result badge not shown");
    }

    @Step("Tab 4: завершити wizard («Готово»)")
    public GlobalPlansPage clickDoneAndReturnToList() {
        log.info("Global plan wizard — done, returning to list");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DONE_BUTTON))
                .click();
        return new GlobalPlansPage(page).waitForLoaded();
    }

    public boolean isTabEnabled(String tabLabel) {
        Locator tab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabLabel));
        return tab.isVisible() && !isTabDisabled(tabLabel);
    }

    private void openAssignmentDialogForResource(String resourceName) {
        expandDecompositionForResource(resourceName);
        resourceRow(resourceName)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Призначити"))
                .or(resourceRow(resourceName)
                        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Змінити")))
                .click();
    }

    private Locator resourceRow(String resourceName) {
        return decompositionItemRow(resourceName);
    }

    private Locator decompositionItemRow(String resourceName) {
        return page.locator("div.flex.flex-col.gap-2.px-4")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("div.font-medium.text-gray-900")
                                .filter(new Locator.FilterOptions().setHasText(resourceName))));
    }

    private Locator techMapFieldInDialog(Locator dialog) {
        return dialog.locator("div.space-y-1")
                .filter(new Locator.FilterOptions().setHas(
                        page.getByText(TECH_MAP_FIELD_LABEL, new Page.GetByTextOptions().setExact(true))))
                .locator("[role='combobox'], button")
                .first();
    }

    private void waitForDecomposeNetworkQuiet(long[] lastDecomposeAt, long quietPeriodMs) {
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (lastDecomposeAt[0] > 0 && System.currentTimeMillis() - lastDecomposeAt[0] >= quietPeriodMs) {
                return;
            }
            page.waitForTimeout(150);
        }
        throw new IllegalStateException("Decompose API chain did not settle");
    }

    private Locator assignmentDialog() {
        return page.getByRole(AriaRole.DIALOG)
                .filter(new Locator.FilterOptions().setHasText(ASSIGN_DIALOG_TITLE_PREFIX));
    }

    private Locator assignmentDialogRows() {
        return assignmentDialog().locator("div.flex.flex-col.sm\\:flex-row");
    }

    private void selectComboboxNearLabel(String label, String optionText) {
        page.locator("div.space-y-2")
                .filter(new Locator.FilterOptions().setHas(page.getByText(label, new Page.GetByTextOptions().setExact(true))))
                .getByRole(AriaRole.COMBOBOX)
                .click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(optionText)).click();
    }

    private void selectDialogCombobox(Locator row, int index, String optionText) {
        Locator combo = row.getByRole(AriaRole.COMBOBOX).nth(index);
        combo.click();
        Locator option = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(optionText))
                .or(page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions()
                        .setName(Pattern.compile(".*" + Pattern.quote(optionText) + ".*"))))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();
    }

    private void waitForDecompositionIdleQuiet() {
        Locator spinner = page.getByText(DECOMPOSITION_SPINNER);
        if (spinner.count() > 0 && spinner.first().isVisible()) {
            return;
        }
    }
}
