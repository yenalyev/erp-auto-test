package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.ResourceDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.data.DataUtils;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional suite for Resource lifecycle and data integrity.
 * * <p>Key Validations:
 * <ul>
 * <li><b>Happy Path:</b> Resource creation with Measurement Unit link and schema validation.</li>
 * <li><b>Data Quality:</b> Mandatory fields (name, unitId) and string length limits.</li>
 * <li><b>Data Integrity:</b> Unique name constraints and DB state consistency.</li>
 * </ul>
 */
@Slf4j
@Epic("Master Data")
@Feature("Resources")
public class ResourceTest extends BaseFunctionalTest {

    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    @Step("Setup environment for Resource Functional tests")
    public void setupResourceTest() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        // Гарантуємо наявність одиниць виміру, бо без них не створити ресурс
        resourceFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-RES-001")
    @Story("Create Resource")
    @Description("Успішне створення ресурсу та перевірка зв'язку з Measurement Unit")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateResource() {
        // 1. Arrange: Отримуємо ID існуючої одиниці виміру з контексту
        ResourceResponse sharedResource = testContext.get(ContextKey.SHARED_RESOURCE);
        ResourceRequest requestBody = ResourceDataFactory.defaultResource(sharedResource.getId()).build();

        // 2. Act: Створення ресурсу
        Response response = Allure.step("STEP 1: Створення ресурсу через POST", () ->
                apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, requestBody)
        );

        // 3. Assert: Валідація
        Allure.step("STEP 2: Валідація статус-коду та схеми", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.RESOURCE_CREATE, response);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_CREATE);
        });

        // Використовуємо універсальний метод верифікації
        verifyCreatedResource(response, requestBody);
    }

    @DataProvider(name = "invalidResourceProvider")
    public Object[][] invalidResourceData() {
        Long unitId = testContext.get(ContextKey.SHARED_UNIT_ID);

        return new Object[][] {
                { ResourceRequest.builder().name(null).measurementUnitId(unitId).build(), 400, "Name is NULL" },
                { ResourceRequest.builder().name("").measurementUnitId(unitId).build(), 400, "Name is EMPTY" },
                { ResourceRequest.builder().name(DataUtils.generateWithUniqueSuffix(256))
                        .measurementUnitId(null).build(), 400, "Unit ID is NULL" },
                { ResourceRequest.builder().name(DataUtils.generateWithUniqueSuffix(256))
                        .measurementUnitId(unitId).build(), 400, "Name is TOO LONG" }
        };
    }

    @Test(dataProvider = "invalidResourceProvider", priority = 20)
    @TestCaseId("TC-RES-002")
    @Story("Validation Rules")
    @Description("Негативні тести валідації полів ресурсу")
    public void testCreateResourceNegative(ResourceRequest requestBody, int expectedStatus, String description) {
        Allure.getLifecycle().updateTestCase(tc -> tc.setName("Negative: " + description));

        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, requestBody);

        assertThat(response.statusCode()).isEqualTo(expectedStatus);

        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage).as("Error message should be present").isNotEmpty();
    }

    // =========================================================================
    // ТЕСТ 3: Дублікати (DataProvider + Integrity Check)
    // =========================================================================

    @DataProvider(name = "duplicateResourceProvider")
    public Object[][] duplicateResourceData() {
        // 1. Дістаємо ресурси, які вже створила фікстура BaseFixture.setupSharedResourceList
        List<ResourceResponse> existingResources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        ResourceResponse target = existingResources.getFirst();

        // 2. Дістаємо юніти, щоб створити сценарій з іншим UnitID
        List<MeasurementUnitResponse> units = testContext.get(ContextKey.SHARED_MEASUREMENT_UNIT_LIST);
        Long differentUnitId = units.stream()
                .map(MeasurementUnitResponse::getId)
                .filter(id -> !id.equals(target.getUnit().getId()))
                .findFirst()
                .orElse(target.getUnit().getId());

        return new Object[][] {
                {
                        ResourceRequest.builder()
                                .name(target.getName())
                                .measurementUnitId(target.getUnit().getId())
                                .build(),
                        "Full Duplicate (Same Name and Same Unit)"
                },
                {
                        ResourceRequest.builder()
                                .name(target.getName())
                                .measurementUnitId(differentUnitId)
                                .build(),
                        "Duplicate Name with different Unit ID"
                }
        };
    }

    @Test(dataProvider = "duplicateResourceProvider", priority = 30)
    @TestCaseId("TC-RES-003")
    @Story("Validation Rules - Duplicates")
    @Description("Перевірка обробки дублікатів назви ресурсу")
    public void testCreateDuplicateResource(ResourceRequest duplicateRequest, String scenario) {
        Allure.getLifecycle().updateTestCase(tc -> tc.setName("Negative: " + scenario));

        // 1. Arrange: Фіксуємо кількість записів до "диверсії"
        long countBefore = getDbCount(
                ApiEndpointDefinition.RESOURCE_GET_ALL,
                UserRole.ADMIN,
                ResourceResponse.class,
                r -> StringUtils.equalsIgnoreCase(r.getName(), duplicateRequest.getName())
        );

        // 2. Act: Спроба створення (зберігаємо Response)
        Response response = Allure.step("Спроба створити дублікат через POST", () ->
                apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, duplicateRequest)
        );

        // 3. Assert: Перевірка статусу та цілісності
        Allure.step("Верифікація результатів", () -> {
            assertThat(response.statusCode())
                    .as("Сервер мав повернути помилку 400 для дубліката (" + scenario + ")")
                    .isEqualTo(400);

            assertDbUnchanged(
                    ApiEndpointDefinition.RESOURCE_GET_ALL,
                    UserRole.ADMIN,
                    countBefore,
                    ResourceResponse.class,
                    r -> StringUtils.equalsIgnoreCase(r.getName(), duplicateRequest.getName())
            );
        });
    }

    /**
     * Спеціалізована верифікація для Resource, оскільки поле 'unit' у Response
     * не мапиться автоматично на 'measurementUnitId' з Request через рефлексію.
     */
    private void verifyCreatedResource(Response response, ResourceRequest request) {
        verifyCreatedEntity(response, request, ApiEndpointDefinition.RESOURCE_GET_ALL, ResourceResponse.class);

        // Додаткова перевірка специфічного мапінгу unitId -> unit.id
        ResourceResponse actualResponse = response.as(ResourceResponse.class);
        Allure.step("Додаткова перевірка зв'язку з Measurement Unit", () -> {
            assertThat(actualResponse.getUnit().getId())
                    .as("ID одиниці виміру у відповіді має збігатися з запитом")
                    .isEqualTo(request.getMeasurementUnitId());
        });
    }
}