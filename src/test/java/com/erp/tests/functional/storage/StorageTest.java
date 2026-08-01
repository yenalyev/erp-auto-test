package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Master Data")
@Feature("Storages")
public class StorageTest extends StorageApiTestBase {

    @BeforeClass(alwaysRun = true)
    @Step("Підготовка середовища для тестів локацій")
    public void setupStorageTest() {
        storageFixture.prepareContext();
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-001")
    @Story("Create Storage")
    @Description("Успішне створення дочірньої локації з parentId, type=STORAGE, relation=INTERNAL")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateStorage() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageRequest requestBody = StorageDataFactory.childStorage(parent.getId(), "create-").build();

        Response response = Allure.step("STEP 1: Створення локації через POST", () ->
                apiExecutor.execute(ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, requestBody)
        );

        Allure.step("STEP 2: Валідація статусу та схеми", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_POST_CREATE, response);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_POST_CREATE);
        });

        Allure.step("STEP 3: Перевірка parent у відповіді", () -> {
            StorageResponse created = response.as(StorageResponse.class);
            storageFixture.trackForCleanup(created.getId());
            assertThat(created.getParent()).isNotNull();
            assertThat(created.getParent().getId()).isEqualTo(parent.getId());
            assertThat(created.getActive()).isTrue();
        });

        verifyEntityViaGetById(
                response,
                requestBody,
                ApiEndpointDefinition.STORAGE_GET_BY_ID,
                StorageResponse.class
        );
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-002")
    @Story("Update Storage")
    @Description("""
            PUT з усіма полями StorageRequest. Змінюємо name, alias, parentId, type,
            identifierNumber, accessMode, nameForInvoices. relation — без змін.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testUpdateStorage() {
        StorageResponse created = storageFixture.createUniqueStorage("upd-");
        StorageResponse fullExisting = storageFixture.getById(UserRole.ADMIN, created.getId());
        StorageRelation originalRelation = StorageRelation.valueOf(fullExisting.getRelation());
        Long currentParentId = fullExisting.getParent() != null ? fullExisting.getParent().getId() : null;

        StorageResponse newParent = storageFixture.resolveAlternateParent(currentParentId, created.getId());
        StorageRequest updateBody = StorageDataFactory.buildUpdateAllExceptRelation(
                fullExisting, newParent.getId());

        assertThat(updateBody.getRelation())
                .as("relation не змінюється при оновленні")
                .isEqualTo(originalRelation);

        Response response = Allure.step("STEP 1: Оновлення локації через PUT", () ->
                storageFixture.update(UserRole.ADMIN, created.getId(), updateBody)
        );

        Allure.step("STEP 2: Валідація статусу та схеми", () -> {
            if (response.statusCode() != 200) {
                AllureHelper.attachResponseDetails(response);
            }
            assertThat(response.statusCode()).isEqualTo(200);
            AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_PUT_UPDATE, response);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_PUT_UPDATE);
        });

        Allure.step("STEP 3: relation не змінився після PUT", () -> {
            StorageResponse updated = response.as(StorageResponse.class);
            assertThat(updated.getRelation()).isEqualTo(originalRelation.name());
        });

        Allure.step("STEP 4: оновлені поля в response", () -> {
            StorageResponse updated = response.as(StorageResponse.class);
            assertThat(updated.getName()).isEqualTo(updateBody.getName());
            assertThat(updated.getAlias()).isEqualTo(updateBody.getAlias());
            assertThat(updated.getType()).isEqualTo(updateBody.getType().name());
            assertThat(updated.getParent().getId()).isEqualTo(updateBody.getParentId());
            assertThat(updated.getIdentifierNumber()).isEqualTo(updateBody.getIdentifierNumber());
            assertThat(updated.getAccessMode()).isEqualTo(updateBody.getAccessMode().name());
            assertThat(updated.getNameForInvoices()).isEqualTo(updateBody.getNameForInvoices());
        });

        verifyUpdatedEntity(
                response,
                updateBody,
                ApiEndpointDefinition.STORAGE_GET_BY_ID,
                StorageResponse.class
        );
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-003")
    @Story("Validation Rules - Duplicates")
    @Description("Заборона створення дублікатів за назвою серед активних локацій")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateDuplicateStorage() {
        StorageResponse fromContext = testContext.get(ContextKey.DYNAMIC_STORAGE);
        if (fromContext == null || fromContext.getId() == null) {
            throw new SkipException("DYNAMIC_STORAGE відсутній у контексті — duplicate-name тест неможливий");
        }
        StorageResponse existing = storageFixture.getById(UserRole.ADMIN, fromContext.getId());
        if (!Boolean.TRUE.equals(existing.getActive())) {
            throw new SkipException("DYNAMIC_STORAGE id=" + existing.getId() + " уже не active — duplicate-name серед активних не перевірити");
        }

        // Full body clone: name-uniqueness must fail before other required-field NPEs → 500.
        StorageRequest.StorageRequestBuilder duplicateBuilder = StorageRequest.builder()
                .name(existing.getName())
                .alias(existing.getAlias())
                .identifierNumber(existing.getIdentifierNumber())
                .nameForInvoices(existing.getNameForInvoices());
        if (existing.getType() != null) {
            duplicateBuilder.type(UnitType.valueOf(existing.getType()));
        }
        if (existing.getRelation() != null) {
            duplicateBuilder.relation(StorageRelation.valueOf(existing.getRelation()));
        }
        if (existing.getAccessMode() != null) {
            duplicateBuilder.accessMode(StorageAccessMode.valueOf(existing.getAccessMode()));
        }
        if (existing.getParent() != null) {
            duplicateBuilder.parentId(existing.getParent().getId());
        }
        StorageRequest duplicateRequest = duplicateBuilder.build();

        long countBefore = getDbCount(
                ApiEndpointDefinition.STORAGE_GET_ALL,
                UserRole.ADMIN,
                StorageResponse.class,
                s -> StringUtils.equalsIgnoreCase(s.getName(), duplicateRequest.getName())
                        && Boolean.TRUE.equals(s.getActive())
        );

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, duplicateRequest);

        Allure.step("Assert: дублікат відхилено", () -> {
            assertThat(response.statusCode())
                    .as("Очікувався статус 400 для дубліката; body=%s", response.body().asString())
                    .isEqualTo(400);
            StorageFixture.assertValidationError(response, "name", "вже існує");
        });

        assertDbUnchanged(
                ApiEndpointDefinition.STORAGE_GET_ALL,
                UserRole.ADMIN,
                countBefore,
                StorageResponse.class,
                s -> StringUtils.equalsIgnoreCase(s.getName(), duplicateRequest.getName())
                        && Boolean.TRUE.equals(s.getActive())
        );
    }

    @DataProvider(name = "invalidStorageProvider")
    public Object[][] invalidStorageData() {
        return new Object[][] {
                { StorageRequest.builder().name(null).build(), "Name is NULL" },
                { StorageRequest.builder().name("").build(), "Name is EMPTY" },
                { StorageRequest.builder().name("   ").build(), "Name is BLANK" }
        };
    }

    @Test(dataProvider = "invalidStorageProvider", priority = 40)
    @TestCaseId("TC-STR-004")
    @Story("Validation Rules")
    @Description("Перевірка валідації обов'язковості поля name")
    public void testCreateStorageNegative(StorageRequest requestBody, String description) {
        Allure.getLifecycle().updateTestCase(tc -> tc.setName("Negative: " + description));

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, requestBody);

        Allure.step("Assert: валідація name", () -> {
            assertThat(response.statusCode()).isEqualTo(400);
            StorageFixture.assertValidationError(response, "name", "обов'язковим");
        });
    }

    @Test(priority = 50)
    @TestCaseId("TC-STR-005")
    @Story("Validation Rules - Relation")
    @Description("Заборона зміни relation з INTERNAL на EXTERNAL при оновленні")
    @Severity(SeverityLevel.NORMAL)
    public void testUpdateRelationInternalToExternalRejected() {
        StorageResponse existing = storageFixture.createUniqueStorage("rel-");
        assertThat(existing.getRelation()).isEqualTo(StorageRelation.INTERNAL.name());

        StorageRequest illegalUpdate = StorageDataFactory.updateFromExisting(existing, builder ->
                builder.relation(StorageRelation.EXTERNAL));

        Response response = storageFixture.update(UserRole.ADMIN, existing.getId(), illegalUpdate);

        Allure.step("Assert: перехід INTERNAL→EXTERNAL заборонено", () -> {
            assertThat(response.statusCode()).isEqualTo(400);
            StorageFixture.assertValidationError(response, "relation", "заборонена");
        });

        StorageResponse unchanged = storageFixture.getById(UserRole.ADMIN, existing.getId());
        assertThat(unchanged.getRelation()).isEqualTo(StorageRelation.INTERNAL.name());
    }
}
