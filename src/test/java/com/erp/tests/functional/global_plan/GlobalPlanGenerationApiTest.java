package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.common.GlobalPlanChainExpectations;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.PlanResponse;
import com.erp.utils.assertions.GlobalPlanAssertions;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plan Generation")
public class GlobalPlanGenerationApiTest extends GlobalPlanApiTestBase {

    private GlobalPlanResponse globalPlan;

    @BeforeMethod(alwaysRun = true)
    public void createPlanForGeneration() {
        globalPlan = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(globalPlan.getId());
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-040")
    @Story("Generate per-location plans")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** генерація з повної декомпозиції створює окремі плани на локаціях з коректними межами місяця.
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/generate`
            **Роль:** ADMIN
            
            **Параметри:**
            - Глобальний план output A = 10, унікальний місяць.
            - Тіло: повна декомпозиція (A@L1/M1, B@L1+L2/M2, C@L1/M3).
            
            **Перевірки:**
            - У відповіді ≥ 2 згенерованих планів.
            - План L1: `month`/`year` = як у глобального, `from`/`to` заповнені, `output` не порожній.
            """)
    public void testGenerateCreatesPerLocationPlansWithMonthBounds() {
        GenerationResponse generation = globalPlanFixture.generate(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());
        trackGeneratedPlans(generation.getPlans().stream().map(p -> p.getPlan().getId()).toList());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        assertThat(generation.getPlans()).hasSizeGreaterThanOrEqualTo(2);

        PlanResponse l1Plan = generation.getPlans().stream()
                .map(gp -> gp.getPlan())
                .filter(p -> chain.getL1StorageId().equals(p.getStorage().getId()))
                .findFirst()
                .orElseThrow();

        assertThat(l1Plan.getMonth()).isEqualTo(globalPlan.getMonth());
        assertThat(l1Plan.getYear()).isEqualTo(globalPlan.getYear());
        assertThat(l1Plan.getFrom()).isNotNull();
        assertThat(l1Plan.getTo()).isNotNull();
        assertThat(l1Plan.getOutput()).isNotEmpty();
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-041")
    @Story("Replace existing monthly plan")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** генерація замінює існуючий місячний план на локації новим записом (`replaced = true`).
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/generate`
            **Роль:** ADMIN
            
            **Параметри:**
            - Попередній location-план: L1, output A = 99, той самий month/year що глобальний.
            - Повна декомпозиція перед generate.
            
            **Перевірки:**
            - Для L1 у відповіді: `replaced = true`.
            - Id нового плану ≠ id існуючого плану.
            """)
    public void testGenerateReplacesExistingPlanForMonth() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        PlanResponse existing = globalPlanFixture.createExistingLocationPlan(
                chain.getL1StorageId(), 99.0, globalPlan.getMonth(), globalPlan.getYear());
        Long existingId = existing.getId();

        GenerationResponse generation = globalPlanFixture.generate(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());

        trackGeneratedPlans(generation.getPlans().stream().map(p -> p.getPlan().getId()).toList());

        var replaced = generation.getPlans().stream()
                .filter(gp -> chain.getL1StorageId().equals(gp.getPlan().getStorage().getId()))
                .findFirst()
                .orElseThrow();

        assertThat(replaced.isReplaced()).isTrue();
        assertThat(replaced.getPlan().getId()).isNotEqualTo(existingId);
    }

    @Test(priority = 30)
    @TestCaseId("TC-GP-042")
    @Story("Generated plans visible via plans endpoint")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** згенеровані плани доступні через стандартний API списку планів локації.
            
            **Ендпоінти:**
            - `POST /global-plans/{id}/generate`
            - `GET /plans` (за storageId L1)
            **Роль:** ADMIN
            
            **Параметри:** повна декомпозиція, global output A = 10.
            
            **Перевірки:**
            - Список планів L1 містить хоча б один id з відповіді generate.
            """)
    public void testGeneratedPlansVisibleViaPlansEndpoint() {
        GenerationResponse generation = globalPlanFixture.generate(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());
        trackGeneratedPlans(generation.getPlans().stream().map(p -> p.getPlan().getId()).toList());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        List<PlanResponse> plans = globalPlanFixture.getLocationPlans(chain.getL1StorageId());

        assertThat(plans).anyMatch(p ->
                generation.getPlans().stream().anyMatch(gp -> gp.getPlan().getId().equals(p.getId())));
    }

    @Test(priority = 40)
    @TestCaseId("TC-GP-043")
    @Story("Decomposition snapshot persisted")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** після generate знімок декомпозиції зберігається на сутності глобального плану.
            
            **Ендпоінти:**
            - `POST /global-plans/{id}/generate`
            - `GET /global-plans/{id}`
            **Роль:** ADMIN
            
            **Параметри:** повна декомпозиція (3 блоки A→B→C, по одному виходу на карту).
            
            **Перевірки:**
            - `decomposition` не null.
            - `decomposition.blocks` містить 3 блоки.
            """)
    public void testGeneratePersistsDecompositionSnapshot() {
        globalPlanFixture.generate(globalPlan.getId(), globalPlanFixture.buildCompleteDecomposition());

        GlobalPlanResponse fetched = globalPlanFixture.getById(globalPlan.getId());
        assertThat(fetched.getDecomposition()).isNotNull();
        assertThat(fetched.getDecomposition().getBlocks()).hasSize(3);
    }

    @Test(priority = 50)
    @TestCaseId("TC-GP-044")
    @Story("Incomplete decomposition rejected")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** заборонити generate, якщо декомпозиція не завершена (лише echo першого блоку).
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/generate`
            **Роль:** ADMIN
            
            **Параметри:**
            - Глобальний план output A = 10.
            - Тіло: `emptyFirstBlock` — блок A без assignments (неповна декомпозиція).
            
            **Перевірки:**
            - HTTP 400.
            """)
    public void testIncompleteDecompositionReturns400() {
        DecompositionRequest incomplete = GlobalPlanDataFactory.emptyFirstBlock(globalPlanFixture.requireChain());

        var response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_GENERATE,
                UserRole.ADMIN,
                incomplete,
                globalPlan.getId());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 60)
    @TestCaseId("TC-GP-055")
    @Story("Generated plans exact output amounts")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після generate per-location плани містять точні net-output кількості.
            
            **Очікування (output A=10, повна декомпозиція):**
            - L1: A=10
            - L2: B=8
            """)
    public void testGenerateCreatesExactLocationOutputAmounts() {
        GenerationResponse generation = globalPlanFixture.generate(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());
        trackGeneratedPlans(generation.getPlans().stream().map(p -> p.getPlan().getId()).toList());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();

        PlanResponse l1Plan = generation.getPlans().stream()
                .map(gp -> gp.getPlan())
                .filter(p -> chain.getL1StorageId().equals(p.getStorage().getId()))
                .findFirst()
                .orElseThrow();
        PlanResponse l2Plan = generation.getPlans().stream()
                .map(gp -> gp.getPlan())
                .filter(p -> chain.getL2StorageId().equals(p.getStorage().getId()))
                .findFirst()
                .orElseThrow();

        GlobalPlanAssertions.assertPlanOutputAmount(
                l1Plan, chain.getResourceA().getId(), GlobalPlanChainExpectations.L1_OUTPUT_A);
        GlobalPlanAssertions.assertPlanOutputAmount(
                l2Plan, chain.getResourceB().getId(), GlobalPlanChainExpectations.L2_OUTPUT_B);
    }
}
