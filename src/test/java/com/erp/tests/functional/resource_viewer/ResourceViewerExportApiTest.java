package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resource Viewer (wolf) Excel export contract.
 */
@Slf4j
@Epic("Resource Viewer")
@Feature("Export")
public class ResourceViewerExportApiTest extends BaseFunctionalTest {

    private static final double SEND_AMOUNT = 3.0;

    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private Long productionStorageId;
    private Long receiverUnitId;
    private Long resourceId;
    private String resourceName;
    private String unrelatedResourceName;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка даних для export resource-viewer")
    public void setupExportSuite() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        relocationFixture.prepareContext();

        productionStorageId = ConfigProvider.getOwner1StorageId();
        receiverUnitId = relocationFixture.resolveUnitStorageId(UserRole.ADMIN);

        ResourceResponse resource = resourceFixture.createUniqueResource("RVW-EXP-");
        resourceId = resource.getId();
        resourceName = resource.getName();
        relocationFixture.ensureStock(productionStorageId, resourceId, 50.0);
        relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, resourceId, SEND_AMOUNT);

        ResourceResponse unrelated = resourceFixture.createUniqueResource("RVW-EXP-OTHER-");
        unrelatedResourceName = unrelated.getName();
        relocationFixture.ensureStock(productionStorageId, unrelated.getId(), 50.0);
        relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, unrelated.getId(), SEND_AMOUNT + 7.0);
        log.info("Export suite ready: resource={}, receiver={}", resourceId, receiverUnitId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-RVW-API-020")
    @Story("Excel export")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /api/v1/resources-viewer/export як wolf:
            1) з resourceIds+receiverIds → 200, Content-Disposition *.xlsx;
            2) файл містить лише дані поточного фільтра;
            3) без tracking target або receiver → порожня відповідь (guard).
            """)
    public void testResourceViewerExportReturnsXlsxAndHonoursGuard() {
        Map<String, Object> validParams = new HashMap<>();
        validParams.put("resourceIds", List.of(resourceId));
        validParams.put("receiverIds", receiverUnitId);

        Response export = Allure.step("Export з валідним фільтром як wolf", () ->
                apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.RESOURCE_VIEWER_EXPORT,
                        UserRole.RESOURCE_VIEWER,
                        validParams));

        assertThat(export.statusCode()).isEqualTo(200);
        String disposition = export.getHeader("Content-Disposition");
        assertThat(disposition)
                .as("Content-Disposition має містити .xlsx")
                .isNotBlank()
                .containsIgnoringCase(".xlsx");
        byte[] xlsx = export.asByteArray();
        assertThat(xlsx.length)
                .as("Тіло Excel-експорту не повинно бути порожнім")
                .isPositive();
        String workbookXml = unzipXmlText(xlsx);
        assertThat(workbookXml)
                .as("Excel містить відфільтрований ресурс")
                .contains(resourceName)
                .as("Excel не містить переміщення іншого ресурсу")
                .doesNotContain(unrelatedResourceName);

        Map<String, Object> guardParams = new HashMap<>();
        guardParams.put("receiverIds", receiverUnitId);

        Response guarded = Allure.step("Export без tracking target (guard)", () ->
                apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.RESOURCE_VIEWER_EXPORT,
                        UserRole.RESOURCE_VIEWER,
                        guardParams));

        assertThat(guarded.statusCode()).isEqualTo(200);
        assertThat(guarded.asByteArray().length)
                .as("Guard без resourceIds/categoryIds → порожнє тіло")
                .isZero();

        Map<String, Object> noReceiverParams = new HashMap<>();
        noReceiverParams.put("resourceIds", List.of(resourceId));
        Response noReceiver = Allure.step("Export без receiver (guard)", () ->
                apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.RESOURCE_VIEWER_EXPORT,
                        UserRole.RESOURCE_VIEWER,
                        noReceiverParams));
        assertThat(noReceiver.statusCode()).isEqualTo(200);
        assertThat(noReceiver.asByteArray()).isEmpty();
    }

    private static String unzipXmlText(byte[] xlsx) {
        StringBuilder xml = new StringBuilder();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("resource-viewer-export-", ".xlsx");
            Files.write(tempFile, xlsx);
            try (ZipFile zip = new ZipFile(tempFile.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".xml")) {
                        try (var entryStream = zip.getInputStream(entry)) {
                            xml.append(new String(entryStream.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Export is not a readable XLSX archive", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Cannot delete temporary Resource Viewer export {}: {}", tempFile, e.getMessage());
                }
            }
        }
        return xml.toString();
    }
}
