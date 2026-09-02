package com.erp.api.endpoints;

import com.erp.api.types.TypeReference;
import com.erp.models.request.*;
import com.erp.models.response.*;
import io.restassured.http.Method;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    // ORDER ENDPOINTS
    // ========================================

    ORDER_GET_PAGE(
            "/api/v1/orders",
            Method.GET,
            "schemas/orders/order-paged-list-schema.json",
            "Get orders page",
            null,
            new TypeReference<PagedOrderResponse>() {},
            null
    ),

    ORDER_GET_BY_ID(
            "/api/v1/orders/{id}",
            Method.GET,
            "schemas/orders/order-response-schema.json",
            "Get order by id",
            null,
            new TypeReference<OrderResponse>() {},
            null
    ),

    ORDER_POST_CREATE(
            "/api/v1/orders",
            Method.POST,
            "schemas/orders/order-response-schema.json",
            "Create order",
            new TypeReference<OrderRequest>() {},
            new TypeReference<OrderResponse>() {},
            "CREATE_ORDER"
    ),

    ORDER_PUT_UPDATE(
            "/api/v1/orders/{id}",
            Method.PUT,
            "schemas/orders/order-response-schema.json",
            "Update order",
            new TypeReference<OrderRequest>() {},
            new TypeReference<OrderResponse>() {},
            "UPDATE_ORDER"
    ),

    ORDER_PUT_CANCEL(
            "/api/v1/orders/{id}/cancel?storageId={storageId}",
            Method.PUT,
            "schemas/orders/order-response-schema.json",
            "Cancel order",
            null,
            new TypeReference<OrderResponse>() {},
            null
    ),

    ORDER_PUT_TAKE_TO_WORK(
            "/api/v1/orders/{id}/take-to-work?storageId={storageId}",
            Method.PUT,
            "schemas/orders/order-response-schema.json",
            "Take order to work",
            null,
            new TypeReference<OrderResponse>() {},
            null
    ),

    ORDER_PUT_MARK_DONE(
            "/api/v1/orders/{id}/mark-done?storageId={storageId}",
            Method.PUT,
            "schemas/orders/order-response-schema.json",
            "Mark order done",
            null,
            new TypeReference<OrderResponse>() {},
            null
    ),

    ORDER_GET_AVAILABILITY(
            "/api/v1/orders/{id}/availability?storageId={storageId}",
            Method.GET,
            null,
            "Get order resource availability",
            null,
            new TypeReference<List<OrderAvailabilityResponse>>() {},
            null
    ),

    ORDER_GET_GATHERING_LOCATIONS(
            "/api/v1/orders/{id}/gathering-locations?storageId={storageId}",
            Method.GET,
            null,
            "Get gathering location candidates",
            null,
            new TypeReference<List<SimpleEntityResponse>>() {},
            null
    ),

    ORDER_PUT_GATHERING_STORAGE(
            "/api/v1/orders/{id}/gathering-storage?storageId={storageId}",
            Method.PUT,
            "schemas/orders/order-response-schema.json",
            "Set order gathering storage",
            new TypeReference<GatheringStorageRequest>() {},
            new TypeReference<OrderResponse>() {},
            null
    ),

    ORDER_GET_BOOKINGS(
            "/api/v1/orders/{id}/bookings",
            Method.GET,
            "schemas/orders/booking-response-list-schema.json",
            "Get order bookings",
            null,
            new TypeReference<List<BookingResponse>>() {},
            null
    ),

    ORDER_POST_BOOKING(
            "/api/v1/orders/{id}/bookings?storageId={storageId}",
            Method.POST,
            "schemas/orders/booking-response-schema.json",
            "Book resource for order",
            new TypeReference<BookingRequest>() {},
            new TypeReference<BookingResponse>() {},
            "CREATE_ORDER_BOOKING"
    ),

    ORDER_DELETE_BOOKING(
            "/api/v1/orders/{id}/bookings/{bookingId}?storageId={storageId}",
            Method.DELETE,
            null,
            "Release order booking",
            null,
            new TypeReference<Void>() {},
            null
    ),

    ORDER_PUT_BOOKING_PREPARED(
            "/api/v1/orders/{id}/bookings/{bookingId}/prepared",
            Method.PUT,
            "schemas/orders/booking-response-schema.json",
            "Set booking prepared flag",
            new TypeReference<PreparedRequest>() {},
            new TypeReference<BookingResponse>() {},
            null
    ),

    ORDER_PUT_BOOKINGS_PREPARED(
            "/api/v1/orders/{id}/bookings/prepared",
            Method.PUT,
            "schemas/orders/booking-response-list-schema.json",
            "Set prepared for all order bookings",
            new TypeReference<PreparedRequest>() {},
            new TypeReference<List<BookingResponse>>() {},
            null
    ),

    ORDER_GET_COMMENTS(
            "/api/v1/orders/{id}/comments",
            Method.GET,
            null,
            "Get order comments",
            null,
            new TypeReference<List<OrderCommentResponse>>() {},
            null
    ),

    ORDER_POST_COMMENT(
            "/api/v1/orders/{id}/comments",
            Method.POST,
            null,
            "Add order comment",
            new TypeReference<OrderCommentRequest>() {},
            new TypeReference<OrderCommentResponse>() {},
            "CREATE_ORDER_COMMENT"
    ),

    // ========================================
    // NOTIFICATION ENDPOINTS
    // ========================================

    NOTIFICATION_GET_PAGE(
            "/api/v1/notifications",
            Method.GET,
            null,
            "Get notifications page",
            null,
            new TypeReference<PagedNotificationResponse>() {},
            null
    ),

    NOTIFICATION_TEMPLATE_GET_ALL(
            "/api/v1/notifications/templates",
            Method.GET,
            null,
            "Get all notification templates",
            null,
            new TypeReference<List<NotificationTemplateResponse>>() {},
            null
    ),

    NOTIFICATION_RECIPIENT_GET_ALL(
            "/api/v1/notifications/recipients",
            Method.GET,
            null,
            "Get all notification recipients",
            null,
            new TypeReference<List<NotificationRecipientResponse>>() {},
            null
    ),

    NOTIFICATION_RECIPIENT_GET_BY_ID(
            "/api/v1/notifications/recipients/{id}",
            Method.GET,
            null,
            "Get notification recipient by id",
            null,
            new TypeReference<NotificationRecipientResponse>() {},
            null
    ),

    NOTIFICATION_RECIPIENT_CREATE(
            "/api/v1/notifications/recipients",
            Method.POST,
            null,
            "Create notification recipient",
            new TypeReference<NotificationRecipientRequest>() {},
            new TypeReference<NotificationRecipientResponse>() {},
            "CREATE_NOTIFICATION_RECIPIENT"
    ),

    NOTIFICATION_RECIPIENT_UPDATE(
            "/api/v1/notifications/recipients/{id}",
            Method.PUT,
            null,
            "Update notification recipient",
            new TypeReference<NotificationRecipientRequest>() {},
            new TypeReference<NotificationRecipientResponse>() {},
            "UPDATE_NOTIFICATION_RECIPIENT"
    ),

    NOTIFICATION_SUBSCRIPTION_GET_PAGE(
            "/api/v1/notifications/subscriptions",
            Method.GET,
            null,
            "Get notification subscriptions page",
            null,
            new TypeReference<PagedNotificationSubscriptionResponse>() {},
            null
    ),

    NOTIFICATION_SUBSCRIPTION_SAVE(
            "/api/v1/notifications/subscriptions",
            Method.POST,
            null,
            "Save notification subscription",
            new TypeReference<NotificationSubscriptionRequest>() {},
            new TypeReference<NotificationSubscriptionResponse>() {},
            "SAVE_NOTIFICATION_SUBSCRIPTION"
    ),

    NOTIFICATION_SUBSCRIPTION_DELETE(
            "/api/v1/notifications/subscriptions",
            Method.DELETE,
            null,
            "Remove notification subscription",
            new TypeReference<RemoveNotificationSubscriptionRequest>() {},
            new TypeReference<Void>() {},
            "DELETE_NOTIFICATION_SUBSCRIPTION"
    ),

    NOTIFICATION_BROWSER_GET(
            "/api/v1/notifications/browser-notifications",
            Method.GET,
            null,
            "Get pending browser push notifications",
            null,
            new TypeReference<List<PushNotificationResponse>>() {},
            null
    ),

    NOTIFICATION_MY_GET(
            "/api/v1/notifications/my",
            Method.GET,
            null,
            "Get current user notification configuration",
            null,
            new TypeReference<UserNotificationConfigResponse>() {},
            null
    ),

    NOTIFICATION_MY_SUBSCRIBE(
            "/api/v1/notifications/my",
            Method.POST,
            null,
            "Subscribe current user to a notification template",
            new TypeReference<NotificationSubscriptionRequest>() {},
            new TypeReference<Void>() {},
            "SUBSCRIBE_MY_NOTIFICATION"
    ),

    NOTIFICATION_MY_UNSUBSCRIBE(
            "/api/v1/notifications/my",
            Method.DELETE,
            null,
            "Unsubscribe current user from a notification template",
            new TypeReference<RemoveNotificationSubscriptionRequest>() {},
            new TypeReference<Void>() {},
            "UNSUBSCRIBE_MY_NOTIFICATION"
    ),

    // ========================================
    // RESOURCE USER BUNDLES
    // ========================================

    RESOURCE_USER_BUNDLES_GET(
            "/api/v1/resources/user-bundles?storageId={storageId}",
            Method.GET,
            null,
            "Get user resource bundles",
            null,
            new TypeReference<List<ResourceBundleResponse>>() {},
            null
    ),

    RESOURCE_USER_BUNDLES_POST(
            "/api/v1/resources/user-bundles",
            Method.POST,
            null,
            "Save user resource bundle",
            new TypeReference<Map<String, Object>>() {},
            new TypeReference<Void>() {},
            "SAVE_RESOURCE_USER_BUNDLE"
    ),

    RESOURCE_USER_BUNDLES_DELETE(
            "/api/v1/resources/user-bundles",
            Method.DELETE,
            null,
            "Delete user resource bundle",
            new TypeReference<Map<String, Object>>() {},
            new TypeReference<Void>() {},
            "DELETE_RESOURCE_USER_BUNDLE"
    ),

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

    RESOURCE_UPDATE_NOTES(
            "/api/v1/resources/{id}/notes",
            Method.PATCH,
            "schemas/resource-response-schema.json",
            "Update resource notes (extracts #tags into resource.tags)",
            new TypeReference<UpdateNotesRequest>() {},
            new TypeReference<ResourceResponse>() {},
            "PATCH_RESOURCE_NOTES"
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

    APP_CONFIG_FAVOURITE_RESOURCES_GET(
            "/api/v1/app-config/favourite-resources",
            Method.GET,
            null,
            "Get favourite resources for current user",
            null,
            new TypeReference<List<FavouriteResourceResponse>>() {},
            null
    ),

    APP_CONFIG_FAVOURITE_RESOURCES_PUT(
            "/api/v1/app-config/favourite-resources",
            Method.PUT,
            null,
            "Save favourite resources for current user",
            new TypeReference<SaveFavouriteResourcesRequest>() {},
            new TypeReference<List<FavouriteResourceResponse>>() {},
            null
    ),

    APP_CONFIG_PRODUCTION_PROCESS_TAGS_GET(
            "/api/v1/app-config/production-process-tags?storageId={storageId}",
            Method.GET,
            null,
            "Get production process tag catalog for storage",
            null,
            new TypeReference<List<String>>() {},
            null
    ),

    APP_CONFIG_TECHNOLOGICAL_MAP_TAGS_GET(
            "/api/v1/app-config/technological-map-tags?storageId={storageId}",
            Method.GET,
            null,
            "Get technological map tag catalog for storage",
            null,
            new TypeReference<List<String>>() {},
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

    RESOURCE_GET_AVAILABLE_CATEGORIES(
            "/api/v1/resources/available-categories",
            Method.GET,
            null,
            "Get resource categories available for a storage",
            null,
            new TypeReference<List<ResourceCategoryResponse>>() {},
            null
    ),

    /**
     * Catalog for «Керувати обраними» on Plan Execution: PRODUCTION tech-map outputs
     * for {@code storageId}, filtered by resource {@code isActive} (UI: Активні / Архівні).
     */
    RESOURCE_WITH_TECHNOLOGICAL_MAP(
            "/api/v1/resources/with-technological-map",
            Method.GET,
            null,
            "Get production tech-map output resources for storage",
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

    RESOURCE_PRICE_PUT_UPDATE(
            "/api/v1/resources-price",
            Method.PUT,
            null,
            "Update resource price",
            new TypeReference<ResourcePriceUpdateRequest>() {},
            new TypeReference<ResourcePriceResponse>() {},
            "UPDATE_RESOURCE_PRICE"
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

    ALERT_GET_BY_ID(
            "/api/v1/alerts/{id}",
            Method.GET,
            null,
            "Get storage alert by id",
            null,
            new TypeReference<StorageAlertResponse>() {},
            null
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

    TECH_MAP_GET_BY_ID(
            "/api/v1/technological-maps/{id}?storageId={storageId}",
            Method.GET,
            "schemas/technological-maps/technological-map-response-schema.json",
            "Get technological map by id and storage",
            null,
            new TypeReference<TechnologicalMapResponse>() {},
            null
    ),

    TECH_MAP_GET_VERSIONS(
            "/api/v1/technological-maps/versions/{groupId}?storageId={storageId}",
            Method.GET,
            "schemas/technological-maps/technological-map-response-list-schema.json",
            "Get all versions of a technological map group",
            null,
            new TypeReference<List<TechnologicalMapResponse>>() {},
            null
    ),

    TECH_MAP_PATCH_NOTES(
            "/api/v1/technological-maps/{id}/notes?storageId={storageId}",
            Method.PATCH,
            "schemas/technological-maps/technological-map-response-schema.json",
            "Update technological map notes for storage link",
            new TypeReference<UpdateNotesRequest>() {},
            new TypeReference<TechnologicalMapResponse>() {},
            "PATCH_TECH_MAP_NOTES"
    ),

    TECH_MAP_TAG_STATISTICS_GET(
            "/api/v1/technological-maps/tag-statistics",
            Method.GET,
            null,
            "Get technological map tag statistics",
            null,
            new TypeReference<List<ProductionProcessTagStatisticResponse>>() {},
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

    /**
     * tk-ui send form uses {@code GET /storages/{selectedStorageId}/names} so REGIONS
     * scoping follows the selected workspace, not JWT isAdmin / unscoped /names.
     */
    STORAGE_GET_NAMES_FOR_STORAGE(
            "/api/v1/storages/{storageId}/names",
            Method.GET,
            "schemas/storages/storage-names-list-schema.json",
            "Get storage names in selected-storage visibility context",
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

    FLY_POINT_GET_STOCKS(
            "/api/v1/fly-points/stocks",
            Method.GET,
            "schemas/fly-points/unit-fly-point-resource-stock-list-schema.json",
            "Get fly-point dashboard resource stocks by parent unit",
            null,
            new TypeReference<List<UnitFlyPointResourceStockResponse>>() {},
            null
    ),

    FLY_POINT_GET_SHORT_STATS(
            "/api/v1/fly-points/short-stats",
            Method.GET,
            "schemas/fly-points/unit-short-stats-list-schema.json",
            "Get fly-point dashboard ammunition short stats by parent unit",
            null,
            new TypeReference<List<UnitShortStatsResponse>>() {},
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

    PRODUCTION_PATCH_NOTES(
            "/api/v1/productions/{id}/notes?storageId={storageId}",
            Method.PATCH,
            "schemas/productions/manufacturing-item-response-schema.json",
            "Update production notes",
            new TypeReference<UpdateNotesRequest>() {},
            new TypeReference<ManufacturingItemResponse>() {},
            "PATCH_PRODUCTION_NOTES"
    ),

    PRODUCTION_TAG_STATISTICS_GET(
            "/api/v1/productions/tag-statistics",
            Method.GET,
            null,
            "Get production process tag statistics",
            null,
            new TypeReference<List<ProductionProcessTagStatisticResponse>>() {},
            null
    ),

    PRODUCTION_DAILY_REPORT_GET(
            "/api/v1/productions/daily-report",
            Method.GET,
            null,
            "Get production daily report for parent storage",
            null,
            null,
            null
    ),

    ASSEMBLY_READINESS_GET_BY_STORAGE(
            "/api/v1/assembly-readiness/{storageId}",
            Method.GET,
            "schemas/assembly-readiness/assembly-readiness-response-list-schema.json",
            "Get assembly readiness rows for storage (ready-to-kit finished products)",
            null,
            new TypeReference<List<AssemblyReadinessResponse>>() {},
            null
    ),

    // ========================================
    // DISASSEMBLE ENDPOINTS
    // ========================================

    DISASSEMBLE_GET_PAGE(
            "/api/v1/disassemble",
            Method.GET,
            null,
            "Get disassemble journal page",
            null,
            null,
            null
    ),

    DISASSEMBLE_POST_CREATE(
            "/api/v1/disassemble/{storageId}",
            Method.POST,
            null,
            "Create disassemble",
            new TypeReference<DisassembleListRequest>() {},
            new TypeReference<List<DisassembleItemResponse>>() {},
            "CREATE_DISASSEMBLE"
    ),

    DISASSEMBLE_GET_BY_ID(
            "/api/v1/disassemble/{id}?storageId={storageId}",
            Method.GET,
            null,
            "Get disassemble by id",
            null,
            new TypeReference<DisassembleItemResponse>() {},
            null
    ),

    DISASSEMBLE_PUT_UPDATE(
            "/api/v1/disassemble/{id}/{storageId}",
            Method.PUT,
            null,
            "Update disassemble",
            new TypeReference<DisassembleListRequest>() {},
            new TypeReference<DisassembleItemResponse>() {},
            "UPDATE_DISASSEMBLE"
    ),

    DISASSEMBLE_DELETE(
            "/api/v1/disassemble/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete disassemble",
            null,
            null,
            "DELETE_DISASSEMBLE"
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
    // PROJECT PRODUCTION ENDPOINTS
    // ========================================

    PROJECT_PRODUCTION_GET_PAGE(
            "/api/v1/project-production?storageIds={storageId}&size=200",
            Method.GET,
            "schemas/project-production/project-production-response-list-schema.json",
            "Get project production page filtered by storage",
            null,
            new TypeReference<List<ProjectProductionResponse>>() {},
            null
    ),

    PROJECT_PRODUCTION_GET_BY_ID(
            "/api/v1/project-production/{id}?storageId={storageId}",
            Method.GET,
            "schemas/project-production/project-production-response-schema.json",
            "Get project production by id",
            null,
            new TypeReference<ProjectProductionResponse>() {},
            null
    ),

    /** Multipart POST — part {@code request} (JSON). Use {@code ApiExecutor#executeProjectProductionCreate}. */
    PROJECT_PRODUCTION_POST_CREATE(
            "/api/v1/project-production",
            Method.POST,
            "schemas/project-production/project-production-response-schema.json",
            "Create project production",
            new TypeReference<ProjectProductionRequest>() {},
            new TypeReference<ProjectProductionResponse>() {},
            "CREATE_PROJECT_PRODUCTION"
    ),

    PROJECT_PRODUCTION_PUT_UPDATE(
            "/api/v1/project-production/{id}",
            Method.PUT,
            "schemas/project-production/project-production-response-schema.json",
            "Update project production",
            new TypeReference<ProjectProductionRequest>() {},
            new TypeReference<ProjectProductionResponse>() {},
            "UPDATE_PROJECT_PRODUCTION"
    ),

    /** Optional body: {@code List<ResourceToRollbackRequest>} — resources to return to stock. */
    PROJECT_PRODUCTION_DELETE(
            "/api/v1/project-production/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete project production",
            new TypeReference<List<ResourceToRollbackRequest>>() {},
            null,
            "DELETE_PROJECT_PRODUCTION"
    ),

    PROJECT_PRODUCTION_STAGE_POST_ADD(
            "/api/v1/project-production/{productionId}/stage?storageId={storageId}",
            Method.POST,
            "schemas/project-production/project-production-stage-response-schema.json",
            "Add stage to project production",
            new TypeReference<ProjectProductionStageRequest>() {},
            new TypeReference<ProjectProductionStageResponse>() {},
            "ADD_PROJECT_PRODUCTION_STAGE"
    ),

    PROJECT_PRODUCTION_STAGE_PUT_UPDATE(
            "/api/v1/project-production/{productionId}/stage/{stageId}?storageId={storageId}",
            Method.PUT,
            "schemas/project-production/project-production-stage-response-schema.json",
            "Update project production stage",
            new TypeReference<ProjectProductionStageRequest>() {},
            new TypeReference<ProjectProductionStageResponse>() {},
            "UPDATE_PROJECT_PRODUCTION_STAGE"
    ),

    PROJECT_PRODUCTION_STAGE_DELETE(
            "/api/v1/project-production/{productionId}/stage/{stageId}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete project production stage",
            null,
            null,
            "DELETE_PROJECT_PRODUCTION_STAGE"
    ),

    PROJECT_PRODUCTION_FINISH(
            "/api/v1/project-production/{productionId}/finish-project?storageId={storageId}",
            Method.PUT,
            null,
            "Finish project production",
            null,
            null,
            "FINISH_PROJECT_PRODUCTION"
    ),

    PROJECT_PRODUCTION_CANCEL_FINISHED(
            "/api/v1/project-production/{productionId}/cancel-finished-project?storageId={storageId}",
            Method.PUT,
            null,
            "Cancel finished project production",
            null,
            null,
            "CANCEL_FINISHED_PROJECT_PRODUCTION"
    ),

    PROJECT_PRODUCTION_RESOURCES_GET(
            "/api/v1/project-production/{productionId}/resources?storageId={storageId}",
            Method.GET,
            "schemas/project-production/project-production-resources-response-schema.json",
            "Get aggregated stage resources for project production",
            null,
            new TypeReference<ProjectProductionResourcesResponse>() {},
            null
    ),

    PROJECT_PRODUCTION_CREATE_TEMPLATE(
            "/api/v1/project-production/{productionId}/create-template?storageId={storageId}&name={name}",
            Method.POST,
            "schemas/project-production/project-production-template-response-schema.json",
            "Create template from existing project production",
            null,
            new TypeReference<ProjectProductionTemplateResponse>() {},
            "CREATE_PROJECT_PRODUCTION_TEMPLATE_FROM_PRODUCTION"
    ),

    /** Finished project production batches (serial numbers) for a given product/category name. */
    PROJECT_PRODUCTION_PRODUCTS_GET(
            "/api/v1/project-production/products?storageId={storageId}&category={category}",
            Method.GET,
            null,
            "Get finished project production instances (batches) by category/product name",
            null,
            new TypeReference<List<ProjectProductInstanceResponse>>() {},
            null
    ),

    // ========================================
    // PROJECT PRODUCTION TEMPLATE ENDPOINTS
    // ========================================

    PROJECT_PRODUCTION_TEMPLATE_GET_PAGE(
            "/api/v1/project-production-template?storageIds={storageId}&size=200",
            Method.GET,
            "schemas/project-production/project-production-template-response-list-schema.json",
            "Get project production template page filtered by storage",
            null,
            new TypeReference<List<ProjectProductionTemplateResponse>>() {},
            null
    ),

    PROJECT_PRODUCTION_TEMPLATE_GET_BY_ID(
            "/api/v1/project-production-template/{id}?storageId={storageId}",
            Method.GET,
            "schemas/project-production/project-production-template-response-schema.json",
            "Get project production template by id",
            null,
            new TypeReference<ProjectProductionTemplateResponse>() {},
            null
    ),

    PROJECT_PRODUCTION_TEMPLATE_POST_CREATE(
            "/api/v1/project-production-template",
            Method.POST,
            "schemas/project-production/project-production-template-response-schema.json",
            "Create project production template",
            new TypeReference<ProjectProductionTemplateRequest>() {},
            new TypeReference<ProjectProductionTemplateResponse>() {},
            "CREATE_PROJECT_PRODUCTION_TEMPLATE"
    ),

    PROJECT_PRODUCTION_TEMPLATE_PUT_UPDATE(
            "/api/v1/project-production-template/{id}",
            Method.PUT,
            "schemas/project-production/project-production-template-response-schema.json",
            "Update project production template",
            new TypeReference<ProjectProductionTemplateRequest>() {},
            new TypeReference<ProjectProductionTemplateResponse>() {},
            "UPDATE_PROJECT_PRODUCTION_TEMPLATE"
    ),

    PROJECT_PRODUCTION_TEMPLATE_DELETE(
            "/api/v1/project-production-template/{id}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete project production template",
            null,
            null,
            "DELETE_PROJECT_PRODUCTION_TEMPLATE"
    ),

    PROJECT_PRODUCTION_TEMPLATE_STAGE_POST_ADD(
            "/api/v1/project-production-template/{templateId}/stage?storageId={storageId}",
            Method.POST,
            "schemas/project-production/project-production-stage-template-response-schema.json",
            "Add stage to project production template",
            new TypeReference<ProjectProductionStageRequest>() {},
            new TypeReference<ProjectProductionStageTemplateResponse>() {},
            "ADD_PROJECT_PRODUCTION_TEMPLATE_STAGE"
    ),

    PROJECT_PRODUCTION_TEMPLATE_STAGE_PUT_UPDATE(
            "/api/v1/project-production-template/{templateId}/stage/{stageId}?storageId={storageId}",
            Method.PUT,
            "schemas/project-production/project-production-stage-template-response-schema.json",
            "Update project production template stage",
            new TypeReference<ProjectProductionStageRequest>() {},
            new TypeReference<ProjectProductionStageTemplateResponse>() {},
            "UPDATE_PROJECT_PRODUCTION_TEMPLATE_STAGE"
    ),

    PROJECT_PRODUCTION_TEMPLATE_STAGE_DELETE(
            "/api/v1/project-production-template/{templateId}/stage/{stageId}?storageId={storageId}",
            Method.DELETE,
            null,
            "Delete project production template stage",
            null,
            null,
            "DELETE_PROJECT_PRODUCTION_TEMPLATE_STAGE"
    ),

    PROJECT_PRODUCTION_TEMPLATE_CREATE_PRODUCTION(
            "/api/v1/project-production-template/{id}/create-production?storageId={storageId}",
            Method.POST,
            "schemas/project-production/project-production-response-schema.json",
            "Create project production from template",
            null,
            new TypeReference<ProjectProductionResponse>() {},
            "CREATE_PROJECT_PRODUCTION_FROM_TEMPLATE"
    ),

    // ========================================
    // PROJECT CATEGORY ENDPOINTS
    // ========================================

    PROJECT_CATEGORY_GET_PAGE(
            "/api/v1/project-category",
            Method.GET,
            "schemas/project-production/project-category-response-list-schema.json",
            "Get project category page",
            null,
            new TypeReference<List<ProjectCategoryResponse>>() {},
            null
    ),

    PROJECT_CATEGORY_GET_ALL_ACTIVE(
            "/api/v1/project-category/all",
            Method.GET,
            "schemas/project-production/project-category-response-list-schema.json",
            "Get all active project categories",
            null,
            new TypeReference<List<ProjectCategoryResponse>>() {},
            null
    ),

    PROJECT_CATEGORY_GET_BY_ID(
            "/api/v1/project-category/{id}",
            Method.GET,
            "schemas/project-production/project-category-response-schema.json",
            "Get project category by id",
            null,
            new TypeReference<ProjectCategoryResponse>() {},
            null
    ),

    PROJECT_CATEGORY_POST_CREATE(
            "/api/v1/project-category",
            Method.POST,
            "schemas/project-production/project-category-response-schema.json",
            "Create project category",
            new TypeReference<ProjectCategoryRequest>() {},
            new TypeReference<ProjectCategoryResponse>() {},
            "CREATE_PROJECT_CATEGORY"
    ),

    PROJECT_CATEGORY_PUT_UPDATE(
            "/api/v1/project-category/{id}",
            Method.PUT,
            "schemas/project-production/project-category-response-schema.json",
            "Update project category",
            new TypeReference<ProjectCategoryRequest>() {},
            new TypeReference<ProjectCategoryResponse>() {},
            "UPDATE_PROJECT_CATEGORY"
    ),

    PROJECT_CATEGORY_DELETE(
            "/api/v1/project-category/{id}",
            Method.DELETE,
            null,
            "Delete (deactivate) project category",
            null,
            null,
            "DELETE_PROJECT_CATEGORY"
    ),

    PROJECT_CATEGORY_PUT_RESTORE(
            "/api/v1/project-category/{id}/restore",
            Method.PUT,
            null,
            "Restore (reactivate) project category",
            null,
            null,
            "RESTORE_PROJECT_CATEGORY"
    ),

    // ========================================
    // PROJECT PRODUCT ENDPOINTS
    // ========================================

    PROJECT_PRODUCT_GET_PAGE(
            "/api/v1/project-product",
            Method.GET,
            "schemas/project-production/project-product-response-list-schema.json",
            "Get project product page",
            null,
            new TypeReference<List<ProjectProductResponse>>() {},
            null
    ),

    PROJECT_PRODUCT_GET_ALL_BY_CATEGORY(
            "/api/v1/project-product/all?projectCategoryId={categoryId}",
            Method.GET,
            "schemas/project-production/project-product-response-list-schema.json",
            "Get project products filtered by category",
            null,
            new TypeReference<List<ProjectProductResponse>>() {},
            null
    ),

    PROJECT_PRODUCT_GET_BY_ID(
            "/api/v1/project-product/{id}",
            Method.GET,
            "schemas/project-production/project-product-response-schema.json",
            "Get project product by id",
            null,
            new TypeReference<ProjectProductResponse>() {},
            null
    ),

    PROJECT_PRODUCT_POST_CREATE(
            "/api/v1/project-product",
            Method.POST,
            "schemas/project-production/project-product-response-schema.json",
            "Create project product",
            new TypeReference<ProjectProductRequest>() {},
            new TypeReference<ProjectProductResponse>() {},
            "CREATE_PROJECT_PRODUCT"
    ),

    PROJECT_PRODUCT_PUT_UPDATE(
            "/api/v1/project-product/{id}",
            Method.PUT,
            "schemas/project-production/project-product-response-schema.json",
            "Update project product",
            new TypeReference<ProjectProductRequest>() {},
            new TypeReference<ProjectProductResponse>() {},
            "UPDATE_PROJECT_PRODUCT"
    ),

    PROJECT_PRODUCT_DELETE(
            "/api/v1/project-product/{id}",
            Method.DELETE,
            null,
            "Delete (deactivate) project product",
            null,
            null,
            "DELETE_PROJECT_PRODUCT"
    ),

    PROJECT_PRODUCT_PUT_RESTORE(
            "/api/v1/project-product/{id}/restore",
            Method.PUT,
            null,
            "Restore (reactivate) project product",
            null,
            null,
            "RESTORE_PROJECT_PRODUCT"
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

    STORAGE_ITEM_BATCHES_GET_BY_RESOURCE(
            "/api/v1/storage-items/batches",
            Method.GET,
            "schemas/inventory/storage-item-batch-list-schema.json",
            "Get storage item batches by storageId + resourceId (exact lookup, no storageItemId scan)",
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

    STORAGE_EQUIPMENT_INVENTORY_STATUS_GET(
            "/api/v1/storages/{id}/equipment-inventory/status",
            Method.GET,
            "schemas/inventory/inventory-session-status-schema.json",
            "Get equipment inventory session status",
            null,
            new TypeReference<InventorySessionStatus>() {},
            null
    ),

    STORAGE_EQUIPMENT_INVENTORY_STATUS_PUT(
            "/api/v1/storages/{id}/equipment-inventory/status",
            Method.PUT,
            "schemas/inventory/inventory-session-status-schema.json",
            "Open or close equipment inventory session",
            new TypeReference<InventorySessionStatus>() {},
            new TypeReference<InventorySessionStatus>() {},
            "OPEN_EQUIPMENT_INVENTORY_SESSION"
    ),

    STORAGE_INVENTORY_MULTI_GET(
            "/api/v1/storages/inventory",
            Method.GET,
            "schemas/inventory/multi-location-inventory-list-schema.json",
            "Get multi-location inventory page (supports exact locations + resourceIds filter)",
            null,
            new TypeReference<List<MultiLocationStorageItemResponse>>() {},
            null
    ),

    STORAGE_INVENTORY_HIERARCHY_GET(
            "/api/v1/storages/inventory",
            Method.GET,
            "schemas/inventory/multi-location-inventory-list-schema.json",
            "Get hierarchy inventory page (?parentStorageId=)",
            null,
            new TypeReference<List<MultiLocationStorageItemResponse>>() {},
            "HIERARCHY_INVENTORY"
    ),

    STORAGE_INVENTORY_TAG_STATISTICS_GET(
            "/api/v1/storages/inventory/tag-statistics",
            Method.GET,
            null,
            "Get inventory resource tag statistics",
            null,
            new TypeReference<List<ProductionProcessTagStatisticResponse>>() {},
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

    EXPORT_CREW_STOCKS(
            "/api/v1/export-analytics/crew-stocks",
            Method.GET,
            null,
            "Export crew stocks XLSX",
            null,
            null,
            null
    ),

    INVENTORY_WRITE_OFF_GET_PAGE(
            "/api/v1/storages/inventory/write-off",
            Method.GET,
            null,
            "Get inventory write-off reconciliation page",
            null,
            null,
            null
    ),

    INVENTORY_WRITE_OFF_PUT_COMPLETE(
            "/api/v1/storages/inventory/write-off/complete",
            Method.PUT,
            null,
            "Complete inventory write-off reconciliation",
            null,
            null,
            null
    ),

    INVENTORY_WRITE_OFF_PUT_REJECT(
            "/api/v1/storages/inventory/write-off/reject",
            Method.PUT,
            null,
            "Reject inventory write-off reconciliation",
            null,
            null,
            null
    ),

    INVENTORY_WRITE_OFF_GET_SHORT_STATS(
            "/api/v1/storages/inventory/write-off/short-stats",
            Method.GET,
            null,
            "Get inventory write-off short stats",
            null,
            null,
            null
    ),

    // ========================================
    // FAITA / RESOURCE RECONCILIATION ENDPOINTS
    // ========================================

    FAITA_RESOURCES_GET(
            "/api/v1/integrations/faita/resources",
            Method.GET,
            null,
            "List FAITA resources with reconciliations and implicit resources",
            null,
            new TypeReference<List<FaitaResourceResponse>>() {},
            null
    ),

    FAITA_IMPLICIT_RESOURCES_PUT(
            "/api/v1/integrations/faita/resources/{externalId}/implicit-resources",
            Method.PUT,
            null,
            "Save additional (implicit) resources for a FAITA product",
            new TypeReference<SaveImplicitResourcesRequest>() {},
            new TypeReference<FaitaResourceResponse>() {},
            null
    ),

    RESOURCE_RECONCILIATION_GET_PAGE(
            "/api/v1/resources/reconciliations",
            Method.GET,
            null,
            "Get resource reconciliation page (FLIGHT ↔ ERP)",
            null,
            new TypeReference<PagedResourceReconciliationResponse>() {},
            null
    ),

    RESOURCE_RECONCILIATION_CREATE(
            "/api/v1/resources/reconciliations",
            Method.POST,
            null,
            "Create FLIGHT resource reconciliation",
            new TypeReference<ResourceReconciliationRequest>() {},
            new TypeReference<List<ResourceReconciliationResponse>>() {},
            "CREATE"
    ),

    RESOURCE_RECONCILIATION_DELETE_BY_ID(
            "/api/v1/resources/reconciliations/{id}",
            Method.DELETE,
            null,
            "Delete resource reconciliation by id",
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
    /**
     * Journal page + aggregated {@code sums} in one response.
     * Separate {@code /relocations/sum} was removed from the backend — clients read {@code sums}
     * from this payload (see tk-ui ResourceRelocationViewPage).
     */
    RESOURCE_VIEWER_RELOCATIONS_GET(
            "/api/v1/resources-viewer/relocations",
            Method.GET,
            "schemas/resource-viewer/resource-relocation-viewer-page-schema.json",
            "Get resource viewer relocation journal page (content + sums)",
            null,
            new TypeReference<PagedResourceRelocationViewerResponse>() {},
            null
    ),

    RESOURCE_VIEWER_EXPORT(
            "/api/v1/resources-viewer/export",
            Method.GET,
            null,
            "Export resource viewer relocation journal to Excel",
            null,
            new TypeReference<byte[]>() {},
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

    INVOICE_POST_GENERATE(
            "/api/v1/invoice/generate/{storageId}/{relocationId}",
            Method.POST,
            null,
            "Generate invoice PDF for relocation",
            new TypeReference<InvoiceDataRequest>() {},
            null,
            "GENERATE_INVOICE"
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
            "Receive resources (SUPPLIER→storage or CREW→storage return, AUTO_FINISHED)",
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
            "Edit outbound relocation (CREATED in-transit or AUTO_FINISHED)",
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
    // INCIDENT ENDPOINTS (надзвичайна подія)
    // ========================================
    INCIDENT_POST_CREATE(
            "/api/v1/incidents/relocations",
            Method.POST,
            null,
            "Create relocation incident (full cargo loss)",
            new TypeReference<RelocationIncidentRequest>() {},
            new TypeReference<Void>() {},
            null
    ),

    INCIDENT_GET_BY_RELOCATION(
            "/api/v1/incidents/relocations/{id}",
            Method.GET,
            "schemas/incidents/relocation-incident-response-schema.json",
            "Get incident by relocation id",
            null,
            new TypeReference<RelocationIncidentResponse>() {},
            null
    ),

    INCIDENT_DELETE_BY_RELOCATION(
            "/api/v1/incidents/relocations/{id}",
            Method.DELETE,
            null,
            "Delete incident by relocation id",
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

    EQUIPMENT_GET_BY_ID(
            "/api/v1/equipment/{id}",
            Method.GET,
            "schemas/equipment/equipment-response-schema.json",
            "Get equipment by id",
            null,
            new TypeReference<EquipmentResponse>() {},
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

    EQUIPMENT_CATEGORY_GET_BY_ID(
            "/api/v1/equipment-categories/{id}",
            Method.GET,
            null,
            "Get equipment category by id",
            null,
            new TypeReference<EquipmentCategoryResponse>() {},
            null
    ),

    EQUIPMENT_CATEGORY_POST_CREATE(
            "/api/v1/equipment-categories",
            Method.POST,
            null,
            "Create equipment category",
            new TypeReference<EquipmentCategoryRequest>() {},
            new TypeReference<EquipmentCategoryResponse>() {},
            "CREATE_EQUIPMENT_CATEGORY"
    ),

    EQUIPMENT_CATEGORY_PUT_UPDATE(
            "/api/v1/equipment-categories/{id}",
            Method.PUT,
            null,
            "Update equipment category",
            new TypeReference<EquipmentCategoryRequest>() {},
            new TypeReference<EquipmentCategoryResponse>() {},
            "UPDATE_EQUIPMENT_CATEGORY"
    ),

    EQUIPMENT_CATEGORY_DELETE(
            "/api/v1/equipment-categories/{id}",
            Method.DELETE,
            null,
            "Delete equipment category",
            null,
            null,
            "DELETE_EQUIPMENT_CATEGORY"
    ),

    EQUIPMENT_POST_CREATE(
            "/api/v1/equipment",
            Method.POST,
            "schemas/equipment/equipment-create-list-schema.json",
            "Create equipment",
            new TypeReference<EquipmentCreateRequest>() {},
            new TypeReference<List<EquipmentResponse>>() {},
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

    EQUIPMENT_GET_HISTORY(
            "/api/v1/equipment/history",
            Method.GET,
            null,
            "Get equipment operation history for a storage and period",
            null,
            null,
            null
    ),

    EQUIPMENT_GET_UNIT_HISTORY(
            "/api/v1/equipment/{id}/history",
            Method.GET,
            null,
            "Get operation and assignment history for one equipment unit",
            null,
            null,
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

    EMPLOYEE_PUT_UPDATE(
            "/api/v1/employees/{id}",
            Method.PUT,
            "schemas/employee/employee-response-schema.json",
            "Update employee",
            new TypeReference<EmployeeRequest>() {},
            new TypeReference<EmployeeResponse>() {},
            "UPDATE_EMPLOYEE"
    ),

    EMPLOYEE_DELETE(
            "/api/v1/employees/{id}",
            Method.DELETE,
            null,
            "Soft-delete employee",
            null,
            null,
            "DELETE_EMPLOYEE"
    ),

    // ========================================
    // SHIFT ENDPOINTS
    // ========================================
    SHIFT_GET_ALL(
            "/api/v1/shifts/{storageId}",
            Method.GET,
            null,
            "Get shifts for storage",
            null,
            new TypeReference<List<ShiftResponse>>() {},
            null
    ),

    SHIFT_GET_BY_ID(
            "/api/v1/shifts/{id}/{storageId}",
            Method.GET,
            null,
            "Get shift by id",
            null,
            new TypeReference<ShiftResponse>() {},
            null
    ),

    SHIFT_POST_CREATE(
            "/api/v1/shifts/{storageId}",
            Method.POST,
            null,
            "Create shift",
            new TypeReference<ShiftRequest>() {},
            new TypeReference<ShiftResponse>() {},
            "CREATE_SHIFT"
    ),

    SHIFT_PUT_UPDATE(
            "/api/v1/shifts/{id}/{storageId}",
            Method.PUT,
            null,
            "Update shift",
            new TypeReference<ShiftRequest>() {},
            new TypeReference<ShiftResponse>() {},
            "UPDATE_SHIFT"
    ),

    SHIFT_DELETE(
            "/api/v1/shifts/{id}/{storageId}",
            Method.DELETE,
            null,
            "Delete shift",
            null,
            null,
            "DELETE_SHIFT"
    ),

    // ========================================
    // PRODUCTION ORDER ENDPOINTS
    // ========================================
    PRODUCTION_ORDER_GET_PAGE(
            "/api/v1/production-orders",
            Method.GET,
            null,
            "Get production orders page",
            null,
            null,
            null
    ),

    PRODUCTION_ORDER_GET_BY_ID(
            "/api/v1/production-orders/{id}",
            Method.GET,
            null,
            "Get production order by id",
            null,
            new TypeReference<ProductionOrderResponse>() {},
            null
    ),

    PRODUCTION_ORDER_POST_CREATE(
            "/api/v1/production-orders",
            Method.POST,
            null,
            "Create production order",
            new TypeReference<ProductionOrderRequest>() {},
            new TypeReference<ProductionOrderResponse>() {},
            "CREATE_PRODUCTION_ORDER"
    ),

    PRODUCTION_ORDER_PUT_UPDATE(
            "/api/v1/production-orders/{id}",
            Method.PUT,
            null,
            "Update production order",
            new TypeReference<ProductionOrderRequest>() {},
            new TypeReference<ProductionOrderResponse>() {},
            "UPDATE_PRODUCTION_ORDER"
    ),

    PRODUCTION_ORDER_DELETE(
            "/api/v1/production-orders/{id}",
            Method.DELETE,
            null,
            "Delete production order",
            null,
            null,
            "DELETE_PRODUCTION_ORDER"
    ),

    PRODUCTION_ORDER_PUT_CANCEL(
            "/api/v1/production-orders/{id}/cancel",
            Method.PUT,
            null,
            "Cancel production order",
            null,
            new TypeReference<ProductionOrderResponse>() {},
            null
    ),

    PRODUCTION_ORDER_GET_HOLDS(
            "/api/v1/production-orders/{id}/holds",
            Method.GET,
            null,
            "Get production order holds",
            null,
            null,
            null
    ),

    PRODUCTION_ORDER_GET_TASKS(
            "/api/v1/production-orders/{id}/tasks",
            Method.GET,
            null,
            "Get production order tasks",
            null,
            null,
            null
    ),

    PRODUCTION_ORDER_GET_TARGET_LOCATIONS(
            "/api/v1/production-orders/target-locations",
            Method.GET,
            null,
            "Get production order target locations",
            null,
            new TypeReference<List<SimpleEntityResponse>>() {},
            null
    ),

    PRODUCTION_ORDER_GET_LINKABLE_ORDERS(
            "/api/v1/production-orders/linkable-orders",
            Method.GET,
            null,
            "Get warehouse orders linkable to a production order",
            null,
            null,
            null
    ),

    PRODUCTION_ORDER_POST_DECOMPOSE(
            "/api/v1/production-orders/{id}/decompose",
            Method.POST,
            null,
            "Decompose production order",
            new TypeReference<DecompositionRequest>() {},
            new TypeReference<DecompositionResponse>() {},
            null
    ),

    PRODUCTION_ORDER_POST_GENERATE(
            "/api/v1/production-orders/{id}/generate",
            Method.POST,
            null,
            "Generate production order tasks",
            new TypeReference<DecompositionRequest>() {},
            null,
            "GENERATE_PRODUCTION_ORDER"
    ),

    PRODUCTION_ORDER_GET_LINKED_ORDERS(
            "/api/v1/production-orders/{id}/orders",
            Method.GET,
            null,
            "Get warehouse orders linked to a production order",
            null,
            null,
            null
    ),

    PRODUCTION_ORDER_POST_LINK_ORDER(
            "/api/v1/production-orders/{id}/orders",
            Method.POST,
            null,
            "Link warehouse order to a production order",
            new TypeReference<ProductionOrderLinkRequest>() {},
            null,
            "LINK_PRODUCTION_ORDER"
    ),

    PRODUCTION_ORDER_DELETE_UNLINK_ORDER(
            "/api/v1/production-orders/{id}/orders/{orderId}",
            Method.DELETE,
            null,
            "Unlink warehouse order from a production order",
            null,
            null,
            "UNLINK_PRODUCTION_ORDER"
    ),

    // ========================================
    // ANALYTICS / AUDIT ENDPOINTS
    // ========================================
    PRODUCTION_ANALYTIC_SUMMARY_GET(
            "/api/v1/production/analytic/summary",
            Method.GET,
            null,
            "Get production analytics summary",
            null,
            null,
            null
    ),

    ORDER_ANALYTIC_SUMMARY_GET(
            "/api/v1/orders/analytic/summary",
            Method.GET,
            null,
            "Get order analytics summary",
            null,
            null,
            null
    ),

    UNIT_ANALYTICS_GET(
            "/api/v1/unit-analytics",
            Method.GET,
            null,
            "Get unit analytics",
            null,
            null,
            null
    ),

    AUDIT_LOG_GET_PAGE(
            "/api/v1/audit-log",
            Method.GET,
            null,
            "Get audit log page",
            null,
            null,
            null
    ),

    ANALYTICS_SESSIONS_GET(
            "/api/v1/analytics/sessions",
            Method.GET,
            null,
            "Get user session analytics",
            null,
            null,
            null
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
    // USER MANAGEMENT ENDPOINTS (Keycloak admin)
    // ========================================

    USER_GET_ME(
            "/api/v1/users/me",
            Method.GET,
            "schemas/users/user-me-response-schema.json",
            "Get current authenticated user profile",
            null,
            new TypeReference<UserMeResponse>() {},
            null
    ),

    USER_GET_PAGE(
            "/api/v1/users",
            Method.GET,
            "schemas/users/user-page-schema.json",
            "Get paginated users list",
            null,
            new TypeReference<PagedUserResponse>() {},
            null
    ),

    USER_GET_BY_ID(
            "/api/v1/users/{id}",
            Method.GET,
            "schemas/users/user-response-schema.json",
            "Get user by Keycloak id",
            null,
            new TypeReference<UserModelResponse>() {},
            null
    ),

    USER_POST_CREATE(
            "/api/v1/users",
            Method.POST,
            "schemas/users/one-time-credentials-schema.json",
            "Create Keycloak user",
            new TypeReference<UserRequest>() {},
            new TypeReference<OneTimeUserCredentialsResponse>() {},
            "CREATE"
    ),

    USER_PUT_UPDATE(
            "/api/v1/users/{id}",
            Method.PUT,
            "schemas/users/user-response-schema.json",
            "Update Keycloak user",
            new TypeReference<UserRequest>() {},
            new TypeReference<UserModelResponse>() {},
            "UPDATE"
    ),

    USER_GET_ROLES(
            "/api/v1/users/roles",
            Method.GET,
            "schemas/users/role-list-schema.json",
            "Get all realm roles",
            null,
            new TypeReference<List<RoleModelResponse>>() {},
            null
    ),

    USER_GET_ROLE_BY_NAME(
            "/api/v1/users/roles/{roleName}",
            Method.GET,
            "schemas/users/role-response-schema.json",
            "Get realm role details with permissions",
            null,
            new TypeReference<RoleModelResponse>() {},
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

    INTERNAL_STORAGE_GET_STRUCTURE(
            "/api/v1/internal/storages/structure",
            Method.GET,
            "schemas/storages/storage-structure-internal-list-schema.json",
            "Internal flat storage structure for delivery-bot location hierarchy",
            null,
            new TypeReference<List<StorageViewInternalResponse>>() {},
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