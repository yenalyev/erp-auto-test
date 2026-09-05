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
import com.erp.fixtures.UserFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.CrewAnalyticsPage;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: Crew-Read-ROLE — sidebar «Аналітика Екіпажів» та залишки екіпажів батальйону.
 */
@Slf4j
@Epic("Inventory")
@Feature("Crew battalion stocks")
@Story("Crew-Read analytics UI")
public class CrewBattalionStocksUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-crew-rbac-";
    private static final String SCENARIO_PREFIX = "ui-crew-rbac-";
    private static final double ISSUE_AMOUNT = 7.0;

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private CrewRegionFixture crewFixture;
    private ResourceFixture resourceFixture;
    private RelocationFixture relocationFixture;
    private UserFixture userFixture;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);

        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();
        userFixture.ensureCrewBattalionUser(getPlaywrightSessionProvider(), UserRole.CREW_READ);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupScenario() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupClassArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test
    @TestCaseId("TC-UI-CREW-RBAC-001")
    @Description(StorageRegionsAllureDescriptions.TC_UI_CREW_RBAC_001)
    @Severity(SeverityLevel.CRITICAL)
    public void crewReadUserSeesBattalionStocksInAnalytics() {
        long memberStorageId = ConfigProvider.getUnitStorageId();
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenarioForMembers(
                SCENARIO_PREFIX, memberStorageId);

        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 50.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resource.getId(),
                ISSUE_AMOUNT);

        authService.invalidateSession(
                UserRole.CREW_READ.getUsername(), UserRole.CREW_READ.getPassword());
        apiExecutor.clearSessionCache();
        injectSessionCookies(cachedSessionCookies(UserRole.CREW_READ), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + memberStorageId + "');");

        String unitName = scenario.unit().getName();
        String crewName = scenario.crew().getName();
        String resourceSearch = resource.getName().trim();

        CrewAnalyticsPage analytics = new CrewAnalyticsPage(page).open()
                .selectCrewLocationByName(unitName)
                .openStocksTab();
        analytics.filterByResourceName(resourceSearch);
        analytics.attachScreenshot("TC-UI-CREW-RBAC-001 — stocks tab loaded");

        assertThat(analytics.isCrewNameVisibleInStocksTable(crewName))
                .as("Таблиця залишків містить екіпаж батальйону")
                .isTrue();

        assertThat(page.url())
                .as("Crew-Read має доступ до /crew-analytics")
                .contains("/crew-analytics");
    }
}
