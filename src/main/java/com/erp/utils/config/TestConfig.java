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

    // Storage IDs per owner role (resolved per environment)
    @Key("owner1.storage.id")
    @DefaultValue("1")
    long owner1StorageId();

    @Key("owner2.storage.id")
    @DefaultValue("2")
    long owner2StorageId();

    /** A storage ID that is guaranteed not to exist; PostgreSQL never assigns negative IDs. */
    @Key("incorrect.storage.id")
    @DefaultValue("-1")
    long incorrectStorageId();

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
}