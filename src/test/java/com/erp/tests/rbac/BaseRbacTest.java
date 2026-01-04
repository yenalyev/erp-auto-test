package com.erp.tests.rbac;

import com.erp.enums.UserRole;
import com.erp.fixtures.ErpFixture;
import com.erp.models.rbac.EndpointAccessRule;
import com.erp.test_context.RbacTestContext;
import com.erp.tests.BaseTest;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BaseRbacTest extends BaseTest {

    protected final RbacTestContext testContext = new RbacTestContext();
    protected ErpFixture erpFixture;

    @BeforeClass(alwaysRun = true)
    public void rbacClassSetup() {
        this.erpFixture = new ErpFixture(testContext, apiExecutor);
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

    /**
     * Виконує HTTP запит з певною роллю (використовуючи session cookies)
     */
    @Step("Execute API request as role")
    public Response executeRequestAsRole(EndpointAccessRule rule, UserRole role, Object requestBody, String pathParam) {
        // Формуємо повний шлях з pathParam якщо є
        String fullPath = rule.getEndpoint();
        if (pathParam != null && !pathParam.isEmpty()) {
            fullPath = fullPath + "/" + pathParam;
        }
        log.info("📡 Executing: {} {} as {}", rule.getHttpMethod(), fullPath, role);

        Map<String, String> session = getSessionForRole(role);

        RequestSpecification requestSpec = RestAssured.given()
                .cookies(session)
                .contentType(ContentType.JSON);

        // Додаємо body якщо є
        if (requestBody != null) {
            requestSpec.body(requestBody);
        }

        // Виконуємо request в залежності від HTTP методу
        Response response;
        switch (rule.getHttpMethod()) {
            case Method.GET:
                response = requestSpec.get(fullPath);
                break;
            case Method.POST:
                response = requestSpec.post(fullPath);
                break;
            case Method.PUT:
                response = requestSpec.put(fullPath);
                break;
            case Method.DELETE:
                response = requestSpec.delete(fullPath);
                break;
            case Method.PATCH:
                response = requestSpec.patch(fullPath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + rule.getHttpMethod());
        }

        int statusCode = response.getStatusCode();
        log.info("📥 Response status: {} (took {}ms)",
                statusCode,
                response.getTime());

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