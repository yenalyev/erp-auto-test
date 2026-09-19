package com.erp.tests.ui;

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
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
abstract class OrderUiTestBase extends BaseUITest {

    /** Fresh location head on the dynamically created requester UNIT. */
    protected static final UserRole REQUESTER = UserRole.UNIT_ANALYST;
    /** Existing foreign-unit user for negative visibility checks. */
    protected static final UserRole OUTSIDER = UserRole.OWNER_1;
    /** System admin for cross-module arrange steps. */
    protected static final UserRole MANAGER = UserRole.ADMIN;
    /** Fresh location head on the dynamically created gathering STORAGE. */
    protected static final UserRole GATHERER = UserRole.ORDER_GATHERER;

    protected OrderFixture orderFixture;
    protected RelocationFixture relocationFixture;
    protected StorageFixture storageFixture;

    protected long requesterStorageId;
    protected long gatheringStorageId;
    protected Long resourceId;
    protected String resourceName;
    protected String requesterStorageName;
    protected String gatheringStorageName;
    private Long primaryGatheringStorageId;
    private LocationProfileFixture locationProfiles;
    private UserFixture userFixture;
    private UserFixture.BusinessActor requesterActor;
    private UserFixture.BusinessActor gathererActor;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        orderFixture = new OrderFixture(testContext, apiExecutor);
        relocationFixture = orderFixture.relocation();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        orderFixture.ensureAvailabilityRootConfig(getDbHelper());
        long availabilityRootId = ConfigProvider.getOrderAvailabilityRootStorageId();
        if (availabilityRootId <= 0) {
            throw new IllegalStateException("Dynamic order gathering needs order.availability.root.storage.id");
        }
        StorageResponse createdRequester = locationProfiles.create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst();
        StorageResponse createdGathering = storageFixture.createOrderHubStorage(
                availabilityRootId, "ord-ui-gathering-");
        StorageResponse requester = storageFixture.getById(UserRole.ADMIN, createdRequester.getId());
        StorageResponse gathering = storageFixture.getById(UserRole.ADMIN, createdGathering.getId());
        requesterStorageId = requester.getId();
        gatheringStorageId = gathering.getId();
        primaryGatheringStorageId = gatheringStorageId;
        requesterStorageName = requester.getName();
        gatheringStorageName = gathering.getName();
        assertThat(requester.getType()).isEqualTo("UNIT");
        assertThat(gathering.getOrderHub()).isTrue();

        ResourceFixture resources = new ResourceFixture(testContext, apiExecutor);
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        ResourceResponse resource = resources.getPage(UserRole.ADMIN, true, null).stream()
                .filter(candidate -> candidate.getId() != null && candidate.getId() > 0
                        && candidate.getName() != null && !candidate.getName().isBlank()
                        && candidate.getUnit() != null && candidate.getCategory() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active catalog resource for UI order fixture"));
        resourceId = resource.getId();
        resourceName = resource.getName();
        relocationFixture.seedExactStock(requesterStorageId, resourceId, 1.0);
        new InventoryFixture(testContext, apiExecutor)
                .resetResourceStock(requesterStorageId, resourceId, 0.0, UserRole.ADMIN);
        testContext.set(ContextKey.ORDER_REQUESTER_STORAGE_ID, requesterStorageId);
        testContext.set(ContextKey.ORDER_GATHERING_STORAGE_ID, gatheringStorageId);
        testContext.set(ContextKey.ORDER_RESOURCE_ID, resourceId);
        testContext.set(ContextKey.SHARED_AVAILABLE_RESOURCES, List.of(resource));
        testContext.set(ContextKey.SHARED_RESOURCE_ID, resourceId);
        testContext.set(ContextKey.SHARED_RESOURCE, resource);

        requesterActor = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(requester));
        apiExecutor.setSessionForRole(REQUESTER, requesterActor.username(), requesterActor.password());
        gathererActor = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(gathering));
        apiExecutor.setSessionForRole(GATHERER, gathererActor.username(), gathererActor.password());
        UserMeResponse requesterMe = userFixture.getMe(REQUESTER);
        assertThat(requesterMe.hasOrderCreateOn(requesterStorageId)).isTrue();
        assertThat(resources.getPageForStorage(REQUESTER, requesterStorageId, resourceName))
                .extracting(ResourceResponse::getId).contains(resourceId);
        UserMeResponse gathererMe = userFixture.getMe(GATHERER);
        assertThat(gathererMe.getGrants().stream()
                .filter(grant -> grant.getStorage() != null
                        && gatheringStorageId == grant.getStorage().getId())
                .map(grant -> grant.getName()).toList())
                .containsExactly("Керівник локації");
    }

    @AfterClass(alwaysRun = true)
    public void cleanupDynamicOrderUiContext() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            log.info("Skipping dynamic order UI context cleanup because API cleanup is disabled");
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
            log.warn("Could not archive dynamic UI gathering STORAGE {}", primaryGatheringStorageId);
        }
        if (locationProfiles != null) {
            locationProfiles.cleanup();
        }
    }

    private void cleanupCreatedOrders() {
        if (orderFixture == null || requesterStorageId == 0) {
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
                        || requesterStorageId != order.getStorage().getId()
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
                log.warn("Could not clean dynamic UI order {}: {}", orderId, e.getMessage());
            }
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureGatheringStock() {
        requireOrderUiContext();
        gatheringStorageId = primaryGatheringStorageId;
        testContext.set(ContextKey.ORDER_GATHERING_STORAGE_ID, primaryGatheringStorageId);
        orderFixture.clearInProgressOrders(MANAGER, gatheringStorageId, requesterStorageId);
        relocationFixture.ensureStock(gatheringStorageId, resourceId, 200.0);
    }

    protected void injectRoleSession(UserRole role, long selectedStorageId) {
        injectRoleSession(role, Long.valueOf(selectedStorageId));
    }

    protected void injectRoleSession(UserRole role, Long selectedStorageId) {
        requireOrderUiContext();
        Map<String, String> cookies = orderRoleSession(role);
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        if (selectedStorageId == null) {
            browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
        } else {
            browserContext.addInitScript(
                    "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        }
    }

    protected Map<String, String> orderRoleSession(UserRole role) {
        requireOrderUiContext();
        if (role == REQUESTER && requesterActor != null) {
            return authService.getSessionForUser(requesterActor.username(), requesterActor.password());
        }
        if (role == GATHERER && gathererActor != null) {
            return authService.getSessionForUser(gathererActor.username(), gathererActor.password());
        }
        return cachedSessionCookies(role);
    }

    protected void requireOrderUiContext() {
        if (primaryGatheringStorageId == null || requesterStorageId <= 0 || resourceId == null
                || requesterActor == null || gathererActor == null) {
            throw new SkipException("Dynamic order UI context was not created; see @BeforeClass failure");
        }
    }

    protected void reopenPageWithSession(UserRole role, long selectedStorageId) {
        reopenPageWithSession(role, Long.valueOf(selectedStorageId));
    }

    protected void reopenPageWithSession(UserRole role, Long selectedStorageId) {
        if (page != null) {
            try {
                page.close();
            } catch (Exception e) {
                log.debug("Could not close page before session reinject: {}", e.getMessage());
            }
        }
        injectRoleSession(role, selectedStorageId);
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }

    /** Dynamic requester location-head session — create / view own orders. */
    protected void loginAsOwner() {
        reopenPageWithSession(REQUESTER, requesterStorageId);
    }

    /** Admin session on requester storage — manage lifecycle (take-to-work, booking, send). */
    protected void loginAsAdmin() {
        reopenPageWithSession(MANAGER, requesterStorageId);
    }

    /** Admin session in «Всі локації» workspace. */
    protected void loginAsAdminAllLocations() {
        reopenPageWithSession(MANAGER, null);
    }

    protected void syncGatheringFromContext() {
        Long resolved = testContext.get(ContextKey.ORDER_GATHERING_STORAGE_ID);
        if (resolved == null || resolved == gatheringStorageId) {
            return;
        }
        gatheringStorageId = resolved;
        gatheringStorageName = storageFixture.getNames(UserRole.ADMIN, true, null, gatheringStorageId)
                .getFirst()
                .getName();
        relocationFixture.ensureStock(gatheringStorageId, resourceId, 200.0);
    }

    protected OrderResponse prepareManagedInProgressUi() {
        OrderResponse order = orderFixture.prepareInProgressWithGathering(REQUESTER, MANAGER);
        syncGatheringFromContext();
        return order;
    }
}
