package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.EquipmentSimpleResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.EquipmentCreatePage;
import com.erp.pages.EquipmentListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Equipment")
@Feature("Equipment batch create UI")
public class EquipmentCreateUITest extends BaseUITest {

    private EquipmentFixture equipmentFixture;
    private long storageId;
    private Long categoryId;
    private Long supplierId;
    private String categoryName;
    private String supplierName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        new RelocationFixture(testContext, apiExecutor).prepareContext();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        equipmentFixture.prepareCategoryContext();

        storageId = ConfigProvider.getOwner1StorageId();
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        categoryName = equipmentFixture.resolveCategoryName(categoryId);
        supplierName = equipmentFixture.resolveSupplierName(supplierId);
    }

    @Test
    @TestCaseId("TC-UI-EQ-002")
    @Issue("CPMA-563")
    @Story("Add several equipment units in one relocation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            CPMA-563: на /equipment/create користувач додає дві позиції обладнання,
            обирає постачальника («Звідки») і зберігає одним submit («Зберегти всі 2»).
            Очікування:
            — redirect на /equipment, обидві назви видимі в таблиці;
            — одне receive-переміщення EQUIPMENT містить обидві одиниці (equipmentItems).
            """)
    public void createSeveralEquipmentInOneRelocation() {
        long suffix = System.currentTimeMillis() % 1_000_000;
        String nameA = "ui-eq-batch-A-" + suffix;
        String nameB = "ui-eq-batch-B-" + suffix;
        List<String> names = List.of(nameA, nameB);

        Allure.parameter("storageId", storageId);
        Allure.parameter("supplier", supplierName);
        Allure.parameter("category", categoryName);
        Allure.parameter("equipmentA", nameA);
        Allure.parameter("equipmentB", nameB);

        injectRoleSession(UserRole.OWNER_1, storageId);
        page = browserContext.newPage();

        EquipmentListPage listPage = Allure.step("UI: створити 2 позиції обладнання з постачальником", () -> {
            EquipmentCreatePage createPage = new EquipmentCreatePage(page)
                    .openForStorage(storageId)
                    .ensureSupplier(supplierName)
                    .fillItem(0, nameA, categoryName)
                    .addAnotherItem()
                    .fillItem(1, nameB, categoryName);

            assertThat(createPage.positionCount())
                    .as("Лічильник позицій має показувати 2")
                    .isEqualTo(2);

            createPage.attachScreenshot("TC-UI-EQ-002 — form with 2 items");
            return createPage.submitAll(2);
        });

        Allure.step("UI: обидві назви видимі в журналі обладнання", () -> {
            listPage.attachScreenshot("TC-UI-EQ-002 — list after batch create");
            assertThat(listPage.isEquipmentNameVisible(nameA))
                    .as("Обладнання A має бути в таблиці")
                    .isTrue();
            assertThat(listPage.isEquipmentNameVisible(nameB))
                    .as("Обладнання B має бути в таблиці")
                    .isTrue();
        });

        Allure.step("API: одне переміщення містить обидві одиниці", () -> {
            RelocationResponse relocation = equipmentFixture.findRelocationContainingEquipmentNames(
                    UserRole.OWNER_1, storageId, names);
            List<String> relocationNames = relocation.getEquipmentItems().stream()
                    .map(EquipmentSimpleResponse::getName)
                    .toList();

            assertThat(relocation.getEquipmentItems())
                    .as("Переміщення має містити рівно 2 одиниці обладнання")
                    .hasSize(2);
            assertThat(relocationNames)
                    .as("equipmentItems мають містити обидві створені назви")
                    .containsExactlyInAnyOrderElementsOf(names);

            Allure.parameter("relocationId", relocation.getId());
            Allure.parameter("relocationState", relocation.getState());
        });

        log.info("TC-UI-EQ-002 PASSED — names={}, supplier={}", names, supplierName);
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
