package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.*;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Crew Issuance UI")
public class CrewIssuanceUITest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-crew-";
    private static final double ISSUE_AMOUNT = 10.0;
    private static final String ISSUER_NAME = "UI Тест Видав";
    private static final String ISSUER_RANK = "Сержант";

    private CrewRegionFixture crewFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;

    private CrewRegionScenario flatScenario;
    private CrewRegionScenario hierarchyScenario;
    private long memberStorageId;
    private long unitStorageId;
    private Long resourceId;
    private String resourceName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        memberStorageId = ConfigProvider.getOwner1StorageId();
        unitStorageId = ConfigProvider.getUnitStorageId();
        flatScenario = crewFixture.prepareSingleCrewScenario("ui-crew-flat-");
        hierarchyScenario = crewFixture.prepareHierarchyScenario("ui-crew-hier-");

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName().trim();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCrewIssuanceArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        relocationFixture.ensureStock(memberStorageId, resourceId, 100.0);
        injectRoleSession(UserRole.OWNER_1, memberStorageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-CREW-001")
    @Story("Issue to crew button visibility")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 на member storage з CREWS region — кнопка «Видати на екіпаж» видима")
    public void testIssueToCrewButtonVisible() {
        RelocationPage relocationPage = new RelocationPage(page).open();
        assertThat(relocationPage.isIssueToCrewButtonVisible())
                .as("Кнопка «Видати на екіпаж» має бути видимою")
                .isTrue();
        relocationPage.attachScreenshot("TC-UI-CREW-001 — journal with crew button");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-CREW-002")
    @Story("Happy path crew issuance")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Плоска ієрархія: підрозділ → екіпаж → ресурс → підтвердити")
    public void testHappyPathCrewIssuance() {
        RelocationPage relocationPage = new RelocationPage(page).open();
        RelocationCreateOutputCrewPage crewForm = relocationPage.clickIssueToCrew();

        crewForm.selectUnitByName(flatScenario.unit().getName())
                .selectCrewByName(flatScenario.crew().getName())
                .selectResourceByName(resourceName)
                .fillQuantity(String.valueOf((int) ISSUE_AMOUNT))
                .fillIssuer(ISSUER_NAME, ISSUER_RANK)
                .fillDescription("TC-UI-CREW-002");

        assertThat(crewForm.isSubmitDisabled()).isFalse();
        crewForm.attachScreenshot("TC-UI-CREW-002 — crew form before submit");
        crewForm.submitAndWaitForJournal();
        relocationPage.openSentTab();

        assertThat(relocationPage.isRowWithTextVisible(resourceName)).isTrue();
        relocationPage.attachScreenshot("TC-UI-CREW-002 — sent tab with resource");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-CREW-003")
    @Story("All locations guard")
    @Severity(SeverityLevel.NORMAL)
    @Description("«Всі локації» — кнопка «Видати на екіпаж» прихована")
    public void testIssueToCrewHiddenForAllLocations() {
        browserContext.clearCookies();
        injectRoleSession(UserRole.OWNER_1, memberStorageId);
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");

        RelocationPage relocationPage = new RelocationPage(page);
        page.navigate(ConfigProvider.getBaseUrl() + RelocationPage.PATH);
        relocationPage.waitForLoaded();

        assertThat(relocationPage.isIssueToCrewButtonVisible())
                .as("У режимі «Всі локації» кнопка не повинна відображатися")
                .isFalse();
        relocationPage.attachScreenshot("TC-UI-CREW-003 — all locations, no crew button");
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-CREW-005")
    @Story("Parent unit → crew cascade")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Обрати батьківський UNIT-A → екіпаж не порожній")
    public void testParentUnitPopulatesCrewCombobox() {
        RelocationPage relocationPage = new RelocationPage(page).open();
        RelocationCreateOutputCrewPage crewForm = relocationPage.clickIssueToCrew();

        crewForm.selectUnitByName(hierarchyScenario.unit().getName());
        assertThat(crewForm.isCrewComboboxEmpty())
                .as("Після вибору батьківського підрозділу екіпаж не повинен бути порожнім")
                .isFalse();
        crewForm.attachScreenshot("TC-UI-CREW-005 — crew combobox populated");
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-CREW-006")
    @Story("No CREWS membership")
    @Severity(SeverityLevel.NORMAL)
    @Description("OWNER_2 storage поза CREWS — кнопки «Видати на екіпаж» немає")
    public void testIssueToCrewHiddenWithoutCrewsMembership() {
        long owner2StorageId = ConfigProvider.getOwner2StorageId();
        injectRoleSession(UserRole.OWNER_2, owner2StorageId);

        RelocationPage relocationPage = new RelocationPage(page).open();
        assertThat(relocationPage.isIssueToCrewButtonVisible()).isFalse();
        relocationPage.attachScreenshot("TC-UI-CREW-006 — owner2 without crews membership");
    }

    @Test(priority = 60)
    @TestCaseId("TC-UI-CREW-007")
    @Story("Stock validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Кількість > stock → submit disabled; допустима кількість → submit enabled")
    public void testStockValidationBlocksExcessiveAmount() {
        RelocationPage relocationPage = new RelocationPage(page).open();
        RelocationCreateOutputCrewPage crewForm = relocationPage.clickIssueToCrew();

        crewForm.selectUnitByName(flatScenario.unit().getName())
                .selectCrewByName(flatScenario.crew().getName())
                .selectResourceByName(resourceName)
                .fillIssuer(ISSUER_NAME, ISSUER_RANK);

        crewForm.fillQuantity("999999");
        assertThat(crewForm.isSubmitDisabled())
                .as("Перевищення stock має блокувати submit")
                .isTrue();
        crewForm.attachScreenshot("TC-UI-CREW-007 — excessive quantity blocked");

        crewForm.fillQuantity(String.valueOf((int) ISSUE_AMOUNT));
        assertThat(crewForm.isSubmitDisabled()).isFalse();
        crewForm.attachScreenshot("TC-UI-CREW-007 — valid quantity enabled");
    }

    @Test(priority = 70)
    @TestCaseId("TC-UI-CREW-009")
    @Story("Operation history «Видано»")
    @Severity(SeverityLevel.NORMAL)
    @Description("API setup createSend → UI /history картка «Видано» delta")
    public void testIssuedSummaryCardAfterCrewSend() {
        relocationFixture.createSend(
                UserRole.OWNER_1,
                memberStorageId,
                flatScenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        OperationHistoryPage historyPage = new OperationHistoryPage(page).open();
        assertThat(historyPage.isSummaryCardVisible("Видано")).isTrue();

        double amount = historyPage.getSummaryCardAmountForResource("Видано", resourceName);
        assertThat(amount).isGreaterThanOrEqualTo(ISSUE_AMOUNT - 0.01);
        historyPage.attachScreenshot("TC-UI-CREW-009 — issued summary card");
    }

    @Test(priority = 80)
    @TestCaseId("TC-UI-CREW-010")
    @Story("Inventory crews mode without crew")
    @Severity(SeverityLevel.NORMAL)
    @Description("mode=crews без екіпажу — «Провести інвентаризацію» disabled")
    public void testInventoryButtonsDisabledWithoutCrewSelection() {
        InventoryCrewsModePage inventoryPage = new InventoryCrewsModePage(page).openCrewsMode(memberStorageId);

        assertThat(inventoryPage.isCrewsModeActive())
                .as("Режим «Запаси екіпажів» має бути активним")
                .isTrue();
        if (inventoryPage.isConductInventoryButtonVisible()) {
            assertThat(inventoryPage.isConductInventoryButtonDisabled())
                    .as("Без обраного екіпажу conduct має бути disabled")
                    .isTrue();
        }
        inventoryPage.attachScreenshot("TC-UI-CREW-010 — crews mode without crew");
    }

    @Test(priority = 90)
    @TestCaseId("TC-UI-CREW-004")
    @Story("Crew stock view")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Після API видачі (OWNER_1) — таблиця crews mode містить resource.
            UI-сесія: argument (Crew-Manager-ROLE, UNIT id=unit.storage.id) — inventory-list::{crew}::read
            після appendGrantedCrews. OWNER_1 без Crew-Manager отримує 403 на GET /storages/{crewId}/inventory.
            """)
    public void testCrewStockVisibleInInventoryCrewsMode() {
        relocationFixture.createSend(
                UserRole.OWNER_1,
                memberStorageId,
                flatScenario.crew().getId(),
                resourceId,
                ISSUE_AMOUNT);

        injectRoleSession(UserRole.CREW_MANAGER, unitStorageId);
        InventoryCrewsModePage inventoryPage = new InventoryCrewsModePage(page).openCrewsMode(unitStorageId);
        inventoryPage.selectUnitByName(flatScenario.unit().getName())
                .selectCrewByName(flatScenario.crew().getName());

        assertThat(page.url()).contains("crew=" + flatScenario.crew().getId());
        if (!inventoryPage.tableContainsResource(resourceName)) {
            inventoryPage.attachScreenshot("TC-UI-CREW-004 — crew stock table empty");
        }
        assertThat(inventoryPage.tableContainsResource(resourceName)).isTrue();
    }

    @Test(priority = 100)
    @TestCaseId("TC-UI-CREW-011")
    @Story("Crew inventory session")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Після вибору екіпажу — кнопка «Відкрити/Закрити інвентаризацію» видима.
            UI-сесія: argument (Crew-Manager-ROLE) — inventory-status::{crew}::update після appendGrantedCrews.
            OWNER_1 / ADMIN без crew-шаблону — кнопка прихована (див. TC-CREW-INV-009).
            """)
    public void testCrewInventorySessionButtonsAfterCrewSelected() {
        injectRoleSession(UserRole.CREW_MANAGER, unitStorageId);
        InventoryCrewsModePage inventoryPage = new InventoryCrewsModePage(page).openCrewsMode(unitStorageId);
        inventoryPage.selectUnitByName(flatScenario.unit().getName())
                .selectCrewByName(flatScenario.crew().getName());

        assertThat(inventoryPage.isInventorySessionToggleVisible())
                .as("Після вибору екіпажу кнопка керування інвентаризацією має бути видимою")
                .isTrue();
        inventoryPage.attachScreenshot("TC-UI-CREW-011 — inventory toggle after crew selected");
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
