package com.erp.api.endpoints;

import com.erp.api.types.TypeReference;
import com.erp.models.request.*;
import com.erp.models.response.*;
import io.restassured.http.Method;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 🎯 Central registry for ALL API endpoints
 * <p>
 * Features:
 * - Dynamic path variables support
 * - Request/Response class mapping with full generic support
 * - Schema validation
 * - Operation discriminator for duplicate paths
 * <p>
 * Uses TypeReference for type-safe generic handling
 */
@Getter
public enum ApiEndpointDefinition {

    // ========================================
    // RESOURCE ENDPOINTS
    // ========================================

    RESOURCE_GET_ALL(
            "/api/v1/resources",
            Method.GET,
            "schemas/resource-list-schema.json",
            "Get all resources - ",
            null,  // no request body
            new TypeReference<List<ResourceResponse>>() {},  // ✅ Type-safe List
            null
    ),

    RESOURCE_GET_PAGE(
            "/api/v1/resources?size={size}",
            Method.GET,
            "schemas/resource-list-schema.json",
            "Get resources page (limited size)",
            null,
            new TypeReference<List<ResourceResponse>>() {},
            null
    ),

    RESOURCE_CREATE(
            "/api/v1/resources",
            Method.POST,
            "schemas/resource-response-schema.json",
            "Create new resource",
            new TypeReference<ResourceRequest>() {},
            new TypeReference<ResourceResponse>() {},
            "CREATE"
    ),

    RESOURCE_UPDATE_NAME(
            "/api/v1/resources/{id}",
            Method.PUT,
            "schemas/resource-response-schema.json",
            "Update resource name",
            new TypeReference<ResourceRequest>() {},
            new TypeReference<ResourceResponse>() {},
            "UPDATE_NAME"
    ),

    RESOURCE_UPDATE_UNIT(
            "/api/v1/resources/{id}",
            Method.PUT,
            "schemas/resource-response-schema.json",
            "Update resource measurement unit",
            new TypeReference<ResourceRequest>() {},
            new TypeReference<ResourceResponse>() {},
            "UPDATE_UNIT"
    ),

    RESOURCE_CATEGORY_GET_ALL(
            "/api/v1/resources/categories",
            Method.GET,
            null,
            "Get all resource categories",
            null,
            new TypeReference<List<ResourceCategoryResponse>>() {},
            null
    ),

    APP_CONFIG_GET_ALL(
            "/api/v1/app-config",
            Method.GET,
            null,
            "Get application configuration",
            null,
            new TypeReference<List<Object>>() {},
            null
    ),

    RESOURCE_GET_BY_ID(
            "/api/v1/resources/{id}",
            Method.GET,
            "schemas/resource-response-schema.json",
            "Get resource by id",
            null,
            new TypeReference<ResourceResponse>() {},
            null
    ),

    RESOURCE_AUTOCOMPLETE(
            "/api/v1/resources/autocomplete",
            Method.GET,
            null,
            "Autocomplete resources",
            null,
            new TypeReference<List<ResourceResponse>>() {},
            null
    ),

    RESOURCE_DEACTIVATE(
            "/api/v1/resources/{id}",
            Method.DELETE,
            null,
            "Deactivate (soft delete) resource",
            null,
            null,
            "DEACTIVATE"
    ),

    RESOURCE_UNARCHIVE(
            "/api/v1/resources/unarchive/{id}",
            Method.PUT,
            null,
            "Reactivate (unarchive) resource",
            null,
            null,
            "UNARCHIVE"
    ),

    RESOURCE_PRICE_GET_PAGE(
            "/api/v1/resources-price",
            Method.GET,
            null,
            "Get resource prices page",
            null,
            new TypeReference<List<ResourcePriceResponse>>() {},
            null
    ),

    ALERT_POST_CREATE(
            "/api/v1/alerts",
            Method.POST,
            null,
            "Create storage alert",
            new TypeReference<StorageAlertRequest>() {},
            new TypeReference<StorageAlertResponse>() {},
            "CREATE_ALERT"
    ),

    ALERT_GET_BY_STORAGE(
            "/api/v1/alerts/storage/{storageId}",
            Method.GET,
            null,
            "Get storage alert by storage id",
            null,
            new TypeReference<StorageAlertResponse>() {},
            null
    ),

    ALERT_PUT_UPDATE(
            "/api/v1/alerts/{id}",
            Method.PUT,
            null,
            "Update storage alert",
            new TypeReference<StorageAlertRequest>() {},
            new TypeReference<StorageAlertResponse>() {},
            "UPDATE_ALERT"
    ),

    ALERT_DELETE(
            "/api/v1/alerts/{id}",
            Method.DELETE,
            null,
            "Delete storage alert",
            null,
            null,
            "DELETE_ALERT"
    ),

    // ========================================
    // MEASUREMENT UNIT ENDPOINTS
    // ========================================
    MEASUREMENT_UNIT_GET_ALL(
            "/api/v1/measurement-unit",
            Method.GET,
            "schemas/measurement-unit-list-schema.json",
            "Get all measurement units",
            null,
            new TypeReference<List<MeasurementUnitResponse>>() {},
            null
    ),

    MEASUREMENT_UNIT_POST_CREATE(
            "/api/v1/measurement-unit",
            Method.POST,
            "schemas/measurement-unit-schema.json",
            "Create measurement unit",
            new TypeReference<MeasurementUnitResponse>() {},
            new TypeReference<MeasurementUnitResponse>() {},
            "CREATE_MEASUREMENT_UNIT"
    ),

    MEASUREMENT_UNIT_POST_CREATE_INVALID_NAME(
            "/api/v1/measurement-unit",
            Method.POST,
            "schemas/measurement-unit-schema.json",
            "Create measurement unit",
            new TypeReference<MeasurementUnitResponse>() {},
            new TypeReference<MeasurementUnitResponse>() {},
            "CREATE_MEASUREMENT_UNIT"
    ),


    // ========================================
    // TECHNOLOGICAL MAP ENDPOINTS
    // ========================================

    TECH_MAP_GET_ALL(
            "/api/v1/technological-maps",
            Method.GET,
            "schemas/technological-maps/technological-map-response-list-schema.json",
            "Get all technological maps",
            null,
            new TypeReference<List<TechnologicalMapResponse>>() {},  // ✅ Type-safe List
            null
    ),

    TECH_MAP_GET_BY_STORAGE(
            "/api/v1/technological-maps?storageIds={storageId}&size=100",
            Method.GET,
            "schemas/technological-maps/technological-map-response-list-schema.json",
            "Get technological maps filtered by storage",
            null,
            new TypeReference<List<TechnologicalMapResponse>>() {},
            null
    ),

    TECH_MAP_GET_BY_STORAGE_AND_NAME(
            "/api/v1/technological-maps?storageIds={storageId}&name={name}&size=10",
            Method.GET,
            "schemas/technological-maps/technological-map-response-list-schema.json",
            "Get technological maps filtered by storage and name",
            null,
            new TypeReference<List<TechnologicalMapResponse>>() {},
            null
    ),

    TECH_MAP_CREATE(
            "/api/v1/technological-maps",
            Method.POST,
            "schemas/technological-maps/technological-map-response-schema.json",
            "Create technological map",
            new TypeReference<TechnologicalMapRequest>() {},
            new TypeReference<TechnologicalMapResponse>() {},
            "CREATE_TECH_MAP"
    ),

    TECH_MAP_UPDATE_NAME(
            "/api/v1/technological-maps/{id}",
            Method.PUT,
            "schemas/technological-maps/technological-map-response-schema.json",
            "Update name in technological map",
            new TypeReference<TechnologicalMapRequest>() {},
            new TypeReference<TechnologicalMapResponse>() {},
            "UPDATE_TECH_MAP_NAME"
    ),

    TECH_MAP_DEACTIVATE(
            "/api/v1/technological-maps/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Deactivate (archive) technological map",
            null,
            null,
            null
    ),

    TECH_MAP_GET_ACTIVE_BY_STORAGE_AND_NAME(
            "/api/v1/technological-maps?storageIds={storageId}&name={name}&isActive=true&size=10",
            Method.GET,
            "schemas/technological-maps/technological-map-response-list-schema.json",
            "Get active technological maps filtered by storage and name",
            null,
            new TypeReference<List<TechnologicalMapResponse>>() {},
            null
    ),

    TECH_MAP_MODE_GET(
            "/api/v1/technological-maps/mode?storageId={storageId}",
            Method.GET,
            "schemas/technological-maps/storage-technological-map-mode-response-schema.json",
            "Get technological map edit mode for storage",
            null,
            new TypeReference<StorageTechnologicalMapModeResponse>() {},
            null
    ),

    TECH_MAP_MODE_UPDATE(
            "/api/v1/technological-maps/mode",
            Method.PUT,
            "schemas/technological-maps/storage-technological-map-mode-response-schema.json",
            "Update technological map edit mode for storage",
            new TypeReference<StorageTechnologicalMapModeRequest>() {},
            new TypeReference<StorageTechnologicalMapModeResponse>() {},
            null
    ),

    // ========================================
    // STORAGE ENDPOINTS
    // ========================================

    STORAGE_GET_ALL(
            "/api/v1/storages",
            Method.GET,
            "schemas/storages/storage-paged-list-schema.json",
            "Get all storages (paged)",
            null,
            new TypeReference<List<StorageResponse>>() {},
            null
    ),

    STORAGE_GET_SUPPLIER(
            "/api/v1/storages?types=SUPPLIER&size=1",
            Method.GET,
            "schemas/storages/storage-paged-list-schema.json",
            "Get first SUPPLIER storage",
            null,
            new TypeReference<List<StorageResponse>>() {},
            null
    ),

    STORAGE_GET_BY_ID(
            "/api/v1/storages/{id}",
            Method.GET,
            "schemas/storages/storage-response-schema.json",
            "Get storage by id",
            null,
            new TypeReference<StorageResponse>() {},
            null
    ),

    STORAGE_GET_NAMES(
            "/api/v1/storages/names",
            Method.GET,
            "schemas/storages/storage-names-list-schema.json",
            "Get storage names list",
            null,
            new TypeReference<List<StorageResponse>>() {},
            null
    ),

    STORAGE_GET_MY_UNITS(
            "/api/v1/storages/names/my-units",
            Method.GET,
            "schemas/storages/storage-names-list-schema.json",
            "Get internal units for current user",
            null,
            new TypeReference<List<StorageResponse>>() {},
            null
    ),

    STORAGE_GET_CREW_UNITS(
            "/api/v1/storages/names/crew-units?storageId={id}",
            Method.GET,
            "schemas/storages/storage-hierarchy-list-schema.json",
            "Get hierarchical units that have crews for member storage",
            null,
            new TypeReference<List<StorageHierarchyResponse>>() {},
            null
    ),

    STORAGE_GET_CREW_NAMES(
            "/api/v1/storages/names/crews?parentId={id}",
            Method.GET,
            "schemas/storages/storage-names-list-schema.json",
            "Get crew names by parent unit",
            null,
            new TypeReference<List<StorageResponse>>() {},
            null
    ),

    STORAGE_GET_CREW_INVENTORY(
            "/api/v1/storages/inventory/crews",
            Method.GET,
            "schemas/storages/crew-resource-stock-paged-list-schema.json",
            "Get crew resource stock or income report",
            null,
            new TypeReference<List<CrewResourceStockResponse>>() {},
            null
    ),

    STORAGE_POST_CREATE(
            "/api/v1/storages",
            Method.POST,
            "schemas/storages/storage-response-schema.json",
            "Create new storage",
            new TypeReference<StorageRequest>() {},
            new TypeReference<StorageResponse>() {},
            "CREATE_STORAGE"
    ),

    STORAGE_PUT_UPDATE(
            "/api/v1/storages/{id}",
            Method.PUT,
            "schemas/storages/storage-response-schema.json",
            "Update storage",
            new TypeReference<StorageRequest>() {},
            new TypeReference<StorageResponse>() {},
            "UPDATE_STORAGE"
    ),

    STORAGE_DELETE_DEACTIVATE(
            "/api/v1/storages/{id}",
            Method.DELETE,
            null,
            "Deactivate (archive) storage",
            null,
            null,
            "DEACTIVATE"
    ),

    STORAGE_PUT_UNARCHIVE(
            "/api/v1/storages/unarchive/{id}",
            Method.PUT,
            null,
            "Reactivate (unarchive) storage",
            null,
            null,
            "UNARCHIVE"
    ),

    // ========================================
    // STORAGE REGION (visibility area) ENDPOINTS
    // ========================================

    STORAGE_REGION_GET_ALL(
            "/api/v1/storages/regions",
            Method.GET,
            "schemas/storages/storage-region-paged-list-schema.json",
            "Get storage visibility regions (paged)",
            null,
            new TypeReference<List<StorageRegionResponse>>() {},
            null
    ),

    STORAGE_REGION_POST_CREATE(
            "/api/v1/storages/regions",
            Method.POST,
            "schemas/storages/storage-region-response-schema.json",
            "Create storage visibility region",
            new TypeReference<StorageRegionRequest>() {},
            new TypeReference<StorageRegionResponse>() {},
            "CREATE_REGION"
    ),

    STORAGE_REGION_GET_BY_ID(
            "/api/v1/storages/regions/{regionId}",
            Method.GET,
            "schemas/storages/storage-region-response-schema.json",
            "Get storage visibility region by id",
            null,
            new TypeReference<StorageRegionResponse>() {},
            null
    ),

    STORAGE_REGION_PUT_UPDATE(
            "/api/v1/storages/regions/{regionId}",
            Method.PUT,
            "schemas/storages/storage-region-response-schema.json",
            "Update storage visibility region",
            new TypeReference<StorageRegionRequest>() {},
            new TypeReference<StorageRegionResponse>() {},
            "UPDATE_REGION"
    ),

    STORAGE_REGION_DELETE(
            "/api/v1/storages/regions/{regionId}",
            Method.DELETE,
            null,
            "Delete storage visibility region",
            null,
            null,
            "DELETE_REGION"
    ),

    STORAGE_REGION_GET_LOCATIONS(
            "/api/v1/storages/regions/{regionId}/locations",
            Method.GET,
            "schemas/storages/storage-region-location-paged-list-schema.json",
            "Get locations in visibility region",
            null,
            new TypeReference<List<StorageRegionLocationResponse>>() {},
            null
    ),

    STORAGE_REGION_PUT_ADD_LOCATIONS(
            "/api/v1/storages/regions/{regionId}/locations",
            Method.PUT,
            "schemas/storages/storage-region-response-schema.json",
            "Add locations to visibility region",
            null,
            new TypeReference<StorageRegionResponse>() {},
            "ADD_REGION_LOCATIONS"
    ),

    STORAGE_REGION_DELETE_LOCATIONS(
            "/api/v1/storages/regions/{regionId}/locations",
            Method.DELETE,
            "schemas/storages/storage-region-response-schema.json",
            "Remove locations from visibility region",
            null,
            new TypeReference<StorageRegionResponse>() {},
            "REMOVE_REGION_LOCATIONS"
    ),

    STORAGE_REGION_GET_MEMBERS(
            "/api/v1/storages/regions/{regionId}/members",
            Method.GET,
            "schemas/storages/storage-region-location-paged-list-schema.json",
            "Get members of visibility region",
            null,
            new TypeReference<List<StorageRegionMemberResponse>>() {},
            null
    ),

    STORAGE_REGION_PUT_ADD_MEMBERS(
            "/api/v1/storages/regions/{regionId}/members",
            Method.PUT,
            "schemas/storages/storage-region-response-schema.json",
            "Add members to visibility region",
            null,
            new TypeReference<StorageRegionResponse>() {},
            "ADD_REGION_MEMBERS"
    ),

    STORAGE_REGION_DELETE_MEMBERS(
            "/api/v1/storages/regions/{regionId}/members",
            Method.DELETE,
            "schemas/storages/storage-region-response-schema.json",
            "Remove members from visibility region",
            null,
            new TypeReference<StorageRegionResponse>() {},
            "REMOVE_REGION_MEMBERS"
    ),

    STORAGE_REGION_GET_RESOURCES(
            "/api/v1/storages/regions/{regionId}/resources",
            Method.GET,
            "schemas/storages/storage-region-resource-paged-list-schema.json",
            "Get resources in visibility region",
            null,
            new TypeReference<List<StorageRegionResourceResponse>>() {},
            null
    ),

    STORAGE_REGION_PUT_ADD_RESOURCES(
            "/api/v1/storages/regions/{regionId}/resources",
            Method.PUT,
            "schemas/storages/storage-region-response-schema.json",
            "Add resources to visibility region",
            null,
            new TypeReference<StorageRegionResponse>() {},
            "ADD_REGION_RESOURCES"
    ),

    STORAGE_REGION_DELETE_RESOURCES(
            "/api/v1/storages/regions/{regionId}/resources",
            Method.DELETE,
            "schemas/storages/storage-region-response-schema.json",
            "Remove resources from visibility region",
            null,
            new TypeReference<StorageRegionResponse>() {},
            "REMOVE_REGION_RESOURCES"
    ),

    STORAGE_GET_LOCATION_LINKS(
            "/api/v1/storages/{storageId}/locations",
            Method.GET,
            "schemas/storages/storage-location-link-paged-list-schema.json",
            "Get explicit and regional location links for storage",
            null,
            new TypeReference<List<StorageLocationLinkResponse>>() {},
            null
    ),

    STORAGE_PUT_ADD_LOCATION_LINKS(
            "/api/v1/storages/{storageId}/locations",
            Method.PUT,
            null,
            "Grant explicit location visibility to storage",
            null,
            null,
            "ADD_STORAGE_LOCATION_LINKS"
    ),

    STORAGE_DELETE_LOCATION_LINKS(
            "/api/v1/storages/{storageId}/locations",
            Method.DELETE,
            null,
            "Revoke explicit location visibility from storage",
            null,
            null,
            "REMOVE_STORAGE_LOCATION_LINKS"
    ),

    STORAGE_GET_LOCATION_SUGGEST(
            "/api/v1/storages/locations/suggest",
            Method.GET,
            "schemas/storages/storage-location-suggest-paged-list-schema.json",
            "Suggest storages and regions for visibility linking",
            null,
            new TypeReference<List<StorageLocationSuggestionResponse>>() {},
            null
    ),

    // ========================================
    // STATISTIC ENDPOINTS
    // ========================================

    STATISTIC_GET_PLAN(
            "/api/v1/statistics/plan?storageId={id}",
            Method.GET,
            "schemas/statistics/plan-statistics-response-schema.json",
            "Get statistic plan",
            null,
            new TypeReference<PlanStatisticsResponse>() {},
            null
    ),

    // ========================================
    // PLAN ENDPOINTS
    // ========================================

    PLAN_GET_ALL(
            "/api/v1/plans?storageId={id}",
            Method.GET,
            "schemas/plans/plan-response-list-schema.json",
            "Get all plans",
            null,
            new TypeReference<List<PlanResponse>>() {},
            null
    ),

    /** Admin «Всі локації» on /plans — no storageId filter. */
    PLAN_GET_ALL_ADMIN(
            "/api/v1/plans",
            Method.GET,
            "schemas/plans/plan-response-list-schema.json",
            "Get all plans across locations (admin)",
            null,
            new TypeReference<List<PlanResponse>>() {},
            null
    ),

    PLAN_POST_CREATE(
            "/api/v1/plans",
            Method.POST,
            "schemas/plans/plan-response-schema.json",
            "Create plan",
            new TypeReference<PlanRequest>() {},
            new TypeReference<PlanResponse>() {},
            "CREATE_PLAN"
    ),

    PLAN_PUT_UPDATE(
            "/api/v1/plans/{id}",
            Method.PUT,
            "schemas/plans/plan-response-schema.json",
            "Update plan",
            new TypeReference<PlanRequest>() {},
            new TypeReference<PlanResponse>() {},
            "UPDATE_PLAN"
    ),

    PLAN_GET_BY_ID(
            "/api/v1/plans/{id}",
            Method.GET,
            "schemas/plans/plan-response-schema.json",
            "Get plan by id",
            null,
            new TypeReference<PlanResponse>() {},
            null
    ),

    PLAN_DELETE(
            "/api/v1/plans/{id}",
            Method.DELETE,
            null,
            "Delete plan",
            null,
            null,
            null
    ),

    // ========================================
    // GLOBAL PLAN ENDPOINTS
    // ========================================

    GLOBAL_PLAN_GET_ALL(
            "/api/v1/global-plans",
            Method.GET,
            "schemas/global-plans/global-plan-list-schema.json",
            "Get all global plans",
            null,
            new TypeReference<List<GlobalPlanResponse>>() {},
            null
    ),

    GLOBAL_PLAN_GET_BY_ID(
            "/api/v1/global-plans/{id}",
            Method.GET,
            "schemas/global-plans/global-plan-response-schema.json",
            "Get global plan by id",
            null,
            new TypeReference<GlobalPlanResponse>() {},
            null
    ),

    GLOBAL_PLAN_POST_CREATE(
            "/api/v1/global-plans",
            Method.POST,
            "schemas/global-plans/global-plan-response-schema.json",
            "Create global plan",
            new TypeReference<GlobalPlanRequest>() {},
            new TypeReference<GlobalPlanResponse>() {},
            "CREATE_GLOBAL_PLAN"
    ),

    GLOBAL_PLAN_PUT_UPDATE(
            "/api/v1/global-plans/{id}",
            Method.PUT,
            "schemas/global-plans/global-plan-response-schema.json",
            "Update global plan",
            new TypeReference<GlobalPlanRequest>() {},
            new TypeReference<GlobalPlanResponse>() {},
            "UPDATE_GLOBAL_PLAN"
    ),

    GLOBAL_PLAN_DELETE(
            "/api/v1/global-plans/{id}",
            Method.DELETE,
            null,
            "Delete global plan",
            null,
            null,
            null
    ),

    GLOBAL_PLAN_DECOMPOSE(
            "/api/v1/global-plans/{id}/decompose",
            Method.POST,
            "schemas/global-plans/decomposition-response-schema.json",
            "Decompose global plan",
            new TypeReference<DecompositionRequest>() {},
            new TypeReference<DecompositionResponse>() {},
            null
    ),

    GLOBAL_PLAN_GENERATE(
            "/api/v1/global-plans/{id}/generate",
            Method.POST,
            "schemas/global-plans/generation-response-schema.json",
            "Generate per-location plans from global plan",
            new TypeReference<DecompositionRequest>() {},
            new TypeReference<GenerationResponse>() {},
            null
    ),

    GLOBAL_PLAN_REQUIREMENTS_EXPORT(
            "/api/v1/global-plans/requirements/export",
            Method.POST,
            null,
            "Export global plan requirements to Excel",
            new TypeReference<RequirementsExportRequest>() {},
            null,
            null
    ),

    // ========================================
    // PRODUCTION ENDPOINTS
    // ========================================

    PRODUCTION_GET_JOURNAL_PAGE(
            "/api/v1/productions",
            Method.GET,
            "schemas/productions/production-response-list-schema.json",
            "Get production journal page (UI-aligned filters and sort)",
            null,
            new TypeReference<List<ManufacturingItemResponse>>() {},
            null
    ),

    PRODUCTION_GET_ALL_BY_STORE_ID(
            "/api/v1/productions?storageIds={id}&size=500",
            Method.GET,
            "schemas/productions/production-response-list-schema.json",
            "Get all production by store",
            null,
            new TypeReference<List<ManufacturingItemResponse>>() {},
            null
    ),

    PRODUCTION_GET_PAGE_BY_STORE_ID(
            "/api/v1/productions?storageIds={id}&size=1",
            Method.GET,
            "schemas/productions/production-response-list-schema.json",
            "Get production page metadata (total count)",
            null,
            new TypeReference<List<ManufacturingItemResponse>>() {},
            null
    ),

    PRODUCTION_GET_BY_ID(
            "/api/v1/productions/{id}?storageId={storageId}",
            Method.GET,
            "schemas/productions/manufacturing-item-response-schema.json",
            "Get production by id",
            null,
            new TypeReference<ManufacturingItemResponse>() {},
            null
    ),

    PRODUCTION_POST_CREATE(
            "/api/v1/productions/{storageId}",
            Method.POST,
            "schemas/productions/production-create-response-schema.json",
            "Create production",
            new TypeReference<ManufacturingListRequest>() {},
            new TypeReference<List<ManufacturingItemResponse>>() {},
            "CREATE_PRODUCTIONS"
    ),

    PRODUCTION_PUT_UPDATE(
            "/api/v1/productions/{id}/{storageId}",
            Method.PUT,
            "schemas/productions/manufacturing-item-response-schema.json",
            "Update production",
            new TypeReference<ManufacturingListRequest>() {},
            new TypeReference<ManufacturingItemResponse>() {},
            "UPDATE_PRODUCTION"
    ),

    PRODUCTION_DELETE(
            "/api/v1/productions/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete production",
            null,
            null,
            "DELETE_PRODUCTION"
    ),

    // ========================================
    // DISASSEMBLE ENDPOINTS
    // ========================================

    DISASSEMBLE_POST_CREATE(
            "/api/v1/disassemble/{storageId}",
            Method.POST,
            null,
            "Create disassemble",
            new TypeReference<DisassembleListRequest>() {},
            null,
            "CREATE_DISASSEMBLE"
    ),

    // ========================================
    // NON-SERIES PRODUCTION ENDPOINTS
    // ========================================

    NON_SERIES_PRODUCTION_GET_ALL(
            "/api/v1/non-series-production",
            Method.GET,
            "schemas/non-series-production/non-series-production-response-list-schema.json",
            "Get all non-series production by store",
            null,
            new TypeReference<List<NonSeriesProductionResponse>>() {},
            null
    ),

    NON_SERIES_PRODUCTION_GET_TOTAL(
            "/api/v1/non-series-production/total",
            Method.GET,
            "schemas/non-series-production/non-series-production-total-response-schema.json",
            "Get non-series production total amount",
            null,
            new TypeReference<NonSeriesProductionTotalResponse>() {},
            null
    ),

    NON_SERIES_PRODUCTION_GET_BY_ID(
            "/api/v1/non-series-production/{id}?storageId={storageId}",
            Method.GET,
            "schemas/non-series-production/non-series-production-response-schema.json",
            "Get non-series production by id",
            null,
            new TypeReference<NonSeriesProductionResponse>() {},
            null
    ),

    NON_SERIES_PRODUCTION_POST_CREATE(
            "/api/v1/non-series-production",
            Method.POST,
            "schemas/non-series-production/non-series-production-response-schema.json",
            "Create non-series production",
            new TypeReference<NonSeriesProductionRequest>() {},
            new TypeReference<NonSeriesProductionResponse>() {},
            "CREATE_NON_SERIES_PRODUCTION"
    ),

    NON_SERIES_PRODUCTION_PUT_UPDATE(
            "/api/v1/non-series-production/{id}",
            Method.PUT,
            "schemas/non-series-production/non-series-production-response-schema.json",
            "Update non-series production",
            new TypeReference<NonSeriesProductionRequest>() {},
            new TypeReference<NonSeriesProductionResponse>() {},
            "UPDATE_NON_SERIES_PRODUCTION"
    ),

    NON_SERIES_PRODUCTION_DELETE(
            "/api/v1/non-series-production/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete non-series production",
            null,
            null,
            "DELETE_NON_SERIES_PRODUCTION"
    ),

    // ========================================
    // INVENTORY ENDPOINTS
    // ========================================

    STORAGE_INVENTORY_GET(
            "/api/v1/storages/{id}/inventory?size=500",
            Method.GET,
            "schemas/inventory/storage-inventory-list-schema.json",
            "Get storage inventory",
            null,
            new TypeReference<List<StorageItemResponse>>() {},
            null
    ),

    STORAGE_INVENTORY_GET_TRACKED(
            "/api/v1/storages/{id}/inventory?size=50",
            Method.GET,
            "schemas/inventory/storage-inventory-list-schema.json",
            "Get storage inventory (limited page for stock checks)",
            null,
            new TypeReference<List<StorageItemResponse>>() {},
            null
    ),

    STORAGE_INVENTORY_PUT(
            "/api/v1/storages/{id}/inventory",
            Method.PUT,
            "schemas/storages/storage-response-schema.json",
            "Update storage inventory",
            new TypeReference<InventoryRequest>() {},
            new TypeReference<StorageResponse>() {},
            "UPDATE_INVENTORY"
    ),

    STORAGE_INVENTORY_BATCHES_GET(
            "/api/v1/storages/{id}/inventory/{storageItemId}/batches?isProduced=true",
            Method.GET,
            "schemas/inventory/storage-item-batch-list-schema.json",
            "Get storage item produced batches",
            null,
            new TypeReference<List<StorageItemBatchResponse>>() {},
            null
    ),

    STORAGE_INVENTORY_BATCHES_GET_NON_PRODUCED(
            "/api/v1/storages/{id}/inventory/{storageItemId}/batches?isProduced=false",
            Method.GET,
            "schemas/inventory/storage-item-batch-list-schema.json",
            "Get storage item non-produced batches",
            null,
            new TypeReference<List<StorageItemBatchResponse>>() {},
            null
    ),

    STORAGE_INVENTORY_STATUS_GET(
            "/api/v1/storages/{id}/inventory/status",
            Method.GET,
            "schemas/inventory/inventory-session-status-schema.json",
            "Get material inventory session status",
            null,
            new TypeReference<InventorySessionStatus>() {},
            null
    ),

    STORAGE_INVENTORY_STATUS_PUT(
            "/api/v1/storages/{id}/inventory/status",
            Method.PUT,
            "schemas/inventory/inventory-session-status-schema.json",
            "Open or close material inventory session",
            new TypeReference<InventorySessionStatus>() {},
            new TypeReference<InventorySessionStatus>() {},
            "OPEN_INVENTORY_SESSION"
    ),

    STORAGE_INVENTORY_MULTI_GET(
            "/api/v1/storages/inventory",
            Method.GET,
            "schemas/inventory/multi-location-inventory-list-schema.json",
            "Get multi-location inventory page",
            null,
            new TypeReference<List<StorageItemResponse>>() {},
            null
    ),

    EXPORT_REMAINDER_GET(
            "/api/v1/export-analytics/export-remainder",
            Method.GET,
            null,
            "Export storage remainders XLSX",
            null,
            null,
            null
    ),

    RESOURCE_OPERATION_HISTORY_GET(
            "/api/v1/statistics/resource-operation-history",
            Method.GET,
            null,
            "Get resource operation history",
            null,
            null,
            null
    ),

    // ========================================
    // RESOURCE VIEWER ENDPOINTS
    // ========================================
    RESOURCE_VIEWER_RELOCATIONS_SUM(
            "/api/v1/resources-viewer/relocations/sum",
            Method.GET,
            "schemas/resource-viewer/resource-relocation-sum-list-schema.json",
            "Get relocated resources sum (aggregated by resource, sorted by name)",
            null,
            new TypeReference<List<ResourceRelocationSumViewerResponse>>() {},
            null
    ),

    // ========================================
    // RELOCATION ENDPOINTS
    // ========================================
    RELOCATION_GET_PAGE(
            "/api/v1/relocations",
            Method.GET,
            "schemas/relocations/relocation-paged-list-schema.json",
            "Get relocations page",
            null,
            new TypeReference<PagedRelocationResponse>() {},
            null
    ),

    /** @deprecated use {@link #RELOCATION_GET_PAGE} */
    @Deprecated
    RELOCATION_GET_ALL_BY_STORE_ID(
            "/api/v1/relocations",
            Method.GET,
            "schemas/relocations/relocation-paged-list-schema.json",
            "Get relocations page (legacy alias)",
            null,
            new TypeReference<PagedRelocationResponse>() {},
            null
    ),

    RELOCATION_GET_CREATION_OPTIONS(
            "/api/v1/relocations/creation-options?storageId={id}",
            Method.GET,
            "schemas/relocations/relocation-creation-options-schema.json",
            "Get relocation creation options",
            null,
            new TypeReference<RelocationCreationOptionsResponse>() {},
            null
    ),

    RELOCATION_GET_EXPORT(
            "/api/v1/relocations/export",
            Method.GET,
            null,
            "Export relocations to Excel",
            null,
            new TypeReference<byte[]>() {},
            null
    ),

    RELOCATION_POST_SEND(
            "/api/v1/relocations/send?generateInvoice=false",
            Method.POST,
            "schemas/relocations/relocation-response-schema.json",
            "Send resources (storage → storage/UNIT)",
            new TypeReference<RelocationOutputRequest>() {},
            new TypeReference<RelocationResponse>() {},
            "CREATE_RELOCATIONS"
    ),

    RELOCATION_POST_SEND_WITH_INVOICE(
            "/api/v1/relocations/send?generateInvoice=true",
            Method.POST,
            "schemas/relocations/relocation-response-schema.json",
            "Send resources with async invoice generation",
            new TypeReference<RelocationOutputRequest>() {},
            new TypeReference<RelocationResponse>() {},
            "CREATE_RELOCATIONS"
    ),

    INVOICE_GET_EXISTS(
            "/api/v1/invoice/{id}/exists",
            Method.GET,
            null,
            "Check whether invoice file exists for relocation",
            null,
            new TypeReference<java.util.Map<String, Boolean>>() {},
            null
    ),

    INVOICE_GET_DOWNLOAD(
            "/api/v1/invoice/{id}",
            Method.GET,
            null,
            "Download generated invoice file",
            null,
            new TypeReference<byte[]>() {},
            null
    ),

    /** @deprecated use {@link #RELOCATION_POST_SEND} */
    @Deprecated
    RELOCATION_POST_CREATE_BY_STORE_ID(
            "/api/v1/relocations/send?generateInvoice=false",
            Method.POST,
            "schemas/relocations/relocation-response-schema.json",
            "Send resources (legacy alias)",
            new TypeReference<RelocationOutputRequest>() {},
            new TypeReference<RelocationResponse>() {},
            "CREATE_RELOCATIONS"
    ),

    RELOCATION_POST_RECEIVE(
            "/api/v1/relocations/receive",
            Method.POST,
            "schemas/relocations/relocation-response-schema.json",
            "Receive resources (SUPPLIER → storage, AUTO_FINISHED)",
            new TypeReference<RelocationInputRequest>() {},
            new TypeReference<RelocationResponse>() {},
            null
    ),

    RELOCATION_PUT_RESOLVE(
            "/api/v1/relocations/{id}/resolve?storageId={storageId}",
            Method.PUT,
            "schemas/relocations/relocation-response-schema.json",
            "Resolve relocation state",
            new TypeReference<RelocationUpdateRequest>() {},
            new TypeReference<RelocationResponse>() {},
            null
    ),

    RELOCATION_PUT_UPDATE_SEND(
            "/api/v1/relocations/{id}/send?storageId={storageId}",
            Method.PUT,
            "schemas/relocations/relocation-response-schema.json",
            "Edit outbound relocation (AUTO_FINISHED)",
            new TypeReference<RelocationOutputEditRequest>() {},
            new TypeReference<RelocationResponse>() {},
            null
    ),

    RELOCATION_PUT_UPDATE_RECEIVE(
            "/api/v1/relocations/{id}/receive?storageId={storageId}",
            Method.PUT,
            "schemas/relocations/relocation-response-schema.json",
            "Edit inbound relocation (AUTO_FINISHED)",
            new TypeReference<RelocationInputEditRequest>() {},
            new TypeReference<RelocationResponse>() {},
            null
    ),

    RELOCATION_DELETE(
            "/api/v1/relocations/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete AUTO_FINISHED relocation",
            null,
            new TypeReference<Void>() {},
            null
    ),

    // ========================================
    // EQUIPMENT RELOCATION ENDPOINTS
    // ========================================
    EQUIPMENT_RELOCATION_POST_SEND(
            "/api/v1/relocations/equipment/send",
            Method.POST,
            "schemas/relocations/relocation-response-schema.json",
            "Send equipment relocation",
            new TypeReference<EquipmentRelocationSendRequest>() {},
            new TypeReference<RelocationResponse>() {},
            null
    ),

    EQUIPMENT_RELOCATION_PUT_UPDATE_SEND(
            "/api/v1/relocations/equipment/{id}/send?storageId={storageId}",
            Method.PUT,
            "schemas/relocations/relocation-response-schema.json",
            "Edit equipment outbound relocation",
            new TypeReference<EquipmentRelocationSendEditRequest>() {},
            new TypeReference<RelocationResponse>() {},
            null
    ),

    EQUIPMENT_RELOCATION_PUT_UPDATE_RECEIVE(
            "/api/v1/relocations/equipment/{id}/receive?storageId={storageId}",
            Method.PUT,
            "schemas/relocations/relocation-response-schema.json",
            "Edit equipment inbound relocation",
            new TypeReference<EquipmentRelocationReceiveEditRequest>() {},
            new TypeReference<RelocationResponse>() {},
            null
    ),

    // ========================================
    // EQUIPMENT ENDPOINTS
    // ========================================
    EQUIPMENT_GET_PAGE(
            "/api/v1/equipment",
            Method.GET,
            "schemas/equipment/equipment-page-schema.json",
            "Get equipment page",
            null,
            new TypeReference<PagedEquipmentResponse>() {},
            null
    ),

    EQUIPMENT_CATEGORY_GET_ALL(
            "/api/v1/equipment-categories",
            Method.GET,
            "schemas/equipment/equipment-category-list-schema.json",
            "Get equipment categories",
            null,
            new TypeReference<List<EquipmentCategoryResponse>>() {},
            null
    ),

    EQUIPMENT_POST_CREATE(
            "/api/v1/equipment",
            Method.POST,
            "schemas/equipment/equipment-response-schema.json",
            "Create equipment",
            new TypeReference<EquipmentRequest>() {},
            new TypeReference<EquipmentResponse>() {},
            null
    ),

    EQUIPMENT_PUT_STATUS(
            "/api/v1/equipment/{id}/status",
            Method.PUT,
            "schemas/equipment/equipment-response-schema.json",
            "Change equipment status",
            new TypeReference<EquipmentStatusUpdateRequest>() {},
            new TypeReference<EquipmentResponse>() {},
            null
    ),

    EQUIPMENT_GET_GROUPED(
            "/api/v1/equipment/grouped",
            Method.GET,
            "schemas/equipment/equipment-grouped-page-schema.json",
            "Get grouped equipment page",
            null,
            new TypeReference<PagedEquipmentGroupResponse>() {},
            null
    ),

    EQUIPMENT_POST_ASSIGNMENT(
            "/api/v1/equipment/{id}/assignments",
            Method.POST,
            "schemas/equipment/equipment-response-schema.json",
            "Assign equipment to employee",
            new TypeReference<EquipmentAssignmentRequest>() {},
            new TypeReference<EquipmentResponse>() {},
            null
    ),

    // ========================================
    // EMPLOYEE ENDPOINTS
    // ========================================
    EMPLOYEE_GET_PAGE(
            "/api/v1/employees",
            Method.GET,
            "schemas/employee/employee-page-schema.json",
            "Get employees page",
            null,
            new TypeReference<PagedEmployeeResponse>() {},
            null
    ),

    EMPLOYEE_POST_CREATE(
            "/api/v1/employees",
            Method.POST,
            "schemas/employee/employee-response-schema.json",
            "Create employee",
            new TypeReference<EmployeeRequest>() {},
            new TypeReference<EmployeeResponse>() {},
            "CREATE"
    ),

    // ========================================
    // DEFECT ENDPOINTS ("Брак")
    // ========================================
    DEFECT_GET_PAGE(
            "/api/v1/defects",
            Method.GET,
            "schemas/defect/defect-paged-list-schema.json",
            "Get defects page (filtered by storageIds, resourceSearch, dates, types)",
            null,
            null,
            null
    ),

    DEFECT_GET_BY_ID(
            "/api/v1/defects/{id}?storageId={storageId}",
            Method.GET,
            "schemas/defect/defect-response-schema.json",
            "Get defect by id",
            null,
            new TypeReference<DefectResponse>() {},
            null
    ),

    DEFECT_GET_LINKED_PRODUCTION_IDS(
            "/api/v1/defects/linked-production-ids",
            Method.GET,
            null,
            "Get production process ids available for production defects",
            null,
            new TypeReference<List<Long>>() {},
            null
    ),

    DEFECT_GET_LINKED_RELOCATION_IDS(
            "/api/v1/defects/linked-relocation-ids",
            Method.GET,
            null,
            "Get relocation ids available for relocation defects",
            null,
            new TypeReference<List<Long>>() {},
            null
    ),

    DEFECT_POST_CREATE(
            "/api/v1/defects",
            Method.POST,
            "schemas/defect/defect-response-schema.json",
            "Create defect",
            new TypeReference<DefectRequest>() {},
            new TypeReference<DefectResponse>() {},
            "CREATE_DEFECT"
    ),

    DEFECT_PUT_UPDATE(
            "/api/v1/defects/{id}",
            Method.PUT,
            "schemas/defect/defect-response-schema.json",
            "Update defect",
            new TypeReference<DefectRequest>() {},
            new TypeReference<DefectResponse>() {},
            "UPDATE_DEFECT"
    ),

    DEFECT_DELETE(
            "/api/v1/defects/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete defect (restores remaining amount to stock)",
            null,
            new TypeReference<Void>() {},
            null
    ),

    DEFECT_POST_WRITE_OFF(
            "/api/v1/defects/write-off",
            Method.POST,
            "schemas/defect/defect-write-off-response-schema.json",
            "Write off defect ('Списати')",
            new TypeReference<DefectWriteOffRequest>() {},
            new TypeReference<DefectWriteOffResponse>() {},
            "CREATE_DEFECT_WRITE_OFF"
    ),

    DEFECT_GET_WRITE_OFFS(
            "/api/v1/defects/write-off/{defectId}?storageId={storageId}",
            Method.GET,
            null,
            "Get write-offs for a defect",
            null,
            new TypeReference<List<DefectWriteOffResponse>>() {},
            null
    ),

    DEFECT_DELETE_WRITE_OFF(
            "/api/v1/defects/write-off/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Cancel (delete) a defect write-off",
            null,
            new TypeReference<Void>() {},
            null
    ),

    // ========================================
    // INTERNAL API (whatsapp-bot, delivery-bot)
    // ========================================

    INTERNAL_STORAGE_GET_ALL(
            "/api/v1/internal/storages",
            Method.GET,
            null,
            "Internal storages export for whatsapp-bot inventory sync",
            null,
            new TypeReference<List<StorageInternalResponse>>() {},
            null
    ),

    INTERNAL_RELOCATION_GET_ALL(
            "/api/v1/internal/relocations",
            Method.GET,
            null,
            "Internal relocations export for delivery-bot sync",
            null,
            new TypeReference<List<RelocationInternalResponse>>() {},
            null
    );


    // ========================================
    // Fields
    // ========================================

    private final String pathTemplate;
    private final Method httpMethod;
    private final String schemaPath;
    private final String description;
    private final TypeReference<?> requestType;   // ✅ Full generic support
    private final TypeReference<?> responseType;  // ✅ Full generic support
    private final String operation;

    ApiEndpointDefinition(String pathTemplate, Method httpMethod, String schemaPath,
                          String description, TypeReference<?> requestType,
                          TypeReference<?> responseType, String operation) {
        this.pathTemplate = pathTemplate;
        this.httpMethod = httpMethod;
        this.schemaPath = schemaPath;
        this.description = description;
        this.requestType = requestType;
        this.responseType = responseType;
        this.operation = operation;
    }

    // ============================================
    // 🔧 Path Building Methods
    // ============================================

    public String getPathTemplate() {
        return pathTemplate;
    }

    /**
     * ✅ Build path with dynamic parameters
     */
    public String getPath(Object... params) {
        if (params == null || params.length == 0) {
            return pathTemplate;
        }

        String result = pathTemplate;
        int paramIndex = 0;

        while (result.contains("{") && paramIndex < params.length) {
            int start = result.indexOf("{");
            int end = result.indexOf("}", start);

            if (start != -1 && end != -1) {
                String placeholder = result.substring(start, end + 1);
                result = result.replace(placeholder, String.valueOf(params[paramIndex]));
                paramIndex++;
            } else {
                break;
            }
        }

        return result;
    }

    public boolean hasPathVariables() {
        return pathTemplate.contains("{");
    }

    public int getPathVariablesCount() {
        int count = 0;
        String temp = pathTemplate;
        while (temp.contains("{")) {
            count++;
            temp = temp.substring(temp.indexOf("}") + 1);
        }
        return count;
    }

    // ============================================
    // 🎯 Type-related Methods
    // ============================================

    /**
     * Get request class (raw type)
     */
    public Class<?> getRequestClass() {
        return requestType != null ? requestType.getRawType() : null;
    }

    /**
     * Get response class (raw type)
     */
    public Class<?> getResponseClass() {
        return responseType != null ? responseType.getRawType() : null;
    }

    /**
     * Get full request Type (with generics)
     */
    public Type getRequestFullType() {
        return requestType != null ? requestType.getType() : null;
    }

    /**
     * Get full response Type (with generics)
     */
    public Type getResponseFullType() {
        return responseType != null ? responseType.getType() : null;
    }

    /**
     * ✅ Check if response is a collection
     */
    public boolean isCollectionResponse() {
        return responseType != null && responseType.isCollection();
    }

    /**
     * ✅ Get element type for collection responses
     * <p>
     * For List<ResourceResponse> returns ResourceResponse.class
     */
    public Class<?> getResponseElementType() {
        if (responseType != null) {
            return responseType.getElementType();
        }
        return null;
    }

    /**
     * Get response type description (human-readable)
     */
    public String getResponseTypeDescription() {
        return responseType != null ? responseType.getTypeDescription() : "void";
    }

    /**
     * Get request type description (human-readable)
     */
    public String getRequestTypeDescription() {
        return requestType != null ? requestType.getTypeDescription() : "none";
    }

    // ============================================
    // 🔍 Search Methods
    // ============================================

    public static ApiEndpointDefinition findByName(String name) {
        try {
            return ApiEndpointDefinition.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown endpoint: " + name + ". Available: " +
                            Arrays.toString(ApiEndpointDefinition.values())
            );
        }
    }

    public static Optional<ApiEndpointDefinition> findByPathAndMethod(String pathTemplate, Method method) {
        long count = Arrays.stream(values())
                .filter(e -> e.getPathTemplate().equals(pathTemplate) && e.getHttpMethod() == method)
                .count();

        if (count > 1) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(e -> e.getPathTemplate().equals(pathTemplate) && e.getHttpMethod() == method)
                .findFirst();
    }

    public static ApiEndpointDefinition findByPathMethodAndOperation(
            String pathTemplate, Method method, String operation) {
        return Arrays.stream(values())
                .filter(e -> e.getPathTemplate().equals(pathTemplate)
                        && e.getHttpMethod() == method
                        && operation.equals(e.getOperation()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("No endpoint found for %s %s [%s]", method, pathTemplate, operation)
                ));
    }

    /**
     * ✅ Find endpoint by request class
     */
    public static Optional<ApiEndpointDefinition> findByRequestClass(Class<?> requestClass) {
        return Arrays.stream(values())
                .filter(e -> e.requestType != null &&
                        requestClass.equals(e.requestType.getRawType()))
                .findFirst();
    }

    // ============================================
    // 🎯 Utility Methods
    // ============================================

    public boolean requiresBody() {
        return requestType != null &&
                requestType.getRawType() != null &&
                requestType.getRawType() != Void.class;
    }

    public boolean hasSchema() {
        return schemaPath != null;
    }

    public String getUniqueKey() {
        String key = httpMethod.name() + ":" + pathTemplate;
        if (operation != null) {
            key += ":" + operation;
        }
        return key;
    }

    @Override
    public String toString() {
        return String.format("%s %s [%s] → %s",
                httpMethod,
                pathTemplate,
                operation != null ? operation : "default",
                getResponseTypeDescription()
        );
    }
}