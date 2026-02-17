package com.erp.tests.functional.measurement_unit;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.measurement_unit.MeasurementUnitRequestDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.MeasurementUnitFixture;
import com.erp.models.request.MeasurementUnitRequest;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.data.DataUtils;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.qameta.allure.testng.Tag;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional suite for Measurement Units lifecycle and data integrity.
 * * <p>Key Validations:
 * <ul>
 * <li><b>Happy Path:</b> Entity creation, JSON contract compliance, and DB persistence.</li>
 * <li><b>Data Quality:</b> Mandatory fields (null/empty) and boundary value analysis.</li>
 * <li><b>Data Integrity:</b> Uniqueness constraints and prevention of "phantom" records.</li>
 * </ul>
 * * <p>Implementation Details:
 * <ul>
 * <li><b>Thread-Safety:</b> Uses {@code getUniqueSuffix()} for parallel execution.</li>
 * <li><b>Contract Testing:</b> Integrated with {@link com.erp.validators.SchemaRegistry}.</li>
 * <li><b>Integrity Check:</b> Uses relative count methodology to verify DB state after failures.</li>
 * </ul>
 * * @author Max_710
 * @since 2026-01-10
 */

@Slf4j
@Epic("Master Data")
@Feature("Measurement Units")
@Tag("functional")
public class MeasurementUnitTest extends BaseFunctionalTest {

    private MeasurementUnitFixture muFixture;

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка середовища для тестів одиниць виміру")
    public void setupMeasurementUnitTest() {
        if (testContext == null) {
            log.warn("testContext was null, initializing manually");
            baseTestClassSetup();
        }
        muFixture = new MeasurementUnitFixture(testContext, apiExecutor);
        muFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();
    }

    // =========================================================================
    // ТЕСТ 1: Позитивне створення
    // =========================================================================

    @Test(priority = 10)
    @TestCaseId("TC-MU-001")
    @Story("Create Measurement Unit")
    @Description("Успішне створення Measurement Unit з валідацією через GET та JSON Schema")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateMeasurementUnit() {
        MeasurementUnitRequest requestBody = MeasurementUnitRequestDataFactory.createRandom().build();

        Response response = Allure.step("STEP 1: Створення одиниці виміру через POST", () ->
                apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE, UserRole.ADMIN, requestBody)
        );

        Allure.step("STEP 2: Валідація контракту (Status Code & JSON Schema)", () -> {
            assertThat(response.statusCode())
                    .as("Очікувався успішний статус код 200")
                    .isEqualTo(200);

            // Response validation (JSON Schema Validator) to Allure report
            AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE, response);

            // Response validation (JSON Schema Validator)
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE);
        });

        // Verified object from database
        verifyCreatedEntity(
                response,
                requestBody,
                ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL,
                MeasurementUnitResponse.class
        );
    }

    // =========================================================================
    // ТЕСТ 2: Невалідні дані (DataProvider)
    // =========================================================================

    @DataProvider(name = "invalidMeasurementUnitProvider")
    public Object[][] invalidMeasurementUnitData() {
        String unique = DataUtils.getUniqueSuffix();
        return new Object[][] {
                { MeasurementUnitRequest.builder().name(null).shortName("kg" + unique).build(), 400, "Name is NULL" },
                { MeasurementUnitRequest.builder().name("").shortName("kg" + unique).build(), 400, "Name is EMPTY" },
                { MeasurementUnitRequest.builder().name("Test-name" + unique).shortName(null).build(), 400, "ShortName is NULL" },
                { MeasurementUnitRequest.builder().name("Test-name" + unique).shortName("").build(), 400, "ShortName is EMPTY" },
                { MeasurementUnitRequest.builder().name("A".repeat(256)).shortName("kg" + unique).build(), 400, "Name is TOO LONG (256)" },
                { MeasurementUnitRequest.builder().name(unique).shortName("S".repeat(256)).build(), 400, "ShortName is TOO LONG (256)" }
        };
    }

    @Test(dataProvider = "invalidMeasurementUnitProvider", priority = 20)
    @TestCaseId("TC-MU-002")
    @Story("Validation Rules")
    @Description("Валідація некоректних вхідних даних (null, empty, long)")
    public void testCreateMeasurementUnitNegative(MeasurementUnitRequest requestBody, int expectedStatus, String description) {
        Allure.getLifecycle().updateTestCase(tc -> tc.setName("Negative: " + description));

        Response response = Allure.step("Запит із некоректними даними", () ->
                apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE, UserRole.ADMIN, requestBody)
        );

        Allure.step("Перевірка помилки валідації", () -> {
            assertThat(response.statusCode()).isEqualTo(expectedStatus);
            String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
            assertThat(errorMessage).as("Повідомлення про помилку у відповіді").isNotEmpty();
        });
    }

    // =========================================================================
    // ТЕСТ 3: Дублікати (DataProvider + Integrity Check)
    // =========================================================================

    @DataProvider(name = "duplicateMeasurementUnitProvider")
    public Object[][] duplicateMeasurementUnitData() {
        List<MeasurementUnitResponse> existingUnits = testContext.get(ContextKey.SHARED_MEASUREMENT_UNIT_LIST);
        MeasurementUnitResponse target = existingUnits.get(0);
        String unique = DataUtils.getUniqueSuffix();

        return new Object[][] {
                { MeasurementUnitRequest.builder()
                        .name(target.getName())
                        .shortName("sh" + unique).build(), "Duplicate Name" },

                { MeasurementUnitRequest.builder()
                        .name("Name" + unique)
                        .shortName(target.getShortName()).build(), "Duplicate Short Name" },

                { MeasurementUnitRequest.builder()
                        .name(target.getName())
                        .shortName(target.getShortName()).build(), "Duplicate ShortName and Name" }
        };
    }

    @Test(dataProvider = "duplicateMeasurementUnitProvider", priority = 30)
    @TestCaseId("TC-MU-003")
    @Story("Validation Rules - Duplicates")
    @Description("Перевірка обробки дублікатів та цілісності БД")
    public void testCreateDuplicateMeasurementUnit(MeasurementUnitRequest requestBody, String scenario) {
        Allure.getLifecycle().updateTestCase(tc -> tc.setName("Negative: " + scenario));

        // 1. Arrange: Фіксуємо стан
        long countBefore = Allure.step("Зафіксувати кількість існуючих записів для дубліката", () -> {
            Response resp = apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL, UserRole.ADMIN);
            return resp.jsonPath().getList("", MeasurementUnitResponse.class).stream()
                    .filter(u -> u.getName().equalsIgnoreCase(requestBody.getName()) ||
                            u.getShortName().equalsIgnoreCase(requestBody.getShortName()))
                    .count();
        });

        // 2. Act: Спроба створення
        Allure.step("Спроба створити дублікат (очікуємо 400)", () -> {
            Response response = apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_POST_CREATE, UserRole.ADMIN, requestBody);
            assertThat(response.statusCode()).isEqualTo(400);
        });

        // 3. Assert: Використовуємо метод з BaseTest
        assertDatabaseCountUnchanged(
                ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL,
                countBefore,
                MeasurementUnitResponse.class,
                u -> u.getName().equalsIgnoreCase(requestBody.getName()) ||
                        u.getShortName().equalsIgnoreCase(requestBody.getShortName())
        );
    }
}