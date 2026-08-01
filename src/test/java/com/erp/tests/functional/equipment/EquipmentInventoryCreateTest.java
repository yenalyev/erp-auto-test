package com.erp.tests.functional.equipment;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.request.EquipmentCreateRequest;
import com.erp.models.request.EquipmentRequest;
import com.erp.models.response.InventorySessionStatus;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Створення обладнання без постачальника залежить від сесії
 * {@code /equipment-inventory/status} (не material inventory).
 */
@Slf4j
@Epic("Equipment")
@Feature("REQ-EQU-001 Equipment inventory")
public class EquipmentInventoryCreateTest extends BaseFunctionalTest {

    private InventoryFixture inventoryFixture;
    private Long storageId;
    private Long categoryId;
    private Boolean equipmentInventoryWasOpen;

    @BeforeClass(alwaysRun = true)
    public void setupEquipmentInventoryCreateTests() {
        EquipmentFixture equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        equipmentFixture.prepareCategoryContext();
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        assertThat(storageId).as("owner1 storage id").isNotNull();
        assertThat(categoryId).as("equipment category id").isNotNull();
    }

    @BeforeMethod(alwaysRun = true)
    public void snapshotEquipmentInventoryStatus() {
        InventorySessionStatus status = inventoryFixture.getEquipmentStatus(storageId, UserRole.ADMIN);
        equipmentInventoryWasOpen = Boolean.TRUE.equals(status.getOpen());
    }

    @AfterMethod(alwaysRun = true)
    public void restoreEquipmentInventoryStatus() {
        if (equipmentInventoryWasOpen == null) {
            return;
        }
        if (equipmentInventoryWasOpen) {
            inventoryFixture.openEquipmentSession(storageId);
        } else {
            inventoryFixture.ensureEquipmentClosed(storageId);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-EQU-003")
    @Story("Owner cannot create equipment without supplier when inventory closed")
    @Description("""
            Preconditions: Admin закриває сесію інвентаризації обладнання на локації Owner 1.
            Client payload: POST /api/v1/equipment multipart без senderStorageId
            (storageId + items[name, serialNumber, categoryId]).
            Очікування: HTTP 400, поле senderStorageId обов'язкове, поки сесія закрита
            (REQ-EQU-001 AC-04 / TC-EQU-003).
            """)
    @Severity(SeverityLevel.NORMAL)
    public void ownerCannotCreateWithoutSupplierWhenEquipmentInventoryClosed() {
        Allure.step("Admin закриває сесію інвентаризації обладнання", () ->
                inventoryFixture.ensureEquipmentClosed(storageId));

        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .storageId(storageId)
                .items(List.of(EquipmentRequest.builder()
                        .name("erp-nosup-" + suffix)
                        .serialNumber("SN-NOSUP-" + suffix)
                        .categoryId(categoryId)
                        .build()))
                .build();

        Response response = Allure.step(
                "Owner POST обладнання без постачальника при закритій сесії", () ->
                        apiExecutor.executeEquipmentCreate(request, UserRole.OWNER_1));

        assertThat(response.statusCode())
                .as("create without supplier rejected when equipment inventory closed; body=%s",
                        response.asString())
                .isEqualTo(400);
        assertThat(response.asString())
                .as("validation should mention senderStorageId")
                .containsIgnoringCase("senderStorageId");
    }
}
