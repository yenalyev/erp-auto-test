package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.response.InventoryProcessResponse;
import com.erp.models.response.InventorySessionStatus;
import com.erp.models.response.InventoryStateResponse;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-003 Inventory request workflow")
public class InventorySessionApiTest extends InventoryApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-INV-PROC-001")
    @Story("Location owner requests inventory with a reason")
    @Severity(SeverityLevel.CRITICAL)
    public void ownerRequestsInventoryWithReason() {
        String reason = "Розбіжність фактичного та облікового залишку";
        InventoryProcessResponse requested = inventoryFixture.requestInventory(
                owner1StorageId,
                UserRole.OWNER_1,
                reason);

        assertThat(requested.getId()).isNotNull();
        assertThat(requested.getStorage()).isNotNull();
        assertThat(requested.getStorage().getId()).isEqualTo(owner1StorageId);
        assertThat(requested.getRequestedAt()).isNotNull();
        assertThat(requested.getRequestedBy()).isNotBlank();

        Response getResponse = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_GET,
                UserRole.OWNER_1,
                String.valueOf(owner1StorageId));
        assertThat(getResponse.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(getResponse, ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_GET);
        InventoryStateResponse state = getResponse.as(InventoryStateResponse.class);
        assertThat(state.getState()).isEqualTo("REQUESTED");

        InventoryProcessResponse history = inventoryFixture.requireInventoryProcess(
                requested.getId(), UserRole.ADMIN);
        assertThat(history.getState()).isEqualTo("REQUESTED");
        assertThat(history.getRequestedComment()).isEqualTo(reason);
    }

    @Test(priority = 20)
    @TestCaseId("TC-INV-PROC-002")
    @Story("Admin approves a pending inventory request")
    @Severity(SeverityLevel.CRITICAL)
    public void adminApprovesInventoryRequest() {
        InventoryProcessResponse requested = inventoryFixture.requestInventory(
                owner1StorageId,
                UserRole.OWNER_1,
                "Планова звірка залишків");

        InventoryProcessResponse opened = inventoryFixture.approveInventory(
                owner1StorageId,
                UserRole.ADMIN,
                "Погоджено адміністратором");

        assertThat(opened.getId()).isEqualTo(requested.getId());
        assertThat(opened.getOpenedAt()).isNotNull();
        assertThat(opened.getResolutionBy()).isNotBlank();
        assertThat(opened.getResolutionComment()).isEqualTo("Погоджено адміністратором");
        assertThat(inventoryFixture.getInventoryState(owner1StorageId, UserRole.OWNER_1).getState())
                .isEqualTo("OPEN");
    }

    @Test(priority = 30)
    @TestCaseId("TC-INV-PROC-003")
    @Story("Admin rejects a pending inventory request with an explanation")
    @Severity(SeverityLevel.CRITICAL)
    public void adminRejectsInventoryRequestWithExplanation() {
        InventoryProcessResponse requested = inventoryFixture.requestInventory(
                owner1StorageId,
                UserRole.OWNER_1,
                "Позапланова перевірка");
        String explanation = "Спочатку завершіть активне переміщення";

        InventoryProcessResponse rejected = inventoryFixture.rejectInventory(
                owner1StorageId,
                UserRole.ADMIN,
                explanation);

        assertThat(rejected.getId()).isEqualTo(requested.getId());
        assertThat(rejected.getState()).isEqualTo("REJECTED");
        assertThat(rejected.getResolutionBy()).isNotBlank();
        assertThat(rejected.getResolutionComment()).isEqualTo(explanation);
        InventoryStateResponse state = inventoryFixture.getInventoryState(owner1StorageId, UserRole.OWNER_1);
        assertThat(state.getState()).isEqualTo("REJECTED");
        assertThat(state.getComment()).isEqualTo(explanation);
    }

    @Test(priority = 40)
    @TestCaseId("TC-INV-PROC-004")
    @Story("Location owner cannot approve a request")
    @Severity(SeverityLevel.CRITICAL)
    public void ownerCannotApproveInventoryRequest() {
        inventoryFixture.requestInventory(
                owner1StorageId,
                UserRole.OWNER_1,
                "Контрольна звірка");

        Response response = inventoryFixture.approveInventoryRaw(
                owner1StorageId,
                UserRole.OWNER_1,
                "Самостійне погодження");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(inventoryFixture.getInventoryState(owner1StorageId, UserRole.ADMIN).getState())
                .isEqualTo("REQUESTED");
    }

    @Test(priority = 50)
    @TestCaseId("TC-INV-PROC-005")
    @Story("Inventory request reason is mandatory")
    @Severity(SeverityLevel.CRITICAL)
    public void inventoryRequestReasonIsMandatory() {
        Response response = inventoryFixture.requestInventoryRaw(
                owner1StorageId,
                UserRole.OWNER_1,
                "   ");

        assertThat(response.statusCode()).isEqualTo(400);
        InventorySessionStatus status = inventoryFixture.getStatus(owner1StorageId, UserRole.ADMIN);
        assertThat(status.getOpen()).isFalse();
    }
}
