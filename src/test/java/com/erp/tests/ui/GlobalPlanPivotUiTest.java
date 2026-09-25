package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.pages.GlobalPlansPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans — pivot table view")
public class GlobalPlanPivotUiTest extends BaseUITest {

    private static final List<String> MONTHS = List.of(
            "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
            "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень");
    private static final double OLDER_A_AMOUNT = 4.0;
    private static final double NEWER_A_AMOUNT = 10.0;
    private static final double NEWER_B_AMOUNT = 2.5;

    private GlobalPlanFixture globalPlans;
    private ResourceFixture resources;
    private TechnologicalMapFixture techMaps;
    private ResourceCategoryResponse categoryA;
    private ResourceCategoryResponse categoryB;
    private TechnologicalMapFixture.IsolatedTechMapContext productA;
    private TechnologicalMapFixture.IsolatedTechMapContext productB;
    private YearMonth olderPeriod;
    private YearMonth newerPeriod;
    private final List<Long> planIdsToCleanup = new ArrayList<>();
    private final List<TechnologicalMapFixture.IsolatedTechMapContext> mapsToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        globalPlans = new GlobalPlanFixture(testContext, apiExecutor);
        resources = globalPlans.getResourceFixture();
        techMaps = globalPlans.getTechMapFixture();
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();

        List<ResourceCategoryResponse> categories = apiExecutor.execute(
                        ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL,
                        UserRole.ADMIN)
                .jsonPath()
                .getList("", ResourceCategoryResponse.class);
        List<ResourceCategoryResponse> uniquelyNamedCategories = uniquelyNamedCategories(categories);
        assertThat(uniquelyNamedCategories)
                .as("Pivot category filter requires two categories with unique visible names")
                .hasSizeGreaterThanOrEqualTo(2);
        categoryA = uniquelyNamedCategories.get(0);
        categoryB = uniquelyNamedCategories.get(1);

        Long unitId = testContext.get(ContextKey.SHARED_UNIT_ID);
        Long storageId = ConfigProvider.getOwner1StorageId();
        String suffix = String.valueOf(System.currentTimeMillis());
        productA = techMaps.createIsolatedProductionTechMap(
                UserRole.ADMIN, storageId, "GP-PIVOT-A-" + suffix, unitId, categoryA.getId());
        mapsToCleanup.add(productA);
        productB = techMaps.createIsolatedProductionTechMap(
                UserRole.ADMIN, storageId, "GP-PIVOT-B-" + suffix, unitId, categoryB.getId());
        mapsToCleanup.add(productB);

        olderPeriod = globalPlans.nextUniquePeriod();
        GlobalPlanResponse older = globalPlans.createGlobalPlanForPeriod(
                olderPeriod.getMonthValue(),
                olderPeriod.getYear(),
                "GP-PIVOT-OLDER-" + suffix,
                List.of(new ResourceUsageRequest(productA.getProduct().getId(), OLDER_A_AMOUNT)));
        planIdsToCleanup.add(older.getId());

        newerPeriod = globalPlans.nextUniquePeriod();
        GlobalPlanResponse newer = globalPlans.createGlobalPlanForPeriod(
                newerPeriod.getMonthValue(),
                newerPeriod.getYear(),
                "GP-PIVOT-NEWER-" + suffix,
                List.of(
                        new ResourceUsageRequest(productA.getProduct().getId(), NEWER_A_AMOUNT),
                        new ResourceUsageRequest(productB.getProduct().getId(), NEWER_B_AMOUNT)));
        planIdsToCleanup.add(newer.getId());

        assertThat(newerPeriod).as("Second allocated period must be newer").isAfter(olderPeriod);

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        injectAllLocationsView();

        log.info("Global plan pivot seed: older={}, newer={}, A={}, B={}, categories=[{}, {}]",
                olderPeriod, newerPeriod,
                productA.getProduct().getId(), productB.getProduct().getId(),
                categoryA.getName(), categoryB.getName());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupPivotData() {
        if (globalPlans != null) {
            for (Long planId : planIdsToCleanup.reversed()) {
                try {
                    globalPlans.deleteGlobalPlan(planId);
                } catch (Exception | AssertionError e) {
                    log.warn("Pivot global plan cleanup failed for {}: {}", planId, e.getMessage());
                }
            }
        }

        Long storageId = ConfigProvider.getOwner1StorageId();
        if (techMaps != null) {
            for (TechnologicalMapFixture.IsolatedTechMapContext context : mapsToCleanup.reversed()) {
                try {
                    techMaps.deactivateTechMap(UserRole.ADMIN, context.getTechMap().getId(), storageId);
                } catch (Exception e) {
                    log.warn("Pivot tech map cleanup failed for {}: {}", context.getTechMap().getId(), e.getMessage());
                }
            }
        }

        if (resources != null) {
            Set<Long> resourceIds = new LinkedHashSet<>();
            for (TechnologicalMapFixture.IsolatedTechMapContext context : mapsToCleanup) {
                context.getTechMap().getInput().stream()
                        .map(ResourceUsageResponse::getResource)
                        .forEach(resource -> resourceIds.add(resource.getId()));
                context.getTechMap().getOutput().stream()
                        .map(ResourceUsageResponse::getResource)
                        .forEach(resource -> resourceIds.add(resource.getId()));
            }
            for (Long resourceId : resourceIds) {
                try {
                    resources.deactivate(UserRole.ADMIN, resourceId);
                } catch (Exception e) {
                    log.warn("Pivot resource cleanup failed for {}: {}", resourceId, e.getMessage());
                }
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-PIVOT-001")
    @Story("Switch between list and resource pivot")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            `/global-plans` opens in list mode. The «Ресурси» toggle renders the pivot and adds
            `view=resources`; switching back restores the list and removes the query parameter.
            """)
    public void switchesBetweenListAndResourcePivot() {
        GlobalPlansPage plansPage = new GlobalPlansPage(page).open();

        assertThat(plansPage.isListViewSelected()).isTrue();
        assertThat(page.url()).doesNotContain("view=");

        plansPage.switchToResourcesView();
        assertThat(plansPage.isResourcesViewSelected()).isTrue();
        assertThat(plansPage.isResourcesViewVisible()).isTrue();
        assertThat(page.url()).contains("view=resources");

        plansPage.switchToListView();
        assertThat(plansPage.isListViewSelected()).isTrue();
        assertThat(page.url()).doesNotContain("view=");
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-PIVOT-002")
    @Story("Pivot matrix contains correct periods and output amounts")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Two API-seeded global plans share product A; the newer plan additionally contains product B.
            The pivot must show descending period columns, one row per resource, exact amounts and `—`
            where a resource is absent in a period.
            """)
    public void resourcePivotShowsCorrectMatrix() {
        GlobalPlansPage plansPage = new GlobalPlansPage(page).openResourcesView();
        String olderLabel = periodLabel(olderPeriod);
        String newerLabel = periodLabel(newerPeriod);
        List<String> headers = plansPage.getPivotPeriodHeaders();

        assertThat(headers).contains(olderLabel, newerLabel);
        assertThat(headers.indexOf(newerLabel))
                .as("Newer period column must precede older period column: %s", headers)
                .isLessThan(headers.indexOf(olderLabel));

        Long resourceAId = productA.getProduct().getId();
        Long resourceBId = productB.getProduct().getId();
        assertThat(plansPage.getPivotResourceRowCount(resourceAId)).isEqualTo(1);
        assertThat(plansPage.getPivotResourceRowCount(resourceBId)).isEqualTo(1);
        assertThat(plansPage.getPivotResourceRowText(resourceAId))
                .contains(productA.getProduct().getName(), productA.getProduct().getUnit().getShortName());
        assertThat(plansPage.getPivotResourceRowText(resourceBId))
                .contains(productB.getProduct().getName(), productB.getProduct().getUnit().getShortName());

        assertThat(plansPage.getPivotAmount(resourceAId, newerLabel)).isEqualTo("10");
        assertThat(plansPage.getPivotAmount(resourceAId, olderLabel)).isEqualTo("4");
        assertThat(plansPage.getPivotAmount(resourceBId, newerLabel)).isEqualTo("2.5");
        assertThat(plansPage.getPivotAmount(resourceBId, olderLabel)).isEqualTo("—");
    }

    @Test(priority = 30)
    @TestCaseId("TC-GP-PIVOT-003")
    @Story("Category multiselect filters pivot rows with OR semantics")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Selecting category A leaves only product A; adding category B shows both products.
            Removing A leaves only B, and clearing the final selection restores all rows.
            """)
    public void categoryFilterControlsPivotRows() {
        GlobalPlansPage plansPage = new GlobalPlansPage(page).openResourcesView();
        Long resourceAId = productA.getProduct().getId();
        Long resourceBId = productB.getProduct().getId();

        plansPage.togglePivotCategory(categoryA.getName())
                .waitForPivotResourceVisibility(resourceAId, true)
                .waitForPivotResourceVisibility(resourceBId, false);

        plansPage.togglePivotCategory(categoryB.getName())
                .waitForPivotResourceVisibility(resourceAId, true)
                .waitForPivotResourceVisibility(resourceBId, true);

        plansPage.togglePivotCategory(categoryA.getName())
                .waitForPivotResourceVisibility(resourceAId, false)
                .waitForPivotResourceVisibility(resourceBId, true);

        plansPage.togglePivotCategory(categoryB.getName())
                .waitForPivotResourceVisibility(resourceAId, true)
                .waitForPivotResourceVisibility(resourceBId, true);
    }

    @Test(priority = 40)
    @TestCaseId("TC-GP-PIVOT-004")
    @Story("Resources view supports direct links and reload")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Opening `/global-plans?view=resources` directly selects the pivot. A browser reload preserves
            the URL-backed mode, while switching to the list removes the view parameter.
            """)
    public void resourcesDeepLinkSurvivesReload() {
        GlobalPlansPage plansPage = new GlobalPlansPage(page).openResourcesView();
        assertThat(plansPage.isResourcesViewSelected()).isTrue();
        assertThat(page.url()).contains("view=resources");

        page.reload();
        plansPage.waitForLoaded();
        assertThat(plansPage.isResourcesViewSelected()).isTrue();
        assertThat(plansPage.isResourcesViewVisible()).isTrue();
        assertThat(page.url()).contains("view=resources");

        plansPage.switchToListView();
        assertThat(page.url()).doesNotContain("view=");
    }

    private static List<ResourceCategoryResponse> uniquelyNamedCategories(
            List<ResourceCategoryResponse> categories) {
        if (categories == null) {
            return List.of();
        }
        Map<String, Long> nameCounts = categories.stream()
                .filter(category -> category != null && category.getName() != null && !category.getName().isBlank())
                .map(ResourceCategoryResponse::getName)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return categories.stream()
                .filter(category -> category != null && category.getId() != null)
                .filter(category -> category.getName() != null && !category.getName().isBlank())
                .filter(category -> nameCounts.getOrDefault(category.getName(), 0L) == 1L)
                .toList();
    }

    private static String periodLabel(YearMonth period) {
        return MONTHS.get(period.getMonthValue() - 1) + " " + period.getYear();
    }
}
