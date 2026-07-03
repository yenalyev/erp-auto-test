package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Resource Viewer")
@Feature("Relocation journal filter")
public class ResourceViewerRelocationFilterTest extends StorageApiTestBase {

    private static final String RESOURCE_PREFIX = "rvw-rel-";
    private static final double SEND_AMOUNT = 6.0;

    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private CrewRegionFixture crewFixture;

    private Long owner1StorageId;
    private Long owner2StorageId;
    private Long resourceId;
    private StorageResponse secondUnit;
    private CrewRegionScenario crewScenario;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupStorageApiBase")
    public void setupResourceViewerRelocationFilterTests() {
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        crewScenario = crewFixture.prepareSingleCrewScenario("rvw-crew-");
        Long parentId = crewScenario.unit().getParent() != null
                ? crewScenario.unit().getParent().getId()
                : storageFixture.resolveParentUnit().getId();
        secondUnit = storageFixture.createUnitStorage(parentId, "rvw-u2-");

        relocationFixture.ensureStock(owner1StorageId, resourceId, 100.0);
        relocationFixture.ensureStock(crewScenario.unit().getId(), resourceId, 50.0);
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-RVW-API-002")
    @Story("Sender filter STORAGE/PRODUCTION only")
    @Description("STORAGE→UNIT visible; UNIT→UNIT hidden у GET /resources-viewer/relocations")
    @Severity(SeverityLevel.CRITICAL)
    public void testResourceViewerRelocationsSenderFilter() {
        RelocationResponse storageToUnit = relocationFixture.createSend(
                UserRole.OWNER_1, owner1StorageId, owner2StorageId, resourceId, SEND_AMOUNT);
        RelocationResponse unitToUnit = relocationFixture.createSend(
                UserRole.OWNER_1, crewScenario.unit().getId(), secondUnit.getId(), resourceId, SEND_AMOUNT);

        Map<String, Object> params = viewerParams();

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);

        assertThat(response.statusCode()).isEqualTo(200);

        List<Map<String, Object>> content = response.jsonPath().getList("content");
        if (content == null) {
            content = List.of();
        }
        Set<Long> relocationIds = content.stream()
                .map(row -> ((Number) row.get("id")).longValue())
                .collect(Collectors.toSet());

        assertThat(relocationIds)
                .as("STORAGE→UNIT має бути у журналі resource-viewer")
                .contains(storageToUnit.getId());
        assertThat(relocationIds)
                .as("UNIT→UNIT не повинен бути у журналі (sender не STORAGE/PRODUCTION)")
                .doesNotContain(unitToUnit.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-API-003")
    @Story("UNIT→CREW excluded from sum")
    @Description("UNIT→CREW відсутній у GET /resources-viewer/relocations/sum для tracked resource")
    @Severity(SeverityLevel.NORMAL)
    public void testUnitToCrewExcludedFromRelocationSum() {
        ResourceResponse isolated = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "crew-");
        relocationFixture.ensureStock(crewScenario.unit().getId(), isolated.getId(), 50.0);

        relocationFixture.createSend(
                UserRole.OWNER_1,
                crewScenario.unit().getId(),
                crewScenario.crew().getId(),
                isolated.getId(),
                SEND_AMOUNT);

        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(isolated.getId()));
        params.put("receiverIds", owner2StorageId);

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_SUM,
                UserRole.RESOURCE_VIEWER,
                params);

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_SUM);

        List<ResourceRelocationSumViewerResponse> sums = response.as(
                new TypeRef<List<ResourceRelocationSumViewerResponse>>() {});
        double total = sums.stream()
                .filter(s -> isolated.getId().equals(s.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sum();

        assertThat(total)
                .as("UNIT→CREW не повинен потрапляти в sum для resource-viewer")
                .isEqualTo(0.0);
    }

    private Map<String, Object> viewerParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(resourceId));
        params.put("receiverIds", owner2StorageId);
        return params;
    }
}
