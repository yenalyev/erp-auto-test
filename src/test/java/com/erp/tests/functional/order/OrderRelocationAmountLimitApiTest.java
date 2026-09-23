package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.OrderRelocationTaskState;
import com.erp.enums.UserRole;
import com.erp.fixtures.OrderRelocationTaskFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.OrderRelocationTaskResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Orders")
@Feature("Замовлення: запити на переміщення")
public class OrderRelocationAmountLimitApiTest extends OrderApiTestBase {

    private static final UserRole ORDER_ADMIN = UserRole.ORDER_ADMIN;

    private OrderRelocationTaskFixture relocationTaskFixture;
    private StorageFixture storageFixture;
    private UserFixture userFixture;
    private StorageResponse sourceStorage;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupRelocationAmountLimit() {
        relocationTaskFixture = new OrderRelocationTaskFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        StorageResponse requesterStorage = storageFixture.getById(UserRole.ADMIN, requesterStorageId);
        UserFixture.BusinessActor orderAdmin = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.ORDER_ADMIN, List.of(requesterStorage));
        apiExecutor.setSessionForRole(ORDER_ADMIN, orderAdmin.username(), orderAdmin.password());
        long availabilityRoot = ConfigProvider.getOrderAvailabilityRootStorageId();
        sourceStorage = storageFixture.createChildStorage(availabilityRoot, "ord-limit-source-");
        relocationFixture.ensureStock(sourceStorage.getId(), resourceId, DEFAULT_ORDER_QTY);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupRelocationAmountLimit() {
        apiExecutor.evictSessionForRole(ORDER_ADMIN);
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
        if (storageFixture != null && sourceStorage != null) {
            storageFixture.archiveStorage(UserRole.ADMIN, sourceStorage.getId());
        }
    }

    @Test
    @TestCaseId(value = "TC-ORD-ADMIN-005", roles = BusinessRole.ORDER_ADMIN)
    @Story("Relocation task amount limit and cancellation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Максимальний запит дорівнює кількості в замовленні незалежно від залишку на зборі; скасування NEW-запиту звільняє резерв джерела.")
    public void relocationTaskUsesOrderQuantityLimitAndCancellationReleasesReservation() {
        double orderQuantity = 5.0;
        double gatheringStock = 2.0;
        inventoryFixture.resetResourceStock(
                gatheringStorageId, resourceId, gatheringStock, UserRole.ADMIN);
        OrderResponse order = orderFixture.createOrder(
                REQUESTER, requesterStorageId, resourceId, orderQuantity);
        orderFixture.takeToWork(ORDER_ADMIN, order.getId(), requesterStorageId);
        orderFixture.setGathering(ORDER_ADMIN, order.getId(), requesterStorageId, gatheringStorageId);
        relocationFixture.ensureStock(sourceStorage.getId(), resourceId, orderQuantity);
        long orderLineId = order.getLines().getFirst().getId();

        Response exceedsOrderQuantity = relocationTaskFixture.createRaw(
                ORDER_ADMIN,
                order.getId(),
                requesterStorageId,
                relocationTaskFixture.request(
                        sourceStorage.getId(), orderLineId, orderQuantity + 1));
        assertThat(exceedsOrderQuantity.statusCode()).isEqualTo(400);
        assertThat(relocationTaskFixture.getForOrder(ORDER_ADMIN, order.getId()))
                .as("An over-limit request must not create a relocation task")
                .isEmpty();
        assertThat(readBookedAmount(sourceStorage.getId()))
                .as("An over-limit request must not reserve source stock")
                .isZero();

        OrderRelocationTaskResponse task = relocationTaskFixture.create(
                ORDER_ADMIN,
                order.getId(),
                requesterStorageId,
                relocationTaskFixture.request(
                        sourceStorage.getId(), orderLineId, orderQuantity));
        assertThat(readBookedAmount(sourceStorage.getId())).isEqualTo(orderQuantity);

        OrderRelocationTaskResponse cancelled = relocationTaskFixture.cancel(
                ORDER_ADMIN, order.getId(), task.getId(), requesterStorageId);
        assertThat(cancelled.getState()).isEqualTo(OrderRelocationTaskState.CANCELLED);
        assertThat(readBookedAmount(sourceStorage.getId())).isZero();
    }
}
