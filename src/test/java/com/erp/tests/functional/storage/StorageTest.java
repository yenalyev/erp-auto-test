package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Master Data")
@Feature("Storages")
public class StorageTest extends BaseFunctionalTest {

    private StorageFixture storageFixture;

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка середовища для тестів складів")
    public void setupStorageTest() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        if (testContext == null) {
            baseTestClassSetup();
        }
        // Ініціалізуємо саме StorageFixture
        storageFixture = new StorageFixture(testContext, apiExecutor);

        // Готуємо дані (створюємо DYNAMIC_STORAGE, SHARED_STORAGE_LIST)
        storageFixture.prepareContext();

        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-001")
    @Story("Create Storage")
    @Description("Успішне створення складу та перевірка його персистенції")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateStorage() {
        // 1. Arrange
        StorageRequest requestBody = StorageDataFactory.randomStorage().build();

        // 2. Act
        Response response = Allure.step("STEP 1: Створення складу через POST", () ->
                apiExecutor.execute(ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, requestBody)
        );

        // 3. Assert
        Allure.step("STEP 2: Валідація статусу та схеми", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_POST_CREATE, response);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_POST_CREATE);
        });

        verifyCreatedEntity(
                response,
                requestBody,
                ApiEndpointDefinition.STORAGE_GET_ALL,
                StorageResponse.class
        );
    }

    @DataProvider(name = "invalidStorageProvider")
    public Object[][] invalidStorageData() {
        return new Object[][] {
                { StorageRequest.builder().name(null).build(), 400, "Name is NULL" },
                { StorageRequest.builder().name("").build(), 400, "Name is EMPTY" },
                { StorageRequest.builder().name(DataUtils.generateWithUniqueSuffix(256)).build(), 400, "Name is TOO LONG" }
        };
    }

    @Test(dataProvider = "invalidStorageProvider", priority = 20)
    @TestCaseId("TC-STR-002")
    @Story("Validation Rules")
    @Description("Перевірка валідації назви складу")
    public void testCreateStorageNegative(StorageRequest requestBody, int expectedStatus, String description) {
        Allure.getLifecycle().updateTestCase(tc -> tc.setName("Negative: " + description));

        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, requestBody);

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-003")
    @Story("Validation Rules - Duplicates")
    @Description("Заборона створення дублікатів складів")
    public void testCreateDuplicateStorage() {
        // 1. Arrange
        StorageResponse existing = testContext.get(ContextKey.DYNAMIC_STORAGE);
        StorageRequest duplicateRequest = StorageRequest.builder().name(existing.getName()).build();

        long countBefore = getDbCount(
                ApiEndpointDefinition.STORAGE_GET_ALL,
                UserRole.ADMIN,
                StorageResponse.class,
                s -> StringUtils.equalsIgnoreCase(s.getName(), duplicateRequest.getName())
        );

        // 2. Act
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, duplicateRequest);

        // 3. Assert
        assertThat(response.statusCode()).as("Очікувався статус 400 для дубліката").isEqualTo(400);

        assertDbUnchanged(
                ApiEndpointDefinition.STORAGE_GET_ALL,
                UserRole.ADMIN,
                countBefore,
                StorageResponse.class,
                s -> StringUtils.equalsIgnoreCase(s.getName(), duplicateRequest.getName())
        );
    }
}