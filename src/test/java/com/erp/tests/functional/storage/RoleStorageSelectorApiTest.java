package com.erp.tests.functional.storage;

import com.erp.annotations.DynamicResourceViewer;
import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.enums.LocationFeature;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Селектор локацій (my-units) для ролей з обмеженою видимістю UNIT.
 */
@Slf4j
@Epic("Authentication & Authorization")
@Feature("Storages")
@Story("My Units Selector")
@DynamicResourceViewer
public class RoleStorageSelectorApiTest extends CrewApiTestBase {

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка storage context")
    public void setupRoleStorageSelectorTests() {
        storageFixture.prepareContext();
    }

    @Test(priority = 10)
    @TestCaseId("TC-ACC-API-001")
    @Description("""
            ACCOUNTANT: GET /storages/names/my-units не містить локацій з функцією ORDERS.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testAccountantMyUnitsExcludesOrdersLocations() {
        assertNoOrdersLocationsInMyUnits(UserRole.ACCOUNTANT);
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-API-001")
    @Description("""
            Глобальна роль RESOURCE_VIEWER не має location grants і не використовує
            GET /storages/names/my-units; endpoint повертає 403, а viewer працює через
            глобальний доступ до власних endpoint-ів.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testResourceViewerGlobalRoleDoesNotRequireMyUnitsAccess() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_GET_MY_UNITS,
                UserRole.RESOURCE_VIEWER);
        assertThat(response.statusCode()).isEqualTo(403);
    }

    private void assertNoOrdersLocationsInMyUnits(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_MY_UNITS, role);
        assertThat(response.statusCode()).isEqualTo(200);

        List<StorageResponse> units = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        assertThat(units).isNotEmpty();

        List<StorageResponse> ordersLocations = units.stream()
                .filter(s -> s.getFeatures() != null && s.getFeatures().contains(LocationFeature.ORDERS))
                .toList();

        assertThat(ordersLocations)
                .as("Роль %s не повинна бачити локації з ORDERS у my-units", role)
                .isEmpty();
    }
}
