package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.RelocationStockAssertions;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API: переміщення в межах / поза областями видимості локацій та ресурсів.
 */
@Slf4j
@Epic("Relocation")
@Feature("Storages")
@Story("Relocation Visibility Regions")
public class RelocationVisibilityTest extends StorageApiTestBase {

    private static final Object OWNER2_ACCESS_LOCK = new Object();
    private static final String PREFIX = "rel-vis-";
    private static final double SEND_AMOUNT = 5.0;
    private static final String RESOURCE_PREFIX = "rel-vis-res-";

    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    private Long owner1StorageId;
    private Long owner2StorageId;
    private Long resourceId;
    private String originalOwner2AccessMode;
    private boolean owner2AccessModeChanged;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupStorageApiBase")
    @Step("Підготовка: OWNER_2 REGIONS + relocation/resource fixtures")
    public void setupRelocationVisibilityTests() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();

        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        resourceId = testContext.get(com.erp.test_context.ContextKey.RELOCATION_RESOURCE_ID);

        SchemaRegistry.logSchemaCoverage();
        ensureOwner2RestrictedAccess();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, owner2StorageId, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void restoreOwner2AccessMode() {
        restoreOwner2AccessIfChanged();
    }

    @Test(priority = 10)
    @TestCaseId("TC-REL-VIS-001")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendWithinVisibilityRegionSucceeds() {
        LocationScopeScenario scenario = prepareLocationScopeScenario(PREFIX + "in-", StorageAccessMode.FULL_ACCESS);
        seedStockOnStorage(owner2StorageId, resourceId);

        Set<Long> tracked = Set.of(resourceId);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "sender before in-scope send");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_2,
                owner2StorageId,
                scenario.inScopeRecipient().getId(),
                resourceId,
                SEND_AMOUNT);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "sender after in-scope send");
        RelocationStockAssertions.assertDebitedFromSender(
                before, after, owner2StorageId, resourceId, SEND_AMOUNT, "send в межах області");
    }

    @Test(priority = 20)
    @TestCaseId("TC-REL-VIS-002")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendOutsideVisibilityRegionRejected() {
        LocationScopeScenario scenario = prepareLocationScopeScenario(PREFIX + "out-", StorageAccessMode.FULL_ACCESS);
        seedStockOnStorage(owner2StorageId, resourceId);

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        assertThat(names.stream().map(StorageResponse::getId))
                .as("outsider не в /names перед send")
                .doesNotContain(scenario.outsider().getId());

        Set<Long> tracked = Set.of(resourceId);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "before out-of-scope send");

        RelocationOutputRequest request = RelocationDataFactory.buildSendRequest(
                owner2StorageId, scenario.outsider().getId(), resourceId, SEND_AMOUNT);
        Response response = relocationFixture.sendRaw(UserRole.OWNER_2, request);
        AllureHelper.attachResponseDetails(response);

        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                    apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "after out-of-scope send");
            RelocationStockAssertions.assertUnchanged(
                    before, after, owner2StorageId, resourceId, "send поза областю — stock без змін");
            return;
        }

        log.warn(
                "TC-REL-VIS-002: бекенд прийняв send на outsider поза /names (status={}) — location guard не реалізовано",
                response.statusCode());
        assertThat(response.statusCode()).as("send поза областю видимості").isBetween(400, 499);
    }

    @Test(priority = 30)
    @TestCaseId("TC-REL-VIS-003")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_003)
    @Severity(SeverityLevel.CRITICAL)
    public void testResolveFinishedWithinVisibilityRegion() {
        LocationScopeScenario scenario = prepareLocationScopeScenario(PREFIX + "resolve-", StorageAccessMode.FULL_ACCESS);
        long senderInRegion = scenario.inScopeRecipient().getId();
        seedStockOnStorage(senderInRegion, resourceId);

        Set<Long> tracked = Set.of(resourceId);

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, senderInRegion, owner2StorageId, resourceId, SEND_AMOUNT);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        ProductionStockAssertions.StockSnapshot recipientBefore = RelocationStockAssertions.capture(
                apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "recipient before resolve");

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.OWNER_2, sent.getId(), owner2StorageId, RelocationState.FINISHED);

        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot recipientAfter = RelocationStockAssertions.capture(
                apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "recipient after resolve");
        RelocationStockAssertions.assertCreditedToRecipient(
                recipientBefore, recipientAfter, owner2StorageId, resourceId, SEND_AMOUNT, "resolve в області");
    }

    @Test(priority = 40)
    @TestCaseId("TC-REL-VIS-004")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_004)
    @Severity(SeverityLevel.CRITICAL)
    public void testResolveWithOutsiderStorageIdRejected() {
        LocationScopeScenario scenario = prepareLocationScopeScenario(PREFIX + "res-out-", StorageAccessMode.FULL_ACCESS);
        seedStockOnStorage(owner2StorageId, resourceId);

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_2,
                owner2StorageId,
                scenario.inScopeRecipient().getId(),
                resourceId,
                SEND_AMOUNT);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        Response response = relocationFixture.resolveRaw(
                UserRole.OWNER_2, sent.getId(), scenario.outsider().getId(), RelocationState.FINISHED);

        assertThat(response.statusCode()).as("resolve з outsider storageId").isBetween(400, 499);
        assertThat(sent.getState()).as("локальний snapshot до resolve").isEqualTo(RelocationState.CREATED);
    }

    @Test(priority = 45)
    @TestCaseId("TC-REL-VIS-009")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_009)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendToRegionsAliasDeliversToRecipientStorageAnchor() {
        RegionsAliasSendScenario scenario = prepareRegionsAliasSendScenario(PREFIX + "alias-anchor-");
        seedStockOnStorage(scenario.senderInRegion().getId(), resourceId);

        StorageResponse aliasEntry = findRegionsAliasNameEntry(
                UserRole.OWNER_2, scenario.region(), scenario.anchor());
        assertThat(aliasEntry.getId())
                .as("id аліасу в /names ≠ sender location")
                .isNotEqualTo(scenario.senderInRegion().getId());

        Set<Long> tracked = Set.of(resourceId);
        ProductionStockAssertions.StockSnapshot anchorBefore = RelocationStockAssertions.capture(
                apiExecutor, scenario.anchor().getId(), UserRole.ADMIN, tracked, "anchor before alias send");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN,
                scenario.senderInRegion().getId(),
                aliasEntry.getId(),
                resourceId,
                SEND_AMOUNT);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(sent.getRecipient().getId())
                .as("видача на аліас → базова локація області")
                .isEqualTo(scenario.anchor().getId());

        RelocationResponse finished = relocationFixture.resolve(
                UserRole.ADMIN, sent.getId(), scenario.anchor().getId(), RelocationState.FINISHED);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot anchorAfter = RelocationStockAssertions.capture(
                apiExecutor, scenario.anchor().getId(), UserRole.ADMIN, tracked, "anchor after FINISHED");
        RelocationStockAssertions.assertCreditedToRecipient(
                anchorBefore, anchorAfter, scenario.anchor().getId(), resourceId, SEND_AMOUNT,
                "stock на anchor після видачі на REGIONS alias");
    }

    @Test(priority = 50)
    @TestCaseId("TC-REL-VIS-005")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_005)
    @Severity(SeverityLevel.CRITICAL)
    public void testRegionsAliasModeAllowsSendToInScopeLocation() {
        LocationScopeScenario scenario = prepareLocationScopeScenario(PREFIX + "alias-", StorageAccessMode.REGIONS);
        seedStockOnStorage(owner2StorageId, resourceId);

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        assertThat(names.stream().map(StorageResponse::getName))
                .as("REGIONS: ім'я області у /names")
                .contains(scenario.region().getName());

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_2,
                owner2StorageId,
                scenario.inScopeRecipient().getId(),
                resourceId,
                SEND_AMOUNT);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
    }

    @Test(priority = 60)
    @TestCaseId("TC-REL-VIS-007")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_007)
    @Severity(SeverityLevel.CRITICAL)
    public void testExplicitGrantAllowsSendToGrantedLocation() {
        StorageResponse visible = storageFixture.createUniqueStorage(PREFIX + "grant-vis-");
        regionFixture.addExplicitLocations(visible.getId(), owner2StorageId);
        seedStockOnStorage(owner2StorageId, resourceId);

        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        assertThat(names.stream().filter(s -> visible.getId().equals(s.getId())).findFirst())
                .as("explicit grant — реальне ім'я у /names")
                .isPresent()
                .get()
                .extracting(StorageResponse::getName)
                .isEqualTo(visible.getName());

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.OWNER_2, owner2StorageId, visible.getId(), resourceId, SEND_AMOUNT);

        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
    }

    @Test(priority = 70)
    @TestCaseId("TC-REL-VIS-008")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_008)
    @Severity(SeverityLevel.NORMAL)
    public void testRevokeExplicitGrantBlocksSubsequentSend() {
        StorageResponse visible = storageFixture.createUniqueStorage(PREFIX + "revoke-vis-");
        regionFixture.addExplicitLocations(visible.getId(), owner2StorageId);
        seedStockOnStorage(owner2StorageId, resourceId);

        RelocationResponse first = relocationFixture.createSend(
                UserRole.OWNER_2, owner2StorageId, visible.getId(), resourceId, SEND_AMOUNT);
        assertThat(first.getState()).isEqualTo(RelocationState.CREATED);
        relocationFixture.resolve(
                UserRole.ADMIN, first.getId(), visible.getId(), RelocationState.FINISHED);

        regionFixture.removeExplicitLocations(visible.getId(), owner2StorageId);

        List<StorageResponse> namesAfterRevoke = storageFixture.getNames(UserRole.OWNER_2, true, null);
        assertThat(namesAfterRevoke.stream().map(StorageResponse::getId))
                .as("після revoke grant локація зникає з /names")
                .doesNotContain(visible.getId());

        Set<Long> tracked = Set.of(resourceId);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "before send after revoke");

        RelocationOutputRequest request = RelocationDataFactory.buildSendRequest(
                owner2StorageId, visible.getId(), resourceId, SEND_AMOUNT);
        Response response = relocationFixture.sendRaw(UserRole.OWNER_2, request);
        AllureHelper.attachResponseDetails(response);

        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                    apiExecutor, owner2StorageId, UserRole.OWNER_2, tracked, "after failed send post-revoke");
            RelocationStockAssertions.assertUnchanged(
                    before, after, owner2StorageId, resourceId, "revoke grant — stock без змін");
            return;
        }

        log.warn(
                "TC-REL-VIS-008: бекенд прийняв send після revoke explicit grant (status={}) — guard не реалізовано",
                response.statusCode());
        assertThat(response.statusCode()).as("send після revoke grant").isBetween(400, 499);
    }

    @Test(priority = 80)
    @TestCaseId("TC-REL-VIS-010")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_010)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendWithResourceInVisibilityScopeSucceeds() {
        ResourceResponse inScope = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "in-");
        RestrictedResourceSetup setup = createRestrictedResourceUnit(PREFIX + "res-in-");
        regionFixture.addRegionResources(setup.region().getId(), inScope.getId());

        relocationFixture.ensureStock(owner1StorageId, inScope.getId(), 50.0);

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN,
                owner1StorageId,
                setup.unit().getId(),
                inScope.getId(),
                SEND_AMOUNT);

        assertThat(sent.getState()).isIn(RelocationState.CREATED, RelocationState.AUTO_FINISHED);
    }

    @Test(priority = 90)
    @TestCaseId("TC-REL-VIS-011")
    @Description(StorageRegionsAllureDescriptions.TC_REL_VIS_011)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendWithResourceOutsideVisibilityScopeRejected() {
        ResourceResponse inScope = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "scoped-");
        ResourceResponse outOfScope = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "hidden-");
        RestrictedResourceSetup setup = createRestrictedResourceUnit(PREFIX + "res-out-");
        regionFixture.addRegionResources(setup.region().getId(), inScope.getId());

        relocationFixture.ensureStock(owner1StorageId, outOfScope.getId(), 50.0);

        assertThat(resourceFixture.isPresentInAutocompleteForStorage(
                UserRole.ADMIN, setup.unit().getId(), outOfScope.getName(), outOfScope.getId(), false))
                .as("ресурс поза областю не в autocomplete")
                .isFalse();

        RelocationOutputRequest request = RelocationDataFactory.buildSendRequest(
                owner1StorageId, setup.unit().getId(), outOfScope.getId(), SEND_AMOUNT);
        Response response = relocationFixture.sendRaw(UserRole.ADMIN, request);
        AllureHelper.attachResponseDetails(response);

        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            return;
        }

        log.warn(
                "TC-REL-VIS-011: бекенд прийняв send ресурсом поза областю RESOURCES (status={}) — guard не реалізовано; "
                        + "UI autocomplete фільтрує за storageId",
                response.statusCode());
        assertThat(response.statusCode()).as("send ресурсом поза областю RESOURCES").isBetween(400, 499);
    }

    private RegionsAliasSendScenario prepareRegionsAliasSendScenario(String prefix) {
        StorageResponse anchor = storageFixture.createUniqueStorage(prefix + "anchor-");
        StorageResponse senderInRegion = storageFixture.createUniqueStorage(prefix + "from-");

        StorageRegionResponse region = regionFixture.createRegion(
                anchor, StorageAccessMode.REGIONS, prefix + "reg-");
        regionFixture.addRegionLocations(region.getId(), senderInRegion.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        return new RegionsAliasSendScenario(region, anchor, senderInRegion);
    }

    private StorageResponse findRegionsAliasNameEntry(
            UserRole viewer, StorageRegionResponse region, StorageResponse anchor) {
        List<StorageResponse> names = storageFixture.getNames(viewer, true, null);
        StorageResponse aliasEntry = names.stream()
                .filter(s -> region.getName().equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "REGIONS alias «" + region.getName() + "» не знайдено в /names"));
        assertThat(aliasEntry.getId())
                .as("/names: id аліасу = recipientStorage (anchor)")
                .isEqualTo(anchor.getId());
        return aliasEntry;
    }

    private LocationScopeScenario prepareLocationScopeScenario(String prefix, StorageAccessMode regionMode) {
        StorageResponse regionAnchor = storageFixture.createUniqueStorage(prefix + "anchor-");
        StorageResponse inScopeRecipient = storageFixture.createUniqueStorage(prefix + "to-");
        StorageResponse outsider = storageFixture.createUniqueStorage(prefix + "outsider-");

        StorageRegionResponse region = regionFixture.createRegion(
                regionAnchor, regionMode, prefix + "reg-");
        regionFixture.addRegionLocations(region.getId(), inScopeRecipient.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        return new LocationScopeScenario(region, inScopeRecipient, outsider);
    }

    private RestrictedResourceSetup createRestrictedResourceUnit(String namePrefix) {
        StorageResponse unit = storageFixture.createStorage(
                StorageDataFactory.restrictedStorage(
                        storageFixture.resolveParentUnit().getId(), namePrefix + "unit-").build());
        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.RESOURCES, namePrefix + "reg-");
        regionFixture.addRegionMembers(region.getId(), unit.getId());
        return new RestrictedResourceSetup(unit, region);
    }

    private void seedStockOnStorage(long storageId, long resId) {
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor, UserRole.ADMIN, storageId, Map.of(resId, 50.0));
    }

    private void ensureOwner2RestrictedAccess() {
        synchronized (OWNER2_ACCESS_LOCK) {
            StorageResponse owner2Storage = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
            originalOwner2AccessMode = owner2Storage.getAccessMode();
            if (!StorageAccessMode.REGIONS.name().equals(originalOwner2AccessMode)) {
                StorageRequest update = StorageDataFactory.withAccessMode(
                        owner2Storage, StorageAccessMode.REGIONS);
                storageFixture.update(UserRole.ADMIN, owner2StorageId, update);
                owner2AccessModeChanged = true;
                log.info("OWNER_2 storage {} set to REGIONS for relocation visibility tests", owner2StorageId);
            }
        }
    }

    private void restoreOwner2AccessIfChanged() {
        if (!owner2AccessModeChanged || originalOwner2AccessMode == null) {
            return;
        }
        synchronized (OWNER2_ACCESS_LOCK) {
            try {
                StorageResponse current = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
                StorageRequest restore = StorageDataFactory.withAccessMode(
                        current, StorageAccessMode.valueOf(originalOwner2AccessMode));
                storageFixture.update(UserRole.ADMIN, owner2StorageId, restore);
                log.info("OWNER_2 storage {} accessMode restored to {}", owner2StorageId, originalOwner2AccessMode);
            } catch (Exception e) {
                log.warn("Failed to restore OWNER_2 storage accessMode: {}", e.getMessage());
            }
        }
    }

    private record RegionsAliasSendScenario(
            StorageRegionResponse region,
            StorageResponse anchor,
            StorageResponse senderInRegion) {
    }

    private record LocationScopeScenario(
            StorageRegionResponse region,
            StorageResponse inScopeRecipient,
            StorageResponse outsider) {
    }

    private record RestrictedResourceSetup(StorageResponse unit, StorageRegionResponse region) {
    }
}
