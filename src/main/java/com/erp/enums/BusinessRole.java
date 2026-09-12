package com.erp.enums;

/**
 * Stable business personas used by test cases.
 *
 * <p>The enum deliberately contains no credentials, environment ids or Keycloak role names.
 * Keycloak mappings live in {@code business-roles.yml}.</p>
 */
public enum BusinessRole {
    BUSINESS_UNIT_OWNER,
    CREW_STOCK_READER,
    CREW_INVENTORY_OPERATOR,
    UNIT_KOMIRNIK
}
