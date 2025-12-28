package com.erp.utils.config;

import lombok.extern.slf4j.Slf4j;
import org.aeonbits.owner.ConfigFactory;

/**
 * Singleton provider for accessing test configuration
 */
@Slf4j
public class ConfigProvider {

    private static TestConfig config;

    static {
        initConfig();
    }

    private static void initConfig() {
        String env = System.getProperty("env", "dev");
        log.info("Loading configuration for environment: {}", env);

        System.setProperty("env", env);
        config = ConfigFactory.create(TestConfig.class, System.getProperties());

        log.info("Configuration loaded successfully");
        log.info("Base URL: {}", config.baseUrl());
    }

    public static TestConfig getConfig() {
        return config;
    }

    public static String getBaseUrl() {
        return config.baseUrl();
    }

    public static String getAuthToken() {
        return config.authToken();
    }

    public static int getTimeout() {
        return config.timeout();
    }
}