package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
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
import com.erp.fixtures.UserFixture;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.pages.OrderListPage;
import com.erp.tests.ui.BaseUITest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract smoke for isolated order locations and their location-scoped actors. */
@Slf4j
@Epic("Orders")
@Feature("Dynamic order fixtures")
public class OrderDynamicFixtureSmokeTest extends BaseUITest {

    private static final UserRole REQUESTER = UserRole.UNIT_ANALYST;
    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;
    private static final UserRole GATHERER = UserRole.ORDER_ISOLATED_GATHERER;

    @Test
    @TestCaseId(value = "TC-ORD-FIXTURE-001", roles = {
            BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.ORDER_ADMIN},
            locationProfiles = LocationProfile.BATTALION_UNIT)
    public void dynamicUnitAndGatheringStorageSupportOrderWorkflow() {
        LocationProfileFixture locations = new LocationProfileFixture(testContext, apiExecutor);
        StorageFixture storages = new StorageFixture(testContext, apiExecutor);
        UserFixture users = new UserFixture(testContext, apiExecutor);
        ResourceFixture resources = new ResourceFixture(testContext, apiExecutor);
        RelocationFixture relocations = new RelocationFixture(testContext, apiExecutor);
        InventoryFixture inventory = new InventoryFixture(testContext, apiExecutor);
        OrderFixture orders = new OrderFixture(testContext, apiExecutor);

        StorageResponse requester = null;
        StorageResponse gathering = null;
        ResourceResponse resource = null;
        Long orderId = null;
        Long bookingId = null;
        try {
            long rootId = ConfigProvider.getOrderAvailabilityRootStorageId();
            assertThat(rootId).as("Configured order availability root").isPositive();

            requester = locations.create(LocationProfile.BATTALION_UNIT, 1).locations().getFirst();
            gathering = storages.createOrderHubStorage(rootId, "ord-smoke-gathering-");
            assertThat(storages.getById(UserRole.ADMIN, requester.getId()).getId())
                    .isEqualTo(requester.getId());
            StorageResponse persistedGathering = storages.getById(UserRole.ADMIN, gathering.getId());
            assertThat(persistedGathering.getParent()).isNotNull();
            assertThat(persistedGathering.getParent().getId()).isEqualTo(rootId);
            Long activeGatheringId = gathering.getId();

            UserFixture.BusinessActor requesterActor = users.createBusinessActor(
                    getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(requester));
            apiExecutor.setSessionForRole(REQUESTER, requesterActor.username(), requesterActor.password());
            UserFixture.BusinessActor orderAdminActor = users.createBusinessActor(
                    getPlaywrightSessionProvider(), BusinessRole.ORDER_ADMIN, List.of(requester));
            apiExecutor.setSessionForRole(ORDER_ADMIN, orderAdminActor.username(), orderAdminActor.password());
            UserFixture.BusinessActor gathererActor = users.createBusinessActor(
                    getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(gathering));
            apiExecutor.setSessionForRole(GATHERER, gathererActor.username(), gathererActor.password());

            UserMeResponse requesterMe = users.getMe(REQUESTER);
            assertThat(requesterMe.hasOrderCreateOn(requester.getId())).isTrue();
            UserMeResponse adminMe = users.getMe(ORDER_ADMIN);
            assertThat(adminMe.getGrants()).extracting(grant -> grant.getName())
                    .contains("Керівник локації", "Замовлення: адміністратор");
            UserMeResponse gathererMe = users.getMe(GATHERER);
            assertThat(gathererMe.getGrants().stream()
                    .filter(grant -> grant.getStorage() != null
                            && activeGatheringId.equals(grant.getStorage().getId()))
                    .map(grant -> grant.getName()).toList())
                    .containsExactly("Керівник локації");

            injectSessionCookies(
                    authService.getSessionForUser(requesterActor.username(), requesterActor.password()),
                    sessionCookieDomain());
            browserContext.addInitScript(
                    "localStorage.setItem('selectedStorageId', '" + requester.getId() + "');");
            OrderListPage orderPage = new OrderListPage(page).open();
            assertThat(orderPage.isCreateButtonVisible()).as("Dynamic requester create button").isTrue();
            assertThat(orderPage.isCreateDisabled()).isFalse();

            resources.fetchSharedUnit(1);
            resources.fetchSharedResourceCategory();
            resource = resources.createUniqueResource("ord-smoke-resource-");
            relocations.seedExactStock(requester.getId(), resource.getId(), 1.0);
            inventory.resetResourceStock(requester.getId(), resource.getId(), 0.0, UserRole.ADMIN);
            assertThat(resources.getPageForStorage(REQUESTER, requester.getId(), resource.getName()))
                    .extracting(ResourceResponse::getId).contains(resource.getId());
            relocations.seedExactStock(gathering.getId(), resource.getId(), 5.0);

            OrderResponse created = orders.createOrder(REQUESTER, requester.getId(), resource.getId(), 5.0);
            orderId = created.getId();
            assertThat(created.getState()).isEqualTo(OrderState.NEW);
            orders.takeToWork(ORDER_ADMIN, orderId, requester.getId());
            assertThat(orders.getGatheringLocations(ORDER_ADMIN, orderId, requester.getId()))
                    .extracting(location -> location.getId()).contains(gathering.getId());
            orders.setGathering(ORDER_ADMIN, orderId, requester.getId(), gathering.getId());
            BookingResponse booking = orders.book(ORDER_ADMIN, orderId, requester.getId(), resource.getId(), 2.0);
            bookingId = booking.getId();
            assertThat(orders.setPrepared(GATHERER, orderId, bookingId, true).isPrepared()).isTrue();
        } finally {
            Long requesterId = requester == null ? null : requester.getId();
            Long gatheringId = gathering == null ? null : gathering.getId();
            Long createdOrderId = orderId;
            Long createdBookingId = bookingId;
            if (createdBookingId != null && createdOrderId != null && requesterId != null) {
                cleanupQuietly("release booking", () -> orders.releaseBooking(
                        ORDER_ADMIN, createdOrderId, createdBookingId, requesterId));
            }
            if (createdOrderId != null && requesterId != null) {
                cleanupQuietly("cancel order", () -> orders.cancel(
                        ORDER_ADMIN, createdOrderId, requesterId));
            }
            for (UserRole role : List.of(REQUESTER, ORDER_ADMIN, GATHERER)) {
                apiExecutor.evictSessionForRole(role);
            }
            cleanupQuietly("deactivate users", users::deactivateTrackedUsers);
            if (gatheringId != null) {
                cleanupQuietly("archive gathering", () -> {
                    if (!storages.archiveStorage(UserRole.ADMIN, gatheringId)) {
                        log.warn("Gathering storage {} was retained for cleanup retry", gatheringId);
                    }
                });
            }
            cleanupQuietly("archive requester", locations::cleanup);
            if (resource != null) {
                Long resourceId = resource.getId();
                cleanupQuietly("deactivate resource", () -> resources.deactivate(UserRole.ADMIN, resourceId));
            }
        }
    }

    private static void cleanupQuietly(String operation, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Exception e) {
            log.warn("Order dynamic smoke cleanup failed at {}: {}", operation, e.getMessage());
        }
    }
}
