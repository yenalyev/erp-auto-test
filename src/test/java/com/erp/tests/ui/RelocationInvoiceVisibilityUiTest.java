package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.InvoiceFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI-перевірка завантаження накладних з журналу «Видати/Отримати» для підрозділу
 * з {@code accessMode=REGIONS}.
 *
 * <p>TC-UI-REL-011/013 — API ({@link com.erp.tests.functional.storage.RelocationInvoiceVisibilityTest}).
 * Тут лишаються UI-сценарії на workspace OWNER_2 (ПМ БАР): видача з UNIT (014) та отримання (012).
 */
@Slf4j
@Epic("Relocation")
@Feature("Relocation UI")
@Story("Invoice download in visibility regions")
public class RelocationInvoiceVisibilityUiTest extends BaseUITest {

    private static final Object OWNER2_ACCESS_LOCK = new Object();
    private static final String SCENARIO_PREFIX = "ui-inv-vis-";
    private static final double SEND_AMOUNT = 1.0;
    private static final int INVOICE_MAX_ATTEMPTS = 5;
    private static final int JOURNAL_ROW_MAX_ATTEMPTS = 5;

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private RelocationFixture relocationFixture;
    private InvoiceFixture invoiceFixture;

    private Long owner2StorageId;
    private String originalOwner2AccessMode;
    private boolean owner2AccessModeChanged;

    private Long scenarioSenderId;
    private Long scenarioRecipientId;
    private Long scenarioRelocationId;
    private String scenarioDescription;
    private boolean scenarioFinished;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        invoiceFixture = new InvoiceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        ensureOwner2RestrictedAccess();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, owner2StorageId, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void restoreOwner2AccessMode() {
        restoreOwner2AccessIfChanged();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupScenario() {
        cancelTrackedRelocationIfNeeded();
        if ("staging".equals(System.getProperty("env", "debug"))) {
            regionFixture.clearTrackedRegions();
            storageFixture.clearTrackedStorages();
            return;
        }
        regionFixture.deleteTrackedRegions(UserRole.ADMIN);
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        resetScenarioState();
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-REL-014")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_014)
    public void senderUnitCanDownloadInvoiceInTransitAndSentTabs() {
        InvoiceScenario scenario = prepareRegionsUnitSendScenario();

        injectOwner2Session(owner2StorageId);

        String invoiceNumber = Allure.step(
                "API (setup): № накладної після generateInvoice (OWNER_2, workspace=unit)",
                () -> {
                    RelocationResponse inTransit = relocationFixture.waitForInTransitWithInvoiceNumberAttempts(
                            UserRole.OWNER_2,
                            owner2StorageId,
                            scenario.description(),
                            INVOICE_MAX_ATTEMPTS);
                    invoiceFixture.waitUntilExistsAttempts(
                            UserRole.OWNER_2,
                            scenario.relocation().getId(),
                            owner2StorageId,
                            INVOICE_MAX_ATTEMPTS);
                    assertThat(inTransit.getCanGenerateInvoice())
                            .as("canGenerateInvoice для UNIT sender")
                            .isTrue();
                    return inTransit.getInvoiceNumber();
                });

        RelocationPage journal = Allure.step(
                "UI: журнал → вкладка «В дорозі» (workspace = ПМ БАР)",
                () -> new RelocationPage(page)
                        .open()
                        .openInTransitTab()
                        .waitForJournalDataSettled());

        assertUiInvoiceDownloadOnTab(
                journal,
                invoiceNumber,
                "В дорозі",
                "TC-UI-REL-014 — in transit");

        acceptRelocationAsRecipient(scenario, UserRole.ADMIN);

        Allure.step("API (setup): переміщення у журналі «Видано» (OWNER_2)", () -> {
            RelocationResponse sentHistory = relocationFixture.waitForSentHistoryWithInvoiceNumberAttempts(
                    UserRole.OWNER_2,
                    owner2StorageId,
                    scenario.description(),
                    INVOICE_MAX_ATTEMPTS);
            assertThat(sentHistory.getState()).isEqualTo(RelocationState.FINISHED);
            assertThat(sentHistory.getInvoiceNumber()).isEqualTo(invoiceNumber);
            assertThat(sentHistory.getCanGenerateInvoice()).isTrue();
        });

        Allure.step("UI: перемкнути на вкладку «Видано»", () -> journal.openSentTab());

        assertUiInvoiceDownloadOnTab(
                journal,
                invoiceNumber,
                "Видано",
                "TC-UI-REL-014 — sent tab");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-REL-012")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_012)
    public void recipientCanDownloadInvoiceFromReceivedTab() {
        InvoiceScenario scenario = prepareRegionsReceiveScenario();

        relocationFixture.waitForInTransitWithInvoiceNumberAttempts(
                UserRole.OWNER_2,
                scenario.recipientStorageId(),
                scenario.description(),
                INVOICE_MAX_ATTEMPTS);

        acceptRelocationAsRecipient(scenario, UserRole.OWNER_2);

        RelocationResponse receivedHistory = Allure.step(
                "API: № накладної у журналі «Отримано» (scope отримувача)",
                () -> relocationFixture.waitForReceivedHistoryWithInvoiceNumberAttempts(
                        UserRole.OWNER_2,
                        scenario.recipientStorageId(),
                        scenario.description(),
                        INVOICE_MAX_ATTEMPTS));

        assertThat(receivedHistory.getState()).isEqualTo(RelocationState.FINISHED);

        injectOwner2Session(scenario.recipientStorageId());

        RelocationPage journal = Allure.step(
                "UI: журнал → вкладка «Отримано» (workspace = локація-отримувач)",
                () -> new RelocationPage(page).open().openReceivedTab().waitForJournalDataSettled());

        assertUiInvoiceDownloadOnTab(
                journal,
                receivedHistory.getInvoiceNumber(),
                "Отримано",
                "TC-UI-REL-012 — received tab");
    }

    /** Видача з UNIT OWNER_2 → ephemeral recipient у FULL_ACCESS region (як ручний UI з ПМ БАР). */
    private InvoiceScenario prepareRegionsUnitSendScenario() {
        StorageResponse regionAnchor = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
        StorageResponse recipientStorage = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "to-");

        StorageRegionResponse region = regionFixture.createRegion(
                regionAnchor, StorageAccessMode.FULL_ACCESS, SCENARIO_PREFIX + "unit-");
        regionFixture.addRegionLocations(region.getId(), recipientStorage.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        Long resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor, UserRole.OWNER_2, owner2StorageId, Map.of(resourceId, 50.0));

        scenarioDescription = SCENARIO_PREFIX + "unit-" + System.currentTimeMillis();
        scenarioSenderId = owner2StorageId;
        scenarioRecipientId = recipientStorage.getId();
        scenarioFinished = false;

        RelocationResponse sent = relocationFixture.createSendWithInvoice(
                UserRole.OWNER_2,
                owner2StorageId,
                recipientStorage.getId(),
                resourceId,
                SEND_AMOUNT,
                scenarioDescription);
        scenarioRelocationId = sent.getId();

        return new InvoiceScenario(
                owner2StorageId,
                recipientStorage,
                scenarioDescription,
                sent);
    }

    /** Видача з локації області → підрозділ OWNER_2 (отримувач у REGIONS). */
    private InvoiceScenario prepareRegionsReceiveScenario() {
        StorageResponse recipientStorage = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
        StorageResponse senderInRegion = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "from-");

        StorageRegionResponse region = regionFixture.createRegion(
                recipientStorage, StorageAccessMode.FULL_ACCESS, SCENARIO_PREFIX + "recv-reg-");
        regionFixture.addRegionLocations(region.getId(), senderInRegion.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        Long resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor, UserRole.ADMIN, senderInRegion.getId(), Map.of(resourceId, 50.0));

        scenarioDescription = SCENARIO_PREFIX + "recv-" + System.currentTimeMillis();
        scenarioSenderId = senderInRegion.getId();
        scenarioRecipientId = owner2StorageId;
        scenarioFinished = false;

        RelocationResponse sent = relocationFixture.createSendWithInvoice(
                UserRole.ADMIN,
                senderInRegion.getId(),
                owner2StorageId,
                resourceId,
                SEND_AMOUNT,
                scenarioDescription);
        scenarioRelocationId = sent.getId();

        return new InvoiceScenario(
                senderInRegion.getId(),
                recipientStorage,
                scenarioDescription,
                sent);
    }

    private void acceptRelocationAsRecipient(InvoiceScenario scenario, UserRole role) {
        Allure.step("API: прийняття переміщення отримувачем (CREATED → FINISHED)", () -> {
            RelocationResponse accepted = relocationFixture.resolve(
                    role,
                    scenario.relocation().getId(),
                    scenario.recipientStorageId(),
                    RelocationState.FINISHED,
                    "TC-UI-REL invoice accept");
            assertThat(accepted.getState()).isEqualTo(RelocationState.FINISHED);
            scenarioFinished = true;
        });
    }

    private void assertUiInvoiceDownloadOnTab(
            RelocationPage journal,
            String invoiceNumber,
            String tabLabel,
            String screenshotPrefix) {
        Allure.step("UI («" + tabLabel + "»): рядок переміщення та посилання № " + invoiceNumber, () -> {
            awaitJournalRow(journal, invoiceNumber, tabLabel);
            assertThat(journal.isInvoiceLinkVisible(invoiceNumber))
                    .as("№ накладної «%s» — клікабельне посилання на вкладці «%s»", invoiceNumber, tabLabel)
                    .isTrue();
        });

        RelocationPage.InvoiceUiDownloadResult uiDownload = Allure.step(
                "UI («" + tabLabel + "»): клік по № накладної → Playwright download",
                () -> journal.clickInvoiceLinkAndWaitForDownload(invoiceNumber));
        journal.attachScreenshot(screenshotPrefix);

        Allure.step("UI («" + tabLabel + "»): assert файлу накладної", () -> {
            assertThat(uiDownload.sizeBytes())
                    .as("Файл накладної з UI не порожній (вкладка «%s»)", tabLabel)
                    .isGreaterThan(100);
            assertThat(uiDownload.suggestedFilename())
                    .as("Ім'я файлу накладної (вкладка «%s»)", tabLabel)
                    .matches("(?i).+\\.(pdf|docx)");
        });
    }

    private void awaitJournalRow(RelocationPage journal, String rowMarker, String tabLabel) {
        AssertionError last = null;
        for (int attempt = 1; attempt <= JOURNAL_ROW_MAX_ATTEMPTS; attempt++) {
            if (journal.isRowWithTextVisible(rowMarker)) {
                if (attempt > 1) {
                    journal.attachScreenshot("row found — «" + rowMarker + "» tab «" + tabLabel + "»");
                }
                return;
            }
            journal.attachScreenshot(
                    "await row attempt " + attempt + "/" + JOURNAL_ROW_MAX_ATTEMPTS
                            + " — marker «" + rowMarker + "» tab «" + tabLabel + "»");
            last = new AssertionError(
                    "Рядок «" + rowMarker + "» ще не видимий на вкладці «" + tabLabel + "» (спроба "
                            + attempt + "/" + JOURNAL_ROW_MAX_ATTEMPTS + ")");
            if (attempt < JOURNAL_ROW_MAX_ATTEMPTS) {
                page.reload();
                journal.waitForLoaded();
                reopenJournalTab(journal, tabLabel);
                page.waitForTimeout(2_000);
            }
        }
        journal.attachScreenshot("FAIL — row «" + rowMarker + "» not visible on tab «" + tabLabel + "»");
        throw last != null ? last : new AssertionError("Journal row not visible: " + rowMarker);
    }

    private void reopenJournalTab(RelocationPage journal, String tabLabel) {
        switch (tabLabel) {
            case "Видано" -> journal.openSentTab();
            case "Отримано" -> journal.openReceivedTab();
            default -> journal.openInTransitTab();
        }
    }

    private void cancelTrackedRelocationIfNeeded() {
        if (scenarioRelocationId == null || scenarioSenderId == null || scenarioFinished) {
            return;
        }
        try {
            relocationFixture.resolve(
                    UserRole.ADMIN,
                    scenarioRelocationId,
                    scenarioSenderId,
                    RelocationState.CANCELLED,
                    "invoice visibility UI cleanup");
        } catch (Exception e) {
            log.warn("Failed to cancel relocation {}: {}", scenarioRelocationId, e.getMessage());
        }
    }

    private void resetScenarioState() {
        scenarioSenderId = null;
        scenarioRecipientId = null;
        scenarioRelocationId = null;
        scenarioDescription = null;
        scenarioFinished = false;
    }

    private void injectOwner2Session(long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_2.getUsername(), UserRole.OWNER_2.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }

    private void ensureOwner2RestrictedAccess() {
        synchronized (OWNER2_ACCESS_LOCK) {
            StorageResponse owner2Storage = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
            originalOwner2AccessMode = owner2Storage.getAccessMode();
            if (!StorageAccessMode.REGIONS.name().equals(originalOwner2AccessMode)) {
                StorageRequest update = StorageDataFactory.withAccessMode(
                        owner2Storage, StorageAccessMode.REGIONS);
                storageFixture.update(UserRole.ADMIN, owner2StorageId, update);
                owner2AccessModeChanged = true;
                log.info("OWNER_2 storage {} set to REGIONS for invoice visibility UI tests", owner2StorageId);
            }
        }
    }

    private void restoreOwner2AccessIfChanged() {
        if (!owner2AccessModeChanged || originalOwner2AccessMode == null) {
            return;
        }
        synchronized (OWNER2_ACCESS_LOCK) {
            try {
                StorageResponse current = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
                StorageRequest restore = StorageDataFactory.withAccessMode(
                        current, StorageAccessMode.valueOf(originalOwner2AccessMode));
                storageFixture.update(UserRole.ADMIN, owner2StorageId, restore);
                log.info("OWNER_2 storage {} accessMode restored to {}", owner2StorageId, originalOwner2AccessMode);
            } catch (Exception e) {
                log.warn("Failed to restore OWNER_2 storage accessMode: {}", e.getMessage());
            }
        }
    }

    private record InvoiceScenario(
            Long senderStorageId,
            StorageResponse recipientStorage,
            String description,
            RelocationResponse relocation) {

        Long recipientStorageId() {
            return recipientStorage.getId();
        }
    }
}
