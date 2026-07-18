package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.fixtures.ProductionFixture;
import com.erp.models.common.GlobalPlanAltGroupContext;
import com.erp.models.common.GlobalPlanAltGroupExpectations;
import com.erp.models.request.AlternativeInputRequest;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.assertions.GlobalPlanAssertions;
import com.erp.utils.assertions.GlobalPlanAssertions.RequirementSection;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Global-plan decomposer coverage for tech maps with alternative groups.
 * <p>
 * Planning requirements reflect the alternative selected for planning (via {@code isDefault}
 * on the tech map until DecompositionRequest supports per-assignment choice).
 * Production may consume a non-default alternative — that does <b>not</b> feed back into
 * decompose; TC-GP-ALT-007 locks that independence (plan↔floor divergence by design).
 */
@Slf4j
@Epic("Production Planning")
@Feature("Global Plan — alternative groups")
public class GlobalPlanAlternativeGroupsApiTest extends BaseFunctionalTest {

    private GlobalPlanFixture globalPlanFixture;
    private ProductionFixture productionFixture;
    private final List<Long> globalPlanIdsToCleanup = new ArrayList<>();
    private final List<Long> generatedPlanIds = new ArrayList<>();
    private GlobalPlanResponse globalPlan;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка alt-group ланцюга для глобальних планів")
    public void setupAltGroupGlobalPlanSuite() {
        globalPlanFixture = new GlobalPlanFixture(testContext, apiExecutor);
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        globalPlanFixture.prepareAltGroupChain();
        SchemaRegistry.logSchemaCoverage();
    }

    @BeforeMethod(alwaysRun = true)
    public void createAltGroupPlan() {
        globalPlan = globalPlanFixture.createAltGroupGlobalPlan(GlobalPlanAltGroupExpectations.OUTPUT_P);
        globalPlanIdsToCleanup.add(globalPlan.getId());
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        if (globalPlanFixture == null) {
            return;
        }
        globalPlanFixture.cleanupGeneratedPlans(generatedPlanIds);
        for (Long planId : globalPlanIdsToCleanup) {
            try {
                globalPlanFixture.deleteGlobalPlan(planId);
            } catch (AssertionError e) {
                log.warn("Global plan cleanup failed for id {}: {}", planId, e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-ALT-001")
    @Story("Decomposer counts actually used alternative")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** decomposer рахує **фактично використаний** ресурс з альтернативної групи —
            це може бути і default, і non-default; не «завжди лише початковий default».
            
            **Сценарій (ізольована техкарта, не shared chain):**
            1. Використаний = D (поточний default) → rawMaterials F=10, D=20; E відсутній.
            2. Перемикаємо використаний на E (swap isDefault) → re-decompose → E=30; D відсутній.
            
            **Примітка:** до появи вибору альтернативи в DecompositionRequest
            «фактично використаний» для планування задається через isDefault на техкарті.
            """)
    public void testDecomposeCountsActuallyUsedAlternative() {
        GlobalPlanAltGroupContext base = globalPlanFixture.requireAltChain();
        String suffix = String.valueOf(System.currentTimeMillis());

        ResourceResponse p = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT001-P-" + suffix);
        ResourceResponse d = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT001-D-" + suffix);
        ResourceResponse e = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT001-E-" + suffix);
        ResourceResponse f = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT001-F-" + suffix);

        TechnologicalMapResponse map = Allure.step("Arrange: техкарта F + {D default, E}", () -> {
            var group = TechnologicalMapDataFactory.alternativeGroup(
                    "Клей",
                    TechnologicalMapDataFactory.alternativeResource(
                            d.getId(), GlobalPlanAltGroupExpectations.DEFAULT_ALT_AMOUNT, true),
                    TechnologicalMapDataFactory.alternativeResource(
                            e.getId(), GlobalPlanAltGroupExpectations.OTHER_ALT_AMOUNT, false));
            TechnologicalMapRequest request = TechnologicalMapDataFactory
                    .createProductionMapWithStorages(
                            "GP-ALT001-M",
                            List.of(new ResourceUsageRequest(
                                    f.getId(), GlobalPlanAltGroupExpectations.FIXED_AMOUNT)),
                            List.of(new ResourceUsageRequest(p.getId(), 1.0)),
                            Set.of(base.getL1StorageId()))
                    .groups(List.of(group))
                    .build();
            return globalPlanFixture.getTechMapFixture()
                    .createTechMapWithRequest(UserRole.ADMIN, request);
        });

        var period = globalPlanFixture.nextUniquePeriod();
        GlobalPlanResponse plan = createPlan(
                p.getId(), period.getMonthValue(), period.getYear(),
                GlobalPlanAltGroupExpectations.OUTPUT_P);
        globalPlanIdsToCleanup.add(plan.getId());

        Allure.step("1) Використаний = D → requirements D (+ F), без E", () -> {
            DecompositionRequest request = DecompositionRequest.builder()
                    .blocks(List.of(GlobalPlanDataFactory.block(
                            GlobalPlanDataFactory.item(p.getId(),
                                    GlobalPlanDataFactory.assignment(
                                            base.getL1StorageId(), map.getId(), "10")))))
                    .build();
            DecompositionResponse response = globalPlanFixture.decompose(plan.getId(), request);
            assertThat(response.isComplete()).isTrue();
            GlobalPlanAssertions.assertRequirementAmount(
                    response.getRequirements(), f.getId(),
                    GlobalPlanAltGroupExpectations.RAW_F, RequirementSection.RAW_MATERIALS);
            GlobalPlanAssertions.assertRequirementAmount(
                    response.getRequirements(), d.getId(),
                    GlobalPlanAltGroupExpectations.RAW_D, RequirementSection.RAW_MATERIALS);
            GlobalPlanAssertions.assertRequirementAbsent(response.getRequirements(), e.getId());
        });

        TechnologicalMapResponse mapWithE = Allure.step(
                "2a) Зробити E фактично використаним (swap isDefault)", () -> {
                    TechnologicalMapRequest update = TechnologicalMapDataFactory.withSwappedDefault(map);
                    Response response = apiExecutor.execute(
                            ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                            UserRole.ADMIN,
                            update,
                            String.valueOf(map.getId()));
                    assertThat(response.statusCode()).isEqualTo(200);
                    return response.as(TechnologicalMapResponse.class);
                });

        Allure.step("2b) Re-decompose — requirements E (+ F), без D", () -> {
            DecompositionRequest request = DecompositionRequest.builder()
                    .blocks(List.of(GlobalPlanDataFactory.block(
                            GlobalPlanDataFactory.item(p.getId(),
                                    GlobalPlanDataFactory.assignment(
                                            base.getL1StorageId(), mapWithE.getId(), "10")))))
                    .build();
            DecompositionResponse response = globalPlanFixture.decompose(plan.getId(), request);
            assertThat(response.isComplete()).isTrue();
            GlobalPlanAssertions.assertRequirementAmount(
                    response.getRequirements(), f.getId(),
                    GlobalPlanAltGroupExpectations.RAW_F, RequirementSection.RAW_MATERIALS);
            GlobalPlanAssertions.assertRequirementAmount(
                    response.getRequirements(), e.getId(),
                    GlobalPlanAltGroupExpectations.RAW_E_AFTER_SWAP, RequirementSection.RAW_MATERIALS);
            GlobalPlanAssertions.assertRequirementAbsent(response.getRequirements(), d.getId());
        });
    }

    @Test(priority = 11)
    @TestCaseId("TC-GP-ALT-002")
    @Story("Mixed fixed input and alternative group")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** fixed input F і default D з групи обидва в rawMaterials; non-default E відсутній.
            Карта: 1F + {D@2 default, E@3} → 1P, assign 10.
            """)
    public void testDecomposeIncludesFixedInputAndDefaultAlt() {
        GlobalPlanAltGroupContext chain = globalPlanFixture.requireAltChain();
        DecompositionResponse response = globalPlanFixture.decompose(
                globalPlan.getId(),
                globalPlanFixture.buildAltGroupDecomposition(GlobalPlanAltGroupExpectations.OUTPUT_P));

        assertThat(response.isComplete()).isTrue();
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceF().getId(),
                GlobalPlanAltGroupExpectations.RAW_F, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), chain.getResourceD().getId(),
                GlobalPlanAltGroupExpectations.RAW_D, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAbsent(
                response.getRequirements(), chain.getResourceE().getId());
    }

    @Test(priority = 12)
    @TestCaseId("TC-GP-ALT-003")
    @Story("Groups-only tech map in decompose")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** техкарта лише з groups (без fixed input) коректно декомпозується;
            demand лише з default.
            """)
    public void testDecomposeGroupsOnlyTechMap() {
        GlobalPlanAltGroupContext base = globalPlanFixture.requireAltChain();
        String suffix = String.valueOf(System.currentTimeMillis());

        ResourceResponse p2 = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT-P2-" + suffix);
        ResourceResponse d2 = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT-D2-" + suffix);
        ResourceResponse e2 = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT-E2-" + suffix);

        TechnologicalMapRequest mapRequest = TechnologicalMapDataFactory.createProductionMapGroupsOnly(
                List.of(d2, e2, p2), base.getL1StorageId());
        TechnologicalMapResponse mapOnly = globalPlanFixture.getTechMapFixture()
                .createTechMapWithRequest(UserRole.ADMIN, mapRequest);

        YearMonthPeriod period = allocatePeriod();
        GlobalPlanResponse plan = createPlan(p2.getId(), period.month(), period.year(), 10.0);
        globalPlanIdsToCleanup.add(plan.getId());

        DecompositionRequest request = DecompositionRequest.builder()
                .blocks(List.of(GlobalPlanDataFactory.block(
                        GlobalPlanDataFactory.item(p2.getId(),
                                GlobalPlanDataFactory.assignment(base.getL1StorageId(), mapOnly.getId(), "10")))))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(plan.getId(), request);
        assertThat(response.isComplete()).isTrue();
        GlobalPlanAssertions.assertRequirementAmount(
                response.getRequirements(), d2.getId(), 15.0, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAbsent(response.getRequirements(), e2.getId());
    }

    @Test(priority = 13)
    @TestCaseId("TC-GP-ALT-004")
    @Story("Produceable default opens nextBlock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** якщо default альтернатива сама є виробом іншої PRODUCTION техкарти —
            вона потрапляє в nextBlock; non-default не відкриває блок.
            """)
    public void testProduceableDefaultOpensNextBlock() {
        GlobalPlanAltGroupContext base = globalPlanFixture.requireAltChain();
        String suffix = String.valueOf(System.currentTimeMillis());

        ResourceResponse p2 = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT-PP-" + suffix);
        ResourceResponse d2 = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT-PD-" + suffix);
        ResourceResponse e2 = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT-PE-" + suffix);
        ResourceResponse z = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT-PZ-" + suffix);

        TechnologicalMapResponse mapOut = globalPlanFixture.getTechMapFixture().createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapGroupsOnly(
                        List.of(d2, e2, p2), base.getL1StorageId()));

        TechnologicalMapResponse mapSemi = globalPlanFixture.getTechMapFixture().createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "GP-ALT-SEMI",
                        List.of(new ResourceUsageRequest(z.getId(), 1.0)),
                        List.of(new ResourceUsageRequest(d2.getId(), 1.0)),
                        Set.of(base.getL1StorageId())).build());

        YearMonthPeriod period = allocatePeriod();
        GlobalPlanResponse plan = createPlan(p2.getId(), period.month(), period.year(), 10.0);
        globalPlanIdsToCleanup.add(plan.getId());

        DecompositionRequest firstBlock = DecompositionRequest.builder()
                .blocks(List.of(GlobalPlanDataFactory.block(
                        GlobalPlanDataFactory.item(p2.getId(),
                                GlobalPlanDataFactory.assignment(base.getL1StorageId(), mapOut.getId(), "10")))))
                .build();

        DecompositionResponse response = globalPlanFixture.decompose(plan.getId(), firstBlock);
        assertThat(response.isComplete()).isFalse();
        GlobalPlanAssertions.assertNextBlockRequired(response, d2.getId(), 15.0);
        GlobalPlanAssertions.assertNextBlockAbsentResource(response, e2.getId());
        assertThat(mapSemi.getId()).isNotNull();
    }

    @Test(priority = 20)
    @TestCaseId("TC-GP-ALT-005")
    @Story("Switch default then re-decompose")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після зміни default D→E на техкарті той самий DecompositionRequest
            на re-decompose дає requirements з E замість D.
            """)
    public void testSwitchDefaultThenRedecompose() {
        GlobalPlanAltGroupContext chain = globalPlanFixture.requireAltChain();
        DecompositionRequest decomposition = globalPlanFixture.buildAltGroupDecomposition(
                GlobalPlanAltGroupExpectations.OUTPUT_P);

        DecompositionResponse before = globalPlanFixture.decompose(globalPlan.getId(), decomposition);
        GlobalPlanAssertions.assertRequirementAmount(
                before.getRequirements(), chain.getResourceD().getId(),
                GlobalPlanAltGroupExpectations.RAW_D, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAbsent(before.getRequirements(), chain.getResourceE().getId());

        TechnologicalMapResponse currentMap = Allure.step("Swap default on tech map", () -> {
            TechnologicalMapRequest update = TechnologicalMapDataFactory.withSwappedDefault(chain.getMapProduct());
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.TECH_MAP_UPDATE_NAME,
                    UserRole.ADMIN,
                    update,
                    String.valueOf(chain.getMapProduct().getId()));
            assertThat(response.statusCode()).isEqualTo(200);
            TechnologicalMapResponse updated = response.as(TechnologicalMapResponse.class);
            // keep context map id for assignments — new version id must be used in assignments
            chain.setMapProduct(updated);
            return updated;
        });

        DecompositionRequest afterSwap = GlobalPlanDataFactory.altGroupDecomposition(
                chain, GlobalPlanAltGroupExpectations.OUTPUT_P);
        assertThat(afterSwap.getBlocks().getFirst().getItems().getFirst().getAssignments()
                .getFirst().getTechnologicalMapId()).isEqualTo(currentMap.getId());

        DecompositionResponse after = globalPlanFixture.decompose(globalPlan.getId(), afterSwap);
        assertThat(after.isComplete()).isTrue();
        GlobalPlanAssertions.assertRequirementAmount(
                after.getRequirements(), chain.getResourceE().getId(),
                GlobalPlanAltGroupExpectations.RAW_E_AFTER_SWAP, RequirementSection.RAW_MATERIALS);
        GlobalPlanAssertions.assertRequirementAbsent(after.getRequirements(), chain.getResourceD().getId());
    }

    @Test(priority = 21)
    @TestCaseId("TC-GP-ALT-006")
    @Story("Generate after alt-group decomposition")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            **Мета:** POST /generate після повної декомпозиції з alt-group техкартою → 200;
            snapshot decomposition збережено; location output на product-side.
            """)
    public void testGenerateAfterAltGroupDecomposition() {
        GlobalPlanAltGroupContext chain = globalPlanFixture.requireAltChain();
        // Fresh map version may have been swapped by prior test — reload from context
        DecompositionRequest decomposition = globalPlanFixture.buildAltGroupDecomposition(
                GlobalPlanAltGroupExpectations.OUTPUT_P);

        DecompositionResponse decomposed = globalPlanFixture.decompose(globalPlan.getId(), decomposition);
        assertThat(decomposed.isComplete()).isTrue();

        GenerationResponse generated = globalPlanFixture.generate(globalPlan.getId(), decomposition);
        assertThat(generated.getPlans()).isNotEmpty();
        generatedPlanIds.addAll(generated.getPlans().stream().map(p -> p.getPlan().getId()).toList());

        GlobalPlanResponse stored = globalPlanFixture.getById(globalPlan.getId());
        assertThat(stored.getDecomposition()).isNotNull();
        assertThat(stored.getDecomposition().getBlocks()).isNotEmpty();

        GlobalPlanAssertions.assertLocationOutput(
                decomposed.getLocationPlans(),
                chain.getL1StorageId(),
                chain.getResourceP().getId(),
                GlobalPlanAltGroupExpectations.OUTPUT_P);
    }

    @Test(priority = 22)
    @TestCaseId("TC-GP-ALT-007")
    @Story("Decompose after production with non-default alternative")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після виробництва з **non-default** альтернативою (E) декомпозиція/generate
            тієї ж техкарти лишаються коректними — потоки незалежні.
            
            **Продуктова поведінка (by design):**
            - Виробництво списує фактично обраний E (alternativeInputs).
            - Decomposer **не** читає історію виробництва; requirements далі рахують **default D**.
            - Stock gate у decompose немає — низький запас D не блокує complete.
            
            **Сценарій:**
            1. Seed stock F/D/E; OWNER_1 POST production з вибором E.
            2. Assert stock: −E, D без змін від alt.
            3. Global plan на P → decompose + generate з тією ж картою.
            4. Assert: 200, complete; rawMaterials F+D; E відсутній у requirements.
            """)
    public void testDecomposeAfterProductionWithNonDefaultAlternative() {
        GlobalPlanAltGroupContext base = globalPlanFixture.requireAltChain();
        String suffix = String.valueOf(System.currentTimeMillis());
        Long storageId = base.getL1StorageId();

        ResourceResponse p = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT007-P-" + suffix);
        ResourceResponse d = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT007-D-" + suffix);
        ResourceResponse e = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT007-E-" + suffix);
        ResourceResponse f = globalPlanFixture.getResourceFixture()
                .createUniqueResource("GP-ALT007-F-" + suffix);

        TechnologicalMapResponse map = Allure.step("Arrange: техкарта F + {D default, E}", () -> {
            var group = TechnologicalMapDataFactory.alternativeGroup(
                    "Клей",
                    TechnologicalMapDataFactory.alternativeResource(
                            d.getId(), GlobalPlanAltGroupExpectations.DEFAULT_ALT_AMOUNT, true),
                    TechnologicalMapDataFactory.alternativeResource(
                            e.getId(), GlobalPlanAltGroupExpectations.OTHER_ALT_AMOUNT, false));
            TechnologicalMapRequest request = TechnologicalMapDataFactory
                    .createProductionMapWithStorages(
                            "GP-ALT007-M",
                            List.of(new ResourceUsageRequest(
                                    f.getId(), GlobalPlanAltGroupExpectations.FIXED_AMOUNT)),
                            List.of(new ResourceUsageRequest(p.getId(), 1.0)),
                            Set.of(storageId))
                    .groups(List.of(group))
                    .build();
            return globalPlanFixture.getTechMapFixture()
                    .createTechMapWithRequest(UserRole.ADMIN, request);
        });

        Long groupId = map.getGroups().getFirst().getId();
        double produceAmount = 5.0;

        Allure.step("Seed stock і створити виробництво з non-default E", () -> {
            productionFixture.ensureStockForTechMapInputs(storageId, map, 200.0);

            Set<Long> tracked = Set.of(f.getId(), d.getId(), e.getId(), p.getId());
            ProductionStockAssertions.StockSnapshot before = ProductionStockAssertions.capture(
                    apiExecutor, storageId, UserRole.OWNER_1, tracked, "before non-default production");

            List<AlternativeInputRequest> choice = ProductionDataFactory.alternativeInputsChoosing(
                    map, groupId, e.getId());
            ManufacturingItemResponse created = productionFixture.createAsWithAlternatives(
                    UserRole.OWNER_1, storageId, map, produceAmount, choice);
            assertThat(created.getId()).isNotNull();

            ProductionStockAssertions.StockSnapshot after = ProductionStockAssertions.capture(
                    apiExecutor, storageId, UserRole.OWNER_1, tracked, "after non-default production");
            ProductionStockAssertions.assertDelta(before, after, Map.of(
                    f.getId(), -(produceAmount * GlobalPlanAltGroupExpectations.FIXED_AMOUNT),
                    d.getId(), 0.0,
                    e.getId(), -(produceAmount * GlobalPlanAltGroupExpectations.OTHER_ALT_AMOUNT),
                    p.getId(), produceAmount
            ), p.getId());
        });

        var period = globalPlanFixture.nextUniquePeriod();
        GlobalPlanResponse plan = createPlan(
                p.getId(), period.getMonthValue(), period.getYear(),
                GlobalPlanAltGroupExpectations.OUTPUT_P);
        globalPlanIdsToCleanup.add(plan.getId());

        DecompositionRequest decomposition = DecompositionRequest.builder()
                .blocks(List.of(GlobalPlanDataFactory.block(
                        GlobalPlanDataFactory.item(p.getId(),
                                GlobalPlanDataFactory.assignment(storageId, map.getId(), "10")))))
                .build();

        DecompositionResponse decomposed = Allure.step(
                "Decompose після non-default production — requirements = default D, не E", () -> {
                    DecompositionResponse response = globalPlanFixture.decompose(plan.getId(), decomposition);
                    assertThat(response.isComplete())
                            .as("Decompose не має ламатися через prior production з E")
                            .isTrue();
                    GlobalPlanAssertions.assertRequirementAmount(
                            response.getRequirements(), f.getId(),
                            GlobalPlanAltGroupExpectations.RAW_F, RequirementSection.RAW_MATERIALS);
                    GlobalPlanAssertions.assertRequirementAmount(
                            response.getRequirements(), d.getId(),
                            GlobalPlanAltGroupExpectations.RAW_D, RequirementSection.RAW_MATERIALS);
                    GlobalPlanAssertions.assertRequirementAbsent(response.getRequirements(), e.getId());
                    return response;
                });

        Allure.step("Generate після non-default production — 200, snapshot збережено", () -> {
            GenerationResponse generated = globalPlanFixture.generate(plan.getId(), decomposition);
            assertThat(generated.getPlans()).isNotEmpty();
            generatedPlanIds.addAll(generated.getPlans().stream().map(gp -> gp.getPlan().getId()).toList());

            GlobalPlanResponse stored = globalPlanFixture.getById(plan.getId());
            assertThat(stored.getDecomposition()).isNotNull();
            assertThat(stored.getDecomposition().getBlocks()).isNotEmpty();

            GlobalPlanAssertions.assertLocationOutput(
                    decomposed.getLocationPlans(), storageId, p.getId(),
                    GlobalPlanAltGroupExpectations.OUTPUT_P);
        });
    }

    private GlobalPlanResponse createPlan(Long productId, int month, int year, double amount) {
        var request = GlobalPlanDataFactory.createPlan(month, year, productId, amount).build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE, UserRole.ADMIN, request);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.as(GlobalPlanResponse.class);
    }

    private YearMonthPeriod allocatePeriod() {
        var period = globalPlanFixture.nextUniquePeriod();
        return new YearMonthPeriod(period.getMonthValue(), period.getYear());
    }

    private record YearMonthPeriod(int month, int year) {
    }
}
