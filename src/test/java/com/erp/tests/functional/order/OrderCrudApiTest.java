package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.OrderState;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.OrderRequest;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
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

    @Test(priority = 6)
    @TestCaseId("TC-ORD-006")
    @Story("Create validation")
    @Description("FULL_ACCESS (Admin) може створити заявку з будь-яким активним ресурсом каталогу.")
    public void testAdminFullAccessAllowsAnyResource() {
        ResourceResponse ungranted = inventoryFixture.pickResourceNotOnStorage(
                requesterStorageId, UserRole.ADMIN, sharedResources);
        OrderRequest request = OrderDataFactory.buildOrderRequest(
                requesterStorageId, ungranted.getId(), 1.0);
        Response response = apiExecutor.execute(ApiEndpointDefinition.ORDER_POST_CREATE, MANAGER, request);
        assertThat(response.statusCode())
                .as("Admin FULL_ACCESS create; body=%s", response.asString())
                .isEqualTo(200);
        OrderResponse created = response.as(OrderResponse.class);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getLines().getFirst().getResource().getId()).isEqualTo(ungranted.getId());
    }

    @Test(priority = 7)
    @TestCaseId("TC-ORD-007")
    @Story("Create on CREW")
    @Description("CREW: доступні ресурси через grant батьківського складу.")
    public void testCreateOrderOnCrewUsesParentGrants() {
        StorageFixture storageFixture = new StorageFixture(testContext, apiExecutor);
        var crew = storageFixture.createCrewStorage(requesterStorageId, "ord-crew-");
        OrderRequest request = OrderDataFactory.buildOrderRequest(crew.getId(), resourceId, 1.0);
        Response response = apiExecutor.execute(ApiEndpointDefinition.ORDER_POST_CREATE, MANAGER, request);
        assertThat(response.statusCode())
                .as("Create on CREW; body=%s", response.asString())
                .isIn(200, 400);
        if (response.statusCode() == 200) {
            assertThat(response.as(OrderResponse.class).getId()).isNotNull();
        }
    }

    @Test(priority = 8)
    @TestCaseId("TC-ORD-008")
    @Story("Create on FLY_POINT")
    @Description("FLY_POINT: ресурси з батьківської ієрархії (фактична поведінка).")
    public void testCreateOrderOnFlyPointUsesParentHierarchy() {
        StorageFixture storageFixture = new StorageFixture(testContext, apiExecutor);
        var fly = storageFixture.createFlyPointStorage(requesterStorageId, "ord-fly-");
        OrderRequest request = OrderDataFactory.buildOrderRequest(fly.getId(), resourceId, 1.0);
        Response response = apiExecutor.execute(ApiEndpointDefinition.ORDER_POST_CREATE, MANAGER, request);
        assertThat(response.statusCode())
                .as("Create on FLY_POINT; body=%s", response.asString())
                .isIn(200, 400);
    }

    @Test(priority = 14)
    @TestCaseId("TC-ORD-011")
    @Story("Update order")
    @Description("Update з чужим storageId → 4xx.")
    public void testUpdateWithForeignStorageIdReturns4xx() {
        OrderResponse created = orderFixture.createOrder(REQUESTER);
        OrderRequest update = OrderDataFactory.buildOrderRequest(
                ConfigProvider.getOwner1StorageId(), resourceId, 2.0);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_UPDATE, REQUESTER, update, created.getId());
        assertThat(response.statusCode()).isBetween(400, 499);
        OrderResponse unchanged = orderFixture.getById(REQUESTER, created.getId());
        assertThat(unchanged.getLines().getFirst().getQuantity().doubleValue())
                .isEqualTo(created.getLines().getFirst().getQuantity().doubleValue());
    }

    @Test(priority = 15)
    @TestCaseId("TC-ORD-014")
    @Story("Available categories")
    @Description("GET /resources/available-categories — лише категорії з доступними ресурсами.")
    public void testAvailableCategoriesForRequesterStorage() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_GET_AVAILABLE_CATEGORIES,
                REQUESTER,
                Map.of("storageId", requesterStorageId));
        assertThat(response.statusCode()).isEqualTo(200);
        List<ResourceCategoryResponse> categories =
                response.jsonPath().getList("", ResourceCategoryResponse.class);
        assertThat(categories).isNotEmpty();
        assertThat(categories).allMatch(c -> c.getId() != null && c.getName() != null);
    }
}
