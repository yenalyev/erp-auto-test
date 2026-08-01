package com.erp.tests.functional.employee;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EmployeeFixture;
import com.erp.models.response.EmployeeResponse;
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
}
