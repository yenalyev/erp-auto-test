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
import com.erp.pages.RelocationCreateInputCrewPage;
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
 * UI happy path повернення від екіпажу (CPMA-647): «Отримати від екіпажа».
 */
@Slf4j
@Epic("Relocation")
@Feature("Crew Return UI")
public class CrewReturnUITest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-crew-ret-";
    private static final double ISSUE_AMOUNT = 12.0;
    private static final double RETURN_AMOUNT = 5.0;
    private static final UserRole STOCK_READER = UserRole.ADMIN;

    private CrewRegionFixture crewFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;

    private CrewRegionScenario unattachedScenario;
    private CrewRegionScenario attachedScenario;
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
        unattachedScenario = crewFixture.prepareSingleCrewScenario("ui-ret-u-");
        attachedScenario = crewFixture.prepareAttachedCrewScenario("ui-ret-a-");

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName().trim();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupCrewReturnArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        relocationFixture.ensureStock(memberStorageId, resourceId, 100.0);
        injectRoleSession(UserRole.OWNER_1, memberStorageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-CREW-RET-001")
    @Story("Receive from crew button")
    @Severity(SeverityLevel.CRITICAL)
    @Description("OWNER_1 на member storage з CREWS — кнопка «Отримати від екіпажа» видима")
    public void receiveFromCrewButtonVisible() {
        RelocationPage relocationPage = new RelocationPage(page).open();
        assertThat(relocationPage.isReceiveFromCrewButtonVisible())
                .as("Кнопка «Отримати від екіпажа» має бути видимою")
                .isTrue();
        relocationPage.attachScreenshot("TC-UI-CREW-RET-001 — journal CTA");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-CREW-RET-002")
    @Story("Happy path unattached crew return")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: видача на unattached CREW FINISHED.
            UI: Отримати від екіпажа → UNIT → CREW → ресурс → кількість → Підтвердити.
            Очікування: вкладка «Отримано» + marker; CREW −N; warehouse +N.
            """)
    public void happyPathUnattachedCrewReturn() {
        String marker = "TC-UI-CREW-RET-002-" + System.currentTimeMillis();
        Long crewId = unattachedScenario.crew().getId();

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, memberStorageId, crewId, resourceId, ISSUE_AMOUNT);
        injectRoleSession(UserRole.OWNER_1, memberStorageId);

        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before UI return");
        ProductionStockAssertions.StockSnapshot beforeWarehouse = RelocationStockAssertions.capture(
                apiExecutor, memberStorageId, UserRole.OWNER_1, Set.of(resourceId), "warehouse before UI return");

        RelocationPage relocationPage = new RelocationPage(page).open();
        RelocationCreateInputCrewPage form = relocationPage.clickReceiveFromCrew();

        form.selectUnitByName(unattachedScenario.unit().getName())
                .selectCrewByName(unattachedScenario.crew().getName())
                .selectResourceByName(resourceName)
                .fillQuantity(String.valueOf((int) RETURN_AMOUNT))
                .fillDescription(marker);

        assertThat(form.isSubmitDisabled()).isFalse();
        form.attachScreenshot("TC-UI-CREW-RET-002 — form before submit");
        form.submitAndWaitForJournal();

        relocationPage.openReceivedTab();
        assertThat(relocationPage.isRowWithTextVisible(marker))
                .as("Після повернення рядок з marker у вкладці «Отримано»")
                .isTrue();
        relocationPage.attachScreenshot("TC-UI-CREW-RET-002 — received tab");

        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after UI return");
        ProductionStockAssertions.StockSnapshot afterWarehouse = RelocationStockAssertions.capture(
                apiExecutor, memberStorageId, UserRole.OWNER_1, Set.of(resourceId), "warehouse after UI return");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeCrew, afterCrew, crewId, resourceId, RETURN_AMOUNT,
                "UI unattached — списання з CREW");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeWarehouse, afterWarehouse, memberStorageId, resourceId, RETURN_AMOUNT,
                "UI unattached — зарахування на склад");
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-CREW-RET-003")
    @Story("Happy path attached crew return via fly point")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: видача на attached CREW → stock на FLY_POINT.
            UI: той самий флоу «Отримати від екіпажа» (sender=CREW).
            Очікування: «Отримано»; FLY_POINT −N; warehouse +N; CREW ≈ 0.
            """)
    public void happyPathAttachedCrewReturnDebitsFlyPoint() {
        String marker = "TC-UI-CREW-RET-003-" + System.currentTimeMillis();
        Long crewId = attachedScenario.crew().getId();
        Long flyPointId = attachedScenario.flyPoint().getId();

        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, memberStorageId, crewId, resourceId, ISSUE_AMOUNT);
        injectRoleSession(UserRole.OWNER_1, memberStorageId);

        ProductionStockAssertions.StockSnapshot beforeFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp before UI return");
        ProductionStockAssertions.StockSnapshot beforeCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew before UI return");
        ProductionStockAssertions.StockSnapshot beforeWarehouse = RelocationStockAssertions.capture(
                apiExecutor, memberStorageId, UserRole.OWNER_1, Set.of(resourceId), "warehouse before UI return");

        assertThat(beforeFp.amountOf(resourceId))
                .as("після auto-forward stock на FLY_POINT")
                .isGreaterThanOrEqualTo(ISSUE_AMOUNT);
        assertThat(beforeCrew.amountOf(resourceId))
                .as("attached CREW ≈ 0 перед поверненням")
                .isLessThan(0.01);

        RelocationPage relocationPage = new RelocationPage(page).open();
        RelocationCreateInputCrewPage form = relocationPage.clickReceiveFromCrew();

        form.selectUnitByName(attachedScenario.unit().getName())
                .selectCrewByName(attachedScenario.crew().getName())
                .selectResourceByName(resourceName)
                .fillQuantity(String.valueOf((int) RETURN_AMOUNT))
                .fillDescription(marker);

        assertThat(form.isSubmitDisabled()).isFalse();
        form.attachScreenshot("TC-UI-CREW-RET-003 — form before submit");
        form.submitAndWaitForJournal();

        relocationPage.openReceivedTab();
        assertThat(relocationPage.isRowWithTextVisible(marker))
                .as("Після повернення рядок з marker у вкладці «Отримано»")
                .isTrue();
        relocationPage.attachScreenshot("TC-UI-CREW-RET-003 — received tab");

        ProductionStockAssertions.StockSnapshot afterFp = RelocationStockAssertions.capture(
                apiExecutor, flyPointId, STOCK_READER, Set.of(resourceId), "fp after UI return");
        ProductionStockAssertions.StockSnapshot afterCrew = RelocationStockAssertions.capture(
                apiExecutor, crewId, STOCK_READER, Set.of(resourceId), "crew after UI return");
        ProductionStockAssertions.StockSnapshot afterWarehouse = RelocationStockAssertions.capture(
                apiExecutor, memberStorageId, UserRole.OWNER_1, Set.of(resourceId), "warehouse after UI return");

        RelocationStockAssertions.assertDebitedFromSender(
                beforeFp, afterFp, flyPointId, resourceId, RETURN_AMOUNT,
                "UI attached — списання з FLY_POINT");
        RelocationStockAssertions.assertUnchanged(
                beforeCrew, afterCrew, crewId, resourceId,
                "UI attached — CREW знову ≈ 0");
        RelocationStockAssertions.assertCreditedToRecipient(
                beforeWarehouse, afterWarehouse, memberStorageId, resourceId, RETURN_AMOUNT,
                "UI attached — зарахування на склад");
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
