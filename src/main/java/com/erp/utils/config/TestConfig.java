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

    // Frontend SPA URL (UI tests)
    @Key("base.url")
    @DefaultValue("http://localhost:8080")
    String baseUrl();

    /** Backend root for OAuth login and REST API (e.g. https://host/server on dev). Falls back to base.url. */
    @Key("backend.url")
    @DefaultValue("")
    String backendUrl();

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

    /** Max wait (seconds) for Playwright actions and UI element visibility. */
    @Key("ui.timeout")
    @DefaultValue("30")
    int uiTimeout();

    // User Credentials
    @Key("user.admin.username")
    @DefaultValue("admin")
    String adminUsername();

    @Key("user.admin.password")
    @DefaultValue("")
    String adminPassword();

    @Key("user.owner1.username")
    @DefaultValue("owner1")
    String owner1Username();

    @Key("user.owner1.password")
    @DefaultValue("")
    String owner1Password();

    @Key("user.owner2.username")
    @DefaultValue("owner2")
    String owner2Username();

    @Key("user.owner2.password")
    @DefaultValue("")
    String owner2Password();

    @Key("user.owner3.username")
    @DefaultValue("owner3")
    String owner3Username();

    @Key("user.owner3.password")
    @DefaultValue("")
    String owner3Password();

    @Key("user.resource-viewer.username")
    @DefaultValue("wolf")
    String resourceViewerUsername();

    @Key("user.resource-viewer.password")
    @DefaultValue("")
    String resourceViewerPassword();

    @Key("user.accountant.username")
    @DefaultValue("accountant")
    String accountantUsername();

    @Key("user.accountant.password")
    @DefaultValue("")
    String accountantPassword();

    /** Crew-Manager test user (dev: argument, Keycloak Crew-Manager-ROLE). */
    @Key("user.unit.username")
    @DefaultValue("argument")
    String crewManagerUsername();

    @Key("user.unit.password")
    @DefaultValue("")
    String crewManagerPassword();

    /** Project production admin (Project-Production-ROLE + catalog CRUD). */
    @Key("user.project-admin.username")
    @DefaultValue("projectprod")
    String projectAdminUsername();

    @Key("user.project-admin.password")
    @DefaultValue("")
    String projectAdminPassword();

    /** Project production manager (Project-Production-ROLE on owner1 storage). */
    @Key("user.project-manager.username")
    @DefaultValue("projectprodab")
    String projectManagerUsername();

    @Key("user.project-manager.password")
    @DefaultValue("")
    String projectManagerPassword();

    /**
     * Mixed location permissions user (Owner full on A1/A2 + Viewer RO on B1/B2).
     * Bound via {@code var_business_unit_id} / {@code var_business_unit_id_ro} (CPMA-644).
     */
    @Key("user.location-mixed.username")
    @DefaultValue("locmixed")
    String locationMixedUsername();

    @Key("user.location-mixed.password")
    @DefaultValue("")
    String locationMixedPassword();

    // Storage IDs per owner role (resolved per environment)
    @Key("owner1.storage.id")
    @DefaultValue("1")
    long owner1StorageId();

    @Key("owner2.storage.id")
    @DefaultValue("2")
    long owner2StorageId();

    /** UNIT storage id for Crew-Manager user ({@code var_business_unit_id} in Keycloak). */
    @Key("unit.storage.id")
    @DefaultValue("77")
    long unitStorageId();

    /**
     * Second view-only location for LOCATION_MIXED (B2).
     * {@code 0} = auto-pick another UNIT from admin names at ensure time.
     */
    @Key("location-mixed.ro2.storage.id")
    @DefaultValue("0")
    long locationMixedRo2StorageId();

    /** A storage ID that is guaranteed not to exist; PostgreSQL never assigns negative IDs. */
    @Key("incorrect.storage.id")
    @DefaultValue("-1")
    long incorrectStorageId();

    /**
     * Order gathering owner (REQ-WMS-010). Empty username → fall back to owner2.
     * Storage id {@code 0} → fall back to {@code owner2.storage.id}.
     */
    @Key("order.gathering.username")
    @DefaultValue("")
    String orderGatheringUsername();

    @Key("order.gathering.password")
    @DefaultValue("")
    String orderGatheringPassword();

    @Key("order.gathering.storage.id")
    @DefaultValue("0")
    long orderGatheringStorageId();

    /**
     * Seed for {@code app_config.order_availability_root_storage}.
     * {@code 0} = skip upsert.
     */
    @Key("order.availability.root.storage.id")
    @DefaultValue("0")
    long orderAvailabilityRootStorageId();

    // Google Sheets
    @Key("google.sheets.spreadsheet.id")
    @DefaultValue("")
    String googleSheetsSpreadsheetId();

    @Key("google.sheets.enabled")
    @DefaultValue("false")
    boolean googleSheetsEnabled();

    // TCM integration
    @Key("tcm.enabled")
    @DefaultValue("false")
    boolean tcmEnabled();

    @Key("tcm.base.url")
    @DefaultValue("http://localhost:8080")
    String tcmBaseUrl();

    @Key("tcm.api.token")
    @DefaultValue("")
    String tcmApiToken();

    @Key("tcm.test.plan.id")
    @DefaultValue("0")
    long tcmTestPlanId();

    // SSH Tunnel Configuration
    @Key("ssh.enabled")
    @DefaultValue("false")
    boolean sshEnabled();

    @Key("ssh.host")
    @DefaultValue("")
    String sshHost();

    @Key("ssh.port")
    @DefaultValue("22")
    int sshPort();

    @Key("ssh.username")
    @DefaultValue("")
    String sshUsername();

    /** Absolute path to the private key file on the dev machine (e.g. C:/Users/me/.ssh/id_rsa). */
    @Key("ssh.key.path")
    @DefaultValue("")
    String sshKeyPath();

    /** SSH password — alternative to {@link #sshKeyPath()}. Use one of them. */
    @Key("ssh.password")
    @DefaultValue("")
    String sshPassword();

    /** Hostname of the PostgreSQL server as seen from the SSH server (usually localhost). */
    @Key("ssh.remote.db.host")
    @DefaultValue("localhost")
    String sshRemoteDbHost();

    @Key("ssh.remote.db.port")
    @DefaultValue("5432")
    int sshRemoteDbPort();

    /** Local port that the SSH tunnel will bind to on the dev machine. */
    @Key("ssh.local.port")
    @DefaultValue("5433")
    int sshLocalPort();

    // Bot integrations (whatsapp-bot, delivery-bot) — OAuth2 client_credentials
    @Key("bot.api.tests.enabled")
    @DefaultValue("true")
    boolean botApiTestsEnabled();

    @Key("bot.oauth.token.url")
    @DefaultValue("")
    String botOAuthTokenUrl();

    @Key("bot.oauth.client.id")
    @DefaultValue("inventory-bot")
    String botOAuthClientId();

    @Key("bot.oauth.client.secret")
    @DefaultValue("")
    String botOAuthClientSecret();

    /** OAuth2 grant_type ({@code GRANT_TYPE} in .env); same default as whatsapp-bot. */
    @Key("bot.oauth.grant.type")
    @DefaultValue("client_credentials")
    String botOAuthGrantType();

    /** Connect timeout for bot OAuth2 and internal API (seconds). */
    @Key("bot.api.timeout.connect.seconds")
    @DefaultValue("5")
    int botApiTimeoutConnectSeconds();

    /** Read timeout for bot OAuth2 and internal API (seconds). */
    @Key("bot.api.timeout.read.seconds")
    @DefaultValue("60")
    int botApiTimeoutReadSeconds();

    /** Max response body size for bot internal API GET (bytes). */
    @Key("bot.api.max.response.bytes")
    @DefaultValue("20971520")
    long botApiMaxResponseBytes();

    /** Max wall-clock time (seconds) for bot-facing API responses. */
    @Key("bot.api.max.response.seconds")
    @DefaultValue("30")
    int botApiMaxResponseSeconds();

    /** Full URL for whatsapp-bot sync ({@code GET_DATA_URL} in .env). */
    @Key("bot.whatsapp.data.url")
    @DefaultValue("")
    String botWhatsappDataUrl();

    /** Full URL for delivery-bot sync ({@code GET_RELOCATIONS_DATA_URL} in .env). */
    @Key("bot.delivery.data.url")
    @DefaultValue("")
    String botDeliveryDataUrl();

    /** Full URL for delivery-bot location structure ({@code GET_STRUCTURE_DATA_URL} in .env). */
    @Key("bot.delivery.structure.url")
    @DefaultValue("")
    String botDeliveryStructureUrl();
}