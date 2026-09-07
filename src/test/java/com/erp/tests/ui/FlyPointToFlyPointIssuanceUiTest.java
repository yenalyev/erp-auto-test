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
import com.erp.pages.RelocationCreateOutputFlyPointPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RelocationStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI видачі між точками зльоту (REQ-CREW-002 AC-23).
 */
@Slf4j
@Epic("Relocation")
@Feature("Fly Point to Fly Point UI")
public class FlyPointToFlyPointIssuanceUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-fp2fp-";
    private static final double ISSUE_AMOUNT = 12.0;
    private static final String ISSUER_NAME = "UI Тест Видав";
    private static final String ISSUER_RANK = "Сержант";
    private static final UserRole STOCK_READER = UserRole.ADMIN;

    private CrewRegionFixture crewFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;

    private CrewRegionScenario scenario;
    private long memberStorageId;
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
        scenario = crewFixture.prepareTwoFlyPointsScenario("ui-fp2fp-");

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName().trim();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupFlyPointIssuanceArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        relocationFixture.ensureStock(memberStorageId, resourceId, 100.0);
        injectRoleSession(UserRole.ADMIN, memberStorageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-FLY-FP-001")
    @Story("Issue between fly points button visibility")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN на member storage з CREWS region — кнопка «Видати між точками зльоту» видима")
    public void testIssueBetweenFlyPointsButtonVisible() {
        RelocationPage relocationPage = new RelocationPage(page).open();
        assertThat(relocationPage.isIssueBetweenFlyPointsButtonVisible())
                .as("Кнопка «Видати між точками зльоту» має бути видимою")
                .isTrue();
        relocationPage.attachScreenshot("TC-UI-FLY-FP-001 — journal with fly-point button");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-FLY-FP-002")
    @Story("Happy path fly-point to fly-point issuance")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Форма між точками: FP_A → FP_B → submit → «В дорозі»; finish відправником → stock ±")
    public void testHappyPathFlyPointToFlyPointIssuance() {
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                memberStorageId,
                scenario.flyPoint().getId(),
                resourceId,
                ISSUE_AMOUNT);

        Long fpA = scenario.flyPoint().getId();
        Long fpB = scenario.flyPointB().getId();
        ProductionStockAssertions.StockSnapshot beforeFpA = RelocationStockAssertions.capture(
                apiExecutor, fpA, STOCK_READER, Set.of(resourceId), "FP_A before UI send");
        ProductionStockAssertions.StockSnapshot beforeFpB = RelocationStockAssertions.capture(
                apiExecutor, fpB, STOCK_READER, Set.of(resourceId), "FP_B before UI send");

        String marker = "TC-UI-FLY-FP-002-" + System.currentTimeMillis();
        RelocationPage relocationPage = new RelocationPage(page).open();
        RelocationCreateOutputFlyPointPage form = relocationPage.clickIssueBetweenFlyPoints();

        form.selectSenderFlyPointByName(scenario.flyPoint().getName())
                .selectRecipientFlyPointByName(scenario.flyPointB().getName())
                .selectResourceByName(resourceName)
                .fillQuantity(String.valueOf((int) ISSUE_AMOUNT))
                .fillIssuer(ISSUER_NAME, ISSUER_RANK)
                .fillDescription(marker);

        assertThat(form.isSubmitDisabled()).isFalse();
        form.attachScreenshot("TC-UI-FLY-FP-002 — form before submit");
        form.submitAndWaitForJournal();

        relocationPage.openInTransitTab();
        relocationPage.attachScreenshot("TC-UI-FLY-FP-002 — in transit after send");

        RelocationResponse inTransit = relocationFixture.findInTransitByDescription(
                UserRole.ADMIN, fpA, marker);
        assertThat(inTransit)
                .as("Після submit видача FP→FP має бути «В дорозі»")
                .isNotNull();
        relocationFixture.resolve(
                UserRole.ADMIN, inTransit.getId(), fpA, RelocationState.FINISHED);

        ProductionStockAssertions.StockSnapshot afterFpA = RelocationStockAssertions.capture(
                apiExecutor, fpA, STOCK_READER, Set.of(resourceId), "FP_A after finish");
        ProductionStockAssertions.StockSnapshot afterFpB = RelocationStockAssertions.capture(
                apiExecutor, fpB, STOCK_READER, Set.of(resourceId), "FP_B after finish");
        RelocationStockAssertions.assertDebitedFromSender(
                beforeFpA, afterFpA, fpA, resourceId, ISSUE_AMOUNT,
                "UI FP_A → FP_B списання");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeFpB, afterFpB, fpB, resourceId, ISSUE_AMOUNT,
                "UI FP_A → FP_B зарахування");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-FLY-FP-003")
    @Story("All locations guard")
    @Severity(SeverityLevel.NORMAL)
    @Description("«Всі локації» — кнопка «Видати між точками зльоту» прихована")
    public void testIssueBetweenFlyPointsHiddenForAllLocations() {
        browserContext.clearCookies();
        injectRoleSession(UserRole.ADMIN, memberStorageId);
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");

        RelocationPage relocationPage = new RelocationPage(page);
        page.navigate(ConfigProvider.getBaseUrl() + RelocationPage.PATH);
        relocationPage.waitForLoaded();

        assertThat(relocationPage.isIssueBetweenFlyPointsButtonVisible())
                .as("У режимі «Всі локації» кнопка не повинна відображатися")
                .isFalse();
        relocationPage.attachScreenshot("TC-UI-FLY-FP-003 — all locations, no fly-point button");
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
