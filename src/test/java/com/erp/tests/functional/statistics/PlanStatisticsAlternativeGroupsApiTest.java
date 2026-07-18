package com.erp.tests.functional.statistics;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.response.PlanDailyNeedResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.PlanStatisticsResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan statistics sufficiency uses default alternative from tech map ({@code effectiveInputs}).
 */
@Slf4j
@Epic("Statistics")
@Feature("Plan statistics — alternative groups")
public class PlanStatisticsAlternativeGroupsApiTest extends BaseFunctionalTest {

    private static final double PLAN_AMOUNT = 30.0;

    private TechnologicalMapFixture techMapFixture;
    private Long storageId;
    private final List<Long> plansToCleanup = new ArrayList<>();
    private final List<Long> techMapsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        techMapFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        for (Long planId : plansToCleanup) {
            try {
                techMapFixture.deleteLocationPlan(planId);
            } catch (Exception e) {
                log.warn("Plan cleanup failed for {}: {}", planId, e.getMessage());
            }
        }
        plansToCleanup.clear();
        for (Long techMapId : techMapsToCleanup) {
            try {
                techMapFixture.deactivateTechMap(UserRole.OWNER_1, techMapId, storageId);
            } catch (Exception e) {
                log.warn("Tech map cleanup failed for {}: {}", techMapId, e.getMessage());
            }
        }
        techMapsToCleanup.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-STAT-ALT-001")
    @Story("Plan sufficiency uses default alternative")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Location plan на продукт з alt-group техкартою → STATISTIC_GET_PLAN
            рахує dailyNeed для default alt (effectiveInputs), non-default alt відсутній.
            """)
    public void testPlanStatisticsCountsDefaultAlternativeOnly() {
        TechnologicalMapResponse techMap = techMapFixture.createTechMapWithAlternativeGroup(UserRole.ADMIN, storageId);
        techMapsToCleanup.add(techMap.getId());

        Long productId = techMap.getOutput().getFirst().getResource().getId();
        Long defaultAltId = techMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();
        Long otherAltId = techMap.getGroups().getFirst().getAlternativeResources().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .map(r -> r.getResource().getId())
                .findFirst()
                .orElseThrow();

        // StatisticsFacade.getPlanStatistics only includes plans that overlap today —
        // so the location plan must be for the current calendar month (1 plan / storage / month).
        YearMonth currentMonth = YearMonth.now();
        techMapFixture.getLocationPlans(storageId).stream()
                .filter(p -> currentMonth.getMonthValue() == p.getMonth()
                        && currentMonth.getYear() == p.getYear())
                .map(PlanResponse::getId)
                .forEach(techMapFixture::deleteLocationPlan);

        var plan = techMapFixture.createLocationPlan(storageId, productId, currentMonth, PLAN_AMOUNT);
        plansToCleanup.add(plan.getId());

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STATISTIC_GET_PLAN,
                UserRole.ADMIN,
                String.valueOf(storageId));
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STATISTIC_GET_PLAN);

        PlanStatisticsResponse stats = response.as(PlanStatisticsResponse.class);
        assertThat(stats.getDailyNeed()).isNotNull();

        Optional<PlanDailyNeedResponse> defaultEntry = stats.getDailyNeed().stream()
                .filter(r -> r.getResource() != null && defaultAltId.equals(r.getResource().getId()))
                .findFirst();
        Optional<PlanDailyNeedResponse> otherEntry = stats.getDailyNeed().stream()
                .filter(r -> r.getResource() != null && otherAltId.equals(r.getResource().getId()))
                .findFirst();

        assertThat(defaultEntry).as("default alt in plan dailyNeed").isPresent();
        assertThat(defaultEntry.get().getDailyNeed()).isGreaterThan(0.0);
        assertThat(otherEntry).as("non-default alt excluded from plan need").isEmpty();
    }
}
