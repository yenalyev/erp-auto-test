package com.erp.tests.rbac;

import com.erp.annotations.TestCaseId;
import com.erp.api.clients.SessionClient;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RbacAccessMatrix;
import com.erp.data.RbacTestContext;
import com.erp.data.RequestBodyFactory;
import com.erp.enums.UserRole;
import com.erp.models.rbac.EndpointAccessRule;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.http.Method;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Authorization")
@Feature("RBAC - Role-Based Access Control")
public class RbacAccessMatrixTest extends BaseRbacTest {

    private int totalTests = 0;
    private int passedTests = 0;
    private int failedTests = 0;
    private int skippedTests = 0;

    // Test context для динамічних даних
    protected RbacTestContext testContext = new RbacTestContext();
    private final SessionClient apiClient = new SessionClient();

    @BeforeClass
    @Step("Setup RBAC tests and pre-authenticate all roles")
    public void setupRbacTests() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🚀 Setting up RBAC Access Matrix Tests");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Логуємо статистику матриці
        String stats = RbacAccessMatrix.getMatrixStats();
        log.info("\n{}", stats);
        Allure.addAttachment("RBAC Matrix Statistics", "text/plain", stats, "txt");

        // Логуємо покриття схемами
        SchemaRegistry.logSchemaCoverage();

        // Pre-authenticate всі ролі
        log.info("🔐 Pre-authenticating all roles...");

        for (UserRole role : UserRole.values()) {
            try {
                long startTime = System.currentTimeMillis();
                getSessionForRole(role);
                long duration = System.currentTimeMillis() - startTime;

                log.info("✅ Pre-authenticated role: {} (took {}ms)", role, duration);

            } catch (Exception e) {
                log.error("❌ Failed to pre-authenticate role {}: {}", role, e.getMessage());
                throw new RuntimeException("Failed to pre-authenticate role: " + role, e);
            }
        }

        // Створюємо тестові ресурси
        createTestResources();

        log.info("✅ All roles pre-authenticated successfully");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Логуємо стан кешу та контексту
        authService.logCacheStats();
        testContext.logInfo();

        Allure.addAttachment("Test Context Summary", "text/plain",
                testContext.toAllureSummary(), "txt");
    }

    /**
     * ✅ Створює повний набір ERP-сутностей для RBAC тестів
     */
    @Step("Setup ERP test data context")
    private void createTestResources() {
        log.info("📦 Starting ERP test data generation...");

        try {
            // 1. Отримуємо Одиницю Виміру (Unit)
            fetchSharedUnit();

            // 2. Створюємо Ресурс (Resource)
            setupSharedResource();

            // 3. Можна додати інші сутності (наприклад, Tech Map)
            // setupSharedTechMap();

        } catch (Exception e) {
            log.error("❌ Critical failure during ERP data setup: ", e);
            log.warn("⚠️ Some tests will be SKIPPED due to missing dependencies");
        }
    }

    /**
     * ✅ Отримує існуючу Одиницю Виміру
     */
    @Step("Fetch existing Measurement Unit from system")
    private void fetchSharedUnit() {
        log.info("🔍 Fetching existing units from system...");

        ApiEndpointDefinition endpoint = ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL;

        Response response = executeRequest(
                endpoint,
                UserRole.ADMIN,
                null,  // No body for GET
                null   // No path param
        );

        if (response.statusCode() == 200) {
            @SuppressWarnings("unchecked")
            Class<MeasurementUnitResponse> elementType =
                    (Class<MeasurementUnitResponse>) endpoint.getResponseElementType();

            List<MeasurementUnitResponse> units = response.jsonPath()
                    .getList("", elementType);

            if (units != null && !units.isEmpty()) {
                MeasurementUnitResponse unit = units.get(0);
                testContext.setSharedUnitId(unit.getId());
                log.info("✅ Found existing unit: {} (ID: {})", unit.getName(), unit.getId());
            } else {
                log.error("❌ No units found in the system!");
                throw new IllegalStateException("Database must have at least one Measurement Unit");
            }
        } else {
            log.error("❌ Failed to fetch units. Status: {}", response.statusCode());
            throw new RuntimeException("Failed to fetch measurement units");
        }
    }

    /**
     * ✅ Створює shared resource
     */
    @Step("Setup Shared Resource")
    private void setupSharedResource() {
        log.info("📦 Creating shared resource...");

        ApiEndpointDefinition endpoint = ApiEndpointDefinition.RESOURCE_CREATE;

        // ✅ ВИПРАВЛЕННЯ: Тепер передаємо endpoint enum, а не рядок
        Object resourceRequest = RequestBodyFactory.generate(endpoint, testContext);

        Response response = executeRequest(
                endpoint,
                UserRole.ADMIN,
                resourceRequest,
                null  // No path param for CREATE
        );

        if (response.statusCode() == 201 || response.statusCode() == 200) {
            @SuppressWarnings("unchecked")
            Class<ResourceResponse> responseClass =
                    (Class<ResourceResponse>) endpoint.getResponseClass();

            ResourceResponse resource = response.as(responseClass);

            testContext.setSharedResourceId(resource.getId());
            log.info("✅ Shared Resource created: {}", resource.getId());
        } else {
            log.error("⚠️ Failed to create shared resource. Status: {}", response.statusCode());
            log.error("Response: {}", response.body().asString());
        }
    }

    @DataProvider(name = "rbacAccessMatrix")
    public Object[][] accessMatrixData() {
        // Генеруємо дані, використовуючи оновлений RbacAccessMatrix
        Object[][] data = RbacAccessMatrix.generateTestData(testContext);
        totalTests = data.length;
        log.info("📊 Generated {} test combinations", totalTests);
        return data;
    }

    @Test(dataProvider = "rbacAccessMatrix", priority = 1)
    @TestCaseId("TC-RBAC-MATRIX-001")
    @Story("REQ-RBAC-001: Role-Based Access Control")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify RBAC matrix: each role has correct access to endpoints")
    public void testRbacAccessMatrix(
            EndpointAccessRule rule,
            UserRole role,
            int expectedStatusCode,
            String accessType
    ) {
        // ═══════════════════════════════════════════════════════════
        // ✅ CHECK IF TEST SHOULD BE SKIPPED
        // ═══════════════════════════════════════════════════════════
        if (!rule.canExecute()) {
            String skipReason = rule.getSkipReason();

            log.warn("⏭️ SKIPPED: {} {}", rule.getEndpointName(), skipReason);

            Allure.addAttachment("⏭️ Skip Reason", "text/plain", skipReason);
            Allure.addAttachment("📋 Rule Details", "text/plain",
                    rule.getDetailedInfo(role, accessType));

            addAllureParameters(rule, role, expectedStatusCode, accessType, "SKIPPED");

            skippedTests++;
            throw new SkipException(skipReason);
        }

        // ═══════════════════════════════════════════════════════════
        // 🧪 EXECUTE TEST
        // ═══════════════════════════════════════════════════════════

        String fullPath = rule.getFullPath();
        addAllureParameters(rule, role, expectedStatusCode, accessType, "RUNNING");

        String testId = String.format("%s %s as %s",
                rule.getHttpMethod(), fullPath, role);

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🧪 Testing: {} - expecting {}", testId, accessType);

        attachRequestDetails(rule, role, fullPath);

        long startTime = System.currentTimeMillis();
        Response response = null;

        try {
            // ✅ Виконуємо запит
            response = executeRequest(
                    rule.getEndpointDefinition(),
                    role,
                    rule.getRequestBody(),
                    rule.getPathParam()
            );

            long duration = System.currentTimeMillis() - startTime;

            log.info("📥 Response status: {} (took {}ms)", response.statusCode(), duration);
            Allure.addAttachment("Response Time", duration + "ms");

            attachResponseDetails(response);

            // === ПЕРЕВІРКА СТАТУС КОДУ ===
            assertThat(response.statusCode())
                    .as("Status code for %s %s with role %s should be %d",
                            rule.getHttpMethod(), fullPath, role, expectedStatusCode)
                    .isEqualTo(expectedStatusCode);

            // === ДОДАТКОВІ ПЕРЕВІРКИ ===
            performAdditionalValidations(rule, response, accessType, role);

            passedTests++;
            log.info("✅ Test PASSED: {}", testId);

        } catch (AssertionError e) {
            failedTests++;
            log.error("❌ Test FAILED: {}", testId);
            log.error("Assertion failed: {}", e.getMessage());

            if (response != null) {
                log.error("Actual status code: {}", response.statusCode());
                log.error("Response body: {}", response.body().asString());
            }

            throw e;

        } catch (Exception e) {
            failedTests++;
            log.error("❌ Test FAILED with exception: {}", testId);
            log.error("Exception: ", e);
            throw new RuntimeException("Test execution failed: " + testId, e);
        }
    }

    /**
     * ✅ Додає параметри в Allure
     */
    private void addAllureParameters(
            EndpointAccessRule rule,
            UserRole role,
            int expectedStatus,
            String accessType,
            String testStatus
    ) {
        ApiEndpointDefinition endpoint = rule.getEndpointDefinition();

        Allure.parameter("Endpoint", rule.getFullPath());
        Allure.parameter("Endpoint Definition", rule.getEndpointName());
        Allure.parameter("HTTP Method", rule.getHttpMethod().toString());
        Allure.parameter("Role", role.toString());
        Allure.parameter("Access Type", accessType);
        Allure.parameter("Expected Status", expectedStatus);
        Allure.parameter("Authentication", "Session-based (JSESSIONID)");

        if (rule.hasSchema()) {
            Allure.parameter("Validation Schema", rule.getSchemaPath());
            Allure.parameter("Response Type", endpoint.getResponseTypeDescription());
        } else {
            Allure.parameter("Validation Schema", "Manual validation (no schema)");
        }

        if (endpoint.requiresBody()) {
            Allure.parameter("Request Type", endpoint.getRequestTypeDescription());
        }

        if (testStatus != null) {
            Allure.parameter("Test Status", testStatus);
        }

        Allure.description(rule.getDescription());
    }

    /**
     * ✅ Додаткові валідації
     */
    @Step("Perform additional validations for {accessType} access")
    private void performAdditionalValidations(
            EndpointAccessRule rule,
            Response response,
            String accessType,
            UserRole role
    ) {
        if ("ALLOWED".equals(accessType)) {
            validateAllowedAccess(rule, response, role);
        } else if ("DENIED".equals(accessType)) {
            validateDeniedAccess(response, role);
        }
    }

    /**
     * ✅ Валідація ALLOWED доступу (Оновлено)
     */
    @Step("Validate ALLOWED access")
    private void validateAllowedAccess(EndpointAccessRule rule, Response response, UserRole role) {
        log.info("✅ Access ALLOWED as expected for role: {}", role);

        // Перевіряємо body (крім DELETE та 204 No Content)
        if (rule.getHttpMethod() != Method.DELETE && response.statusCode() != 204) {
            assertThat(response.body())
                    .as("Response body should not be null")
                    .isNotNull();

            String body = response.body().asString();
            assertThat(body)
                    .as("Response body should not be empty")
                    .isNotEmpty();
        }

        // ✅ Schema validation або fallback
        if (rule.hasSchema()) {
            log.info("📋 Validating response using JSON Schema: {}", rule.getSchemaPath());

            // 🔥 НОВЕ: Прикріплюємо деталі валідації в Allure перед самою перевіркою
            attachSchemaValidationInfo(rule, response);

            SchemaRegistry.validateIfSuccess(response, rule);
        } else {
            performFallbackValidation(rule, response);
        }
    }

    /**
     * 🔥 НОВИЙ МЕТОД: Прикріплює очікувану схему та фактичний JSON в Allure
     */
    @Step("Attach Schema Validation Details (Expected vs Actual)")
    private void attachSchemaValidationInfo(EndpointAccessRule rule, Response response) {
        // 1. Отримуємо та прикріплюємо Actual Body
        String actualJson = "{}";
        try {
            actualJson = response.jsonPath().prettify();
        } catch (Exception e) {
            actualJson = response.body().asString();
        }
        Allure.addAttachment("🔍 Actual Response Body", "application/json", actualJson, "json");

        // 2. Отримуємо та прикріплюємо Expected Schema
        String schemaPath = rule.getSchemaPath();
        try (InputStream schemaStream = getClass().getClassLoader().getResourceAsStream(schemaPath)) {
            if (schemaStream != null) {
                String schemaContent = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
                Allure.addAttachment("📜 Expected JSON Schema (" + schemaPath + ")",
                        "application/json", schemaContent, "json");
            } else {
                Allure.addAttachment("⚠️ Schema Error", "text/plain",
                        "Could not find schema file at: " + schemaPath, "txt");
            }
        } catch (Exception e) {
            log.error("Failed to read schema file for attachment", e);
            Allure.addAttachment("⚠️ Schema Error", "text/plain",
                    "Error reading schema: " + e.getMessage(), "txt");
        }
    }

    /**
     * ✅ Fallback валідація
     */
    @Step("Perform fallback validation")
    private void performFallbackValidation(EndpointAccessRule rule, Response response) {
        Method method = rule.getHttpMethod();
        log.warn("⚠️ No schema for {} {}, using fallback validation",
                method, rule.getEndpointName());

        switch (method) {
            case POST:
            case PUT:
                validateCreateUpdateResponseFallback(response, method);
                break;
            case GET:
                validateGetResponseFallback(response);
                break;
            case DELETE:
                // No validation needed for DELETE success
                break;
            default:
                log.warn("⚠️ No fallback validation for method: {}", method);
        }
    }

    private void validateCreateUpdateResponseFallback(Response response, Method method) {
        try {
            // Тільки якщо статус 200/201
            if (response.statusCode() < 300) {
                Object id = response.jsonPath().get("id");
                assertThat(id)
                        .as(method + " response should contain 'id' field")
                        .isNotNull();
            }
        } catch (Exception e) {
            log.error("❌ {} response fallback validation failed", method);
        }
    }

    private void validateGetResponseFallback(Response response) {
        String body = response.body().asString();
        assertThat(body).as("GET response should contain data").isNotEmpty();
    }

    /**
     * ✅ Валідація DENIED доступу
     */
    @Step("Validate DENIED access")
    private void validateDeniedAccess(Response response, UserRole role) {
        log.info("🚫 Access DENIED as expected for role: {}", role);
        // Тут можна додати перевірку на тіло помилки, якщо API повертає JSON для 403
    }

    /**
     * ✅ Attachment - Request Details
     */
    @Step("Attach request details")
    private void attachRequestDetails(EndpointAccessRule rule, UserRole role, String fullPath) {
        ApiEndpointDefinition endpoint = rule.getEndpointDefinition();

        StringBuilder sb = new StringBuilder();
        sb.append("Endpoint Definition: ").append(rule.getEndpointName()).append("\n");
        sb.append("HTTP Method: ").append(rule.getHttpMethod()).append("\n");
        sb.append("Full Path: ").append(fullPath).append("\n");
        sb.append("Role: ").append(role).append("\n");

        if (rule.getRequestBody() != null) {
            sb.append("\nRequest Body:\n").append(rule.getRequestBody()).append("\n");
        }

        Allure.addAttachment("Request Details", "text/plain", sb.toString(), "txt");
    }

    /**
     * ✅ Attachment - Response Details
     */
    @Step("Attach response details")
    private void attachResponseDetails(Response response) {
        Allure.addAttachment("Response Status", String.valueOf(response.statusCode()));

        if (response.body() != null) {
            String body = response.body().asString();
            if (!body.isEmpty()) {
                Allure.addAttachment("Response Body", "application/json", body, "json");
            }
        }
    }

    private Response executeRequest(
            ApiEndpointDefinition endpoint,
            UserRole role,
            Object requestBody,
            String pathParam
    ) {
        Map<String, String> sessionCookies = getSessionForRole(role);

        // Будуємо шлях, підставляючи ID якщо треба
        String path = (pathParam != null)
                ? endpoint.getPath(pathParam)
                : endpoint.getPath();

        return apiClient.executeWithCookies(
                endpoint.getHttpMethod(),
                path,
                requestBody,
                sessionCookies
        );
    }

    @AfterClass
    @Step("Cleanup RBAC tests and log statistics")
    public void cleanupRbacTests() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🧹 Cleaning up RBAC tests");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        String stats = String.format(
                "RBAC Test Execution Statistics\n" +
                        "================================\n" +
                        "Total Tests: %d\n" +
                        "Passed: %d (%.1f%%)\n" +
                        "Failed: %d (%.1f%%)\n" +
                        "Skipped: %d (%.1f%%)\n",
                totalTests,
                passedTests,
                totalTests > 0 ? (passedTests * 100.0 / totalTests) : 0,
                failedTests,
                totalTests > 0 ? (failedTests * 100.0 / totalTests) : 0,
                skippedTests,
                totalTests > 0 ? (skippedTests * 100.0 / totalTests) : 0
        );
        log.info(stats);
        Allure.addAttachment("Test Statistics", "text/plain", stats, "txt");
    }
}