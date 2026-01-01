package com.erp.tests;

import com.erp.services.CleanupService;
import com.erp.utils.TestcontainersManager;
import com.erp.utils.auth.AuthService;
import com.erp.utils.config.ConfigReader;
import com.erp.utils.helpers.DatabaseHelper;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class BaseTest {

    protected static RequestSpecification requestSpec;
    protected static AuthService authService;
    protected static CleanupService cleanupService;
    protected static DatabaseHelper dbHelper;

    private static String baseUrl;
    private static String authToken;
    private static boolean isTestcontainersMode;
    private static boolean useDocker;  // ✅ Додано

    // Зберігаємо створені ресурси для cleanup
    protected List<String> createdItemIds = new ArrayList<>();
    protected List<String> createdOrderIds = new ArrayList<>();

    @BeforeSuite(alwaysRun = true)
    public void globalSetup() {
        log.info("🚀 Starting test suite setup...");

        // Читаємо конфігурацію
        String profile = System.getProperty("profile", "debug");  // ✅ За замовчуванням debug (без Docker)
        log.info("📋 Running with profile: {}", profile);

        // Визначаємо чи використовувати Docker
        useDocker = Boolean.parseBoolean(System.getProperty("use.docker", "false"));  // ✅ Додано
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
                baseUrl = ConfigReader.getProperty("base.url");
                isTestcontainersMode = false;
            }
        } else {
            log.info("📝 Running WITHOUT Testcontainers (using config from properties)");
            baseUrl = ConfigReader.getProperty("base.url");
        }

        log.info("🌐 Base URL: {}", baseUrl);

        // Ініціалізуємо сервіси
        authService = new AuthService(baseUrl);
        cleanupService = new CleanupService(baseUrl);

        // Database Helper тільки якщо потрібен
        if (shouldInitializeDatabase()) {
            dbHelper = new DatabaseHelper();
        }

        // Отримуємо токен авторизації
        authToken = authenticateUser();

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

        log.info("✅ Test suite cleanup completed");
    }

    @BeforeClass(alwaysRun = true)
    public void classSetup() {
        log.info("📦 Setting up test class: {}", this.getClass().getSimpleName());
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

        // Оновлюємо токен якщо потрібно (перевіряємо expiration)
        if (authService.isTokenExpired(authToken)) {
            log.info("🔄 Token expired, refreshing...");
            authToken = authenticateUser();
            updateRequestSpecWithToken();
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
     * Перевірка чи потрібно ініціалізувати Database Helper
     */
    private boolean shouldInitializeDatabase() {
        String profile = System.getProperty("profile", "debug");
        String useDb = ConfigReader.getProperty("use.database", "false");

        // Database потрібен для local (з Testcontainers) або якщо явно вказано
        return isTestcontainersMode || "true".equals(useDb) || "local".equals(profile);
    }

    /**
     * Автентифікація через Keycloak
     */
    @Step("Authenticate user and get access token")
    private String authenticateUser() {
        String username = ConfigReader.getProperty("auth.username", "test-user");
        String password = ConfigReader.getProperty("auth.password", "test-password");

        log.info("🔐 Authenticating user: {}", username);

        try {
            String token = authService.getAccessToken(username, password);
            log.info("✅ Authentication successful");
            return token;
        } catch (Exception e) {
            log.error("❌ Authentication failed: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate", e);
        }
    }

    /**
     * Налаштування RestAssured
     */
    private void configureRestAssured() {
        RestAssured.baseURI = baseUrl;

        requestSpec = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .setRelaxedHTTPSValidation()
                .log(LogDetail.ALL)
                .build();

        // Додаємо фільтри для логування
        boolean verboseLogging = Boolean.parseBoolean(
                ConfigReader.getProperty("logging.verbose", "true"));

        if (verboseLogging) {
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
        String profile = System.getProperty("profile", "debug");

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

        // Видаляємо створені items
        for (String itemId : createdItemIds) {
            try {
                cleanupService.deleteItem(itemId, authToken);
                log.info("🗑️  Deleted item: {}", itemId);
            } catch (Exception e) {
                log.warn("⚠️  Failed to delete item {}: {}", itemId, e.getMessage());
            }
        }

        // Видаляємо створені orders
        for (String orderId : createdOrderIds) {
            try {
                cleanupService.deleteOrder(orderId, authToken);
                log.info("🗑️  Deleted order: {}", orderId);
            } catch (Exception e) {
                log.warn("⚠️  Failed to delete order {}: {}", orderId, e.getMessage());
            }
        }
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
}