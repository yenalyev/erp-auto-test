package com.erp.tests.functional.notification;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.DefectFixture;
import com.erp.fixtures.NotificationFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.NotificationLogResponse;
import com.erp.models.response.NotificationRecipientResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-07: special characters in storage names survive notification scheduling
 * and appear correctly in the journal. WhatsApp markdown rendering is manual (ESC-002).
 * <p>
 * Trigger path (matches SUT):
 * <ol>
 *   <li>Stock alert must exist so {@code calculateWeight} returns RED when amount ≤ 0
 *       (without alerts weight stays 0 and {@code StorageItemListener} is skipped).</li>
 *   <li>STORAGE defect depletes stock inside {@code @Transactional} DefectFacade, which
 *       publishes {@code StorageItemChanged}. Alert create alone is not transactional, so
 *       {@code @TransactionalEventListener} would drop that event.</li>
 * </ol>
 */
@Slf4j
@Epic("Notifications")
@Feature("REQ-NOTIF Escaping")
public class NotificationEscapingTest extends BaseFunctionalTest {

    private static final double SEED_AMOUNT = 5.0;
    private static final double ALERT_LIMIT = 1.0;
    private static final long AWAIT_MS = 60_000;

    private NotificationFixture notificationFixture;
    private StorageFixture storageFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private AlertFixture alertFixture;
    private DefectFixture defectFixture;

    private Long owner1StorageId;
    private Long resourceId;
    private final List<Integer> recipientIds = new ArrayList<>();
    private final List<Long> storageIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        notificationFixture = new NotificationFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        alertFixture = new AlertFixture(testContext, apiExecutor);
        defectFixture = new DefectFixture(testContext, apiExecutor);

        notificationFixture.prepareContext();
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();

        owner1StorageId = ConfigProvider.getOwner1StorageId();
        ResourceResponse resource = resourceFixture.createUniqueResource("notif-esc-res-");
        resourceId = resource.getId();
        testContext.set(ContextKey.RELOCATION_RESOURCE_ID, resourceId);
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        for (Integer recipientId : recipientIds) {
            try {
                notificationFixture.deleteSubscription(
                        UserRole.ADMIN, recipientId, NotificationDataFactory.TEMPLATE_STOCK_RED);
            } catch (Exception e) {
                log.warn("Subscription cleanup {}: {}", recipientId, e.getMessage());
            }
        }
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            log.warn("Staging mode — skipping recipient/storage cleanup");
            return;
        }
        for (Long storageId : storageIds) {
            try {
                alertFixture.deleteAlertForStorage(storageId, UserRole.ADMIN);
            } catch (Exception e) {
                log.warn("Alert cleanup storage {}: {}", storageId, e.getMessage());
            }
        }
        notificationFixture.disableTrackedRecipients(UserRole.ADMIN);
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }

    @Test(priority = 1)
    @TestCaseId("TC-NOTIF-ESC-001")
    @Story("AC-07 Markup special chars")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Storage name with * ( ) : - _ appears unchanged in stock_red journal row.")
    public void stockRedJournalPreservesMarkupSpecialCharsInStorageName() {
        String storageName = NotificationDataFactory.storageNameWithMarkupChars();
        triggerStockRedAndAssertJournal(storageName);
    }

    @Test(priority = 2)
    @TestCaseId("TC-NOTIF-ESC-002")
    @Story("AC-07 WhatsApp *текст* trap")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Storage name containing *текст* is preserved in journal (ERP path).
            MANUAL: verify WhatsApp shows *текст* literally (not bold / edit markup).
            """)
    public void stockRedJournalPreservesWhatsAppBoldTrapInStorageName() {
        String storageName = NotificationDataFactory.storageNameWithWhatsAppBoldTrap();
        NotificationLogResponse row = triggerStockRedAndAssertJournal(storageName);
        assertThat(row.getStorage().getName()).contains("*текст*");
    }

    @Test(priority = 3)
    @TestCaseId("TC-NOTIF-ESC-003")
    @Story("AC-07 JSON-critical chars")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Storage name with " and \\ schedules stock_red without compile crash;
            journal keeps the name. Encrypted notification.message is not readable via public API;
            expected escapeJson fragment is attached for manual/DB follow-up.
            """)
    public void stockRedJournalPreservesJsonCriticalCharsInStorageName() {
        String storageName = NotificationDataFactory.storageNameWithJsonCriticalChars();
        NotificationLogResponse row = triggerStockRedAndAssertJournal(storageName);
        assertThat(row.getStorage().getName()).contains("\"");
        assertThat(row.getStorage().getName()).contains("\\");

        String expectedEscaped = escapeJsonLikeBackend(storageName);
        Allure.addAttachment("expected-escapeJson-storage_name", "text/plain", expectedEscaped);
        Allure.step("Public API does not expose message body; journal.name + escapeJson attachment cover AC-07 API path");
    }

    /** Mirrors Apache Commons StringEscapeUtils.escapeJson used by NotificationService. */
    private static String escapeJsonLikeBackend(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private NotificationLogResponse triggerStockRedAndAssertJournal(String storageName) {
        StorageResponse storage = createStorageWithName(storageName);
        storageIds.add(storage.getId());

        NotificationRecipientResponse recipient = notificationFixture.createActiveRecipient(UserRole.ADMIN);
        recipientIds.add(recipient.getId());
        notificationFixture.saveSubscription(
                UserRole.ADMIN,
                NotificationDataFactory.subscription(
                        recipient.getId(),
                        NotificationDataFactory.TEMPLATE_STOCK_RED,
                        List.of(storage.getId())));

        String batch = "notif-esc-" + UUID.randomUUID().toString().substring(0, 8);
        relocationFixture.seedBatchOnStorage(storage.getId(), resourceId, SEED_AMOUNT, batch);

        // Required for RED weight; event from alert create itself is not reliable (no TX boundary).
        alertFixture.createOrUpdateStockAlert(
                UserRole.ADMIN, storage.getId(), resourceId, ALERT_LIMIT);

        defectFixture.createAs(
                UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(storage.getId(), resourceId, SEED_AMOUNT));

        NotificationLogResponse row = notificationFixture.awaitStockRedForStorage(
                UserRole.ADMIN, storage.getId(), AWAIT_MS);

        assertThat(row.getTemplateCode()).isEqualTo(NotificationDataFactory.TEMPLATE_STOCK_RED);
        assertThat(row.getStorage()).isNotNull();
        assertThat(row.getStorage().getId()).isEqualTo(storage.getId());
        assertThat(row.getStorage().getName()).isEqualTo(storageName);
        assertThat(row.getState()).isIn("PENDING", "SENDING", "SENT", "FAILED", "CANCELED");
        return row;
    }

    private StorageResponse createStorageWithName(String name) {
        Long parentId = owner1StorageId;
        StorageRequest request = StorageDataFactory.childStorage(parentId)
                .name(name)
                .alias(StorageDataFactory.shortAlias())
                .build();
        StorageResponse created = storageFixture.createStorage(request);
        assertThat(created.getName()).isEqualTo(name);
        return created;
    }
}
