package com.erp.test_context;

import com.erp.enums.UserRole;
import com.erp.models.response.*;
import com.erp.utils.config.ConfigProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global Test Context - Runtime Data Storage
 * <p>
 * Зберігає динамічно створені тестові дані для RBAC тестів:
 * <p>
 * 1. **Per-Role Resources** - ресурси створені кожною роллю
 * 2. **Shared Resources** - спільні ресурси (створені ADMIN)
 * 3. **ERP Entity IDs** - IDs різних сутностей системи
 * <p>
 * Features:
 * - Thread-safe (ConcurrentHashMap)
 * - Support for multiple resource types
 * - Automatic endpoint → resource ID mapping
 * - Rich logging and debugging
 */
@Slf4j
@Data
public class GlobalTestContext implements TestContext {

    private Map<UserRole, Long> createdResources = new ConcurrentHashMap<>();

    private final Map<ContextKey, Object> attributes = new ConcurrentHashMap<>();

    // shared resources - for read-only tests
    private Long sharedResourceId;
    private ResourceResponse sharedResource;
    private Long sharedUnitId;
    private List<MeasurementUnitResponse> sharedAvailableMeasurementUnits;
    private Long sharedTechMapId;
    private Long sharedOrderId;
    private List<ResourceResponse> sharedAvailableResources;
    private List<StorageResponse> sharedStorageList;

    //dynamic resources for update/delete operation
    private TechnologicalMapResponse dynamicTechnologicalMap;
    private Long dynamicTechnologicalMapId;
    private String dynamicTechnologicalMapNewName;
    private List<ManufacturingItemResponse> dynamicProductionList;
    private StorageResponse dynamicStorage;
    private PlanResponse dynamicPlan;
    private List<PlanResponse> dynamicPlanList;


    // ============================================
    // Validation Methods
    // ============================================

    /**
     * Перевіряє чи всі необхідні shared resources створені
     */
    public boolean hasAllRequiredResources() {
        return sharedUnitId != null && sharedResourceId != null;
    }


    // ============================================
    // Logging & Debugging
    // ============================================

    /**
     * Логує інформацію про контекст
     */
    public void logInfo() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info(" Global Test Context State");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        log.info("   Shared Resources:");
        log.info("   Unit ID:          {}", formatId(sharedUnitId));
        log.info("   Resource ID:      {}", formatId(sharedResourceId));
        log.info("   Tech Map ID:      {}", formatId(sharedTechMapId));
        log.info("   Order ID:         {}", formatId(sharedOrderId));

        log.info("👥 Per-Role Resources:");
        if (createdResources.isEmpty()) {
            log.info("   (none)");
        } else {
            createdResources.forEach((role, id) ->
                    log.info("   {}: {}", role, id)
            );
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("Status:");
        log.info("   All Required Resources: {}",
                hasAllRequiredResources() ? "✅ Yes" : "❌ No");
        log.info("   Per-Role Resources:     {}",
                !createdResources.isEmpty() ? "✅ Yes" : "⚠️ None");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Форматує ID для логування
     */
    private String formatId(Long id) {
        return id != null ? String.valueOf(id) : "null";
    }

    /**
     * Створює summary для Allure report
     */
    public String toAllureSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        summary.append("RBAC Test Context Summary\n");
        summary.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        summary.append("  Shared Resources:\n");
        summary.append("  Unit ID:     ").append(formatId(sharedUnitId)).append("\n");
        summary.append("  Resource ID: ").append(formatId(sharedResourceId)).append("\n");
        summary.append("  Tech Map ID: ").append(formatId(sharedTechMapId)).append("\n");
        summary.append("  Order ID:    ").append(formatId(sharedOrderId)).append("\n\n");

        summary.append("👥 Per-Role Resources:\n");
        if (createdResources.isEmpty()) {
            summary.append("  (none)\n");
        } else {
            createdResources.forEach((role, id) ->
                    summary.append("  ").append(role).append(": ").append(id).append("\n")
            );
        }

        summary.append("\n Status:\n");
        summary.append("  All Required Resources: ")
                .append(hasAllRequiredResources() ? "✅ Yes" : "❌ No")
                .append("\n");
        summary.append("  Per-Role Resources:     ")
                .append(!createdResources.isEmpty() ? "✅ Yes" : "⚠️ None")
                .append("\n");

        return summary.toString();
    }

    @Override
    public void logState() {
        this.logInfo(); // Викликає існуючий метод логування
    }

    @Override
    public String toSummary() {
        return this.toAllureSummary();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(ContextKey key) {
        // 1. Пріоритет явним полям
        Object value = switch (key) {
            case SHARED_RESOURCE_ID -> sharedResourceId;
            case SHARED_UNIT_ID -> sharedUnitId;
            case SHARED_TECH_MAP_ID -> sharedTechMapId;
            case SHARED_ORDER_ID -> sharedOrderId;
            case SHARED_MEASUREMENT_UNIT_LIST -> sharedAvailableMeasurementUnits;
            case SHARED_RESOURCE -> sharedResource;
            case SHARED_AVAILABLE_RESOURCES -> sharedAvailableResources;
            case DYNAMIC_TECH_MAP -> dynamicTechnologicalMap;
            case DYNAMIC_TECH_MAP_ID -> dynamicTechnologicalMapId;
            case DYNAMIC_TECH_MAP_NEW_NAME -> dynamicTechnologicalMapNewName;
            case OWNER_1_STORAGE_ID -> ConfigProvider.getOwner1StorageId();
            case OWNER_2_STORAGE_ID -> ConfigProvider.getOwner2StorageId();
            case OWNER_INCORRECT_STORAGE_ID -> ConfigProvider.getIncorrectStorageId();
            case DYNAMIC_PRODUCTIONS ->  dynamicProductionList;
            case DYNAMIC_STORAGE -> dynamicStorage;
            case DYNAMIC_PLAN -> dynamicPlan;
            case DYNAMIC_PLAN_LIST -> dynamicPlanList;
            case SHARED_STORAGE_LIST -> sharedStorageList;
            default -> attributes.get(key); // Шукаємо в мапі за Enum ключем
        };
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(ContextKey key, T value) {
        if (value == null) {
            log.warn("⚠️ Skipping null value for key: {}", key);
            return;
        }

        switch (key) {
            case SHARED_RESOURCE_ID -> this.sharedResourceId = (Long) value;
            case SHARED_UNIT_ID -> this.sharedUnitId = (Long) value;
            case SHARED_TECH_MAP_ID -> this.sharedTechMapId = (Long) value;
            case SHARED_ORDER_ID -> this.sharedOrderId = (Long) value;
            case SHARED_MEASUREMENT_UNIT_LIST -> this.sharedAvailableMeasurementUnits = (List<MeasurementUnitResponse>) value;
            case SHARED_RESOURCE -> this.sharedResource = (ResourceResponse) value;
            case SHARED_AVAILABLE_RESOURCES -> this.sharedAvailableResources = (List<ResourceResponse>) value;
            case DYNAMIC_TECH_MAP -> this.dynamicTechnologicalMap = (TechnologicalMapResponse) value;
            case DYNAMIC_TECH_MAP_ID -> this.dynamicTechnologicalMapId = (Long) value;
            case DYNAMIC_TECH_MAP_NEW_NAME -> this.dynamicTechnologicalMapNewName = (String)value;
            case DYNAMIC_PRODUCTIONS -> this.dynamicProductionList = (List<ManufacturingItemResponse>) value;
            case DYNAMIC_STORAGE -> this.dynamicStorage = (StorageResponse) value;
            case DYNAMIC_PLAN -> this.dynamicPlan = (PlanResponse) value;
            case DYNAMIC_PLAN_LIST -> this.dynamicPlanList = (List<PlanResponse>) value;
            case SHARED_STORAGE_LIST -> this.sharedStorageList = (List<StorageResponse>) value;

            default -> attributes.put(key, value);
        }
    }

    @Override
    public void clear() {
        // Очищаємо всі ключі, визначені в Enum
        for (ContextKey key : ContextKey.values()) {
            try {
                this.set(key, null);
            } catch (ClassCastException e) {
            }
        }

        // Очищаємо додаткові структури
        createdResources.clear();
        attributes.clear();

        log.debug("🗑️ RBAC Context fully cleared using ContextKey iteration");
    }

    @Override
    public boolean isEmpty() {
        if (!attributes.isEmpty() || !createdResources.isEmpty()) {
            return false;
        }
        for (ContextKey key : ContextKey.values()) {
            if (this.get(key) != null) {
                return false;
            }
        }
        return true;
    }
}