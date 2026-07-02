package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
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
public class RoleStorageSelectorApiTest extends CrewApiTestBase {

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка storage context")
    public void setupRoleStorageSelectorTests() {
        storageFixture.prepareContext();
    }

    @Test(priority = 10)
    @TestCaseId("TC-ACC-API-001")
    @Description("""
            ACCOUNTANT: GET /storages/names/my-units не містить локацій type=UNIT.
            Очікуваний результат: лише STORAGE / PRODUCTION (операційні локації).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testAccountantMyUnitsExcludesUnitType() {
        assertNoUnitTypeInMyUnits(UserRole.ACCOUNTANT);
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-API-001")
    @Description("""
            RESOURCE_VIEWER: GET /storages/names/my-units не містить локацій type=UNIT.
            Очікуваний результат: лише STORAGE / PRODUCTION.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testResourceViewerMyUnitsExcludesUnitType() {
        assertNoUnitTypeInMyUnits(UserRole.RESOURCE_VIEWER);
    }

    private void assertNoUnitTypeInMyUnits(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_MY_UNITS, role);
        assertThat(response.statusCode()).isEqualTo(200);

        List<StorageResponse> units = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        assertThat(units).isNotEmpty();

        List<StorageResponse> unitTypeLocations = units.stream()
                .filter(s -> s.getType() != null && "UNIT".equalsIgnoreCase(s.getType()))
                .toList();

        assertThat(unitTypeLocations)
                .as("Роль %s не повинна бачити UNIT у my-units", role)
                .isEmpty();
    }
}
