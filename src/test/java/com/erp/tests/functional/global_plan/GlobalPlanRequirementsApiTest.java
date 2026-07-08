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
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plan Requirements")
public class GlobalPlanRequirementsApiTest extends GlobalPlanApiTestBase {

    private GlobalPlanResponse globalPlan;

    @BeforeMethod(alwaysRun = true)
    public void createPlanForRequirements() {
        globalPlan = globalPlanFixture.createGlobalPlan(GlobalPlanChainExpectations.OUTPUT_A);
        trackGlobalPlan(globalPlan.getId());
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-028")
    @Story("Raw material requirements")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після повної декомпозиції ланцюга M1/M2/M3 для output A=10
            `requirements.rawMaterials` містить точні брутто-потреби сировини.
            
            **Розрахунок:** M1 (10×): x=30; M2 (20×): y=40; M3 (20×): z=20.
            
            **Перевірки:** x=30, y=40, z=20 у `rawMaterials`.
            """)
    public void testCompleteDecompositionRawMaterialAmounts() {
        DecompositionResponse response = globalPlanFixture.decompose(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        assertThat(response.isComplete()).isTrue();
        assertThat(response.getRequirements()).isNotNull();

        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceX().getId(),
                GlobalPlanChainExpectations.RAW_X, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceY().getId(),
                GlobalPlanChainExpectations.RAW_Y, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceZ().getId(),
                GlobalPlanChainExpectations.RAW_Z, RequirementSection.RAW_MATERIALS);
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-029")
    @Story("Semi-finished requirements")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після повної декомпозиції напівфабрикати B і C мають точні `requiredAmount`
            і `producedAmount` (вироблено стільки ж, скільки спожито).
            
            **Розрахунок:** M1 потребує 20B; M2 потребує 20C; обидва повністю покриті assignments.
            
            **Перевірки:** B=20, C=20; `producedAmount` = `requiredAmount` для кожного.
            """)
    public void testCompleteDecompositionSemiFinishedAmounts() {
        DecompositionResponse response = globalPlanFixture.decompose(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        assertThat(response.isComplete()).isTrue();

        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceB().getId(),
                GlobalPlanChainExpectations.SEMI_B, RequirementSection.SEMI_FINISHED);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceC().getId(),
                GlobalPlanChainExpectations.SEMI_C, RequirementSection.SEMI_FINISHED);
        GlobalPlanAssertions.assertRequirementProducedEqualsRequired(
                response.getRequirements(), chain.getResourceB().getId());
        GlobalPlanAssertions.assertRequirementProducedEqualsRequired(
                response.getRequirements(), chain.getResourceC().getId());
    }

    @Test(priority = 30)
    @TestCaseId("TC-GP-030")
    @Story("Location plan net output")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** net-output локацій після повної декомпозиції враховує споживання
            напівфабрикатів на тій самій локації.
            
            **Очікування:**
            - L1: лише A=10 (B і C повністю споживаються на L1).
            - L2: B=8 (вироблено на L2, не споживається локально).
            """)
    public void testCompleteDecompositionLocationNetOutputs() {
        DecompositionResponse response = globalPlanFixture.decompose(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        assertThat(response.isComplete()).isTrue();
        assertThat(response.getLocationPlans()).isNotEmpty();

        GlobalPlanAssertions.assertLocationOutput(
                response.getLocationPlans(), chain.getL1StorageId(),
                chain.getResourceA().getId(), GlobalPlanChainExpectations.L1_OUTPUT_A);
        GlobalPlanAssertions.assertLocationOutput(
                response.getLocationPlans(), chain.getL2StorageId(),
                chain.getResourceB().getId(), GlobalPlanChainExpectations.L2_OUTPUT_B);
    }

    @Test(priority = 40)
    @TestCaseId("TC-GP-031")
    @Story("Under-assignment blocks completion")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** недорозподіл output (5 з 10 для A) не завершує декомпозицію
            і блокує generate.
            
            **Параметри:** блок 1 — assignment A=5 на L1/M1 (замість 10).
            
            **Перевірки:** `complete=false`; generate → HTTP 400.
            """)
    public void testUnderAssignmentBlocksGenerate() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        DecompositionRequest partial = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(
                                        chain.getL1StorageId(), chain.getMapM1().getId(), "5")))
                ))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(globalPlan.getId(), partial);

        assertThat(response.isComplete()).isFalse();

        var generateResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_GENERATE,
                UserRole.ADMIN,
                partial,
                globalPlan.getId());

        assertThat(generateResponse.statusCode()).isEqualTo(400);
    }

    @Test(priority = 50)
    @TestCaseId("TC-GP-032")
    @Story("Output amount change recalculates next block")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** зміна output A з 10 на 15 перераховує потребу в B на наступному блоці.
            
            **Сценарій:** PUT A=15 → POST /decompose з block 0 (A=15@L1/M1).
            
            **Перевірки:** `nextBlock` містить B з `requiredAmount=30` (M1: 2B на 1A).
            """)
    public void testOutputAmountChangeRecalculatesNextBlock() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        GlobalPlanRequest putBody = GlobalPlanRequest.builder()
                .description(globalPlan.getDescription())
                .month(globalPlan.getMonth())
                .year(globalPlan.getYear())
                .output(List.of(new com.erp.models.request.ResourceUsageRequest(
                        chain.getResourceA().getId(), GlobalPlanChainExpectations.OUTPUT_A_EDITED)))
                .build();

        var putResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE,
                UserRole.ADMIN,
                putBody,
                globalPlan.getId());
        assertThat(putResponse.statusCode()).isEqualTo(200);

        DecompositionRequest afterPut = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(
                                        chain.getL1StorageId(), chain.getMapM1().getId(), "15")))
                ))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(globalPlan.getId(), afterPut);

        assertThat(response.isComplete()).isFalse();
        GlobalPlanAssertions.assertNextBlockRequired(
                response, chain.getResourceB().getId(), GlobalPlanChainExpectations.SEMI_B_FOR_A15);
    }
}
