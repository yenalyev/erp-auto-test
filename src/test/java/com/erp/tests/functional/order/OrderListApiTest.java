package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.OrderState;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.PagedOrderResponse;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Order list")
public class OrderListApiTest extends OrderApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-ORD-030")
    @Story("List orders")
    @Severity(SeverityLevel.NORMAL)
    public void testGetPageWithStorageIdsAndPagination() {
        OrderResponse created = orderFixture.createOrder(REQUESTER);

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_PAGE,
                REQUESTER,
                Map.of("storageIds", requesterStorageId, "page", 0, "size", 10));
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_GET_PAGE);

        PagedOrderResponse page = response.as(PagedOrderResponse.class);
        assertThat(page.getContent()).isNotNull();
        assertThat(page.getContent().stream().anyMatch(o -> created.getId().equals(o.getId()))).isTrue();
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-031")
    @Story("List filters")
    public void testFilterByStates() {
        OrderResponse newOrder = orderFixture.createOrder(REQUESTER);
        OrderResponse inProgressOrder = orderFixture.createOrder(REQUESTER);
        inProgressOrder = orderFixture.takeToWork(MANAGER, inProgressOrder.getId(), requesterStorageId);
        Long inProgressId = inProgressOrder.getId();

        Response filtered = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_PAGE,
                REQUESTER,
                Map.of(
                        "storageIds", requesterStorageId,
                        "states", OrderState.IN_PROGRESS.name(),
                        "page", 0,
                        "size", 50));
        assertThat(filtered.statusCode()).isEqualTo(200);

        PagedOrderResponse page = filtered.as(PagedOrderResponse.class);
        assertThat(page.getContent().stream().anyMatch(o -> inProgressId.equals(o.getId()))).isTrue();
        assertThat(page.getContent().stream().noneMatch(o -> newOrder.getId().equals(o.getId()))).isTrue();
        assertThat(page.getContent()).allMatch(o -> o.getState() == OrderState.IN_PROGRESS);
    }

    @Test(priority = 12)
    @TestCaseId("TC-ORD-032")
    @Story("List filters")
    public void testFilterByResourceSearch() {
        orderFixture.createOrder(REQUESTER, requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        String searchSubstring = resourceName.length() > 4
                ? resourceName.substring(0, 4)
                : resourceName;

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_PAGE,
                REQUESTER,
                Map.of(
                        "storageIds", requesterStorageId,
                        "resourceSearch", searchSubstring,
                        "page", 0,
                        "size", 50));
        assertThat(response.statusCode()).isEqualTo(200);

        PagedOrderResponse page = response.as(PagedOrderResponse.class);
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test(priority = 13)
    @TestCaseId("TC-ORD-034")
    @Story("List visibility")
    @Severity(SeverityLevel.CRITICAL)
    public void testListVisibleByGatheringStorageId() {
        OrderResponse order = prepareManagedInProgress();

        PagedOrderResponse page = orderFixture.getPage(MANAGER, gatheringStorageId);
        assertThat(page.getContent().stream().anyMatch(o -> order.getId().equals(o.getId()))).isTrue();
    }

    @Test(priority = 14)
    @TestCaseId("TC-ORD-035")
    @Story("Get by id")
    @Severity(SeverityLevel.CRITICAL)
    public void testGetById() {
        OrderResponse created = orderFixture.createOrder(REQUESTER);

        OrderResponse fetched = orderFixture.getById(REQUESTER, created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getState()).isEqualTo(OrderState.NEW);
        assertThat(fetched.getLines()).isNotEmpty();
        assertThat(fetched.getLines().getFirst().getResource().getId()).isEqualTo(resourceId);
    }
}
