package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.global_plan.GlobalPlanDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.common.GlobalPlanAltGroupContext;
import com.erp.models.common.GlobalPlanAltGroupExpectations;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapAlternativeGroupRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.DecompositionResponse;
import com.erp.models.response.GenerationResponse;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseHelper;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
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

        // Single output per PRODUCTION map (product rule / UI constraint).
        // M1: 2B + 3x -> 1A @L1
        TechnologicalMapResponse m1 = createMap("GP-M1", Set.of(l1),
                List.of(usage(b, 2), usage(x, 3)),
                List.of(usage(a, 1)));

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

    @Step("FIXTURE: Підготовка техкарти з альтернативною групою для глобального плану")
    public GlobalPlanAltGroupContext prepareAltGroupChain() {
        GlobalPlanAltGroupContext existing = testContext.get(ContextKey.GLOBAL_PLAN_ALT_CHAIN);
        if (existing != null) {
            return existing;
        }

        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();

        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse p = resourceFixture.createUniqueResource("GP-ALT-P-" + suffix);
        ResourceResponse d = resourceFixture.createUniqueResource("GP-ALT-D-" + suffix);
        ResourceResponse e = resourceFixture.createUniqueResource("GP-ALT-E-" + suffix);
        ResourceResponse f = resourceFixture.createUniqueResource("GP-ALT-F-" + suffix);

        Long l1 = ConfigProvider.getOwner1StorageId();

        TechnologicalMapAlternativeGroupRequest group = TechnologicalMapDataFactory.alternativeGroup(
                "Клей",
                TechnologicalMapDataFactory.alternativeResource(
                        d.getId(), GlobalPlanAltGroupExpectations.DEFAULT_ALT_AMOUNT, true),
                TechnologicalMapDataFactory.alternativeResource(
                        e.getId(), GlobalPlanAltGroupExpectations.OTHER_ALT_AMOUNT, false));

        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithStorages(
                        "GP-ALT-M",
                        List.of(usage(f, GlobalPlanAltGroupExpectations.FIXED_AMOUNT)),
                        List.of(usage(p, 1.0)),
                        Set.of(l1))
                .groups(List.of(group))
                .build();

        TechnologicalMapResponse mapProduct = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);

        if (periodBase == null) {
            periodBase = GlobalPlanDataFactory.uniquePlanPeriod(3);
            periodOffset = 0;
        }

        GlobalPlanAltGroupContext chain = GlobalPlanAltGroupContext.builder()
                .l1StorageId(l1)
                .resourceP(p)
                .resourceD(d)
                .resourceE(e)
                .resourceF(f)
                .mapProduct(mapProduct)
                .build();

        testContext.set(ContextKey.GLOBAL_PLAN_ALT_CHAIN, chain);
        log.info("Global plan alt-group chain ready: map={}, product={}", mapProduct.getId(), p.getId());
        return chain;
    }

    public GlobalPlanAltGroupContext requireAltChain() {
        GlobalPlanAltGroupContext chain = testContext.get(ContextKey.GLOBAL_PLAN_ALT_CHAIN);
        if (chain == null) {
            throw new IllegalStateException("GLOBAL_PLAN_ALT_CHAIN not prepared — call prepareAltGroupChain()");
        }
        return chain;
    }

    @Step("API: створити глобальний план з output P={amount} (alt-group chain)")
    public GlobalPlanResponse createAltGroupGlobalPlan(double amount) {
        GlobalPlanAltGroupContext chain = requireAltChain();
        YearMonth period = nextUniquePeriod();
        GlobalPlanRequest request = GlobalPlanDataFactory.createPlan(
                period.getMonthValue(),
                period.getYear(),
                chain.getResourceP().getId(),
                amount).build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);
        validateSuccess(response, "Create alt-group global plan");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);

        GlobalPlanResponse created = response.as(GlobalPlanResponse.class);
        testContext.set(ContextKey.GLOBAL_PLAN_ID, created.getId());
        testContext.set(ContextKey.GLOBAL_PLAN, created);
        return created;
    }

    public DecompositionRequest buildAltGroupDecomposition(double amount) {
        return GlobalPlanDataFactory.altGroupDecomposition(requireAltChain(), amount);
    }

    public TechnologicalMapFixture getTechMapFixture() {
        return techMapFixture;
    }

    public ResourceFixture getResourceFixture() {
        return resourceFixture;
    }

    /** Allocates a calendar month not used by an existing global plan (API + shared dev env). */
    public YearMonth nextUniquePeriod() {
        if (periodBase == null) {
            periodBase = GlobalPlanDataFactory.uniquePlanPeriod(3);
            periodOffset = 0;
        }
        Set<String> occupied = new HashSet<>();
        for (GlobalPlanResponse plan : getAllGlobalPlans()) {
            occupied.add(plan.getYear() + "-" + plan.getMonth());
        }
        YearMonth candidate;
        int attempts = 0;
        do {
            candidate = periodBase.plusMonths(periodOffset++);
            attempts++;
            if (attempts > 120) {
                throw new IllegalStateException("No free global plan period found within 10 years");
            }
        } while (occupied.contains(candidate.getYear() + "-" + candidate.getMonthValue()));
        log.info("Allocated unique global plan period {}/{}", candidate.getMonthValue(), candidate.getYear());
        return candidate;
    }

    /**
     * Free calendar month strictly before the current month (GP unique + optional location plans).
     * Used to backdate a generated plan so the snapshot becomes «неактуальний».
     */
    public YearMonth nextUniquePastPeriod(Long locationStorageId) {
        Set<String> occupied = new HashSet<>();
        for (GlobalPlanResponse plan : getAllGlobalPlans()) {
            occupied.add(plan.getYear() + "-" + plan.getMonth());
        }
        if (locationStorageId != null) {
            for (PlanResponse plan : getLocationPlans(locationStorageId)) {
                occupied.add(plan.getYear() + "-" + plan.getMonth());
            }
        }
        YearMonth candidate = YearMonth.now().minusMonths(1);
        int attempts = 0;
        while (occupied.contains(candidate.getYear() + "-" + candidate.getMonthValue())) {
            candidate = candidate.minusMonths(1);
            attempts++;
            if (attempts > 120) {
                throw new IllegalStateException("No free past global plan period found within 10 years");
            }
        }
        log.info("Allocated unique past global plan period {}/{}", candidate.getMonthValue(), candidate.getYear());
        return candidate;
    }

    /**
     * Generate a live GP (API), then JDBC-backdate it and its location plans to a free past month.
     * Snapshot stays; the plan becomes historical so archive of maps in the snapshot is allowed.
     */
    @Step("Seed: generated GP + backdate to past month (historical snapshot)")
    public HistoricalPlanSeed seedHistoricalGeneratedPlan(
            DatabaseHelper db,
            Long outputResourceId,
            Long storageId,
            Long techMapId,
            double amount) {
        if (db == null || db.getConnection() == null) {
            throw new IllegalStateException("JDBC is required to backdate a generated global plan");
        }
        YearMonth pastPeriod = nextUniquePastPeriod(storageId);
        YearMonth livePeriod = nextUniquePeriod();

        GlobalPlanRequest gpRequest = GlobalPlanDataFactory.createPlan(
                livePeriod.getMonthValue(), livePeriod.getYear(), outputResourceId, amount).build();
        Response createResponse = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE,
                UserRole.ADMIN,
                gpRequest);
        validateSuccess(createResponse, "Create live GP before backdate");
        SchemaRegistry.validateIfSuccess(createResponse, ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE);
        GlobalPlanResponse created = createResponse.as(GlobalPlanResponse.class);

        DecompositionRequest decomposition = DecompositionRequest.builder()
                .blocks(List.of(GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(
                        outputResourceId,
                        GlobalPlanDataFactory.assignment(storageId, techMapId, String.valueOf((int) amount))))))
                .build();
        decompose(created.getId(), decomposition);
        GenerationResponse generation = generate(created.getId(), decomposition);
        List<Long> locationPlanIds = generation.getPlans().stream()
                .map(gp -> gp.getPlan().getId())
                .toList();

        backdateGeneratedPlan(db, created.getId(), locationPlanIds, pastPeriod);

        GlobalPlanResponse pastPlan = getById(created.getId());
        assertThat(pastPlan.getMonth()).isEqualTo(pastPeriod.getMonthValue());
        assertThat(pastPlan.getYear()).isEqualTo(pastPeriod.getYear());
        assertThat(pastPlan.getDecomposition()).isNotNull();
        assertThat(pastPlan.getTo()).isBefore(LocalDate.now().withDayOfMonth(1));
        return new HistoricalPlanSeed(pastPlan, pastPeriod, locationPlanIds);
    }

    @Step("DB: backdate GP {globalPlanId} and location plans to {pastPeriod}")
    public void backdateGeneratedPlan(
            DatabaseHelper db,
            Long globalPlanId,
            List<Long> locationPlanIds,
            YearMonth pastPeriod) {
        LocalDate from = pastPeriod.atDay(1);
        LocalDate to = pastPeriod.atEndOfMonth();
        try {
            try (PreparedStatement gp = db.getConnection().prepareStatement(
                    "UPDATE global_plan SET from_date = ?, to_date = ? WHERE id = ?")) {
                gp.setDate(1, Date.valueOf(from));
                gp.setDate(2, Date.valueOf(to));
                gp.setLong(3, globalPlanId);
                int updated = gp.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException(
                            "Expected 1 global_plan row backdated, got " + updated + " for id=" + globalPlanId);
                }
            }
            if (locationPlanIds != null) {
                for (Long locationPlanId : locationPlanIds) {
                    if (locationPlanId == null) {
                        continue;
                    }
                    try (PreparedStatement plan = db.getConnection().prepareStatement(
                            "UPDATE plan SET from_date = ?, to_date = ? WHERE id = ?")) {
                        plan.setDate(1, Date.valueOf(from));
                        plan.setDate(2, Date.valueOf(to));
                        plan.setLong(3, locationPlanId);
                        plan.executeUpdate();
                    }
                    try (PreparedStatement output = db.getConnection().prepareStatement(
                            "UPDATE plan_output SET from_date = ?, to_date = ? WHERE plan_id = ?")) {
                        output.setDate(1, Date.valueOf(from));
                        output.setDate(2, Date.valueOf(to));
                        output.setLong(3, locationPlanId);
                        output.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to backdate global plan " + globalPlanId + " to " + pastPeriod, e);
        }
        log.info("Backdated global plan {} and {} location plans to {}/{}",
                globalPlanId, locationPlanIds == null ? 0 : locationPlanIds.size(),
                pastPeriod.getMonthValue(), pastPeriod.getYear());
    }

    public record HistoricalPlanSeed(
            GlobalPlanResponse pastPlan,
            YearMonth pastPeriod,
            List<Long> locationPlanIds
    ) {}

    public static List<Long> snapshotTechMapIds(GlobalPlanResponse plan) {
        if (plan.getDecomposition() == null || plan.getDecomposition().getBlocks() == null) {
            return List.of();
        }
        return plan.getDecomposition().getBlocks().stream()
                .flatMap(block -> block.getItems().stream())
                .flatMap(item -> item.getAssignments().stream())
                .map(DecompositionRequest.DecompositionAssignmentRequest::getTechnologicalMapId)
                .filter(id -> id != null)
                .toList();
    }

    @Step("API: GET all global plans")
    public List<GlobalPlanResponse> getAllGlobalPlans() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.GLOBAL_PLAN_GET_ALL,
                UserRole.ADMIN);
        validateSuccess(response, "Get all global plans");
        List<GlobalPlanResponse> plans = DatabaseIntegrityValidator.extractList(response, GlobalPlanResponse.class);
        return plans != null ? plans : new ArrayList<>();
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

    @Step("Resolve storage name for id {storageId}")
    public String resolveStorageName(Long storageId) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, UserRole.ADMIN);
        validateSuccess(response, "Get storages for name lookup");
        List<StorageResponse> storages = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        return storages.stream()
                .filter(s -> storageId.equals(s.getId()))
                .map(StorageResponse::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Storage not found: " + storageId));
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
