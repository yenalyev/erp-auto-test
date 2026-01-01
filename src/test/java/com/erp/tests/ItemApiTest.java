package com.erp.tests;


import io.qameta.allure.*;
import org.testng.annotations.Test;


@Epic("Inventory Management")
@Feature("Items API")
public class ItemApiTest extends BaseTest {

    @Test
    @Story("REQ-INV-001: Create new item")
//    @Severity()
    @Description("Test creating a new inventory item via POST /api/items")
    public void testCreateItem() {

    }

    @Test
    @Story("REQ-INV-002: Get item by ID")
//    @Severity(SeverityLevel.NORMAL)
    @Link(name = "BUG-123", url = "https://jira.company.com/BUG-123", type = "issue")
    public void testGetItemById() {
        // Your test code
    }
}
