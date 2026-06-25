package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EmployeeFixture;
import com.erp.fixtures.EquipmentFixture;
import com.erp.models.response.EmployeeResponse;
import com.erp.models.response.EquipmentGroupResponse;
import com.erp.models.response.EquipmentResponse;
import com.erp.pages.EquipmentListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Equipment")
@Feature("Equipment list — assignee filter UI")
public class EquipmentAssigneeFilterUITest extends BaseUITest {

    private EquipmentFixture equipmentFixture;
    private EmployeeFixture employeeFixture;
    private long storageId;
    private Long categoryId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        employeeFixture = new EmployeeFixture(testContext, apiExecutor);
        equipmentFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
    }

    @DataProvider(name = "adminAndOwnerRoles")
    public Object[][] adminAndOwnerRoles() {
        return new Object[][]{
                {UserRole.ADMIN},
                {UserRole.OWNER_1}
        };
    }

    @Test(dataProvider = "adminAndOwnerRoles")
    @TestCaseId("TC-UI-EQ-001")
    @Story("Assignee filter dropdown and filtering")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin та Owner відкривають /equipment на конкретній локації.
            Дропдаун «Закріплене за» має містити опцію «Всі/Усі співробітники»
            та позивні зі словника співробітників (GET /employees).
            Після вибору конкретного співробітника таблиця показує лише обладнання,
            закріплене за ним (assigneeId у GET /equipment/grouped).
            """)
    public void assigneeFilterDropdownListsEmployeesAndFiltersTable(UserRole role) {
        long suffix = System.currentTimeMillis() % 1_000_000;
        String employeeACallSign = "ui-eq-emp-A-" + suffix;
        String employeeBCallSign = "ui-eq-emp-B-" + suffix;

        Allure.parameter("role", role.name());
        Allure.parameter("storageId", storageId);

        EmployeeResponse employeeA = Allure.step("API: створити співробітника A", () ->
                employeeFixture.createEmployee(UserRole.ADMIN, storageId, employeeACallSign));
        EmployeeResponse employeeB = Allure.step("API: створити співробітника B", () ->
                employeeFixture.createEmployee(UserRole.ADMIN, storageId, employeeBCallSign));

        EquipmentResponse equipmentA = Allure.step("API: створити та закріпити обладнання A", () -> {
            EquipmentResponse created = equipmentFixture.createEquipmentOnStorage(
                    UserRole.ADMIN, storageId, categoryId);
            equipmentFixture.assignEquipment(UserRole.ADMIN, created.getId(), employeeA.getId());
            return created;
        });

        EquipmentResponse equipmentB = Allure.step("API: створити та закріпити обладнання B", () -> {
            EquipmentResponse created = equipmentFixture.createEquipmentOnStorage(
                    UserRole.ADMIN, storageId, categoryId);
            equipmentFixture.assignEquipment(UserRole.ADMIN, created.getId(), employeeB.getId());
            return created;
        });

        String equipmentAName = equipmentA.getName();
        String equipmentBName = equipmentB.getName();

        List<EmployeeResponse> dictionaryEmployees = Allure.step(
                "API: отримати словник співробітників для локації", () ->
                        employeeFixture.getEmployees(role, storageId));

        Allure.parameter("employeeA", employeeACallSign);
        Allure.parameter("employeeB", employeeBCallSign);
        Allure.parameter("equipmentA", equipmentAName);
        Allure.parameter("equipmentB", equipmentBName);

        injectRoleSession(role, storageId);
        page = browserContext.newPage();

        EquipmentListPage equipmentPage = Allure.step("Відкрити /equipment", () ->
                new EquipmentListPage(page).openForStorage(storageId).waitForLoaded());

        Allure.step("Перевірити опції дропдауну «Закріплене за»", () -> {
            List<String> dropdownOptions = equipmentPage.readAssigneeFilterOptions();
            equipmentPage.attachScreenshot("TC-UI-EQ-001 — assignee dropdown options");

            assertThat(equipmentPage.hasAllEmployeesDefaultOption(dropdownOptions))
                    .as("Дропдаун має містити опцію «Всі/Усі співробітники»")
                    .isTrue();
            assertThat(dropdownOptions)
                    .as("Дропдаун не повинен містити лише опцію «усі співробітники»")
                    .hasSizeGreaterThan(1);

            assertThat(dropdownOptions)
                    .as("Тестові співробітники мають бути у дропдауні")
                    .contains(employeeACallSign, employeeBCallSign);

            List<String> expectedFromDictionary = dictionaryEmployees.stream()
                    .map(EmployeeResponse::getCallSign)
                    .filter(callSign -> callSign != null && !callSign.isBlank())
                    .toList();
            assertThat(dropdownOptions)
                    .as("Усі активні співробітники зі словника мають бути доступні у фільтрі")
                    .containsAll(expectedFromDictionary);
        });

        Allure.step("Обрати співробітника A і перевірити фільтрацію таблиці", () -> {
            equipmentPage.filterByAssignee(employeeACallSign);
            equipmentPage.attachScreenshot("TC-UI-EQ-001 — filtered by employee A");

            List<EquipmentGroupResponse> apiGroups = equipmentFixture.getGroupedEquipment(
                    role, storageId, employeeA.getId());
            List<String> apiNames = equipmentFixture.extractEquipmentNames(apiGroups);

            assertThat(equipmentPage.isEquipmentNameVisible(equipmentAName))
                    .as("Обладнання A має бути видиме після фільтрації за співробітником A")
                    .isTrue();
            assertThat(equipmentPage.isEquipmentNameVisible(equipmentBName))
                    .as("Обладнання B не повинно бути видиме після фільтрації за співробітником A")
                    .isFalse();

            assertThat(apiNames)
                    .as("API grouped має містити обладнання A")
                    .contains(equipmentAName);
            assertThat(apiNames)
                    .as("API grouped не повинен містити обладнання B при фільтрі assigneeId=A")
                    .doesNotContain(equipmentBName);

            List<String> displayedNames = equipmentPage.getDisplayedEquipmentNames();
            assertThat(displayedNames)
                    .as("UI-таблиця після фільтра має збігатися з API grouped за assigneeId")
                    .containsExactlyInAnyOrderElementsOf(apiNames);
        });

        log.info("TC-UI-EQ-001 PASSED — role={}, employeeA={}, equipmentA={}",
                role, employeeACallSign, equipmentAName);
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
