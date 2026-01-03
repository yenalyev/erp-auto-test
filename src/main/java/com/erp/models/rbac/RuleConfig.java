package com.erp.models.rbac;

import com.erp.enums.UserRole;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Конфігурація одного RBAC правила з YAML
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleConfig {

    /**
     * Endpoint URL (може містити {resourceId} placeholder)
     */
    private String endpoint;

    /**
     * HTTP метод (GET, POST, PUT, DELETE, PATCH)
     */
    private String method;

    /**
     * Опис правила
     */
    private String description;

    /**
     * Ролі які мають доступ
     */
    private List<UserRole> allowedRoles;

    /**
     * Ролі яким заборонено доступ
     */
    private List<UserRole> deniedRoles;

    /**
     * Чи потрібен динамічний ID з testContext
     */
    private boolean requiresId = false;

    /**
     * Чи потрібне тіло запиту (для POST/PUT)
     */
    private boolean requiresBody = false;

    /**
     * Тип тіла запиту (для вибору builder методу)
     * Наприклад: "createResource", "updateResource"
     */
    private String bodyType;
}