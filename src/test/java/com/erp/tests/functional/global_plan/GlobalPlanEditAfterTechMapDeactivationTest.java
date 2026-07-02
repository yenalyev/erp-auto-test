package com.erp.tests.functional.global_plan;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RequestBodyFactory;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production Planning")
@Feature("Global Plans")
public class GlobalPlanEditAfterTechMapDeactivationTest extends GlobalPlanApiTestBase {

    private TechnologicalMapFixture techMapFixture;
    private ResourceFixture resourceFixture;
    private Long storageId;
    private ResourceResponse productX;
    private TechnologicalMapResponse mapA;
    private TechnologicalMapResponse mapB;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupGlobalPlanApiSuite")
    @Step("Підготовка двох техкарт A і B на виріб X для однієї локації")
    public void setupDualTechMapsForProduct() {
        techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();

        storageId = ConfigProvider.getOwner1StorageId();
        techMapFixture.setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse raw = resourceFixture.createUniqueResource("GP-RAW-" + suffix);
        productX = resourceFixture.createUniqueResource("GP-PROD-X-" + suffix);

        TechnologicalMapRequest mapARequest = TechnologicalMapDataFactory.createProductionMapWithStorages(
                "GP-TM-A",
                List.of(new ResourceUsageRequest(raw.getId(), 1.0)),
                List.of(new ResourceUsageRequest(productX.getId(), 1.0)),
                Set.of(storageId)).build();
        mapA = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, mapARequest);
        mapB = techMapFixture.createAlternateActiveTechMap(UserRole.ADMIN, mapA);
    }

    @AfterClass(alwaysRun = true)
    @Step("Відновити READ_ONLY для режиму техкарт локації")
    public void restoreTechMapMode() {
        if (techMapFixture != null && storageId != null) {
            techMapFixture.setMode(storageId, StorageTechnologicalMapMode.READ_ONLY);
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-GP-045")
    @Story("Decompose after referenced tech map deactivated")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Відомий дефект:** після деактивації техкарти A, на яку посилається збережена декомпозиція,
            POST /decompose повертає HTTP 400 (`global-plan.assignment.technologicalMapId.invalid`).
            На UI це викликає цикл помилок при відкритті вкладки 2 wizard-а.
            
            **Очікувана поведінка (тест фіксує баг):** /decompose має повернути 200 і запропонувати
            активну альтернативну техкарту B (застарілий assignment на M_A ігнорується/зрізається).
            
            **Сценарій:**
            1. На L1 дві активні техкарти A і B на виріб X.
            2. Глобальний план output=X, декомпозиція L1/M_A, generate.
            3. Деактивовано M_A (M_B активна).
            4. PUT оновлення опису — 200.
            5. POST /decompose зі snapshot-ом (як UI `useGlobalPlanDecomposition.start`) — **200**, не 400.
            """)
    public void testDecomposeWithStaleSnapshotSucceedsAfterReferencedTechMapDeactivated() {
        GlobalPlanResponse created = Allure.step("Arrange: глобальний план на виріб X", this::createGlobalPlanForProductX);
        trackGlobalPlan(created.getId());

        DecompositionRequest decomposition = DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                productX.getId(),
                                GlobalPlanDataFactory.assignment(storageId, mapA.getId(), "10")))))
                .build();

        Allure.step("Arrange: декомпозиція та generate з техкартою A", () -> {
            DecompositionResponse decompose = globalPlanFixture.decompose(created.getId(), decomposition);
            assertThat(decompose.isComplete())
                    .as("Одноблочна декомпозиція має бути повною")
                    .isTrue();

            GenerationResponse generation = globalPlanFixture.generate(created.getId(), decomposition);
            trackGeneratedPlans(generation.getPlans().stream()
                    .map(gp -> gp.getPlan().getId())
                    .toList());
        });

        Allure.step("Act: деактивувати техкарту A (техкарта B лишається активною)", () -> {
            Response deactivate = techMapFixture.deactivateTechMap(UserRole.ADMIN, mapA.getId(), storageId);
            assertThat(deactivate.statusCode()).isEqualTo(200);
            assertThat(techMapFixture.countActiveTechMapsByName(storageId, UserRole.ADMIN, mapB.getName()))
                    .isGreaterThanOrEqualTo(1);
        });

        Allure.step("Act: редагувати глобальний план (PUT)", () -> {
            testContext.set(ContextKey.GLOBAL_PLAN, globalPlanFixture.getById(created.getId()));
            Object body = RequestBodyFactory.generate(ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE, testContext);
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE,
                    UserRole.ADMIN,
                    body,
                    created.getId());

            assertThat(response.statusCode()).isEqualTo(200);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE);
        });

        Allure.step("Assert: GET by id — план читається, decomposition збережено", () -> {
            GlobalPlanResponse fetched = globalPlanFixture.getById(created.getId());
            assertThat(fetched.getDecomposition()).isNotNull();
            assertThat(fetched.getDescription()).contains("UPDATED");
            assertThat(fetched.getGeneratedPlans()).isNotEmpty();
        });

        Allure.step("Assert: /decompose зі snapshot-ом (UI reopen tab 2) — очікується 200, не 400", () -> {
            GlobalPlanResponse fetched = globalPlanFixture.getById(created.getId());
            assertThat(fetched.getDecomposition()).isNotNull();

            // UI start(): block 0 seeded from flattened snapshot assignments (useGlobalPlanDecomposition).
            DecompositionRequest uiReopenSeed = DecompositionRequest.builder()
                    .blocks(List.of(
                            GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                                    productX.getId(),
                                    GlobalPlanDataFactory.assignment(storageId, mapA.getId(), "10")))))
                    .build();

            Response decomposeResponse = apiExecutor.execute(
                    ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE,
                    UserRole.ADMIN,
                    uiReopenSeed,
                    created.getId());

            assertThat(decomposeResponse.statusCode())
                    .as("Відомий дефект: зараз 400 через deactivated M_A у snapshot; очікується 200")
                    .isEqualTo(200);
            SchemaRegistry.validateIfSuccess(decomposeResponse, ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE);

            DecompositionResponse decompose = decomposeResponse.as(DecompositionResponse.class);
            assertThat(decompose.getBlocks()).isNotEmpty();
            var options = decompose.getBlocks().getFirst().getItems().getFirst().getOptions();
            assertThat(options)
                    .as("Активна техкарта B має бути серед варіантів")
                    .anyMatch(opt -> mapB.getId().equals(opt.getTechnologicalMap().getId()));
            assertThat(options)
                    .as("Деактивована техкарта A не пропонується")
                    .noneMatch(opt -> mapA.getId().equals(opt.getTechnologicalMap().getId()));
        });
    }

    private GlobalPlanResponse createGlobalPlanForProductX() {
        YearMonth period = globalPlanFixture.nextUniquePeriod();
        GlobalPlanRequest request = GlobalPlanDataFactory.createPlan(
                period.getMonthValue(),
                period.getYear(),
                productX.getId(),
                10.0).build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);
        return response.as(GlobalPlanResponse.class);
    }
}
