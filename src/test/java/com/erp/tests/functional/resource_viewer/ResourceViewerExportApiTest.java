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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        relocationFixture.ensureStock(productionStorageId, resourceId, 50.0);
        relocationFixture.createSend(
                UserRole.ADMIN, productionStorageId, receiverUnitId, resourceId, SEND_AMOUNT);
        log.info("Export suite ready: resource={}, receiver={}", resourceId, receiverUnitId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-RVW-API-020")
    @Story("Excel export")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /api/v1/resources-viewer/export як wolf:
            1) з resourceIds+receiverIds → 200, Content-Disposition *.xlsx, непорожнє тіло;
            2) без tracking target → порожня відповідь (guard).
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
        assertThat(export.asByteArray().length)
                .as("Тіло Excel-експорту не повинно бути порожнім")
                .isPositive();

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
    }
}
