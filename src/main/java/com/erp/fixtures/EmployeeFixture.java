package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.EmployeeRequest;
import com.erp.models.response.EmployeeResponse;
import com.erp.models.response.PagedEmployeeResponse;
import com.erp.test_context.TestContext;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class EmployeeFixture extends BaseFixture {

    public EmployeeFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("API: створити співробітника «{callSign}» на storage {storageId}")
    public EmployeeResponse createEmployee(UserRole role, long storageId, String callSign) {
        Response response = createEmployeeRaw(role, storageId, callSign);
        validateSuccess(response, "Create employee");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EMPLOYEE_POST_CREATE);
        EmployeeResponse employee = response.as(EmployeeResponse.class);
        log.info("Employee created: id={}, callSign={}", employee.getId(), employee.getCallSign());
        return employee;
    }

    @Step("API: POST employee raw callSign={callSign} storage={storageId}")
    public Response createEmployeeRaw(UserRole role, long storageId, String callSign) {
        EmployeeRequest request = EmployeeRequest.builder()
                .callSign(callSign)
                .storageIds(List.of(storageId))
                .build();
        return apiExecutor.execute(ApiEndpointDefinition.EMPLOYEE_POST_CREATE, role, request);
    }

    @Step("API: отримати сторінку співробітників для storage {storageId}")
    public List<EmployeeResponse> getEmployees(UserRole role, long storageId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EMPLOYEE_GET_PAGE,
                role,
                Map.of("storageIds", storageId, "size", 100));
        validateSuccess(response, "Get employees page");
        PagedEmployeeResponse page = response.as(PagedEmployeeResponse.class);
        return page.getContent() != null ? page.getContent() : List.of();
    }
}
