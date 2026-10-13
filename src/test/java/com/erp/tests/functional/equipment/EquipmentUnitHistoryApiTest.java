package com.erp.tests.functional.equipment;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.models.request.EquipmentRelocationSendEditRequest;
import com.erp.models.response.EquipmentHistoryResponse;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Equipment")
@Feature("REQ-EQU-004 Unit history")
public class EquipmentUnitHistoryApiTest extends BaseFunctionalTest {

    private EquipmentFixture equipmentFixture;
    private Long senderStorageId;
    private Long recipientStorageId;
    private Long categoryId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareEquipmentHistory() {
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        equipmentFixture.prepareContext();
        senderStorageId = ConfigProvider.getOwner1StorageId();
        recipientStorageId = ConfigProvider.getOwner2StorageId();
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
    }

    @Test
    @TestCaseId("TC-EQ-HIST-API-001")
    @Story("Cancelled transfer leaves no sent event in equipment history")
    public void cancellationRemovesSentFromUnitHistory() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, senderStorageId, categoryId);
        RelocationResponse transfer = equipmentFixture.sendEquipment(
                UserRole.ADMIN, senderStorageId, recipientStorageId, equipment.getId());

        assertThat(sentCount(equipment.getId())).isEqualTo(1);
        equipmentFixture.resolveEquipment(
                UserRole.ADMIN, transfer.getId(), senderStorageId, RelocationState.RETURNED);

        List<EquipmentHistoryResponse> history = unitHistory(equipment.getId());
        assertThat(history).isNotEmpty();
        assertThat(history.stream().filter(item -> "SENT".equals(item.getOperation())).count())
                .as("Скасоване передавання не лишає запису «Відправлено»")
                .isZero();
    }

    @Test
    @TestCaseId("TC-EQ-HIST-API-002")
    @Story("Editing transfer keeps a single sent event")
    public void editDoesNotDuplicateSentInUnitHistory() {
        EquipmentResponse equipment = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, senderStorageId, categoryId);
        RelocationResponse transfer = equipmentFixture.sendEquipment(
                UserRole.ADMIN, senderStorageId, recipientStorageId, equipment.getId());

        EquipmentRelocationSendEditRequest edit = EquipmentRelocationSendEditRequest.builder()
                .toStorageId(recipientStorageId)
                .equipmentIds(List.of(equipment.getId()))
                .date(LocalDate.now())
                .description("Оновлений опис передавання")
                .build();
        equipmentFixture.editEquipmentSend(UserRole.ADMIN, transfer.getId(), senderStorageId, edit);

        assertThat(sentCount(equipment.getId()))
                .as("Редагування не створює дубль «Відправлено»")
                .isEqualTo(1);
        equipmentFixture.resolveEquipment(
                UserRole.ADMIN, transfer.getId(), senderStorageId, RelocationState.RETURNED);
        assertThat(sentCount(equipment.getId()))
                .as("Після скасування не лишається «Відправлено»")
                .isZero();
    }

    private long sentCount(Long equipmentId) {
        return unitHistory(equipmentId).stream()
                .filter(item -> "SENT".equals(item.getOperation()))
                .count();
    }

    private List<EquipmentHistoryResponse> unitHistory(Long equipmentId) {
        return apiExecutor.execute(
                        ApiEndpointDefinition.EQUIPMENT_GET_UNIT_HISTORY,
                        UserRole.ADMIN,
                        null,
                        equipmentId)
                .jsonPath().getList("$", EquipmentHistoryResponse.class);
    }
}
