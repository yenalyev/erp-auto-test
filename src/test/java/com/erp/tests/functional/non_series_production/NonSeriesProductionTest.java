package com.erp.tests.functional.non_series_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.NonSeriesProductionFixture;
import com.erp.models.query.NonSeriesProductionQuery;
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

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @TestCaseId({
            "TC-NSP-001",
            "TC-NON-SER-MAN-003"
    })
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
    @TestCaseId({
            "TC-NSP-002",
            "TC-NON-SER-MAN-001",
            "TC-NON-SER-MAN-002"
    })
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
    @TestCaseId({
            "TC-NSP-003",
            "TC-NON-SER-MAN-006"
    })
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

    @Test(priority = 40)
    @TestCaseId({
            "TC-NSP-004",
            "TC-NON-SER-MAN-007"
    })
    @Story("Total volume calculation")
    @Description("""
            GET /non-series-production/total має повертати суму об'ємів записів,
            що відповідають фільтрам productSearch та statuses (як і список).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testTotalAmountMatchesFilteredListSum() {
        String searchPrefix = NonSeriesProductionDataFactory.uniqueProductSearchPrefix();
        fixture.ensureStockAtLeast(storageId, resourceId, 20.0);

        Allure.step("Створити тестові записи з різним об'ємом і статусом", () -> {
            fixture.createAs(
                    UserRole.OWNER_1,
                    NonSeriesProductionStatus.IN_PROGRESS,
                    searchPrefix + "-A",
                    2,
                    resourceId,
                    1.0);
            fixture.createAs(
                    UserRole.OWNER_1,
                    NonSeriesProductionStatus.DONE,
                    searchPrefix + "-B",
                    3,
                    resourceId,
                    1.0);
            Allure.parameter("productSearch", searchPrefix);
        });

        NonSeriesProductionQuery baseQuery = NonSeriesProductionQuery.builder()
                .storageId(storageId)
                .productSearch(searchPrefix)
                .pageSize(500)
                .build();

        Allure.step("Без фільтра статусу: total = сума amount у списку", () -> {
            List<NonSeriesProductionResponse> list = fixture.getList(baseQuery);
            BigDecimal listSum = NonSeriesProductionFixture.sumAmounts(list);
            BigDecimal apiTotal = fixture.getTotalAmount(baseQuery);

            assertThat(list).hasSizeGreaterThanOrEqualTo(2);
            assertThat(listSum).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(apiTotal).isEqualByComparingTo(listSum);

            Allure.parameter("listSum", listSum);
            Allure.parameter("apiTotal", apiTotal);
        });

        Allure.step("Фільтр statuses=IN_PROGRESS: total = 2", () -> {
            NonSeriesProductionQuery query = NonSeriesProductionQuery.builder()
                    .storageId(storageId)
                    .productSearch(searchPrefix)
                    .statuses(List.of(NonSeriesProductionStatus.IN_PROGRESS))
                    .pageSize(500)
                    .build();

            BigDecimal listSum = NonSeriesProductionFixture.sumAmounts(fixture.getList(query));
            BigDecimal apiTotal = fixture.getTotalAmount(query);

            assertThat(listSum).isEqualByComparingTo(BigDecimal.valueOf(2));
            assertThat(apiTotal).isEqualByComparingTo(listSum);
        });

        Allure.step("Фільтр statuses=DONE: total = 3", () -> {
            NonSeriesProductionQuery query = NonSeriesProductionQuery.builder()
                    .storageId(storageId)
                    .productSearch(searchPrefix)
                    .statuses(List.of(NonSeriesProductionStatus.DONE))
                    .pageSize(500)
                    .build();

            BigDecimal listSum = NonSeriesProductionFixture.sumAmounts(fixture.getList(query));
            BigDecimal apiTotal = fixture.getTotalAmount(query);

            assertThat(listSum).isEqualByComparingTo(BigDecimal.valueOf(3));
            assertThat(apiTotal).isEqualByComparingTo(listSum);
        });
    }

    @Test(priority = 50)
    @TestCaseId("TC-NON-SER-MAN-009")
    @Story("Delete non-series production")
    @Description("""
            Видалення несерійного виробництва в статусі «В роботі» — сировина повертається на склад,
            запис зникає з журналу (REQ-NON-SER-MAN AC-05).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteInProgressRestoresStockToWarehouse() {
        double productAmount = 2.0;
        double usagePerUnit = 4.0;
        String product = NonSeriesProductionDataFactory.uniqueProductName();
        double expectedDeduction = usagePerUnit * productAmount;

        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        assertThat(stockBefore)
                .as("На складі має бути достатньо сировини для тесту (потрібно %.2f)", expectedDeduction)
                .isGreaterThanOrEqualTo(expectedDeduction);

        long recordsBefore = countNonSeriesProductions();

        NonSeriesProductionResponse created = Allure.step(
                "Створити несерійне виробництво «В роботі» з відомою витратою сировини", () ->
                        fixture.createAs(
                                UserRole.OWNER_1,
                                NonSeriesProductionStatus.IN_PROGRESS,
                                product,
                                productAmount,
                                resourceId,
                                usagePerUnit));

        assertThat(created.getId()).isNotNull();

        Allure.step("Перевірити списання сировини після створення", () -> {
            double stockAfterCreate = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterCreate).isCloseTo(stockBefore - expectedDeduction, within(0.01));
            Allure.parameter("stockBefore", stockBefore);
            Allure.parameter("stockAfterCreate", stockAfterCreate);
            Allure.parameter("expectedDeduction", expectedDeduction);
        });

        Allure.step("Видалити несерійне виробництво", () -> {
            fixture.deleteAs(UserRole.OWNER_1, created.getId(), storageId);
            Allure.parameter("deletedId", created.getId());
        });

        Allure.step("Перевірити повернення сировини на склад", () -> {
            double stockAfterDelete = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterDelete).isCloseTo(stockBefore, within(0.01));
            Allure.parameter("stockAfterDelete", stockAfterDelete);
        });

        Allure.step("Перевірити, що запис видалено з журналу", () -> {
            Response getResponse = apiExecutor.execute(
                    ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID,
                    UserRole.OWNER_1,
                    null,
                    String.valueOf(created.getId()),
                    String.valueOf(storageId));
            assertThat(getResponse.statusCode()).isEqualTo(404);

            long recordsAfter = countNonSeriesProductions();
            assertThat(recordsAfter).isEqualTo(recordsBefore);
        });
    }

    @Test(priority = 60)
    @TestCaseId("TC-NON-SER-MAN-004")
    @Story("Delete completed non-series production")
    @Description("""
            Owner може видалити несерійне виробництво в статусі «Завершено» протягом 2 днів
            з моменту start; сировина повертається на склад (REQ-NON-SER-MAN AC-02).
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testDeleteDoneWithinTwoDaysRestoresStock() {
        double productAmount = 1.0;
        double usagePerUnit = 3.0;
        String product = NonSeriesProductionDataFactory.uniqueProductName();
        double expectedDeduction = usagePerUnit * productAmount;

        fixture.ensureStockAtLeast(storageId, resourceId, expectedDeduction + 5.0);
        double stockBefore = fixture.getResourceStock(storageId, resourceId);

        NonSeriesProductionResponse created = Allure.step(
                "Створити несерійне виробництво зі статусом «Завершено»", () ->
                        fixture.createAs(
                                UserRole.OWNER_1,
                                NonSeriesProductionStatus.DONE,
                                product,
                                productAmount,
                                resourceId,
                                usagePerUnit));

        assertThat(created.getStatus()).isEqualTo(NonSeriesProductionStatus.DONE);
        assertThat(fixture.getResourceStock(storageId, resourceId))
                .isCloseTo(stockBefore - expectedDeduction, within(0.01));

        Allure.step("Видалити запис «Завершено» під Owner", () ->
                fixture.deleteAs(UserRole.OWNER_1, created.getId(), storageId));

        Allure.step("Сировина повернена, запис відсутній", () -> {
            assertThat(fixture.getResourceStock(storageId, resourceId))
                    .isCloseTo(stockBefore, within(0.01));
            Response getResponse = apiExecutor.execute(
                    ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID,
                    UserRole.OWNER_1,
                    null,
                    String.valueOf(created.getId()),
                    String.valueOf(storageId));
            assertThat(getResponse.statusCode()).isEqualTo(404);
        });
    }

    @Test(priority = 70)
    @TestCaseId("TC-NON-SER-MAN-005")
    @Story("Complete non-series production")
    @Description("""
            Перехід «В роботі» → «Завершено» дозволений навіть коли залишок використаного
            ресурсу вже 0 (без повторної валідації залишків) — REQ-NON-SER-MAN AC-07 / CPMA-517.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testTransitionInProgressToDoneWithZeroStock() {
        fixture.ensureStockAtLeast(storageId, resourceId, 5.0);
        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        double usagePerUnit = stockBefore;
        String product = NonSeriesProductionDataFactory.uniqueProductName();

        NonSeriesProductionResponse created = Allure.step(
                "Створити «В роботі» з витратою всього залишку ресурсу", () ->
                        fixture.createAs(
                                UserRole.OWNER_1,
                                NonSeriesProductionStatus.IN_PROGRESS,
                                product,
                                1.0,
                                resourceId,
                                usagePerUnit));

        Allure.step("Залишок ресурсу = 0 після створення", () -> {
            double stockAfterCreate = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterCreate).isCloseTo(0.0, within(0.01));
            Allure.parameter("stockAfterCreate", stockAfterCreate);
        });

        NonSeriesProductionRequest updateRequest = NonSeriesProductionFixture.toUpdateRequest(created, storageId)
                .toBuilder()
                .status(NonSeriesProductionStatus.DONE)
                .build();

        NonSeriesProductionResponse updated = Allure.step(
                "PUT: змінити статус на «Завершено» при нульовому залишку", () ->
                        fixture.updateAs(UserRole.OWNER_1, created.getId(), updateRequest));

        assertThat(updated.getStatus()).isEqualTo(NonSeriesProductionStatus.DONE);
        assertThat(fixture.getResourceStock(storageId, resourceId))
                .as("Після DONE залишок лишається 0 (rollback+produce)")
                .isCloseTo(0.0, within(0.01));
    }

    @Test(priority = 80)
    @TestCaseId("TC-NON-SER-MAN-010")
    @Story("Owner 2-day window for DONE")
    @Description("""
            Owner не може оновлювати/видаляти «Завершено» якщо start старіший за 2 дні;
            Admin може (REQ-NON-SER-MAN AC-02 / AC-03).
            Запис створює Admin із застарілим start (Owner не може створити поза вікном 2 днів).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testOwnerCannotMutateDoneOlderThanTwoDaysButAdminCan() {
        double productAmount = 1.0;
        double usagePerUnit = 2.0;
        fixture.ensureStockAtLeast(storageId, resourceId, usagePerUnit + 5.0);

        LocalDate staleStart = LocalDate.now().minusDays(3);
        String product = NonSeriesProductionDataFactory.uniqueProductName();

        NonSeriesProductionRequest createRequest = NonSeriesProductionDataFactory.buildCreateRequest(
                        storageId,
                        NonSeriesProductionStatus.DONE,
                        product,
                        productAmount,
                        List.of(NonSeriesProductionDataFactory.usage(resourceId, usagePerUnit)))
                .toBuilder()
                .start(staleStart)
                .end(staleStart.plusDays(1))
                .build();

        NonSeriesProductionResponse created = Allure.step(
                "Admin створює «Завершено» зі start старше 2 днів", () -> {
                    Response response = apiExecutor.execute(
                            ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE,
                            UserRole.ADMIN,
                            createRequest);
                    assertThat(response.statusCode()).isEqualTo(200);
                    return response.as(NonSeriesProductionResponse.class);
                });

        assertThat(created.getStatus()).isEqualTo(NonSeriesProductionStatus.DONE);
        assertThat(created.getStart()).isEqualTo(staleStart);

        NonSeriesProductionRequest updateRequest = NonSeriesProductionFixture.toUpdateRequest(created, storageId)
                .toBuilder()
                .description("erp-auto-test owner blocked after 2 days")
                .build();

        Allure.step("Owner PUT на застарілий DONE → 4xx", () -> {
            Response response = fixture.updateRaw(UserRole.OWNER_1, created.getId(), updateRequest);
            assertThat(response.statusCode()).isBetween(400, 499);
            Allure.parameter("ownerPutStatus", response.statusCode());
        });

        Allure.step("Owner DELETE на застарілий DONE → 4xx", () -> {
            Response response = fixture.deleteRaw(UserRole.OWNER_1, created.getId(), storageId);
            assertThat(response.statusCode()).isBetween(400, 499);
            Allure.parameter("ownerDeleteStatus", response.statusCode());
        });

        Allure.step("Admin DELETE на застарілий DONE → success", () -> {
            fixture.deleteAs(UserRole.ADMIN, created.getId(), storageId);
            Response getResponse = apiExecutor.execute(
                    ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID,
                    UserRole.ADMIN,
                    null,
                    String.valueOf(created.getId()),
                    String.valueOf(storageId));
            assertThat(getResponse.statusCode()).isEqualTo(404);
        });
    }

    @Test(priority = 90)
    @TestCaseId("TC-NSP-005")
    @Story("Owner edits IN_PROGRESS without time restriction")
    @Description("""
            Owner може редагувати несерійне виробництво в статусі «В роботі»
            незалежно від давності start (REQ-NON-SER-MAN AC-04 / TC-NSP-005).
            Контраст: для «Завершено» діє вікно 2 дні (див. TC-NON-SER-MAN-010).
            Arrange: Admin створює IN_PROGRESS зі start старше 2 днів
            (Owner не може створити поза вікном при create).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testOwnerCanEditInProgressOlderThanTwoDays() {
        double productAmount = 1.0;
        double usagePerUnit = 2.0;
        fixture.ensureStockAtLeast(storageId, resourceId, usagePerUnit + 5.0);

        LocalDate staleStart = LocalDate.now().minusDays(3);
        String product = NonSeriesProductionDataFactory.uniqueProductName();
        String updatedDescription = "erp-auto-test owner edit IN_PROGRESS after 2 days";

        NonSeriesProductionRequest createRequest = NonSeriesProductionDataFactory.buildCreateRequest(
                        storageId,
                        NonSeriesProductionStatus.IN_PROGRESS,
                        product,
                        productAmount,
                        List.of(NonSeriesProductionDataFactory.usage(resourceId, usagePerUnit)))
                .toBuilder()
                .start(staleStart)
                .end(staleStart.plusDays(1))
                .build();

        NonSeriesProductionResponse created = Allure.step(
                "Admin створює «В роботі» зі start старше 2 днів", () -> {
                    Response response = apiExecutor.execute(
                            ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE,
                            UserRole.ADMIN,
                            createRequest);
                    assertThat(response.statusCode()).isEqualTo(200);
                    return response.as(NonSeriesProductionResponse.class);
                });

        assertThat(created.getStatus()).isEqualTo(NonSeriesProductionStatus.IN_PROGRESS);
        assertThat(created.getStart()).isEqualTo(staleStart);

        NonSeriesProductionRequest updateRequest = NonSeriesProductionFixture.toUpdateRequest(created, storageId)
                .toBuilder()
                .description(updatedDescription)
                .build();

        NonSeriesProductionResponse updated = Allure.step(
                "Owner PUT на застарілий IN_PROGRESS → 200", () ->
                        fixture.updateAs(UserRole.OWNER_1, created.getId(), updateRequest));

        assertThat(updated.getStatus()).isEqualTo(NonSeriesProductionStatus.IN_PROGRESS);
        assertThat(updated.getDescription()).isEqualTo(updatedDescription);

        Allure.step("GET by id підтверджує збережені зміни", () -> {
            NonSeriesProductionResponse fetched = fixture.getById(created.getId(), storageId);
            assertThat(fetched.getDescription()).isEqualTo(updatedDescription);
            assertThat(fetched.getStatus()).isEqualTo(NonSeriesProductionStatus.IN_PROGRESS);
        });
    }

    private long countNonSeriesProductions() {
        NonSeriesProductionQuery query = NonSeriesProductionQuery.builder()
                .storageId(storageId)
                .pageSize(500)
                .build();
        Response listResponse = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_ALL,
                UserRole.OWNER_1,
                query.toListQueryParams());
        return DatabaseIntegrityValidator.extractPageTotalElements(listResponse);
    }
}
