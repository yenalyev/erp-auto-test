package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.RelocationState;
import com.erp.fixtures.DefectFixture;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.InventoryRequest;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-ORD AC-10: write-offs that would reduce on-hand below active order holds are rejected.
 * <p>
 * Arrange: onHand=10, ACTIVE booking=8 → free=2; attempt operations consuming &gt;2 → HTTP 400
 * with message containing «вільного залишку» or «заброньовано».
 */
@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Free stock regression")
public class OrderStockRegressionApiTest extends OrderApiTestBase {

    private static final double TOTAL_STOCK = 10.0;
    private static final double HOLD_QTY = 8.0;
    /** Exceeds free stock (2) after hold. */
    private static final double WRITE_OFF_QTY = 5.0;
    /** Within free stock after hold — enough to create a send that later cannot grow into the hold. */
    private static final double FREE_SEND_QTY = 2.0;
    /** Target on-hand below booked amount for inventory adjust. */
    private static final double ADJUST_BELOW_HOLD = 5.0;

    private DefectFixture defectFixture;
    private ProductionFixture productionFixture;
    private TechnologicalMapFixture techMapFixture;
    private ResourceFixture resourceFixture;
    private TechnologicalMapResponse productionTechMap;
    private Long productionOutputResourceId;
    private final List<Long> pendingSendIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupStockRegressionFixtures() {
        new com.erp.fixtures.StorageFixture(testContext, apiExecutor)
                .ensureProductionFeature(com.erp.enums.UserRole.ADMIN, gatheringStorageId);
        defectFixture = new DefectFixture(testContext, apiExecutor);
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        if (testContext.get(ContextKey.RELOCATION_SUPPLIER_ID) == null) {
            testContext.set(
                    ContextKey.RELOCATION_SUPPLIER_ID,
                    RelocationStockSeeder.resolveSupplierStorageId(apiExecutor, MANAGER));
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanupStockRegressionTechMap() {
        if (relocationFixture != null && requesterStorageId != null) {
            for (Long pendingSendId : pendingSendIds) {
                try {
                    relocationFixture.resolve(
                            MANAGER, pendingSendId, requesterStorageId, RelocationState.FINISHED);
                } catch (RuntimeException cleanupError) {
                    log.warn("Could not finish stock regression send {}", pendingSendId, cleanupError);
                }
            }
        }
        if (techMapFixture != null && productionTechMap != null && gatheringStorageId != null) {
            try {
                techMapFixture.deactivateTechMap(MANAGER, productionTechMap.getId(), gatheringStorageId);
            } catch (RuntimeException cleanupError) {
                log.warn("Could not deactivate stock regression tech map {}",
                        productionTechMap.getId(), cleanupError);
            }
        }
        if (resourceFixture != null && productionOutputResourceId != null) {
            try {
                Response response = resourceFixture.deactivate(MANAGER, productionOutputResourceId);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Could not deactivate stock regression output resource {}: HTTP {}: {}",
                            productionOutputResourceId, response.statusCode(), response.asString());
                }
            } catch (RuntimeException cleanupError) {
                log.warn("Could not deactivate stock regression output resource {}",
                        productionOutputResourceId, cleanupError);
            }
        }
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

        Long recipientId = requesterStorageId;
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
        pinGatheringOnHand(0);
        double stockAfterPin = inventoryFixture.getResourceStock(
                gatheringStorageId, resourceId, GATHERER);
        assertThat(stockAfterPin)
                .as("gathering must be empty so the receive under test is the only on-hand")
                .isLessThan(0.01);

        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse receive = relocationFixture.createExternalReceive(
                GATHERER, gatheringStorageId, resourceId, TOTAL_STOCK, batchNumber);
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
            Техкарта на gathering: 1 од. продукції списує WRITE_OFF_QTY ресурсу заявки.
            """)
    public void testProductionInputBelowHoldReturns400() {
        seedExactGatheringStock(TOTAL_STOCK);

        ResourceResponse output = resourceFixture.createUniqueResource("ord-reg5-out-");
        productionOutputResourceId = output.getId();
        TechnologicalMapRequest tmRequest = TechnologicalMapDataFactory.createProductionMapWithStorages(
                "ord-reg5",
                List.of(new ResourceUsageRequest(resourceId, WRITE_OFF_QTY)),
                List.of(new ResourceUsageRequest(output.getId(), 1.0)),
                Set.of(gatheringStorageId)).build();
        TechnologicalMapResponse techMap = techMapFixture.createTechMapWithRequest(MANAGER, tmRequest);
        productionTechMap = techMap;

        prepareInProgressWithActiveHold();

        Response response = productionFixture.tryCreateAs(
                MANAGER,
                gatheringStorageId,
                ProductionDataFactory.buildCreateRequest(techMap, 1.0));
        assertBookedStockBlocked(response);
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

        Long recipientId = requesterStorageId;
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
        RelocationResponse sent = afterRelease.as(RelocationResponse.class);
        if (sent.getState() == RelocationState.CREATED) {
            pendingSendIds.add(sent.getId());
            relocationFixture.resolve(
                    MANAGER, sent.getId(), requesterStorageId, RelocationState.FINISHED);
            pendingSendIds.remove(sent.getId());
        }
    }

    @Test(priority = 7)
    @TestCaseId("TC-ORD-REG-007")
    @Story("Send edit blocked by hold")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-ORD AC-10: після ACTIVE hold видача в межах вільного залишку створюється,
            але PUT /relocations/{id}/send зі збільшенням у заброньовану частину → 400.
            Кількість видачі і залишок відправника без змін; у отримувача до прийняття 0.
            """)
    public void testEditSendIntoBookedStockReturns400() {
        seedExactGatheringStock(TOTAL_STOCK);
        prepareInProgressWithActiveHold();

        Long recipientId = requesterStorageId;
        String marker = "TC-ORD-REG-007-" + System.currentTimeMillis();
        RelocationResponse sent = relocationFixture.createSendWithDescription(
                GATHERER, gatheringStorageId, recipientId, resourceId, FREE_SEND_QTY, marker);
        pendingSendIds.add(sent.getId());
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        Set<Long> tracked = trackedResource();
        ProductionStockAssertions.StockSnapshot senderBefore = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, GATHERER, tracked, "ДО edit into booked");
        ProductionStockAssertions.StockSnapshot recipientBefore = RelocationStockAssertions.capture(
                apiExecutor, recipientId, MANAGER, tracked, "ДО edit into booked recipient");

        RelocationOutputEditRequest edit = RelocationDataFactory.buildSendEditRequest(
                resourceId, WRITE_OFF_QTY, marker);
        if (sent.getVersion() != null) {
            edit = edit.toBuilder().version(sent.getVersion()).build();
        }
        Response response = relocationFixture.editSendRaw(
                MANAGER, sent.getId(), gatheringStorageId, edit);
        assertBookedStockBlocked(response);

        RelocationResponse still = relocationFixture.findInTransitByDescription(
                MANAGER, gatheringStorageId, marker);
        assertThat(still).as("CREATED send %s still on «В дорозі»", marker).isNotNull();
        assertThat(still.getItems().getFirst().getAmount())
                .as("rejected edit must not change send qty")
                .isEqualByComparingTo(BigDecimal.valueOf(FREE_SEND_QTY));
        RelocationStockAssertions.assertUnchanged(
                senderBefore,
                RelocationStockAssertions.capture(
                        apiExecutor, gatheringStorageId, GATHERER, tracked, "ПІСЛЯ rejected edit"),
                gatheringStorageId, resourceId, "sender stock after rejected booked edit");
        RelocationStockAssertions.assertUnchanged(
                recipientBefore,
                RelocationStockAssertions.capture(
                        apiExecutor, recipientId, MANAGER, tracked, "ПІСЛЯ rejected edit recipient"),
                recipientId, resourceId, "recipient stock after rejected booked edit");
        relocationFixture.resolve(
                MANAGER, sent.getId(), requesterStorageId, RelocationState.FINISHED);
        pendingSendIds.remove(sent.getId());
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
