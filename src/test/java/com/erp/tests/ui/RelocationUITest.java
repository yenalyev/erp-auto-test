package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationBatchAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import com.erp.utils.helpers.RelocationBatchAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Relocation UI")
public class RelocationUITest extends BaseUITest {

    private RelocationFixture fixture;
    private long storageId;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new RelocationFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        injectRoleSession(UserRole.OWNER_1, storageId);
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }

    @Test
    @TestCaseId("TC-UI-REL-001")
    @Story("Relocation journal and external receive")
    @Severity(SeverityLevel.CRITICAL)
    public void externalReceiveJournalAndStock() {
        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open();
        relocationPage.attachScreenshot("Relocations journal");

        assertThat(relocationPage.isReceiveButtonVisible())
                .as("Кнопка «Отримати» видима")
                .isTrue();
        assertThat(relocationPage.isSendButtonVisible())
                .as("Кнопка «Видати» видима")
                .isTrue();

        relocationPage.clickReceive()
                .fillInvoiceNumber(RelocationDataFactory.uniqueInvoiceNumber())
                .fillDescription("ui-receive-smoke")
                .attachScreenshot("Receive form");

        double amount = 10.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);
        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ДО receive");

        fixture.createExternalReceive(UserRole.OWNER_1, storageId, resourceId, amount, batchNumber);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ПІСЛЯ receive");
        RelocationStockAssertions.assertCreditedToRecipient(
                before, after, storageId, resourceId, amount, "receive stock check");

        relocationPage.open().openReceivedHistoryTab();
        relocationPage.attachScreenshot("History after receive");
    }

    @Test
    @TestCaseId("TC-UI-REL-004")
    @Story("Admin edit external receive via UI")
    @Severity(SeverityLevel.CRITICAL)
    public void adminEditExternalReceiveViaUi() {
        injectRoleSession(UserRole.ADMIN, storageId);
        page = browserContext.newPage();

        double initial = 20.0;
        double edited = 12.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        String marker = "ui-admin-edit-" + System.currentTimeMillis();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = fixture.createExternalReceive(
                UserRole.OWNER_1, storageId, resourceId, initial, batchNumber);

        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "ДО UI admin edit");
        RelocationBatchAssertions.BatchSnapshot batchBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, storageId, UserRole.ADMIN, resourceId, batchNumber, false, "ДО UI admin edit");

        Allure.step("API admin edit (UI form — окремий сценарій)", () -> {
            RelocationInputEditRequest edit = RelocationDataFactory.buildReceiveEditRequest(
                    resourceId, edited, batchNumber, marker);
            fixture.editExternalReceive(UserRole.ADMIN, created.getId(), storageId, edit);
        });

        ProductionStockAssertions.StockSnapshot stockAfter = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "ПІСЛЯ UI admin edit");
        RelocationStockAssertions.assertDebitedFromSender(
                stockBefore, stockAfter, storageId, resourceId, initial - edited, "UI+API admin edit");

        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open().openReceivedHistoryTab();
        relocationPage.attachScreenshot("History after admin edit");
    }

    @Test
    @TestCaseId("TC-UI-REL-005")
    @Story("Admin delete external receive via UI")
    @Severity(SeverityLevel.CRITICAL)
    public void adminDeleteExternalReceiveViaUi() {
        injectRoleSession(UserRole.ADMIN, storageId);
        page = browserContext.newPage();

        double amount = 15.0;
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        String marker = RelocationDataFactory.uniqueInvoiceNumber();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse created = Allure.step("Setup external receive via API", () ->
                fixture.createExternalReceive(
                        UserRole.OWNER_1, storageId, resourceId, amount, batchNumber));

        ProductionStockAssertions.StockSnapshot stockBefore = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "ДО UI delete");

        Allure.step("API delete (UI dialog covered by POM)", () ->
                fixture.deleteRelocation(UserRole.ADMIN, created.getId(), storageId));

        ProductionStockAssertions.StockSnapshot stockAfter = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.ADMIN, tracked, "ПІСЛЯ delete");
        RelocationStockAssertions.assertDebitedFromSender(
                stockBefore, stockAfter, storageId, resourceId, amount, "admin delete revert");

        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open().openReceivedHistoryTab();
        relocationPage.attachScreenshot("History after delete");
    }

    @Test
    @TestCaseId("TC-UI-REL-002")
    @Story("Send and resolve via UI journal")
    public void sendAndResolveViaUiJournal() {
        double amount = 6.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot senderBefore = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ДО send");

        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, storageId, ConfigProvider.getOwner2StorageId(), resourceId, amount);

        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open().openActiveTab();
        relocationPage.attachScreenshot("Active relocations after send");

        fixture.resolve(UserRole.OWNER_1, sent.getId(), ConfigProvider.getOwner2StorageId(),
                RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot senderAfter = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ПІСЛЯ resolve");
        RelocationStockAssertions.assertDebitedFromSender(
                senderBefore, senderAfter, storageId, resourceId, amount, "UI journal send+resolve");
    }

    @Test
    @TestCaseId("TC-UI-REL-003")
    @Story("Send to UNIT shows in history")
    public void sendToUnitVisibleInHistory() {
        double amount = 4.0;
        Long unitId = testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ДО UNIT send");

        fixture.createSend(UserRole.OWNER_1, storageId, unitId, resourceId, amount);

        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open().openHistoryTab();
        relocationPage.attachScreenshot("History after UNIT send");

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ПІСЛЯ UNIT send");
        RelocationStockAssertions.assertDebitedFromSender(
                before, after, storageId, resourceId, amount, "UNIT send UI");
    }

    @Test
    @TestCaseId("TC-UI-REL-006")
    @Story("Cancel and return flow")
    public void cancelAndReturnFlow() {
        double amount = 5.0;
        long owner2 = ConfigProvider.getOwner2StorageId();
        Set<Long> tracked = fixture.trackedResource(resourceId);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ДО cancel/return");

        fixture.cancelThenReturn(
                UserRole.OWNER_1, UserRole.OWNER_1, storageId, owner2, resourceId, amount);

        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open().openHistoryTab();
        relocationPage.attachScreenshot("History after cancel/return");

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ПІСЛЯ cancel/return");
        RelocationStockAssertions.assertUnchanged(
                before, after, storageId, resourceId, "stock restored");
    }

    @Test
    @TestCaseId("TC-UI-REL-007")
    @Story("Edit AUTO_FINISHED send")
    public void editAutoFinishedSendViaJournal() {
        double initial = 8.0;
        double edited = 5.0;
        Long unitId = testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationResponse sent = fixture.createSend(
                UserRole.OWNER_1, storageId, unitId, resourceId, initial);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ДО edit send");

        fixture.editSend(UserRole.OWNER_1, sent.getId(), storageId,
                RelocationDataFactory.buildSendEditRequest(resourceId, edited, "ui edit send"));

        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open().openHistoryTab();
        relocationPage.attachScreenshot("History after send edit");

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "ПІСЛЯ edit send");
        RelocationStockAssertions.assertCreditedToRecipient(
                before, after, storageId, resourceId, initial - edited, "UI edit send revert");
    }

    @Test
    @TestCaseId("TC-UI-REL-008")
    @Story("Send with named batch")
    public void sendWithNamedBatchViaJournal() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        fixture.seedBatchOnStorage(storageId, resourceId, 15.0, batchNumber);
        double sendAmount = 5.0;
        Set<Long> tracked = fixture.trackedResource(resourceId);

        RelocationBatchAssertions.BatchSnapshot batchBefore = RelocationBatchAssertions.captureBatch(
                apiExecutor, storageId, UserRole.OWNER_1, resourceId, batchNumber, false, "ДО UI batch send");

        fixture.createSendWithBatch(
                UserRole.OWNER_1, storageId, ConfigProvider.getOwner2StorageId(),
                resourceId, sendAmount, batchNumber, false);

        RelocationPage relocationPage = new RelocationPage(page);
        relocationPage.open().openActiveTab();
        relocationPage.attachScreenshot("Active after batch send");

        RelocationBatchAssertions.BatchSnapshot batchAfter = RelocationBatchAssertions.captureBatch(
                apiExecutor, storageId, UserRole.OWNER_1, resourceId, batchNumber, false, "ПІСЛЯ UI batch send");
        RelocationBatchAssertions.assertBatchDebited(
                batchBefore, batchAfter, sendAmount, "UI batch send");
    }
}
