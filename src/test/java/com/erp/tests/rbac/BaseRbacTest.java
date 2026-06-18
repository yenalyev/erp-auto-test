package com.erp.tests.rbac;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.RbacFixture;
import com.erp.models.rbac.EndpointAccessRule;
import com.erp.test_context.ContextKey;
import com.erp.tests.BaseTest;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BaseRbacTest extends BaseTest {

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

        // 3. Формуємо запит
        RequestSpecification requestSpec = RestAssured.given()
                .cookies(getSessionForRole(role))
                .contentType(ContentType.JSON);

        if (requestBody != null) {
            requestSpec.body(requestBody);
        }

        // 4. Виконуємо запит за фінальним шляхом
        Response response = requestSpec.request(definition.getHttpMethod(), finalPath);

        log.info("📥 Response: {} ({} ms)", response.getStatusCode(), response.getTime());
        return response;
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