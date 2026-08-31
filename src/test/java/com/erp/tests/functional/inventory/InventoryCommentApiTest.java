package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.storage.CrewApiTestBase;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * REQ-WMS-003 AC-12…AC-14 — optional {@code comment} on PUT /storages/{id}/inventory
 * for all stock-managed location types.
 */
@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-003 Comment")
public class InventoryCommentApiTest extends CrewApiTestBase {

    private static final String PREFIX = "inv-cmt-";
    private static final double STOCK_AMOUNT = 20.0;
    private static final double ISSUE_AMOUNT = 12.0;

    private record LocationSeed(String label, UnitType type, long storageId, long resourceId) {}

    private Long parentId;
    private LocationSeed unitSeed;
    private LocationSeed storageSeed;
    private LocationSeed productionSeed;
    private LocationSeed flyPointSeed;
    private LocationSeed crewSeed;
    private LocationSeed omitCommentSeed;
    private LocationSeed blankCommentSeed;
    private LocationSeed validationSeed;
    private LocationSeed isolationStorageSeed;
    private LocationSeed isolationProductionSibling;
    private long isolationResourceAId;
    private long isolationResourceBId;
    private final List<Long> sessionStorageIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка локацій UNIT/STORAGE/PRODUCTION/FLY_POINT/CREW для comment-тестів")
    public void setupInventoryCommentLocations() {
        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        StorageResponse member = storageFixture.getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        parentId = member.getParent() != null ? member.getParent().getId() : member.getId();

        unitSeed = seedSimpleLocation(UnitType.UNIT, PREFIX + "unit-");
        storageSeed = seedSimpleLocation(UnitType.STORAGE, PREFIX + "stor-");
        productionSeed = seedSimpleLocation(UnitType.PRODUCTION, PREFIX + "prod-");
        flyPointSeed = seedFlyPointLocation(PREFIX + "fp-");
        crewSeed = seedUnattachedCrewLocation(PREFIX + "crew-");
        omitCommentSeed = seedSimpleLocation(UnitType.UNIT, PREFIX + "omit-");
        blankCommentSeed = seedSimpleLocation(UnitType.STORAGE, PREFIX + "blank-");
        validationSeed = seedSimpleLocation(UnitType.STORAGE, PREFIX + "val-");
        seedIsolationPair();
    }

    @AfterMethod(alwaysRun = true)
    public void closeOpenInventorySessions() {
        for (Long storageId : sessionStorageIds) {
            try {
                inventoryFixture.ensureClosed(storageId);
            } catch (Exception e) {
                log.warn("Failed to close inventory session on {}: {}", storageId, e.getMessage());
            }
        }
        sessionStorageIds.clear();
    }

    @DataProvider(name = "supportedInventoryLocationTypes")
    public Object[][] supportedInventoryLocationTypes() {
        return new Object[][]{
                {unitSeed},
                {storageSeed},
                {productionSeed},
                {flyPointSeed},
                {crewSeed}
        };
    }

    @Test(priority = 10, dataProvider = "supportedInventoryLocationTypes")
    @TestCaseId("TC-WMS-003-012")
    @Story("Comment stored in operation history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            UI client payload: PUT /storages/{id}/inventory with full resources snapshot + comment.
            Mirrors tk-ui InventoryEditPage save() — comment copied onto ADDED_INV/REMOVED_INV rows.
            """)
    public void commentStoredInHistoryForSupportedLocationType(LocationSeed seed) {
        Allure.parameter("locationType", seed.label());
        Allure.parameter("storageId", seed.storageId());
        String comment = "Перерахунок " + seed.type() + " " + seed.storageId();

        inventoryFixture.openSession(seed.storageId());
        trackSession(seed.storageId());
        double before = inventoryFixture.getResourceStock(seed.storageId(), seed.resourceId(), UserRole.ADMIN);
        double target = before + 3.0;

        List<StorageItemResponse> items = inventoryFixture.listItems(seed.storageId(), UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(seed.resourceId(), target), comment);
        inventoryFixture.conductInventory(seed.storageId(), UserRole.ADMIN, request);

        assertThat(inventoryFixture.getResourceStock(seed.storageId(), seed.resourceId(), UserRole.ADMIN))
                .isCloseTo(target, within(0.01));
        assertThat(inventoryFixture.findInventoryHistoryComment(seed.storageId(), seed.resourceId(), UserRole.ADMIN))
                .isEqualTo(comment);
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-003-013")
    @Story("Omit and blank comment")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT without comment and with blank comment — stock changes, history comment is null.")
    public void omitAndBlankCommentAcceptedWithNullHistoryComment() {
        assertCommentOptionalOnLocation(omitCommentSeed, null);
        assertCommentOptionalOnLocation(blankCommentSeed, "   ");
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-003-014")
    @Story("Comment only on changed resources + sibling isolation")
    @Severity(SeverityLevel.CRITICAL)
    public void commentOnlyOnChangedResourcesWithSiblingIsolation() {
        String comment = "Коментар лише для ресурсу A " + isolationStorageSeed.storageId();
        double stockAOnSiblingBefore = inventoryFixture.getResourceStock(
                isolationProductionSibling.storageId(), isolationResourceAId, UserRole.ADMIN);

        inventoryFixture.openSession(isolationStorageSeed.storageId());
        trackSession(isolationStorageSeed.storageId());

        double beforeA = inventoryFixture.getResourceStock(
                isolationStorageSeed.storageId(), isolationResourceAId, UserRole.ADMIN);
        double beforeB = inventoryFixture.getResourceStock(
                isolationStorageSeed.storageId(), isolationResourceBId, UserRole.ADMIN);
        double targetA = beforeA + 2.0;

        List<StorageItemResponse> items = inventoryFixture.listItems(
                isolationStorageSeed.storageId(), UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(isolationResourceAId, targetA), comment);
        inventoryFixture.conductInventory(isolationStorageSeed.storageId(), UserRole.ADMIN, request);

        assertThat(inventoryFixture.findInventoryHistoryComment(
                isolationStorageSeed.storageId(), isolationResourceAId, UserRole.ADMIN))
                .isEqualTo(comment);
        assertThat(inventoryFixture.findInventoryHistoryComment(
                isolationStorageSeed.storageId(), isolationResourceBId, UserRole.ADMIN))
                .isNull();
        assertThat(inventoryFixture.getResourceStock(
                isolationStorageSeed.storageId(), isolationResourceBId, UserRole.ADMIN))
                .isCloseTo(beforeB, within(0.01));
        assertThat(inventoryFixture.getResourceStock(
                isolationProductionSibling.storageId(), isolationResourceAId, UserRole.ADMIN))
                .isCloseTo(stockAOnSiblingBefore, within(0.01));
        assertThat(inventoryFixture.findInventoryHistoryComment(
                isolationProductionSibling.storageId(), isolationResourceAId, UserRole.ADMIN))
                .isNull();
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-003-015")
    @Story("Comment too long rejected")
    @Severity(SeverityLevel.CRITICAL)
    public void commentTooLongReturns400AndKeepsStock() {
        inventoryFixture.openSession(validationSeed.storageId());
        trackSession(validationSeed.storageId());

        double before = inventoryFixture.getResourceStock(
                validationSeed.storageId(), validationSeed.resourceId(), UserRole.ADMIN);
        List<StorageItemResponse> items = inventoryFixture.listItems(validationSeed.storageId(), UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items,
                Map.of(validationSeed.resourceId(), before + 1.0),
                InventoryDataFactory.commentOfExactLength(InventoryDataFactory.COMMENT_MAX_LENGTH + 1));

        Response response = inventoryFixture.conductInventoryRaw(
                validationSeed.storageId(), UserRole.ADMIN, request);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("errors[0].field")).isEqualTo("comment");
        assertThat(inventoryFixture.getResourceStock(
                validationSeed.storageId(), validationSeed.resourceId(), UserRole.ADMIN))
                .isCloseTo(before, within(0.01));
    }

    @Test(priority = 50)
    @TestCaseId("TC-WMS-003-016")
    @Story("Comment exactly max length accepted")
    @Severity(SeverityLevel.CRITICAL)
    public void commentExactlyMaxLengthStoredInHistory() {
        String comment = InventoryDataFactory.commentOfExactLength(InventoryDataFactory.COMMENT_MAX_LENGTH);
        inventoryFixture.openSession(validationSeed.storageId());
        trackSession(validationSeed.storageId());

        double before = inventoryFixture.getResourceStock(
                validationSeed.storageId(), validationSeed.resourceId(), UserRole.ADMIN);
        List<StorageItemResponse> items = inventoryFixture.listItems(validationSeed.storageId(), UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(validationSeed.resourceId(), before + 1.0), comment);
        inventoryFixture.conductInventory(validationSeed.storageId(), UserRole.ADMIN, request);

        assertThat(inventoryFixture.findInventoryHistoryComment(
                validationSeed.storageId(), validationSeed.resourceId(), UserRole.ADMIN))
                .isEqualTo(comment);
    }

    private void assertCommentOptionalOnLocation(LocationSeed seed, String comment) {
        inventoryFixture.openSession(seed.storageId());
        trackSession(seed.storageId());

        double before = inventoryFixture.getResourceStock(seed.storageId(), seed.resourceId(), UserRole.ADMIN);
        double target = before + 1.0;
        List<StorageItemResponse> items = inventoryFixture.listItems(seed.storageId(), UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(seed.resourceId(), target));
        if (comment != null) {
            request = request.toBuilder().comment(comment).build();
        }

        inventoryFixture.conductInventory(seed.storageId(), UserRole.ADMIN, request);

        assertThat(inventoryFixture.getResourceStock(seed.storageId(), seed.resourceId(), UserRole.ADMIN))
                .isCloseTo(target, within(0.01));
        assertThat(inventoryFixture.findInventoryHistoryComment(seed.storageId(), seed.resourceId(), UserRole.ADMIN))
                .isNull();
    }

    private LocationSeed seedSimpleLocation(UnitType type, String prefix) {
        StorageResponse location = storageFixture.createChildStorage(
                parentId, prefix, type, StorageRelation.INTERNAL);
        ResourceResponse resource = resourceFixture.createUniqueResource(prefix + "res-");
        relocationFixture.ensureStock(location.getId(), resource.getId(), STOCK_AMOUNT);
        return new LocationSeed(type.name(), type, location.getId(), resource.getId());
    }

    private LocationSeed seedFlyPointLocation(String prefix) {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario(prefix);
        ResourceResponse resource = resourceFixture.createUniqueResource(prefix + "res-");
        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.flyPoint().getId(),
                resource.getId(),
                ISSUE_AMOUNT);
        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);
        return new LocationSeed(
                UnitType.FLY_POINT.name(),
                UnitType.FLY_POINT,
                scenario.flyPoint().getId(),
                resource.getId());
    }

    private LocationSeed seedUnattachedCrewLocation(String prefix) {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario(prefix);
        ResourceResponse resource = resourceFixture.createUniqueResource(prefix + "res-");
        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resource.getId(),
                ISSUE_AMOUNT);
        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);
        return new LocationSeed(
                UnitType.CREW.name(),
                UnitType.CREW,
                scenario.crew().getId(),
                resource.getId());
    }

    private void seedIsolationPair() {
        StorageResponse storage = storageFixture.createChildStorage(
                parentId, PREFIX + "iso-stor-", UnitType.STORAGE, StorageRelation.INTERNAL);
        StorageResponse production = storageFixture.createChildStorage(
                parentId, PREFIX + "iso-prod-", UnitType.PRODUCTION, StorageRelation.INTERNAL);

        ResourceResponse resourceA = resourceFixture.createUniqueResource(PREFIX + "iso-a-");
        ResourceResponse resourceB = resourceFixture.createUniqueResource(PREFIX + "iso-b-");
        isolationResourceAId = resourceA.getId();
        isolationResourceBId = resourceB.getId();

        relocationFixture.ensureStock(storage.getId(), isolationResourceAId, STOCK_AMOUNT);
        relocationFixture.ensureStock(storage.getId(), isolationResourceBId, STOCK_AMOUNT);
        relocationFixture.ensureStock(production.getId(), isolationResourceAId, STOCK_AMOUNT);

        isolationStorageSeed = new LocationSeed(
                UnitType.STORAGE.name(), UnitType.STORAGE, storage.getId(), isolationResourceAId);
        isolationProductionSibling = new LocationSeed(
                UnitType.PRODUCTION.name(), UnitType.PRODUCTION, production.getId(), isolationResourceAId);
    }

    private void trackSession(long storageId) {
        if (!sessionStorageIds.contains(storageId)) {
            sessionStorageIds.add(storageId);
        }
    }
}
