package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.pages.ProjectProductionListPage;
import com.erp.test_context.ContextKey;
import com.erp.tests.support.UploadLimitTestFiles;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Feature("50MB multipart upload")
public class FileUpload50MbUiTest extends BaseUITest {
    private ProjectProductionFixture fixture;
    private Long storageId;
    private ProjectProductionResponse production;
    private Path oversizedPdf;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        injectSessionCookies(cachedSessionCookies(UserRole.OWNER_1), sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + storageId + "');");
        try {
            oversizedPdf = UploadLimitTestFiles.createPdf("ui-oversized", UploadLimitTestFiles.OVER_LIMIT_BYTES);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create oversized PDF fixture", e);
        }
    }

    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        super.testSetup();
        page.setDefaultTimeout(60_000);
        production = fixture.createAs(UserRole.ADMIN,
                ProjectProductionDataFactory.buildCreateRequest(
                        storageId,
                        testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID),
                        testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID),
                        ProjectProductionState.IN_PROGRESS,
                        ProjectProductionType.CREATION,
                        null));
    }

    @AfterMethod(alwaysRun = true)
    public void deleteProduction() {
        if (production != null) {
            fixture.deleteAs(UserRole.ADMIN, production.getId(), storageId, null);
            production = null;
        }
    }

    @AfterClass(alwaysRun = true)
    public void deleteTemporaryFile() throws IOException {
        try {
            Long sharedId = testContext.get(ContextKey.PROJECT_PRODUCTION_ID);
            if (sharedId != null) fixture.deleteAs(UserRole.ADMIN, sharedId, storageId, null);
        } finally {
            if (oversizedPdf != null) Files.deleteIfExists(oversizedPdf);
        }
    }

    @Test
    @TestCaseId("TC-UPLOAD-50-03")
    @Story("Oversized PDF error is visible in UI")
    public void displaysBackendSizeErrorInsteadOfSuccess() {
        new ProjectProductionListPage(page)
                .open()
                .clearPeriodFilter()
                .waitForRowWithSerial(production.getSerialNumber());
        page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(production.getSerialNumber()))
                .locator("button[title='Редагувати']")
                .click();

        Locator input = page.locator("input[type='file'][accept*='application/pdf']").first();
        Response upload = page.waitForResponse(
                response -> response.request().method().equals("PUT")
                        && response.url().contains("/api/v1/project-production/" + production.getId() + "/file"),
                () -> input.setInputFiles(oversizedPdf));

        assertThat(upload.status()).isEqualTo(400);
        assertThat(upload.text()).as("Size error response body")
                .contains(UploadLimitTestFiles.EXPECTED_ERROR);
        page.getByText(UploadLimitTestFiles.EXPECTED_ERROR,
                        new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))
                .first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(8_000));
        assertThat(page.locator("[data-sonner-toast]")
                .filter(new Locator.FilterOptions().setHasText("Файл завантажено")).count()).isZero();
        assertThat(fixture.getById(production.getId(), storageId).getFiles()).isEmpty();
    }
}
