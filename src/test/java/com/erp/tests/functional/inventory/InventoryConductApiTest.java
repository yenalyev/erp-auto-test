package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.InventoryProcessResponse;
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
        String reason = "Підозра на розбіжність залишку";
        InventoryProcessResponse requested = inventoryFixture.requestInventory(
                owner1StorageId, UserRole.OWNER_1, reason);
        inventoryFixture.approveInventory(
                owner1StorageId, UserRole.ADMIN, "Погоджено для звірки");
        double stock = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.OWNER_1);
        assertThat(stock).isGreaterThan(0);

        List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, UserRole.OWNER_1);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                        items, Map.of(anchorResourceId, stock + 1.0)).toBuilder()
                .comment("Фактичний залишок підтверджено")
                .build();
        inventoryFixture.conductInventory(owner1StorageId, UserRole.OWNER_1, request);

        assertThat(inventoryFixture.getInventoryState(owner1StorageId, UserRole.OWNER_1).getState())
                .isEqualTo("CLOSED");
        InventoryProcessResponse completed = inventoryFixture.requireInventoryProcess(
                requested.getId(), UserRole.ADMIN);
        log.info("Completed inventory process diff: {}", completed.getDiff());
        assertThat(completed.getState()).isEqualTo("CLOSED");
        assertThat(completed.getRequestedComment()).isEqualTo(reason);
        assertThat(completed.getOpenedAt()).isNotNull();
        assertThat(completed.getClosedAt()).isNotNull();
        assertThat(completed.getDiff()).isNotNull();
        assertThat(completed.getDiff().isArray()).isTrue();
        assertThat(completed.getDiff().size()).isPositive();
    }

    @Test(priority = 15)
    @TestCaseId("TC-INV-PROC-006")
    @Story("Inventory can be submitted only once")
    @Severity(SeverityLevel.CRITICAL)
    public void ownerCannotSubmitCompletedInventoryTwice() {
        inventoryFixture.requestInventory(
                owner1StorageId, UserRole.OWNER_1, "Разова контрольна звірка");
        inventoryFixture.approveInventory(
                owner1StorageId, UserRole.ADMIN, "Погоджено");
        List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, UserRole.OWNER_1);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(items, Map.of()).toBuilder()
                .comment("Результат першого проведення")
                .build();

        inventoryFixture.conductInventory(owner1StorageId, UserRole.OWNER_1, request);
        Response repeated = inventoryFixture.conductInventoryRaw(
                owner1StorageId, UserRole.OWNER_1, request);

        assertThat(repeated.statusCode()).isEqualTo(403);
        assertThat(inventoryFixture.getInventoryState(owner1StorageId, UserRole.OWNER_1).getState())
                .isEqualTo("CLOSED");
    }

    @Test(priority = 18)
    @TestCaseId("TC-INV-PROC-007")
    @Story("Completed inventory history stores before, delta and after amounts")
    @Severity(SeverityLevel.CRITICAL)
    public void completedInventoryHistoryStoresBeforeDeltaAndAfter() {
        InventoryProcessResponse requested = inventoryFixture.requestInventory(
                owner1StorageId, UserRole.OWNER_1, "Перевірка історії результату");
        inventoryFixture.approveInventory(
                owner1StorageId, UserRole.ADMIN, "Погоджено для перевірки історії");
        double before = inventoryFixture.getResourceStock(
                owner1StorageId, anchorResourceId, UserRole.OWNER_1);
        double after = before + 3.0;
        List<StorageItemResponse> items = inventoryFixture.listItems(
                owner1StorageId, UserRole.OWNER_1);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                        items, Map.of(anchorResourceId, after)).toBuilder()
                .comment("Збережено результат звірки")
                .build();

        inventoryFixture.conductInventory(owner1StorageId, UserRole.OWNER_1, request);
        InventoryProcessResponse completed = inventoryFixture.requireInventoryProcess(
                requested.getId(), UserRole.ADMIN);
        com.fasterxml.jackson.databind.JsonNode resourceDiff = completed.getDiff().findValue("resourceId");
        assertThat(resourceDiff).as("History must contain resource rows").isNotNull();
        com.fasterxml.jackson.databind.JsonNode row = completed.getDiff().findParents("resourceId").stream()
                .filter(node -> node.path("resourceId").asLong() == anchorResourceId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No diff row for resource " + anchorResourceId + ": " + completed.getDiff()));

        assertThat(row.hasNonNull("beforeAmount"))
                .as("Result row must contain 'було' (beforeAmount): %s", row)
                .isTrue();
        assertThat(row.path("diffAmount").asDouble())
                .as("Result row must contain 'що змінилося'")
                .isCloseTo(3.0, within(0.01));
        assertThat(row.hasNonNull("afterAmount"))
                .as("Result row must contain 'стало' (afterAmount): %s", row)
                .isTrue();
        assertThat(row.path("beforeAmount").asDouble()).isCloseTo(before, within(0.01));
        assertThat(row.path("afterAmount").asDouble()).isCloseTo(after, within(0.01));
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
        String reason = "Адміністративна звірка";
        InventoryProcessResponse requested = inventoryFixture.requestInventory(
                owner1StorageId, UserRole.ADMIN, reason);
        inventoryFixture.approveInventory(
                owner1StorageId, UserRole.ADMIN, "Відкрито адміністратором");
        double before = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.ADMIN);
        double target = Math.max(1.0, before - 2.0);

        List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, UserRole.ADMIN);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(anchorResourceId, target)).toBuilder()
                .comment("Результат адміністративної звірки")
                .build();
        inventoryFixture.conductInventory(owner1StorageId, UserRole.ADMIN, request);

        assertThat(inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.ADMIN))
                .isCloseTo(target, within(0.01));
        InventoryProcessResponse completed = inventoryFixture.requireInventoryProcess(
                requested.getId(), UserRole.ADMIN);
        assertThat(completed.getState()).isEqualTo("CLOSED");
        assertThat(completed.getRequestedComment()).isEqualTo(reason);
        assertThat(completed.getClosedAt()).isNotNull();
        assertThat(completed.getDiff()).isNotNull();
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

        trackStorageResourceForCleanup(newResource.getId());

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
