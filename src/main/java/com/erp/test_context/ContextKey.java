package com.erp.test_context;

import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
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
    SHARED_UNIT_ID("sharedUnitId", Long.class),
    SHARED_TECH_MAP_ID("sharedTechMapId", Long.class),
    SHARED_ORDER_ID("sharedOrderId", Long.class),
    SHARED_RESOURCE("sharedResource", ResourceResponse.class),
    SHARED_MEASUREMENT_UNIT_LIST("sharedAvailableMeasurementUnits", List.class),
    SHARED_AVAILABLE_RESOURCES("sharedAvailableResources", List.class),
    DYNAMIC_TECH_MAP ("dynamicTechnologicalMap", TechnologicalMapResponse.class),
    DYNAMIC_TECH_MAP_NEW_NAME ("dynamicTechnologicalMapNewName", String.class),
    DYNAMIC_TECH_MAP_ID("dynamicTechnologicalMapId", Long.class),
    OWNER_1_STORAGE_ID("owner_1_storageId", Long.class),
    OWNER_2_STORAGE_ID("owner_2_storageId", Long.class),
    OWNER_INCORRECT_STORAGE_ID("owner_incorrect_storageId", Long.class),
    DYNAMIC_PRODUCTIONS("dynamic_productions", List.class ),
    DYNAMIC_STORAGE("dynamicStorage",StorageResponse .class ),
    DYNAMIC_PLAN("dynamicPlan",PlanResponse .class ),
    DYNAMIC_PLAN_LIST("dynamicPlanList",List.class ),
    SHARED_STORAGE_LIST("sharedStorageList", List.class);
    private final String name;
    private final Class<?> type;
}