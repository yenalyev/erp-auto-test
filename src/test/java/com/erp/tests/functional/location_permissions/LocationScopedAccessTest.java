package com.erp.tests.functional.location_permissions;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationPermissionSupport;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.response.InventorySessionStatus;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPMA-644: API scoped access — read on full∪RO, create denied on RO.
 */
@Slf4j
@Epic("Administration")
@Feature("REQ-LOC-PERM")
@Story("API location-scoped access")
public class LocationScopedAccessTest extends BaseFunctionalTest {

    private UserFixture userFixture;
    private StorageFixture storageFixture;
    private UserFixture.LocationPermissionIds ids;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void ensureMixedUser() {
        userFixture = new UserFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        long ro2 = LocationPermissionSupport.resolveRo2StorageId(storageFixture);
        ids = userFixture.ensureLocationMixedUser(getPlaywrightSessionProvider(), ro2);
    }

    @DataProvider(name = "allowedStorageIds")
    public Object[][] allowedStorageIds() {
        return new Object[][] {
                {"fullA1", ids.fullA1()},
                {"fullA2", ids.fullA2()},
                {"roB1", ids.roB1()},
                {"roB2", ids.roB2()}
        };
    }

    @DataProvider(name = "fullStorageIds")
    public Object[][] fullStorageIds() {
        return new Object[][] {
                {"fullA1", ids.fullA1()},
                {"fullA2", ids.fullA2()}
        };
    }

    @DataProvider(name = "roStorageIds")
    public Object[][] roStorageIds() {
        return new Object[][] {
                {"roB1", ids.roB1()},
                {"roB2", ids.roB2()}
        };
    }

    @Test(dataProvider = "allowedStorageIds")
    @TestCaseId("TC-LOC-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("LOCATION_MIXED: GET production list 200 on each full and RO location.")
    public void productionListAllowedOnFullAndRo(String label, long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_GET_ALL_BY_STORE_ID,
                UserRole.LOCATION_MIXED,
                String.valueOf(storageId));
        assertThat(response.statusCode())
                .as("GET production list on %s (%s) must be 200", label, storageId)
                .isEqualTo(200);
    }

    @Test(dataProvider = "roStorageIds")
    @TestCaseId("TC-LOC-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("LOCATION_MIXED: POST production create → 403 on each RO location.")
    public void productionCreateForbiddenOnRo(String label, long storageId) {
        ManufacturingListRequest body = emptyCreateBody();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                UserRole.LOCATION_MIXED,
                body,
                String.valueOf(storageId));
        assertThat(response.statusCode())
                .as("POST production create on RO %s (%s) must be 403", label, storageId)
                .isEqualTo(403);
    }

    @Test(dataProvider = "fullStorageIds")
    @TestCaseId("TC-LOC-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            LOCATION_MIXED: POST production create on full locations must not be RBAC-denied (403).
            400/422 from validation is acceptable when body is intentionally empty.
            """)
    public void productionCreateNotRbacDeniedOnFull(String label, long storageId) {
        ManufacturingListRequest body = emptyCreateBody();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                UserRole.LOCATION_MIXED,
                body,
                String.valueOf(storageId));
        assertThat(response.statusCode())
                .as("POST production create on full %s (%s) must not be 403", label, storageId)
                .isNotEqualTo(403);
    }

    @Test(dataProvider = "allowedStorageIds")
    @TestCaseId("TC-LOC-API-002")
    @Severity(SeverityLevel.NORMAL)
    @Description("LOCATION_MIXED: GET inventory 200 on full and RO locations.")
    public void inventoryGetAllowedOnFullAndRo(String label, long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.LOCATION_MIXED,
                String.valueOf(storageId));
        assertThat(response.statusCode())
                .as("GET inventory on %s (%s) must be 200", label, storageId)
                .isEqualTo(200);
    }

    @Test(dataProvider = "roStorageIds")
    @TestCaseId("TC-LOC-API-002")
    @Severity(SeverityLevel.NORMAL)
    @Description("LOCATION_MIXED: inventory session mutate → 403 on RO (and typically Owner lacks open even on full).")
    public void inventoryMutateForbiddenOnRo(String label, long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_PUT,
                UserRole.LOCATION_MIXED,
                InventorySessionStatus.builder().open(true).build(),
                String.valueOf(storageId));
        assertThat(response.statusCode())
                .as("PUT inventory status on RO %s (%s) must be 403", label, storageId)
                .isEqualTo(403);
    }

    private static ManufacturingListRequest emptyCreateBody() {
        return ManufacturingListRequest.builder()
                .items(List.of())
                .build();
    }
}
