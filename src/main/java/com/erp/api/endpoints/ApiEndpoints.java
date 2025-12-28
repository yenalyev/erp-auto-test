package com.erp.api.endpoints;

/**
 * Central repository for all API endpoints
 */
public class ApiEndpoints {

    // Base paths
    private static final String API_V1 = "/api/v1";

    // Inventory endpoints
    public static final String INCOMING = API_V1 + "/inventory/incoming";
    public static final String INCOMING_BY_ID = API_V1 + "/inventory/incoming/{id}";
    public static final String OUTGOING = API_V1 + "/inventory/outgoing";
    public static final String OUTGOING_BY_ID = API_V1 + "/inventory/outgoing/{id}";

    // Production endpoints
    public static final String PRODUCTION_ORDERS = API_V1 + "/production/orders";
    public static final String PRODUCTION_ORDER_BY_ID = API_V1 + "/production/orders/{id}";

    // Reporting endpoints
    public static final String REPORTS = API_V1 + "/reports";
    public static final String INVENTORY_REPORT = API_V1 + "/reports/inventory";
    public static final String PRODUCTION_REPORT = API_V1 + "/reports/production";

    // Common
    public static final String HEALTH = API_V1 + "/health";
}