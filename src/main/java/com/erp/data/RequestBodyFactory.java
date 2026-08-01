package com.erp.data;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.ResourceRequestBodyFactory;
import com.erp.data.factories.defect.DefectRequestBodyFactory;
import com.erp.data.factories.faita.FaitaRequestBodyFactory;
import com.erp.data.factories.inventory.InventoryRequestBodyFactory;
import com.erp.data.factories.measurement_unit.MeasurementUnitResponseBodyFactory;
import com.erp.data.factories.non_series_production.NonSeriesProductionRequestBodyFactory;
import com.erp.data.factories.notification.NotificationRequestBodyFactory;
import com.erp.data.factories.order.OrderRequestBodyFactory;
import com.erp.data.factories.project_production.ProjectProductionRequestBodyFactory;
import com.erp.data.factories.global_plan.GlobalPlanRequestBodyFactory;
import com.erp.data.factories.plan.PlanRequestBodyFactory;
import com.erp.data.factories.production.ProductionRequestBodyFactory;
import com.erp.data.factories.incident.IncidentRequestBodyFactory;
import com.erp.data.factories.relocation.RelocationRequestBodyFactory;
import com.erp.data.factories.storage.StorageRequestBodyFactory;
import com.erp.data.factories.tech_map.TechnologicalMapRequestBodyFactory;
import com.erp.data.factories.user.UserRequestBodyFactory;
import com.erp.data.strategies.RequestBodyStrategy;
import com.erp.test_context.TestContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class RequestBodyFactory {

    // EnumMap для ефективності та Type Safety
    private static final Map<ApiEndpointDefinition, RequestBodyStrategy> STRATEGIES =
            new EnumMap<>(ApiEndpointDefinition.class);

    static {
        // Реєструємо стратегії для Resource, TechMaps, Storages, .....
        ResourceRequestBodyFactory.registerStrategies();
        MeasurementUnitResponseBodyFactory.registerStrategies();
        TechnologicalMapRequestBodyFactory.registerStrategies();
        StorageRequestBodyFactory.registerStrategies();
        ProductionRequestBodyFactory.registerStrategies();
        RelocationRequestBodyFactory.registerStrategies();
        IncidentRequestBodyFactory.registerStrategies();
        PlanRequestBodyFactory.registerStrategies();
        GlobalPlanRequestBodyFactory.registerStrategies();
        NonSeriesProductionRequestBodyFactory.registerStrategies();
        ProjectProductionRequestBodyFactory.registerStrategies();
        DefectRequestBodyFactory.registerStrategies();
        InventoryRequestBodyFactory.registerStrategies();
        UserRequestBodyFactory.registerStrategies();
        FaitaRequestBodyFactory.registerStrategies();
        OrderRequestBodyFactory.registerStrategies();
        NotificationRequestBodyFactory.registerStrategies();
    }

    /**
     * Генерує тіло запиту на основі визначення ендпоінту
     */
    public static Object generate(ApiEndpointDefinition endpoint,
                                  TestContext context) {
        if (endpoint == null) return null;

        RequestBodyStrategy strategy = STRATEGIES.get(endpoint);

        if (strategy == null) {
            // Якщо ендпоінт вимагає тіло, але стратегії немає - це помилка конфігурації
            if (endpoint.requiresBody()) {
                log.error("ERROR No strategy registered for endpoint: {}", endpoint);
            }
            return null;
        }

        return strategy.generate(context);
    }

    public static void register(ApiEndpointDefinition endpoint, RequestBodyStrategy strategy) {
        STRATEGIES.put(endpoint, strategy);
    }
}