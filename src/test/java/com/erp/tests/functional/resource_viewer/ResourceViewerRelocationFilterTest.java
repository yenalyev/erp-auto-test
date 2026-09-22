package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.DynamicResourceViewer;
import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.LocationFeature;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceRelocationViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
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
@DynamicResourceViewer
public class ResourceViewerRelocationFilterTest extends BaseFunctionalTest {

    private static final String RESOURCE_PREFIX = "rvw-rel-";
    private static final double SEND_AMOUNT = 6.0;

    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private CrewRegionFixture crewFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private LocationProfileFixture locationProfileFixture;

    private Long storageSourceId;
    private Long productionSourceId;
    private Long tsukParentId;
    private Long unitReceiverId;
    private Long resourceId;
    private StorageResponse secondUnit;
    private StorageResponse flyPoint;
    private CrewRegionScenario crewScenario;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupResourceViewerRelocationFilterTests() {
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        locationProfileFixture = new LocationProfileFixture(testContext, apiExecutor);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        var warehouseSet = locationProfileFixture.create(LocationProfile.TSUK_WARENHAUSE, 1);
        storageSourceId = warehouseSet.locations().getFirst().getId();
        tsukParentId = warehouseSet.parent().getId();
        productionSourceId = locationProfileFixture
                .create(LocationProfile.TSUK_PRODUCTION, 1)
                .locations().getFirst().getId();
        unitReceiverId = locationProfileFixture
                .create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst().getId();

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();

        crewScenario = crewFixture.prepareSingleCrewScenario("rvw-crew-");
        secondUnit = locationProfileFixture
                .create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst();
        flyPoint = storageFixture.createFlyPointStorage(crewScenario.unit().getId(), "rvw-fp-");

        relocationFixture.ensureStock(storageSourceId, resourceId, 100.0, UserRole.ADMIN);
        relocationFixture.ensureStock(productionSourceId, resourceId, 100.0, UserRole.ADMIN);
        relocationFixture.ensureStock(crewScenario.unit().getId(), resourceId, 50.0, UserRole.ADMIN);
        SchemaRegistry.logSchemaCoverage();
        log.info("RVW filter sources: storage={}, production={}, unitReceiver={}",
                storageSourceId, productionSourceId, unitReceiverId);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupLocations() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
        if (locationProfileFixture != null) {
            locationProfileFixture.cleanup();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-RVW-API-002")
    @Story("Journal follows the reporting hierarchy boundary")
    @Description("""
            У GET /resources-viewer/relocations входять переміщення з дозволеного
            дерева TSUK до зовнішньої структури. Подальше переміщення між
            зовнішніми локаціями не входить до звіту.
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
                .as("переміщення з TSUK до зовнішньої структури мають бути у journal")
                .contains(storageToUnit.getId(), productionToUnit.getId());
        assertThat(relocationIds)
                .as("подальше переміщення між зовнішніми локаціями не входить до journal")
                .doesNotContain(unitToUnit.getId());

        double total = page.getSums() == null ? 0.0 : page.getSums().stream()
                .filter(sum -> resourceId.equals(sum.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sum();
        assertThat(total)
                .as("підсумок містить лише переміщення, що перетнули reporting boundary")
                .isEqualTo(SEND_AMOUNT * 2);
    }

    @Test(priority = 15)
    @TestCaseId("TC-RVW-API-022")
    @Story("Hierarchy wins over location capabilities")
    @Description("""
            Контрприклад до legacy type-based логіки:
            - локації з unit-like features усередині TSUK, уключно з вкладеним нащадком,
              мають потрапити до journal при видачі назовні;
            - warehouse-like локація поза TSUK не має потрапити до journal;
            - видача всередині TSUK не перетинає reporting boundary.
            """)
    @Severity(SeverityLevel.BLOCKER)
    public void testHierarchyDeterminesVisibilityRegardlessOfLocationCapabilities() {
        StorageResponse allowedUnitLikeSource = createLocation(
                tsukParentId,
                "rvw-tsuk-unit-like-",
                Set.of(LocationFeature.RELOCATIONS, LocationFeature.ORDERS));
        StorageResponse allowedNestedUnitLikeSource = createLocation(
                allowedUnitLikeSource.getId(),
                "rvw-tsuk-nested-unit-like-",
                Set.of(LocationFeature.RELOCATIONS, LocationFeature.ORDERS));
        StorageResponse internalUnitLikeReceiver = createLocation(
                tsukParentId,
                "rvw-tsuk-internal-receiver-",
                Set.of(LocationFeature.RELOCATIONS, LocationFeature.ORDERS));
        StorageResponse outsideWarehouseLikeSource = createLocation(
                unitReceiverId,
                "rvw-outside-warehouse-like-",
                Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT));

        ResourceResponse isolated = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "hierarchy-");
        for (Long sourceId : List.of(
                allowedUnitLikeSource.getId(),
                allowedNestedUnitLikeSource.getId(),
                outsideWarehouseLikeSource.getId(),
                storageSourceId)) {
            relocationFixture.ensureStock(sourceId, isolated.getId(), 50.0, UserRole.ADMIN);
        }

        RelocationResponse allowedDirect = relocationFixture.createSend(
                UserRole.ADMIN,
                allowedUnitLikeSource.getId(),
                unitReceiverId,
                isolated.getId(),
                2.0);
        RelocationResponse allowedNested = relocationFixture.createSend(
                UserRole.ADMIN,
                allowedNestedUnitLikeSource.getId(),
                unitReceiverId,
                isolated.getId(),
                3.0);
        RelocationResponse excludedOutsideSource = relocationFixture.createSend(
                UserRole.ADMIN,
                outsideWarehouseLikeSource.getId(),
                secondUnit.getId(),
                isolated.getId(),
                5.0);
        RelocationResponse excludedInternalRoute = relocationFixture.createSend(
                UserRole.ADMIN,
                storageSourceId,
                internalUnitLikeReceiver.getId(),
                isolated.getId(),
                7.0);

        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(isolated.getId()));
        params.put("receiverIds", List.of(
                unitReceiverId,
                secondUnit.getId(),
                internalUnitLikeReceiver.getId()));
        params.put("page", 0);
        params.put("size", 100);

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET);
        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        Set<Long> relocationIds = (page.getContent() == null
                ? List.<ResourceRelocationViewerResponse>of()
                : page.getContent()).stream()
                .map(ResourceRelocationViewerResponse::getRelocationId)
                .collect(Collectors.toSet());

        assertThat(relocationIds)
                .as("TSUK ancestry, including nested ancestry, includes unit-like sources")
                .contains(allowedDirect.getId(), allowedNested.getId())
                .as("capabilities cannot include an outside source or an internal TSUK route")
                .doesNotContain(excludedOutsideSource.getId(), excludedInternalRoute.getId());

        double total = page.getSums() == null ? 0.0 : page.getSums().stream()
                .filter(sum -> isolated.getId().equals(sum.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Number::doubleValue)
                .sum();
        assertThat(total)
                .as("sums use the same hierarchy boundary as journal")
                .isEqualTo(5.0);
    }

    @Test(priority = 20)
    @TestCaseId("TC-RVW-API-003")
    @Story("Downstream relocations outside TSUK are excluded from journal and sum")
    @Description("Подальші передачі зовнішньої локації до CREW і FLY_POINT не додаються до Resource Viewer; історичне переміщення з TSUK назовні залишається")
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
                .as("подальші outside→CREW/FLY_POINT не змінюють історичний підсумок TSUK→outside")
                .isEqualTo(initialAmount);
    }

    private Map<String, Object> viewerParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(resourceId));
        params.put("receiverIds", unitReceiverId);
        return params;
    }

    private StorageResponse createLocation(
            Long parentId,
            String namePrefix,
            Set<LocationFeature> features) {
        return storageFixture.createStorage(
                StorageDataFactory.childStorage(parentId, namePrefix)
                        .features(features)
                        .build());
    }
}
