package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalPlanFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.TechnologicalMapFixture.IsolatedTechMapContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans")
@Story("Historical snapshot seed")
public class GlobalPlanHistoricalTechMapApiTest extends GlobalPlanApiTestBase {

    private TechnologicalMapFixture techMapFixture;
    private Long l1StorageId;

    @Override
    protected boolean shouldInitializeDatabase() {
        return true;
    }

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupGlobalPlanApiSuite")
    @Step("EDIT_ALLOWED на L1 для архіву/створення техкарт")
    public void setupHistoricalTechMapSuite() {
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        l1StorageId = globalPlanFixture.requireChain().getL1StorageId();
        techMapFixture.setMode(l1StorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
    }

    @AfterClass(alwaysRun = true)
    public void restoreTechMapMode() {
        if (techMapFixture != null && l1StorageId != null) {
            techMapFixture.setMode(l1StorageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @Test(priority = 5)
    @TestCaseId("TC-GP-056")
    @Story("Archive allowed in historical snapshot")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** архів дозволений, коли карта є лише в snapshot **неактуального** GP
            (минулий місяць після generate + JDBC backdate).
            
            **Ендпоінт:** `DELETE /api/v1/technological-maps/{id}?storageId=`
            
            Контраст: TC-GP-046/047 (актуальний план блокує).
            """)
    public void testCanArchiveTechMapWhenOnlyInHistoricalGlobalPlan() {
        requireJdbc();
        IsolatedHistorical context = seedIsolatedHistorical("GP-056-M1");
        TechnologicalMapResponse mapM1 = context.map();

        Allure.step("Act: DELETE M1 з історичного snapshot", () -> {
            Response deactivate = techMapFixture.deactivateTechMap(
                    UserRole.ADMIN, mapM1.getId(), l1StorageId);
            assertThat(deactivate.statusCode()).isEqualTo(200);
            assertThat(activeIds(mapM1.getName()))
                    .as("M1 має зникнути з active-list")
                    .doesNotContain(mapM1.getId());
        });

        Allure.step("Isolation: історичний snapshot лишає id M1", () -> {
            GlobalPlanResponse historical = globalPlanFixture.getById(context.seed().pastPlan().getId());
            assertThat(GlobalPlanFixture.snapshotTechMapIds(historical))
                    .contains(mapM1.getId());
        });
    }

    @Test(priority = 6)
    @TestCaseId("TC-GP-057")
    @Story("Structural update allowed in historical snapshot")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** PUT структури (норма input / version bump) дозволений, коли карта є лише
            в snapshot неактуального GP.
            
            **Ендпоінт:** `PUT /api/v1/technological-maps/{id}`
            
            Контраст: TC-GP-054/059.
            """)
    public void testCanUpdateTechMapStructureWhenOnlyInHistoricalGlobalPlan() {
        requireJdbc();
        IsolatedHistorical context = seedIsolatedHistorical("GP-057-M1");
        TechnologicalMapResponse mapM1 = context.map();

        TechnologicalMapResponse mapFetched = techMapFixture.getById(
                UserRole.ADMIN, mapM1.getId(), l1StorageId);
        double originalAmount = mapFetched.getInput().getFirst().getAmount();
        double modifiedAmount = originalAmount + 1.0;
        TechnologicalMapRequest updateRequest =
                TechnologicalMapDataFactory.withFirstInputAmount(mapFetched, modifiedAmount);

        Response response = Allure.step("Act: PUT змінити норму input M1", () ->
                techMapFixture.updateTechMap(UserRole.ADMIN, mapM1.getId(), updateRequest));

        Allure.step("Assert: структура змінена (минулий GP не блокує version bump)", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            TechnologicalMapResponse updated = response.as(TechnologicalMapResponse.class);
            assertThat(updated.getInput().getFirst().getAmount())
                    .as("Нова норма input")
                    .isEqualTo(modifiedAmount);

            GlobalPlanResponse historical = globalPlanFixture.getById(context.seed().pastPlan().getId());
            assertThat(GlobalPlanFixture.snapshotTechMapIds(historical))
                    .as("Історичний snapshot лишає оригінальний id M1")
                    .contains(mapM1.getId());
        });
    }

    @Test(priority = 7)
    @TestCaseId("TC-GP-061")
    @Story("Actual guard wins over historical snapshot")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** якщо карта одночасно в snapshot минулого і актуального GP — діє гуард актуального.
            Архів і зміна структури заборонені.
            
            **Сид:** історичний GP (generate + backdate) + другий GP на майбутній місяць з тією ж M1.
            """)
    public void testActualGlobalPlanGuardWinsWhenMapAlsoInHistoricalPlan() {
        requireJdbc();
        IsolatedHistorical context = seedIsolatedHistorical("GP-061-M1");
        TechnologicalMapResponse mapM1 = context.map();
        Long productAId = context.productId();

        GlobalPlanResponse livePlan = Allure.step("Arrange: актуальний GP з тією ж M1", () ->
                createGeneratedPlan(productAId, mapM1.getId()));

        Allure.step("Act: DELETE M1 (очікувана відмова)", () -> {
            Response deactivate = techMapFixture.deactivateTechMap(
                    UserRole.ADMIN, mapM1.getId(), l1StorageId);
            techMapFixture.assertUsedInGlobalPlanRejection(deactivate, livePlan.getDescription());
            assertThat(activeIds(mapM1.getName())).contains(mapM1.getId());
        });

        Allure.step("Act: PUT структури M1 (очікувана відмова)", () -> {
            TechnologicalMapResponse mapFetched = techMapFixture.getById(
                    UserRole.ADMIN, mapM1.getId(), l1StorageId);
            double originalAmount = mapFetched.getInput().getFirst().getAmount();
            TechnologicalMapRequest updateRequest = TechnologicalMapDataFactory.withFirstInputAmount(
                    mapFetched, originalAmount + 1.0);
            Response update = techMapFixture.updateTechMap(UserRole.ADMIN, mapM1.getId(), updateRequest);
            techMapFixture.assertUsedInGlobalPlanRejection(update, livePlan.getDescription());

            TechnologicalMapResponse mapAfter = techMapFixture.getById(
                    UserRole.ADMIN, mapM1.getId(), l1StorageId);
            assertThat(mapAfter.getVersion()).isEqualTo(mapFetched.getVersion());
            assertThat(mapAfter.getInput().getFirst().getAmount()).isEqualTo(originalAmount);
        });
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-062")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** після архіву техкарти з snapshot **неактуального** GP нова карта на той самий
            продукт працює в новому актуальному плані; reopen історичного snapshot (M1 inactive) — 200.
            
            **Сид:** generate на майбутній місяць, JDBC backdate `global_plan` + `plan`/`plan_output`
            на вільний минулий місяць. Generate за минулий місяць заборонений API.
            
            **Replay:** POST /decompose історичного GP зі snapshot assignments (як UI start).
            **Новий GP:** block 0 assignment **M2 @ L1**, не M1 і не порожні assignments.
            """)
    public void testHistoricalSnapshotArchiveThenNewMapWorksInNewActivePlan() {
        requireJdbc();

        IsolatedTechMapContext isolated = Allure.step("Arrange: ізольовані продукт A і M1", () ->
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, l1StorageId, "GP-062-M1"));
        TechnologicalMapResponse mapM1 = isolated.getTechMap();
        Long productAId = isolated.getProduct().getId();
        String mapM1Name = mapM1.getName();

        GlobalPlanFixture.HistoricalPlanSeed seed = Allure.step(
                "Сид: generate + backdate → історичний snapshot з M1", () -> {
                    GlobalPlanFixture.HistoricalPlanSeed created = globalPlanFixture.seedHistoricalGeneratedPlan(
                            getDbHelper(), productAId, l1StorageId, mapM1.getId(), 10.0);
                    trackGlobalPlan(created.pastPlan().getId());
                    trackGeneratedPlans(created.locationPlanIds());
                    assertThat(GlobalPlanFixture.snapshotTechMapIds(created.pastPlan()))
                            .as("Історичний snapshot має містити M1")
                            .contains(mapM1.getId());
                    return created;
                });

        Allure.step("Mutate: архів M1 з історичного snapshot", () -> {
            Response deactivate = techMapFixture.deactivateTechMap(
                    UserRole.ADMIN, mapM1.getId(), l1StorageId);
            assertThat(deactivate.statusCode()).isEqualTo(200);
            assertThat(activeIds(mapM1Name)).doesNotContain(mapM1.getId());
        });

        Allure.step("Replay: decompose історичного GP зі snapshot M1", () -> {
            DecompositionRequest snapshot = seed.pastPlan().getDecomposition();
            assertThat(snapshot).isNotNull();
            DecompositionResponse replay = globalPlanFixture.decompose(seed.pastPlan().getId(), snapshot);
            assertThat(replay.isComplete())
                    .as("Минулий план replayInactiveCards має прийняти архівовану M1")
                    .isTrue();
        });

        TechnologicalMapResponse mapM2 = Allure.step("Mutate: POST M2 на продукт A", () -> {
            TechnologicalMapResponse created = techMapFixture.createAlternateActiveTechMap(
                    UserRole.ADMIN, mapM1);
            assertThat(techMapFixture.getOutputResourceId(created)).isEqualTo(productAId);
            assertThat(activeIds(created.getName())).contains(created.getId());
            assertThat(activeIds(mapM1Name)).doesNotContain(mapM1.getId());
            return created;
        });

        Allure.step("Replay: новий актуальний GP з assignment M2 @ L1", () -> {
            YearMonth period = globalPlanFixture.nextUniquePeriod();
            GlobalPlanRequest gpRequest = GlobalPlanDataFactory.createPlan(
                    period.getMonthValue(), period.getYear(), productAId, 10.0).build();
            Response createResponse = apiExecutor.execute(
                    ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                    UserRole.ADMIN,
                    gpRequest);
            assertThat(createResponse.statusCode()).isEqualTo(200);
            SchemaRegistry.validateIfSuccess(createResponse, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);
            GlobalPlanResponse created = createResponse.as(GlobalPlanResponse.class);
            trackGlobalPlan(created.getId());

            DecompositionRequest decomposition = DecompositionRequest.builder()
                    .blocks(List.of(GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                            productAId,
                            GlobalPlanDataFactory.assignment(l1StorageId, mapM2.getId(), "10")))))
                    .build();
            DecompositionResponse decompose = globalPlanFixture.decompose(created.getId(), decomposition);
            assertThat(decompose.isComplete()).isTrue();
            List<Long> optionIds = decompose.getBlocks().getFirst().getItems().getFirst().getOptions()
                    .stream()
                    .map(opt -> opt.getTechnologicalMap().getId())
                    .toList();
            assertThat(optionIds).contains(mapM2.getId());
            assertThat(optionIds).doesNotContain(mapM1.getId());

            GenerationResponse generation = globalPlanFixture.generate(created.getId(), decomposition);
            trackGeneratedPlans(generation.getPlans().stream()
                    .map(gp -> gp.getPlan().getId())
                    .toList());

            GlobalPlanResponse fetched = globalPlanFixture.getById(created.getId());
            assertThat(GlobalPlanFixture.snapshotTechMapIds(fetched)).contains(mapM2.getId());
            assertThat(GlobalPlanFixture.snapshotTechMapIds(fetched)).doesNotContain(mapM1.getId());
        });

        Allure.step("Isolation: історичний snapshot лишає M1", () -> {
            GlobalPlanResponse historical = globalPlanFixture.getById(seed.pastPlan().getId());
            assertThat(GlobalPlanFixture.snapshotTechMapIds(historical))
                    .as("Історичний snapshot не має змінитись")
                    .contains(mapM1.getId())
                    .doesNotContain(mapM2.getId());
        });
    }

    private void requireJdbc() {
        if (ensureDatabaseHelper() == null) {
            throw new SkipException(
                    "Історичний snapshot GP потребує JDBC для backdate періоду "
                            + "(VPN до dev DB або -Duse.database=true)");
        }
    }

    private IsolatedHistorical seedIsolatedHistorical(String mapPrefix) {
        IsolatedTechMapContext isolated = Allure.step("Arrange: ізольовані продукт A і M1", () ->
                techMapFixture.createIsolatedProductionTechMap(UserRole.ADMIN, l1StorageId, mapPrefix));
        TechnologicalMapResponse mapM1 = isolated.getTechMap();
        Long productAId = isolated.getProduct().getId();

        GlobalPlanFixture.HistoricalPlanSeed seed = Allure.step(
                "Сид: generate + backdate → історичний snapshot з M1", () -> {
                    GlobalPlanFixture.HistoricalPlanSeed created = globalPlanFixture.seedHistoricalGeneratedPlan(
                            getDbHelper(), productAId, l1StorageId, mapM1.getId(), 10.0);
                    trackGlobalPlan(created.pastPlan().getId());
                    trackGeneratedPlans(created.locationPlanIds());
                    assertThat(GlobalPlanFixture.snapshotTechMapIds(created.pastPlan()))
                            .as("Історичний snapshot має містити M1")
                            .contains(mapM1.getId());
                    return created;
                });
        return new IsolatedHistorical(mapM1, productAId, seed);
    }

    private GlobalPlanResponse createGeneratedPlan(Long productAId, Long techMapId) {
        YearMonth period = globalPlanFixture.nextUniquePeriod();
        GlobalPlanRequest gpRequest = GlobalPlanDataFactory.createPlan(
                period.getMonthValue(), period.getYear(), productAId, 10.0).build();
        Response createResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                gpRequest);
        assertThat(createResponse.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(createResponse, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);
        GlobalPlanResponse created = createResponse.as(GlobalPlanResponse.class);
        trackGlobalPlan(created.getId());

        DecompositionRequest decomposition = DecompositionRequest.builder()
                .blocks(List.of(GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                        productAId,
                        GlobalPlanDataFactory.assignment(l1StorageId, techMapId, "10")))))
                .build();
        DecompositionResponse decompose = globalPlanFixture.decompose(created.getId(), decomposition);
        assertThat(decompose.isComplete()).isTrue();
        GenerationResponse generation = globalPlanFixture.generate(created.getId(), decomposition);
        trackGeneratedPlans(generation.getPlans().stream()
                .map(gp -> gp.getPlan().getId())
                .toList());

        GlobalPlanResponse fetched = globalPlanFixture.getById(created.getId());
        assertThat(GlobalPlanFixture.snapshotTechMapIds(fetched)).contains(techMapId);
        return fetched;
    }

    private List<Long> activeIds(String name) {
        return techMapFixture.getActiveTechMapsByName(l1StorageId, UserRole.ADMIN, name).stream()
                .map(TechnologicalMapResponse::getId)
                .toList();
    }

    private record IsolatedHistorical(
            TechnologicalMapResponse map,
            Long productId,
            GlobalPlanFixture.HistoricalPlanSeed seed
    ) {}
}
