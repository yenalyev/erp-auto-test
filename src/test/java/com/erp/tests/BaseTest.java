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
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseHelper;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
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
    private static boolean isTestcontainersMode;
    private static boolean useDocker;
    protected static SessionClient sessionClient;

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
            baseUrl = ConfigProvider.getBaseUrl();  // ✅ Змінено
        }

        log.info("🌐 Base URL: {}", baseUrl);

        // Ініціалізуємо сервіси
        authService = new AuthService(baseUrl);
        cleanupService = new CleanupService(baseUrl);

        sessionClient = new SessionClient();
        // Ініціалізуємо ApiExecutor (використовує sessionClient для запитів та authService для кешування сесій)
        apiExecutor = new ApiExecutor(sessionClient, authService);

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
    public void baseTestClassSetup() {
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
        String profile = System.getProperty("env", "debug");

        // ✅ Використовуємо ConfigProvider
        return isTestcontainersMode || ConfigProvider.useDatabase() || "local".equals(profile);
    }

    /**
     * Автентифікація через Keycloak
     */
    @Step("Authenticate user and get access token")
    private String authenticateUser() {
        // ✅ Отримуємо credentials з ConfigProvider
        String username = ConfigProvider.getAuthUsername();
        String password = ConfigProvider.getAuthPassword();

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

        requestSpec = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .setRelaxedHTTPSValidation()
                .log(LogDetail.ALL)
                .build();

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
        // Використовуємо responseClass для універсальності
        List<T> items = response.jsonPath().getList("", responseClass);

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
}