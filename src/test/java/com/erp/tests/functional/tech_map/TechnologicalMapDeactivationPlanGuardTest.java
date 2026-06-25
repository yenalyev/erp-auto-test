package com.erp.tests.functional.tech_map;

import com.erp.annotations.TestCaseId;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.TechnologicalMapFixture.IsolatedTechMapContext;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Technological Maps")
@Feature("Deactivation plan guard")
public class TechnologicalMapDeactivationPlanGuardTest extends BaseFunctionalTest {

    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private final List<Long> planIdsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів обмеження видалення техкарт")
    public void setupPlanGuardTests() {
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
        storageId = techMapFixture.getOwner1StorageId();
        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        SchemaRegistry.logSchemaCoverage();
    }

    @AfterMethod(alwaysRun = true)
    @Step("Cleanup: видалити плани після тесту")
    public void cleanupPlansAfterTest() {
        if (techMapFixture == null) {
            return;
        }
        List<Long> snapshot = new ArrayList<>(planIdsToCleanup);
        for (Long planId : snapshot) {
            try {
                techMapFixture.deleteLocationPlan(planId);
            } catch (AssertionError e) {
                log.warn("Could not delete plan {}: {}", planId, e.getMessage());
            }
        }
        planIdsToCleanup.removeAll(snapshot);
    }

    @AfterClass(alwaysRun = true)
    @Step("Cleanup: видалити створені плани та відновити READ_ONLY")
    public void teardownPlanGuardTests() {
        if (techMapFixture != null) {
            for (Long planId : planIdsToCleanup) {
                try {
                    techMapFixture.deleteLocationPlan(planId);
                } catch (AssertionError e) {
                    log.warn("Could not delete plan {}: {}", planId, e.getMessage());
                }
            }
            if (storageId != null) {
                techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
            }
        }
    }

    @DataProvider(name = "ownerAndAdminRoles")
    public Object[][] ownerAndAdminRoles() {
        return new Object[][]{
                {UserRole.OWNER_1},
                {UserRole.ADMIN}
        };
    }

    @Test(priority = 10, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-MFG-026")
    @Story("Deactivate blocked by active plan")
    @Description("Єдину активну техкарту продукту не можна деактивувати, якщо продукт у плані на поточний або майбутній місяць")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotDeactivateSoleTechMapWhenProductInActivePlan(UserRole role) {
        IsolatedTechMapContext context = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        String productName = context.getProduct().getName();
        TechnologicalMapResponse techMap = context.getTechMap();

        PlanResponse plan = Allure.step("Arrange: актуальний план з продуктом «" + productName + "»", () -> {
            PlanResponse created = techMapFixture.createActiveLocationPlan(
                    storageId,
                    techMapFixture.getOutputResourceId(techMap),
                    YearMonth.now().plusMonths(1),
                    100.0);
            planIdsToCleanup.add(created.getId());
            return created;
        });

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName());

        Response response = Allure.step(role + ": DELETE deactivate sole tech map (expected failure)", () ->
                techMapFixture.deactivateTechMap(role, techMap.getId(), storageId));

        Allure.step("Assert: відмова через план «" + plan.getDescription() + "»", () -> {
            techMapFixture.assertUsedInPlanRejection(response);
            long activeCountAfter = techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName());
            assertThat(activeCountAfter).isEqualTo(activeCountBefore);
        });
    }

    @Test(priority = 11, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-MFG-027")
    @Story("Deactivate blocked by future plan")
    @Description("Єдину активну техкарту не можна деактивувати, якщо продукт лише у майбутньому плані")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotDeactivateSoleTechMapWhenProductInFuturePlan(UserRole role) {
        IsolatedTechMapContext context = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        TechnologicalMapResponse techMap = context.getTechMap();

        Allure.step("Arrange: майбутній актуальний план", () -> {
            PlanResponse created = techMapFixture.createActiveLocationPlan(
                    storageId,
                    techMapFixture.getOutputResourceId(techMap),
                    YearMonth.now().plusMonths(3),
                    50.0);
            planIdsToCleanup.add(created.getId());
        });

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName());

        Response response = Allure.step(role + ": DELETE deactivate sole tech map (expected failure)", () ->
                techMapFixture.deactivateTechMap(role, techMap.getId(), storageId));

        Allure.step("Assert: деактивація заблокована планом", () -> {
            techMapFixture.assertUsedInPlanRejection(response);
            assertThat(techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName()))
                    .isEqualTo(activeCountBefore);
        });
    }

    @Test(priority = 20, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-MFG-028")
    @Story("Deactivate allowed with alternate active tech map")
    @Description("Техкарту можна деактивувати, якщо для продукту є інша активна техкарта, навіть коли продукт у плані")
    @Severity(SeverityLevel.CRITICAL)
    public void testCanDeactivateWhenOtherActiveTechMapExists(UserRole role) {
        IsolatedTechMapContext context = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        TechnologicalMapResponse primary = context.getTechMap();
        TechnologicalMapResponse alternate = Allure.step("Arrange: друга активна техкарта на той самий продукт", () ->
                techMapFixture.createAlternateActiveTechMap(UserRole.ADMIN, primary));

        Allure.step("Arrange: актуальний план з продуктом", () -> {
            PlanResponse created = techMapFixture.createActiveLocationPlan(
                    storageId,
                    techMapFixture.getOutputResourceId(primary),
                    YearMonth.now().plusMonths(6),
                    75.0);
            planIdsToCleanup.add(created.getId());
        });

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, primary.getName());

        Response response = Allure.step(role + ": DELETE deactivate one of two active tech maps", () ->
                techMapFixture.deactivateTechMap(role, primary.getId(), storageId));

        Allure.step("Assert: деактивація дозволена", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            long activeCountAfter = techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, primary.getName());
            assertThat(activeCountAfter).isEqualTo(activeCountBefore - 1);
            assertThat(techMapFixture.getActiveTechMapsByName(storageId, UserRole.ADMIN, alternate.getName()))
                    .anyMatch(m -> alternate.getId().equals(m.getId()));
        });
    }

    @Test(priority = 21, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-MFG-029")
    @Story("Deactivate allowed when product only in past plan")
    @Description("Єдину активну техкарту можна деактивувати, якщо продукт є лише в минулому плані")
    @Severity(SeverityLevel.CRITICAL)
    public void testCanDeactivateWhenProductOnlyInPastPlan(UserRole role) {
        IsolatedTechMapContext context = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        TechnologicalMapResponse techMap = context.getTechMap();

        YearMonth pastPeriod = techMapFixture.nextFreeLocationPlanPeriod(
                storageId, YearMonth.now().minusMonths(1), true);
        assertThat(pastPeriod.atEndOfMonth())
                .as("План має закінчуватись у минулому")
                .isBefore(java.time.LocalDate.now());
        Allure.step("Arrange: минулий план на " + pastPeriod, () -> {
            PlanResponse created = techMapFixture.createLocationPlan(
                    storageId,
                    techMapFixture.getOutputResourceId(techMap),
                    pastPeriod,
                    25.0);
            planIdsToCleanup.add(created.getId());
        });

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName());

        Response response = Allure.step(role + ": DELETE deactivate sole tech map", () ->
                techMapFixture.deactivateTechMap(role, techMap.getId(), storageId));

        Allure.step("Assert: деактивація дозволена — план у минулому", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName()))
                    .isEqualTo(activeCountBefore - 1);
        });
    }

    @Test(priority = 22, dataProvider = "ownerAndAdminRoles")
    @TestCaseId("TC-MFG-030")
    @Story("Deactivate allowed without plan")
    @Description("Єдину активну техкарту можна деактивувати, якщо продукт відсутній у планах")
    @Severity(SeverityLevel.NORMAL)
    public void testCanDeactivateSoleTechMapWhenNoPlan(UserRole role) {
        IsolatedTechMapContext context = techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, storageId);
        TechnologicalMapResponse techMap = context.getTechMap();

        long activeCountBefore = techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName());

        Response response = Allure.step(role + ": DELETE deactivate sole tech map without plan", () ->
                techMapFixture.deactivateTechMap(role, techMap.getId(), storageId));

        Allure.step("Assert: деактивація дозволена", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, techMap.getName()))
                    .isEqualTo(activeCountBefore - 1);
        });
    }
}
