package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.OrderRelocationTaskLineRequest;
import com.erp.models.request.OrderRelocationTaskRequest;
import com.erp.models.response.OrderRelocationTaskResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class OrderRelocationTaskFixture extends BaseFixture {

    public OrderRelocationTaskFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public OrderRelocationTaskRequest request(long sourceStorageId, long orderLineId, double amount) {
        return OrderRelocationTaskRequest.builder()
                .sourceStorageId(sourceStorageId)
                .lines(List.of(OrderRelocationTaskLineRequest.builder()
                        .orderLineId(orderLineId)
                        .amount(BigDecimal.valueOf(amount))
                        .build()))
                .build();
    }

    @Step("API: POST relocation task for order {orderId}")
    public Response createRaw(UserRole role, long orderId, long requesterStorageId,
                              OrderRelocationTaskRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_RELOCATION_TASK,
                role,
                request,
                orderId,
                requesterStorageId);
    }

    public OrderRelocationTaskResponse create(UserRole role, long orderId, long requesterStorageId,
                                              OrderRelocationTaskRequest request) {
        Response response = createRaw(role, orderId, requesterStorageId, request);
        validateSuccess(response, "Create order relocation task");
        return response.as(OrderRelocationTaskResponse.class);
    }

    @Step("API: GET relocation tasks for order {orderId}")
    public List<OrderRelocationTaskResponse> getForOrder(UserRole role, long orderId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_RELOCATION_TASKS, role, null, orderId);
        validateSuccess(response, "Get order relocation tasks");
        List<OrderRelocationTaskResponse> tasks =
                response.jsonPath().getList("", OrderRelocationTaskResponse.class);
        return tasks == null ? List.of() : tasks;
    }

    @Step("API: GET active relocation tasks for source storage {storageId}")
    public List<OrderRelocationTaskResponse> getForSource(UserRole role, long storageId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_RELOCATION_TASKS_BY_STORAGES,
                role,
                Map.of("storageIds", storageId));
        validateSuccess(response, "Get relocation tasks for source storage");
        List<OrderRelocationTaskResponse> tasks =
                response.jsonPath().getList("", OrderRelocationTaskResponse.class);
        return tasks == null ? List.of() : tasks;
    }

    @Step("API: PUT cancel relocation task {taskId}")
    public OrderRelocationTaskResponse cancel(UserRole role, long orderId, long taskId,
                                              long requesterStorageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_CANCEL_RELOCATION_TASK,
                role,
                null,
                orderId,
                taskId,
                requesterStorageId);
        validateSuccess(response, "Cancel order relocation task");
        return response.as(OrderRelocationTaskResponse.class);
    }
}
