package com.erp.utils.config;

import lombok.extern.slf4j.Slf4j;
import org.aeonbits.owner.ConfigFactory;

@Slf4j
public class ConfigProvider {

    private static TestConfig config;

    static {
        initConfig();
    }

    private static void initConfig() {
        String env = System.getProperty("env", "debug");
        log.info("Loading configuration for environment: {}", env);

        System.setProperty("env", env);
        config = ConfigFactory.create(TestConfig.class, System.getProperties());

        log.info("✅ Configuration loaded successfully");
        log.info("Base URL: {}", config.baseUrl());
        log.info("Keycloak URL: {}", config.keycloakUrl());
        log.info("Auth Username: {}", config.authUsername());
    }

    public static TestConfig getConfig() {
        return config;
    }

    // Convenience methods
    public static String getBaseUrl() {
        return config.baseUrl();
    }

    public static String getKeycloakUrl() {
        return config.keycloakUrl();
    }

    public static String getKeycloakRealm() {
        return config.keycloakRealm();
    }

    public static String getKeycloakClientId() {
        return config.keycloakClientId();
    }

    public static String getKeycloakClientSecret() {
        return config.keycloakClientSecret();
    }

    public static String getAuthUsername() {
        return config.authUsername();
    }

    public static String getAuthPassword() {
        return config.authPassword();
    }

    // Database methods
    public static String getDbUrl() {
        return config.dbUrl();
    }

    public static String getDbUsername() {
        return config.dbUsername();
    }

    public static String getDbPassword() {
        return config.dbPassword();
    }

    public static boolean useDatabase() {
        return config.useDatabase();
    }

    public static boolean verboseLogging() {
        return config.verboseLogging();
    }

    public static int getTimeout() {
        return config.timeout();
    }

    public static void reload() {
        initConfig();
    }

    public static String getGoogleSheetsSpreadsheetId() {
        return config.googleSheetsSpreadsheetId();
    }

    public static boolean isGoogleSheetsEnabled() {
        return config.googleSheetsEnabled();
    }
}