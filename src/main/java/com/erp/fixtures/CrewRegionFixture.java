package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.models.response.CrewResourceStockResponse;
import com.erp.models.response.UnitFlyPointResourceStockResponse;
import com.erp.models.response.UnitShortStatsResponse;
import com.erp.models.response.RelocationCreationOptionsResponse;
import com.erp.models.response.StorageHierarchyResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class CrewRegionFixture extends BaseFixture {

    private final StorageFixture storageFixture;
    private final StorageRegionFixture regionFixture;

    public CrewRegionFixture(
            TestContext testContext,
            ApiExecutor apiExecutor,
            StorageFixture storageFixture,
            StorageRegionFixture regionFixture) {
        super(testContext, apiExecutor);
        this.storageFixture = storageFixture;
        this.regionFixture = regionFixture;
    }

    @Builder
    public record CrewRegionScenario(
            StorageRegionResponse region,
            StorageResponse unit,
            StorageResponse childUnit,
            StorageResponse flyPoint,
            StorageResponse crew,
            Long memberStorageId) {
    }

    /**
     * Canonical setup (як {@code StorageFacadeForCrewsIT}):
     * UNIT → CREW (екіпаж без точки вильоту); область CREWS з member=OWNER_1 та location=UNIT.
     */
    @Step("FIXTURE: підготувати область CREWS з одним екіпажем (unattached)")
    public CrewRegionScenario prepareSingleCrewScenario(String namePrefix) {
        Long memberId = ConfigProvider.getOwner1StorageId();
        StorageResponse member = storageFixture.getById(UserRole.ADMIN, memberId);
        Long parentId = member.getParent() != null ? member.getParent().getId() : memberId;

        StorageResponse unit = storageFixture.createUnitStorage(parentId, namePrefix + "unit-");
        StorageResponse crew = storageFixture.createCrewStorage(unit.getId(), namePrefix + "crew-");

        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.CREWS, namePrefix + "reg-");
        addCrewRegionMembers(region.getId());
        regionFixture.addRegionLocations(region.getId(), unit.getId(), crew.getId());

        CrewRegionScenario scenario = CrewRegionScenario.builder()
                .region(region)
                .unit(unit)
                .childUnit(null)
                .flyPoint(null)
                .crew(crew)
                .memberStorageId(memberId)
                .build();
        testContext.set(ContextKey.CREW_STORAGE_ID, crew.getId());
        testContext.set(ContextKey.CREW_PARENT_UNIT_ID, unit.getId());
        return scenario;
    }

    @Step("API: GET /fly-points/stocks parentId={parentId}")
    public Response getFlyPointStocksRaw(UserRole role, Long parentId, String resourceName) {
        Map<String, Object> params = new HashMap<>();
        if (parentId != null) {
            params.put("parentId", parentId);
        }
        if (resourceName != null && !resourceName.isBlank()) {
            params.put("resourceName", resourceName);
        }
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.FLY_POINT_GET_STOCKS, role, params);
    }

    public List<UnitFlyPointResourceStockResponse> getFlyPointStocks(
            UserRole role, Long parentId, String resourceName) {
        Response response = getFlyPointStocksRaw(role, parentId, resourceName);
        validateSuccess(response, "Get fly-point stocks for parent " + parentId);
        return DatabaseIntegrityValidator.extractList(response, UnitFlyPointResourceStockResponse.class);
    }

    @Step("API: GET /fly-points/short-stats parentId={parentId} days={days}")
    public Response getFlyPointShortStatsRaw(UserRole role, Long parentId, int days) {
        Map<String, Object> params = new HashMap<>();
        if (parentId != null) {
            params.put("parentId", parentId);
        }
        params.put("days", days);
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.FLY_POINT_GET_SHORT_STATS, role, params);
    }

    public List<UnitShortStatsResponse> getFlyPointShortStats(UserRole role, Long parentId, int days) {
        Response response = getFlyPointShortStatsRaw(role, parentId, days);
        validateSuccess(response, "Get fly-point short-stats for parent " + parentId);
        return DatabaseIntegrityValidator.extractList(response, UnitShortStatsResponse.class);
    }

    /**
     * UNIT → FLY_POINT → CREW: екіпаж прикріплений до точки вильоту.
     * Після FINISHED видачі на CREW залишок авто-переміщується на FLY_POINT.
     */
    @Step("FIXTURE: підготувати область CREWS з екіпажем на точці вильоту (attached)")
    public CrewRegionScenario prepareAttachedCrewScenario(String namePrefix) {
        Long memberId = ConfigProvider.getOwner1StorageId();
        StorageResponse member = storageFixture.getById(UserRole.ADMIN, memberId);
        Long parentId = member.getParent() != null ? member.getParent().getId() : memberId;

        StorageResponse unit = storageFixture.createUnitStorage(parentId, namePrefix + "unit-");
        StorageResponse flyPoint = storageFixture.createFlyPointStorage(unit.getId(), namePrefix + "fp-");
        StorageResponse crew = storageFixture.createCrewStorage(flyPoint.getId(), namePrefix + "crew-");

        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.CREWS, namePrefix + "reg-");
        addCrewRegionMembers(region.getId());
        regionFixture.addRegionLocations(region.getId(), unit.getId(), flyPoint.getId(), crew.getId());

        CrewRegionScenario scenario = CrewRegionScenario.builder()
                .region(region)
                .unit(unit)
                .childUnit(null)
                .flyPoint(flyPoint)
                .crew(crew)
                .memberStorageId(memberId)
                .build();
        testContext.set(ContextKey.CREW_STORAGE_ID, crew.getId());
        testContext.set(ContextKey.CREW_PARENT_UNIT_ID, unit.getId());
        return scenario;
    }

    /**
     * UNIT → FLY_POINT (без екіпажу) для видачі безпосередньо на точку вильоту.
     */
    @Step("FIXTURE: підготувати область CREWS з точкою вильоту")
    public CrewRegionScenario prepareFlyPointScenario(String namePrefix) {
        Long memberId = ConfigProvider.getOwner1StorageId();
        StorageResponse member = storageFixture.getById(UserRole.ADMIN, memberId);
        Long parentId = member.getParent() != null ? member.getParent().getId() : memberId;

        StorageResponse unit = storageFixture.createUnitStorage(parentId, namePrefix + "unit-");
        StorageResponse flyPoint = storageFixture.createFlyPointStorage(unit.getId(), namePrefix + "fp-");

        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.CREWS, namePrefix + "reg-");
        addCrewRegionMembers(region.getId());
        regionFixture.addRegionLocations(region.getId(), unit.getId(), flyPoint.getId());

        return CrewRegionScenario.builder()
                .region(region)
                .unit(unit)
                .childUnit(null)
                .flyPoint(flyPoint)
                .crew(null)
                .memberStorageId(memberId)
                .build();
    }

    /**
     * Ієрархія UNIT → child UNIT → CREW для тестів crew-units / crew-names.
     */
    @Step("FIXTURE: підготувати ієрархію UNIT→UNIT→CREW з областю CREWS")
    public CrewRegionScenario prepareHierarchyScenario(String namePrefix) {
        Long memberId = ConfigProvider.getOwner1StorageId();
        StorageResponse member = storageFixture.getById(UserRole.ADMIN, memberId);
        Long parentId = member.getParent() != null ? member.getParent().getId() : memberId;

        StorageResponse unitA = storageFixture.createUnitStorage(parentId, namePrefix + "uA-");
        StorageResponse unitAB = storageFixture.createUnitStorage(unitA.getId(), namePrefix + "uAB-");
        StorageResponse crew = storageFixture.createCrewStorage(unitAB.getId(), namePrefix + "crew-");

        StorageRegionResponse region = regionFixture.createRegion(
                unitA, StorageAccessMode.CREWS, namePrefix + "reg-");
        addCrewRegionMembers(region.getId());
        regionFixture.addRegionLocations(region.getId(), unitA.getId(), crew.getId());

        return CrewRegionScenario.builder()
                .region(region)
                .unit(unitA)
                .childUnit(unitAB)
                .flyPoint(null)
                .crew(crew)
                .memberStorageId(memberId)
                .build();
    }

    @Step("API: GET /relocations/creation-options storageId={storageId}")
    public RelocationCreationOptionsResponse getCreationOptions(UserRole role, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_GET_CREATION_OPTIONS, role, String.valueOf(storageId));
        validateSuccess(response, "Get relocation creation options for " + storageId);
        return response.as(RelocationCreationOptionsResponse.class);
    }

    @Step("API: GET /storages/names/crew-units storageId={storageId}")
    public List<StorageHierarchyResponse> getCrewUnits(UserRole role, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_GET_CREW_UNITS, role, String.valueOf(storageId));
        validateSuccess(response, "Get crew units for " + storageId);
        return DatabaseIntegrityValidator.extractList(response, StorageHierarchyResponse.class);
    }

    @Step("API: GET /storages/names/crews parentId={parentId}")
    public List<StorageResponse> getCrewNames(UserRole role, Long parentId, String nameFilter) {
        Map<String, Object> params = new HashMap<>();
        if (nameFilter != null && !nameFilter.isBlank()) {
            params.put("name", nameFilter);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_CREW_NAMES,
                role,
                params,
                String.valueOf(parentId));
        validateSuccess(response, "Get crew names for parent " + parentId);
        return DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
    }

    @Step("API: GET /storages/inventory/crews")
    public List<CrewResourceStockResponse> getCrewInventory(UserRole role, Map<String, Object> params) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_CREW_INVENTORY, role, params);
        validateSuccess(response, "Get crew inventory");
        return DatabaseIntegrityValidator.extractList(response, CrewResourceStockResponse.class);
    }

    public static boolean hierarchyContainsUnitWithChild(
            List<StorageHierarchyResponse> roots,
            Long unitId,
            Long childUnitId) {
        for (StorageHierarchyResponse root : roots) {
            if (Objects.equals(root.getId(), unitId)) {
                return root.getChildren() != null
                        && root.getChildren().stream().anyMatch(c -> Objects.equals(c.getId(), childUnitId));
            }
            if (root.getChildren() != null
                    && hierarchyContainsUnitWithChild(root.getChildren(), unitId, childUnitId)) {
                return true;
            }
        }
        return false;
    }

    /** OWNER_1 (relocation API) + Crew-Manager UNIT member for appendGrantedCrews at login. */
    private void addCrewRegionMembers(Long regionId) {
        long ownerMember = ConfigProvider.getOwner1StorageId();
        long crewManagerMember = ConfigProvider.getUnitStorageId();
        if (crewManagerMember == ownerMember) {
            regionFixture.addRegionMembers(regionId, ownerMember);
        } else {
            regionFixture.addRegionMembers(regionId, ownerMember, crewManagerMember);
        }
    }
}
