package com.erp.tests.rbac;

import com.erp.annotations.TestCaseId;
import com.erp.data.RbacAccessMatrix;
import com.erp.enums.UserRole;
import com.erp.fixtures.RbacFixture;
import com.erp.models.rbac.EndpointAccessRule;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.http.Method;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.*;


import static com.erp.utils.helpers.AllureHelper.attachSchemaValidationInfo;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Authorization")
@Feature("RBAC - Role-Based Access Control")
public class RbacAccessMatrixTest extends BaseRbacTest {

    private int totalTests = 0;
    private int passedTests = 0;
    private int failedTests = 0;
    private int skippedTests = 0;


    @BeforeClass(alwaysRun = true, dependsOnMethods = "rbacClassSetup")
    @Step("Setup RBAC Access Matrix environment")
    public void setupRbacTests() {
        log.info("🚀 Preparing ERP environment for RBAC tests...");

        // 1. Використовуємо фікстуру для підготовки даних (всередині неї вже є кроки Allure)
        rbacFixture.prepareFullRbacContext();

        // 2. Логуємо статистику матриці
        String stats = RbacAccessMatrix.getMatrixStats();
        Allure.addAttachment("RBAC Matrix Statistics", stats);
        SchemaRegistry.logSchemaCoverage();

        log.info("✅ Environment ready. Context: {}", testContext.toSummary());
    }

    @DataProvider(name = "rbacAccessMatrix")
    public Object[][] accessMatrixData() {
        // Гарантуємо, що erpFixture ініціалізований, навіть якщо DataProvider випередив @BeforeClass
        if (rbacFixture == null) {
            log.info("erpFixture was null in DataProvider, initializing manually...");
            this.rbacFixture = new RbacFixture(testContext, apiExecutor);
        }

        // Якщо контекст порожній - наповнюємо його
        if (testContext.getSharedUnitId() == null) {
            log.info("Context is empty in DataProvider, preparing environment...");
            rbacFixture.prepareFullRbacContext();
        }

        Object[][] data = RbacAccessMatrix.generateTestData(testContext);
        totalTests = data.length;
        return data;
    }

    @Test(dataProvider = "rbacAccessMatrix", priority = 1)
    @TestCaseId("TC-RBAC-MATRIX-001")
    @Story("REQ-RBAC-001: Role-Based Access Control Matrix")
    @Severity(SeverityLevel.BLOCKER)
    public void testRbacAccessMatrix(
            EndpointAccessRule rule,
            UserRole role,
            int expectedStatusCode,
            String accessType
    ) {
        // Встановлюємо 'Description' в алюр-репорті
        Allure.description(rule.getDescription());

        // === SKIP LOGIC ===
        if (!rule.canExecute()) {
            skippedTests++;
            throw new SkipException(rule.getSkipReason());
        }

        // === EXECUTION ===
        addAllureParameters(rule, role, expectedStatusCode, accessType);
        attachRequestDetails(rule, role);

        // Використовуємо ApiExecutor, який успадкований від BaseTest
        Response response = executeRequestAsRole(
                rule,
                role,
                rule.getRequestBody()
        );

        attachResponseDetails(response);

        // === ASSERTIONS ===
        try {
            assertThat(response.statusCode())
                    .as("Access %s: %s %s as %s",
                            accessType, rule.getHttpMethod(), rule.getFullPath(), role)
                    .isEqualTo(expectedStatusCode);

            performAdditionalValidations(rule, response, accessType, role);
            passedTests++;

        } catch (AssertionError e) {
            failedTests++;
            log.error("❌ RBAC Violation: expected {}, but got {}", expectedStatusCode, response.statusCode());
            throw e;
        }
    }

    // Рекомендується згодом винести їх у допоміжний клас ValidatorHelper

    @Step("Perform additional validations for {accessType} access")
    private void performAdditionalValidations(EndpointAccessRule rule, Response response, String accessType, UserRole role) {
        if ("ALLOWED".equals(accessType)) {
            validateAllowedAccess(rule, response, role);
        }
    }

    @Step("Validate ALLOWED access details")
    private void validateAllowedAccess(EndpointAccessRule rule, Response response, UserRole role) {
        boolean allowsEmptyBody = rule.getHttpMethod() == Method.DELETE
                || response.statusCode() == 204
                || "INCIDENT_POST_CREATE".equals(rule.getEndpointName())
                || "INCIDENT_DELETE_BY_RELOCATION".equals(rule.getEndpointName());
        if (!allowsEmptyBody) {
            assertThat(response.body().asString()).isNotEmpty();
        }

        if (rule.hasSchema()) {
            attachSchemaValidationInfo(rule, response);
            SchemaRegistry.validateIfSuccess(response, rule);
        }
    }

    @Step("Attach Details")
    private void attachRequestDetails(EndpointAccessRule rule, UserRole role) {
        String details = String.format("Endpoint: %s\nMethod: %s\nRole: %s\nBody: %s",
                rule.getFullPath(), rule.getHttpMethod(), role, rule.getRequestBody());
        Allure.addAttachment("Request Info", details);
    }

    @Step("Attach Response")
    private void attachResponseDetails(Response response) {
        Allure.addAttachment("Status Code", String.valueOf(response.statusCode()));
        if (!response.body().asString().isEmpty()) {
            Allure.addAttachment("Response Body", "application/json", response.body().asString(), "json");
        }
    }

    private void addAllureParameters(EndpointAccessRule rule, UserRole role, int expectedStatus, String accessType) {
        Allure.parameter("Role", role);
        Allure.parameter("Endpoint", rule.getEndpointName());
        Allure.parameter("Expected Status", expectedStatus);
        Allure.parameter("Access Type", accessType);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupRbacTests() {
        log.info("📊 Finished RBAC Matrix: Total {}, Passed {}, Failed {}, Skipped {}",
                totalTests, passedTests, failedTests, skippedTests);
    }
}