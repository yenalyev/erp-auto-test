package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.response.InventorySessionStatus;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-003 Session")
public class InventorySessionApiTest extends InventoryApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-WMS-003-001")
    @Story("Admin opens inventory session")
    @Severity(SeverityLevel.CRITICAL)
    public void adminOpensInventorySession() {
        InventorySessionStatus opened = inventoryFixture.openSession(owner1StorageId);
        assertThat(opened.getOpen()).isTrue();

        Response getResponse = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_GET,
                UserRole.ADMIN,
                String.valueOf(owner1StorageId));
        assertThat(getResponse.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(getResponse, ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_GET);
        assertThat(getResponse.as(InventorySessionStatus.class).getOpen()).isTrue();
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-003-002")
    @Story("Admin closes inventory session")
    @Severity(SeverityLevel.CRITICAL)
    public void adminClosesInventorySession() {
        inventoryFixture.openSession(owner1StorageId);
        InventorySessionStatus closed = inventoryFixture.closeSession(owner1StorageId);
        assertThat(closed.getOpen()).isFalse();
        assertThat(inventoryFixture.getStatus(owner1StorageId, UserRole.ADMIN).getOpen()).isFalse();
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-003-003")
    @Story("Owner cannot open session")
    @Severity(SeverityLevel.CRITICAL)
    public void ownerCannotOpenInventorySession() {
        Response response = inventoryFixture.putStatus(owner1StorageId, UserRole.OWNER_1, true);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(inventoryFixture.getStatus(owner1StorageId, UserRole.ADMIN).getOpen()).isFalse();
    }
}
