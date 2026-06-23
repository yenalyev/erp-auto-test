package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Equipment Relocation UI")
public class EquipmentRelocationUITest extends BaseUITest {

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        new RelocationFixture(testContext, apiExecutor).prepareContext();
        new EquipmentFixture(testContext, apiExecutor).prepareContext();

        long storageId = ConfigProvider.getOwner1StorageId();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    @Test
    @TestCaseId("TC-UI-REL-EQ-001")
    @Story("Equipment relocation journal smoke")
    public void equipmentRelocationJournalAccessible() {
        RelocationPage pageObj = new RelocationPage(page);
        pageObj.open();
        pageObj.attachScreenshot("Relocations with equipment tab");
        assertThat(pageObj.isSendButtonVisible()).isTrue();
    }

    @Test
    @TestCaseId("TC-UI-REL-EQ-002")
    @Story("Equipment resolve FINISHED via API + UI journal")
    public void equipmentResolveFinishedShowsInJournal() {
        EquipmentFixture equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        long owner1 = ConfigProvider.getOwner1StorageId();
        long owner2 = ConfigProvider.getOwner2StorageId();
        Long categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);

        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1, categoryId).getId();
        RelocationResponse relocation = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1, owner2, equipmentId);
        equipmentFixture.resolveEquipment(
                UserRole.OWNER_2, relocation.getId(), owner2, RelocationState.FINISHED);

        EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_2, owner2, equipmentId);
        assertThat(status).isEqualTo(EquipmentStatus.AVAILABLE);

        RelocationPage pageObj = new RelocationPage(page);
        pageObj.open().openHistoryTab();
        pageObj.attachScreenshot("Equipment relocation history");
    }

    @Test
    @TestCaseId("TC-UI-REL-EQ-003")
    @Story("Equipment edit receive removeInvoiceFile")
    public void equipmentEditReceiveRemoveInvoiceFile() {
        EquipmentFixture equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        long storageId = ConfigProvider.getOwner1StorageId();
        Long categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        Long supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);

        var equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, storageId, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, storageId, equipment.getId());

        var request = com.erp.models.request.EquipmentRelocationReceiveEditRequest.builder()
                .fromStorageId(supplierId)
                .equipmentIds(java.util.List.of(equipment.getId()))
                .date(java.time.LocalDate.now())
                .description("ui eq edit")
                .removeInvoiceFile(true)
                .build();
        equipmentFixture.editEquipmentReceive(UserRole.ADMIN, relocationId, storageId, request);

        RelocationPage pageObj = new RelocationPage(page);
        pageObj.open().openReceivedHistoryTab();
        pageObj.attachScreenshot("Equipment receive after edit");
    }
}
