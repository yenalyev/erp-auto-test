package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.TechnologicalMapFixture.IsolatedTechMapContext;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.pages.GlobalPlansPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans UI")
public class GlobalPlanHistoricalTechMapUiTest extends BaseUITest {

    private static final String TEST_ID = "TC-GP-UI-062";

    private GlobalPlanFixture globalPlanFixture;
    private TechnologicalMapFixture techMapFixture;
    private Long l1StorageId;
    private String l1StorageName;
    private final List<Long> globalPlanIdsToCleanup = new ArrayList<>();
    private final List<Long> generatedPlanIdsToCleanup = new ArrayList<>();

    @Override
    protected boolean shouldInitializeDatabase() {
        return true;
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        globalPlanFixture.getResourceFixture().fetchSharedUnit(1);
        globalPlanFixture.getResourceFixture().fetchSharedResourceCategory();
        l1StorageId = ConfigProvider.getOwner1StorageId();
        l1StorageName = globalPlanFixture.resolveStorageName(l1StorageId);
        techMapFixture.setMode(l1StorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        injectAllLocationsView();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (globalPlanFixture != null) {
            globalPlanFixture.cleanupGeneratedPlans(generatedPlanIdsToCleanup);
            for (Long planId : globalPlanIdsToCleanup) {
                try {
                    globalPlanFixture.deleteGlobalPlan(planId);
                } catch (AssertionError e) {
                    log.warn("Global plan cleanup failed for id {}: {}", planId, e.getMessage());
                }
            }
        }
        if (techMapFixture != null && l1StorageId != null) {
            techMapFixture.setMode(l1StorageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @Test(priority = 10)
    @TestCaseId(TEST_ID)
    @Story("Historical snapshot then new tech map in wizard")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після архіву карти з історичного GP і створення нової карти UI wizard
            не показує «Не вдалося розрахувати декомпозицію».
            
            **Сид:** generate + JDBC backdate (як TC-GP-062). API: DELETE M1, POST M2.
            
            **UI start():** block 0 з output і порожніми assignments + flattenSnapshot.
            
            1. Reopen історичного плану — Tab 2 replay snapshot M1 (inactive).
            2. Новий GP з виробом A — Tab 2 auto-assign M2, generate.
            """)
    public void historicalSnapshotThenNewMapWizardSucceeds() {
        if (ensureDatabaseHelper() == null) {
            throw new SkipException(
                    "TC-GP-UI-062 потребує JDBC для backdate періоду GP "
                            + "(VPN до dev DB або -Duse.database=true)");
        }

        IsolatedTechMapContext isolated = Allure.step("API: ізольовані A + M1", () ->
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, l1StorageId, "GP-UI-062-M1"));
        TechnologicalMapResponse mapM1 = isolated.getTechMap();
        String productAName = isolated.getProduct().getName();
        Long productAId = isolated.getProduct().getId();

        GlobalPlanFixture.HistoricalPlanSeed seed = Allure.step("API-сид: історичний GP зі snapshot M1", () -> {
            GlobalPlanFixture.HistoricalPlanSeed created = globalPlanFixture.seedHistoricalGeneratedPlan(
                    getDbHelper(), productAId, l1StorageId, mapM1.getId(), 10.0);
            globalPlanIdsToCleanup.add(created.pastPlan().getId());
            generatedPlanIdsToCleanup.addAll(created.locationPlanIds());
            return created;
        });

        TechnologicalMapResponse mapM2 = Allure.step("API: архів M1 і POST M2", () -> {
            assertThat(techMapFixture.deactivateTechMap(UserRole.ADMIN, mapM1.getId(), l1StorageId)
                    .statusCode()).isEqualTo(200);
            return techMapFixture.createAlternateActiveTechMap(UserRole.ADMIN, mapM1);
        });

        Allure.step("UI: reopen історичного плану — Tab 2 без failed", () -> {
            GlobalPlanWizardPage historical = new GlobalPlanWizardPage(page)
                    .openById(seed.pastPlan().getId(), true);
            historical.attachScreenshot(TEST_ID + " — historical tab1");
            historical.openProductionTab();
            historical.waitForDecompositionIdle();
            historical.attachScreenshot(TEST_ID + " — historical tab2");

            assertThat(historical.isDecompositionFailedVisible())
                    .as("Reopen історичного snapshot не має показати failed-екран")
                    .isFalse();
            assertThat(historical.isPastPeriodReadOnlyBannerVisible())
                    .as("Має бути банер минулого періоду")
                    .isTrue();
            assertThat(historical.isResourceVisibleInDecomposition(productAName))
                    .as("Виріб A має бути в декомпозиції")
                    .isTrue();
        });

        YearMonth newPeriod = globalPlanFixture.nextUniquePeriod();
        Allure.step("UI: новий GP з A — Tab 2 призначає M2, generate", () -> {
            GlobalPlanWizardPage wizard = new GlobalPlansPage(page).open().clickCreatePlan();
            wizard.attachScreenshot(TEST_ID + " — new wizard empty");
            wizard.fillDescription("UI-062-" + newPeriod.getMonthValue() + "/" + newPeriod.getYear()
                            + "-" + System.currentTimeMillis())
                    .selectPeriod(newPeriod.getMonthValue(), newPeriod.getYear())
                    .fillOutputProduct(productAName, "10")
                    .submitCreatePlan();
            Long newPlanId = wizard.extractPlanIdFromUrl();
            globalPlanIdsToCleanup.add(newPlanId);
            wizard.attachScreenshot(TEST_ID + " — new plan tab1 saved");

            wizard.waitForDecompositionIdle();
            wizard.attachScreenshot(TEST_ID + " — new plan tab2");
            assertThat(wizard.isDecompositionFailedVisible())
                    .as("Новий GP не має failed-екрану декомпозиції")
                    .isFalse();
            assertThat(wizard.hasMissingTechMapForResource(productAName))
                    .as("Для A має бути доступна техкарта M2")
                    .isFalse();
            wizard.verifyAssignedTechMap(productAName, l1StorageName, mapM2.getName());
            wizard.attachScreenshot(TEST_ID + " — new plan M2 assigned");

            wizard.waitForDistributeEnabled();
            wizard.clickDistributeToLocations();
            wizard.proceedFromRequirementsTab();
            wizard.generateLocationPlans();
            wizard.attachScreenshot(TEST_ID + " — new plan generated");

            GlobalPlansPage list = wizard.clickDoneAndReturnToList();
            assertThat(list.isListHeadingVisible()).isTrue();
            list.attachScreenshot(TEST_ID + " — back on list");
        });
    }
}
