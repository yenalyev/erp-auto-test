package com.erp.tests.auth;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
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
@Feature("Login Smoke")
public class LoginSmokeTest extends BaseTest {

    // -------------------------------------------------------------------------
    // TC-SMOKE-001: Session-based auth via Playwright (browser OAuth2 flow)
    // -------------------------------------------------------------------------

    @Test(priority = 1)
    @TestCaseId("TC-SMOKE-001")
    @Story("Session Auth via Playwright")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Пройти повний OAuth2 browser flow через Playwright і отримати JSESSIONID")
    public void testSessionLogin() {
        String username = UserRole.ADMIN.getUsername();
        String password = UserRole.ADMIN.getPassword();
        log.info("🎭 Session auth smoke: user={}", username);

        Map<String, String> cookies = authService.getSessionForUser(username, password);

        assertThat(cookies)
                .as("Cookies не повинні бути порожніми після логіну")
                .isNotEmpty();
        assertThat(cookies)
                .as("JSESSIONID повинен бути присутній у cookies після OAuth2 flow")
                .containsKey("JSESSIONID");
        assertThat(cookies.get("JSESSIONID"))
                .as("JSESSIONID не повинен бути порожнім")
                .isNotBlank();

        Allure.parameter("User", username);
        Allure.parameter("Cookies", cookies.keySet().toString());
        log.info("✅ Session auth OK — JSESSIONID={}", cookies.get("JSESSIONID").substring(0, 8) + "...");
    }

    // -------------------------------------------------------------------------
    // TC-SMOKE-003: Захищений ендпоінт доступний з отриманою сесією
    // -------------------------------------------------------------------------

    @Test(priority = 2, dependsOnMethods = "testSessionLogin")
    @TestCaseId("TC-SMOKE-002")
    @Story("Session Auth via Playwright")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Викликати захищений API-ендпоінт з JSESSIONID і отримати 200 OK")
    public void testProtectedEndpointWithSession() {
        String username = UserRole.ADMIN.getUsername();
        String password = UserRole.ADMIN.getPassword();
        log.info("🔒 Protected endpoint smoke: {}", ApiEndpointDefinition.RESOURCE_GET_ALL.getPath());

        Map<String, String> cookies = authService.getSessionForUser(username, password);

        Response response = given()
                .baseUri(ConfigProvider.getBackendUrl())
                .cookies(cookies)
                .contentType("application/json")
                .when()
                .get(ApiEndpointDefinition.RESOURCE_GET_ALL.getPath())
                .then()
                .extract()
                .response();

        assertThat(response.statusCode())
                .as("Захищений ендпоінт повинен повернути 200 OK для авторизованої сесії")
                .isEqualTo(200);

        Allure.parameter("Endpoint", ApiEndpointDefinition.RESOURCE_GET_ALL.getPath());
        Allure.parameter("Status", response.statusCode());
        log.info("✅ Protected endpoint OK — status={}", response.statusCode());
    }
}
