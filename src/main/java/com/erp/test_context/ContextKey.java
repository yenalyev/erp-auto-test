package com.erp.test_context;

import com.erp.models.response.ResourceResponse;
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
    DYNAMIC_TECH_MAP_ID("dynamicTechnologicalMapId", Long.class);
    private final String name;
    private final Class<?> type;
}