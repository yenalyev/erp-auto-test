package com.erp.test_context;

import com.erp.models.common.GlobalPlanAltGroupContext;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.models.response.UserModelResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 🔑 Ключі для доступу до даних у TestContext.
 * Забезпечує типізацію та виключає помилки в назвах.
 */
@Getter
@RequiredArgsConstructor
public enum ContextKey {
    SHARED_RESOURCE_ID("sharedResourceId", Long.class),
    SHARED_RESOURCE_CATEGORY_ID("sharedResourceCategoryId", Long.class),
    SHARED_UNIT_ID("sharedUnitId", Long.class),
    SHARED_TECH_MAP_ID("sharedTechMapId", Long.class),
    SHARED_ORDER_ID("sharedOrderId", Long.class),
    ORDER_ID("orderId", Long.class),
    ORDER_RESOURCE_ID("orderResourceId", Long.class),
    ORDER_REQUESTER_STORAGE_ID("orderRequesterStorageId", Long.class),
    ORDER_GATHERING_STORAGE_ID("orderGatheringStorageId", Long.class),
    ORDER_BOOKING_ID("orderBookingId", Long.class),
    NOTIFICATION_RECIPIENT_ID("notificationRecipientId", Integer.class),
    SHARED_RESOURCE("sharedResource", ResourceResponse.class),
    SHARED_MEASUREMENT_UNIT_LIST("sharedAvailableMeasurementUnits", List.class),
    SHARED_AVAILABLE_RESOURCES("sharedAvailableResources", List.class),
    DYNAMIC_TECH_MAP ("dynamicTechnologicalMap", TechnologicalMapResponse.class),
    DYNAMIC_TECH_MAP_NEW_NAME ("dynamicTechnologicalMapNewName", String.class),
    DYNAMIC_TECH_MAP_ID("dynamicTechnologicalMapId", Long.class),
    OWNER_1_STORAGE_ID("owner_1_storageId", Long.class),
    OWNER_2_STORAGE_ID("owner_2_storageId", Long.class),
    OWNER_INCORRECT_STORAGE_ID("owner_incorrect_storageId", Long.class),
    DYNAMIC_PRODUCTIONS("dynamic_productions", List.class),
    PRODUCTION_TECH_MAP("productionTechMap", TechnologicalMapResponse.class),
    PRODUCTION_OUTPUT_RESOURCE_ID("productionOutputResourceId", Long.class),
    PRODUCTION_INPUT_RESOURCE_IDS("productionInputResourceIds", List.class),
    PRODUCTION_OUTPUT_STORAGE_ITEM_ID("productionOutputStorageItemId", Long.class),
    DISASSEMBLE_TECH_MAP("disassembleTechMap", TechnologicalMapResponse.class),
    DISASSEMBLE_INPUT_RESOURCE_ID("disassembleInputResourceId", Long.class),
    DISASSEMBLE_OUTPUT_RESOURCE_ID("disassembleOutputResourceId", Long.class),
    NON_SERIES_RESOURCE_ID("nonSeriesResourceId", Long.class),
    NON_SERIES_RESOURCE_NAME("nonSeriesResourceName", String.class),
    NON_SERIES_SEEDED_STOCK("nonSeriesSeededStock", Double.class),
    DYNAMIC_STORAGE("dynamicStorage",StorageResponse .class ),
    DYNAMIC_PLAN("dynamicPlan",PlanResponse .class ),
    DYNAMIC_PLAN_LIST("dynamicPlanList",List.class ),
    DYNAMIC_PLAN_ID("dynamicPlanId", Long.class),
    GLOBAL_PLAN("globalPlan", GlobalPlanResponse.class),
    GLOBAL_PLAN_ID("globalPlanId", Long.class),
    GLOBAL_PLAN_CHAIN("globalPlanChain", GlobalPlanChainContext.class),
    GLOBAL_PLAN_ALT_CHAIN("globalPlanAltChain", GlobalPlanAltGroupContext.class),
    SHARED_STORAGE_LIST("sharedStorageList", List.class),
    RELOCATION_ID("relocationId", Long.class),
    RELOCATION_CREATED_ID("relocationCreatedId", Long.class),
    RELOCATION_AUTO_FINISHED_SEND_ID("relocationAutoFinishedSendId", Long.class),
    RELOCATION_SUPPLIER_ID("relocationSupplierId", Long.class),
    RELOCATION_UNIT_STORAGE_ID("relocationUnitStorageId", Long.class),
    RELOCATION_RESOURCE_ID("relocationResourceId", Long.class),
    /** CREATED relocation reserved for RBAC incident create (denied-only matrix). */
    RELOCATION_FOR_INCIDENT_ID("relocationForIncidentId", Long.class),
    /** LOST relocation with an existing incident (RBAC GET). */
    RELOCATION_WITH_INCIDENT_ID("relocationWithIncidentId", Long.class),
    EQUIPMENT_ID("equipmentId", Long.class),
    EQUIPMENT_CATEGORY_ID("equipmentCategoryId", Long.class),
    DEFECT_ID("defectId", Long.class),
    DEFECT_RESOURCE_ID("defectResourceId", Long.class),
    DEFECT_PRODUCTION_PROCESS_ID("defectProductionProcessId", Long.class),
    DEFECT_RELOCATION_ID("defectRelocationId", Long.class),
    /** Екіпаж (CREW storage) у області видимості CREWS для OWNER_1. */
    CREW_STORAGE_ID("crewStorageId", Long.class),
    /** Батьківський UNIT екіпажу (для crew-names RBAC / fixtures). */
    CREW_PARENT_UNIT_ID("crewParentUnitId", Long.class),
    /** Keycloak user id для RBAC matrix / user management tests. */
    SHARED_USER_ID("sharedUserId", String.class),
    /** Повна модель користувача для PUT /users/{id}. */
    SHARED_USER("sharedUser", UserModelResponse.class),
    /** Realm role name для GET /users/roles/{roleName}. */
    SHARED_ROLE_NAME("sharedRoleName", String.class),
    /** FAITA external product id for implicit-resources PUT / RBAC. */
    FAITA_EXTERNAL_ID("faitaExternalId", String.class),
    /** Project category id shared across project-production tests. */
    PROJECT_CATEGORY_ID("projectCategoryId", Long.class),
    PROJECT_CATEGORY_NAME("projectCategoryName", String.class),
    /** Project product id shared across project-production tests. */
    PROJECT_PRODUCT_ID("projectProductId", Long.class),
    PROJECT_PRODUCT_NAME("projectProductName", String.class),
    /** Resource id seeded with stock for project-production stage resource usage. */
    PROJECT_RESOURCE_ID("projectResourceId", Long.class),
    PROJECT_RESOURCE_NAME("projectResourceName", String.class),
    PROJECT_SEEDED_STOCK("projectSeededStock", Double.class),
    /** Optional shared project production id (e.g. for RBAC scenarios). */
    PROJECT_PRODUCTION_ID("projectProductionId", Long.class);
    private final String name;
    private final Class<?> type;
}