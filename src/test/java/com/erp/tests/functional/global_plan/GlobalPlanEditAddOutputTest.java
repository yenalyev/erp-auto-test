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
import com.erp.models.response.GenerationResponse;
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
public class GlobalPlanEditAddOutputTest extends GlobalPlanApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-GP-050")
    @Story("Decompose after adding output item on edit")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після повного створення глобального плану (етапи 1–4) редагування етапу 1
            з додаванням ще одного пункту output має відкривати етап 2 без помилки.
            
            **Відтворює UI:** перший POST /decompose після «Зберегти зміни» — block 0 = поточні
            `plan.output`; assignments лише з snapshot block 0 (A зберігає M1=10; новий direct
            output B отримує порожній список, далі auto-assign у runChain).
            
            **Ланцюг:** single-output PRODUCTION maps — M1: 2B+3x→1A, M2: 2y+1C→1B, M3: 1z→1C.
            
            **Сценарій:**
            1. POST global plan output A=10, унікальний місяць.
            2. POST /decompose + /generate — повна декомпозиція (B=12+8, C=20; етапи 2–4).
            3. PUT — додати другий output (ресурс B з ланцюга — напівфабрикат у snapshot, amount=5).
            4. POST /decompose з uiStartSeed після оновленого GET.
            
            **Очікування:** HTTP 200; block 0 містить обидва output-ресурси (A і B).
            UI-аналог: TC-GP-UI-050.
            """)
    public void testDecomposeAfterAddingOutputItemSucceeds() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();

        GlobalPlanResponse created = Allure.step("Arrange: створити глобальний план output A=10", () -> {
            GlobalPlanResponse plan = globalPlanFixture.createGlobalPlan(10.0);
            trackGlobalPlan(plan.getId());
            return plan;
        });

        Allure.step("Arrange: повна декомпозиція та generate (етапи 2–4)", () -> {
            DecompositionRequest decomposition = globalPlanFixture.buildCompleteDecomposition();
            globalPlanFixture.decompose(created.getId(), decomposition);
            GenerationResponse generation = globalPlanFixture.generate(created.getId(), decomposition);
            trackGeneratedPlans(generation.getPlans().stream()
                    .map(gp -> gp.getPlan().getId())
                    .toList());
        });

        GlobalPlanResponse updated = Allure.step("Act: PUT — додати другий output (ресурс B)", () -> {
            GlobalPlanResponse beforeUpdate = globalPlanFixture.getById(created.getId());
            assertThat(beforeUpdate.getDecomposition()).isNotNull();
            assertThat(beforeUpdate.getOutput()).hasSize(1);

            GlobalPlanRequest putBody = GlobalPlanDataFactory.withAdditionalOutput(
                    beforeUpdate,
                    chain.getResourceB().getId(),
                    5.0);

            Response putResponse = apiExecutor.execute(
                    ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE,
                    UserRole.ADMIN,
                    putBody,
                    created.getId());

            assertThat(putResponse.statusCode())
                    .as("PUT з додатковим output має зберегти план")
                    .isEqualTo(200);
            SchemaRegistry.validateIfSuccess(putResponse, ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE);

            GlobalPlanResponse afterPut = putResponse.as(GlobalPlanResponse.class);
            assertThat(afterPut.getOutput()).hasSize(2);
            return globalPlanFixture.getById(created.getId());
        });

        Allure.step("Assert: /decompose як UI start() після edit Tab 1 — 200", () -> {
            assertThat(updated.getOutput()).hasSize(2);
            assertThat(updated.getDecomposition())
                    .as("Snapshot декомпозиції має лишитися після PUT")
                    .isNotNull();

            DecompositionRequest uiStartSeed = GlobalPlanDataFactory.uiStartSeed(updated);

            assertThat(uiStartSeed.getBlocks().getFirst().getItems())
                    .filteredOn(item -> item.getResourceId().equals(chain.getResourceB().getId()))
                    .singleElement()
                    .satisfies(item -> assertThat(item.getAssignments())
                            .as("Новий direct output B не має успадковувати block-1 assignments")
                            .isEmpty());

            Response decomposeResponse = apiExecutor.execute(
                    ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE,
                    UserRole.ADMIN,
                    uiStartSeed,
                    created.getId());

            assertThat(decomposeResponse.statusCode())
                    .as("Після додавання пункту на етапі 1 етап 2 (decompose) має відкритися без 400")
                    .isEqualTo(200);
            SchemaRegistry.validateIfSuccess(decomposeResponse, ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE);

            DecompositionResponse decompose = decomposeResponse.as(DecompositionResponse.class);
            assertThat(decompose.getBlocks()).isNotEmpty();
            assertThat(decompose.getBlocks().getFirst().getItems())
                    .extracting(item -> item.getResource().getId())
                    .containsExactlyInAnyOrder(
                            chain.getResourceA().getId(),
                            chain.getResourceB().getId());
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-052")
    @Story("Add output recalculates requirements after full re-decompose")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** після додавання direct output B=5 до існуючого A=10 повна re-decompose
            перераховує gross-потреби (Tab 3): напівфабрикат, що є і direct output, і споживається
            внутрішньо, рахується як inputDemand + seedDemand.
            
            **Розрахунок:** M1 для A=10 → inputDemand B=20; gross B = 20 + 5 (seed) = 25;
            C=25 (5 від direct B@M2 + 20 від M2 для B); x=30, y=50, z=25.
            
            **Сценарій:** create+generate → PUT add B=5 → decompose 3 блоки (A=10, B=25, C=25).
            """)
    public void testAddOutputRecalculatesRequirementsAfterFullDecompose() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();

        GlobalPlanResponse created = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(created.getId());

        DecompositionRequest initial = globalPlanFixture.buildCompleteDecomposition();
        globalPlanFixture.decompose(created.getId(), initial);
        GenerationResponse generation = globalPlanFixture.generate(created.getId(), initial);
        trackGeneratedPlans(generation.getPlans().stream()
                .map(gp -> gp.getPlan().getId())
                .toList());

        GlobalPlanResponse beforeUpdate = globalPlanFixture.getById(created.getId());
        GlobalPlanRequest putBody = GlobalPlanDataFactory.withAdditionalOutput(
                beforeUpdate, chain.getResourceB().getId(), 5.0);

        Response putResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE,
                UserRole.ADMIN,
                putBody,
                created.getId());
        assertThat(putResponse.statusCode()).isEqualTo(200);

        DecompositionRequest fullReDecompose = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(
                                GlobalPlanDataFactory.item(
                                        chain.getResourceA().getId(),
                                        GlobalPlanDataFactory.assignment(
                                                chain.getL1StorageId(), chain.getMapM1().getId(), "10")),
                                GlobalPlanDataFactory.item(
                                        chain.getResourceB().getId(),
                                        GlobalPlanDataFactory.assignment(
                                                chain.getL1StorageId(), chain.getMapM2().getId(), "5"))),
                        GlobalPlanDataFactory.block(
                                GlobalPlanDataFactory.item(
                                        chain.getResourceB().getId(),
                                        GlobalPlanDataFactory.assignment(
                                                chain.getL1StorageId(), chain.getMapM2().getId(), "12"),
                                        GlobalPlanDataFactory.assignment(
                                                chain.getL2StorageId(), chain.getMapM2().getId(), "8")),
                                GlobalPlanDataFactory.item(
                                        chain.getResourceC().getId(),
                                        GlobalPlanDataFactory.assignment(
                                                chain.getL1StorageId(), chain.getMapM3().getId(), "5"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceC().getId(),
                                GlobalPlanDataFactory.assignment(
                                        chain.getL1StorageId(), chain.getMapM3().getId(), "20")))
                ))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(created.getId(), fullReDecompose);

        assertThat(response.isComplete()).isTrue();
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceB().getId(),
                GlobalPlanChainExpectations.SEMI_B_WITH_DIRECT_OUTPUT_5, RequirementSection.SEMI_FINISHED);
        GlobalPlanAssertions.assertRequirementProducedEqualsRequired(
                response.getRequirements(), chain.getResourceB().getId());
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceC().getId(),
                GlobalPlanChainExpectations.SEMI_C_WITH_DIRECT_B_5, RequirementSection.SEMI_FINISHED);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceX().getId(),
                GlobalPlanChainExpectations.RAW_X, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceY().getId(),
                GlobalPlanChainExpectations.RAW_Y_WITH_DIRECT_B_5, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceZ().getId(),
                GlobalPlanChainExpectations.RAW_Z_WITH_DIRECT_B_5, RequirementSection.RAW_MATERIALS);
    }
}
