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
    @Description("Remote: Playwright session (JSESSIONID). Local/Testcontainers: Keycloak JWT.")
    public void testSuccessfulLogin() {
        String username = ConfigProvider.getAuthUsername();
        String password = ConfigProvider.getAuthPassword();
        log.info("🔐 Testing login for user: {}", username);

        if (getPlaywrightSessionProvider() != null) {
            Map<String, String> cookies = authService.getSessionForUser(username, password);
            assertThat(cookies)
                    .as("Session cookies after login")
                    .isNotEmpty();
            assertThat(cookies)
                    .as("JSESSIONID after OAuth2 browser flow")
                    .containsKey("JSESSIONID");
            assertThat(cookies.get("JSESSIONID"))
                    .as("JSESSIONID value")
                    .isNotBlank();
            Allure.parameter("Auth mode", "session");
            Allure.parameter("Username", username);
            log.info("✅ Session login successful");
            return;
        }

        String token = getAuthToken();
        assertThat(token)
                .as("JWT token should not be null")
                .isNotNull();
        assertThat(token)
                .as("JWT should start with eyJ")
                .startsWith("eyJ");
        assertThat(token.split("\\."))
                .as("JWT should have 3 parts")
                .hasSize(3);

        Allure.parameter("Auth mode", "JWT");
        Allure.parameter("Username", username);
        log.info("✅ JWT login successful");
    }

    @Test(priority = 2)
    @TestCaseId("TC-AUTH-002")
    @Story("REQ-AUTH-002: Failed Login - Invalid Credentials")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify system rejects invalid credentials")
    public void testFailedLoginInvalidPassword() {
        String username = ConfigProvider.getAuthUsername();
        String invalidPassword = "wrong_password_123";

        log.info("🔐 Testing login with invalid password");

        try {
            if (getPlaywrightSessionProvider() != null) {
                authService.getSessionForUser(username, invalidPassword);
            } else {
                authService.getAccessToken(username, invalidPassword);
            }
            assertThat(false).as("Should throw exception for invalid credentials").isTrue();
        } catch (RuntimeException e) {
            assertLoginRejected(e);
            log.info("✅ Invalid credentials correctly rejected");
        }
    }

    @Test(priority = 3)
    @TestCaseId("TC-AUTH-003")
    @Story("REQ-AUTH-002: Failed Login - Invalid Credentials")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify system rejects non-existent user")
    public void testFailedLoginNonExistentUser() {
        String nonExistentUser = "non_existent_user_" + System.currentTimeMillis();
        String password = "any_password";

        log.info("🔐 Testing login with non-existent user");

        try {
            if (getPlaywrightSessionProvider() != null) {
                authService.getSessionForUser(nonExistentUser, password);
            } else {
                authService.getAccessToken(nonExistentUser, password);
            }
            assertThat(false).as("Should throw exception for non-existent user").isTrue();
        } catch (RuntimeException e) {
            assertLoginRejected(e);
            log.info("✅ Non-existent user correctly rejected");
        }
    }

    /** Playwright rejects via /users/me 401; Keycloak token grant uses "authentication failed". */
    private static void assertLoginRejected(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        assertThat(message)
                .as("Expected login rejection, got: %s", e.getMessage())
                .satisfiesAnyOf(
                        m -> assertThat(m).contains("authentication failed"),
                        m -> assertThat(m).contains("failed api check"),
                        m -> assertThat(m).contains("keycloak login failed"),
                        m -> assertThat(m).contains("status 401"));
    }

    @Test(priority = 4)
    @TestCaseId("TC-AUTH-004")
    @Story("REQ-AUTH-005: All Endpoints Require Authentication")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify API endpoint returns 401 when no token provided")
    public void testEndpointWithoutToken() {
        log.info("🔒 Testing API access without authentication token");

        Response response = given()
                .baseUri(ConfigProvider.getBackendUrl())
                .contentType("application/json")
                .when()
                .get(ApiEndpointDefinition.RESOURCE_GET_ALL.getPath())
                .then()
                .extract()
                .response();

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
        String invalidToken = "invalid.jwt.token";
        log.info("🔒 Testing API access with invalid token");

        Response response = given()
                .baseUri(ConfigProvider.getBackendUrl())
                .header("Authorization", "Bearer " + invalidToken)
                .contentType("application/json")
                .when()
                .get(ApiEndpointDefinition.RESOURCE_GET_ALL.getPath())
                .then()
                .extract()
                .response();

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
        log.info("🌐 Simulating browser login flow to get session cookies");

        Map<String, String> sessionCookies = authService.loginViaBrowserFlow(
                ConfigProvider.getAuthUsername(),
                ConfigProvider.getAuthPassword(),
                ApiEndpointDefinition.RESOURCE_GET_ALL.getPath()
        );

        log.info("🔓 Testing API access using obtained session");

        Response response = given()
                .baseUri(ConfigProvider.getBackendUrl())
                .cookies(sessionCookies)
                .contentType("application/json")
                .when()
                .get(ApiEndpointDefinition.RESOURCE_GET_ALL.getPath())
                .then()
                .extract()
                .response();

        assertThat(response.statusCode())
                .as("API should return 200 OK for a valid session; body=%s", response.asString())
                .isIn(200, 404);

        log.info("✅ Valid session accepted — status={}", response.statusCode());
    }
}
