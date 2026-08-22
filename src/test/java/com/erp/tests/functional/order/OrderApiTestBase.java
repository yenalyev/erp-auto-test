package com.erp.tests.functional.order;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.OrderFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAmountResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
abstract class OrderApiTestBase extends BaseFunctionalTest {

    protected static final double DEFAULT_ORDER_QTY = 5.0;
    protected static final double DEFAULT_SEED_STOCK = 200.0;

    /** Підрозділ 3bat — create/see own orders ({@code order::create} on UNIT). */
    protected static final UserRole REQUESTER = UserRole.UNIT_ANALYST;
    /** alkatras — other unit; must not see 3bat orders. */
    protected static final UserRole OUTSIDER = UserRole.OWNER_1;
    /** Administrator — order::manage lifecycle (take-to-work, book, ship). */
    protected static final UserRole MANAGER = UserRole.ADMIN;
    /** Owner of gathering storage — prepare bookings (order::update on gathering). */
    protected static final UserRole GATHERER = UserRole.ORDER_GATHERER;

    protected OrderFixture orderFixture;
    protected RelocationFixture relocationFixture;
    protected InventoryFixture inventoryFixture;
    protected Long requesterStorageId;
    protected Long gatheringStorageId;
    protected Long resourceId;
    protected String resourceName;
    protected List<ResourceResponse> sharedResources;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupOrderApiTests() {
        orderFixture = new OrderFixture(testContext, apiExecutor);
        relocationFixture = orderFixture.relocation();
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        orderFixture.ensureAvailabilityRootConfig(getDbHelper());
        orderFixture.prepareContext();

        requesterStorageId = testContext.get(ContextKey.ORDER_REQUESTER_STORAGE_ID);
        gatheringStorageId = ConfigProvider.getOrderGatheringStorageId();
        resourceId = testContext.get(ContextKey.ORDER_RESOURCE_ID);
        sharedResources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceName = sharedResources.stream()
                .filter(r -> resourceId.equals(r.getId()))
                .map(ResourceResponse::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Resource name not found for id " + resourceId));

        SchemaRegistry.logSchemaCoverage();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureGatheringStock() {
        clearSharedGatheringHolds();
        relocationFixture.ensureStock(gatheringStorageId, resourceId, DEFAULT_SEED_STOCK);
    }

    @AfterMethod(alwaysRun = true)
    public void releaseSharedGatheringHolds() {
        clearSharedGatheringHolds();
    }

    protected void clearSharedGatheringHolds() {
        if (orderFixture == null || requesterStorageId == null || gatheringStorageId == null) {
            return;
        }
        orderFixture.clearInProgressOrders(MANAGER, gatheringStorageId, requesterStorageId);
    }

    /**
     * Clears ACTIVE holds on gathering and pins on-hand so free stock matches {@code onHandTarget}.
     * Do <b>not</b> call after creating the order under test — cancel would wipe it.
     */
    protected void pinGatheringOnHand(double onHandTarget) {
        Double booked = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            clearSharedGatheringHolds();
            booked = readGatheringBookedAmount();
            if (booked == null || booked < 0.01) {
                booked = 0.0;
                break;
            }
            log.warn("Gathering {} still has bookedAmount={} (attempt {})",
                    gatheringStorageId, booked, attempt + 1);
        }
        if (booked != null && booked >= 0.01) {
            throw new SkipException(
                    "Cannot clear ACTIVE holds on gathering " + gatheringStorageId
                            + " resource " + resourceId + ": bookedAmount=" + booked);
        }
        inventoryFixture.resetResourceStock(gatheringStorageId, resourceId, onHandTarget, MANAGER);
    }

    /**
     * Pins gathering on-hand without cancelling IN_PROGRESS orders (safe after prepareManagedInProgress).
     */
    protected void resetGatheringOnHandKeepingOrders(double onHandTarget) {
        Double booked = readGatheringBookedAmount();
        if (booked != null && booked >= 0.01) {
            throw new SkipException(
                    "Gathering " + gatheringStorageId + " has bookedAmount=" + booked
                            + " before pin; clear holds in @BeforeMethod / previous tests");
        }
        inventoryFixture.resetResourceStock(gatheringStorageId, resourceId, onHandTarget, MANAGER);
    }

    protected Double readGatheringBookedAmount() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET,
                MANAGER,
                Map.of(
                        "locations", gatheringStorageId,
                        "resourceIds", resourceId,
                        "size", 5));
        if (response.statusCode() != 200) {
            log.warn("Could not read bookedAmount for gathering {}: status={}",
                    gatheringStorageId, response.statusCode());
            return null;
        }
        List<MultiLocationStorageItemResponse> content =
                response.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        if (content == null || content.isEmpty() || content.getFirst().getLocations() == null) {
            return 0.0;
        }
        return content.getFirst().getLocations().stream()
                .filter(loc -> loc.getStorage() != null
                        && gatheringStorageId.equals(loc.getStorage().getId()))
                .map(StorageAmountResponse::getBookedAmount)
                .filter(v -> v != null)
                .findFirst()
                .orElse(0.0);
    }

    protected Set<Long> trackedResource() {
        return Set.of(resourceId);
    }

    protected Long secondResourceId() {
        if (sharedResources == null || sharedResources.size() < 2) {
            throw new IllegalStateException("Need at least 2 shared resources for this test");
        }
        return sharedResources.get(1).getId();
    }

    /** Create as Owner, Admin takes to work + sets gathering (from API candidates in scope). */
    protected OrderResponse prepareManagedInProgress() {
        return prepareManagedInProgress(DEFAULT_ORDER_QTY);
    }

    /** Same as {@link #prepareManagedInProgress()} with explicit order-line quantity. */
    protected OrderResponse prepareManagedInProgress(double quantity) {
        OrderResponse order = orderFixture.prepareInProgressWithGathering(REQUESTER, MANAGER, quantity);
        Long resolvedGathering = testContext.get(ContextKey.ORDER_GATHERING_STORAGE_ID);
        if (resolvedGathering != null) {
            gatheringStorageId = resolvedGathering;
        }
        return order;
    }
}
