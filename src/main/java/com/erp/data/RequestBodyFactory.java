package com.erp.data;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.builders.resources.ResourceTestData;
import com.erp.builders.units.MeasurementUnitTestData;
import com.erp.builders.techmap.TechnologicalMapTestData;
import com.erp.data.strategies.RequestBodyStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class RequestBodyFactory {

    // ✅ Використовуємо EnumMap для ефективності та Type Safety
    private static final Map<ApiEndpointDefinition, RequestBodyStrategy> STRATEGIES = new EnumMap<>(ApiEndpointDefinition.class);

    // Білдери
    private static final ResourceTestData RESOURCE_BUILDER = new ResourceTestData();
    private static final MeasurementUnitTestData UNIT_BUILDER = new MeasurementUnitTestData();
    private static final TechnologicalMapTestData TECH_MAP_BUILDER = new TechnologicalMapTestData(); // Додайте, якщо використовуєте

    static {
        // ============================
        // RESOURCES
        // ============================

        // POST /api/v1/resources
        register(ApiEndpointDefinition.RESOURCE_CREATE, context ->
                RESOURCE_BUILDER.random()
        );

        // PUT /api/v1/resources/{id} - Update Name
        register(ApiEndpointDefinition.RESOURCE_UPDATE_NAME, context -> {
            Long id = context.getSharedResourceId();
            if (id == null) {
                log.warn("⚠️ Cannot generate body for RESOURCE_UPDATE_NAME: Shared Resource ID is null");
                return null;
            }
            return RESOURCE_BUILDER.modify(RESOURCE_BUILDER.random(), req ->
                    req.setName("Updated Resource Name via RBAC")
            );
        });

        // PUT /api/v1/resources/{id} - Update Unit (Negative Case)
        register(ApiEndpointDefinition.RESOURCE_UPDATE_UNIT, context -> {
            Long id = context.getSharedResourceId();
            if (id == null) return null;
            return RESOURCE_BUILDER.modify(RESOURCE_BUILDER.random(), req ->
                    req.setMeasurementUnitId(9999L) // Невалідний ID для перевірки помилки
            );
        });

        // ============================
        // TECHNOLOGICAL MAPS
        // ============================

        // POST /api/v1/technological-maps
        register(ApiEndpointDefinition.TECH_MAP_CREATE, context ->
                TECH_MAP_BUILDER.random()
        );

        // PUT /api/v1/technological-maps/{id}
        register(ApiEndpointDefinition.TECH_MAP_UPDATE, context ->
                TECH_MAP_BUILDER.random() // Або специфічна логіка оновлення
        );
    }

    /**
     * Генерує тіло запиту на основі визначення ендпоінту
     */
    public static Object generate(ApiEndpointDefinition endpoint, RbacTestContext context) {
        if (endpoint == null) return null;

        RequestBodyStrategy strategy = STRATEGIES.get(endpoint);

        if (strategy == null) {
            // Якщо ендпоінт вимагає тіло, але стратегії немає - це помилка конфігурації
            if (endpoint.requiresBody()) {
                log.error("❌ No strategy registered for endpoint: {}", endpoint);
            }
            return null;
        }

        return strategy.generate(context);
    }

    public static void register(ApiEndpointDefinition endpoint, RequestBodyStrategy strategy) {
        STRATEGIES.put(endpoint, strategy);
    }
}