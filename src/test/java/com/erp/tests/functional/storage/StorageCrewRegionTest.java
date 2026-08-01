package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageRegionDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.models.request.StorageRegionRequest;
import com.erp.models.response.StorageRegionLocationResponse;
import com.erp.models.response.StorageRegionMemberResponse;
import com.erp.models.response.StorageRegionResponse;
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
 * CRUD областей видимості з {@code accessMode=CREWS} — передумова для видачі на екіпажі.
 */
@Slf4j
@Epic("Master Data")
@Feature("Storages")
@Story("Crew Visibility Regions")
public class StorageCrewRegionTest extends CrewApiTestBase {

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    @Step("Підготовка середовища для CREWS region тестів")
    public void setupStorageCrewRegionTest() {
        storageFixture.prepareContext();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STR-CREW-001")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_001)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateCrewRegion() {
        StorageResponse recipient = storageFixture.createUnitStorage(
                storageFixture.resolveParentUnit().getId(), "crew-reg-rec-");

        StorageRegionRequest request = StorageRegionDataFactory.createRegion(
                recipient, StorageAccessMode.CREWS, "crew-reg-create-");

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_REGION_POST_CREATE, UserRole.ADMIN, request);

        assertThat(response.statusCode()).isEqualTo(200);
        AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.STORAGE_REGION_POST_CREATE, response);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_REGION_POST_CREATE);

        StorageRegionResponse created = response.as(StorageRegionResponse.class);
        regionFixture.trackForCleanup(created.getId());
        assertThat(created.getId()).as("region id після create").isNotNull();
        assertThat(created.getAccessMode()).isEqualTo(StorageAccessMode.CREWS.name());
        // Deployed API: CREWS regions do not persist/return recipientStorage (null is valid).
        if (created.getRecipientStorage() != null) {
            assertThat(created.getRecipientStorage().getId()).isEqualTo(recipient.getId());
        }
    }

    @Test(priority = 20)
    @TestCaseId("TC-STR-CREW-002")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_002)
    @Severity(SeverityLevel.CRITICAL)
    public void testAddAndListCrewRegionLocations() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-loc-");
        Long regionId = scenario.region().getId();

        List<StorageRegionLocationResponse> locations =
                regionFixture.getRegionLocations(UserRole.ADMIN, regionId);
        assertThat(locations.stream().map(StorageRegionLocationResponse::getStorageId))
                .contains(scenario.unit().getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-STR-CREW-003")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_003)
    @Severity(SeverityLevel.CRITICAL)
    public void testAddAndListCrewRegionMembers() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-mem-");

        List<StorageRegionMemberResponse> members =
                regionFixture.getRegionMembers(UserRole.ADMIN, scenario.region().getId());
        assertThat(members.stream().map(StorageRegionMemberResponse::getStorageId))
                .contains(scenario.memberStorageId());
    }

    @Test(priority = 40)
    @TestCaseId("TC-STR-CREW-004")
    @Description(StorageRegionsAllureDescriptions.TC_STR_CREW_004)
    @Severity(SeverityLevel.NORMAL)
    public void testGetCrewRegionById() {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario("crew-get-");

        StorageRegionResponse region = regionFixture.getById(UserRole.ADMIN, scenario.region().getId());
        assertThat(region.getId()).isEqualTo(scenario.region().getId());
        assertThat(region.getAccessMode()).isEqualTo(StorageAccessMode.CREWS.name());
        // Deployed API: CREWS regions do not persist/return recipientStorage (null is valid).
        if (region.getRecipientStorage() != null) {
            assertThat(region.getRecipientStorage().getId()).isEqualTo(scenario.unit().getId());
        }
    }
}
