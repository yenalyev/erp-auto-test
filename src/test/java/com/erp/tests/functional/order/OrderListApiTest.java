package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.OrderState;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.PagedOrderResponse;
import com.erp.utils.config.ConfigProvider;
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

    @Test(priority = 15)
    @TestCaseId("TC-ORD-033")
    @Story("List filters")
    @Description("Фільтр startDate/endDate по createdAt — сьогоднішня заявка потрапляє в вікно.")
    public void testFilterByCreatedDateRange() {
        OrderResponse created = orderFixture.createOrder(REQUESTER);
        String today = java.time.LocalDate.now().toString();
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_PAGE,
                REQUESTER,
                Map.of(
                        "storageIds", requesterStorageId,
                        "startDate", today,
                        "endDate", today,
                        "page", 0,
                        "size", 50));
        assertThat(response.statusCode()).isEqualTo(200);
        PagedOrderResponse page = response.as(PagedOrderResponse.class);
        assertThat(page.getContent().stream().anyMatch(o -> created.getId().equals(o.getId()))).isTrue();
    }

    @Test(priority = 16)
    @TestCaseId("TC-ORD-036")
    @Story("List progress fields")
    @Description("List progress activeBookings/preparedBookings видимі користувачу з правом бачити броні.")
    public void testListProgressFieldsForManager() {
        OrderResponse order = prepareManagedInProgress();
        orderFixture.book(MANAGER, order.getId(), requesterStorageId, resourceId, DEFAULT_ORDER_QTY);
        PagedOrderResponse page = orderFixture.getPage(MANAGER, requesterStorageId);
        OrderResponse row = page.getContent().stream()
                .filter(o -> order.getId().equals(o.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(row.getId()).isEqualTo(order.getId());
        assertThat(row.getActiveBookings()).isGreaterThan(0);
    }

    @Test(priority = 17)
    @TestCaseId("TC-ORD-037")
    @Story("List visibility")
    @Description("Чужий підрозділ не бачить заявку в списку.")
    public void testOutsiderListDoesNotIncludeOrder() {
        OrderResponse created = orderFixture.createOrder(REQUESTER);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_PAGE,
                OUTSIDER,
                Map.of("storageIds", ConfigProvider.getOwner1StorageId(), "page", 0, "size", 50));
        if (response.statusCode() == 403) {
            return;
        }
        assertThat(response.statusCode()).isEqualTo(200);
        PagedOrderResponse page = response.as(PagedOrderResponse.class);
        assertThat(page.getContent().stream().noneMatch(o -> created.getId().equals(o.getId()))).isTrue();
    }
}
