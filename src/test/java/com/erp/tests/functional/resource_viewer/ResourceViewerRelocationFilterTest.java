package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceRelocationViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
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

    private Long productionOrStorageId;
    private Long unitReceiverId;
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

        // Viewer journal: sender ∈ {STORAGE, PRODUCTION}, recipient type=UNIT only.
        productionOrStorageId = ConfigProvider.getOwner1StorageId();
        unitReceiverId = relocationFixture.resolveUnitStorageId(UserRole.ADMIN);

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        crewScenario = crewFixture.prepareSingleCrewScenario("rvw-crew-");
        Long parentId = crewScenario.unit().getParent() != null
                ? crewScenario.unit().getParent().getId()
                : storageFixture.resolveParentUnit().getId();
        secondUnit = storageFixture.createUnitStorage(parentId, "rvw-u2-");

        relocationFixture.ensureStock(productionOrStorageId, resourceId, 100.0, UserRole.ADMIN);
        relocationFixture.ensureStock(crewScenario.unit().getId(), resourceId, 50.0, UserRole.ADMIN);
        SchemaRegistry.logSchemaCoverage();
        log.info("RVW filter storages: senderStorage/Production={}, unitReceiver={}",
                productionOrStorageId, unitReceiverId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-RVW-API-002")
    @Story("Journal only STORAGE/PRODUCTION → UNIT")
    @Description("""
            У GET /resources-viewer/relocations потрапляють лише переміщення
            sender type ∈ {STORAGE, PRODUCTION} → recipient type=UNIT.
            STORAGE/PRODUCTION→UNIT — видно; UNIT→UNIT — приховано.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testResourceViewerRelocationsSenderFilter() {
        RelocationResponse storageOrProductionToUnit = relocationFixture.createSend(
                UserRole.ADMIN, productionOrStorageId, unitReceiverId, resourceId, SEND_AMOUNT);
        RelocationResponse unitToUnit = relocationFixture.createSend(
                UserRole.ADMIN, crewScenario.unit().getId(), secondUnit.getId(), resourceId, SEND_AMOUNT);

        Map<String, Object> params = viewerParams();

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);

        assertThat(response.statusCode()).isEqualTo(200);

        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        Set<Long> relocationIds = (page.getContent() == null
                ? List.<ResourceRelocationViewerResponse>of()
                : page.getContent()).stream()
                .map(ResourceRelocationViewerResponse::getRelocationId)
                .collect(Collectors.toSet());

        assertThat(relocationIds)
                .as("STORAGE/PRODUCTION→UNIT має бути у журналі resource-viewer")
                .contains(storageOrProductionToUnit.getId());
        assertThat(relocationIds)
                .as("UNIT→UNIT не повинен бути у журналі (sender не STORAGE/PRODUCTION)")
                .doesNotContain(unitToUnit.getId());
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-API-003")
    @Story("UNIT→CREW excluded from sum")
    @Description("UNIT→CREW відсутній у sums з GET /resources-viewer/relocations для tracked resource")
    @Severity(SeverityLevel.NORMAL)
    public void testUnitToCrewExcludedFromRelocationSum() {
        ResourceResponse isolated = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "crew-");
        relocationFixture.ensureStock(crewScenario.unit().getId(), isolated.getId(), 50.0, UserRole.ADMIN);

        relocationFixture.createSendAndFinishBySender(
                UserRole.ADMIN,
                crewScenario.unit().getId(),
                crewScenario.crew().getId(),
                isolated.getId(),
                SEND_AMOUNT);

        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(isolated.getId()));
        params.put("receiverIds", unitReceiverId);

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);

        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        List<ResourceRelocationSumViewerResponse> sums =
                page.getSums() != null ? page.getSums() : List.of();
        double total = sums.stream()
                .filter(s -> isolated.getId().equals(s.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sum();

        assertThat(total)
                .as("UNIT→CREW не повинен потрапляти в sums для resource-viewer")
                .isEqualTo(0.0);
    }

    private Map<String, Object> viewerParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(resourceId));
        params.put("receiverIds", unitReceiverId);
        return params;
    }
}
