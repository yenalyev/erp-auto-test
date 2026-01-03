package com.erp.data;

import com.erp.builders.resources.ResourceTestData;
import com.erp.builders.units.MeasurementUnitTestData;
import com.erp.data.strategies.RequestBodyStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RequestBodyFactory {

    private static final Map<String, RequestBodyStrategy> STRATEGIES = new HashMap<>();

    // Створюємо інстанси білдерів один раз (Singleton-like)
    private static final ResourceTestData RESOURCE_BUILDER = new ResourceTestData();
    private static final MeasurementUnitTestData UNIT_BUILDER = new MeasurementUnitTestData();

    static {
        // --- Resource Strategies ---
        register("create_resource", context -> RESOURCE_BUILDER.random());
        // 1. Сценарій: Тільки зміна імені (OK)
        register("update_resource_name", context -> {
            Long id = context.getSharedResourceId();
            return (id != null) ? RESOURCE_BUILDER.modify(RESOURCE_BUILDER.random(), req -> {
                req.setName("Updated Resource Name");
                // Unit залишаємо той самий, що прийшов з random() або null (якщо API дозволяє)
            }) : null;
        });

        // 2. Сценарій: Спроба зміни одиниці виміру (Error)
        register("update_resource_unit", context -> {
            Long id = context.getSharedResourceId();
            return (id != null) ? RESOURCE_BUILDER.modify(RESOURCE_BUILDER.random(), req -> {
                // Спеціально ставимо інший UnitId, щоб спровокувати помилку
                req.setMeasurementUnitId(2L);
            }) : null;
        });

        // --- Unit Strategies ---
        register("create_unit", context -> UNIT_BUILDER.random());

    }

    public static Object generate(String bodyType, RbacTestContext context) {
        if (bodyType == null || bodyType.isBlank()) return null;

        RequestBodyStrategy strategy = STRATEGIES.get(bodyType.toLowerCase());
        if (strategy == null) {
            log.error("❌ No strategy registered for body type: {}", bodyType);
            return null;
        }

        return strategy.generate(context);
    }

    public static void register(String bodyType, RequestBodyStrategy strategy) {
        STRATEGIES.put(bodyType.toLowerCase(), strategy);
    }
}