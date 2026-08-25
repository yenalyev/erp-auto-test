package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.ShiftRequest;
import com.erp.models.response.ShiftResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.util.List;

@Slf4j
public class ShiftFixture extends BaseFixture {

    public ShiftFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public ShiftRequest uniqueRequest(String prefix) {
        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        return ShiftRequest.builder()
                .name(prefix + suffix)
                .description("autotest shift")
                .timeStart(LocalTime.of(8, 0))
                .timeEnd(LocalTime.of(16, 0))
                .workerQty(4)
                .build();
    }

    @Step("API: GET shifts for storage {storageId}")
    public List<ShiftResponse> getAll(UserRole role, long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.SHIFT_GET_ALL, role, String.valueOf(storageId));
        validateSuccess(response, "GET shifts");
        List<ShiftResponse> shifts = response.jsonPath().getList("", ShiftResponse.class);
        return shifts == null ? List.of() : shifts;
    }

    @Step("API: POST shift on storage {storageId}")
    public ShiftResponse create(UserRole role, long storageId, ShiftRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.SHIFT_POST_CREATE, role, request, String.valueOf(storageId));
        validateSuccess(response, "Create shift");
        return response.as(ShiftResponse.class);
    }

    @Step("API: PUT shift {shiftId} on storage {storageId}")
    public ShiftResponse update(UserRole role, long shiftId, long storageId, ShiftRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.SHIFT_PUT_UPDATE,
                role,
                request,
                String.valueOf(shiftId),
                String.valueOf(storageId));
        validateSuccess(response, "Update shift");
        return response.as(ShiftResponse.class);
    }

    @Step("API: DELETE shift {shiftId} on storage {storageId}")
    public Response deleteRaw(UserRole role, long shiftId, long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.SHIFT_DELETE,
                role,
                null,
                String.valueOf(shiftId),
                String.valueOf(storageId));
    }
}
