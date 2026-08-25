package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.RelocationResponse;
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
    @Description("Плоска ієрархія: підрозділ → екіпаж → ресурс → submit → «В дорозі»; finish відправником → «Видано»")
    public void testHappyPathCrewIssuance() {
        String marker = "TC-UI-CREW-002-" + System.currentTimeMillis();
        RelocationPage relocationPage = new RelocationPage(page).open();
        RelocationCreateOutputCrewPage crewForm = relocationPage.clickIssueToCrew();

        crewForm.selectUnitByName(flatScenario.unit().getName())
                .selectCrewByName(flatScenario.crew().getName())
                .selectResourceByName(resourceName)
                .fillQuantity(String.valueOf((int) ISSUE_AMOUNT))
                .fillIssuer(ISSUER_NAME, ISSUER_RANK)
                .fillDescription(marker);

        assertThat(crewForm.isSubmitDisabled()).isFalse();
        crewForm.attachScreenshot("TC-UI-CREW-002 — crew form before submit");
        crewForm.submitAndWaitForJournal();

        String crewName = flatScenario.crew().getName();
        relocationPage.openInTransitTab();
        assertThat(relocationPage.isRowWithTextVisible(marker))
                .as("Після submit видача на CREW у вкладці «В дорозі»")
                .isTrue();
        relocationPage.attachScreenshot("TC-UI-CREW-002 — in transit after send");

        RelocationResponse inTransit = relocationFixture.findInTransitByDescription(
                UserRole.OWNER_1, memberStorageId, marker);
        assertThat(inTransit).isNotNull();
        relocationFixture.resolve(
                UserRole.OWNER_1, inTransit.getId(), memberStorageId, RelocationState.FINISHED);

        page.reload();
        relocationPage.waitForLoaded();
        relocationPage.openSentTab();

        assertThat(relocationPage.isRowWithTextVisible(resourceName)).isTrue();
        assertThat(relocationPage.getDisplayedJournalRows())
                .as("У «Видано» колонка «До» має містити назву екіпажу, не «_приховано_»")
                .anySatisfy(row -> {
                    assertThat(row.getRecipientName()).isEqualTo(crewName);
                    assertThat(row.getRecipientName()).isNotEqualTo("_приховано_");
                });
        relocationPage.attachScreenshot("TC-UI-CREW-002 — sent tab with crew name");
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
        relocationFixture.createSendAndFinishBySender(
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
