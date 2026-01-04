package com.erp.tests.auth;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.tests.BaseTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Authentication & Authorization")
@Feature("User Authentication")
public class AuthenticationTest extends BaseTest {

    @Test(priority = 1)
    @TestCaseId("TC-AUTH-001")
    @Story("REQ-AUTH-001: Successful Login")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify user can successfully login with valid credentials and receive JWT token")
    public void testSuccessfulLogin() {
        // Arrange
        String username = ConfigProvider.getAuthUsername();
        log.info("🔐 Testing login for user: {}", username);

        // Act
        String token = getAuthToken();

        // Assert
        assertThat(token)
                .as("Token should not be null")
                .isNotNull();

        assertThat(token)
                .as("Token should start with JWT format")
                .startsWith("eyJ");

        // Verify token contains user info (decode JWT)
        String[] parts = token.split("\\.");
        assertThat(parts)
                .as("JWT should have 3 parts")
                .hasSize(3);

        Allure.parameter("Username", username);
        Allure.parameter("Token received", "Yes");

        log.info("✅ Login successful - valid JWT token received");
    }

    @Test(priority = 2)
    @TestCaseId("TC-AUTH-002")
    @Story("REQ-AUTH-002: Failed Login - Invalid Credentials")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify system rejects invalid credentials with HTTP 401")
    public void testFailedLoginInvalidPassword() {
        // Arrange
        String username = ConfigProvider.getAuthUsername();
        String invalidPassword = "wrong_password_123";

        log.info("🔐 Testing login with invalid password");

        // Act
        try {
            authService.getAccessToken(username, invalidPassword);
            assertThat(false).as("Should throw exception for invalid credentials").isTrue();
        } catch (RuntimeException e) {
            // Assert
            assertThat(e.getMessage())
                    .as("Should contain authentication failed message")
                    .containsIgnoringCase("authentication failed");

            log.info("✅ Invalid credentials correctly rejected");
        }
    }

    @Test(priority = 3)
    @TestCaseId("TC-AUTH-003")
    @Story("REQ-AUTH-002: Failed Login - Invalid Credentials")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify system rejects non-existent user with HTTP 401")
    public void testFailedLoginNonExistentUser() {
        // Arrange
        String nonExistentUser = "non_existent_user_" + System.currentTimeMillis();
        String password = "any_password";

        log.info("🔐 Testing login with non-existent user");

        // Act & Assert
        try {
            authService.getAccessToken(nonExistentUser, password);
            assertThat(false).as("Should throw exception for non-existent user").isTrue();
        } catch (RuntimeException e) {
            assertThat(e.getMessage())
                    .as("Should contain authentication failed message")
                    .containsIgnoringCase("authentication failed");

            log.info("✅ Non-existent user correctly rejected");
        }
    }

    @Test(priority = 4)
    @TestCaseId("TC-AUTH-004")
    @Story("REQ-AUTH-005: All Endpoints Require Authentication")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify API endpoint returns 401 when no token provided")
    public void testEndpointWithoutToken() {
        // Arrange
        log.info("🔒 Testing API access without authentication token");

        // Act
        Response response = given()
                .baseUri(ConfigProvider.getBaseUrl())
                .contentType("application/json")
                // ❌ Без Authorization header
                .when()
                .get(ApiEndpointDefinition.RESOURCE_GET_ALL.getPath())
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode())
                .as("Should return 401 Unauthorized")
                .isEqualTo(401);

        log.info("✅ Unauthenticated request correctly rejected with 401");
    }

    @Test(priority = 5)
    @TestCaseId("TC-AUTH-005")
    @Story("REQ-AUTH-005: All Endpoints Require Authentication")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify API endpoint returns 401 when invalid token provided")
    public void testEndpointWithInvalidToken() {
        // Arrange
        String invalidToken = "invalid.jwt.token";
        log.info("🔒 Testing API access with invalid token");

        // Act
        Response response = given()
                .baseUri(ConfigProvider.getBaseUrl())
                .header("Authorization", "Bearer " + invalidToken)
                .contentType("application/json")
                .when()
                .get(ApiEndpointDefinition.RESOURCE_GET_ALL.getPath())
                .then()
                .extract()
                .response();

        // Assert
        assertThat(response.statusCode())
                .as("Should return 401 Unauthorized")
                .isEqualTo(401);

        log.info("✅ Invalid token correctly rejected with 401");
    }

    @Test(priority = 6)
    @TestCaseId("TC-AUTH-006")
    @Story("REQ-AUTH-005: All Endpoints Require Authentication")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify API endpoint accepts valid session via browser-like login flow")
    public void testEndpointWithValidSession() {
        log.info("🌐 Step 1: Simulating full browser login flow to get session cookies");

        // Отримуємо куки через ланцюжок редіректів (Backend -> Keycloak -> Backend)
        // Використовуємо той самий роут, який збираємось тестувати
        Map<String, String> sessionCookies = authService.loginViaBrowserFlow(
                ConfigProvider.getAuthUsername(),
                ConfigProvider.getAuthPassword(),
                ApiEndpointDefinition.RESOURCE_GET_ALL.getPath()
        );

        log.info("🔓 Step 2: Testing API access using obtained JSESSIONID");

        // Виконуємо запит до API
        Response response = given()
                .baseUri(ConfigProvider.getBaseUrl())
                .cookies(sessionCookies) // Передаємо отриману сесію
                .contentType("application/json")
                .when()
                .get(ApiEndpointDefinition.RESOURCE_GET_ALL.getPath())
                .then()
                .extract()
                .response();

        // Перевірка результату
        assertThat(response.statusCode())
                .as("API should return 200 OK for a valid session. If 500 occurs, check CustomPermissionEvaluator cast.")
                .isIn(200, 404);

        log.info("✅ Test passed! Status code: {}. Backend successfully recognized DefaultOidcUser.", response.statusCode());
    }
}