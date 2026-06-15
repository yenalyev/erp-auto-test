package com.erp.tests.functional.non_series_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.NonSeriesProductionFixture;
import com.erp.models.request.NonSeriesProductionRequest;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureHelper;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Non-Series Production")
@Feature("Non-Series Production API")
public class NonSeriesProductionTest extends BaseFunctionalTest {

    private NonSeriesProductionFixture fixture;
    private Long storageId;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів несерійного виробництва")
    public void setupNonSeriesProductionTest() {
        fixture = new NonSeriesProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.NON_SERIES_RESOURCE_ID);

        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-NSP-001")
    @Story("Create non-series production")
    @Description("Створення 1 од. несерійного виробництва — перевірка коректного списання сировини з залишків")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateOneUnitDeductsStockCorrectly() {
        double productAmount = 1.0;
        double usagePerUnit = 4.0;
        String product = NonSeriesProductionDataFactory.uniqueProductName();

        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        long recordsBefore = countNonSeriesProductions();

        NonSeriesProductionRequest request = NonSeriesProductionDataFactory.buildInProgressRequest(
                storageId, resourceId, usagePerUnit, productAmount);
        request = request.toBuilder().product(product).build();
        final NonSeriesProductionRequest createRequest = request;

        Response response = Allure.step("POST create non-series production (1 unit)", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE,
                        UserRole.OWNER_1,
                        createRequest));

        assertThat(response.statusCode()).isEqualTo(200);
        AllureHelper.attachSchemaValidationInfo(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, response);
        boolean postSchemaValid = SchemaRegistry.validateIfSuccessSoft(
                response, ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE);

        NonSeriesProductionResponse created = response.as(NonSeriesProductionResponse.class);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getProduct()).isEqualTo(product);
        assertThat(created.getAmount().doubleValue()).isCloseTo(productAmount, within(0.01));
        assertThat(created.getStatus()).isEqualTo(NonSeriesProductionStatus.IN_PROGRESS);

        Allure.step("Verify stock decreased by usagePerUnit × productAmount", () -> {
            double expectedDeduction = usagePerUnit * productAmount;
            double stockAfter = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfter).isCloseTo(stockBefore - expectedDeduction, within(0.01));
            Allure.parameter("stockBefore", stockBefore);
            Allure.parameter("stockAfter", stockAfter);
            Allure.parameter("expectedDeduction", expectedDeduction);
        });

        Allure.step("Verify journal count increased", () -> {
            long recordsAfter = countNonSeriesProductions();
            assertThat(recordsAfter).isEqualTo(recordsBefore + 1);
        });

        Allure.step("Verify GET /non-series-production/{id}?storageId= returns created record", () -> {
            Response getResponse = apiExecutor.execute(
                    ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID,
                    UserRole.OWNER_1,
                    null,
                    String.valueOf(created.getId()),
                    String.valueOf(storageId));
            assertThat(getResponse.statusCode()).isEqualTo(200);
            AllureHelper.attachSchemaValidationInfo(
                    ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID, getResponse);
            boolean getSchemaValid = SchemaRegistry.validateIfSuccessSoft(
                    getResponse, ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID);

            NonSeriesProductionResponse fetched = getResponse.as(NonSeriesProductionResponse.class);
            assertThat(fetched.getId()).isEqualTo(created.getId());
            assertThat(fetched.getProduct()).isEqualTo(product);
            assertThat(fetched.getAmount().doubleValue()).isCloseTo(productAmount, within(0.01));
            assertThat(fetched.getStatus()).isEqualTo(NonSeriesProductionStatus.IN_PROGRESS);
            assertThat(fetched.getResourceUsageList()).hasSize(1);
            assertThat(fetched.getResourceUsageList().getFirst().getResource().getId()).isEqualTo(resourceId);

            assertThat(postSchemaValid)
                    .as("POST response JSON schema")
                    .isTrue();
            assertThat(getSchemaValid)
                    .as("GET by id response JSON schema")
                    .isTrue();
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-NSP-002")
    @Story("Stock validation")
    @Description("Неможливо створити несерійне виробництво, якщо сировини більше ніж є на складі")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotUseMoreResourcesThanAvailable() {
        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        assertThat(stockBefore).isGreaterThan(0.0);

        double productAmount = 1.0;
        double usagePerUnit = stockBefore + 10.0;
        String product = NonSeriesProductionDataFactory.uniqueProductName();
        long recordsBefore = countNonSeriesProductions();

        NonSeriesProductionRequest request = NonSeriesProductionDataFactory.buildInProgressRequest(
                storageId, resourceId, usagePerUnit, productAmount);
        final NonSeriesProductionRequest createRequest = request.toBuilder().product(product).build();

        Response response = Allure.step("POST create with excessive resource usage", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE,
                        UserRole.OWNER_1,
                        createRequest));

        Allure.step("Validate rejection", () -> {
            assertThat(response.statusCode()).isEqualTo(400);
            Allure.parameter("stockBefore", stockBefore);
            Allure.parameter("requestedUsagePerUnit", usagePerUnit);
            Allure.parameter("requestedTotal", usagePerUnit * productAmount);
        });

        Allure.step("Verify stock unchanged", () -> {
            double stockAfter = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfter).isCloseTo(stockBefore, within(0.01));
        });

        Allure.step("Verify no new record in journal", () -> {
            long recordsAfter = countNonSeriesProductions();
            assertThat(recordsAfter).isEqualTo(recordsBefore);
        });
    }

    @Test(priority = 30)
    @TestCaseId("TC-NSP-003")
    @Story("Create non-series production")
    @Description("Створення 2 од. несерійного виробництва — списання сировини = usagePerUnit × 2")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateTwoUnitsDeductsStockCorrectly() {
        double productAmount = 2.0;
        double usagePerUnit = 3.0;
        String product = NonSeriesProductionDataFactory.uniqueProductName();

        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        double expectedDeduction = usagePerUnit * productAmount;

        assertThat(stockBefore)
                .as("На складі має бути достатньо сировини для тесту (потрібно %.2f)", expectedDeduction)
                .isGreaterThanOrEqualTo(expectedDeduction);

        NonSeriesProductionRequest request = NonSeriesProductionDataFactory.buildInProgressRequest(
                storageId, resourceId, usagePerUnit, productAmount);
        request = request.toBuilder().product(product).build();
        final NonSeriesProductionRequest createRequest = request;

        Response response = Allure.step("POST create non-series production (2 units)", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE,
                        UserRole.OWNER_1,
                        createRequest));

        assertThat(response.statusCode()).isEqualTo(200);
        AllureHelper.attachSchemaValidationInfo(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, response);
        boolean postSchemaValid = SchemaRegistry.validateIfSuccessSoft(
                response, ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE);

        NonSeriesProductionResponse created = response.as(NonSeriesProductionResponse.class);
        assertThat(created.getAmount().doubleValue()).isCloseTo(productAmount, within(0.01));

        Allure.step("Verify stock decreased by usagePerUnit × 2", () -> {
            double stockAfter = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfter).isCloseTo(stockBefore - expectedDeduction, within(0.01));
            Allure.parameter("stockBefore", stockBefore);
            Allure.parameter("stockAfter", stockAfter);
            Allure.parameter("expectedDeduction", expectedDeduction);
        });

        Allure.step("Verify GET /non-series-production/{id}?storageId= returns created record", () -> {
            Response getResponse = apiExecutor.execute(
                    ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID,
                    UserRole.OWNER_1,
                    null,
                    String.valueOf(created.getId()),
                    String.valueOf(storageId));
            assertThat(getResponse.statusCode()).isEqualTo(200);
            AllureHelper.attachSchemaValidationInfo(
                    ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID, getResponse);
            boolean getSchemaValid = SchemaRegistry.validateIfSuccessSoft(
                    getResponse, ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID);

            NonSeriesProductionResponse fetched = getResponse.as(NonSeriesProductionResponse.class);
            assertThat(fetched.getProduct()).isEqualTo(product);
            assertThat(fetched.getAmount().doubleValue()).isCloseTo(productAmount, within(0.01));
            assertThat(fetched.getResourceUsageList()).hasSize(1);

            assertThat(postSchemaValid)
                    .as("POST response JSON schema")
                    .isTrue();
            assertThat(getSchemaValid)
                    .as("GET by id response JSON schema")
                    .isTrue();
        });
    }

    private long countNonSeriesProductions() {
        Response listResponse = apiExecutor.execute(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_ALL,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        return DatabaseIntegrityValidator.extractPageTotalElements(listResponse);
    }
}
