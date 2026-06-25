package com.erp.tests.functional.equipment;

import com.erp.annotations.TestCaseId;
import com.erp.enums.StorageRelation;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.test_context.ContextKey;
import com.erp.models.request.EquipmentRequest;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.storage.StorageApiTestBase;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт API селектора «Звідки». Створені локації архівуються після кожного тесту (StorageApiTestBase).
 */
@Slf4j
@Epic("Equipment")
@Feature("Equipment Form Selectors")
@Story("StorageRelation EXTERNAL filter for «Звідки»")
public class EquipmentSelectorContractTest extends StorageApiTestBase {

    private EquipmentFixture equipmentFixture;
    private Long owner1StorageId;
    private Long categoryId;
    private Long supplierId;

    @BeforeClass(alwaysRun = true)
    public void setupEquipmentSelectorTests() {
        RelocationFixture relocationFixture = new RelocationFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        equipmentFixture.prepareContext();
        owner1StorageId = com.erp.utils.config.ConfigProvider.getOwner1StorageId();
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-EQ-SEL-001")
    @Description("""
            Що перевіряємо: API-контракт dropdown «Звідки» на /equipment — фільтр relation=EXTERNAL, не type.
            Тестові дані: INTERNAL child STORAGE (eq-int-) vs EXTERNAL child STORAGE (eq-ext-);
            GET /storages/names?isActive=true&relation=EXTERNAL як у useEquipmentForm.ts.
            Очікування: EXTERNAL child у списку, INTERNAL — ні; є хоча б один SUPPLIER (джерело постачання).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testExternalNamesExcludeInternalStorages() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse internalStorage = storageFixture.createChildStorage(parent.getId(), "eq-int-");
        StorageResponse externalStorage = storageFixture.createExternalChildStorage(parent.getId(), "eq-ext-");

        List<StorageResponse> externalNames = storageFixture.getNames(
                UserRole.ADMIN, true, StorageRelation.EXTERNAL, null, null, null);

        List<Long> ids = externalNames.stream().map(StorageResponse::getId).toList();
        assertThat(ids).contains(externalStorage.getId());
        assertThat(ids).doesNotContain(internalStorage.getId());
        assertThat(externalNames.stream().anyMatch(s -> "SUPPLIER".equals(s.getType())))
                .as("EXTERNAL names include at least one SUPPLIER for equipment «Звідки»")
                .isTrue();
    }

    @Test(priority = 20)
    @TestCaseId("TC-EQ-SEL-002")
    @Description("""
            Що перевіряємо: створення обладнання з EXTERNAL sender (supplier) — позитивний шлях форми.
            Тестові дані: recipient=owner1StorageId (INTERNAL), senderStorageId=supplier з fixture,
            categoryId з fixture, унікальні name/inventoryNumber/serialNumber.
            Очікування: HTTP 201/200, equipment.storage.id = owner1StorageId.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateEquipmentFromExternalSupplier() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1StorageId, supplierId, categoryId);

        assertThat(equipment.getId()).isNotNull();
        assertThat(equipment.getStorage().getId()).isEqualTo(owner1StorageId);
    }

    @Test(priority = 30)
    @TestCaseId("TC-EQ-SEL-003")
    @Description("""
            Що перевіряємо: INTERNAL STORAGE не може бути senderStorageId (валідація type, не relation).
            Тестові дані: INTERNAL child STORAGE як sender, owner1StorageId як recipient, мінімальний EquipmentRequest.
            Очікування: HTTP 400, field=senderStorageId, повідомлення про заборону джерела.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testCreateEquipmentFromInternalStorageRejected() {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageResponse internalSender = storageFixture.createChildStorage(parent.getId(), "eq-snd-");

        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        EquipmentRequest request = EquipmentRequest.builder()
                .name("erp-invalid-sender-" + suffix)
                .inventoryNumber("INV-BAD-" + suffix)
                .serialNumber("SN-BAD-" + suffix)
                .categoryId(categoryId)
                .storageId(owner1StorageId)
                .senderStorageId(internalSender.getId())
                .build();

        Response response = apiExecutor.executeEquipmentCreate(request, UserRole.ADMIN);

        assertThat(response.statusCode()).isEqualTo(400);
        StorageFixture.assertValidationError(response, "senderStorageId", "Заборонено");
    }
}
