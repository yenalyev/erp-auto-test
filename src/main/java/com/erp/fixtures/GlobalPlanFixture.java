package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class GlobalPlanFixture extends BaseFixture {

    private final ResourceFixture resourceFixture;
    private final TechnologicalMapFixture techMapFixture;
    private YearMonth periodBase;
    private int periodOffset;

    public GlobalPlanFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка ланцюга техкарт для глобального плану (M1/M2/M3)")
    public GlobalPlanChainContext prepareDecompositionChain() {
        GlobalPlanChainContext existing = testContext.get(ContextKey.GLOBAL_PLAN_CHAIN);
        if (existing != null) {
            return existing;
        }

        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();

        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse a = resourceFixture.createUniqueResource("GP-A-" + suffix);
        ResourceResponse b = resourceFixture.createUniqueResource("GP-B-" + suffix);
        ResourceResponse c = resourceFixture.createUniqueResource("GP-C-" + suffix);
        ResourceResponse x = resourceFixture.createUniqueResource("GP-x-" + suffix);
        ResourceResponse y = resourceFixture.createUniqueResource("GP-y-" + suffix);
        ResourceResponse z = resourceFixture.createUniqueResource("GP-z-" + suffix);

        Long l1 = ConfigProvider.getOwner1StorageId();
        Long l2 = ConfigProvider.getOwner2StorageId();

        // M1: 2B + 3x -> 1A + 1C @L1
        TechnologicalMapResponse m1 = createMap("GP-M1", Set.of(l1),
                List.of(usage(b, 2), usage(x, 3)),
                List.of(usage(a, 1), usage(c, 1)));

        // M2: 2y + 1C -> 1B @L1+L2
        TechnologicalMapResponse m2 = createMap("GP-M2", Set.of(l1, l2),
                List.of(usage(y, 2), usage(c, 1)),
                List.of(usage(b, 1)));

        // M3: 1z -> 1C @L1
        TechnologicalMapResponse m3 = createMap("GP-M3", Set.of(l1),
                List.of(usage(z, 1)),
                List.of(usage(c, 1)));

        periodBase = GlobalPlanDataFactory.uniquePlanPeriod(3);
        periodOffset = 0;

        GlobalPlanChainContext chain = GlobalPlanChainContext.builder()
                .l1StorageId(l1)
                .l2StorageId(l2)
                .resourceA(a)
                .resourceB(b)
                .resourceC(c)
                .resourceX(x)
                .resourceY(y)
                .resourceZ(z)
                .mapM1(m1)
                .mapM2(m2)
                .mapM3(m3)
                .build();

        testContext.set(ContextKey.GLOBAL_PLAN_CHAIN, chain);
        log.info("Global plan chain ready (period base {}/{})", periodBase.getMonthValue(), periodBase.getYear());
        return chain;
    }

    /** Allocates a fresh calendar month per global plan (one plan per month rule). */
    public YearMonth nextUniquePeriod() {
        if (periodBase == null) {
            periodBase = GlobalPlanDataFactory.uniquePlanPeriod(3);
            periodOffset = 0;
        }
        return periodBase.plusMonths(periodOffset++);
    }

    @Step("API: створити глобальний план output A={amount}")
    public GlobalPlanResponse createGlobalPlan(double amount) {
        GlobalPlanChainContext chain = requireChain();
        YearMonth period = nextUniquePeriod();
        return createGlobalPlanForPeriod(period.getMonthValue(), period.getYear(), amount);
    }

    @Step("API: створити глобальний план {month}/{year} output A={amount}")
    public GlobalPlanResponse createGlobalPlanForPeriod(int month, int year, double amount) {
        GlobalPlanChainContext chain = requireChain();
        GlobalPlanRequest request = GlobalPlanDataFactory.createPlan(
                month,
                year,
                chain.getResourceA().getId(),
                amount).build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);
        validateSuccess(response, "Create global plan");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);

        GlobalPlanResponse created = response.as(GlobalPlanResponse.class);
        testContext.set(ContextKey.GLOBAL_PLAN_ID, created.getId());
        testContext.set(ContextKey.GLOBAL_PLAN, created);
        return created;
    }

    @Step("API: POST decompose для global plan {planId}")
    public DecompositionResponse decompose(Long planId, DecompositionRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE,
                UserRole.ADMIN,
                request,
                planId);
        validateSuccess(response, "Decompose global plan " + planId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE);
        return response.as(DecompositionResponse.class);
    }

    @Step("API: POST generate для global plan {planId}")
    public GenerationResponse generate(Long planId, DecompositionRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_GENERATE,
                UserRole.ADMIN,
                request,
                planId);
        validateSuccess(response, "Generate plans from global plan " + planId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_GENERATE);
        return response.as(GenerationResponse.class);
    }

    public DecompositionRequest buildCompleteDecomposition() {
        return GlobalPlanDataFactory.completeDecomposition(requireChain());
    }

    @Step("API: GET global plan by id {planId}")
    public GlobalPlanResponse getById(Long planId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_GET_BY_ID,
                UserRole.ADMIN,
                String.valueOf(planId));
        validateSuccess(response, "Get global plan " + planId);
        return response.as(GlobalPlanResponse.class);
    }

    @Step("API: DELETE global plan {planId}")
    public void deleteGlobalPlan(Long planId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_DELETE,
                UserRole.ADMIN,
                String.valueOf(planId));
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Step("API: DELETE per-location plan {planId}")
    public void deleteLocationPlan(Long planId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_DELETE,
                UserRole.ADMIN,
                String.valueOf(planId));
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Step("API: створити per-location план для заміни на місяць {month}/{year}")
    public PlanResponse createExistingLocationPlan(Long storageId, double amount, int month, int year) {
        GlobalPlanChainContext chain = requireChain();
        var request = com.erp.data.factories.plan.PlanDataFactory.createSimplePlan(
                storageId,
                chain.getResourceA().getId(),
                month,
                year,
                amount).build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);
        validateSuccess(response, "Create existing location plan");
        return response.as(PlanResponse.class);
    }

    @Step("API: GET plans for storage {storageId}")
    public List<PlanResponse> getLocationPlans(Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_GET_ALL,
                UserRole.ADMIN,
                String.valueOf(storageId));
        validateSuccess(response, "Get location plans for storage " + storageId);
        List<PlanResponse> plans = DatabaseIntegrityValidator.extractList(response, PlanResponse.class);
        return plans != null ? plans : new ArrayList<>();
    }

    @Step("Cleanup generated location plans")
    public void cleanupGeneratedPlans(List<Long> planIds) {
        if (planIds == null) {
            return;
        }
        for (Long planId : planIds) {
            if (planId != null) {
                try {
                    deleteLocationPlan(planId);
                } catch (AssertionError e) {
                    log.warn("Could not delete plan {}: {}", planId, e.getMessage());
                }
            }
        }
    }

    public GlobalPlanChainContext requireChain() {
        GlobalPlanChainContext chain = testContext.get(ContextKey.GLOBAL_PLAN_CHAIN);
        if (chain == null) {
            throw new IllegalStateException("GLOBAL_PLAN_CHAIN not prepared — call prepareDecompositionChain()");
        }
        return chain;
    }

    private TechnologicalMapResponse createMap(String name,
                                                 Set<Long> storageIds,
                                                 List<ResourceUsageRequest> input,
                                                 List<ResourceUsageRequest> output) {
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithStorages(name, input, output, storageIds)
                .build();
        return techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);
    }

    private static ResourceUsageRequest usage(ResourceResponse resource, double amount) {
        return new ResourceUsageRequest(resource.getId(), amount);
    }
}
