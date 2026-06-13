package com.erp.tests;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.clients.SessionClient;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.services.CleanupService;
import com.erp.test_context.GlobalTestContext;
import com.erp.test_context.TestContext;
import com.erp.utils.TestcontainersManager;
import com.erp.utils.auth.AuthService;
import com.erp.utils.auth.PlaywrightSessionProvider;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseHelper;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
public abstract class BaseTest {
    // Кожен клас повинен мати свій екземпляр
    protected ApiExecutor apiExecutor;
    protected GlobalTestContext testContext;

   // protected static ApiExecutor apiExecutor;
    protected static RequestSpecification requestSpec;
    protected static AuthService authService;
    protected static CleanupService cleanupService;
    protected static DatabaseHelper dbHelper;

    private static String baseUrl;
    private static String authToken;
    private static boolean tokenAuthAvailable;
    private static boolean isTestcontainersMode;
    private static boolean useDocker;
    protected static SessionClient sessionClient;
    private static PlaywrightSessionProvider playwrightSessionProvider;

    /** Set when {@link #globalSetup()} aborts (e.g. DB pre-flight); blocks {@link #baseTestClassSetup()}. */
    private static volatile String suiteSkipReason;

    // Зберігаємо створені ресурси для cleanup
    protected List<String> createdItemIds = new ArrayList<>();
    protected List<String> createdOrderIds = new ArrayList<>();

    @BeforeSuite(alwaysRun = true)
    public void globalSetup() {
        log.info("🚀 Starting test suite setup...");

        // Читаємо конфігурацію
        String profile = System.getProperty("env", "debug");
        log.info("📋 Running with profile: {}", profile);

        // Визначаємо чи використовувати Docker
        useDocker = Boolean.parseBoolean(System.getProperty("use.docker", "false"));
        isTestcontainersMode = "local".equals(profile) && useDocker;

        if (isTestcontainersMode) {
            try {
                log.info("🐳 Starting Testcontainers...");
                TestcontainersManager.start();
                baseUrl = TestcontainersManager.getApplicationUrl();
                log.info("✅ Testcontainers started successfully");
            } catch (Exception e) {
                log.error("❌ Failed to start Testcontainers: {}", e.getMessage());
                log.warn("⚠️  Falling back to configuration from properties file");
                baseUrl = ConfigProvider.getBaseUrl();  // ✅ Змінено
                isTestcontainersMode = false;
            }
        } else {
            log.info("📝 Running WITHOUT Testcontainers (using config from properties)");
            baseUrl = ConfigProvider.getBackendUrl();
        }

        log.info("🌐 Backend URL (API + login): {}", baseUrl);
        log.info("🖥️  Frontend URL (UI): {}", ConfigProvider.getBaseUrl());

        // Ініціалізуємо сервіси
        authService = new AuthService(baseUrl);
        cleanupService = new CleanupService(baseUrl);

        // Playwright потрібен лише для remote-середовищ (dev, staging, debug).
        // Testcontainers-режим використовує прямий token-based auth через локальний Keycloak.
        if (!isTestcontainersMode) {
            try {
                playwrightSessionProvider = new PlaywrightSessionProvider(ConfigProvider.getBackendUrl());
                authService.setPlaywrightSessionProvider(playwrightSessionProvider);
            } catch (Exception e) {
                log.warn("⚠️  Playwright initialization failed — falling back to RestAssured auth flow");
                log.warn("    Причина: {}. Встанови Chromium: mvn exec:java@install-chromium", e.getMessage());
            }
        }

        sessionClient = new SessionClient();
        // Ініціалізуємо ApiExecutor (використовує sessionClient для запитів та authService для кешування сесій)
        apiExecutor = new ApiExecutor(sessionClient, authService);

        // Database Helper тільки якщо потрібен
        if (shouldInitializeDatabase()) {
            initDatabaseOrSkip();
        }

        // Отримуємо токен авторизації
        authToken = authenticateUser();
        tokenAuthAvailable = (authToken != null);

        // Налаштовуємо RestAssured
        configureRestAssured();

        log.info("✅ Test suite setup completed");
    }

    @AfterSuite(alwaysRun = true)
    public void globalTeardown() {
        log.info("🧹 Starting test suite cleanup...");

        // Зупиняємо Testcontainers
        if (isTestcontainersMode) {
            log.info("🐳 Stopping Testcontainers...");
            TestcontainersManager.stop();
        }

        // Закриваємо DB connection
        if (dbHelper != null) {
            dbHelper.closeConnection();
        }

        // Закриваємо Playwright браузер
        if (playwrightSessionProvider != null) {
            playwrightSessionProvider.close();
        }

        log.info("✅ Test suite cleanup completed");
    }


    @BeforeClass(alwaysRun = true)
    public void baseTestClassSetup() {
        if (suiteSkipReason != null) {
            throw new SkipException(suiteSkipReason);
        }
        log.info("📦 Setting up test class: {}", this.getClass().getSimpleName());
        log.info("Initializing Base Test Context for: {}", this.getClass().getSimpleName());

        // 1. Створюємо новий контекст для кожного тестового класу
        // Далі класи можуть перевизначити тестовий контекст
        this.testContext = new GlobalTestContext();

        // 2. Ініціалізуємо Executor саме з цим контекстом
        this.apiExecutor = new ApiExecutor(sessionClient, authService);
    }



    @AfterClass(alwaysRun = true)
    public void classTeardown() {
        log.info("🧹 Cleaning up test class: {}", this.getClass().getSimpleName());
    }

    @BeforeMethod(alwaysRun = true)
    public void testSetup() {
        log.info("▶️  Starting test method");

        // Очищуємо списки створених ресурсів
        createdItemIds.clear();
        createdOrderIds.clear();

        // Оновлюємо токен тільки якщо Keycloak був доступний під час globalSetup.
        // Якщо токен не вдалося отримати на старті — не повторюємо спроби перед кожним тестом.
        if (tokenAuthAvailable && authService.isTokenExpired(authToken)) {
            log.debug("Token expired, refreshing...");
            String refreshed = authenticateUser();
            if (refreshed != null) {
                authToken = refreshed;
                updateRequestSpecWithToken();
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    public void testTeardown() {
        log.info("🧹 Cleaning up after test method");

        // Cleanup створених ресурсів
        cleanupTestData();

        log.info("✅ Test method completed");
    }

    /**
     * Перевірка чи потрібно ініціалізувати Database Helper.
     * UI-тести перевизначають у {@link com.erp.tests.ui.BaseUITest}.
     */
    protected boolean shouldInitializeDatabase() {
        String profile = System.getProperty("env", "debug");

        // ✅ Використовуємо ConfigProvider
        return isTestcontainersMode || ConfigProvider.useDatabase() || "local".equals(profile);
    }

    /**
     * Initializes the database connection and validates it with a ping.
     * If the SSH tunnel or JDBC connection cannot be established, throws
     * {@link SkipException} so that the entire suite is marked as SKIPPED
     * rather than FAILED — preventing useless test runs against a broken DB.
     */
    private void initDatabaseOrSkip() {
        boolean sshMode = ConfigProvider.isSshEnabled();
        String phase = sshMode ? "SSH tunnel" : "database";

        log.info("Checking {} connectivity before running tests...", phase);

        try {
            dbHelper = new DatabaseHelper();
        } catch (Exception e) {
            String hint = buildDbHint(sshMode, e);
            String msg = String.format(
                    "Pre-flight check failed: cannot connect to the database%s.%n" +
                    "Reason: %s%n%s%n" +
                    "Fix the issue and re-run, or set use.database=false to skip DB checks.",
                    sshMode ? " via SSH tunnel" : "",
                    e.getMessage(),
                    hint
            );
            log.error(msg);
            suiteSkipReason = msg;
            throw new SkipException(msg);
        }

        if (!dbHelper.ping()) {
            String msg = "Pre-flight check failed: database is reachable but SELECT 1 returned no response. " +
                         "Check DB credentials and permissions.";
            log.error(msg);
            dbHelper.closeConnection();
            dbHelper = null;
            suiteSkipReason = msg;
            throw new SkipException(msg);
        }

        log.info("Database pre-flight check passed — connection is healthy");
    }

    private String buildDbHint(boolean sshMode, Exception cause) {
        if (sshMode) {
            String rootMessage = cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage();
            if (rootMessage != null && rootMessage.contains("Connection refused")) {
                return "Hint: SSH host is unreachable. Check ssh.host, ssh.port and firewall rules in dev.properties.";
            }
            if (rootMessage != null && (rootMessage.contains("Auth fail") || rootMessage.contains("publickey"))) {
                return "Hint: SSH key rejected. Verify ssh.key.path points to the correct private key file.";
            }
            if (cause instanceof IllegalStateException) {
                return "Hint: Fill in ssh.host, ssh.username and ssh.key.path in dev.properties.";
            }
            return "Hint: Check ssh.* properties in dev.properties.";
        }
        return "Hint: Check db.url, db.username and db.password in properties.";
    }

    /**
     * Автентифікація через Keycloak (token-based).
     * Повертає null якщо Keycloak недоступний — у такому разі suite продовжується
     * в session-only режимі (автентифікація через Playwright).
     */
    @Step("Authenticate user and get access token")
    private String authenticateUser() {
        if (playwrightSessionProvider != null) {
            log.info("🎭 Playwright session auth enabled — skipping Keycloak token grant");
            return null;
        }

        String username = ConfigProvider.getAuthUsername();
        String password = ConfigProvider.getAuthPassword();

        log.info("🔐 Authenticating user via Keycloak token grant: {}", username);

        try {
            String token = authService.getAccessToken(username, password);
            log.info("✅ Token authentication successful");
            return token;
        } catch (Exception e) {
            log.warn("⚠️  Token authentication unavailable (Keycloak not reachable at {}): {}",
                    ConfigProvider.getKeycloakUrl(), e.getMessage());
            log.warn("⚠️  Running in session-only mode — use Playwright-based auth (getSessionForUser)");
            return null;
        }
    }

    /**
     * Автентифікація конкретного користувача
     * Примітка: Зараз всі користувачі беруться з основного конфігу.
     * Якщо потрібна підтримка multiple users - додайте окремий properties файл.
     */
    @Step("Authenticate specific user: {userType}")
    protected String authenticateUser(String userType) {
        // ✅ Наразі використовуємо основного користувача
        // TODO: Додати підтримку різних типів користувачів якщо потрібно
        String username = ConfigProvider.getAuthUsername();
        String password = ConfigProvider.getAuthPassword();

        log.info("🔐 Authenticating user: {} (type: {})", username, userType);

        try {
            String token = authService.getAccessToken(username, password);
            log.info("✅ Authentication successful");
            return token;
        } catch (Exception e) {
            log.error("❌ Authentication failed: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate " + userType, e);
        }
    }

    /**
     * Налаштування RestAssured
     */
    private void configureRestAssured() {
        RestAssured.baseURI = baseUrl;

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .setRelaxedHTTPSValidation()
                .log(LogDetail.ALL);

        if (authToken != null) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }

        requestSpec = builder.build();

        // ✅ Використовуємо ConfigProvider
        if (ConfigProvider.verboseLogging()) {
            RestAssured.filters(
                    new RequestLoggingFilter(LogDetail.ALL),
                    new ResponseLoggingFilter(LogDetail.ALL)
            );
        }

        log.info("✅ RestAssured configured");
    }

    /**
     * Оновлення токена в RequestSpec
     */
    private void updateRequestSpecWithToken() {
        if (authToken == null) {
            log.debug("⏭️  Skipping requestSpec update — no token available (session-only mode)");
            return;
        }

        requestSpec = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .setRelaxedHTTPSValidation()
                .log(LogDetail.ALL)
                .build();

        log.info("✅ Token updated in RequestSpec");
    }

    /**
     * Очищення тестових даних після тесту
     */
    @Step("Cleanup test data")
    private void cleanupTestData() {
        String profile = System.getProperty("env", "debug");

        // В staging не видаляємо автоматично
        if ("staging".equals(profile)) {
            log.warn("⚠️  Staging mode - skipping automatic cleanup");
            if (!createdItemIds.isEmpty() || !createdOrderIds.isEmpty()) {
                log.info("📝 Created resources (manual cleanup required):");
                log.info("   Items: {}", createdItemIds);
                log.info("   Orders: {}", createdOrderIds);
            }
            return;
        }
    }

    //
    @Step("Верифікація цілісності даних: кількість записів не змінилася")
    protected <T> void assertDatabaseCountUnchanged(ApiEndpointDefinition getEndpoint,
                                                    long initialCount,
                                                    Class<T> responseClass,
                                                    Predicate<T> filter) {
        Response response = apiExecutor.execute(getEndpoint, UserRole.ADMIN);
        List<T> items = DatabaseIntegrityValidator.extractList(response, responseClass);

        long currentCount = items.stream().filter(filter).count();

        assertThat(currentCount)
                .as("Кількість записів у базі для " + responseClass.getSimpleName())
                .isEqualTo(initialCount);
    }

    /**
     * Допоміжний метод для реєстрації створених ресурсів
     */
    protected void registerCreatedItem(String itemId) {
        createdItemIds.add(itemId);
        log.debug("📝 Registered item for cleanup: {}", itemId);
    }

    protected void registerCreatedOrder(String orderId) {
        createdOrderIds.add(orderId);
        log.debug("📝 Registered order for cleanup: {}", orderId);
    }

    /**
     * Отримання токена (для використання в тестах)
     */
    protected String getAuthToken() {
        return authToken;
    }

    /**
     * Отримання RequestSpec (для використання в тестах)
     */
    protected RequestSpecification getRequestSpec() {
        return requestSpec;
    }

    /**
     * Database helper (для перевірок в БД)
     */
    protected DatabaseHelper getDbHelper() {
        return dbHelper;
    }

    /**
     * Expose the shared PlaywrightSessionProvider so UI-test subclasses can
     * reuse the already-launched Browser instance.
     */
    protected static PlaywrightSessionProvider getPlaywrightSessionProvider() {
        return playwrightSessionProvider;
    }
}