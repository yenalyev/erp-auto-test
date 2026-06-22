package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.test_context.ContextKey;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-003 Conduct")
public class InventoryConductApiTest extends InventoryApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-WMS-003-005")
    @Story("Owner can conduct when session open")
    @Severity(SeverityLevel.CRITICAL)
    public void ownerCanConductWhenSessionOpen() {
        inventoryFixture.openSession(owner1StorageId);
        double stock = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.OWNER_1);
        assertThat(stock).isGreaterThan(0);

        List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, UserRole.OWNER_1);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(items, Map.of());
        inventoryFixture.conductInventory(owner1StorageId, UserRole.OWNER_1, request);

        assertThat(inventoryFixture.getStatus(owner1StorageId, UserRole.OWNER_1).getOpen()).isTrue();
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-003-006")
    @Story("Owner updates resource amount")
    @Severity(SeverityLevel.CRITICAL)
    public void ownerUpdatesResourceAmount() {
        inventoryFixture.openSession(owner1StorageId);
        double before = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.OWNER_1);
        double target = before + 5.0;

        inventoryFixture.setResourceAmount(owner1StorageId, UserRole.OWNER_1, anchorResourceId, target);

        assertThat(inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.OWNER_1))
                .isCloseTo(target, within(0.01));
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-003-007")
    @Story("Admin updates resource amount")
    @Severity(SeverityLevel.CRITICAL)
    public void adminUpdatesResourceAmount() {
        inventoryFixture.openSession(owner1StorageId);
        double before = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.ADMIN);
        double target = Math.max(1.0, before - 2.0);

        inventoryFixture.setResourceAmount(owner1StorageId, UserRole.ADMIN, anchorResourceId, target);

        assertThat(inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.ADMIN))
                .isCloseTo(target, within(0.01));
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-003-008")
    @Story("Conduct blocked when session closed")
    @Severity(SeverityLevel.CRITICAL)
    public void conductBlockedWhenSessionClosed() {
        double amount = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.OWNER_1);
        InventoryRequest request = InventoryDataFactory.seedAmounts(
                Map.of(anchorResourceId, amount + 1.0));

        Response response = inventoryFixture.conductInventoryRaw(
                owner1StorageId, UserRole.OWNER_1, request);
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test(priority = 50)
    @TestCaseId("TC-WMS-003-009")
    @Story("Add resource not previously on storage")
    @Severity(SeverityLevel.CRITICAL)
    public void addResourceNotOnStorage() {
        inventoryFixture.openSession(owner1StorageId);
        List<ResourceResponse> catalog = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        ResourceResponse newResource = inventoryFixture.pickResourceNotOnStorage(
                owner1StorageId, UserRole.ADMIN, catalog);

        List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(newResource.getId(), 5.0));
        inventoryFixture.conductInventory(owner1StorageId, UserRole.ADMIN, request);

        assertThat(inventoryFixture.getResourceStock(owner1StorageId, newResource.getId(), UserRole.ADMIN))
                .isCloseTo(5.0, within(0.01));
    }

    @Test(priority = 60)
    @TestCaseId("TC-WMS-003-010")
    @Story("Remove resource from storage via inventory")
    @Severity(SeverityLevel.CRITICAL)
    public void removeResourceFromStorage() {
        inventoryFixture.openSession(owner1StorageId);
        relocationFixture.ensureStock(owner1StorageId, anchorResourceId, 10.0);

        List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.copyExcept(items, anchorResourceId);
        inventoryFixture.conductInventory(owner1StorageId, UserRole.ADMIN, request);

        assertThat(inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.ADMIN))
                .isCloseTo(0.0, within(0.01));
    }

    @Test(priority = 70)
    @TestCaseId("TC-WMS-003-011")
    @Story("Owner 2 cannot conduct on Owner 1 storage")
    @Severity(SeverityLevel.CRITICAL)
    public void owner2CannotConductOnOwner1Storage() {
        inventoryFixture.openSession(owner1StorageId);
        List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(items, Map.of());

        Response response = inventoryFixture.conductInventoryRaw(
                owner1StorageId, UserRole.OWNER_2, request);
        assertThat(response.statusCode()).isEqualTo(403);
    }
}
