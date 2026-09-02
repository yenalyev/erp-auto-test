package com.erp.tests.rbac;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.RbacFixture;
import com.erp.models.rbac.EndpointAccessRule;
import com.erp.test_context.ContextKey;
import com.erp.tests.BaseTest;
import com.erp.utils.auth.SessionUnauthorizedRetry;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BaseRbacTest extends BaseTest {

    private static final Set<String> MULTIPART_JSON_ENDPOINTS = Set.of(
            ApiEndpointDefinition.INCIDENT_POST_CREATE.name(),
            ApiEndpointDefinition.PROJECT_PRODUCTION_POST_CREATE.name(),
            ApiEndpointDefinition.DEFECT_POST_CREATE.name(),
            ApiEndpointDefinition.DEFECT_PUT_UPDATE.name()
    );

    private static final ObjectMapper MULTIPART_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    protected RbacFixture rbacFixture;


    @BeforeClass(alwaysRun = true)
    public void rbacClassSetup() {
        this.rbacFixture = new RbacFixture(testContext, apiExecutor);
    }

    // Кеш сесій для кожної ролі (щоб не логінитись кожен раз)
    protected Map<UserRole, Map<String, String>> roleSessionCache = new ConcurrentHashMap<>();

    /**
     * Отримує session cookies для вказаної ролі (з кешу або логінитись)
     */
    @Step("Get session for role: {role}")
    protected Map<String, String> getSessionForRole(UserRole role) {
        if (role == UserRole.ANONYMOUS) {
            return new HashMap<>(); // Повертаємо порожню мапу кук
        }


        return roleSessionCache.computeIfAbsent(role, r -> {
            log.info("🔐 Getting session for role: {}", role);
            try {
                Map<String, String> cookies = authService.getSessionForUser(
                        role.getUsername(),
                        role.getPassword()
                );
                log.debug("✅ Session obtained for role: {}", role);
                return cookies;
            } catch (Exception e) {
                log.error("❌ Failed to get session for role: {}", role, e);
                throw new RuntimeException("Failed to authenticate as " + role, e);
            }
        });
    }

    /**
     * Отримує тільки JSESSIONID для вказаної ролі
     */
    protected String getJSessionIdForRole(UserRole role) {
        Map<String, String> cookies = getSessionForRole(role);
        return cookies.get("JSESSIONID");
    }

    // У класі BaseRbacTest

    /**
     * Виконує HTTP запит з певною роллю, автоматично підставляючи ID з контексту
     */
    @Step("Execute API request as role: {role} for {rule.endpointName}")
    public Response executeRequestAsRole(EndpointAccessRule rule, UserRole role, Object requestBody) {
        // 1. Отримуємо метадані ендпоїнта
        ApiEndpointDefinition definition = rule.getEndpointDefinition();

        // 2. Визначаємо фінальний шлях (з підставленим ID)
        String finalPath;

        if (definition.hasPathVariables()) {
            // Перевіряємо, чи вказано ключ у YAML
            if (rule.getContextKey() == null) {
                throw new IllegalStateException(String.format(
                        "❌ Помилка конфігурації: Ендпоїнт %s вимагає {id}, але в YAML не вказано contextKey",
                        rule.getEndpointName()));
            }

            // Дістаємо ID з контексту
            Object id = testContext.get(rule.getContextKey());

            if (id == null) {
                log.error("❌ Дані відсутні: Ключ {} порожній у контексті", rule.getContextKey());
                throw new RuntimeException("Test Setup Failed: ID not found via key " + rule.getContextKey());
            }

            if (definition.getPathVariablesCount() > 1) {
                Object storageId = testContext.get(ContextKey.OWNER_1_STORAGE_ID);
                if (storageId == null) {
                    storageId = com.erp.utils.config.ConfigProvider.getOwner1StorageId();
                }
                finalPath = definition.getPath(id, storageId);
            } else {
                finalPath = definition.getPath(id);
            }
        } else {
            finalPath = definition.getPathTemplate();
        }

        log.info("📡 [RBAC] {} {} | Role: {} | Key: {}",
                definition.getHttpMethod(), finalPath, role, rule.getContextKey());

        Response response = executeRbacRequest(definition, finalPath, role, requestBody);
        if (SessionUnauthorizedRetry.shouldRelogin(role, response.statusCode())) {
            log.warn("⚠️ RBAC {} returned 401 — re-login and retry once", role);
            evictRoleSession(role);
            response = executeRbacRequest(definition, finalPath, role, requestBody);
        }

        log.info("📥 Response: {} ({} ms)", response.getStatusCode(), response.getTime());
        return response;
    }

    private Response executeRbacRequest(ApiEndpointDefinition definition,
                                        String finalPath,
                                        UserRole role,
                                        Object requestBody) {
        if (MULTIPART_JSON_ENDPOINTS.contains(definition.name()) && requestBody != null) {
            return executeMultipartJsonRequest(definition, finalPath, role, requestBody);
        }
        RequestSpecification requestSpec = RestAssured.given()
                .cookies(getSessionForRole(role))
                .contentType(ContentType.JSON);
        if (requestBody != null) {
            requestSpec.body(requestBody);
        }
        return requestSpec.request(definition.getHttpMethod(), finalPath);
    }

    private void evictRoleSession(UserRole role) {
        roleSessionCache.remove(role);
        if (role != UserRole.ANONYMOUS) {
            authService.invalidateSession(role.getUsername(), role.getPassword());
        }
        if (apiExecutor != null) {
            apiExecutor.evictSessionForRole(role);
        }
    }

    private Response executeMultipartJsonRequest(ApiEndpointDefinition definition,
                                                 String finalPath,
                                                 UserRole role,
                                                 Object requestBody) {
        try {
            String json = MULTIPART_MAPPER.writeValueAsString(requestBody);
            return RestAssured.given()
                    .baseUri(ConfigProvider.getBackendUrl())
                    .accept(ContentType.JSON)
                    .cookies(getSessionForRole(role))
                    .multiPart(new io.restassured.builder.MultiPartSpecBuilder(json)
                            .controlName("request")
                            .mimeType("application/json")
                            .charset("UTF-8")
                            .build())
                    .when()
                    .request(definition.getHttpMethod(), finalPath)
                    .then()
                    .extract()
                    .response();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize RBAC multipart JSON part", e);
        }
    }

    /**
     * Очищує кеш сесій (використовувати після logout тестів)
     */
    @Step("Clear session cache")
    protected void clearSessionCache() {
        log.info("🗑️ Clearing role session cache");
        roleSessionCache.clear();
        authService.clearSessionCache();
    }

    /**
     * Перевіряє, чи сесія для ролі ще активна
     */
    protected boolean isSessionValidForRole(UserRole role) {
        Map<String, String> cookies = roleSessionCache.get(role);
        if (cookies == null) {
            return false;
        }
        return authService.isSessionValid(cookies);
    }

    /**
     * Логує інформацію про всі закешовані сесії
     */
    protected void logSessionCacheInfo() {
        log.info("📊 Session Cache Info:");
        roleSessionCache.forEach((role, cookies) -> {
            String jsessionId = cookies.get("JSESSIONID");
            log.info("   {}: {} (valid: {})",
                    role,
                    jsessionId,
                    authService.isSessionValid(cookies));
        });
    }
}