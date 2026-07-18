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
        String env = normalizeEnv(System.getProperty("env", "debug"));
        log.info("Loading configuration for environment: {}", env);

        System.setProperty("env", env);
        DotEnvLoader.loadForProfile(env);
        config = ConfigFactory.create(TestConfig.class, System.getProperties());

        log.info("✅ Configuration loaded successfully");
        log.info("Base URL (frontend): {}", config.baseUrl());
        log.info("Backend URL (API): {}", getBackendUrl());
        log.info("Keycloak URL: {}", config.keycloakUrl());
        log.info("Auth Username: {}", config.authUsername());
        if (config.botApiTestsEnabled()) {
            log.info("Bot OAuth: tokenUrl={}, clientId={}, secretConfigured={}",
                    config.botOAuthTokenUrl(),
                    config.botOAuthClientId(),
                    !config.botOAuthClientSecret().isBlank());
        }
    }

    public static TestConfig getConfig() {
        return config;
    }

    // Convenience methods
    public static String getBaseUrl() {
        return config.baseUrl();
    }

    /**
     * Backend root used for {@code /login} and as RestAssured base URI ({@code /api/v1/...} paths).
     * On dev/stage this is often {@code {base.url}/server}; locally equals {@code base.url}.
     */
    public static String getBackendUrl() {
        String backend = config.backendUrl();
        if (backend == null || backend.isBlank()) {
            return config.baseUrl();
        }
        return backend.replaceAll("/+$", "");
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

    public static int getUiTimeoutSeconds() {
        return config.uiTimeout();
    }

    /** @deprecated use {@link #getUiTimeoutSeconds()} */
    @Deprecated
    public static int getUiFilterWaitTimeoutSeconds() {
        return getUiTimeoutSeconds();
    }

    public static void reload() {
        initConfig();
    }

    /** Maps CLI aliases to classpath config profile names ({@code config/{profile}.properties}). */
    private static String normalizeEnv(String env) {
        if ("stage".equalsIgnoreCase(env)) {
            return "staging";
        }
        return env;
    }

    // User credentials
    public static String getAdminUsername()  { return config.adminUsername(); }
    public static String getAdminPassword()  { return config.adminPassword(); }
    public static String getOwner1Username() { return config.owner1Username(); }
    public static String getOwner1Password() { return config.owner1Password(); }
    public static String getOwner2Username() { return config.owner2Username(); }
    public static String getOwner2Password() { return config.owner2Password(); }
    public static String getOwner3Username() { return config.owner3Username(); }
    public static String getOwner3Password() { return config.owner3Password(); }
    public static String getResourceViewerUsername() { return config.resourceViewerUsername(); }
    public static String getResourceViewerPassword() { return config.resourceViewerPassword(); }
    public static String getAccountantUsername() { return config.accountantUsername(); }
    public static String getAccountantPassword() { return config.accountantPassword(); }
    public static String getCrewManagerUsername() { return config.crewManagerUsername(); }
    public static String getCrewManagerPassword() { return config.crewManagerPassword(); }

    // Storage IDs per owner
    public static long getOwner1StorageId()     { return config.owner1StorageId(); }
    public static long getOwner2StorageId()     { return config.owner2StorageId(); }
    public static long getUnitStorageId()       { return config.unitStorageId(); }
    public static long getIncorrectStorageId()  { return config.incorrectStorageId(); }

    public static String getGoogleSheetsSpreadsheetId() {
        return config.googleSheetsSpreadsheetId();
    }

    public static boolean isGoogleSheetsEnabled() {
        return config.googleSheetsEnabled();
    }

    public static boolean isTcmEnabled() {
        return isTcmReportingEnabled();
    }

    public static boolean isTcmReportingEnabled() {
        if (!config.tcmEnabled() || config.tcmApiToken().isBlank()) {
            return false;
        }
        if (getTcmFeatureId() != null || getTcmAcId() != null) {
            return true;
        }
        return config.tcmTestPlanId() > 0;
    }

    public static Long getTcmFeatureId() {
        return parseLongProperty("tcm.feature.id");
    }

    public static Long getTcmAcId() {
        return parseLongProperty("tcm.ac.id");
    }

    private static Long parseLongProperty(String key) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            log.warn("Invalid {} value: {}", key, raw);
            return null;
        }
    }

    public static String getTcmBaseUrl() {
        return config.tcmBaseUrl();
    }

    public static String getTcmApiToken() {
        return config.tcmApiToken();
    }

    public static Long getTcmProjectId() {
        return parseLongProperty("tcm.project.id");
    }

    public static String getTcmRemoteRunId() {
        String raw = System.getProperty("tcm.remote.run.id");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    public static long getTcmTestPlanId() {
        return config.tcmTestPlanId();
    }

    // SSH Tunnel methods
    public static boolean isSshEnabled() {
        return config.sshEnabled();
    }

    public static String getSshHost() {
        return config.sshHost();
    }

    public static int getSshPort() {
        return config.sshPort();
    }

    public static String getSshUsername() {
        return config.sshUsername();
    }

    public static String getSshKeyPath() {
        return config.sshKeyPath();
    }

    public static String getSshPassword() {
        return config.sshPassword();
    }

    public static String getSshRemoteDbHost() {
        return config.sshRemoteDbHost();
    }

    public static int getSshRemoteDbPort() {
        return config.sshRemoteDbPort();
    }

    public static int getSshLocalPort() {
        return config.sshLocalPort();
    }

    public static boolean isBotApiTestsEnabled() {
        return config.botApiTestsEnabled()
                && !getBotOAuthTokenUrl().isBlank()
                && !getBotOAuthClientSecret().isBlank();
    }

    public static String getBotOAuthTokenUrl() {
        return config.botOAuthTokenUrl();
    }

    public static String getBotOAuthClientId() {
        return config.botOAuthClientId();
    }

    public static String getBotOAuthClientSecret() {
        return config.botOAuthClientSecret();
    }

    public static String getBotOAuthGrantType() {
        return config.botOAuthGrantType();
    }

    public static int getBotApiConnectTimeoutMs() {
        return config.botApiTimeoutConnectSeconds() * 1000;
    }

    public static int getBotApiReadTimeoutMs() {
        return config.botApiTimeoutReadSeconds() * 1000;
    }

    public static long getBotApiMaxResponseBytes() {
        return config.botApiMaxResponseBytes();
    }

    public static int getBotApiMaxResponseSeconds() {
        return config.botApiMaxResponseSeconds();
    }

    public static String getBotWhatsappDataUrl() {
        String configured = config.botWhatsappDataUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        return getBackendUrl() + com.erp.api.endpoints.ApiEndpointDefinition.INTERNAL_STORAGE_GET_ALL.getPath();
    }

    public static String getBotDeliveryDataUrl() {
        String configured = config.botDeliveryDataUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        return getBackendUrl() + com.erp.api.endpoints.ApiEndpointDefinition.INTERNAL_RELOCATION_GET_ALL.getPath();
    }
}