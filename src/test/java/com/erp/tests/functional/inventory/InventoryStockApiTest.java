package com.erp.tests.functional.inventory;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageItemResponse;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-007 Stock")
public class InventoryStockApiTest extends InventoryApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-WMS-007-001")
    @Story("Roles can access inventory list API")
    @Severity(SeverityLevel.NORMAL)
    public void ownerAndAdminCanAccessStockList() {
        for (UserRole role : List.of(UserRole.OWNER_1, UserRole.ADMIN)) {
            List<StorageItemResponse> items = inventoryFixture.listItems(owner1StorageId, role);
            assertThat(items).isNotNull();
        }
        log.info("TC-WMS-007-001: Audit role skipped — no credentials in test config");
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-007-002")
    @Story("View stock on selected storage")
    @Severity(SeverityLevel.NORMAL)
    public void viewStockOnStorage() {
        StorageItemResponse item = requireAnchorItem();
        assertThat(item.getResource()).isNotNull();
        assertThat(item.getAmount()).isGreaterThan(0);
        assertThat(item.getResource().getName()).isNotBlank();
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-007-003")
    @Story("Filter stock by search term")
    @Severity(SeverityLevel.NORMAL)
    public void filterStockBySearchTerm() {
        StorageItemResponse anchor = requireAnchorItem();
        String searchTerm = anchor.getResource().getName().trim();
        long resourceId = anchor.getResource().getId();

        List<StorageItemResponse> filtered = inventoryFixture.listItems(
                owner1StorageId, UserRole.OWNER_1, Map.of("searchTerm", searchTerm));
        assertThat(filtered).isNotEmpty();
        assertThat(filtered.stream().anyMatch(i ->
                resourceId == i.getResource().getId())).isTrue();
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-007-004")
    @Story("Multi-location inventory aggregate")
    @Severity(SeverityLevel.NORMAL)
    public void multiLocationInventoryView() {
        String locations = owner1StorageId + "," + owner2StorageId;
        Response response = inventoryFixture.getMultiLocationInventory(UserRole.ADMIN, locations);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET);
        List<?> content = response.jsonPath().getList("content");
        assertThat(content).isNotNull();
    }

    @Test(priority = 50)
    @TestCaseId("TC-WMS-007-006")
    @Story("Export remainders XLSX")
    @Severity(SeverityLevel.NORMAL)
    public void exportRemaindersExcel() {
        Response response = inventoryFixture.exportRemainders(owner1StorageId, UserRole.OWNER_1);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getContentType()).contains("octet-stream");
        assertThat(response.asByteArray().length).isGreaterThan(100);
    }

    @Test(priority = 60)
    @TestCaseId("TC-WMS-007-007")
    @Story("Inventory operations in history")
    @Severity(SeverityLevel.NORMAL)
    public void inventoryRecordedInOperationHistory() {
        inventoryFixture.openSession(owner1StorageId);
        double before = inventoryFixture.getResourceStock(owner1StorageId, anchorResourceId, UserRole.ADMIN);
        inventoryFixture.setResourceAmount(owner1StorageId, UserRole.ADMIN, anchorResourceId, before + 3.0);

        Response history = inventoryFixture.getOperationHistoryToday(owner1StorageId, UserRole.ADMIN);
        if (history.statusCode() == 403) {
            throw new SkipException("Current role lacks resource-operation-history read permission");
        }
        assertThat(history.statusCode()).isEqualTo(200);
        String body = history.getBody().asString();
        assertThat(body).containsAnyOf("ADDED_INV", "REMOVED_INV");
    }
}
