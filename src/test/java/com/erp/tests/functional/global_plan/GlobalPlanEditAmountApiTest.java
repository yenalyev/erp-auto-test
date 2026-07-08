package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.common.GlobalPlanChainExpectations;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.utils.assertions.GlobalPlanAssertions;
import com.erp.utils.assertions.GlobalPlanAssertions.RequirementSection;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans")
public class GlobalPlanEditAmountApiTest extends GlobalPlanApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-GP-051")
    @Story("Edit output amount recalculates requirements")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** зміна output A з 10 на 15 і повторна повна декомпозиція перераховує
            потреби в B, C та сировині.
            
            **Розрахунок (A=15):** M1 → B=30, x=45; M2 (30B) → C=30, y=60; M3 → z=30.
            
            **Сценарій:** PUT A=15 → decompose 3 блоки (A=15, B=30, C=30).
            """)
    public void testEditOutputAmountRecalculatesRequirements() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();

        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(GlobalPlanChainExpectations.OUTPUT_A);
        trackGlobalPlan(created.getId());

        GlobalPlanRequest putBody = GlobalPlanRequest.builder()
                .description(created.getDescription() + " EDITED")
                .month(created.getMonth())
                .year(created.getYear())
                .output(List.of(new com.erp.models.request.ResourceUsageRequest(
                        chain.getResourceA().getId(), GlobalPlanChainExpectations.OUTPUT_A_EDITED)))
                .build();

        Response putResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE,
                UserRole.ADMIN,
                putBody,
                created.getId());
        assertThat(putResponse.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(putResponse, ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE);

        DecompositionRequest fullDecompose = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(
                                        chain.getL1StorageId(), chain.getMapM1().getId(), "15"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceB().getId(),
                                GlobalPlanDataFactory.assignment(
                                        chain.getL1StorageId(), chain.getMapM2().getId(), "18"),
                                GlobalPlanDataFactory.assignment(
                                        chain.getL2StorageId(), chain.getMapM2().getId(), "12"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceC().getId(),
                                GlobalPlanDataFactory.assignment(
                                        chain.getL1StorageId(), chain.getMapM3().getId(), "30")))
                ))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(created.getId(), fullDecompose);

        assertThat(response.isComplete()).isTrue();
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceB().getId(),
                GlobalPlanChainExpectations.SEMI_B_FOR_A15, RequirementSection.SEMI_FINISHED);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceC().getId(),
                30.0, RequirementSection.SEMI_FINISHED);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceX().getId(),
                GlobalPlanChainExpectations.RAW_X_FOR_A15, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceY().getId(),
                60.0, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceZ().getId(),
                30.0, RequirementSection.RAW_MATERIALS);
    }
}
