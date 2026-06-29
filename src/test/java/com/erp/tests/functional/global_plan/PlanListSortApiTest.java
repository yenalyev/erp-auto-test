package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.response.PlanResponse;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin view on {@code /plans} with «Всі локації»: {@code GET /api/v1/plans} (no {@code storageId}).
 * Expected order: start date ({@code from}) DESC, then location name ({@code storage.name}) ASC.
 */
@Slf4j
@Epic("Plans")
@Feature("Plan list — admin cross-location sort")
public class PlanListSortApiTest extends GlobalPlanApiTestBase {

    private static final Comparator<PlanResponse> ADMIN_PLANS_SORT = Comparator
            .comparing(PlanResponse::getFrom, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                    plan -> plan.getStorage() != null ? plan.getStorage().getName() : null,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    @Test(priority = 1)
    @TestCaseId("TC-PLAN-001")
    @Story("Admin all-locations list sort")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin на /plans з «Всі локації»:
            сортування за датою початку (from) DESC, потім за назвою локації ASC.
            """)
    public void adminAllLocationsPlansSortedByStartDateDescThenLocationNameAsc() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        YearMonth olderMonth = globalPlanFixture.nextUniquePeriod();
        YearMonth newerMonth = globalPlanFixture.nextUniquePeriod();

        PlanResponse olderAtL2 = globalPlanFixture.createExistingLocationPlan(
                chain.getL2StorageId(), 10.0, olderMonth.getMonthValue(), olderMonth.getYear());
        PlanResponse olderAtL1 = globalPlanFixture.createExistingLocationPlan(
                chain.getL1StorageId(), 10.0, olderMonth.getMonthValue(), olderMonth.getYear());
        PlanResponse newerAtL1 = globalPlanFixture.createExistingLocationPlan(
                chain.getL1StorageId(), 10.0, newerMonth.getMonthValue(), newerMonth.getYear());

        Set<Long> createdIds = Set.of(olderAtL2.getId(), olderAtL1.getId(), newerAtL1.getId());
        generatedPlanIds.addAll(createdIds);

        Response response = Allure.step("GET /plans без storageId (Admin, як «Всі локації»)", () ->
                apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.PLAN_GET_ALL_ADMIN,
                        UserRole.ADMIN,
                        Map.of()));

        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.PLAN_GET_ALL_ADMIN);

        List<PlanResponse> allPlans = DatabaseIntegrityValidator.extractList(response, PlanResponse.class);
        assertThat(allPlans).isNotNull();

        List<PlanResponse> createdPlansInResponseOrder = allPlans.stream()
                .filter(plan -> createdIds.contains(plan.getId()))
                .toList();

        assertThat(createdPlansInResponseOrder)
                .as("Усі створені плани мають бути у відповіді GET /plans")
                .hasSize(3);
        assertThat(createdPlansInResponseOrder)
                .as("Порядок: from DESC, storage.name ASC")
                .isSortedAccordingTo(ADMIN_PLANS_SORT);

        List<Long> idsInResponseOrder = allPlans.stream()
                .map(PlanResponse::getId)
                .collect(Collectors.toList());
        int idxNewer = idsInResponseOrder.indexOf(newerAtL1.getId());
        int idxOlderL1 = idsInResponseOrder.indexOf(olderAtL1.getId());
        int idxOlderL2 = idsInResponseOrder.indexOf(olderAtL2.getId());

        assertThat(idxNewer)
                .as("Новіший план має бути вище у списку")
                .isLessThan(Math.min(idxOlderL1, idxOlderL2));

        if (olderAtL1.getStorage().getName().compareToIgnoreCase(olderAtL2.getStorage().getName()) <= 0) {
            assertThat(idxOlderL1).as("При однаковій даті L1 перед L2 за назвою ASC").isLessThan(idxOlderL2);
        } else {
            assertThat(idxOlderL2).as("При однаковій даті L2 перед L1 за назвою ASC").isLessThan(idxOlderL1);
        }

        log.info("Plan list sort OK — newerAtL1={}, olderAtL1={}, olderAtL2={}",
                newerAtL1.getId(), olderAtL1.getId(), olderAtL2.getId());
    }
}
