package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.enums.BusinessRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.UserFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.AccessFixture;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.*;
import com.erp.pages.*;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Production Planning")
@Feature("Tech-map hints — global-plan update permission")
public class GlobalPlanTechMapHintsUiTest extends BaseUITest {
    private GlobalPlanFixture plans;
    private TechnologicalMapFixture maps;
    private UserFixture users;
    private UserFixture.BusinessActor actor;
    private Long storageId;
    private StorageTechnologicalMapMode originalMode;
    private final List<Long> globalIds = new ArrayList<>();
    private final Set<Long> locationIds = new LinkedHashSet<>();
    private final Set<Long> resourceIds = new LinkedHashSet<>();
    private final Map<String, String> mapGroups = new LinkedHashMap<>();
    private final Map<Long, List<Long>> currentLocationPlans = new HashMap<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        plans = new GlobalPlanFixture(testContext, apiExecutor);
        maps = new TechnologicalMapFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        plans.getResourceFixture().fetchSharedUnit(1);
        plans.getResourceFixture().fetchSharedResourceCategory();
        storageId = ConfigProvider.getOwner1StorageId();
        var response = apiExecutor.execute(ApiEndpointDefinition.TECH_MAP_MODE_GET,
                UserRole.ADMIN, storageId.toString());
        assertThat(response.statusCode()).isEqualTo(200);
        originalMode = response.as(StorageTechnologicalMapModeResponse.class).getMode();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupHintArtifacts() {
        List<Throwable> errors = new ArrayList<>();
        if (plans != null) {
            // Deleting GP first releases the guard on its tech-map versions.
            for (Long id : globalIds) attempt(errors, () -> plans.deleteGlobalPlan(id));
            for (Long id : locationIds) attempt(errors, () -> plans.deleteLocationPlan(id));
        }
        if (maps != null) {
            // Resolve UI-created versions even if an assertion failed before their ID was recorded.
            // Exact group lineage and owned output IDs prove ownership independently of names.
            for (var entry : mapGroups.entrySet()) attempt(errors, () -> {
                for (var active : maps.getActiveTechMapsByName(storageId, UserRole.ADMIN, entry.getKey())) {
                    if (Objects.equals(active.getGroupId(), entry.getValue())
                            && resourceIds.contains(active.getOutput().getFirst().getResource().getId())) {
                        assertThat(maps.deactivateTechMap(UserRole.ADMIN, active.getId(), storageId).statusCode())
                                .isEqualTo(200);
                    }
                }
            });
            for (Long id : resourceIds) attempt(errors, () -> assertThat(
                    plans.getResourceFixture().deactivate(UserRole.ADMIN, id).statusCode()).isEqualTo(200));
            if (originalMode != null) attempt(errors, () -> maps.setMode(storageId, originalMode));
        }
        if (actor != null) {
            attempt(errors, () -> apiExecutor.restoreDefaultSessionForRole(UserRole.DYNAMIC_LOCATION_OWNER));
            attempt(errors, () -> users.deactivateUser(UserRole.ADMIN, actor.userId()));
            actor = null;
        }
        if (users != null) users.deactivateTrackedUsers();
        globalIds.clear(); locationIds.clear(); resourceIds.clear();
        mapGroups.clear(); currentLocationPlans.clear();
        assertThat(errors).as("Cleanup failures").isEmpty();
    }

    @Test @TestCaseId("TC-GP-HINT-UI-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN з підтвердженим global-plan::update бачить warning і confirmation. Скасування не змінює id/version/input або snapshot. UI надсилає повний PUT лише після підтвердження.")
    public void updaterSeesWarningAndCanCancelModal() {
        var context = arrange("Hint-admin", true);
        login(UserRole.ADMIN, true);
        var form = open(context.map());
        form.waitForLiveGlobalPlanWarning(context.plan().getDescription());
        assertThat(form.getGlobalPlanLinkHref(context.plan().getId()))
                .isEqualTo("/global-plans/" + context.plan().getId());
        form.fillInputAmount(0, amount(context.map())).requestStructuralUpdateConfirmation();
        form.attachScreenshot("TC-GP-HINT-UI-001 — confirmation");
        assertThat(form.getReassignConfirmationTitle()).isEqualTo("Створити нову версію техкарти?");
        assertThat(form.getReassignConfirmationText()).contains(context.plan().getDescription(),
                "призначте ресурс знову", "повторно", "згенеруйте");
        form.dismissReassignConfirmation();
        assertThat(form.isReassignConfirmationVisible()).isFalse();
        var unchanged = maps.getById(UserRole.ADMIN, context.map().getId(), storageId);
        assertThat(unchanged.getVersion()).isEqualTo(context.map().getVersion());
        assertThat(unchanged.getInput()).isEqualTo(context.map().getInput());
        assertSnapshot(context.plan(), context.map().getId());
    }

    @Test @TestCaseId("TC-GP-HINT-UI-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 може редагувати техкарти на власній локації, але /users/me не містить global-plan::update. Warning про live GP відсутній навіть за прямим URL update.")
    public void nonUpdaterDoesNotSeeEditWarning() {
        var context = arrange("Hint-owner-warning", true);
        login(UserRole.OWNER_1, false);
        var form = open(context.map());
        form.attachScreenshot("TC-GP-HINT-UI-002 — owner edit form");
        assertThat(form.isLiveGlobalPlanWarningVisible()).isFalse();
        assertThat(form.isReassignConfirmationVisible()).isFalse();
    }

    @Test @TestCaseId("TC-GP-HINT-UI-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 без global-plan::update змінює input amount повним UI PUT без confirmation. Нова версія активна; GP лишається на попередній версії. Відомий ризик: стара реалізація відкриває модалку всім редакторам.")
    public void nonUpdaterSavesStructuralEditWithoutModal() {
        var context = arrange("Hint-owner-save", true);
        login(UserRole.OWNER_1, false);
        var form = open(context.map()).fillInputAmount(0, amount(context.map()));
        form.saveAndWaitForConfirmationOrUpdate();
        form.attachScreenshot("TC-GP-HINT-UI-003 — save outcome");
        assertThat(form.isReassignConfirmationVisible()).as("Modal requires global-plan::update").isFalse();
        var updated = maps.getActiveTechMapsByName(storageId, UserRole.ADMIN, context.map().getName())
                .stream().max(Comparator.comparing(TechnologicalMapResponse::getVersion)).orElseThrow();
        assertThat(updated.getId()).isNotEqualTo(context.map().getId());
        assertThat(updated.getVersion()).isEqualTo(context.map().getVersion() + 1);
        assertThat(updated.getInput().getFirst().getAmount())
                .isEqualTo(context.map().getInput().getFirst().getAmount() + 1);
        assertSnapshot(context.plan(), context.map().getId());
    }

    @Test @TestCaseId("TC-GP-HINT-UI-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Arrange generate M1; structural PUT M1→M2; ADMIN reopens UI. Виробничі плани мають помаранчевий warning-бейдж ! замість числа; перевіряються RGB та помаранчевий відтінок 20–50°. Вимогу узгоджено з фактичною реалізацією за вказівкою користувача.")
    public void updaterSeesOrangeExclamationInsteadOfCount() {
        var context = arrange("Hint-badge", true);
        mutate(context.map());
        login(UserRole.ADMIN, true);
        new GlobalPlansPage(page).open();
        var hints = new GlobalPlanHintsPage(page);
        hints.productionPlansEntry().scrollIntoViewIfNeeded();
        hints.attachScreenshot("TC-GP-HINT-UI-004 — production plans badge");
        hints.waitForExclamation();
        assertThat(hints.exclamationBadge().count()).isEqualTo(1);
        assertThat(hints.productionPlansEntry().innerText()).doesNotContainPattern("\\d");
        assertOrange(hints.badgeBackground());
    }

    @Test @TestCaseId("TC-GP-HINT-UI-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Та сама invalid decomposition; OWNER_1 без global-plan::update не бачить ! у Виробничих планах. Форма техкарти і sidebar завантажені справжньою сесією.")
    public void nonUpdaterDoesNotSeeExclamation() {
        var context = arrange("Hint-owner-badge", true);
        var updated = mutate(context.map());
        login(UserRole.OWNER_1, false);
        open(updated);
        new AppSidebarPage(page).waitForSidebarLoaded();
        var hints = new GlobalPlanHintsPage(page);
        assertThat(hints.productionPlansEntry().isVisible()).isTrue();
        hints.attachScreenshot("TC-GP-HINT-UI-005 — no owner badge");
        assertThat(hints.exclamationBadge().count()).isZero();
        assertThat(hints.hintTexts()).isEmpty();
    }

    @Test @TestCaseId("TC-GP-HINT-UI-006")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Два generated future GP з різними техкартами; structural PUT обох; третя незмінена карта. На /global-plans підказка має заголовок «Розподіл у глобальних планах … став недійсним для техкарт:», посилання з описами планів, список назв карт та окрему інструкцію відновлення. Вимогу узгоджено з фактичною реалізацією за вказівкою користувача.")
    public void globalPlansHintListsAffectedPlansAndMaps() {
        var first = arrange("Hint-first", true);
        var second = arrange("Hint-second", true);
        var sibling = arrange("Hint-unchanged", true);
        mutate(first.map()); mutate(second.map());
        login(UserRole.ADMIN, true);
        new GlobalPlansPage(page).open();
        var hints = new GlobalPlanHintsPage(page);
        hints.attachScreenshot("TC-GP-HINT-UI-006 — invalid distribution hints");
        hints.waitForHint(first.plan().getDescription());
        hints.expandTechMapList();
        hints.attachScreenshot("TC-GP-HINT-UI-006 — expanded tech-map list");
        String combined = String.join(" ", hints.hintTexts());
        assertThat(hints.hintPlanLabels()).contains(label(first.plan()), label(second.plan()))
                .doesNotContain(label(sibling.plan()));
        assertThat(hints.hintHeaderText()).isEqualTo("Розподіл у глобальних планах "
                + String.join(", ", hints.hintPlanLabels()) + " став недійсним для техкарт:");
        assertThat(hints.hintTechMapNames()).contains(first.map().getName(), second.map().getName())
                .doesNotContain(sibling.map().getName());
        assertThat(hints.isRecoveryInstructionVisible()).isTrue();
        assertThat(combined)
                .doesNotContain(sibling.map().getName(), sibling.plan().getDescription(), "${", "undefined", "null");
        assertYellow(hints.hintBackground(first.plan().getDescription()));
        assertSnapshot(sibling.plan(), sibling.map().getId());
    }

    @Test @TestCaseId("TC-GP-HINT-UI-007")
    @Severity(SeverityLevel.NORMAL)
    @Description("Name-only PUT generated M1 зберігає id/version. Після reload /global-plans warning не містить цей план або нову назву. Сторонні stale плани dev не заважають перевірці.")
    public void renameDoesNotInvalidateDistribution() {
        var context = arrange("Hint-rename", true);
        String renamed = context.map().getName() + " renamed";
        var response = maps.updateTechMap(UserRole.ADMIN, context.map().getId(),
                TechnologicalMapDataFactory.withRenamed(context.map(), renamed));
        assertThat(response.statusCode()).isEqualTo(200);
        var updated = response.as(TechnologicalMapResponse.class);
        assertThat(updated.getId()).isEqualTo(context.map().getId());
        assertThat(updated.getVersion()).isEqualTo(context.map().getVersion());
        mapGroups.remove(context.map().getName());
        mapGroups.put(renamed, context.map().getGroupId());
        login(UserRole.ADMIN, true);
        new GlobalPlansPage(page).open();
        var hints = new GlobalPlanHintsPage(page);
        hints.attachScreenshot("TC-GP-HINT-UI-007 — renamed map does not invalidate plan");
        assertThat(String.join(" ", hints.hintTexts())).doesNotContain(context.plan().getDescription(), renamed);
        assertSnapshot(context.plan(), context.map().getId());
    }

    @Test @TestCaseId("TC-GP-HINT-UI-008")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Два незалежні invalid GP. Reassign M2 повним block0 assignments і generate першого; reload прибирає лише перший hint, другий лишається. Після regenerate другого обидва зникли. Відкриття плану саме по собі не є виправленням.")
    public void regenerationClearsOnlyRepairedPlanHint() {
        var first = arrange("Hint-repair-first", true);
        var second = arrange("Hint-repair-second", true);
        var firstNew = mutate(first.map()); var secondNew = mutate(second.map());
        login(UserRole.ADMIN, true);
        new GlobalPlansPage(page).open();
        var hints = new GlobalPlanHintsPage(page);
        hints.attachScreenshot("TC-GP-HINT-UI-008 — before recovery");
        hints.waitForHint(first.plan().getDescription());
        generate(first.plan(), firstNew);
        new GlobalPlansPage(page).open();
        hints.waitForHint(second.plan().getDescription());
        assertThat(String.join(" ", hints.hintTexts())).doesNotContain(first.plan().getDescription());
        assertSnapshot(first.plan(), firstNew.getId());
        assertSnapshot(second.plan(), second.map().getId());
        generate(second.plan(), secondNew);
        new GlobalPlansPage(page).open();
        hints.attachScreenshot("TC-GP-HINT-UI-008 — after recovery");
        assertThat(String.join(" ", hints.hintTexts())).doesNotContain(
                first.plan().getDescription(), second.plan().getDescription());
        assertSnapshot(second.plan(), secondNew.getId());
    }

    @Test @TestCaseId("TC-GP-HINT-UI-009")
    @Severity(SeverityLevel.NORMAL)
    @Description("Невикористана карта: у ADMIN немає warning або confirmation; structural UI PUT одразу створює version+1, без жодного GP snapshot.")
    public void unusedMapSavesWithoutWarningOrModal() {
        var context = arrange("Hint-unused", false);
        login(UserRole.ADMIN, true);
        var form = open(context.map());
        assertThat(form.isLiveGlobalPlanWarningVisible()).isFalse();
        form.fillInputAmount(0, amount(context.map())).saveAndWaitForConfirmationOrUpdate();
        form.attachScreenshot("TC-GP-HINT-UI-009 — unused map saved");
        assertThat(form.isReassignConfirmationVisible()).isFalse();
        var updated = maps.getActiveTechMapsByName(storageId, UserRole.ADMIN, context.map().getName()).getFirst();
        assertThat(updated.getVersion()).isEqualTo(context.map().getVersion() + 1);
        assertThat(updated.getId()).isNotEqualTo(context.map().getId());
    }

    @Test @TestCaseId("TC-GP-HINT-UI-010")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Ізольований non-admin керівник локації з global-plan.read/manage grants бачить warning/modal; manage розгортається у global-plan::update. Після відкликання manage та нового входу warning/modal зникають; structural UI save лишається доступним. isAdmin=false підтверджено через /users/me.")
    public void granularPermissionControlsHintsIndependentlyOfAdminRole() {
        var context = arrange("Hint-granular", true);
        var storage = new StorageFixture(testContext, apiExecutor).getById(UserRole.ADMIN, storageId);
        actor = users.createBusinessActor(getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER,
                List.of(storage), List.of("global-plan.read", "global-plan.manage"));
        apiExecutor.setSessionForRole(UserRole.DYNAMIC_LOCATION_OWNER, actor.username(), actor.password());
        loginActor(true);
        var form = open(context.map()).waitForLiveGlobalPlanWarning(context.plan().getDescription());
        form.fillInputAmount(0, amount(context.map())).requestStructuralUpdateConfirmation();
        form.attachScreenshot("TC-GP-HINT-UI-010 — non-admin granted update");
        assertThat(form.isReassignConfirmationVisible()).isTrue();
        form.dismissReassignConfirmation();

        var access = new AccessFixture(testContext, apiExecutor);
        var updateGrants = access.grants(actor.userId(), false).stream()
                .filter(grant -> "global-plan.manage".equals(grant.getPermissionKey())).toList();
        assertThat(updateGrants).hasSize(1);
        access.revoke(updateGrants.getFirst().getId(), "Autotest: revoke own actor global plan update");
        apiExecutor.evictSessionForRole(UserRole.DYNAMIC_LOCATION_OWNER);
        loginActor(false);
        form = open(context.map());
        assertThat(form.isLiveGlobalPlanWarningVisible()).isFalse();
        form.fillInputAmount(0, amount(context.map())).saveAndWaitForConfirmationOrUpdate();
        form.attachScreenshot("TC-GP-HINT-UI-010 — update revoked, direct save");
        assertThat(form.isReassignConfirmationVisible()).isFalse();
        var active = maps.getActiveTechMapsByName(storageId, UserRole.ADMIN, context.map().getName());
        assertThat(active).hasSize(1);
        assertThat(active.getFirst().getVersion()).isEqualTo(context.map().getVersion() + 1);
        assertSnapshot(context.plan(), context.map().getId());
    }

    private void loginActor(boolean canUpdate) {
        var response = apiExecutor.execute(ApiEndpointDefinition.USER_GET_ME, UserRole.DYNAMIC_LOCATION_OWNER);
        assertThat(response.statusCode()).isEqualTo(200);
        var me = response.as(UserMeResponse.class);
        assertThat(me.getIsAdmin()).isFalse();
        assertThat(me.getPermissions().contains("global-plan::update")).isEqualTo(canUpdate);
        browserContext.clearCookies();
        injectSessionCookies(authService.getSessionForUser(actor.username(), actor.password()), sessionCookieDomain());
        injectWorkspaceView(actor.username(), storageId);
    }

    private void login(UserRole role, boolean canUpdate) {
        var response = apiExecutor.execute(ApiEndpointDefinition.USER_GET_ME, role);
        assertThat(response.statusCode()).isEqualTo(200);
        var me = response.as(UserMeResponse.class);
        assertThat(me.getPermissions().contains("global-plan::update"))
                .as("Actual global-plan::update for %s", role).isEqualTo(canUpdate);
        browserContext.clearCookies();
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        injectWorkspaceView(role, role == UserRole.ADMIN ? "all" : storageId);
    }

    private TechnologicalMapFormPage open(TechnologicalMapResponse map) {
        var form = new TechnologicalMapFormPage(page).openUpdate(map.getId(), storageId);
        // Resolve async live references before negative assertions as well.
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        return form;
    }

    private Context arrange(String prefix, boolean withPlan) {
        maps.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        Long input1 = createResource(prefix + "-input1");
        Long input2 = createResource(prefix + "-input2");
        Long product = createResource(prefix + "-output");
        var map = maps.createTechMapWithRequest(UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(prefix,
                        List.of(new ResourceUsageRequest(input1, 2.0), new ResourceUsageRequest(input2, 1.0)),
                        List.of(new ResourceUsageRequest(product, 1.0)), Set.of(storageId)).build());
        mapGroups.put(map.getName(), map.getGroupId());
        if (!withPlan) return new Context(null, map);
        YearMonth period = plans.nextUniquePeriod();
        var request = GlobalPlanDataFactory.createPlan(period.getMonthValue(), period.getYear(),
                product, 10).build();
        var response = apiExecutor.execute(ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE, UserRole.ADMIN, request);
        assertThat(response.statusCode()).isEqualTo(200);
        var plan = response.as(GlobalPlanResponse.class);
        globalIds.add(plan.getId());
        generate(plan, map);
        return new Context(plans.getById(plan.getId()), map);
    }

    private Long createResource(String prefix) {
        Long id = plans.getResourceFixture().createUniqueResource(prefix).getId();
        resourceIds.add(id);
        return id;
    }

    private void generate(GlobalPlanResponse plan, TechnologicalMapResponse map) {
        var request = DecompositionRequest.builder().blocks(List.of(GlobalPlanDataFactory.block(
                GlobalPlanDataFactory.item(map.getOutput().getFirst().getResource().getId(),
                        GlobalPlanDataFactory.assignment(storageId, map.getId(), "10"))))).build();
        plans.decompose(plan.getId(), request);
        var generated = plans.generate(plan.getId(), request);
        locationIds.removeAll(currentLocationPlans.getOrDefault(plan.getId(), List.of()));
        var latestIds = new ArrayList<Long>();
        for (var item : generated.getPlans()) {
            // Replaced plans no longer exist; only clean up the current plan for this period.
            locationIds.add(item.getPlan().getId());
            latestIds.add(item.getPlan().getId());
        }
        currentLocationPlans.put(plan.getId(), latestIds);
        assertSnapshot(plan, map.getId());
    }

    private TechnologicalMapResponse mutate(TechnologicalMapResponse map) {
        var response = maps.updateTechMap(UserRole.ADMIN, map.getId(),
                TechnologicalMapDataFactory.withFirstInputAmount(map, map.getInput().getFirst().getAmount() + 1));
        assertThat(response.statusCode()).isEqualTo(200);
        var updated = response.as(TechnologicalMapResponse.class);
        assertThat(updated.getVersion()).isEqualTo(map.getVersion() + 1);
        return maps.getById(UserRole.ADMIN, updated.getId(), storageId);
    }

    private void assertSnapshot(GlobalPlanResponse plan, Long mapId) {
        assertThat(GlobalPlanFixture.snapshotTechMapIds(plans.getById(plan.getId()))).containsExactly(mapId);
    }

    private static String amount(TechnologicalMapResponse map) {
        return String.valueOf(map.getInput().getFirst().getAmount() + 1);
    }

    private static String label(GlobalPlanResponse plan) {
        var months = List.of("Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
                "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень");
        String period = months.get(plan.getMonth() - 1) + " " + plan.getYear();
        String description = plan.getDescription() == null ? "" : plan.getDescription().trim();
        return description.isEmpty() ? period : description;
    }

    private static void assertOrange(String cssColor) {
        var rgb = rgb(cssColor);
        assertThat(rgb[0]).isGreaterThan(rgb[1] + 30).isGreaterThan(rgb[2] + 30);
        double hue = 60.0 * (rgb[1] - rgb[2]) / (rgb[0] - Math.min(rgb[1], rgb[2]));
        assertThat(hue).as("Orange warning badge hue, actual %s", cssColor).isBetween(20.0, 50.0);
    }

    private static void assertYellow(String cssColor) {
        var rgb = rgb(cssColor);
        assertThat(rgb[0]).isGreaterThanOrEqualTo(240);
        assertThat(rgb[1]).isGreaterThanOrEqualTo(220);
        assertThat(rgb[2]).isLessThan(rgb[0]);
    }

    private static int[] rgb(String cssColor) {
        var matcher = java.util.regex.Pattern.compile("[\\d.]+").matcher(cssColor);
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            assertThat(matcher.find()).as("RGB CSS color: %s", cssColor).isTrue();
            result[i] = (int) Double.parseDouble(matcher.group());
        }
        return result;
    }

    private static void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (Exception | AssertionError error) { errors.add(error); }
    }

    private record Context(GlobalPlanResponse plan, TechnologicalMapResponse map) { }
}
