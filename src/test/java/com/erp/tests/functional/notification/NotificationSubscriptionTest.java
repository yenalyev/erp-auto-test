package com.erp.tests.functional.notification;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.request.NotificationSubscriptionRequest;
import com.erp.models.response.NotificationRecipientResponse;
import com.erp.models.response.NotificationSubscriptionResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
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
@Feature("REQ-NOTIF Subscriptions")
public class NotificationSubscriptionTest extends BaseFunctionalTest {

    private NotificationFixture fixture;
    private NotificationRecipientResponse recipient;
    private Long storage1;
    private Long storage2;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new NotificationFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storage1 = ConfigProvider.getOwner1StorageId();
        storage2 = ConfigProvider.getOwner2StorageId();
        testContext.set(ContextKey.OWNER_1_STORAGE_ID, storage1);
        testContext.set(ContextKey.OWNER_2_STORAGE_ID, storage2);
        recipient = fixture.createActiveRecipient(UserRole.ADMIN);
        SchemaRegistry.logSchemaCoverage();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (recipient != null) {
            try {
                fixture.deleteSubscription(UserRole.ADMIN, recipient.getId(),
                        NotificationDataFactory.TEMPLATE_STOCK_RED);
            } catch (Exception e) {
                log.warn("Subscription cleanup: {}", e.getMessage());
            }
            try {
                fixture.deleteSubscription(UserRole.ADMIN, recipient.getId(),
                        NotificationDataFactory.TEMPLATE_STOCK_YELLOW);
            } catch (Exception e) {
                log.warn("Subscription cleanup yellow: {}", e.getMessage());
            }
        }
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            return;
        }
        fixture.disableTrackedRecipients(UserRole.ADMIN);
    }

    @Test(priority = 1)
    @TestCaseId("TC-NOTIF-020")
    @Story("AC-03 Subscribe scoped")
    @Severity(SeverityLevel.CRITICAL)
    public void subscribeWithSingleStorage() {
        fixture.saveSubscription(UserRole.ADMIN, NotificationDataFactory.subscription(
                recipient.getId(),
                NotificationDataFactory.TEMPLATE_STOCK_RED,
                List.of(storage1)));

        NotificationSubscriptionResponse found = fixture.findSubscription(
                UserRole.ADMIN, recipient.getId(), NotificationDataFactory.TEMPLATE_STOCK_RED);
        assertThat(found).isNotNull();
        assertThat(found.getRecipientName()).isEqualTo(recipient.getCaption());
        assertThat(found.getStorages()).extracting(SimpleEntityResponse::getId).containsExactly(storage1);

        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_GET_PAGE,
                UserRole.ADMIN,
                java.util.Map.of("page", 0, "size", 50));
        SchemaRegistry.validateIfSuccess(page, ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_GET_PAGE);
    }

    @Test(priority = 2)
    @TestCaseId("TC-NOTIF-021")
    @Story("AC-03 Empty storages = all")
    @Severity(SeverityLevel.CRITICAL)
    public void upsertWithEmptyStoragesMeansAll() {
        fixture.saveSubscription(UserRole.ADMIN, NotificationSubscriptionRequest.builder()
                .recipientId(recipient.getId())
                .templateCode(NotificationDataFactory.TEMPLATE_STOCK_RED)
                .storages(List.of())
                .build());

        NotificationSubscriptionResponse found = fixture.findSubscription(
                UserRole.ADMIN, recipient.getId(), NotificationDataFactory.TEMPLATE_STOCK_RED);
        assertThat(found).isNotNull();
        assertThat(found.getStorages()).isEmpty();
    }

    @Test(priority = 3)
    @TestCaseId("TC-NOTIF-022")
    @Story("AC-03 Upsert two storages")
    @Severity(SeverityLevel.NORMAL)
    public void upsertWithTwoStorages() {
        fixture.saveSubscription(UserRole.ADMIN, NotificationDataFactory.subscription(
                recipient.getId(),
                NotificationDataFactory.TEMPLATE_STOCK_RED,
                List.of(storage1, storage2)));

        NotificationSubscriptionResponse found = fixture.findSubscription(
                UserRole.ADMIN, recipient.getId(), NotificationDataFactory.TEMPLATE_STOCK_RED);
        assertThat(found).isNotNull();
        assertThat(found.getStorages()).extracting(SimpleEntityResponse::getId)
                .containsExactlyInAnyOrder(storage1, storage2);
    }

    @Test(priority = 4)
    @TestCaseId("TC-NOTIF-023")
    @Story("AC-03 Delete idempotent")
    @Severity(SeverityLevel.NORMAL)
    public void deleteSubscriptionIdempotent() {
        fixture.saveSubscription(UserRole.ADMIN, NotificationDataFactory.subscription(
                recipient.getId(),
                NotificationDataFactory.TEMPLATE_STOCK_YELLOW,
                List.of(storage1)));

        fixture.deleteSubscription(UserRole.ADMIN, recipient.getId(),
                NotificationDataFactory.TEMPLATE_STOCK_YELLOW);
        assertThat(fixture.findSubscription(
                UserRole.ADMIN, recipient.getId(), NotificationDataFactory.TEMPLATE_STOCK_YELLOW))
                .isNull();

        Response second = fixture.deleteSubscriptionRaw(
                UserRole.ADMIN,
                NotificationDataFactory.removeSubscription(
                        recipient.getId(), NotificationDataFactory.TEMPLATE_STOCK_YELLOW));
        assertThat(second.statusCode()).isBetween(200, 299);
    }

    @Test(priority = 5)
    @TestCaseId("TC-NOTIF-024")
    @Story("AC-03 Unknown ids → 404")
    @Severity(SeverityLevel.NORMAL)
    public void subscribeUnknownTemplateOrRecipientReturns404() {
        Response unknownTemplate = fixture.saveSubscriptionRaw(UserRole.ADMIN,
                NotificationDataFactory.subscription(
                        recipient.getId(), "no_such_template_xyz", List.of(storage1)));
        assertThat(unknownTemplate.statusCode()).isEqualTo(404);

        Response unknownRecipient = fixture.saveSubscriptionRaw(UserRole.ADMIN,
                NotificationDataFactory.subscription(
                        Integer.MAX_VALUE - 7,
                        NotificationDataFactory.TEMPLATE_STOCK_RED,
                        List.of(storage1)));
        assertThat(unknownRecipient.statusCode()).isEqualTo(404);
    }
}
