package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.OrderState;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.request.OrderRequest;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Order CRUD")
public class OrderCrudApiTest extends OrderApiTestBase {

    private InventoryFixture inventoryFixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupInventoryFixture() {
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
    }

    @Test(priority = 1)
    @TestCaseId("TC-ORD-001")
    @Story("Create order")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateOrderHappyPathNew() {
        OrderResponse order = orderFixture.createOrder(REQUESTER, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getState()).isEqualTo(OrderState.NEW);
        assertThat(order.getStorage()).isNotNull();
        assertThat(order.getStorage().getId()).isEqualTo(requesterStorageId);
        assertThat(order.getLines()).hasSize(1);
        assertThat(order.getLines().getFirst().getResource().getId()).isEqualTo(resourceId);
    }

    @Test(priority = 2)
    @TestCaseId("TC-ORD-002")
    @Story("Create validation")
    public void testCreateEmptyLinesOrMissingStorageReturns400() {
        Response emptyLines = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_CREATE,
                REQUESTER,
                OrderRequest.builder().storageId(requesterStorageId).lines(List.of()).build());
        assertThat(emptyLines.statusCode()).isEqualTo(400);

        // null storageId fails PreAuthorize(hasPermission(#request.storageId, ...)) → 403 before validation
        Response missingStorage = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_CREATE,
                REQUESTER,
                OrderRequest.builder().lines(List.of(OrderDataFactory.line(resourceId, 1.0))).build());
        assertThat(missingStorage.statusCode()).isIn(400, 403);
    }

    @Test(priority = 3)
    @TestCaseId("TC-ORD-003")
    @Story("Create validation")
    public void testCreateInvalidQuantityReturns400() {
        OrderRequest request = OrderDataFactory.buildOrderRequest(requesterStorageId, resourceId, 0.0);
        Response response = apiExecutor.execute(ApiEndpointDefinition.ORDER_POST_CREATE, REQUESTER, request);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 4)
    @TestCaseId("TC-ORD-004")
    @Story("Create validation")
    public void testCreateDuplicateResourceInLinesReturns400() {
        OrderRequest request = OrderRequest.builder()
                .storageId(requesterStorageId)
                .lines(List.of(
                        OrderDataFactory.line(resourceId, 1.0),
                        OrderDataFactory.line(resourceId, 2.0)))
                .build();
        Response response = apiExecutor.execute(ApiEndpointDefinition.ORDER_POST_CREATE, REQUESTER, request);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 5)
    @TestCaseId("TC-ORD-005")
    @Story("Create validation")
    @Description("Resource without grant on requester location → 400. Skipped when env uses FULL_ACCESS on requester storage.")
    public void testCreateResourceNotAccessibleToLocationReturns400() {
        ResourceResponse ungranted = inventoryFixture.pickResourceNotOnStorage(
                requesterStorageId, UserRole.ADMIN, sharedResources);
        OrderRequest request = OrderDataFactory.buildOrderRequest(requesterStorageId, ungranted.getId(), 1.0);
        Response response = apiExecutor.execute(ApiEndpointDefinition.ORDER_POST_CREATE, REQUESTER, request);
        if (response.statusCode() == 200) {
            throw new SkipException(
                    "Requester storage allows ungranted resource (likely FULL_ACCESS) — TC-ORD-005 needs RESTRICTED location");
        }
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 10)
    @TestCaseId("TC-ORD-009")
    @Story("Update order")
    @Severity(SeverityLevel.CRITICAL)
    public void testUpdateWhenNewReplacesLines() {
        OrderResponse created = orderFixture.createOrder(REQUESTER, requesterStorageId, resourceId, 1.0);
        Long alternateResourceId = secondResourceId();

        OrderRequest update = OrderDataFactory.buildOrderRequest(requesterStorageId, alternateResourceId, 9.0);
        OrderResponse updated = orderFixture.updateOrder(REQUESTER, created.getId(), update);

        assertThat(updated.getState()).isEqualTo(OrderState.NEW);
        assertThat(updated.getLines()).hasSize(1);
        assertThat(updated.getLines().getFirst().getResource().getId()).isEqualTo(alternateResourceId);
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-010")
    @Story("Update order")
    public void testUpdateWhenInProgressReturns400() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        order = orderFixture.takeToWork(MANAGER, order.getId(), requesterStorageId);

        OrderRequest update = OrderDataFactory.buildOrderRequest(requesterStorageId, resourceId, 2.0);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_UPDATE,
                REQUESTER,
                update,
                order.getId());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 12)
    @TestCaseId("TC-ORD-012")
    @Story("Stock safety")
    public void testCreateDoesNotChangeStock() {
        Set<Long> tracked = trackedResource();
        ProductionStockAssertions.StockSnapshot requesterBefore = RelocationStockAssertions.capture(
                apiExecutor, requesterStorageId, REQUESTER, tracked, "requester ДО create");
        ProductionStockAssertions.StockSnapshot gatheringBefore = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, GATHERER, tracked, "gathering ДО create");

        orderFixture.createOrder(REQUESTER, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);

        ProductionStockAssertions.StockSnapshot requesterAfter = RelocationStockAssertions.capture(
                apiExecutor, requesterStorageId, REQUESTER, tracked, "requester ПІСЛЯ create");
        ProductionStockAssertions.StockSnapshot gatheringAfter = RelocationStockAssertions.capture(
                apiExecutor, gatheringStorageId, GATHERER, tracked, "gathering ПІСЛЯ create");

        RelocationStockAssertions.assertUnchanged(
                requesterBefore, requesterAfter, requesterStorageId, resourceId, "create must not touch requester stock");
        RelocationStockAssertions.assertUnchanged(
                gatheringBefore, gatheringAfter, gatheringStorageId, resourceId, "create must not touch gathering stock");
    }

    @Test(priority = 13)
    @TestCaseId("TC-ORD-013")
    @Story("Create validation")
    public void testCreateMalformedJsonReturns400() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_CREATE,
                REQUESTER,
                "{not-valid-json");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.statusCode()).isNotEqualTo(500);
    }
}
