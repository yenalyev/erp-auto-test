package com.erp.data;

import com.erp.enums.UserRole;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🎯 RBAC Test Context - Runtime Data Storage
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
public class RbacTestContext {

    // ============================================
    // 📦 Per-Role Resources
    // ============================================

    /**
     * Створені ресурси для кожної ролі: Role → ResourceId
     * <p>
     * Використовується коли потрібно тестувати що кожна роль
     * може працювати тільки зі своїми ресурсами
     */
    private Map<UserRole, Long> createdResources = new ConcurrentHashMap<>();

    // ============================================
    // 🌍 Shared Resources (спільні для всіх ролей)
    // ============================================

    /**
     * ID спільного ресурсу (створений ADMIN)
     * <p>
     * Використовується для:
     * - RESOURCE_UPDATE_NAME
     * - RESOURCE_UPDATE_UNIT
     * - RESOURCE_GET_BY_ID
     * - RESOURCE_DELETE
     */
    private Long sharedResourceId;

    /**
     * ID спільної одиниці виміру (Measurement Unit)
     * <p>
     * Використовується для створення Resources
     */
    private Long sharedUnitId;

    /**
     * ID спільної технологічної карти (Technological Map)
     * <p>
     * Використовується для:
     * - TECH_MAP_UPDATE
     * - TECH_MAP_GET_BY_ID
     * - TECH_MAP_DELETE
     */
    private Long sharedTechMapId;

    /**
     * ID спільного замовлення (Order)
     * <p>
     * Використовується для операцій з замовленнями
     */
    private Long sharedOrderId;

    // Додаткові shared resources можна додавати тут...

    // ============================================
    // 🔍 Per-Role Resource Management
    // ============================================

    /**
     * Зберігає ID створеного ресурсу для ролі
     */
    public void setResourceIdForRole(UserRole role, Long resourceId) {
        createdResources.put(role, resourceId);
        log.debug("✅ Stored resource ID {} for role {}", resourceId, role);
    }

    /**
     * Отримує ID ресурсу для ролі
     */
    public Long getResourceIdForRole(UserRole role) {
        return createdResources.get(role);
    }

    /**
     * Перевіряє чи є ресурс для ролі
     */
    public boolean hasResourceForRole(UserRole role) {
        return createdResources.containsKey(role);
    }

    // ============================================
    // 🎯 Endpoint → Resource ID Mapping
    // ============================================

    /**
     * ✅ Отримує Resource ID для конкретного endpoint
     * <p>
     * Використовується в RbacAccessMatrix для автоматичної
     * підстановки path parameters
     *
     * @param endpointName Endpoint definition name (e.g., "RESOURCE_UPDATE_NAME")
     * @return Resource ID as String or null if not applicable
     */
    public String getResourceIdForEndpoint(String endpointName) {
        if (endpointName == null) {
            log.warn("⚠️ Endpoint name is null");
            return null;
        }

        // ============================================
        // RESOURCE ENDPOINTS
        // ============================================

        if (endpointName.startsWith("RESOURCE_")) {
            // Endpoints що не потребують ID
            if (endpointName.equals("RESOURCE_GET_ALL") ||
                    endpointName.equals("RESOURCE_CREATE")) {
                return null;
            }

            // Всі інші RESOURCE endpoints потребують resource ID
            if (sharedResourceId != null) {
                return String.valueOf(sharedResourceId);
            } else {
                log.warn("⚠️ Endpoint {} requires resource ID but sharedResourceId is null",
                        endpointName);
                return null;
            }
        }

        // ============================================
        // TECHNOLOGICAL MAP ENDPOINTS
        // ============================================

        if (endpointName.startsWith("TECH_MAP_")) {
            // Endpoints що не потребують ID
            if (endpointName.equals("TECH_MAP_GET_ALL") ||
                    endpointName.equals("TECH_MAP_CREATE")) {
                return null;
            }

            // Всі інші TECH_MAP endpoints потребують tech map ID
            if (sharedTechMapId != null) {
                return String.valueOf(sharedTechMapId);
            } else {
                log.warn("⚠️ Endpoint {} requires tech map ID but sharedTechMapId is null",
                        endpointName);
                return null;
            }
        }

        // ============================================
        // MEASUREMENT UNIT ENDPOINTS
        // ============================================

        if (endpointName.startsWith("MEASUREMENT_UNIT_")) {
            if (endpointName.equals("MEASUREMENT_UNIT_GET_ALL")) {
                return null;
            }

            if (sharedUnitId != null) {
                return String.valueOf(sharedUnitId);
            } else {
                log.warn("⚠️ Endpoint {} requires unit ID but sharedUnitId is null",
                        endpointName);
                return null;
            }
        }

        // ============================================
        // ORDER ENDPOINTS
        // ============================================

        if (endpointName.startsWith("ORDER_")) {
            if (endpointName.equals("ORDER_GET_ALL") ||
                    endpointName.equals("ORDER_CREATE")) {
                return null;
            }

            if (sharedOrderId != null) {
                return String.valueOf(sharedOrderId);
            } else {
                log.warn("⚠️ Endpoint {} requires order ID but sharedOrderId is null",
                        endpointName);
                return null;
            }
        }

        // Default - немає ID для цього endpoint
        log.debug("ℹ️ No resource ID mapping for endpoint: {}", endpointName);
        return null;
    }

    // ============================================
    // ✅ Validation Methods
    // ============================================

    /**
     * Перевіряє чи всі необхідні shared resources створені
     */
    public boolean hasAllRequiredResources() {
        return sharedUnitId != null && sharedResourceId != null;
    }

    /**
     * Перевіряє чи є shared resource для endpoint
     */
    public boolean hasResourceForEndpoint(String endpointName) {
        String resourceId = getResourceIdForEndpoint(endpointName);
        return resourceId != null;
    }

    // ============================================
    // 🗑️ Cleanup Methods
    // ============================================

    /**
     * Очищає всі створені ресурси
     */
    public void clear() {
        createdResources.clear();
        sharedResourceId = null;
        sharedUnitId = null;
        sharedTechMapId = null;
        sharedOrderId = null;
        log.debug("🗑️ Test context cleared");
    }

    /**
     * Очищає тільки per-role ресурси
     */
    public void clearPerRoleResources() {
        createdResources.clear();
        log.debug("🗑️ Per-role resources cleared");
    }

    /**
     * Очищає тільки shared ресурси
     */
    public void clearSharedResources() {
        sharedResourceId = null;
        sharedUnitId = null;
        sharedTechMapId = null;
        sharedOrderId = null;
        log.debug("🗑️ Shared resources cleared");
    }

    // ============================================
    // 📊 Logging & Debugging
    // ============================================

    /**
     * Логує інформацію про контекст
     */
    public void logInfo() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📊 RBAC Test Context State");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        log.info("🌍 Shared Resources:");
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

        summary.append("🌍 Shared Resources:\n");
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

        summary.append("\n📊 Status:\n");
        summary.append("  All Required Resources: ")
                .append(hasAllRequiredResources() ? "✅ Yes" : "❌ No")
                .append("\n");
        summary.append("  Per-Role Resources:     ")
                .append(!createdResources.isEmpty() ? "✅ Yes" : "⚠️ None")
                .append("\n");

        return summary.toString();
    }

    // ============================================
    // 🎯 Builder-style Setters (для зручності)
    // ============================================

    /**
     * Builder-style setter для unit ID
     */
    public RbacTestContext withSharedUnitId(Long unitId) {
        this.sharedUnitId = unitId;
        return this;
    }

    /**
     * Builder-style setter для resource ID
     */
    public RbacTestContext withSharedResourceId(Long resourceId) {
        this.sharedResourceId = resourceId;
        return this;
    }

    /**
     * Builder-style setter для tech map ID
     */
    public RbacTestContext withSharedTechMapId(Long techMapId) {
        this.sharedTechMapId = techMapId;
        return this;
    }

    /**
     * Builder-style setter для order ID
     */
    public RbacTestContext withSharedOrderId(Long orderId) {
        this.sharedOrderId = orderId;
        return this;
    }

    // ============================================
    // 📈 Statistics Methods
    // ============================================

    /**
     * Отримує кількість per-role ресурсів
     */
    public int getPerRoleResourcesCount() {
        return createdResources.size();
    }

    /**
     * Отримує кількість shared ресурсів
     */
    public int getSharedResourcesCount() {
        int count = 0;
        if (sharedUnitId != null) count++;
        if (sharedResourceId != null) count++;
        if (sharedTechMapId != null) count++;
        if (sharedOrderId != null) count++;
        return count;
    }

    /**
     * Перевіряє чи контекст порожній
     */
    public boolean isEmpty() {
        return createdResources.isEmpty() && getSharedResourcesCount() == 0;
    }
}