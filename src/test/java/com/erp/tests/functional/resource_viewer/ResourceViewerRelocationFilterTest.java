package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
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

    private Long storageSourceId;
    private Long productionSourceId;
    private Long unitReceiverId;
    private Long resourceId;
    private StorageResponse secondUnit;
    private StorageResponse flyPoint;
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
        unitReceiverId = relocationFixture.resolveUnitStorageId(UserRole.ADMIN);

        Long parentId = storageFixture.resolveParentUnit().getId();
        storageSourceId = storageFixture.createChildStorage(
                parentId, "rvw-src-st-", UnitType.STORAGE, StorageRelation.INTERNAL).getId();
        productionSourceId = storageFixture.createChildStorage(
                parentId, "rvw-src-pr-", UnitType.PRODUCTION, StorageRelation.INTERNAL).getId();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        crewScenario = crewFixture.prepareSingleCrewScenario("rvw-crew-");
        Long unitParentId = crewScenario.unit().getParent() != null
                ? crewScenario.unit().getParent().getId()
                : storageFixture.resolveParentUnit().getId();
        secondUnit = storageFixture.createUnitStorage(unitParentId, "rvw-u2-");
        flyPoint = storageFixture.createFlyPointStorage(crewScenario.unit().getId(), "rvw-fp-");

        relocationFixture.ensureStock(storageSourceId, resourceId, 100.0, UserRole.ADMIN);
        relocationFixture.ensureStock(productionSourceId, resourceId, 100.0, UserRole.ADMIN);
        relocationFixture.ensureStock(crewScenario.unit().getId(), resourceId, 50.0, UserRole.ADMIN);
        SchemaRegistry.logSchemaCoverage();
        log.info("RVW filter sources: storage={}, production={}, unitReceiver={}",
                storageSourceId, productionSourceId, unitReceiverId);
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
        RelocationResponse storageToUnit = relocationFixture.createSend(
                UserRole.ADMIN, storageSourceId, unitReceiverId, resourceId, SEND_AMOUNT);
        RelocationResponse productionToUnit = relocationFixture.createSend(
                UserRole.ADMIN, productionSourceId, unitReceiverId, resourceId, SEND_AMOUNT);
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
                .contains(storageToUnit.getId(), productionToUnit.getId());
        assertThat(relocationIds)
                .as("UNIT→UNIT не повинен бути у журналі (sender не STORAGE/PRODUCTION)")
                .doesNotContain(unitToUnit.getId());

        double total = page.getSums() == null ? 0.0 : page.getSums().stream()
                .filter(sum -> resourceId.equals(sum.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sum();
        assertThat(total)
                .as("підсумок містить лише STORAGE→UNIT і PRODUCTION→UNIT")
                .isEqualTo(SEND_AMOUNT * 2);
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-API-003")
    @Story("UNIT→CREW/FLY_POINT excluded from journal and sum")
    @Description("Подальші передачі UNIT→CREW і UNIT→FLY_POINT не додаються до Resource Viewer; історична STORAGE→UNIT залишається")
    @Severity(SeverityLevel.NORMAL)
    public void testUnitToCrewExcludedFromRelocationSum() {
        ResourceResponse isolated = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "crew-");
        double initialAmount = 10.0;
        relocationFixture.ensureStock(storageSourceId, isolated.getId(), 50.0, UserRole.ADMIN);
        RelocationResponse initial = relocationFixture.createSend(
                UserRole.ADMIN, storageSourceId, unitReceiverId, isolated.getId(), initialAmount);
        relocationFixture.ensureStock(crewScenario.unit().getId(), isolated.getId(), 50.0, UserRole.ADMIN);

        RelocationResponse toCrew = relocationFixture.createSendAndFinishBySender(
                UserRole.ADMIN,
                crewScenario.unit().getId(),
                crewScenario.crew().getId(),
                isolated.getId(),
                3.0);
        RelocationResponse toFlyPoint = relocationFixture.createSendAndFinishBySender(
                UserRole.ADMIN,
                crewScenario.unit().getId(),
                flyPoint.getId(),
                isolated.getId(),
                4.0);

        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(isolated.getId()));
        params.put("receiverIds", List.of(
                unitReceiverId, crewScenario.crew().getId(), flyPoint.getId()));

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);

        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        List<Long> relocationIds = page.getContent() == null ? List.of() : page.getContent().stream()
                .map(ResourceRelocationViewerResponse::getRelocationId)
                .toList();
        assertThat(relocationIds)
                .contains(initial.getId())
                .doesNotContain(toCrew.getId(), toFlyPoint.getId());

        List<ResourceRelocationSumViewerResponse> sums =
                page.getSums() != null ? page.getSums() : List.of();
        double total = sums.stream()
                .filter(s -> isolated.getId().equals(s.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sum();

        assertThat(total)
                .as("UNIT→CREW/FLY_POINT не змінюють історичний підсумок STORAGE→UNIT")
                .isEqualTo(initialAmount);
    }

    private Map<String, Object> viewerParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(resourceId));
        params.put("receiverIds", unitReceiverId);
        return params;
    }
}
