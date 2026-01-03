package com.erp.utils.config;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "system:env",
        "classpath:config/${env}.properties",
        "classpath:config/default.properties"
})
public interface TestConfig extends Config {

    // Backend API
    @Key("base.url")
    @DefaultValue("http://localhost:8080")
    String baseUrl();

    // Keycloak Authentication
    @Key("auth.keycloak.url")
    @DefaultValue("http://localhost:8180")
    String keycloakUrl();

    @Key("auth.keycloak.realm")
    @DefaultValue("erp-realm")
    String keycloakRealm();

    @Key("auth.keycloak.client.id")
    @DefaultValue("erp-client")
    String keycloakClientId();

    @Key("auth.keycloak.client.secret")
    @DefaultValue("")
    String keycloakClientSecret();

    // Test User Credentials
    @Key("auth.username")
    @DefaultValue("test-user")
    String authUsername();

    @Key("auth.password")
    @DefaultValue("test123")
    String authPassword();

    // Database Configuration
    @Key("db.url")
    @DefaultValue("jdbc:postgresql://localhost:5432/erp_db")
    String dbUrl();

    @Key("db.username")
    @DefaultValue("postgres")
    String dbUsername();

    @Key("db.password")
    @DefaultValue("postgres")
    String dbPassword();

    // Other Settings
    @Key("use.database")
    @DefaultValue("false")
    boolean useDatabase();

    @Key("logging.verbose")
    @DefaultValue("true")
    boolean verboseLogging();

    @Key("api.timeout")
    @DefaultValue("30")
    int timeout();

    // Google Sheets
    @Key("google.sheets.spreadsheet.id")
    @DefaultValue("")
    String googleSheetsSpreadsheetId();

    @Key("google.sheets.enabled")
    @DefaultValue("false")
    boolean googleSheetsEnabled();
}