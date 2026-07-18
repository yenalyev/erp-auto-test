package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.models.response.EquipmentGroupResponse;
import com.erp.pages.EquipmentListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Equipment")
@Feature("Equipment list — group item sorting UI")
public class EquipmentGroupSortUITest extends BaseUITest {

    private EquipmentFixture equipmentFixture;
    private long storageId;
    private Long categoryId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        equipmentFixture.prepareCategoryContext();
        storageId = ConfigProvider.getOwner1StorageId();
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
    }

    @Test
    @TestCaseId("TC-UI-EQ-003")
    @Story("Sort equipment items within expanded group")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            GET /equipment/grouped повертає items у групі відсортованими за inventoryNumber ASC.
            UI /equipment: розгорнута група показує інв. номери в тому ж порядку, що й API.
            Arrange: API створює 4 одиниці з однаковою назвою та різними інв. номерами
            (у довільному порядку створення).
            UI: пошук за назвою → розгорнути групу → порівняти інв. номери з API.
            """)
    public void expandedGroupShowsItemsSortedByInventoryNumber() {
        long suffix = System.currentTimeMillis() % 1_000_000;
        String groupName = "ui-eq-group-sort-" + suffix;
        List<String> inventoryNumbers = List.of(
                "EQ-GRP-" + suffix + "-004",
                "EQ-GRP-" + suffix + "-001",
                "EQ-GRP-" + suffix + "-003",
                "EQ-GRP-" + suffix + "-002");
        List<String> expectedAsc = inventoryNumbers.stream().sorted().toList();

        Allure.parameter("storageId", storageId);
        Allure.parameter("groupName", groupName);
        Allure.parameter("inventoryNumbers", String.join(", ", inventoryNumbers));

        Allure.step("API: створити групу з 4 одиниць обладнання", () ->
                equipmentFixture.createEquipmentGroup(
                        UserRole.ADMIN, storageId, categoryId, groupName, inventoryNumbers));

        EquipmentGroupResponse apiGroup = Allure.step("API: отримати групу з GET /equipment/grouped", () -> {
            EquipmentGroupResponse group = equipmentFixture.findGroupByName(
                    UserRole.ADMIN, storageId, groupName);
            List<String> apiOrder = equipmentFixture.extractInventoryNumbers(group);
            assertThat(apiOrder)
                    .as("API має повертати items групи відсортованими за інв. номером ASC")
                    .containsExactlyElementsOf(expectedAsc);
            return group;
        });

        List<String> apiInventoryOrder = equipmentFixture.extractInventoryNumbers(apiGroup);

        injectRoleSession(UserRole.OWNER_1, storageId);
        page = browserContext.newPage();

        EquipmentListPage equipmentPage = Allure.step("Відкрити /equipment і знайти групу", () -> {
            EquipmentListPage listPage = new EquipmentListPage(page)
                    .openForStorage(storageId)
                    .waitForLoaded()
                    .filterBySearch(groupName);
            assertThat(listPage.isGroupVisible(groupName))
                    .as("Група «%s» має бути видима після пошуку", groupName)
                    .isTrue();
            listPage.attachScreenshot("TC-UI-EQ-003 — group found");
            return listPage;
        });

        Allure.step("Розгорнути групу — UI відповідає API (ASC за інв. номером)", () -> {
            equipmentPage.expandGroup(groupName);
            equipmentPage.attachScreenshot("TC-UI-EQ-003 — group expanded");

            List<String> uiInventoryNumbers = equipmentPage.readExpandedGroupInventoryNumbers(groupName);
            assertThat(uiInventoryNumbers)
                    .as("UI має показувати інв. номери в тому ж порядку, що й GET /equipment/grouped")
                    .containsExactlyElementsOf(apiInventoryOrder);
            assertThat(uiInventoryNumbers)
                    .as("Інв. номери у розгорнутій групі мають бути відсортовані ASC")
                    .isSortedAccordingTo(Comparator.naturalOrder());
        });

        log.info("TC-UI-EQ-003 PASSED — group={}, inventoryNumbers={}", groupName, inventoryNumbers);
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
