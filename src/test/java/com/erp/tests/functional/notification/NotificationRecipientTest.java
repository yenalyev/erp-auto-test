package com.erp.tests.functional.notification;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.request.NotificationRecipientRequest;
import com.erp.models.response.NotificationRecipientResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Notifications")
@Feature("REQ-NOTIF Recipients")
public class NotificationRecipientTest extends BaseFunctionalTest {

    private NotificationFixture fixture;
    private NotificationRecipientResponse sharedRecipient;
    private String sharedPhone;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new NotificationFixture(testContext, apiExecutor);
        fixture.prepareContext();
        sharedPhone = NotificationDataFactory.randomPhone();
        sharedRecipient = fixture.createRecipient(UserRole.ADMIN,
                NotificationDataFactory.recipient(
                        NotificationDataFactory.uniqueCaption(),
                        sharedPhone,
                        NotificationDataFactory.STATE_ACTIVE));
        SchemaRegistry.logSchemaCoverage();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            log.warn("Staging mode — skipping notification recipient cleanup");
            return;
        }
        fixture.disableTrackedRecipients(UserRole.ADMIN);
    }

    @Test(priority = 1)
    @TestCaseId("TC-NOTIF-001")
    @Story("AC-01 Create recipient")
    @Severity(SeverityLevel.CRITICAL)
    public void createRecipientMasksAddressInfo() {
        String phone = NotificationDataFactory.randomPhone();
        NotificationRecipientRequest request = NotificationDataFactory.recipient(
                NotificationDataFactory.uniqueCaption(), phone, NotificationDataFactory.STATE_ACTIVE);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_CREATE, UserRole.ADMIN, request);
        assertThat(response.statusCode()).isBetween(200, 299);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.NOTIFICATION_RECIPIENT_CREATE);

        NotificationRecipientResponse created = response.as(NotificationRecipientResponse.class);
        fixture.trackRecipient(created.getId());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCaption()).isEqualTo(request.getCaption());
        assertThat(created.getState()).isEqualTo(NotificationDataFactory.STATE_ACTIVE);
        assertMaskedPhone(created.getAddressInfo(), phone);
    }

    @Test(priority = 2)
    @TestCaseId("TC-NOTIF-002")
    @Story("AC-01 Get recipient")
    @Severity(SeverityLevel.NORMAL)
    public void getRecipientByIdAndList() {
        NotificationRecipientResponse byId = fixture.getRecipientById(UserRole.ADMIN, sharedRecipient.getId());
        assertThat(byId.getId()).isEqualTo(sharedRecipient.getId());
        assertThat(byId.getCaption()).isEqualTo(sharedRecipient.getCaption());
        assertMaskedPhone(byId.getAddressInfo(), sharedPhone);

        List<NotificationRecipientResponse> all = fixture.getAllRecipients(UserRole.ADMIN);
        assertThat(all).anyMatch(r -> sharedRecipient.getId().equals(r.getId()));

        Response listResponse = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_GET_ALL, UserRole.ADMIN);
        SchemaRegistry.validateIfSuccess(listResponse, ApiEndpointDefinition.NOTIFICATION_RECIPIENT_GET_ALL);
    }

    @Test(priority = 3)
    @TestCaseId("TC-NOTIF-003")
    @Story("AC-01 Mask round-trip")
    @Severity(SeverityLevel.CRITICAL)
    public void updateWithMaskedAddressKeepsPhone() {
        NotificationRecipientResponse before = fixture.getRecipientById(UserRole.ADMIN, sharedRecipient.getId());
        String masked = before.getAddressInfo();

        NotificationRecipientResponse after = fixture.updateRecipient(
                UserRole.ADMIN,
                sharedRecipient.getId(),
                NotificationRecipientRequest.builder()
                        .id(sharedRecipient.getId())
                        .type(before.getType() != null
                                ? before.getType()
                                : NotificationDataFactory.TYPE_WHATSAPP)
                        .caption(before.getCaption())
                        .addressInfo(masked)
                        .state(NotificationDataFactory.STATE_ACTIVE)
                        .build());

        assertThat(after.getAddressInfo()).isEqualTo(masked);
        assertMaskedPhone(after.getAddressInfo(), sharedPhone);
    }

    @Test(priority = 4)
    @TestCaseId("TC-NOTIF-004")
    @Story("AC-01 Update phone")
    @Severity(SeverityLevel.NORMAL)
    public void updateWithNewPhoneChangesMask() {
        String newPhone = NotificationDataFactory.alternatePhone();
        NotificationRecipientResponse before = fixture.getRecipientById(UserRole.ADMIN, sharedRecipient.getId());

        NotificationRecipientResponse after = fixture.updateRecipient(
                UserRole.ADMIN,
                sharedRecipient.getId(),
                NotificationRecipientRequest.builder()
                        .id(sharedRecipient.getId())
                        .type(before.getType() != null
                                ? before.getType()
                                : NotificationDataFactory.TYPE_WHATSAPP)
                        .caption(before.getCaption())
                        .addressInfo(newPhone)
                        .state(NotificationDataFactory.STATE_ACTIVE)
                        .build());

        assertMaskedPhone(after.getAddressInfo(), newPhone);
        assertThat(after.getAddressInfo()).isNotEqualTo(before.getAddressInfo());
        sharedPhone = newPhone;
        sharedRecipient = after;
    }

    @Test(priority = 5)
    @TestCaseId("TC-NOTIF-005")
    @Story("AC-01 Disable recipient")
    @Severity(SeverityLevel.NORMAL)
    public void disableRecipient() {
        NotificationRecipientResponse target = fixture.createActiveRecipient(UserRole.ADMIN);
        fixture.disableRecipient(UserRole.ADMIN, target.getId());

        NotificationRecipientResponse after = fixture.getRecipientById(UserRole.ADMIN, target.getId());
        assertThat(after.getState()).isEqualTo(NotificationDataFactory.STATE_DISABLED);
    }

    @Test(priority = 6)
    @TestCaseId("TC-NOTIF-006")
    @Story("AC-01 Validation")
    @Severity(SeverityLevel.NORMAL)
    public void createWithBlankFieldsReturns400() {
        NotificationRecipientRequest blank = NotificationRecipientRequest.builder()
                .type(NotificationDataFactory.TYPE_WHATSAPP)
                .caption(" ")
                .addressInfo(" ")
                .state(NotificationDataFactory.STATE_ACTIVE)
                .build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_CREATE, UserRole.ADMIN, blank);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    private static void assertMaskedPhone(String masked, String original) {
        assertThat(masked).isNotBlank();
        assertThat(masked).contains("****");
        assertThat(masked).isNotEqualTo(original);
        if (original != null && original.length() >= 8) {
            assertThat(masked).startsWith(original.substring(0, 3));
            assertThat(masked).endsWith(original.substring(original.length() - 2));
        }
    }
}
