package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.models.response.CrewResourceStockResponse;
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
            StorageResponse crew,
            Long memberStorageId) {
    }

    /**
     * Canonical setup (як {@code StorageFacadeForCrewsIT}):
     * UNIT → CREW; область CREWS з member=OWNER_1 та location=UNIT.
     */
    @Step("FIXTURE: підготувати область CREWS з одним екіпажем")
    public CrewRegionScenario prepareSingleCrewScenario(String namePrefix) {
        Long memberId = ConfigProvider.getOwner1StorageId();
        StorageResponse member = storageFixture.getById(UserRole.ADMIN, memberId);
        Long parentId = member.getParent() != null ? member.getParent().getId() : memberId;

        StorageResponse unit = storageFixture.createUnitStorage(parentId, namePrefix + "unit-");
        StorageResponse crew = storageFixture.createCrewStorage(unit.getId(), namePrefix + "crew-");

        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.CREWS, namePrefix + "reg-");
        addCrewRegionMembers(region.getId());
        regionFixture.addRegionLocations(region.getId(), unit.getId());

        CrewRegionScenario scenario = CrewRegionScenario.builder()
                .region(region)
                .unit(unit)
                .childUnit(null)
                .crew(crew)
                .memberStorageId(memberId)
                .build();
        testContext.set(ContextKey.CREW_STORAGE_ID, crew.getId());
        testContext.set(ContextKey.CREW_PARENT_UNIT_ID, unit.getId());
        return scenario;
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
        regionFixture.addRegionLocations(region.getId(), unitA.getId());

        return CrewRegionScenario.builder()
                .region(region)
                .unit(unitA)
                .childUnit(unitAB)
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
