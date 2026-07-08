package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
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
    private static final String SAVE_CHANGES_BUTTON = "Зберегти зміни";
    private static final String ADD_PRODUCT_BUTTON = "Додати виріб";
    private static final String DISTRIBUTE_BUTTON = "Розподілити по локаціям";
    private static final String TAB3_NEXT_BUTTON = "Далі";
    private static final String GENERATE_BUTTON = "Створити плани по локаціям";
    private static final String DONE_BUTTON = "Готово";
    private static final String DECOMPOSITION_SPINNER = "Розрахунок декомпозиції";
    private static final String DECOMPOSITION_FAILED = "Не вдалося розрахувати декомпозицію";
    private static final String ASSIGN_DIALOG_TITLE_PREFIX = "Призначення виробництва";
    private static final String NO_TECH_MAP_BADGE = "Немає доступної техкарти";
    private static final String TECH_MAP_FIELD_LABEL = "Технологічна карта";
    private static final String RESOURCE_COMBO_PLACEHOLDER = "Оберіть зі списку...";
    private static final String AMOUNT_PLACEHOLDER = "Введіть кількість...";
    private static final String WIZARD_LOADING_TEXT = "Завантаження...";
    private static final String PRODUCT_OUTPUT_ROW =
            "div.overflow-hidden.rounded-\\[6px\\].border.border-gray-200.px-4";
    private static final String PERIOD_FIELDS_ROW = "div.flex.flex-col.md\\:flex-row.gap-2";
    private static final Pattern PLAN_ID_IN_URL = Pattern.compile("/global-plans/(\\d+)");
    private static final Pattern PLAN_ID_IN_JSON = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern PLAN_PUT_URL = Pattern.compile(".*/api/v1/global-plans/\\d+$");
    private static final String TAB_3_SEMI_FINISHED = "Напівфабрикати";
    private static final String TAB_3_RAW_MATERIALS = "Сировина (не виробляється)";
    private static final Pattern AMOUNT_IN_CELL = Pattern.compile("([\\d.,]+)");

    private Long lastCreatedPlanId;

    private static final String[] UKRAINIAN_MONTHS = {
            "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
            "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"
    };

    public record ProductionAssignment(String storageName, String mapName, String amount) {}

    public enum RequirementSection {
        SEMI_FINISHED(TAB_3_SEMI_FINISHED),
        RAW_MATERIALS(TAB_3_RAW_MATERIALS);

        private final String heading;

        RequirementSection(String heading) {
            this.heading = heading;
        }

        public String heading() {
            return heading;
        }
    }

    public GlobalPlanWizardPage(Page page) {
        super(page);
    }

    public GlobalPlanWizardPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        waitForWizardLoadingFinished();
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
        selectPeriodField("Місяць", UKRAINIAN_MONTHS[month - 1]);
        selectPeriodField("Рік", String.valueOf(year));
        return this;
    }

    @Step("Відкрити глобальний план id={planId} для редагування")
    public GlobalPlanWizardPage openById(long planId) {
        String url = ConfigProvider.getBaseUrl() + "/global-plans/" + planId;
        log.info("Global plan wizard — open edit id={}", planId);
        navigateTo(url, "Global plan edit /" + planId);
        waitForLoaded();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_CHANGES_BUTTON))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        outputProductRows().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("Tab 1: обрати виріб «{resourceName}» кількість {amount}")
    public GlobalPlanWizardPage fillOutputProduct(String resourceName, String amount) {
        fillOutputProductAtLastRow(resourceName, amount);
        waitForCreatePlanEnabled();
        return this;
    }

    @Step("Tab 1: змінити кількість output «{resourceName}» на {amount}")
    public GlobalPlanWizardPage setOutputAmount(String resourceName, String amount) {
        log.info("Global plan wizard — set output amount: {} = {}", resourceName, amount);
        Locator row = outputProductRow(resourceName);
        Locator amountInput = row.locator("input[type='number']")
                .or(row.getByPlaceholder(AMOUNT_PLACEHOLDER));
        amountInput.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        amountInput.fill("");
        amountInput.fill(amount);
        waitForSaveChangesEnabled();
        return this;
    }

    @Step("Tab 1: додати виріб «{resourceName}» кількість {amount}")
    public GlobalPlanWizardPage addOutputProduct(String resourceName, String amount) {
        log.info("Global plan wizard — add output: {} x {}", resourceName, amount);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_PRODUCT_BUTTON))
                .click();
        fillOutputProductAtLastRow(resourceName, amount);
        waitForSaveChangesEnabled();
        return this;
    }

    @Step("Tab 1: дочекатися активної кнопки «Зберегти зміни»")
    public GlobalPlanWizardPage waitForSaveChangesEnabled() {
        Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_CHANGES_BUTTON));
        long deadline = System.currentTimeMillis() + uiTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (button.isEnabled()) {
                return this;
            }
            page.waitForTimeout(200);
        }
        throw new IllegalStateException("«Зберегти зміни» не активувалась — перевірте рядки output");
    }

    /**
     * Saves Tab 1 edits (PUT), then waits for the auto-started Tab 2 {@code POST /decompose}
     * triggered by {@code onSaved → setActiveTab('2') → start()}.
     *
     * @return HTTP status of the first decompose response after save
     */
    @Step("Tab 1: зберегти зміни і дочекатися POST /decompose")
    public int submitSaveChangesAndAwaitDecomposition() {
        log.info("Global plan wizard — saving Tab 1 changes and awaiting decompose");
        waitForSaveChangesEnabled();
        Response decomposeResponse = page.waitForResponse(
                response -> response.url().contains("/decompose")
                        && "POST".equals(response.request().method()),
                () -> {
                    Response putResponse = page.waitForResponse(
                            response -> PLAN_PUT_URL.matcher(response.url()).matches()
                                    && "PUT".equals(response.request().method()),
                            () -> page.getByRole(AriaRole.BUTTON,
                                            new Page.GetByRoleOptions().setName(SAVE_CHANGES_BUTTON))
                                    .click());
                    if (putResponse.status() < 200 || putResponse.status() >= 300) {
                        throw new IllegalStateException(
                                "Update global plan failed: HTTP " + putResponse.status()
                                        + " — " + putResponse.text());
                    }
                });
        return decomposeResponse.status();
    }

    public boolean isDecompositionFailedVisible() {
        Locator failed = page.getByText(DECOMPOSITION_FAILED);
        return failed.count() > 0 && failed.first().isVisible();
    }

    public boolean isResourceVisibleInDecomposition(String resourceName) {
        Locator row = decompositionItemRow(resourceName);
        if (row.count() > 0 && row.first().isVisible()) {
            return true;
        }
        Locator closedLevels = page.locator("[data-state='closed']")
                .filter(new Locator.FilterOptions().setHasText("Рівень"));
        int levels = closedLevels.count();
        for (int i = 0; i < levels; i++) {
            closedLevels.nth(i).click();
            page.waitForTimeout(200);
            row = decompositionItemRow(resourceName);
            if (row.count() > 0 && row.first().isVisible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param levelNumber 1-based decomposition level («Рівень 1» = block 0 / direct outputs)
     */
    public boolean isResourceVisibleInDecompositionLevel(String resourceName, int levelNumber) {
        expandDecompositionLevelIfClosed(levelNumber);
        Locator levelSection = decompositionLevelSection(levelNumber);
        if (levelSection.count() == 0) {
            return false;
        }
        Locator row = decompositionRowInSection(levelSection, resourceName);
        return row.count() > 0 && row.first().isVisible();
    }

    /** Text from «Потрібно: …» line for a resource row in Tab 2 decomposition. */
    public String getDecompositionRequiredAmountText(String resourceName) {
        expandDecompositionForResource(resourceName);
        Locator row = decompositionItemRow(resourceName);
        if (row.count() == 0) {
            return "";
        }
        Locator required = row.locator("div.text-sm.text-gray-500").first();
        return required.count() > 0 ? required.innerText().trim() : "";
    }

    private Locator outputProductRows() {
        return page.locator(PRODUCT_OUTPUT_ROW);
    }

    private Locator outputProductRow(String resourceName) {
        Locator rows = outputProductRows();
        Locator byName = rows.filter(new Locator.FilterOptions().setHasText(resourceName));
        if (byName.count() > 0) {
            return byName.first();
        }
        if (rows.count() == 1) {
            return rows.first();
        }
        throw new IllegalStateException(
                "Output product row not found for «" + resourceName + "» (rows=" + rows.count() + ")");
    }

    private void fillOutputProductAtLastRow(String resourceName, String amount) {
        String trimmed = resourceName.trim();
        log.info("Global plan wizard — output row: {} x {}", trimmed, amount);
        Locator row = outputProductRows().last();
        Locator combo = row.getByPlaceholder(RESOURCE_COMBO_PLACEHOLDER);
        combo.click();
        combo.fill(trimmed);

        Locator option = page.locator("[data-slot='combobox-item']")
                .filter(new Locator.FilterOptions().setHasText(trimmed))
                .first();
        option.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        option.click();

        row.locator("input[type='number']")
                .or(row.getByPlaceholder(AMOUNT_PLACEHOLDER))
                .fill(amount);
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
        return page.getByText(TAB_3_SEMI_FINISHED).isVisible()
                || page.getByText(TAB_3_RAW_MATERIALS).isVisible();
    }

    /**
     * Reads the «Потрібно» column value for a resource row on Tab 3.
     *
     * @return numeric amount as string (without unit), or empty if row not found
     */
    @Step("Tab 3: прочитати потребу «{resourceName}» ({section})")
    public String getRequirementAmount(String resourceName, RequirementSection section) {
        log.info("Global plan wizard — requirement {} in {}", resourceName, section);
        Locator row = requirementsTableRow(section, resourceName);
        if (row.count() == 0) {
            return "";
        }
        Locator requiredCell = row.locator("td").nth(1);
        String text = requiredCell.innerText().trim();
        Matcher matcher = AMOUNT_IN_CELL.matcher(text);
        return matcher.find() ? matcher.group(1).replace(',', '.') : text;
    }

    @Step("Tab 3: перевірити потребу «{resourceName}» = {expectedAmount}")
    public GlobalPlanWizardPage verifyRequirementAmount(
            String resourceName,
            String expectedAmount,
            RequirementSection section) {
        String actual = getRequirementAmount(resourceName, section);
        if (!actual.contains(expectedAmount)) {
            throw new AssertionError(String.format(
                    "Очікувалась потреба «%s» = %s у секції %s, фактично: «%s»",
                    resourceName, expectedAmount, section.heading(), actual));
        }
        return this;
    }

    private Locator requirementsTableRow(RequirementSection section, String resourceName) {
        Locator sectionRoot = page.locator("section")
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(section.heading()))));
        return sectionRoot.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(resourceName));
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
        return decompositionRowInSection(page.locator("body"), resourceName);
    }

    private Locator decompositionLevelSection(int levelNumber) {
        return page.locator("div.rounded-lg.border.border-gray-200.bg-white")
                .filter(new Locator.FilterOptions().setHasText("Рівень " + levelNumber));
    }

    private Locator decompositionRowInSection(Locator section, String resourceName) {
        return section.locator("div.flex.flex-col.gap-2.px-4")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("div.font-medium.text-gray-900")
                                .filter(new Locator.FilterOptions().setHasText(resourceName))));
    }

    private void expandDecompositionLevelIfClosed(int levelNumber) {
        Locator closed = page.locator("[data-state='closed']")
                .filter(new Locator.FilterOptions().setHasText("Рівень " + levelNumber))
                .first();
        if (closed.count() > 0 && closed.isVisible()) {
            closed.click();
            page.waitForTimeout(200);
        }
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
        return assignmentDialog().locator("div.flex.flex-col.sm\\:flex-row.gap-3");
    }

    private void waitForWizardLoadingFinished() {
        Locator loading = page.getByText(WIZARD_LOADING_TEXT);
        if (loading.count() > 0 && loading.first().isVisible()) {
            loading.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(uiTimeoutMs()));
        }
    }

    private Locator periodField(String label) {
        return page.locator(PERIOD_FIELDS_ROW)
                .locator("div.space-y-2")
                .filter(new Locator.FilterOptions().setHas(
                        page.getByText(label, new Page.GetByTextOptions().setExact(true))));
    }

    private void selectPeriodField(String label, String optionText) {
        periodField(label).getByRole(AriaRole.COMBOBOX).click();
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
