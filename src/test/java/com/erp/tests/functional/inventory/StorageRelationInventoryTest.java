package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Запаси залежать від StorageRelation. Створені локації архівуються після кожного тесту (StorageApiTestBase).
 */
@Slf4j
@Epic("Inventory")
@Feature("Storage Relation × Stock")
public class StorageRelationInventoryTest extends StorageApiTestBase {

    private static final List<UnitType> RELATION_TEST_TYPES = List.of(
            UnitType.STORAGE, UnitType.UNIT, UnitType.PRODUCTION);

    private RelocationFixture relocationFixture;
    private InventoryFixture inventoryFixture;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    public void setupStorageRelationInventory() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceId = testContext.get(com.erp.test_context.ContextKey.RELOCATION_RESOURCE_ID);
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-INV-REL-001")
    @Story("INTERNAL storage tracks stock on external receive")
    @Description("""
            Що перевіряємо: зовнішнє отримання (SUPPLIER→storage) нараховує залишок на INTERNAL локацію.
            Тестові дані: нова child INTERNAL type=STORAGE (inv-int-), ресурс з relocation fixture,
            amount=12, унікальна партія, receive від EXTERNAL supplier (fixture).
            Очікування: stock на INTERNAL зростає на amount після AUTO_FINISHED receive.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testReceiveIncreasesStockOnInternalStorage() {
        StorageResponse internalStorage = storageFixture.createUniqueStorage("inv-int-");
        assertThat(internalStorage.getRelation()).isEqualTo(StorageRelation.INTERNAL.name());

        double amount = 12.0;
        String batch = RelocationDataFactory.uniqueBatchNumber();
        Set<Long> tracked = Set.of(resourceId);

        ProductionStockAssertions.StockSnapshot before = RelocationStockAssertions.capture(
                apiExecutor, internalStorage.getId(), UserRole.ADMIN, tracked, "ДО receive→INTERNAL");

        relocationFixture.createExternalReceive(
                UserRole.ADMIN, internalStorage.getId(), resourceId, amount, batch);

        ProductionStockAssertions.StockSnapshot after = RelocationStockAssertions.capture(
                apiExecutor, internalStorage.getId(), UserRole.ADMIN, tracked, "ПІСЛЯ receive→INTERNAL");

        RelocationStockAssertions.assertCreditedToRecipient(
                before, after, internalStorage.getId(), resourceId, amount, "INTERNAL recipient");
    }

    @Test(priority = 20)
    @TestCaseId("TC-INV-REL-002")
    @Story("EXTERNAL storage does not accumulate stock on receive")
    @Description("""
            Що перевіряємо: relocation receive не змінює облікований залишок на EXTERNAL recipient.
            Тестові дані: EXTERNAL child type=STORAGE (inv-ext-), той самий resourceId, amount=8, receive від supplier.
            Очікування: GET inventory до/після — однаковий залишок (зазвичай 0).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testReceiveDoesNotChangeStockOnExternalStorage() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse externalStorage = storageFixture.createExternalChildStorage(parent.getId(), "inv-ext-");

        double amount = 8.0;
        String batch = RelocationDataFactory.uniqueBatchNumber();

        double stockBefore = inventoryFixture.getResourceStock(
                externalStorage.getId(), resourceId, UserRole.ADMIN);

        relocationFixture.createExternalReceive(
                UserRole.ADMIN, externalStorage.getId(), resourceId, amount, batch);

        double stockAfter = inventoryFixture.getResourceStock(
                externalStorage.getId(), resourceId, UserRole.ADMIN);

        assertThat(stockAfter).isEqualTo(stockBefore);
    }

    @Test(priority = 25)
    @TestCaseId("TC-INV-REL-005")
    @Story("EXTERNAL receive no-op is independent of UnitType")
    @Description("""
            Що перевіряємо: відсутність нарахування залишку на EXTERNAL не залежить від UnitType локації.
            Тестові дані: для type ∈ {STORAGE, UNIT, PRODUCTION} створюємо EXTERNAL child (inv-ext-<type>-),
            виконуємо receive amount=5 від supplier на кожну.
            Очікування: stock після receive = stock до receive для кожного type.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testExternalReceiveStockNoOpIndependentOfUnitType() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        double amount = 5.0;

        for (UnitType type : RELATION_TEST_TYPES) {
            StorageResponse external = storageFixture.createChildStorage(
                    parent.getId(),
                    "inv-ext-" + type.name().toLowerCase() + "-",
                    type,
                    StorageRelation.EXTERNAL);
            String batch = RelocationDataFactory.uniqueBatchNumber();

            double before = inventoryFixture.getResourceStock(
                    external.getId(), resourceId, UserRole.ADMIN);

            relocationFixture.createExternalReceive(
                    UserRole.ADMIN, external.getId(), resourceId, amount, batch);

            double after = inventoryFixture.getResourceStock(
                    external.getId(), resourceId, UserRole.ADMIN);

            assertThat(after)
                    .as("EXTERNAL receive must not credit stock for type=%s", type)
                    .isEqualTo(before);
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-INV-REL-003")
    @Story("Send to EXTERNAL recipient — AUTO_FINISHED, no stock on recipient")
    @Description("""
            Що перевіряємо: send INTERNAL→EXTERNAL списує з відправника, не зараховує на EXTERNAL, state=AUTO_FINISHED.
            Тестові дані: INTERNAL sender + EXTERNAL recipient (type=STORAGE), seed receive 26 од., send amount=6.
            Очікування: sender −6, recipient stock без змін, relocation AUTO_FINISHED.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testSendToExternalRecipientAutoFinishedWithoutStockCredit() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse internalSender = storageFixture.createChildStorage(parent.getId(), "inv-snd-");
        StorageResponse externalRecipient = storageFixture.createExternalChildStorage(parent.getId(), "inv-rcv-");

        double amount = 6.0;
        Set<Long> tracked = Set.of(resourceId);
        String batch = RelocationDataFactory.uniqueBatchNumber();
        relocationFixture.createExternalReceive(
                UserRole.ADMIN, internalSender.getId(), resourceId, amount + 20, batch);

        ProductionStockAssertions.StockSnapshot senderBefore = RelocationStockAssertions.capture(
                apiExecutor, internalSender.getId(), UserRole.ADMIN, tracked, "відправник ДО send→EXTERNAL");
        ProductionStockAssertions.StockSnapshot recipientBefore = RelocationStockAssertions.capture(
                apiExecutor, externalRecipient.getId(), UserRole.ADMIN, tracked, "EXTERNAL отримувач ДО send");

        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, internalSender.getId(), externalRecipient.getId(), resourceId, amount);

        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        ProductionStockAssertions.StockSnapshot senderAfter = RelocationStockAssertions.capture(
                apiExecutor, internalSender.getId(), UserRole.ADMIN, tracked, "відправник ПІСЛЯ send→EXTERNAL");
        ProductionStockAssertions.StockSnapshot recipientAfter = RelocationStockAssertions.capture(
                apiExecutor, externalRecipient.getId(), UserRole.ADMIN, tracked, "EXTERNAL отримувач ПІСЛЯ send");

        RelocationStockAssertions.assertDebitedFromSender(
                senderBefore, senderAfter, internalSender.getId(), resourceId, amount, "send→EXTERNAL");
        RelocationStockAssertions.assertUnchanged(
                recipientBefore, recipientAfter, externalRecipient.getId(), resourceId,
                "EXTERNAL recipient skips stock credit");
    }

    @Test(priority = 40)
    @TestCaseId("TC-INV-REL-004")
    @Story("Inventory session on EXTERNAL storage")
    @Description("""
            Що перевіряємо: ручна інвентаризація (WMS session PUT) на EXTERNAL — окремий шлях від relocation.
            Тестові дані: EXTERNAL child type=STORAGE, open session, PUT inventory resourceId→25.
            Очікування: HTTP 200; залишок=25 (relocation no-op на EXTERNAL — див. TC-INV-REL-002/005).
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testConductInventoryOnExternalStorageViaSession() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse externalStorage = storageFixture.createExternalChildStorage(parent.getId(), "inv-put-");
        double targetAmount = 25.0;

        inventoryFixture.ensureClosed(externalStorage.getId());
        inventoryFixture.openSession(externalStorage.getId());
        try {
            InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                    List.of(), java.util.Map.of(resourceId, targetAmount));
            Response response = inventoryFixture.conductInventoryRaw(
                    externalStorage.getId(), UserRole.ADMIN, request);

            assertThat(response.statusCode()).isEqualTo(200);

            double stockAfter = inventoryFixture.getResourceStock(
                    externalStorage.getId(), resourceId, UserRole.ADMIN);
            assertThat(stockAfter).isEqualTo(targetAmount);
        } finally {
            inventoryFixture.closeSession(externalStorage.getId());
        }
    }
}
