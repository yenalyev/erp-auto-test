package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.enums.StorageKind;
import com.erp.enums.MilUnitType;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.fixtures.AccessFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.access.GrantScopeKind;
import com.erp.models.request.InventoryRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.InventoryProcessResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AccessForbiddenPage;
import com.erp.pages.InventoryEditPage;
import com.erp.pages.UnitManagementPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Both location roles on the same fresh user; no legacy region or global grants. */
@Epic("Inventory")
@Feature("REQ-CREW-INVENTORY — combined roles and location hierarchy")
public class CrewInventoryAccessUiTest extends BaseUITest {
    private static final UserRole ACTOR_ROLE = UserRole.CREW_WRITE;
    private static final List<String> ROLES = List.of(
            UserFixture.CREW_WRITE_ROLE_NAME, UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME);
    private static final String COMMENT = "Перевірка прав інвентаризації";

    private StorageFixture storages;
    private ResourceFixture resources;
    private InventoryFixture inventory;
    private UserFixture users;
    private AccessFixture access;
    private final List<Long> scenarioStorageIds = new ArrayList<>();
    private final List<Long> scenarioResourceIds = new ArrayList<>();

    private record Scenario(StorageResponse root, StorageResponse target, StorageResponse control,
                            ResourceResponse changed, ResourceResponse unchanged,
                            UserFixture.BusinessActor actor, long processId) { }

    private enum HeadScope {
        ROOT_ONLY(GrantScopeKind.LOCATION, null),
        LOCATIONS(GrantScopeKind.SUBTREE, List.of(StorageKind.LOCATION)),
        LOCATIONS_CREWS(GrantScopeKind.SUBTREE, List.of(StorageKind.LOCATION, StorageKind.CREW)),
        LOCATIONS_FLY_POINTS(GrantScopeKind.SUBTREE, List.of(StorageKind.LOCATION, StorageKind.FLY_POINT)),
        ALL_KINDS(GrantScopeKind.SUBTREE, null);

        final GrantScopeKind scope;
        final List<StorageKind> kinds;

        HeadScope(GrantScopeKind scope, List<StorageKind> kinds) {
            this.scope = scope;
            this.kinds = kinds;
        }

        boolean covers(StorageKind kind) {
            return scope == GrantScopeKind.SUBTREE && (kinds == null || kinds.contains(kind));
        }
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storages = new StorageFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        inventory = new InventoryFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        access = new AccessFixture(testContext, apiExecutor);
        resources.fetchSharedUnit(3);
        resources.fetchSharedResourceCategory();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupScenario() {
        apiExecutor.restoreDefaultSessionForRole(ACTOR_ROLE);
        List<Long> ownedLocations = List.copyOf(scenarioStorageIds);
        try {
            if (inventory != null) {
                for (long id : scenarioStorageIds) {
                    inventory.ensureClosed(id);
                    // Zero rows still count as resource usage; remove them before archiving.
                    for (long resourceId : scenarioResourceIds) {
                        inventory.removeResourceFromStorage(id, resourceId, UserRole.ADMIN);
                    }
                }
            }
            scenarioStorageIds.clear();
        } finally {
            try {
                if (users != null) {
                    users.deactivateTrackedUsers();
                }
            } finally {
                if (storages != null) {
                    for (long id : ownedLocations.reversed()) {
                        assertThat(storages.archiveStorage(UserRole.ADMIN, id))
                                .as("Archive owned inventory location %s", id).isTrue();
                    }
                    storages.deactivateTrackedStorages(UserRole.ADMIN);
                }
                SoftAssertions cleanup = new SoftAssertions();
                for (long id : List.copyOf(scenarioResourceIds)) {
                    var response = resources.deactivate(UserRole.ADMIN, id);
                    cleanup.assertThat(response.statusCode())
                            .as("Deactivate owned resource %s: %s", id, response.asString()).isEqualTo(200);
                    if (response.statusCode() == 200) {
                        scenarioResourceIds.remove(id);
                    }
                }
                cleanup.assertAll();
            }
        }
    }

    @AfterClass(alwaysRun = true)
    public void retryOwnedArtifactCleanup() {
        cleanupScenario();
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-001")
    @Description("Both direct location roles must allow API conduct and UI form submission on a flight point. "
            + "A redirect away from inventory remains a regression failure.")
    public void flyPointWithDirectCombinedRoles() {
        conduct(prepare(false, false, false));
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-002")
    @Description("Both direct location roles must allow API conduct and UI form submission on a crew. "
            + "A redirect away from inventory remains a regression failure.")
    public void crewWithDirectCombinedRoles() {
        conduct(prepare(true, false, false));
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-003")
    public void flyPointWithInheritedCombinedRoles() {
        conduct(prepare(false, true, false));
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-004")
    public void crewWithInheritedCombinedRoles() {
        conduct(prepare(true, true, false));
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-005")
    public void flyPointDeniedWhenBothRolesExcludeDescendants() {
        deny(prepare(false, true, true));
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-006")
    public void crewDeniedWhenBothRolesExcludeDescendants() {
        deny(prepare(true, true, true));
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-007")
    @Description("InventoryEditPage sends resources with resourceId/amount/version, comment and knownResourceIds. "
            + "Both roles are assigned directly to an ordinary LOCATION without descendants; "
            + "API and UI must conduct OPEN inventory without HTTP 403.")
    public void ordinaryLocationWithDirectCombinedRoles() {
        conduct(prepare("LOCATION", false, false));
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-008")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithLocalKeeperAndLocalHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.LOCATION, HeadScope.ROOT_ONLY);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-009")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithLocalKeeperAndLocationsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.LOCATION, HeadScope.LOCATIONS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-010")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithLocalKeeperAndCrewsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.LOCATION, HeadScope.LOCATIONS_CREWS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-011")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithLocalKeeperAndFlyPointsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.LOCATION, HeadScope.LOCATIONS_FLY_POINTS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-012")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithLocalKeeperAndAllKindsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.LOCATION, HeadScope.ALL_KINDS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-013")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithInheritedKeeperAndLocalHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.SUBTREE, HeadScope.ROOT_ONLY);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-014")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithInheritedKeeperAndLocationsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.SUBTREE, HeadScope.LOCATIONS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-015")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithInheritedKeeperAndCrewsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.SUBTREE, HeadScope.LOCATIONS_CREWS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-016")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithInheritedKeeperAndFlyPointsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.SUBTREE, HeadScope.LOCATIONS_FLY_POINTS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-017")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void flyPointWithInheritedKeeperAndAllKindsHead() {
        exerciseScopedRoles(StorageKind.FLY_POINT, GrantScopeKind.SUBTREE, HeadScope.ALL_KINDS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-018")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void crewWithLocalKeeperAndCrewsHead() {
        exerciseScopedRoles(StorageKind.CREW, GrantScopeKind.LOCATION, HeadScope.LOCATIONS_CREWS);
    }

    @Test
    @TestCaseId("TC-CREW-INV-ACCESS-019")
    @Description("Keeper role is assigned separately to battalion and warehouse; head scope is varied. "
            + "Client inventory PUT uses resources with resourceId/amount/version, comment and knownResourceIds.")
    public void crewWithLocalKeeperAndFlyPointsHead() {
        exerciseScopedRoles(StorageKind.CREW, GrantScopeKind.LOCATION, HeadScope.LOCATIONS_FLY_POINTS);
    }

    private void exerciseScopedRoles(StorageKind kind, GrantScopeKind keeperScope, HeadScope headScope) {
        boolean allowed = keeperScope == GrantScopeKind.SUBTREE || headScope.covers(kind);
        Scenario scenario = prepare(kind.name(), true, !allowed, keeperScope, headScope);
        if (allowed) {
            conduct(scenario);
        } else {
            deny(scenario);
        }
    }

    @Step("Prepare isolated inventory, fresh combined-role actor and exact grant scope")
    private Scenario prepare(boolean crew, boolean hierarchy, boolean denied) {
        return prepare(crew ? "CREW" : "FLY_POINT", hierarchy, denied);
    }

    private Scenario prepare(String kind, boolean hierarchy, boolean denied) {
        return prepare(kind, hierarchy, denied, null, null);
    }

    private Scenario prepare(String kind, boolean hierarchy, boolean denied,
                             GrantScopeKind keeperScope, HeadScope headScope) {
        boolean matrix = headScope != null;
        StorageResponse root = matrix
                ? storages.createStorage(StorageDataFactory.unitStorage(storages.resolveParentUnit().getId(),
                        "inv-access-battalion-").milUnitType(MilUnitType.BATALION).build())
                : storages.createUnitStorage(storages.resolveParentUnit().getId(), "inv-access-root-");
        scenarioStorageIds.add(root.getId());
        StorageResponse parent = hierarchy
                ? matrix ? storages.createChildStorage(root.getId(), "inv-access-warehouse-")
                    : storages.createUnitStorage(root.getId(), "inv-access-child-") : root;
        if (hierarchy) {
            scenarioStorageIds.add(parent.getId());
        }
        StorageResponse target = switch (kind) {
            case "LOCATION" -> storages.createUnitStorage(parent.getId(), "inv-access-location-");
            case "CREW" -> storages.createCrewStorage(parent.getId(), "inv-access-crew-");
            default -> storages.createFlyPointStorage(parent.getId(), "inv-access-fly-");
        };
        scenarioStorageIds.add(target.getId());
        StorageResponse control = storages.createChildStorage(root.getId(), "inv-access-control-");
        scenarioStorageIds.add(control.getId());
        ResourceResponse changed = resources.createUniqueResource("inv-access-R-");
        scenarioResourceIds.add(changed.getId());
        ResourceResponse unchanged = resources.createUniqueResource("inv-access-S-");
        scenarioResourceIds.add(unchanged.getId());
        seed(target.getId(), Map.of(changed.getId(), 10.0, unchanged.getId(), 4.0));
        seed(control.getId(), Map.of(changed.getId(), 7.0));

        StorageResponse grantLocation = hierarchy ? root : target;
        UserFixture.BusinessActor actor = users.createBusinessActor(getPlaywrightSessionProvider(),
                BusinessRole.CREW_INVENTORY_OPERATOR, List.of(grantLocation));
        GrantScopeKind scope = hierarchy && !denied ? GrantScopeKind.SUBTREE : GrantScopeKind.LOCATION;
        if (matrix) {
            configureScopedRoles(actor, root, parent, keeperScope, headScope);
        } else if (scope == GrantScopeKind.SUBTREE) {
            access.replaceGrants(actor.userId(), ROLES, List.of(), scope, grantLocation.getId());
        }
        var grants = access.grants(actor.userId(), false);
        assertThat(grants).as("Only the requested role assignments exist").hasSize(matrix ? 3 : 2);
        assertThat(grants.stream().map(g -> g.getRole().getName()).distinct().toList())
                .containsExactlyInAnyOrderElementsOf(ROLES);
        assertThat(grants).allSatisfy(g -> {
            if (!matrix) {
                assertThat(g.getScopeKind()).isEqualTo(scope);
                assertThat(g.getStorage().getId()).isEqualTo(grantLocation.getId());
            }
            assertThat(g.getPermissionKey()).isNull();
        });
        assertThat(access.hasEffectivePermission(actor.userId(), "inventory.adjust", target.getId()))
                .as("Effective inventory.adjust follows the selected grant scope").isEqualTo(!denied);

        InventoryProcessResponse requested = inventory.requestInventory(
                target.getId(), UserRole.ADMIN, "Перевірка комбінованих ролей");
        inventory.approveInventory(target.getId(), UserRole.ADMIN, "Погоджено для автотесту");
        apiExecutor.setSessionForRole(ACTOR_ROLE, actor.username(), actor.password());
        browserContext.clearCookies();
        injectSessionCookies(authService.getSessionForUser(actor.username(), actor.password()), sessionCookieDomain());
        // CREW/FLY_POINT are selected by the deep link, not by the workspace picker.
        injectWorkspaceView(actor.username(), matrix ? parent.getId() : hierarchy ? root.getId()
                : "LOCATION".equals(kind) ? target.getId() : "all");
        if (matrix) {
            Allure.parameter("workspaceStorageId", parent.getId());
        }
        Allure.parameter("targetStorageId", target.getId());
        Allure.parameter("actor", actor.username());
        if (!matrix) {
            Allure.parameter("scope", scope.name());
        }
        Allure.parameter("grantLocationId", grantLocation.getId());
        return new Scenario(root, target, control, changed, unchanged, actor, requested.getId());
    }

    @Step("Assign keeper separately to battalion and warehouse; assign head to battalion with explicit kinds")
    private void configureScopedRoles(UserFixture.BusinessActor actor, StorageResponse battalion,
                                      StorageResponse warehouse, GrantScopeKind keeperScope, HeadScope head) {
        access.revokeAll(actor.userId());
        for (StorageResponse location : List.of(battalion, warehouse)) {
            access.ensureGrants(actor.userId(), List.of(UserFixture.CREW_WRITE_ROLE_NAME), List.of(),
                    keeperScope, location.getId());
        }
        access.ensureGrants(actor.userId(), List.of(UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME), List.of(),
                head.scope, battalion.getId(), head.kinds);
        var grants = access.grants(actor.userId(), false);
        assertThat(grants).hasSize(3);
        for (StorageResponse location : List.of(battalion, warehouse)) {
            assertThat(grants.stream().filter(g -> UserFixture.CREW_WRITE_ROLE_NAME.equals(g.getRole().getName())
                    && location.getId().equals(g.getStorage().getId())).toList()).singleElement().satisfies(g -> {
                assertThat(g.getScopeKind()).isEqualTo(keeperScope);
                assertThat(g.getStorageKinds()).isNullOrEmpty();
            });
            assertThat(access.hasEffectivePermission(actor.userId(), "inventory.adjust", location.getId())).isTrue();
        }
        assertThat(grants.stream().filter(g -> UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME.equals(g.getRole().getName()))
                .toList()).singleElement().satisfies(g -> {
            assertThat(g.getStorage().getId()).isEqualTo(battalion.getId());
            assertThat(g.getScopeKind()).isEqualTo(head.scope);
            if (head.kinds == null) {
                assertThat(g.getStorageKinds()).isNullOrEmpty();
            } else {
                assertThat(g.getStorageKinds()).containsExactlyInAnyOrderElementsOf(head.kinds);
            }
        });
        // Repeating the same filtered assignment must not collapse it into an unfiltered grant.
        assertThat(access.ensureGrants(actor.userId(), List.of(UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME), List.of(),
                head.scope, battalion.getId(), head.kinds)).isEmpty();
        Allure.parameter("battalionId", battalion.getId());
        Allure.parameter("warehouseId", warehouse.getId());
        Allure.parameter("keeperScopeAtBothLocations", keeperScope.name());
        Allure.parameter("headScope", head.scope.name());
        Allure.parameter("headStorageKinds", head.kinds == null ? "all" : head.kinds.toString());
    }

    private void seed(long storageId, Map<Long, Double> amounts) {
        inventory.openSession(storageId);
        inventory.conductInventory(storageId, UserRole.ADMIN, InventoryDataFactory.seedAmounts(amounts));
    }

    @Step("Conduct through UI and verify persisted stock, attribution, history and closed-session API guard")
    private void conduct(Scenario s) {
        long id = s.target().getId();
        // Exercise the actual API permission even if the UI route subsequently rejects this actor.
        assertThat(inventory.listItems(id, ACTOR_ROLE)).hasSize(2);
        assertThat(inventory.conductInventoryRaw(id, ACTOR_ROLE, request(s, 6.0)).statusCode())
                .as("Combined-role user conducts OPEN inventory via API").isEqualTo(200);
        InventoryProcessResponse apiCompleted = verifyCompleted(s);
        assertThat(inventory.conductInventoryRaw(id, ACTOR_ROLE, request(s, 5.0)).statusCode())
                .as("Repeated API conduct is forbidden after close").isEqualTo(403);
        assertStock(s, 6.0);
        assertThat(inventory.requireInventoryProcess(s.processId(), UserRole.ADMIN))
                .usingRecursiveComparison().isEqualTo(apiCompleted);
        Allure.addAttachment("API permission confirmed", "OPEN PUT=200; R=6, S=4, K/R=7; "
                + "CLOSED; author=" + s.actor().username() + "; repeat PUT=403");

        // A separate OPEN process checks the same business action through the real frontend.
        seed(id, Map.of(s.changed().getId(), 10.0, s.unchanged().getId(), 4.0));
        InventoryProcessResponse uiRequested = inventory.requestInventory(id, UserRole.ADMIN, "UI перевірка прав");
        inventory.approveInventory(id, UserRole.ADMIN, "Погоджено UI перевірку");
        Scenario ui = new Scenario(s.root(), s.target(), s.control(), s.changed(), s.unchanged(),
                s.actor(), uiRequested.getId());
        UnitManagementPage stock = openStock(id).waitForConductButtonEnabled();
        assertThat(stock.isConductInventoryButtonEnabled()).isTrue();
        attachScreenshot("Combined roles — inventory action available");
        stock.clickConductInventory();
        InventoryEditPage form = new InventoryEditPage(page).waitForLoaded();
        assertThat(form.getResourceAmountInputValue(s.changed().getName().trim())).isEqualTo("10");
        form.updateAmountForResource(s.changed().getName().trim(), "6").fillComment(COMMENT);
        assertThat(form.isSaveEnabled()).isTrue();
        attachScreenshot("Combined roles — inventory ready to save");
        var submit = form.saveChangesRaw();
        Allure.addAttachment("UI inventory submit", "PUT " + submit.url()
                + "\nHTTP " + submit.status() + "\n" + submit.request().postData());
        assertThat(java.net.URI.create(submit.url()).getPath())
                .as("UI submits inventory for the selected location")
                .endsWith("/api/v1/storages/" + id + "/inventory");
        assertThat(submit.status()).as("Combined-role UI inventory submit must succeed without 403").isEqualTo(200);

        assertStock(s, 6.0);
        InventoryProcessResponse completed = verifyCompleted(ui);

        stock = openStock(id);
        assertThat(stock.isConductInventoryButtonEnabled()).as("Closed process cannot be conducted again").isFalse();
        attachScreenshot("Combined roles — process closed after conduct");
        var repeat = inventory.conductInventoryRaw(id, ACTOR_ROLE, request(s, 5.0));
        assertThat(repeat.statusCode()).as("Repeated PUT on CLOSED inventory").isEqualTo(403);
        assertStock(s, 6.0);
        assertThat(inventory.requireInventoryProcess(ui.processId(), UserRole.ADMIN))
                .usingRecursiveComparison().isEqualTo(completed);
    }

    private UnitManagementPage openStock(long id) {
        page.navigate(ConfigProvider.getBaseUrl() + "/inventory?storageId=" + id);
        page.waitForCondition(() -> page.getByTestId("inventory-start").isVisible()
                || page.getByTestId("inventory-request-open").isVisible()
                || page.getByText(AccessForbiddenPage.FORBIDDEN_MESSAGE).isVisible()
                || page.url().contains("/analytics/") || page.url().contains("/production"));
        assertThat(page.url()).as("Authorized inventory deep link must not redirect to an unrelated page")
                .contains("/inventory");
        assertThat(page.getByText(AccessForbiddenPage.FORBIDDEN_MESSAGE).isVisible())
                .as("Both roles permit inventory on the target").isFalse();
        return new UnitManagementPage(page).waitForLoaded();
    }

    private InventoryProcessResponse verifyCompleted(Scenario s) {
        assertStock(s, 6.0);
        InventoryProcessResponse completed = inventory.requireInventoryProcess(s.processId(), UserRole.ADMIN);
        assertThat(completed.getState()).isEqualTo("CLOSED");
        assertThat(completed.getStorage().getId()).isEqualTo(s.target().getId());
        assertThat(completed.getCompletedBy()).isEqualTo(s.actor().username());
        assertThat(completed.getCompletedComment()).isEqualTo(COMMENT);
        assertThat(completed.getDiff()).hasSize(1);
        assertThat(completed.getDiff().get(0).path("resourceId").asLong()).isEqualTo(s.changed().getId());
        assertThat(completed.getDiff().get(0).path("diffAmount").asDouble()).isEqualTo(-4.0);

        return completed;
    }

    @Step("Deny descendant inventory via API and direct editor link; keep OPEN process and stock unchanged")
    private void deny(Scenario s) {
        InventoryProcessResponse before = inventory.requireInventoryProcess(s.processId(), UserRole.ADMIN);
        var response = inventory.conductInventoryRaw(s.target().getId(), ACTOR_ROLE, request(s, 6.0));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(response.statusCode()).as("No inventory.adjust on descendant").isEqualTo(403);
        softly.assertThat(snapshot(s.target().getId()))
                .isEqualTo(Map.of(s.changed().getId(), 10.0, s.unchanged().getId(), 4.0));
        softly.assertThat(snapshot(s.control().getId())).isEqualTo(Map.of(s.changed().getId(), 7.0));
        softly.assertThat(inventory.requireInventoryProcess(s.processId(), UserRole.ADMIN))
                .usingRecursiveComparison().isEqualTo(before);
        // Complete API checks even if the UI fails to deny the route.
        softly.assertAll();

        List<Integer> targetReadStatuses = new ArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Response> captureTargetRead = r -> {
            if (r.url().matches(".*/api/v1/storages/" + s.target().getId() + "(?:\\?.*)?")
                    && "GET".equals(r.request().method())) {
                targetReadStatuses.add(r.status());
            }
        };
        page.onResponse(captureTargetRead);
        try {
            page.navigate(ConfigProvider.getBaseUrl() + "/inventory/" + s.target().getId());
            page.waitForCondition(() -> page.getByText(AccessForbiddenPage.FORBIDDEN_MESSAGE).isVisible()
                    || page.getByText("Не вдалося завантажити склад", new com.microsoft.playwright.Page.GetByTextOptions()
                            .setExact(true)).isVisible());
            if (!page.getByText(AccessForbiddenPage.FORBIDDEN_MESSAGE).isVisible()) {
                page.waitForCondition(() -> !targetReadStatuses.isEmpty());
                assertThat(targetReadStatuses).as("Load error is an access denial, not an infrastructure error")
                        .contains(403);
            }
            var save = page.getByRole(AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Зберегти"));
            if (save.count() > 0) {
                assertThat(save.isEnabled()).as("Direct editor cannot save descendant inventory").isFalse();
            }
            assertThat(page.getByText(s.changed().getName().trim(),
                    new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).count())
                    .as("No editable target resource rows are disclosed").isZero();
        } finally {
            page.offResponse(captureTargetRead);
        }
        attachScreenshot("Combined roles — descendant editor forbidden");
        assertStock(s, 10.0);
        assertThat(inventory.getInventoryState(s.target().getId(), UserRole.ADMIN).getState()).isEqualTo("OPEN");
        assertThat(inventory.requireInventoryProcess(s.processId(), UserRole.ADMIN))
                .as("Opening a forbidden editor cannot change the inventory process")
                .usingRecursiveComparison().isEqualTo(before);
    }

    private InventoryRequest request(Scenario s, double amount) {
        List<StorageItemResponse> items = inventory.listItems(s.target().getId(), UserRole.ADMIN);
        return InventoryRequest.builder().comment(COMMENT)
                .knownResourceIds(items.stream().map(i -> i.getResource().getId()).toList())
                .resources(items.stream().map(i -> ResourceUsageRequest.builder()
                        .resourceId(i.getResource().getId()).version(i.getVersion())
                        .amount(BigDecimal.valueOf(i.getResource().getId().equals(s.changed().getId())
                                ? amount : i.getAmount())).build()).toList()).build();
    }

    private void assertStock(Scenario s, double amount) {
        assertThat(snapshot(s.target().getId())).as("Only R changes on the target")
                .isEqualTo(Map.of(s.changed().getId(), amount, s.unchanged().getId(), 4.0));
        assertThat(snapshot(s.control().getId())).as("Control storage stays unchanged")
                .isEqualTo(Map.of(s.changed().getId(), 7.0));
    }

    private Map<Long, Double> snapshot(long storageId) {
        Map<Long, Double> result = new TreeMap<>();
        inventory.listItems(storageId, UserRole.ADMIN).forEach(i -> result.put(i.getResource().getId(), i.getAmount()));
        return result;
    }
}
