package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.tests.support.UploadLimitTestFiles;
import com.erp.tests.support.UploadLimitBrowserClient;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import com.microsoft.playwright.options.FormData;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Feature("50MB resource photo upload")
public class ResourcePhoto50MbApiTest extends BaseFunctionalTest {
    private ResourceFixture fixture;
    private ResourceResponse resource;
    private Path oversizedPng;
    private Map<String, String> cookies;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void preparePhotoTests() throws IOException {
        fixture = new ResourceFixture(testContext, apiExecutor);
        fixture.fetchSharedUnit(3);
        fixture.fetchSharedResourceCategory();
        cookies = authService.getSessionForUser(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        oversizedPng = UploadLimitTestFiles.createOversizedPng();
    }

    @BeforeMethod(alwaysRun = true)
    public void createResource() {
        resource = fixture.createUniqueResource("upload-50-photo-");
    }

    @AfterMethod(alwaysRun = true)
    public void deactivateResource() {
        if (resource != null) {
            Response result = fixture.deactivate(UserRole.ADMIN, resource.getId());
            assertThat(result.statusCode()).isBetween(200, 299);
            resource = null;
        }
    }

    @AfterClass(alwaysRun = true)
    public void deleteTemporaryFile() throws IOException {
        if (oversizedPng != null) Files.deleteIfExists(oversizedPng);
    }

    @Test
    @TestCaseId("TC-UPLOAD-50-04")
    @Story("Allowed resource photo upload")
    public void uploadsSmallResourcePhoto() {
        fixture.uploadPng(UserRole.ADMIN, resource.getId());

        assertThat(fixture.getById(UserRole.ADMIN, resource.getId()).getImagePath())
                .isEqualTo("resources/" + resource.getId() + "/image");
        Response image = fixture.getImage(UserRole.ADMIN, resource.getId());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.asByteArray()).isNotEmpty();
    }

    @Test
    @TestCaseId("TC-UPLOAD-50-05")
    @Story("Oversized resource photo error")
    public void rejectsOversizedResourcePhotoWithBackendMessage() {
        UploadLimitBrowserClient.UploadResponse upload = UploadLimitBrowserClient.send(
                getPlaywrightSessionProvider().getBrowser(), cookies, "POST",
                "/api/v1/resources/" + resource.getId() + "/image",
                FormData.create().append("file", oversizedPng), null);

        assertThat(upload.status()).as(upload.body()).isEqualTo(400);
        assertThat(upload.firstMessage()).isEqualTo(UploadLimitTestFiles.EXPECTED_ERROR);
        assertThat(fixture.getById(UserRole.ADMIN, resource.getId()).getImagePath()).isNullOrEmpty();
        assertThat(fixture.getImage(UserRole.ADMIN, resource.getId()).statusCode()).isEqualTo(404);
    }
}
