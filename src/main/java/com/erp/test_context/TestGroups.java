package com.erp.test_context;

/**
 * Constants for TestNG groups
 */
public class TestGroups {

    // Priority groups
    public static final String SMOKE = "smoke";
    public static final String CRITICAL = "critical";
    public static final String REGRESSION = "regression";
    public static final String EXTENDED = "extended";

    // Type groups
    public static final String POSITIVE = "positive";
    public static final String NEGATIVE = "negative";
    public static final String VALIDATION = "validation";
    public static final String SECURITY = "security";

    // Module groups
    public static final String INVENTORY = "inventory";
    public static final String INCOMING = "incoming";
    public static final String OUTGOING = "outgoing";
    public static final String PRODUCTION = "production";
    public static final String REPORTING = "reporting";

    // Dependency groups
    public static final String STANDALONE = "standalone";
    public static final String INTEGRATION = "integration";
    public static final String E2E = "e2e";
}