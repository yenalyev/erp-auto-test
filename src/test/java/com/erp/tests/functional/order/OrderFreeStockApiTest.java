package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.StorageAmountResponse;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-WMS-010 Free stock and holds")
public class OrderFreeStockApiTest extends OrderApiTestBase {

    private static final double HOLD_QTY = 8.0;
    private static final double TOTAL_STOCK = 10.0;

    @Test(priority = 10)
    @TestCaseId("TC-ORD-100")
    @Story("Inventory bookedAmount")
    @Severity(SeverityLevel.CRITICAL)
    public void testInventoryShowsBookedAmountAfterActiveBooking() {
        pinGatheringOnHand(Math.max(TOTAL_STOCK, HOLD_QTY + 2));
        // Order line must be >= HOLD_QTY (createOrder default is 5).
        OrderResponse order = prepareManagedInProgress(HOLD_QTY);
        resetGatheringOnHandKeepingOrders(TOTAL_STOCK);
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, HOLD_QTY);

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET,
                GATHERER,
                Map.of("locations", gatheringStorageId, "resourceIds", resourceId, "size", 5));

        assertThat(response.statusCode()).isEqualTo(200);
        List<MultiLocationStorageItemResponse> content =
                response.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        assertThat(content).isNotEmpty();

        Double bookedAmount = content.getFirst().getLocations().stream()
                .filter(loc -> loc.getStorage() != null
                        && gatheringStorageId.equals(loc.getStorage().getId()))
                .map(StorageAmountResponse::getBookedAmount)
                .findFirst()
                .orElse(0.0);
        assertThat(bookedAmount).isGreaterThan(0.0);
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-104")
    @Story("Relocation blocked by hold")
    @Severity(SeverityLevel.CRITICAL)
    public void testRelocationSendBelowFreeStockReturns400() {
        OrderResponse order = prepareManagedInProgress(HOLD_QTY);
        resetGatheringOnHandKeepingOrders(TOTAL_STOCK);
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, HOLD_QTY);

        Long elsewhereId = ConfigProvider.getOwner1StorageId();
        RelocationOutputRequest send = RelocationDataFactory.buildSendRequest(
                gatheringStorageId, elsewhereId, resourceId, 5.0);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, GATHERER, send);

        assertThat(response.statusCode()).isEqualTo(400);
        String body = response.body().asString();
        assertThat(body).containsAnyOf("заброньовано", "вільного залишку", "Недостатньо");
    }
}
