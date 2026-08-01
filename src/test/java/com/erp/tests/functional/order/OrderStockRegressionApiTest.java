package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.fixtures.DefectFixture;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.InventoryRequest;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-WMS-010 AC-10: write-offs that would reduce on-hand below active order holds are rejected.
 * <p>
 * Arrange: onHand=10, ACTIVE booking=8 → free=2; attempt operations consuming &gt;2 → HTTP 400
 * with message containing «вільного залишку» or «заброньовано».
 */
@Slf4j
@Epic("Orders")
@Feature("REQ-WMS-010 Free stock regression")
public class OrderStockRegressionApiTest extends OrderApiTestBase {

    private static final double TOTAL_STOCK = 10.0;
    private static final double HOLD_QTY = 8.0;
    /** Exceeds free stock (2) after hold. */
    private static final double WRITE_OFF_QTY = 5.0;
    /** Target on-hand below booked amount for inventory adjust. */
    private static final double ADJUST_BELOW_HOLD = 5.0;

    private DefectFixture defectFixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupStockRegressionFixtures() {
        defectFixture = new DefectFixture(testContext, apiExecutor);
    }

    @Test(priority = 1)
    @TestCaseId("TC-ORD-REG-001")
    @Story("Inventory adjust blocked by hold")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після ACTIVE hold на gathering: інвентаризація (PUT /storages/{id}/inventory)
            зі зменшенням on-hand нижче заброньованої кількості → 400.
            """)
    public void testInventoryAdjustBelowHoldReturns400() {
        seedExactGatheringStock(TOTAL_STOCK);
        prepareInProgressWithActiveHold();

        inventoryFixture.openSession(gatheringStorageId);
        try {
            List<StorageItemResponse> items = inventoryFixture.listItems(gatheringStorageId, GATHERER);
            InventoryRequest request = com.erp.data.factories.inventory.InventoryDataFactory.mergeWithExisting(
                    items, java.util.Map.of(resourceId, ADJUST_BELOW_HOLD));
            Response response = inventoryFixture.conductInventoryRaw(
                    gatheringStorageId, GATHERER, request);
            assertBookedStockBlocked(response);
        } finally {
            inventoryFixture.closeSession(gatheringStorageId);
        }
    }

    @Test(priority = 2)
    @TestCaseId("TC-ORD-REG-002")
    @Story("Relocation send blocked by hold")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після ACTIVE hold: звичайна relocation send (POST /relocations/send) з gathering
            з кількістю, що перевищує вільний залишок → 400.
            """)
    public void testRelocationSendBelowFreeStockReturns400() {
        seedExactGatheringStock(TOTAL_STOCK);
        prepareInProgressWithActiveHold();

        Long recipientId = ConfigProvider.getOwner1StorageId();
        RelocationOutputRequest send = RelocationDataFactory.buildSendRequest(
                gatheringStorageId, recipientId, resourceId, WRITE_OFF_QTY);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, GATHERER, send);

        assertBookedStockBlocked(response);
    }

    @Test(priority = 3)
    @TestCaseId("TC-ORD-REG-003")
    @Story("Receive edit rollback blocked by hold")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Після ACTIVE hold: редагування external receive (PUT update receive) зі зменшенням
            кількості так, що on-hand опуститься нижче hold → 400.
            """)
    public void testReceiveEditBelowHoldReturns400() {
        inventoryFixture.removeResourceFromStorage(gatheringStorageId, resourceId, GATHERER);
        double stockAfterRemove = inventoryFixture.getResourceStock(
                gatheringStorageId, resourceId, GATHERER);
        if (stockAfterRemove > 0.01) {
            throw new SkipException(
                    "Cannot isolate receive record: gathering still has stock " + stockAfterRemove
                            + " after removeResourceFromStorage");
        }

        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse receive;
        try {
            receive = relocationFixture.createExternalReceive(
                    GATHERER, gatheringStorageId, resourceId, TOTAL_STOCK, batchNumber);
        } catch (RuntimeException e) {
            throw new SkipException(
                    "External receive fixture failed (staging product/env): " + e.getMessage(), e);
        }
        prepareInProgressWithActiveHold();

        RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                resourceId, ADJUST_BELOW_HOLD, batchNumber, "TC-ORD-REG-003 reduce below hold");
        Response response = relocationFixture.editReceiveRaw(
                MANAGER, receive.getId(), gatheringStorageId, edit);

        assertBookedStockBlocked(response);
    }

    @Test(priority = 4)
    @TestCaseId("TC-ORD-REG-004")
    @Story("Defect write-off blocked by hold")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після ACTIVE hold: створення storage FIFO defect, що списує більше вільного
            залишку → 400.
            """)
    public void testStorageDefectBelowFreeStockReturns400() {
        seedExactGatheringStock(TOTAL_STOCK);
        prepareInProgressWithActiveHold();

        DefectRequest defectRequest = DefectDataFactory.buildStorageFifoDefect(
                gatheringStorageId, resourceId, WRITE_OFF_QTY);
        Response response = defectFixture.createRaw(GATHERER, defectRequest);

        assertBookedStockBlocked(response);
    }

    @Test(priority = 5)
    @TestCaseId("TC-ORD-REG-005")
    @Story("Production input blocked by hold")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після ACTIVE hold: виробництво, що споживає input з gathering нижче hold → 400.
            Пропуск: ProductionFixture/tech map inputs не прив'язані до ORDER_RESOURCE_ID на gathering.
            """)
    public void testProductionInputBelowHoldReturns400() {
        throw new SkipException(
                "Production input regression requires tech map whose inputs match ORDER_RESOURCE_ID "
                        + "on gathering storage — not available in current OrderFixture/ProductionFixture setup");
    }

    @Test(priority = 6)
    @TestCaseId("TC-ORD-REG-006")
    @Story("Write-off succeeds after booking release")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Той самий relocation send, що падав через hold, після releaseBooking проходить
            (не повертає повідомлення про заброньований залишок).
            """)
    public void testRelocationSendSucceedsAfterBookingRelease() {
        seedExactGatheringStock(TOTAL_STOCK);
        BookedStockContext ctx = prepareInProgressWithActiveHold();

        Long recipientId = ConfigProvider.getOwner1StorageId();
        RelocationOutputRequest send = RelocationDataFactory.buildSendRequest(
                gatheringStorageId, recipientId, resourceId, WRITE_OFF_QTY);

        Response blocked = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, GATHERER, send);
        assertBookedStockBlocked(blocked);

        orderFixture.releaseBooking(
                MANAGER, ctx.order().getId(), ctx.booking().getId(), requesterStorageId);

        Response afterRelease = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_POST_SEND, GATHERER, send);
        assertThat(afterRelease.statusCode()).as("body=%s", afterRelease.body().asString()).isEqualTo(200);
        String body = afterRelease.body().asString();
        assertThat(body).doesNotContain("заброньовано");
        assertThat(body).doesNotContain("вільного залишку");
    }

    private record BookedStockContext(OrderResponse order, BookingResponse booking) {}

    private BookedStockContext prepareInProgressWithActiveHold() {
        OrderResponse order = prepareManagedInProgress(HOLD_QTY);
        resetGatheringOnHandKeepingOrders(TOTAL_STOCK);
        BookingResponse booking = orderFixture.book(
                MANAGER, order.getId(), requesterStorageId, resourceId, HOLD_QTY);
        return new BookedStockContext(order, booking);
    }

    private void seedExactGatheringStock(double targetAmount) {
        pinGatheringOnHand(targetAmount);
    }

    private static void assertBookedStockBlocked(Response response) {
        assertThat(response.statusCode()).as("body=%s", response.body().asString()).isEqualTo(400);
        String body = response.body().asString();
        assertThat(body).containsAnyOf("заброньовано", "вільного залишку", "Недостатньо");
    }
}
