package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional suite for storage deactivation (archive) and reactivation (unarchive).
 *
 * <p>Unlike resources, the backend does not block deactivation when stock or links exist.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Deactivate storage")
public class StorageDeactivationTest extends StorageApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-STR-006")
    @Description("""
            ADMIN архівує ізольовану локацію.
            Після деактивації active=false, запис відсутній у /names?isActive=true
            і присутній у /names?isActive=false.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testDeactivateStorageHidesFromActiveNames() {
        StorageResponse storage = Allure.step("Arrange: створити ізольовану локацію", () ->
                storageFixture.createUniqueStorage("deact-"));

        String searchTerm = storage.getName();

        Response deactivateResponse = Allure.step("Act: DELETE /storages/{id}", () ->
                storageFixture.deactivate(UserRole.ADMIN, storage.getId()));

        Allure.step("Assert: деактивація успішна", () -> {
            assertThat(deactivateResponse.statusCode()).isEqualTo(200);
            StorageResponse byId = storageFixture.getById(UserRole.ADMIN, storage.getId());
            assertThat(byId.getActive()).isFalse();
        });

        Allure.step("Assert: відсутня серед активних імен", () ->
                assertThat(storageFixture.isPresentInNames(UserRole.ADMIN, storage.getId(), true, searchTerm))
                        .as("Архівована локація не повинна бути в isActive=true")
                        .isFalse());

        Allure.step("Assert: присутня серед архівованих імен", () ->
                assertThat(storageFixture.isPresentInNames(UserRole.ADMIN, storage.getId(), false, searchTerm))
                        .as("Архівована локація повинна бути в isActive=false")
                        .isTrue());
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-007")
    @Description("Після unarchive локація знову active=true і видима в активному списку імен")
    @Severity(SeverityLevel.CRITICAL)
    public void testUnarchiveStorageRestoresActiveNames() {
        StorageResponse storage = storageFixture.createUniqueStorage("unarc-");
        String searchTerm = storage.getName();

        Response deactivateResponse = storageFixture.deactivate(UserRole.ADMIN, storage.getId());
        assertThat(deactivateResponse.statusCode()).isEqualTo(200);

        Response unarchiveResponse = Allure.step("Act: PUT /storages/unarchive/{id}", () ->
                storageFixture.unarchive(UserRole.ADMIN, storage.getId()));

        Allure.step("Assert: розархівація успішна", () -> {
            assertThat(unarchiveResponse.statusCode()).isEqualTo(200);
            StorageResponse byId = storageFixture.getById(UserRole.ADMIN, storage.getId());
            assertThat(byId.getActive()).isTrue();
        });

        Allure.step("Assert: знову в активному списку імен", () ->
                assertThat(storageFixture.isPresentInNames(UserRole.ADMIN, storage.getId(), true, searchTerm))
                        .isTrue());
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-008")
    @Description("Після архівації можна створити нову активну локацію з тим самим ім'ям")
    @Severity(SeverityLevel.NORMAL)
    public void testDeactivatedNameCanBeReusedForNewStorage() {
        StorageResponse original = storageFixture.createUniqueStorage("reuse-");
        String reusedName = original.getName();

        Response deactivateResponse = storageFixture.deactivate(UserRole.ADMIN, original.getId());
        assertThat(deactivateResponse.statusCode()).isEqualTo(200);

        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageRequest recreateRequest = StorageDataFactory.childStorage(parent.getId())
                .name(reusedName)
                .build();

        Response createResponse = Allure.step("Act: POST нова локація з архівною назвою", () ->
                apiExecutor.execute(ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, recreateRequest));

        Allure.step("Assert: створення з повторною назвою дозволено", () -> {
            assertThat(createResponse.statusCode()).isEqualTo(200);
            StorageResponse recreated = createResponse.as(StorageResponse.class);
            storageFixture.trackForCleanup(recreated.getId());
            assertThat(recreated.getName()).isEqualTo(reusedName);
            assertThat(recreated.getId()).isNotEqualTo(original.getId());
            assertThat(recreated.getActive()).isTrue();
        });
    }
}
