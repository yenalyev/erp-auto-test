package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageRegionDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRegionRequest;
import com.erp.models.response.StorageLocationLinkResponse;
import com.erp.models.response.StorageLocationSuggestionResponse;
import com.erp.models.response.StorageRegionLocationResponse;
import com.erp.models.response.StorageRegionMemberResponse;
import com.erp.models.response.StorageRegionResourceResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class StorageRegionFixture extends BaseFixture {

    public static final String SYSTEM_ALL_RESOURCES_REGION_NAME = "Системний регіон - ВСІ РЕСУРСИ";

    private final Set<Long> regionsToCleanup = new LinkedHashSet<>();

    public StorageRegionFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public void trackForCleanup(Long regionId) {
        if (regionId != null) {
            regionsToCleanup.add(regionId);
        }
    }

    public void untrackForCleanup(Long regionId) {
        if (regionId != null) {
            regionsToCleanup.remove(regionId);
        }
    }

    public void clearTrackedRegions() {
        regionsToCleanup.clear();
    }

    @Step("FIXTURE: видалити тестові області видимості")
    public void deleteTrackedRegions(UserRole role) {
        if (regionsToCleanup.isEmpty()) {
            return;
        }
        for (Long regionId : List.copyOf(regionsToCleanup)) {
            try {
                Response response = apiExecutor.execute(
                        ApiEndpointDefinition.STORAGE_REGION_DELETE, role, null, String.valueOf(regionId));
                if (response.statusCode() == 200) {
                    log.debug("Cleanup: deleted region id={}", regionId);
                } else {
                    log.warn("Cleanup: delete region id={} returned HTTP {}", regionId, response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Cleanup: failed to delete region id={}: {}", regionId, e.getMessage());
            }
        }
        regionsToCleanup.clear();
    }

    @Step("FIXTURE: видалити області видимості з префіксами імен {prefixes}")
    public void purgeRegionsByNamePrefixes(UserRole role, String... prefixes) {
        if (prefixes == null || prefixes.length == 0) {
            return;
        }
        List<StorageRegionResponse> regions = findRegions(role, null);
        for (StorageRegionResponse region : regions) {
            if (region == null || region.getId() == null || region.getName() == null) {
                continue;
            }
            boolean matches = false;
            for (String prefix : prefixes) {
                if (prefix != null && region.getName().startsWith(prefix)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            try {
                Response response = apiExecutor.execute(
                        ApiEndpointDefinition.STORAGE_REGION_DELETE, role, null, String.valueOf(region.getId()));
                if (response.statusCode() == 200) {
                    untrackForCleanup(region.getId());
                    log.info("Purge: deleted region id={} name={}", region.getId(), region.getName());
                } else {
                    log.warn("Purge: delete region id={} returned HTTP {}", region.getId(), response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Purge: failed to delete region id={}: {}", region.getId(), e.getMessage());
            }
        }
    }

    @Step("API: POST створити область видимості")
    public StorageRegionResponse createRegion(StorageRegionRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_REGION_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create storage region");
        StorageRegionResponse created = response.as(StorageRegionResponse.class);
        trackForCleanup(created.getId());
        return created;
    }

    @Step("API: POST створити область видимості recipient={recipient.id}")
    public StorageRegionResponse createRegion(
            StorageResponse recipient,
            StorageAccessMode accessMode,
            String namePrefix) {
        return createRegion(StorageRegionDataFactory.createRegion(recipient, accessMode, namePrefix));
    }

    @Step("API: GET область видимості id={regionId}")
    public StorageRegionResponse getById(UserRole role, Long regionId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_REGION_GET_BY_ID, role, null, String.valueOf(regionId));
        validateSuccess(response, "Get storage region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: GET /storages/regions")
    public List<StorageRegionResponse> findRegions(UserRole role, String nameFilter) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", 500);
        if (nameFilter != null && !nameFilter.isBlank()) {
            params.put("name", nameFilter);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_GET_ALL, role, params);
        validateSuccess(response, "Find storage regions");
        return DatabaseIntegrityValidator.extractList(response, StorageRegionResponse.class);
    }

    @Step("API: знайти системний регіон ВСІ РЕСУРСИ")
    public StorageRegionResponse findSystemAllResourcesRegion() {
        List<StorageRegionResponse> matches = findRegions(
                UserRole.ADMIN, SYSTEM_ALL_RESOURCES_REGION_NAME).stream()
                .filter(region -> SYSTEM_ALL_RESOURCES_REGION_NAME.equals(region.getName()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one system ALL RESOURCES region, found " + matches.size());
        }
        return matches.getFirst();
    }

    @Step("API: додати storage id={storageId} до системного регіону ВСІ РЕСУРСИ")
    public StorageRegionResponse attachMemberToSystemAllResourcesRegion(Long storageId) {
        return addRegionMembers(findSystemAllResourcesRegion().getId(), storageId);
    }

    @Step("API: прибрати storage id={storageId} із системного регіону ВСІ РЕСУРСИ")
    public StorageRegionResponse detachMemberFromSystemAllResourcesRegion(Long storageId) {
        return removeRegionMembers(findSystemAllResourcesRegion().getId(), storageId);
    }

    @Step("API: PUT оновити область видимості id={regionId}")
    public StorageRegionResponse updateRegion(Long regionId, StorageRegionRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_REGION_PUT_UPDATE,
                UserRole.ADMIN,
                request,
                String.valueOf(regionId));
        validateSuccess(response, "Update storage region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: DELETE область видимості id={regionId}")
    public Response deleteRegion(UserRole role, Long regionId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_REGION_DELETE, role, null, String.valueOf(regionId));
    }

    @Step("API: GET locations області id={regionId}")
    public List<StorageRegionLocationResponse> getRegionLocations(UserRole role, Long regionId) {
        Map<String, Object> params = Map.of("page", 0, "size", 500);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_GET_LOCATIONS,
                role,
                params,
                String.valueOf(regionId));
        validateSuccess(response, "Get region locations " + regionId);
        return DatabaseIntegrityValidator.extractList(response, StorageRegionLocationResponse.class);
    }

    @Step("API: GET members області id={regionId}")
    public List<StorageRegionMemberResponse> getRegionMembers(UserRole role, Long regionId) {
        Map<String, Object> params = Map.of("page", 0, "size", 500);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_GET_MEMBERS,
                role,
                params,
                String.valueOf(regionId));
        validateSuccess(response, "Get region members " + regionId);
        return DatabaseIntegrityValidator.extractList(response, StorageRegionMemberResponse.class);
    }

    @Step("API: PUT додати locations до області id={regionId}")
    public StorageRegionResponse addRegionLocations(Long regionId, Long... locationIds) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_PUT_ADD_LOCATIONS,
                UserRole.ADMIN,
                idListParams("locations", locationIds),
                String.valueOf(regionId));
        validateSuccess(response, "Add locations to region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: DELETE прибрати locations з області id={regionId}")
    public StorageRegionResponse removeRegionLocations(Long regionId, Long... locationIds) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_DELETE_LOCATIONS,
                UserRole.ADMIN,
                idListParams("locations", locationIds),
                String.valueOf(regionId));
        validateSuccess(response, "Remove locations from region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: PUT додати members до області id={regionId}")
    public StorageRegionResponse addRegionMembers(Long regionId, Long... memberIds) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_PUT_ADD_MEMBERS,
                UserRole.ADMIN,
                idListParams("members", memberIds),
                String.valueOf(regionId));
        validateSuccess(response, "Add members to region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: DELETE прибрати members з області id={regionId}")
    public StorageRegionResponse removeRegionMembers(Long regionId, Long... memberIds) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_DELETE_MEMBERS,
                UserRole.ADMIN,
                idListParams("members", memberIds),
                String.valueOf(regionId));
        validateSuccess(response, "Remove members from region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: GET explicit/regional links для storage id={storageId}")
    public List<StorageLocationLinkResponse> getStorageLocationLinks(UserRole role, Long storageId) {
        Map<String, Object> params = Map.of("page", 0, "size", 500);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_LOCATION_LINKS,
                role,
                params,
                String.valueOf(storageId));
        validateSuccess(response, "Get storage location links " + storageId);
        return DatabaseIntegrityValidator.extractList(response, StorageLocationLinkResponse.class);
    }

    @Step("API: PUT explicit grant — storage_id=visibleLocation, location_storage_id=viewer")
    public Response addExplicitLocations(Long visibleStorageId, Long... viewerStorageIds) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_PUT_ADD_LOCATION_LINKS,
                UserRole.ADMIN,
                idListParams("locations", viewerStorageIds),
                String.valueOf(visibleStorageId));
        validateSuccess(response, "Add explicit visibility for storage " + visibleStorageId);
        return response;
    }

    @Step("API: DELETE explicit revoke — storage_id=visibleLocation, location_storage_id=viewer")
    public Response removeExplicitLocations(Long visibleStorageId, Long... viewerStorageIds) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_DELETE_LOCATION_LINKS,
                UserRole.ADMIN,
                idListParams("locations", viewerStorageIds),
                String.valueOf(visibleStorageId));
        validateSuccess(response, "Remove explicit visibility for storage " + visibleStorageId);
        return response;
    }

    @Step("FIXTURE: очистити області/grants для viewer storageId={viewerStorageId}")
    public void purgeViewerVisibilityScope(UserRole role, Long viewerStorageId, StorageFixture storageFixture) {
        purgeRegionMemberships(role, viewerStorageId);
        purgeExplicitGrantsForViewer(role, viewerStorageId, storageFixture);
        logRemainingViewerVisibility(viewerStorageId, storageFixture);
    }

    private void purgeRegionMemberships(UserRole role, Long viewerStorageId) {
        List<StorageLocationLinkResponse> links = getStorageLocationLinks(role, viewerStorageId);
        Set<Long> regionIds = new LinkedHashSet<>();
        for (StorageLocationLinkResponse link : links) {
            if (link.getRegionId() != null) {
                regionIds.add(link.getRegionId());
            }
        }
        for (Long regionId : regionIds) {
            try {
                removeRegionMembers(regionId, viewerStorageId);
                log.info("Purge: removed viewer {} from region {}", viewerStorageId, regionId);
            } catch (Exception e) {
                log.warn("Purge: failed to remove viewer {} from region {}: {}",
                        viewerStorageId, regionId, e.getMessage());
            }
        }
    }

    private void purgeExplicitGrantsForViewer(UserRole role, Long viewerStorageId, StorageFixture storageFixture) {
        List<StorageResponse> visibleNames = storageFixture.getNames(UserRole.OWNER_2, true, null);
        for (StorageResponse visible : visibleNames) {
            if (Objects.equals(visible.getId(), viewerStorageId)) {
                continue;
            }
            try {
                List<StorageLocationLinkResponse> viewers =
                        getStorageLocationLinks(role, visible.getId());
                boolean viewerLinked = viewers.stream()
                        .anyMatch(link -> Objects.equals(link.getLocationId(), viewerStorageId));
                if (viewerLinked) {
                    removeExplicitLocations(visible.getId(), viewerStorageId);
                    log.info("Purge: revoked explicit grant visible={} viewer={}",
                            visible.getId(), viewerStorageId);
                }
            } catch (Exception e) {
                log.warn("Purge: failed to revoke grant visible={} viewer={}: {}",
                        visible.getId(), viewerStorageId, e.getMessage());
            }
        }
    }

    private void logRemainingViewerVisibility(Long viewerStorageId, StorageFixture storageFixture) {
        List<StorageResponse> names = storageFixture.getNames(UserRole.OWNER_2, true, null);
        List<Long> ids = names.stream().map(StorageResponse::getId).toList();
        if (ids.size() == 1 && Objects.equals(ids.getFirst(), viewerStorageId)) {
            log.info("Purge: viewer {} sees only own storage in /names", viewerStorageId);
            return;
        }
        log.warn("Purge: viewer {} still sees {} entries in /names: {}",
                viewerStorageId, ids.size(), ids);
    }

    @Step("API: GET resources області id={regionId}")
    public List<StorageRegionResourceResponse> getRegionResources(UserRole role, Long regionId) {
        Map<String, Object> params = Map.of("page", 0, "size", 500);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_GET_RESOURCES,
                role,
                params,
                String.valueOf(regionId));
        validateSuccess(response, "Get region resources " + regionId);
        return DatabaseIntegrityValidator.extractList(response, StorageRegionResourceResponse.class);
    }

    @Step("API: PUT додати resources до області id={regionId}")
    public StorageRegionResponse addRegionResources(Long regionId, Long... resourceIds) {
        Response response = addRegionResourcesRaw(UserRole.ADMIN, regionId, resourceIds);
        validateSuccess(response, "Add resources to region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: PUT додати resources до області id={regionId} (raw)")
    public Response addRegionResourcesRaw(UserRole role, Long regionId, Long... resourceIds) {
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_PUT_ADD_RESOURCES,
                role,
                idListParams("resources", resourceIds),
                String.valueOf(regionId));
    }

    @Step("API: DELETE прибрати resources з області id={regionId}")
    public StorageRegionResponse removeRegionResources(Long regionId, Long... resourceIds) {
        Response response = removeRegionResourcesRaw(UserRole.ADMIN, regionId, resourceIds);
        validateSuccess(response, "Remove resources from region " + regionId);
        return response.as(StorageRegionResponse.class);
    }

    @Step("API: DELETE прибрати resources з області id={regionId} (raw)")
    public Response removeRegionResourcesRaw(UserRole role, Long regionId, Long... resourceIds) {
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_REGION_DELETE_RESOURCES,
                role,
                idListParams("resources", resourceIds),
                String.valueOf(regionId));
    }

    @Step("API: GET /storages/locations/suggest")
    public List<StorageLocationSuggestionResponse> suggestLocations(UserRole role, String nameFilter) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", 500);
        if (nameFilter != null && !nameFilter.isBlank()) {
            params.put("name", nameFilter);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_LOCATION_SUGGEST, role, params);
        validateSuccess(response, "Suggest locations for linking");
        return DatabaseIntegrityValidator.extractList(response, StorageLocationSuggestionResponse.class);
    }

    private static Map<String, Object> idListParams(String paramName, Long... ids) {
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, Arrays.asList(ids));
        return params;
    }
}
