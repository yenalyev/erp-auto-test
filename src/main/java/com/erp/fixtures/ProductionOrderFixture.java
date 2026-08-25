package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.ProductionOrderLinkRequest;
import com.erp.models.request.ProductionOrderOutputRequest;
import com.erp.models.request.ProductionOrderRequest;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.response.ProductionOrderResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
public class ProductionOrderFixture extends BaseFixture {

    public ProductionOrderFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public ProductionOrderRequest buildCreateRequest(long targetStorageId, long resourceId, double amount) {
        return ProductionOrderRequest.builder()
                .targetStorageId(targetStorageId)
                .targetDate(LocalDate.now().plusDays(7))
                .description("autotest-po-" + System.currentTimeMillis() % 1_000_000)
                .output(List.of(ProductionOrderOutputRequest.builder()
                        .resourceId(resourceId)
                        .amount(BigDecimal.valueOf(amount))
                        .build()))
                .build();
    }

    @Step("API: POST production-order")
    public ProductionOrderResponse create(UserRole role, ProductionOrderRequest request) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_ORDER_POST_CREATE, role, request);
        validateSuccess(response, "Create production order");
        return response.as(ProductionOrderResponse.class);
    }

    @Step("API: GET production-order {id}")
    public ProductionOrderResponse getById(UserRole role, long id) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_ORDER_GET_BY_ID, role, null, id);
        validateSuccess(response, "Get production order");
        return response.as(ProductionOrderResponse.class);
    }

    @Step("API: PUT cancel production-order {id}")
    public ProductionOrderResponse cancel(UserRole role, long id) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_ORDER_PUT_CANCEL, role, null, id);
        validateSuccess(response, "Cancel production order");
        return response.as(ProductionOrderResponse.class);
    }

    @Step("API: DELETE production-order {id}")
    public Response deleteRaw(UserRole role, long id) {
        return apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_ORDER_DELETE, role, null, id);
    }

    @Step("API: POST decompose production-order {id}")
    public Response decomposeRaw(UserRole role, long id, DecompositionRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_ORDER_POST_DECOMPOSE, role, request, id);
    }

    @Step("API: GET production-order holds {id}")
    public Response getHoldsRaw(UserRole role, long id) {
        return apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_ORDER_GET_HOLDS, role, null, id);
    }

    @Step("API: GET production-order target locations")
    public Response getTargetLocationsRaw(UserRole role) {
        return apiExecutor.execute(ApiEndpointDefinition.PRODUCTION_ORDER_GET_TARGET_LOCATIONS, role);
    }

    @Step("API: GET production-orders page for storage {storageId}")
    public Response getPageRaw(UserRole role, long storageId) {
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_ORDER_GET_PAGE,
                role,
                Map.of("storageIds", storageId, "page", 0, "size", 20));
    }

    @Step("API: POST link warehouse order {orderId} to production-order {id}")
    public Response linkOrderRaw(UserRole role, long id, long orderId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_ORDER_POST_LINK_ORDER,
                role,
                ProductionOrderLinkRequest.builder().orderId(orderId).build(),
                id);
    }

    public long defaultTargetStorageId() {
        Long fromContext = testContext.get(ContextKey.OWNER_1_STORAGE_ID);
        if (fromContext != null) {
            return fromContext;
        }
        return ConfigProvider.getOwner1StorageId();
    }
}
