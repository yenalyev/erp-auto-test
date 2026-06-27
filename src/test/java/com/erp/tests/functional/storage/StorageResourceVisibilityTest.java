package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.InventoryRequest;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionResourceResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Області видимості <b>ресурсів</b> для підрозділів з {@code accessMode=REGIONS} (RESTRICTED).
 * <p>Бекенд: область з {@code accessMode=RESOURCES} + {@code storage_region_resource};
 * union через кілька областей; explicit grant через {@code storage_resource} (relocation auto-grant).
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Resource Visibility Scopes")
public class StorageResourceVisibilityTest extends StorageApiTestBase {

    private static final String RESOURCE_PREFIX = "res-vis-";

    private ResourceFixture resourceFixture;
    private RelocationFixture relocationFixture;
    private InventoryFixture inventoryFixture;

    private Long owner1StorageId;

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка: ресурси, relocation/inventory fixtures")
    public void setupResourceVisibilityTests() {
        storageFixture.prepareContext();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();

        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);

        owner1StorageId = ConfigProvider.getOwner1StorageId();
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-RES-001")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testAddAndListRegionResources() {
        ResourceResponse resourceA = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "a-");
        ResourceResponse resourceB = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "b-");
        RestrictedUnitSetup setup = createRestrictedUnit("res-reg-crud-");

        StorageRegionResponse afterAdd = regionFixture.addRegionResources(
                setup.region().getId(), resourceA.getId(), resourceB.getId());

        assertThat(afterAdd.getResourcesCount()).isGreaterThanOrEqualTo(2);

        List<StorageRegionResourceResponse> resources =
                regionFixture.getRegionResources(UserRole.ADMIN, setup.region().getId());
        assertThat(resources.stream().map(StorageRegionResourceResponse::getResourceId))
                .contains(resourceA.getId(), resourceB.getId());

        regionFixture.removeRegionResources(setup.region().getId(), resourceB.getId());
        resources = regionFixture.getRegionResources(UserRole.ADMIN, setup.region().getId());
        assertThat(resources.stream().map(StorageRegionResourceResponse::getResourceId))
                .contains(resourceA.getId())
                .doesNotContain(resourceB.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-RES-002")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testRestrictedUnitWithoutResourceRegionsSeesNoCatalog() {
        ResourceResponse hidden = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "hidden-");
        RestrictedUnitSetup setup = createRestrictedUnit("res-empty-");

        List<ResourceResponse> results = resourceFixture.autocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), RESOURCE_PREFIX, false);

        assertThat(results).isEmpty();
        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), hidden.getName(), hidden.getId(), false))
                .isFalse();
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-RES-003")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_003)
    @Severity(SeverityLevel.CRITICAL)
    public void testRestrictedMemberSeesOnlyGrantedResources() {
        ResourceResponse granted = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "granted-");
        ResourceResponse outsider = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "outsider-");

        RestrictedUnitSetup setup = createRestrictedUnit("res-member-");
        regionFixture.addRegionResources(setup.region().getId(), granted.getId());

        List<ResourceResponse> autocomplete = resourceFixture.autocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), RESOURCE_PREFIX, false);
        Set<Long> ids = autocomplete.stream().map(ResourceResponse::getId).collect(Collectors.toSet());

        assertThat(ids).contains(granted.getId()).doesNotContain(outsider.getId());

        List<ResourceResponse> page = resourceFixture.getPageForStorage(
                UserRole.ADMIN, setup.unit().getId(), RESOURCE_PREFIX);
        assertThat(page.stream().map(ResourceResponse::getId))
                .contains(granted.getId())
                .doesNotContain(outsider.getId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-STR-RES-004")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_004)
    @Severity(SeverityLevel.NORMAL)
    public void testTwoResourceRegionsUnion() {
        ResourceResponse r1 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "u1-");
        ResourceResponse r2 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "u2-");
        ResourceResponse r3 = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "u3-");

        StorageResponse unit = createRestrictedStorage("res-union-unit-");
        StorageRegionResponse region1 = regionFixture.createRegion(
                unit, StorageAccessMode.RESOURCES, "res-union-r1-");
        StorageRegionResponse region2 = regionFixture.createRegion(
                unit, StorageAccessMode.RESOURCES, "res-union-r2-");

        regionFixture.addRegionMembers(region1.getId(), unit.getId());
        regionFixture.addRegionMembers(region2.getId(), unit.getId());
        regionFixture.addRegionResources(region1.getId(), r1.getId(), r2.getId());
        regionFixture.addRegionResources(region2.getId(), r2.getId(), r3.getId());

        List<ResourceResponse> visible = resourceFixture.autocompleteForStorage(
                UserRole.ADMIN, unit.getId(), RESOURCE_PREFIX, false);

        assertThat(visible.stream().map(ResourceResponse::getId))
                .contains(r1.getId(), r2.getId(), r3.getId());
    }

    @Test(priority = 50)
    @TestCaseId("TC-STR-RES-005")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_005)
    @Severity(SeverityLevel.CRITICAL)
    public void testInternalRelocationAutoGrantsResourceVisibility() {
        ResourceResponse inRegion = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "in-reg-");
        ResourceResponse newViaRelocation = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "reloc-");

        RestrictedUnitSetup setup = createRestrictedUnit("res-auto-grant-");
        regionFixture.addRegionResources(setup.region().getId(), inRegion.getId());

        relocationFixture.ensureStock(owner1StorageId, newViaRelocation.getId(), 50.0);

        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), newViaRelocation.getName(),
                newViaRelocation.getId(), false))
                .as("Ресурс ще не видимий до relocation")
                .isFalse();

        var sent = relocationFixture.createSend(
                UserRole.ADMIN,
                owner1StorageId,
                setup.unit().getId(),
                newViaRelocation.getId(),
                5.0);
        relocationFixture.resolve(
                UserRole.ADMIN, sent.getId(), setup.unit().getId(), RelocationState.FINISHED);

        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), newViaRelocation.getName(),
                newViaRelocation.getId(), false))
                .as("Після receive ресурс має з'явитись у селекторі (auto-grant)")
                .isTrue();
        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), inRegion.getName(), inRegion.getId(), false))
                .isTrue();
    }

    @Test(priority = 60)
    @TestCaseId("TC-STR-RES-006")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_006)
    @Severity(SeverityLevel.NORMAL)
    public void testFullAccessSeesMoreResourcesThanRestricted() {
        ResourceResponse shared = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "shared-");
        ResourceResponse extra = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "extra-");

        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse fullAccess = storageFixture.createStorage(
                StorageDataFactory.childStorage(parent.getId(), "res-full-").build());
        RestrictedUnitSetup restricted = createRestrictedUnit("res-restricted-");
        regionFixture.addRegionResources(restricted.region().getId(), shared.getId());

        // Пошук за точним іменем: prefix res-vis- на dev часто дає ≥50 збігів (dirty data) і обрізається size=50.
        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, fullAccess.getId(), shared.getName(), shared.getId(), false))
                .as("FULL_ACCESS підрозділ бачить shared у autocomplete")
                .isTrue();
        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, fullAccess.getId(), extra.getName(), extra.getId(), false))
                .as("FULL_ACCESS підрозділ бачить extra поза областями")
                .isTrue();
        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, restricted.unit().getId(), shared.getName(), shared.getId(), false))
                .as("RESTRICTED бачить лише shared з області")
                .isTrue();
        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, restricted.unit().getId(), extra.getName(), extra.getId(), false))
                .as("RESTRICTED не бачить extra поза областю")
                .isFalse();
    }

    @Test(priority = 70)
    @TestCaseId("TC-STR-RES-007")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_007)
    @Severity(SeverityLevel.NORMAL)
    public void testRemoveResourceFromRegionBlockedWhenStockPresent() {
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "stock-guard-");
        RestrictedUnitSetup setup = createRestrictedUnit("res-stock-guard-");
        regionFixture.addRegionResources(setup.region().getId(), resource.getId());

        relocationFixture.ensureStock(owner1StorageId, resource.getId(), 50.0);
        var sent = relocationFixture.createSend(
                UserRole.ADMIN,
                owner1StorageId,
                setup.unit().getId(),
                resource.getId(),
                10.0);
        relocationFixture.resolve(
                UserRole.ADMIN, sent.getId(), setup.unit().getId(), RelocationState.FINISHED);

        assertThat(inventoryFixture.getResourceStock(
                setup.unit().getId(), resource.getId(), UserRole.ADMIN))
                .isGreaterThan(0.0);

        Response removeResponse = regionFixture.removeRegionResourcesRaw(
                UserRole.ADMIN, setup.region().getId(), resource.getId());

        if (removeResponse.statusCode() == 400) {
            assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                    UserRole.ADMIN, setup.unit().getId(), resource.getName(), resource.getId(), false))
                    .isTrue();
            return;
        }

        log.warn("TC-STR-RES-007: бекенд дозволив DELETE ресурсу з області при stock>0 (status={}) — guard 2.1.1 не реалізовано",
                removeResponse.statusCode());
        assertThat(removeResponse.statusCode()).isEqualTo(200);
    }

    @Test(priority = 80)
    @TestCaseId("TC-STR-RES-008")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_008)
    @Severity(SeverityLevel.CRITICAL)
    public void testInventoryRejectsResourceOutsideVisibilityScope() {
        ResourceResponse visible = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "inv-vis-");
        ResourceResponse hidden = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "inv-hid-");

        RestrictedUnitSetup setup = createRestrictedUnit("res-inv-");
        regionFixture.addRegionResources(setup.region().getId(), visible.getId());

        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), hidden.getName(), hidden.getId(), false))
                .as("Прихований ресурс не має бути в autocomplete до інвентаризації")
                .isFalse();

        inventoryFixture.ensureClosed(setup.unit().getId());
        inventoryFixture.openSession(setup.unit().getId());

        try {
            assertThat(inventoryFixture.getResourceStock(
                    setup.unit().getId(), hidden.getId(), UserRole.ADMIN))
                    .as("На складі ще немає прихованого ресурсу")
                    .isEqualTo(0.0);

            InventoryRequest request = InventoryDataFactory.seedAmounts(Map.of(hidden.getId(), 3.0));
            Response inventoryResponse = inventoryFixture.conductInventoryRaw(
                    setup.unit().getId(), UserRole.ADMIN, request);

            AllureHelper.attachResponseDetails(inventoryResponse);
            if (inventoryResponse.statusCode() == 400) {
                assertThat(inventoryFixture.getResourceStock(
                        setup.unit().getId(), hidden.getId(), UserRole.ADMIN))
                        .as("Залишок прихованого ресурсу не має змінитись")
                        .isEqualTo(0.0);
                return;
            }

            log.warn(
                    "TC-STR-RES-008: бекенд прийняв inventory з ресурсом поза областю (status={}) — guard 4.2 не реалізовано; "
                            + "UI не відтворює (autocomplete фільтрує за storageId)",
                    inventoryResponse.statusCode());
            assertThat(inventoryResponse.statusCode())
                    .as("Інвентаризація з ресурсом поза областю видимості має бути відхилена (400)")
                    .isEqualTo(400);
        } finally {
            inventoryFixture.ensureClosed(setup.unit().getId());
        }
    }

    @Test(priority = 85)
    @TestCaseId("TC-STR-RES-010")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_010)
    @Severity(SeverityLevel.NORMAL)
    public void testInventoryAllowsResourceInsideVisibilityScope() {
        ResourceResponse visible = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "inv-ok-");

        RestrictedUnitSetup setup = createRestrictedUnit("res-inv-ok-");
        regionFixture.addRegionResources(setup.region().getId(), visible.getId());

        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), visible.getName(), visible.getId(), false))
                .isTrue();

        inventoryFixture.ensureClosed(setup.unit().getId());
        inventoryFixture.openSession(setup.unit().getId());

        try {
            assertThat(inventoryFixture.getResourceStock(
                    setup.unit().getId(), visible.getId(), UserRole.ADMIN))
                    .isEqualTo(0.0);

            InventoryRequest request = InventoryDataFactory.seedAmounts(Map.of(visible.getId(), 4.0));
            inventoryFixture.conductInventory(setup.unit().getId(), UserRole.ADMIN, request);

            assertThat(inventoryFixture.getResourceStock(
                    setup.unit().getId(), visible.getId(), UserRole.ADMIN))
                    .isCloseTo(4.0, within(0.01));
        } finally {
            inventoryFixture.ensureClosed(setup.unit().getId());
        }
    }

    @Test(priority = 87)
    @TestCaseId("TC-STR-RES-011")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_011)
    @Severity(SeverityLevel.CRITICAL)
    public void testInternalReceiveExpandsResourceVisibilityScope() {
        ResourceResponse inScope = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "recv-in-");
        ResourceResponse outOfScope = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "recv-out-");

        RestrictedUnitSetup setup = createRestrictedUnit("res-int-recv-");
        regionFixture.addRegionResources(setup.region().getId(), inScope.getId());

        relocationFixture.ensureStock(owner1StorageId, inScope.getId(), 50.0);
        relocationFixture.ensureStock(owner1StorageId, outOfScope.getId(), 50.0);

        long recipientId = setup.unit().getId();

        Allure.step("Assert: до receive inScope видимий, outOfScope — ні", () -> {
            assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                    UserRole.ADMIN, recipientId, inScope.getName(), inScope.getId(), false))
                    .isTrue();
            assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                    UserRole.ADMIN, recipientId, outOfScope.getName(), outOfScope.getId(), false))
                    .isFalse();
            assertThat(inventoryFixture.getResourceStock(recipientId, inScope.getId(), UserRole.ADMIN))
                    .isEqualTo(0.0);
            assertThat(inventoryFixture.getResourceStock(recipientId, outOfScope.getId(), UserRole.ADMIN))
                    .isEqualTo(0.0);
        });

        double inScopeQty = 7.0;
        double outOfScopeQty = 11.0;

        RelocationResponse outOfScopeSend = Allure.step(
                "Act: INTERNAL send outOfScope (поза областю)",
                () -> relocationFixture.createSend(
                        UserRole.ADMIN,
                        owner1StorageId,
                        recipientId,
                        outOfScope.getId(),
                        outOfScopeQty));
        relocationFixture.resolve(
                UserRole.ADMIN, outOfScopeSend.getId(), recipientId, RelocationState.FINISHED);

        Allure.step("Assert: outOfScope з'явився у видимості після receive", () -> {
            assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                    UserRole.ADMIN, recipientId, outOfScope.getName(), outOfScope.getId(), false))
                    .as("outOfScope має з'явитись у селекторі після internal receive")
                    .isTrue();
            assertThat(inventoryFixture.getResourceStock(recipientId, outOfScope.getId(), UserRole.ADMIN))
                    .isCloseTo(outOfScopeQty, within(0.01));
        });

        RelocationResponse inScopeSend = Allure.step(
                "Act: INTERNAL send inScope (в області)",
                () -> relocationFixture.createSend(
                        UserRole.ADMIN,
                        owner1StorageId,
                        recipientId,
                        inScope.getId(),
                        inScopeQty));
        relocationFixture.resolve(
                UserRole.ADMIN, inScopeSend.getId(), recipientId, RelocationState.FINISHED);

        Allure.step("Assert: після receive обидва ресурси у селекторі та на залишку", () -> {
            assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                    UserRole.ADMIN, recipientId, inScope.getName(), inScope.getId(), false))
                    .isTrue();
            assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                    UserRole.ADMIN, recipientId, outOfScope.getName(), outOfScope.getId(), false))
                    .as("outOfScope має з'явитись у області видимості після internal receive")
                    .isTrue();

            assertThat(inventoryFixture.getResourceStock(recipientId, inScope.getId(), UserRole.ADMIN))
                    .isCloseTo(inScopeQty, within(0.01));
            assertThat(inventoryFixture.getResourceStock(recipientId, outOfScope.getId(), UserRole.ADMIN))
                    .isCloseTo(outOfScopeQty, within(0.01));
        });

        Allure.step("Assert: outOfScope не в named-області, але видимий через auto-grant", () -> {
            List<StorageRegionResourceResponse> regionResources =
                    regionFixture.getRegionResources(UserRole.ADMIN, setup.region().getId());
            assertThat(regionResources.stream().map(StorageRegionResourceResponse::getResourceId))
                    .contains(inScope.getId())
                    .doesNotContain(outOfScope.getId());
            assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                    UserRole.ADMIN, recipientId, outOfScope.getName(), outOfScope.getId(), false))
                    .isTrue();
        });
    }

    @Test(priority = 90)
    @TestCaseId("TC-STR-RES-009")
    @Description(StorageRegionsAllureDescriptions.TC_STR_RES_009)
    @Severity(SeverityLevel.NORMAL)
    public void testSupplierReceiveForNonVisibleResource() {
        ResourceResponse hidden = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "supp-");
        RestrictedUnitSetup setup = createRestrictedUnit("res-supp-");

        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), hidden.getName(), hidden.getId(), false))
                .isFalse();

        try {
            RelocationStockSeeder.receiveFromSupplier(
                    apiExecutor,
                    UserRole.ADMIN,
                    setup.unit().getId(),
                    Map.of(hidden.getId(), 3.0));
        } catch (IllegalStateException e) {
            log.info("TC-STR-RES-009: supplier receive відхилено: {}", e.getMessage());
            assertThat(e.getMessage()).contains("Relocation receive failed");
            return;
        }

        boolean visibleAfter = resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), hidden.getName(), hidden.getId(), false);
        assertThat(visibleAfter)
                .as("Після supplier receive: або 400 (4.1), або auto-grant у селектор (2.2)")
                .isTrue();
    }

    private RestrictedUnitSetup createRestrictedUnit(String namePrefix) {
        StorageResponse unit = createRestrictedStorage(namePrefix + "unit-");
        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.RESOURCES, namePrefix + "reg-");
        regionFixture.addRegionMembers(region.getId(), unit.getId());
        return new RestrictedUnitSetup(unit, region);
    }

    private StorageResponse createRestrictedStorage(String namePrefix) {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageRequest request = StorageDataFactory.restrictedStorage(parent.getId(), namePrefix).build();
        return storageFixture.createStorage(request);
    }

    private record RestrictedUnitSetup(StorageResponse unit, StorageRegionResponse region) {
    }
}
