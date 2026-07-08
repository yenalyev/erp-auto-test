package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.response.DecompositionBlockItemResponse;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.LocationPlanResponse;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plan Decomposition")
public class GlobalPlanDecompositionApiTest extends GlobalPlanApiTestBase {

    private GlobalPlanResponse globalPlan;

    @BeforeMethod(alwaysRun = true)
    public void createPlanForDecomposition() {
        globalPlan = globalPlanFixture.createGlobalPlan(10.0);
        trackGlobalPlan(globalPlan.getId());
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-020")
    @Story("Block0 echo with options")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** перший крок декомпозиції («хто виробляє A») повертає echo-блок з варіантами техкарт і прапором autoAssignable.
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Setup (@BeforeMethod):** глобальний план output A = 10, унікальний місяць.
            **Тіло:** один блок, item для ресурсу A без assignments (`emptyFirstBlock`).
            
            **Перевірки:**
            - Один блок у відповіді.
            - У item: `options` не порожні (доступні техкарти M1 на L1).
            - `autoAssignable = true`, `requiredAmount = 10.0` (збігається з output плану).
            """)
    public void testBlock0EchoWithOptionsAndAutoAssignable() {
        DecompositionRequest request = GlobalPlanDataFactory.emptyFirstBlock(globalPlanFixture.requireChain());
        DecompositionResponse response = globalPlanFixture.decompose(globalPlan.getId(), request);

        assertThat(response.getBlocks()).hasSize(1);
        DecompositionBlockItemResponse item = response.getBlocks().getFirst().getItems().getFirst();
        assertThat(item.getOptions()).isNotEmpty();
        assertThat(item.isAutoAssignable()).isTrue();
        assertThat(item.getRequiredAmount()).isEqualTo(10.0);
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-021")
    @Story("Next block reveals B requirement")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після розподілу виробництва A система обчислює наступний блок потреб по ресурсу B.
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Параметри декомпозиції (блок 1):**
            - Ресурс A, assignment: storage L1, техкарта M1, amount = 10.
            - M1 (один вихід): 2B + 3x → 1A ⇒ для 10 од. A потрібно 20 од. B.
            
            **Перевірки:**
            - `complete = false`, `nextBlock` присутній.
            - У `nextBlock` є item для ресурсу B з `requiredAmount = 20.0`.
            """)
    public void testNextBlockMathRevealsB20() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        DecompositionRequest request = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM1().getId(), "10")))
                ))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(globalPlan.getId(), request);

        assertThat(response.isComplete()).isFalse();
        assertThat(response.getNextBlock()).isNotNull();
        assertThat(response.getNextBlock().getItems()).anyMatch(i ->
                chain.getResourceB().getId().equals(i.getResource().getId())
                        && i.getRequiredAmount() == 20.0);
    }

    @Test(priority = 30)
    @TestCaseId("TC-GP-022")
    @Story("Full C requirement without by-product")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** при одному виході на PRODUCTION-техкарті (без by-product) потреба в C
            дорівнює повному споживанню M2.
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Параметри (блоки 1–2):**
            - Блок 1: A=10 на L1 через M1 (M1: 2B+3x → 1A, без побічного C).
            - Блок 2: B — 12 на L1 + 8 на L2 через M2 (разом 20 од. B).
            - M2 споживає 1C на 1B ⇒ потрібно 20C (усі через M3).
            
            **Перевірки:**
            - `nextBlock` містить ресурс C з `requiredAmount = 20.0`.
            """)
    public void testNextBlockRequiresFullC20WithoutByProduct() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        DecompositionRequest request = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM1().getId(), "10"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceB().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM2().getId(), "12"),
                                GlobalPlanDataFactory.assignment(chain.getL2StorageId(), chain.getMapM2().getId(), "8")))
                ))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(globalPlan.getId(), request);

        assertThat(response.getNextBlock()).isNotNull();
        assertThat(response.getNextBlock().getItems()).anyMatch(i ->
                chain.getResourceC().getId().equals(i.getResource().getId())
                        && i.getRequiredAmount() == 20.0);
    }

    @Test(priority = 40)
    @TestCaseId("TC-GP-023")
    @Story("Complete decomposition")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** повна декомпозиція всіх трьох рівнів ланцюга повертає зведення потреб і чернетки планів на локаціях.
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Параметри:** `completeDecomposition` — 3 блоки (single-output maps):
            - A=10 @L1/M1, B=12@L1+8@L2/M2, C=20@L1/M3.
            
            **Перевірки:**
            - `complete = true`.
            - `requirements.semiFinished` не порожній.
            - `locationPlans` не порожній (мінімум L1 і L2).
            """)
    public void testCompleteDecompositionReturnsRequirementsAndLocationPlans() {
        DecompositionResponse response = globalPlanFixture.decompose(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());

        assertThat(response.isComplete()).isTrue();
        assertThat(response.getRequirements()).isNotNull();
        assertThat(response.getRequirements().getSemiFinished()).isNotEmpty();
        assertThat(response.getLocationPlans()).isNotEmpty();
    }

    @Test(priority = 50)
    @TestCaseId("TC-GP-024")
    @Story("willReplace flag")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** якщо на локації вже є місячний план, декомпозиція позначає майбутній план прапором `willReplace`.
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Параметри:**
            - Існуючий location-план: L1, output A = 50, той самий `month`/`year`, що й глобальний.
            - Повна декомпозиція 3 блоків.
            
            **Перевірки:**
            - У `locationPlans` для L1: `willReplace = true`.
            - `existingPlanId` збігається з id попередньо створеного плану.
            """)
    public void testLocationPlanFlagsExistingPlanForReplacement() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        PlanResponse existing = globalPlanFixture.createExistingLocationPlan(
                chain.getL1StorageId(), 50.0, globalPlan.getMonth(), globalPlan.getYear());
        trackGeneratedPlans(List.of(existing.getId()));

        DecompositionResponse response = globalPlanFixture.decompose(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());

        LocationPlanResponse l1Plan = response.getLocationPlans().stream()
                .filter(lp -> chain.getL1StorageId().equals(lp.getStorage().getId()))
                .findFirst()
                .orElseThrow();

        assertThat(l1Plan.isWillReplace()).isTrue();
        assertThat(l1Plan.getExistingPlanId()).isEqualTo(existing.getId());
    }

    @Test(priority = 60)
    @TestCaseId("TC-GP-025")
    @Story("Over-assignment rejected")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** відхилити розподіл, якщо сума assignment перевищує output глобального плану.
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Параметри:**
            - Глобальний план: output A = 10.
            - Блок 1: assignment A = **15** на L1 через M1 (перевищення на 5 од.).
            
            **Перевірки:**
            - HTTP 400.
            """)
    public void testOverAssignmentReturns400() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        DecompositionRequest request = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM1().getId(), "15")))
                ))
                .build();

        var response = apiExecutor.execute(
                com.erp.api.endpoints.ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE,
                UserRole.ADMIN,
                request,
                globalPlan.getId());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 70)
    @TestCaseId("TC-GP-026")
    @Story("Stale block mismatch")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** відхилити декомпозицію з «застарілим» або пропущеним блоком (невідповідність очікуваному порядку ресурсів).
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Параметри:**
            - Глобальний план output A = 10.
            - Тіло містить лише блок для ресурсу **B** (пропущено обов'язковий перший блок A).
            
            **Перевірки:**
            - HTTP 400 (block mismatch / невалідна послідовність блоків).
            """)
    public void testStaleBlockReturnsBlockMismatch() {
        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        DecompositionRequest request = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceB().getId()))
                ))
                .build();

        var response = apiExecutor.execute(
                com.erp.api.endpoints.ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE,
                UserRole.ADMIN,
                request,
                globalPlan.getId());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 80)
    @TestCaseId("TC-GP-027")
    @Story("Sub-product consumed same location")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** при повній декомпозиції локація L1 отримує чернетку плану з ненульовим output
            (A, частина B та C виробляються на L1).
            
            **Ендпоінт:** `POST /api/v1/global-plans/{id}/decompose`
            **Роль:** ADMIN
            
            **Параметри:** повна декомпозиція M1→M2→M3 (по одному виходу на карту), global output A = 10.
            
            **Перевірки:**
            - У `locationPlans` для L1 поле `output` не порожнє.
            """)
    public void testSubProductConsumedSameLocationKeepsNetRemainder() {
        DecompositionResponse response = globalPlanFixture.decompose(
                globalPlan.getId(),
                globalPlanFixture.buildCompleteDecomposition());

        GlobalPlanChainContext chain = globalPlanFixture.requireChain();
        LocationPlanResponse l1Plan = response.getLocationPlans().stream()
                .filter(lp -> chain.getL1StorageId().equals(lp.getStorage().getId()))
                .findFirst()
                .orElseThrow();

        assertThat(l1Plan.getOutput()).isNotEmpty();
    }
}
