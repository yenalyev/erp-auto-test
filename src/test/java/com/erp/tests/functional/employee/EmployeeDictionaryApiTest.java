package com.erp.tests.functional.employee;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EmployeeFixture;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.request.EmployeeRequest;
import com.erp.models.response.EmployeeResponse;
import com.erp.models.response.EquipmentResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Словник співробітників — унікальність активного позивного в межах локації.
 */
@Slf4j
@Epic("Master Data")
@Feature("Employees")
@Story("Duplicate call sign guard")
public class EmployeeDictionaryApiTest extends BaseFunctionalTest {

    private EmployeeFixture employeeFixture;
    private long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupEmployeeDictionaryTests() {
        employeeFixture = new EmployeeFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
    }

    @Test(priority = 10)
    @TestCaseId("TC-DICT-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-DICT-001 AC-03 / CPMA-431: не можна додати другого активного співробітника
            з тим самим позивним на ту саму локацію.
            """)
    public void cannotCreateDuplicateActiveCallSignOnSameLocation() {
        String callSign = "dict-dup-" + System.currentTimeMillis() % 1_000_000;

        EmployeeResponse first = employeeFixture.createEmployee(UserRole.ADMIN, storageId, callSign);
        assertThat(first.getId()).isNotNull();
        assertThat(first.getCallSign()).isEqualTo(callSign);

        Response duplicate = employeeFixture.createEmployeeRaw(UserRole.ADMIN, storageId, callSign);
        assertThat(duplicate.statusCode())
                .as("дублікат позивного на локації; body=%s", duplicate.asString())
                .isBetween(400, 499);
    }

    @Test(priority = 20)
    @TestCaseId("TC-DICT-002")
    @Story("Employee CRUD")
    @Severity(SeverityLevel.CRITICAL)
    @Description("REQ-DICT-001 AC-01: створити / оновити / видалити співробітника.")
    public void createUpdateDeleteEmployee() {
        String callSign = "dict-crud-" + System.currentTimeMillis() % 1_000_000;
        EmployeeResponse created = employeeFixture.createEmployee(UserRole.ADMIN, storageId, callSign);
        assertThat(created.getId()).isNotNull();

        EmployeeRequest update = EmployeeRequest.builder()
                .callSign(callSign + "-u")
                .storageIds(List.of(storageId))
                .active(true)
                .build();
        EmployeeResponse updated = employeeFixture.update(UserRole.ADMIN, created.getId(), update);
        assertThat(updated.getCallSign()).isEqualTo(callSign + "-u");

        Response deleted = employeeFixture.deleteRaw(UserRole.ADMIN, created.getId());
        assertThat(deleted.statusCode()).isIn(200, 204);
    }

    @Test(priority = 30)
    @TestCaseId("TC-DICT-004")
    @Story("Delete blocked when equipment assigned")
    @Severity(SeverityLevel.CRITICAL)
    @Description("REQ-DICT-001 AC-04: не можна видалити співробітника з закріпленим обладнанням.")
    public void cannotDeleteEmployeeWithAssignedEquipment() {
        String callSign = "dict-eq-" + System.currentTimeMillis() % 1_000_000;
        EmployeeResponse employee = employeeFixture.createEmployee(UserRole.ADMIN, storageId, callSign);

        EquipmentFixture equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        equipmentFixture.prepareCategoryContext();
        Long categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        InventoryFixture inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        inventoryFixture.openEquipmentSession(storageId);
        try {
            EquipmentResponse equipment = equipmentFixture.createEquipmentOnStorage(
                    UserRole.ADMIN, storageId, categoryId);
            equipmentFixture.assignEquipment(UserRole.ADMIN, equipment.getId(), employee.getId());
            Response deleted = employeeFixture.deleteRaw(UserRole.ADMIN, employee.getId());
            assertThat(deleted.statusCode())
                    .as("delete employee with assigned equipment; body=%s", deleted.asString())
                    .isBetween(400, 499);
        } finally {
            inventoryFixture.ensureEquipmentClosed(storageId);
        }
    }
}
