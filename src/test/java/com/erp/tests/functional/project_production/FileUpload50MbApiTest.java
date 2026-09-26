package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.clients.HttpClientSupport;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.response.ProjectProductionFileResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.tests.support.UploadLimitTestFiles;
import com.erp.tests.support.UploadLimitBrowserClient;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Feature("50MB multipart upload")
public class FileUpload50MbApiTest extends BaseFunctionalTest {
    private ProjectProductionFixture fixture;
    private Long storageId;
    private Long productionId;
    private Path smallPdf;
    private Path oversizedPdf;
    private Path fiveMbPng;
    private Map<String, String> cookies;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareUploadTests() throws IOException {
        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        cookies = authService.getSessionForUser(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        smallPdf = UploadLimitTestFiles.createPdf("small", 1024);
        oversizedPdf = UploadLimitTestFiles.createPdf("oversized", UploadLimitTestFiles.OVER_LIMIT_BYTES);
        fiveMbPng = UploadLimitTestFiles.createPng("five-mb", UploadLimitTestFiles.FIVE_MB);
    }

    @BeforeMethod(alwaysRun = true)
    public void createProduction() {
        ProjectProductionResponse created = fixture.createAs(UserRole.ADMIN,
                ProjectProductionDataFactory.buildCreateRequest(
                        storageId,
                        testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID),
                        testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID),
                        ProjectProductionState.IN_PROGRESS,
                        ProjectProductionType.CREATION,
                        null));
        productionId = created.getId();
    }

    @AfterMethod(alwaysRun = true)
    public void deleteProduction() {
        if (productionId != null) {
            fixture.deleteAs(UserRole.ADMIN, productionId, storageId, null);
            productionId = null;
        }
    }

    @AfterClass(alwaysRun = true)
    public void deleteTemporaryFiles() throws IOException {
        try {
            Long sharedId = testContext.get(ContextKey.PROJECT_PRODUCTION_ID);
            if (sharedId != null) fixture.deleteAs(UserRole.ADMIN, sharedId, storageId, null);
        } finally {
            if (smallPdf != null) Files.deleteIfExists(smallPdf);
            if (oversizedPdf != null) Files.deleteIfExists(oversizedPdf);
            if (fiveMbPng != null) Files.deleteIfExists(fiveMbPng);
        }
    }

    @Test
    @TestCaseId("TC-UPLOAD-50-01")
    @Story("Allowed PDF upload")
    public void uploadsAllowedPdfAndDownloadsSameBytes() throws IOException {
        Response upload = upload(smallPdf);
        assertThat(upload.statusCode()).isBetween(200, 299);

        ProjectProductionResponse saved = fixture.getById(productionId, storageId);
        ProjectProductionFileResponse file = saved.getFiles().stream()
                .filter(item -> smallPdf.getFileName().toString().equals(item.getFileNameOriginal()))
                .findFirst().orElseThrow();

        Response download = given()
                .config(HttpClientSupport.config())
                .baseUri(ConfigProvider.getBackendUrl())
                .cookies(cookies)
                .queryParam("storageId", storageId)
                .when().get("/api/v1/project-production/{id}/file/{fileId}", productionId, file.getId());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.asByteArray()).isEqualTo(Files.readAllBytes(smallPdf));
    }

    @Test
    @TestCaseId("TC-UPLOAD-50-02")
    @Story("Oversized PDF error")
    public void rejectsOversizedPdfWithBackendMessage() {
        List<Long> before = fileIds();
        UploadLimitBrowserClient.UploadResponse upload = UploadLimitBrowserClient.send(
                getPlaywrightSessionProvider().getBrowser(), cookies, "PUT",
                "/api/v1/project-production/" + productionId + "/file",
                FormData.create().append("file", oversizedPdf), storageId);

        assertThat(upload.status()).as(upload.body()).isEqualTo(400);
        assertThat(upload.firstMessage()).isEqualTo(UploadLimitTestFiles.EXPECTED_ERROR);
        assertThat(fileIds()).isEqualTo(before);
    }

    @Test
    @TestCaseId("TC-UPLOAD-50-06")
    @Story("Ten 5MB images exceed the total request limit")
    public void rejectsTenFiveMbImagesInOneRequest() throws Exception {
        assertThat(Files.size(fiveMbPng)).isEqualTo(UploadLimitTestFiles.FIVE_MB);
        ProjectProductionRequest body = ProjectProductionDataFactory.buildCreateRequest(
                storageId,
                testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID),
                testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID),
                ProjectProductionState.IN_PROGRESS,
                ProjectProductionType.CREATION,
                null);
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(body);
        FormData form = FormData.create().append("request",
                new FilePayload("request.json", "application/json",
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        byte[] png = Files.readAllBytes(fiveMbPng);
        for (int i = 1; i <= 10; i++) {
            form.append("files", new FilePayload("photo-" + i + ".png", "image/png", png));
        }

        UploadLimitBrowserClient.UploadResponse upload = UploadLimitBrowserClient.send(
                getPlaywrightSessionProvider().getBrowser(), cookies, "POST",
                "/api/v1/project-production", form, null);
        assertThat(upload.status()).as(upload.body()).isEqualTo(400);
        assertThat(upload.firstMessage()).isEqualTo(UploadLimitTestFiles.EXPECTED_ERROR);
    }

    private List<Long> fileIds() {
        return fixture.getById(productionId, storageId).getFiles().stream()
                .map(ProjectProductionFileResponse::getId).toList();
    }

    private Response upload(Path file) {
        try {
            return given()
                    .config(HttpClientSupport.config())
                    .baseUri(ConfigProvider.getBackendUrl())
                    .accept(ContentType.ANY)
                    .header("Expect", "100-continue")
                    .cookies(cookies)
                    .queryParam("storageId", storageId)
                    .multiPart("file", file.toFile(), "application/pdf")
                    .when().put("/api/v1/project-production/{id}/file", productionId);
        } catch (RuntimeException ex) {
            throw new AssertionError("Upload disconnected before an HTTP response; check the proxy request limit", ex);
        }
    }
}
