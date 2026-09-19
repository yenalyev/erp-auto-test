package com.erp.enums;

/**
 * Stable business personas used by test cases.
 *
 * <p>The enum deliberately contains no credentials, environment ids or access-role names.
 * Additional DB-backed grants live in {@code business-roles.yml}; the fixture always adds
 * «Керівник локації» for regular non-admin actors.</p>
 */
public enum BusinessRole {
    BUSINESS_UNIT_OWNER,
    BUSINESS_UNIT_AND_PROJECT_OWNER,
    PRODUCTION_GROUP_DIRECTOR,
    CREW_STOCK_READER,
    CREW_INVENTORY_OPERATOR,
    UNIT_KOMIRNIK,
    /** Order operator with location-head access on its assigned locations. */
    ORDER_ADMIN
}
