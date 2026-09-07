package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.ResourceDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.AllureHelper;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API: видалення фото ресурсу через PUT {@code removeImage=true}.
 */
@Slf4j
@Epic("Master Data")
@Feature("Resources")
public class ResourceImageApiTest extends BaseFunctionalTest {

    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    @Step("Setup environment for Resource image tests")
    public void setupResourceImageTest() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
    }

    @Test
    @TestCaseId("TC-RES-030")
    @Story("Delete resource photo")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Ресурс — видалити фото: POST /resources/{id}/image зберігає фото,
            PUT /resources/{id} з removeImage=true прибирає його.
            Очікування: GET by id без imagePath; GET /resources/{id}/image → 404.
            """)
    public void updateRemovesResourcePhoto() {
        Long categoryId = testContext.get(ContextKey.SHARED_RESOURCE_CATEGORY_ID);

        ResourceResponse created = Allure.step("Arrange: створити ресурс без фото", () ->
                resourceFixture.createUniqueResource("res-photo-"));
        Long resourceId = created.getId();
        Allure.parameter("resourceId", resourceId);
        Allure.parameter("name", created.getName());

        Allure.step("Act: POST /resources/{id}/image — завантажити PNG", () -> {
            resourceFixture.uploadPng(UserRole.ADMIN, resourceId);
            ResourceResponse withPhoto = resourceFixture.getById(UserRole.ADMIN, resourceId);
            Allure.addAttachment("imagePath after upload", String.valueOf(withPhoto.getImagePath()));
            assertThat(withPhoto.getImagePath())
                    .as("Після upload GET by id має містити imagePath")
                    .isEqualTo("resources/" + resourceId + "/image");
            Response image = resourceFixture.getImage(UserRole.ADMIN, resourceId);
            AllureHelper.attachResponseDetails(image);
            assertThat(image.statusCode())
                    .as("GET фото після upload має бути 200")
                    .isEqualTo(200);
            assertThat(image.asByteArray())
                    .as("Тіло фото не порожнє")
                    .isNotEmpty();
        });

        Allure.step("Act: PUT /resources/{id} removeImage=true", () -> {
            ResourceRequest remove = ResourceDataFactory.fromExisting(created, categoryId)
                    .removeImage(true)
                    .build();
            ResourceResponse updated = resourceFixture.update(UserRole.ADMIN, resourceId, remove);
            Allure.addAttachment("PUT response imagePath",
                    String.valueOf(updated.getImagePath()));
        });

        Allure.step("Assert: GET by id — imagePath відсутній", () -> {
            ResourceResponse after = resourceFixture.getById(UserRole.ADMIN, resourceId);
            Allure.addAttachment("GET by id imagePath",
                    String.valueOf(after.getImagePath()));
            assertThat(after.getImagePath())
                    .as("Після removeImage GET by id не повинен повертати imagePath")
                    .isNullOrEmpty();
        });

        Allure.step("Assert: GET /resources/{id}/image — 404", () -> {
            Response image = resourceFixture.getImage(UserRole.ADMIN, resourceId);
            AllureHelper.attachResponseDetails(image);
            assertThat(image.statusCode())
                    .as("Фото має бути видалене з бекенду")
                    .isEqualTo(404);
        });
    }
}
