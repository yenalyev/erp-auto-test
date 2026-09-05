package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.InvoiceFixture;
import com.erp.fixtures.IsolatedRestrictedOwnerScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API: генерація та завантаження накладних при видачі з дочірніх локацій (STORAGE / PRODUCTION)
 * у межах області видимості REGIONS. UI journal для ephemeral child недоступний OWNER_2 через JWT scope —
 * перевірка journal/download лишається на API (ADMIN).
 */
@Slf4j
@Epic("Relocation")
@Feature("Storages")
@Story("Invoice in visibility regions")
public class RelocationInvoiceVisibilityTest extends StorageApiTestBase {

    private static final String SCENARIO_PREFIX = "rel-inv-vis-";
    private static final double SEND_AMOUNT = 1.0;
    /** Async invoice generation under suite load needs a wider poll window than a quick smoke. */
    private static final int INVOICE_MAX_ATTEMPTS = 15;

    private RelocationFixture relocationFixture;
    private InvoiceFixture invoiceFixture;
    private IsolatedRestrictedOwnerScope isolatedOwnerScope;

    private Long owner2StorageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupStorageApiBase")
    @Step("Підготовка: isolated REGIONS UNIT + relocation/invoice fixtures")
    public void setupRelocationInvoiceVisibilityTests() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        invoiceFixture = new InvoiceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        isolatedOwnerScope = new IsolatedRestrictedOwnerScope(
                storageFixture,
                new UserFixture(testContext, apiExecutor),
                apiExecutor,
                getPlaywrightSessionProvider());
        owner2StorageId = isolatedOwnerScope.acquire();
        regionFixture.purgeViewerVisibilityScope(UserRole.ADMIN, owner2StorageId, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void restoreOwner2AndIsolatedUnit() {
        if (isolatedOwnerScope != null) {
            isolatedOwnerScope.release();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-REL-011")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_011)
    public void senderStorageInvoiceGeneratedAndDownloadableViaApi() {
        runChildSenderInvoiceApiScenario(UnitType.STORAGE);
    }

    @Test(priority = 11)
    @TestCaseId("TC-UI-REL-013")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_013)
    public void senderProductionInvoiceGeneratedAndDownloadableViaApi() {
        runChildSenderInvoiceApiScenario(UnitType.PRODUCTION);
    }

    private void runChildSenderInvoiceApiScenario(UnitType senderType) {
        ChildSenderScenario scenario = prepareChildSenderScenario(senderType);

        Allure.parameter("senderType", senderType.name());
        Allure.parameter("senderStorageId", scenario.senderId());

        StorageResponse senderStorage = storageFixture.getById(UserRole.ADMIN, scenario.senderId());
        assertThat(UnitType.valueOf(senderStorage.getType()))
                .as("відправник має бути type=%s", senderType)
                .isEqualTo(senderType);

        Allure.step("API: дочекатись async-файлу накладної (/invoice/{id}/exists)", () ->
                invoiceFixture.waitUntilExistsAttempts(
                        UserRole.ADMIN,
                        scenario.relocationId(),
                        scenario.senderId(),
                        INVOICE_MAX_ATTEMPTS));

        RelocationResponse inTransit = Allure.step(
                "API: № накладної у журналі «В дорозі» (ADMIN, scope sender)",
                () -> relocationFixture.waitForInTransitWithInvoiceNumberAttempts(
                        UserRole.ADMIN,
                        scenario.senderId(),
                        scenario.description(),
                        INVOICE_MAX_ATTEMPTS));

        Allure.step("API: поля журналу після генерації накладної", () -> {
            assertThat(inTransit.getCanGenerateInvoice())
                    .as("canGenerateInvoice для INTERNAL %s", senderType)
                    .isTrue();
            assertThat(inTransit.getInvoiceNumber())
                    .as("invoiceNumber у journal «В дорозі»")
                    .isNotBlank();
        });

        Allure.step("API: GET download накладної («В дорозі»)", () -> {
            byte[] body = invoiceFixture.download(
                    UserRole.ADMIN,
                    scenario.relocationId(),
                    scenario.senderId(),
                    scenario.recipientId());
            assertThat(body.length).as("invoice file size").isGreaterThan(100);
        });

        Allure.step("API: resolve → FINISHED", () -> {
            RelocationResponse finished = relocationFixture.resolve(
                    UserRole.ADMIN,
                    scenario.relocationId(),
                    scenario.recipientId(),
                    RelocationState.FINISHED,
                    "TC-UI-REL invoice API accept");
            assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);
        });

        RelocationResponse sentHistory = Allure.step(
                "API: № накладної у журналі «Видано»",
                () -> relocationFixture.waitForSentHistoryWithInvoiceNumberAttempts(
                        UserRole.ADMIN,
                        scenario.senderId(),
                        scenario.description(),
                        INVOICE_MAX_ATTEMPTS));

        Allure.step("API: GET download накладної («Видано»)", () -> {
            assertThat(sentHistory.getInvoiceNumber()).isEqualTo(inTransit.getInvoiceNumber());
            assertThat(sentHistory.getCanGenerateInvoice()).isTrue();
            byte[] body = invoiceFixture.download(
                    UserRole.ADMIN,
                    scenario.relocationId(),
                    scenario.senderId(),
                    scenario.recipientId());
            assertThat(body.length).as("invoice file size after FINISHED").isGreaterThan(100);
        });
    }

    private ChildSenderScenario prepareChildSenderScenario(UnitType senderType) {
        StorageResponse regionAnchor = storageFixture.getById(UserRole.ADMIN, owner2StorageId);
        StorageResponse senderStorage = createChildSenderStorage(senderType);
        StorageResponse recipientStorage = storageFixture.createUniqueStorage(SCENARIO_PREFIX + "to-");

        StorageRegionResponse region = regionFixture.createRegion(
                regionAnchor, StorageAccessMode.FULL_ACCESS, SCENARIO_PREFIX + senderType.name().toLowerCase() + "-");
        regionFixture.addRegionLocations(region.getId(), senderStorage.getId(), recipientStorage.getId());
        regionFixture.addRegionMembers(region.getId(), owner2StorageId);

        Long resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor, UserRole.ADMIN, senderStorage.getId(), Map.of(resourceId, 50.0));

        String description = SCENARIO_PREFIX + senderType.name().toLowerCase() + "-" + System.currentTimeMillis();

        RelocationResponse sent = relocationFixture.createSendWithInvoice(
                UserRole.ADMIN,
                senderStorage.getId(),
                recipientStorage.getId(),
                resourceId,
                SEND_AMOUNT,
                description);

        return new ChildSenderScenario(
                senderStorage.getId(),
                recipientStorage.getId(),
                description,
                sent.getId());
    }

    private StorageResponse createChildSenderStorage(UnitType senderType) {
        String prefix = SCENARIO_PREFIX + senderType.name().toLowerCase() + "-snd-";
        return switch (senderType) {
            case STORAGE -> storageFixture.createChildStorage(owner2StorageId, prefix);
            case PRODUCTION -> storageFixture.createChildStorage(
                    owner2StorageId, prefix, UnitType.PRODUCTION, StorageRelation.INTERNAL);
            default -> throw new IllegalArgumentException("Unsupported child sender type: " + senderType);
        };
    }

    private record ChildSenderScenario(
            Long senderId,
            Long recipientId,
            String description,
            Long relocationId) {
    }
}
