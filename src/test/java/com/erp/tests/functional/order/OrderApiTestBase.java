package com.erp.tests.functional.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.BookingState;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.OrderState;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.OrderFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.TestArtifactRegistry;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAmountResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
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

    /** Fresh location head on the dynamically created requester location. */
    protected static final UserRole REQUESTER = UserRole.UNIT_ANALYST;
    /** Existing foreign-unit user for negative visibility checks. */
    protected static final UserRole OUTSIDER = UserRole.OWNER_1;
    /** System admin remains the technical manager for cross-module API scenarios. */
    protected static final UserRole MANAGER = UserRole.ADMIN;
    /** Fresh location head on the dynamically created gathering STORAGE. */
    protected static final UserRole GATHERER = UserRole.ORDER_GATHERER;

    protected OrderFixture orderFixture;
    protected RelocationFixture relocationFixture;
    protected InventoryFixture inventoryFixture;
    protected Long requesterStorageId;
    protected Long gatheringStorageId;
    private Long primaryGatheringStorageId;
    protected Long resourceId;
    protected String resourceName;
    protected List<ResourceResponse> sharedResources;
    private LocationProfileFixture locationProfiles;
    private StorageFixture storageFixture;
    private UserFixture userFixture;
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupOrderApiTests() {
        orderFixture = new OrderFixture(testContext, apiExecutor);
        relocationFixture = orderFixture.relocation();
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        orderFixture.ensureAvailabilityRootConfig(getDbHelper());

        long availabilityRootId = ConfigProvider.getOrderAvailabilityRootStorageId();
        if (availabilityRootId <= 0) {
            throw new IllegalStateException("Dynamic order gathering needs order.availability.root.storage.id");
        }
        StorageResponse createdRequester = locationProfiles.create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst();
        StorageResponse createdGathering = storageFixture.createOrderHubStorage(
                availabilityRootId, "ord-api-gathering-");
        StorageResponse requester = storageFixture.getById(UserRole.ADMIN, createdRequester.getId());
        StorageResponse gathering = storageFixture.getById(UserRole.ADMIN, createdGathering.getId());
        requesterStorageId = requester.getId();
        gatheringStorageId = gathering.getId();
        primaryGatheringStorageId = gatheringStorageId;

        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();
        sharedResources = resourceFixture.getPage(UserRole.ADMIN, true, null).stream()
                .filter(resource -> resource.getId() != null && resource.getId() > 0
                        && resource.getName() != null && !resource.getName().isBlank()
                        && resource.getUnit() != null && resource.getCategory() != null)
                .limit(requiredResourceCount())
                .toList();
        if (sharedResources.size() < requiredResourceCount()) {
            throw new IllegalStateException("Need " + requiredResourceCount()
                    + " active catalog resources, found " + sharedResources.size());
        }
        for (ResourceResponse resource : sharedResources) {
            // A zero inventory row makes the resource selectable on the new location.
            relocationFixture.seedExactStock(requesterStorageId, resource.getId(), 1.0);
            inventoryFixture.resetResourceStock(requesterStorageId, resource.getId(), 0.0, UserRole.ADMIN);
        }
        resourceId = sharedResources.getFirst().getId();
        resourceName = sharedResources.getFirst().getName();
        testContext.set(ContextKey.ORDER_REQUESTER_STORAGE_ID, requesterStorageId);
        testContext.set(ContextKey.ORDER_GATHERING_STORAGE_ID, gatheringStorageId);
        testContext.set(ContextKey.ORDER_RESOURCE_ID, resourceId);
        testContext.set(ContextKey.SHARED_AVAILABLE_RESOURCES, sharedResources);
        testContext.set(ContextKey.SHARED_RESOURCE_ID, resourceId);
        testContext.set(ContextKey.SHARED_RESOURCE, sharedResources.getFirst());

        UserFixture.BusinessActor requesterActor = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(requester));
        apiExecutor.setSessionForRole(REQUESTER, requesterActor.username(), requesterActor.password());
        UserFixture.BusinessActor gathererActor = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(gathering));
        apiExecutor.setSessionForRole(GATHERER, gathererActor.username(), gathererActor.password());
        UserMeResponse requesterMe = userFixture.getMe(REQUESTER);
        assertThat(requesterMe.hasOrderCreateOn(requesterStorageId)).isTrue();
        assertThat(resourceFixture.getPageForStorage(REQUESTER, requesterStorageId, resourceName))
                .extracting(ResourceResponse::getId).contains(resourceId);
        UserMeResponse gathererMe = userFixture.getMe(GATHERER);
        assertThat(gathererMe.getGrants().stream()
                .filter(grant -> grant.getStorage() != null
                        && gatheringStorageId.equals(grant.getStorage().getId()))
                .map(grant -> grant.getName()).toList())
                .containsExactly("Керівник локації");

        SchemaRegistry.logSchemaCoverage();
    }

    /** Most order cases need one resource; only multi-line cases request more. */
    protected int requiredResourceCount() {
        return 1;
    }

    @AfterClass(alwaysRun = true)
    public void cleanupDynamicOrderContext() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            log.info("Skipping dynamic order context cleanup because API cleanup is disabled");
            return;
        }
        cleanupCreatedOrders();
        apiExecutor.evictSessionForRole(REQUESTER);
        apiExecutor.evictSessionForRole(GATHERER);
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
        if (storageFixture != null && primaryGatheringStorageId != null
                && !storageFixture.archiveStorage(UserRole.ADMIN, primaryGatheringStorageId)) {
            log.warn("Could not archive dynamic gathering STORAGE {}", primaryGatheringStorageId);
        }
        if (locationProfiles != null) {
            locationProfiles.cleanup();
        }
    }

    private void cleanupCreatedOrders() {
        if (orderFixture == null || requesterStorageId == null) {
            return;
        }
        for (Long orderId : apiExecutor.getArtifactRegistry().pendingIds(TestArtifactRegistry.Kind.ORDER)) {
            try {
                Response response = apiExecutor.execute(
                        ApiEndpointDefinition.ORDER_GET_BY_ID, MANAGER, null, orderId);
                if (response.statusCode() != 200) {
                    continue;
                }
                OrderResponse order = response.as(OrderResponse.class);
                if (order.getStorage() == null
                        || !requesterStorageId.equals(order.getStorage().getId())
                        || order.getState() == OrderState.DONE
                        || order.getState() == OrderState.CANCELLED) {
                    continue;
                }
                for (BookingResponse booking : orderFixture.getBookings(MANAGER, orderId)) {
                    if (booking.getId() != null && booking.getState() == BookingState.ACTIVE) {
                        orderFixture.releaseBooking(MANAGER, orderId, booking.getId(), requesterStorageId);
                    }
                }
                orderFixture.cancel(MANAGER, orderId, requesterStorageId);
            } catch (RuntimeException e) {
                log.warn("Could not clean dynamic order {}: {}", orderId, e.getMessage());
            }
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureGatheringStock() {
        if (primaryGatheringStorageId == null || requesterStorageId == null || resourceId == null) {
            throw new SkipException("Dynamic order API context was not created; see @BeforeClass failure");
        }
        // Some cases deliberately switch the gathering location. Keep that choice local to the case:
        // the shared gatherer actor only has a grant on the original STORAGE.
        gatheringStorageId = primaryGatheringStorageId;
        testContext.set(ContextKey.ORDER_GATHERING_STORAGE_ID, primaryGatheringStorageId);
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
            throw new AssertionError(
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
            throw new AssertionError(
                    "Gathering " + gatheringStorageId + " has bookedAmount=" + booked
                            + " before pin; clear holds in @BeforeMethod / previous tests");
        }
        inventoryFixture.resetResourceStock(gatheringStorageId, resourceId, onHandTarget, MANAGER);
    }

    protected Double readGatheringBookedAmount() {
        return readBookedAmount(gatheringStorageId);
    }

    protected Double readBookedAmount(Long storageId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET,
                MANAGER,
                Map.of(
                        "locations", storageId,
                        "resourceIds", resourceId,
                        "size", 5));
        assertThat(response.statusCode()).as("Read bookedAmount on storage " + storageId).isEqualTo(200);
        List<MultiLocationStorageItemResponse> content =
                response.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        if (content == null || content.isEmpty() || content.getFirst().getLocations() == null) {
            return 0.0;
        }
        return content.getFirst().getLocations().stream()
                .filter(loc -> loc.getStorage() != null
                        && storageId.equals(loc.getStorage().getId()))
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
