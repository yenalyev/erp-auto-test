package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.plan.PlanDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.response.PlanResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Виробничі плани — заборона другого плану на той самий період локації.
 */
@Slf4j
@Epic("Plans")
@Feature("Location production plans")
@Story("Duplicate period guard")
public class PlanDuplicatePeriodApiTest extends GlobalPlanApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-MAN-PLN-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-MAN-PLN AC-02: Admin не може створити 2 виробничі плани для однієї локації
            на один і той самий період (місяць/рік).
            """)
    public void cannotCreateSecondPlanForSameLocationPeriod() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        YearMonth period = globalPlanFixture.nextUniquePeriod();
        Long storageId = chain.getL1StorageId();
        Long resourceId = chain.getResourceA().getId();

        PlanResponse first = globalPlanFixture.createExistingLocationPlan(
                storageId, 10.0, period.getMonthValue(), period.getYear());
        generatedPlanIds.add(first.getId());

        var duplicateRequest = PlanDataFactory.createSimplePlan(
                storageId, resourceId, period.getMonthValue(), period.getYear(), 15.0).build();
        Response duplicate = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_POST_CREATE, UserRole.ADMIN, duplicateRequest);

        assertThat(duplicate.statusCode())
                .as("другий план на той самий період; body=%s", duplicate.asString())
                .isBetween(400, 499);
    }
}
