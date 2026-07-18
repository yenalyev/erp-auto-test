package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.response.RelocationCreationOptionsResponse;
import com.erp.models.response.StorageHierarchyResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.AllureHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Discovery API для екіпажів: hasCrews, crew-units, crew-names.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Crew Discovery")
public class CrewVisibilityTest extends CrewApiTestBase {

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка середовища для crew visibility тестів")
    public void setupCrewVisibilityTest() {
        storageFixture.prepareContext();
        regionFixture.purgeRegionsByNamePrefixes(
                UserRole.ADMIN, "crew-opt-", "crew-nomem-", "crew-hier-", "crew-rel-", "crew-inv-");
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-CREW-005")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_005)
    @Severity(SeverityLevel.CRITICAL)
    public void testRelocationCreationOptionsHasCrews() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-opt-");

        RelocationCreationOptionsResponse memberOptions =
                crewFixture.getCreationOptions(UserRole.OWNER_1, scenario.memberStorageId());
        assertThat(memberOptions.getHasCrews()).isTrue();

        RelocationCreationOptionsResponse outsiderOptions =
                crewFixture.getCreationOptions(UserRole.OWNER_2, owner2StorageId);
        assertThat(outsiderOptions.getHasCrews()).isFalse();
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-CREW-006")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_006)
    @Severity(SeverityLevel.CRITICAL)
    public void testHasCrewsFalseWithoutMembers() {
        StorageResponse unit = storageFixture.createUnitStorage(
                storageFixture.resolveParentUnit().getId(), "crew-nomem-u-");
        StorageResponse crew = storageFixture.createCrewStorage(unit.getId(), "crew-nomem-c-");

        var region = regionFixture.createRegion(unit, StorageAccessMode.CREWS, "crew-nomem-reg-");
        regionFixture.addRegionLocations(region.getId(), unit.getId(), crew.getId());

        RelocationCreationOptionsResponse options =
                crewFixture.getCreationOptions(UserRole.OWNER_2, owner2StorageId);
        assertThat(options.getHasCrews())
                .as("OWNER_2 не member цієї області — hasCrews має бути false")
                .isFalse();
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-CREW-011")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_011)
    @Severity(SeverityLevel.CRITICAL)
    public void testGetCrewUnitsHierarchy() {
        CrewRegionScenario scenario = crewFixture.prepareHierarchyScenario("crew-hier-");

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_GET_CREW_UNITS,
                UserRole.OWNER_1,
                String.valueOf(scenario.memberStorageId()));
        assertThat(response.statusCode()).isEqualTo(200);
        AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_GET_CREW_UNITS, response);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_GET_CREW_UNITS);

        List<StorageHierarchyResponse> units =
                crewFixture.getCrewUnits(UserRole.OWNER_1, scenario.memberStorageId());
        assertThat(units).isNotEmpty();
        assertThat(CrewRegionFixture.hierarchyContainsUnitWithChild(
                units, scenario.unit().getId(), scenario.childUnit().getId())).isTrue();

        assertNoCrewNodes(units);
    }

    @Test(priority = 40)
    @TestCaseId("TC-STR-CREW-012")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_012)
    @Severity(SeverityLevel.CRITICAL)
    public void testGetCrewNamesByParent() {
        CrewRegionScenario scenario = crewFixture.prepareHierarchyScenario("crew-names-");

        List<StorageResponse> crews = crewFixture.getCrewNames(
                UserRole.OWNER_1, scenario.childUnit().getId(), null);
        assertThat(crews)
                .extracting(StorageResponse::getId)
                .contains(scenario.crew().getId());

        // Екіпажі шукаються рекурсивно — root UNIT теж містить crew нащадка
        List<StorageResponse> crewsUnderRoot = crewFixture.getCrewNames(
                UserRole.OWNER_1, scenario.unit().getId(), null);
        assertThat(crewsUnderRoot)
                .extracting(StorageResponse::getId)
                .contains(scenario.crew().getId());

        Long parentId = storageFixture.resolveParentUnit().getId();
        StorageResponse outsideUnit = storageFixture.createUnitStorage(parentId, "crew-out-");
        List<StorageResponse> outsideCrews = crewFixture.getCrewNames(
                UserRole.OWNER_1, outsideUnit.getId(), null);
        assertThat(outsideCrews).isEmpty();
    }

    @Test(priority = 50)
    @TestCaseId("TC-STR-CREW-013")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_013)
    @Severity(SeverityLevel.CRITICAL)
    public void testGetCrewNamesRecursiveFromParentUnit() {
        CrewRegionScenario scenario = crewFixture.prepareHierarchyScenario("crew-rec-");

        List<StorageResponse> crewsFromParent = crewFixture.getCrewNames(
                UserRole.OWNER_1, scenario.unit().getId(), null);
        assertThat(crewsFromParent)
                .extracting(StorageResponse::getId)
                .contains(scenario.crew().getId());
    }

    @Test(priority = 60)
    @TestCaseId("TC-STR-CREW-014")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_014)
    @Severity(SeverityLevel.CRITICAL)
    public void testGetCrewUnitsReturnsParentChildTree() {
        CrewRegionScenario scenario = crewFixture.prepareHierarchyScenario("crew-tree-");

        List<StorageHierarchyResponse> units =
                crewFixture.getCrewUnits(UserRole.OWNER_1, scenario.memberStorageId());

        assertThat(units.stream().map(StorageHierarchyResponse::getId))
                .contains(scenario.unit().getId());
        assertThat(CrewRegionFixture.hierarchyContainsUnitWithChild(
                units, scenario.unit().getId(), scenario.childUnit().getId())).isTrue();
    }

    private static void assertNoCrewNodes(List<StorageHierarchyResponse> nodes) {
        for (StorageHierarchyResponse node : nodes) {
            assertThat(node.getUnitType())
                    .as("Ієрархія crew-units не повинна містити CREW nodes (id=%s)", node.getId())
                    .isNotEqualTo(com.erp.enums.UnitType.CREW);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                assertNoCrewNodes(node.getChildren());
            }
        }
    }
}
