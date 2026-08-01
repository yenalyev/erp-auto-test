package com.erp.tests.functional.notification;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.models.response.NotificationTemplateResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Notifications")
@Feature("REQ-NOTIF Templates")
public class NotificationTemplateTest extends BaseFunctionalTest {

    private NotificationFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new NotificationFixture(testContext, apiExecutor);
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 1)
    @TestCaseId("TC-NOTIF-010")
    @Story("AC-02 Seeded templates")
    @Severity(SeverityLevel.CRITICAL)
    public void getTemplatesContainsSeededCodes() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_TEMPLATE_GET_ALL, UserRole.ADMIN);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.NOTIFICATION_TEMPLATE_GET_ALL);

        List<NotificationTemplateResponse> templates = fixture.getTemplates(UserRole.ADMIN);
        Set<String> codes = templates.stream()
                .map(NotificationTemplateResponse::getCode)
                .collect(Collectors.toSet());

        assertThat(codes).contains(
                NotificationDataFactory.TEMPLATE_STOCK_RED,
                NotificationDataFactory.TEMPLATE_STOCK_YELLOW,
                NotificationDataFactory.TEMPLATE_TECH_MAP);

        assertThat(templates).allSatisfy(t -> {
            assertThat(t.getDescription()).isNotBlank();
            assertThat(t.getTemplate()).isNotBlank();
            assertThat(t.getState()).isIn("ENABLED", "DISABLED");
        });
    }
}
