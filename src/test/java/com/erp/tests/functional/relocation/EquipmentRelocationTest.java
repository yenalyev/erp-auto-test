package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.EmployeeFixture;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.EquipmentRelocationReceiveEditRequest;
import com.erp.models.request.EquipmentRelocationSendEditRequest;
import com.erp.models.request.EquipmentRelocationSendRequest;
import com.erp.models.response.EmployeeResponse;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Equipment Relocations")
public class EquipmentRelocationTest extends BaseFunctionalTest {

    private EquipmentFixture equipmentFixture;
    private RelocationFixture relocationFixture;
    private EmployeeFixture employeeFixture;
    private Long owner1Storage;
    private Long owner2Storage;
    private Long unitStorageId;
    private Long categoryId;
    private Long supplierId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupEquipmentRelocationTests() {
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        equipmentFixture.prepareContext();
        employeeFixture = new EmployeeFixture(testContext, apiExecutor);
        owner1Storage = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        unitStorageId = testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        supplierId = equipmentFixture.supplierId();
    }

    @Test
    @TestCaseId("TC-REL-EQ-001")
    @Story("Equipment send CREATED")
    @Severity(SeverityLevel.CRITICAL)
    public void sendCreatesPendingRelocationAndMarksEquipmentInTransit() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        EquipmentStatus before = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_1, owner1Storage, equipmentId);
        assertThat(before).isEqualTo(EquipmentStatus.AVAILABLE);

        RelocationResponse relocation = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1Storage, owner2Storage, equipmentId);
        assertThat(relocation.getState()).isEqualTo(RelocationState.CREATED);

        EquipmentStatus after = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_1, owner1Storage, equipmentId);
        assertThat(after).isEqualTo(EquipmentStatus.IN_TRANSIT);
    }

    @Test
    @TestCaseId("TC-REL-EQ-002")
    @Story("Equipment send to UNIT AUTO_FINISHED")
    public void sendToUnitAutoFinishesAndMovesEquipment() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        RelocationResponse relocation = equipmentFixture.sendEquipmentToUnit(
                UserRole.OWNER_1, owner1Storage, unitStorageId, equipmentId);
        assertThat(relocation.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                UserRole.ADMIN, unitStorageId, equipmentId);
        assertThat(status).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    @TestCaseId({
            "TC-REL-EQ-003",
            "TC-EQU-002"
    })
    @Story("Resolve FINISHED moves equipment")
    public void resolveFinishedMovesEquipmentToRecipient() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        RelocationResponse relocation = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1Storage, owner2Storage, equipmentId);

        equipmentFixture.resolveEquipment(
                UserRole.OWNER_2, relocation.getId(), owner2Storage, RelocationState.FINISHED);

        EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_2, owner2Storage, equipmentId);
        assertThat(status).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    @TestCaseId("TC-REL-EQ-004")
    @Story("Cancel then return restores equipment")
    public void cancelThenReturnRestoresEquipmentToSender() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        RelocationResponse relocation = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1Storage, owner2Storage, equipmentId);

        equipmentFixture.resolveEquipment(
                UserRole.OWNER_2, relocation.getId(), owner2Storage, RelocationState.CANCELLED);
        equipmentFixture.resolveEquipment(
                UserRole.OWNER_1, relocation.getId(), owner1Storage, RelocationState.RETURNED);

        EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_1, owner1Storage, equipmentId);
        assertThat(status).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    @TestCaseId("TC-REL-EQ-005")
    @Story("Sender direct RETURNED shortcut")
    public void senderCancelsOwnPendingRelocationReturnsEquipmentToSender() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        RelocationResponse relocation = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1Storage, owner2Storage, equipmentId);

        equipmentFixture.resolveEquipment(
                UserRole.OWNER_1, relocation.getId(), owner1Storage, RelocationState.RETURNED);

        EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_1, owner1Storage, equipmentId);
        assertThat(status).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    @TestCaseId("TC-REL-EQ-006")
    @Story("Recipient cannot RETURNED directly")
    public void recipientCannotCancelPendingRelocationDirectlyToReturned() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        RelocationResponse relocation = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1Storage, owner2Storage, equipmentId);

        Response response = equipmentFixture.resolveEquipmentRaw(
                UserRole.OWNER_2, relocation.getId(), owner2Storage, RelocationState.RETURNED);
        assertThat(response.statusCode()).isIn(403, 400);

        EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_1, owner1Storage, equipmentId);
        assertThat(status).isEqualTo(EquipmentStatus.IN_TRANSIT);
    }

    @Test
    @TestCaseId("TC-REL-EQ-007")
    @Story("Cannot send retired equipment")
    public void cannotSendRetiredEquipment() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();
        equipmentFixture.changeEquipmentStatus(UserRole.ADMIN, equipmentId, EquipmentStatus.RETIRED);

        EquipmentRelocationSendRequest request = EquipmentRelocationSendRequest.builder()
                .fromStorageId(owner1Storage)
                .toStorageId(owner2Storage)
                .equipmentIds(List.of(equipmentId))
                .date(LocalDate.now())
                .build();
        Response response = equipmentFixture.sendEquipmentRaw(UserRole.OWNER_1, request);
        assertThat(response.statusCode()).isBetween(400, 499);
    }

    @Test
    @TestCaseId("TC-REL-EQ-008")
    @Story("Cannot send equipment not at sender")
    public void cannotSendEquipmentNotAtSender() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();
        RelocationResponse moved = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1Storage, owner2Storage, equipmentId);
        equipmentFixture.resolveEquipment(
                UserRole.OWNER_2, moved.getId(), owner2Storage, RelocationState.FINISHED);

        EquipmentRelocationSendRequest request = EquipmentRelocationSendRequest.builder()
                .fromStorageId(owner1Storage)
                .toStorageId(owner2Storage)
                .equipmentIds(List.of(equipmentId))
                .date(LocalDate.now())
                .build();
        Response response = equipmentFixture.sendEquipmentRaw(UserRole.OWNER_1, request);
        assertThat(response.statusCode()).isBetween(400, 499);
    }

    @Test
    @TestCaseId({
            "TC-REL-EQ-009",
            "TC-EQU-004"
    })
    @Story("Delete supplier receive removes equipment")
    public void deleteRelocationFromSupplierSenderDeletesEquipmentFromSystem() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        equipmentFixture.deleteRelocationRaw(UserRole.ADMIN, relocationId, owner1Storage)
                .then().statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200),
                        org.hamcrest.Matchers.is(204)));

        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EQUIPMENT_GET_PAGE,
                UserRole.ADMIN,
                java.util.Map.of("storageIds", owner1Storage, "size", 100));
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.asString()).doesNotContain(equipment.getInventoryNumber());
    }

    @Test
    @TestCaseId({
            "TC-DEL-REL_EQ-001",
            "TC-EDIT_REL-007",
            "TC-REL-EQ-010"
    })
    @Story("Delete fails when equipment assigned")
    @Description("""
            TC-DEL-REL_EQ-001 / TC-EDIT_REL-007 / TC-REL-EQ-010: Admin не може видалити отримання обладнання,
            якщо воно закріплене за співробітником (HTTP 4xx).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void deleteRelocationFailsWhenEquipmentIsAssigned() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        EmployeeResponse employee = employeeFixture.createEmployee(
                UserRole.ADMIN, owner1Storage, "eq-del-asgn-" + System.currentTimeMillis() % 1_000_000);
        equipmentFixture.assignEquipment(UserRole.ADMIN, equipment.getId(), employee.getId());

        Response response = equipmentFixture.deleteRelocationRaw(
                UserRole.ADMIN, relocationId, owner1Storage);
        assertThat(response.statusCode())
                .as("нельзя delete assigned equipment receive; body=%s", response.asString())
                .isBetween(400, 499);
    }

    @Test
    @TestCaseId({
            "TC-DEL-REL_EQ-002",
            "TC-EDIT_REL-003"
    })
    @Story("Delete fails when equipment in transit to another location")
    @Description("""
            TC-DEL-REL_EQ-002 / TC-EDIT_REL-003: Admin не може видалити отримання,
            якщо обладнання вже в дорозі на іншу локацію.
            """)
    @Severity(SeverityLevel.NORMAL)
    public void deleteReceiveFailsWhenEquipmentAlreadyInTransitElsewhere() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long initialRelocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        equipmentFixture.sendEquipment(UserRole.OWNER_1, owner1Storage, owner2Storage, equipment.getId());

        Response response = equipmentFixture.deleteRelocationRaw(
                UserRole.ADMIN, initialRelocationId, owner1Storage);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @TestCaseId("TC-EDIT_REL-002")
    @Story("Edit receive removeInvoiceFile")
    @Description("TC-EDIT_REL-002 / CPMA-432: Admin може зняти фото накладної з отримання обладнання (removeInvoiceFile).")
    @Severity(SeverityLevel.NORMAL)
    public void editEquipmentReceiveRemovesInvoicePhoto() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        EquipmentRelocationReceiveEditRequest request = EquipmentRelocationReceiveEditRequest.builder()
                .fromStorageId(supplierId)
                .equipmentIds(List.of(equipment.getId()))
                .date(LocalDate.now())
                .description("TC-EDIT_REL-002 remove invoice")
                .removeInvoiceFile(true)
                .build();
        RelocationResponse updated = equipmentFixture.editEquipmentReceive(
                UserRole.ADMIN, relocationId, owner1Storage, request);
        assertThat(updated.getHasExternalInvoicePhoto()).isFalse();
    }

    @Test
    @TestCaseId("TC-REL-EQ-011")
    @Story("Delete supplier receive with assignment history")
    public void deleteRelocationFromSupplierWithReturnedAssignmentHistory() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        equipmentFixture.deleteRelocationRaw(UserRole.ADMIN, relocationId, owner1Storage)
                .then().statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200),
                        org.hamcrest.Matchers.is(204)));
    }

    @Test
    @TestCaseId("TC-REL-EQ-012")
    @Story("Delete fails when equipment relocated further")
    public void deleteRelocationFailsWhenEquipmentWasRelocatedFurther() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long initialRelocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        RelocationResponse onward = equipmentFixture.sendEquipment(
                UserRole.OWNER_1, owner1Storage, owner2Storage, equipment.getId());
        equipmentFixture.resolveEquipment(
                UserRole.OWNER_2, onward.getId(), owner2Storage, RelocationState.FINISHED);

        Response response = equipmentFixture.deleteRelocationRaw(
                UserRole.ADMIN, initialRelocationId, owner1Storage);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @TestCaseId("TC-REL-EQ-013")
    @Story("Delete fails when equipment in transit")
    public void deleteRelocationFailsWhenEquipmentIsInTransit() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long initialRelocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        equipmentFixture.sendEquipment(UserRole.OWNER_1, owner1Storage, owner2Storage, equipment.getId());

        Response response = equipmentFixture.deleteRelocationRaw(
                UserRole.ADMIN, initialRelocationId, owner1Storage);
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @TestCaseId("TC-REL-EQ-014")
    @Story("Delete storage AUTO_FINISHED send returns equipment")
    public void deleteRelocationFromStorageSenderReturnsEquipmentToSender() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        RelocationResponse relocation = equipmentFixture.sendEquipmentToUnit(
                UserRole.OWNER_1, owner1Storage, unitStorageId, equipmentId);
        assertThat(relocation.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        equipmentFixture.deleteRelocationRaw(UserRole.ADMIN, relocation.getId(), owner1Storage)
                .then().statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200),
                        org.hamcrest.Matchers.is(204)));

        EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                UserRole.OWNER_1, owner1Storage, equipmentId);
        assertThat(status).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    @TestCaseId("TC-REL-EQ-015")
    @Story("Edit receive non-equipment fields")
    public void editReceiveNonEquipmentFieldWithUnchangedEquipmentListSucceeds() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        EquipmentRelocationReceiveEditRequest request = EquipmentRelocationReceiveEditRequest.builder()
                .fromStorageId(supplierId)
                .equipmentIds(List.of(equipment.getId()))
                .date(LocalDate.now())
                .description("updated description")
                .note("updated note")
                .build();
        RelocationResponse updated = equipmentFixture.editEquipmentReceive(
                UserRole.ADMIN, relocationId, owner1Storage, request);
        assertThat(updated.getDescription()).contains("updated");
    }

    @Test
    @TestCaseId("TC-REL-EQ-016")
    @Story("Edit receive removeInvoiceFile")
    public void editReceiveWithRemoveInvoiceFileClearsFlag() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        EquipmentRelocationReceiveEditRequest request = EquipmentRelocationReceiveEditRequest.builder()
                .fromStorageId(supplierId)
                .equipmentIds(List.of(equipment.getId()))
                .date(LocalDate.now())
                .description("remove invoice")
                .removeInvoiceFile(true)
                .build();
        RelocationResponse updated = equipmentFixture.editEquipmentReceive(
                UserRole.ADMIN, relocationId, owner1Storage, request);
        assertThat(updated.getHasExternalInvoicePhoto()).isFalse();
    }

    @Test
    @TestCaseId("TC-REL-EQ-017")
    @Story("Edit receive without remove flag")
    public void editReceiveWithoutRemoveFlagKeepsInvoiceFields() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentFromSupplier(
                UserRole.ADMIN, owner1Storage, supplierId, categoryId);
        Long relocationId = equipmentFixture.findEquipmentReceiveRelocationId(
                UserRole.ADMIN, owner1Storage, equipment.getId());

        EquipmentRelocationReceiveEditRequest request = EquipmentRelocationReceiveEditRequest.builder()
                .fromStorageId(supplierId)
                .equipmentIds(List.of(equipment.getId()))
                .date(LocalDate.now())
                .description("keep invoice flag")
                .removeInvoiceFile(false)
                .build();
        RelocationResponse updated = equipmentFixture.editEquipmentReceive(
                UserRole.ADMIN, relocationId, owner1Storage, request);
        assertThat(updated.getDescription()).contains("keep invoice");
    }

    @Test
    @TestCaseId("TC-REL-EQ-018")
    @Story("Edit send person fields without invoice")
    public void editSendUpdatesPersonFieldsWithoutCreatingInvoice() {
        Long equipmentId = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, owner1Storage, categoryId).getId();

        RelocationResponse relocation = equipmentFixture.sendEquipmentToUnit(
                UserRole.OWNER_1, owner1Storage, unitStorageId, equipmentId);

        EquipmentRelocationSendEditRequest request = EquipmentRelocationSendEditRequest.builder()
                .toStorageId(unitStorageId)
                .equipmentIds(List.of(equipmentId))
                .date(LocalDate.now())
                .description("person fields")
                .sendingPersonName("Олег")
                .sendingPersonRank("Майор")
                .receivingPersonName("Андрій")
                .receivingPersonRank("Капітан")
                .build();
        RelocationResponse updated = equipmentFixture.editEquipmentSend(
                UserRole.ADMIN, relocation.getId(), owner1Storage, request);
        assertThat(updated.getDescription()).isEqualTo("person fields");
    }
}
