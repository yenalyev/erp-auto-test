package com.erp.tests.functional.notification;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.models.response.NotificationLogResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Notifications")
@Feature("REQ-NOTIF Journal")
public class NotificationJournalTest extends BaseFunctionalTest {

    private NotificationFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new NotificationFixture(testContext, apiExecutor);
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 1)
    @TestCaseId("TC-NOTIF-030")
    @Story("AC-04 Journal page")
    @Severity(SeverityLevel.NORMAL)
    public void getNotificationsJournalPage() {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NOTIFICATION_GET_PAGE,
                UserRole.ADMIN,
                Map.of("page", 0, "size", 20, "sort", "createdAt,desc"));

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.NOTIFICATION_GET_PAGE);

        assertThat(response.jsonPath().getList("content")).isNotNull();

        List<NotificationLogResponse> content = fixture.listNotifications(UserRole.ADMIN);
        for (NotificationLogResponse item : content) {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getTemplateCode()).isNotBlank();
            assertThat(item.getState()).isIn("PENDING", "SENDING", "SENT", "FAILED", "CANCELED");
            assertThat(item.getAttempt()).isNotNull().isGreaterThanOrEqualTo(0);
            assertThat(item.getRecipientNames()).isNotNull();
        }

        // Message body and phones must not leak in journal DTO
        List<Map<String, Object>> raw = response.jsonPath().getList("content");
        if (raw != null) {
            for (Map<String, Object> row : raw) {
                assertThat(row).doesNotContainKey("message");
                assertThat(row).doesNotContainKey("addressInfo");
            }
        }
    }
}
