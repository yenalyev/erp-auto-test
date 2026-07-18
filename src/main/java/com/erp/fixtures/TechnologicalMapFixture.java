package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RequestBodyFactory;
import com.erp.data.factories.plan.PlanDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.models.query.TechnologicalMapListQuery;
import com.erp.models.request.PlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.StorageTechnologicalMapModeRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.request.UpdateNotesRequest;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ProductionProcessTagStatisticResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.StorageTechnologicalMapModeResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class TechnologicalMapFixture extends BaseFixture {

    private static final int REQUIRED_RESOURCES = 3;
    private static final int RESOURCE_PAGE_SIZE = 10;

    private final ResourceFixture resourceFixture;

    public TechnologicalMapFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.resourceFixture = new ResourceFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів техкарт")
    public void prepareContext() {
        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();
        ensureTechMapResources(REQUIRED_RESOURCES);
    }

    public Long getOwner1StorageId() {
        return ConfigProvider.getOwner1StorageId();
    }

    /**
     * Без GET /resources?size=9999 — беремо невелику сторінку або створюємо нестачу.
     */
    @Step("Ensure at least {count} resources for tech map tests")
    public void ensureTechMapResources(int count) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_GET_PAGE,
                UserRole.ADMIN,
                String.valueOf(Math.max(count, RESOURCE_PAGE_SIZE)));

        List<ResourceResponse> resources = new ArrayList<>(
                DatabaseIntegrityValidator.extractList(response, ResourceResponse.class));

        while (resources.size() < count) {
            Object body = RequestBodyFactory.generate(ApiEndpointDefinition.RESOURCE_CREATE, testContext);
            Response createResponse = apiExecutor.execute(
                    ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, body);
            validateSuccess(createResponse, "Create resource for tech map setup");
            resources.add(createResponse.as(ResourceResponse.class));
        }

        List<ResourceResponse> selected = resources.subList(0, count);
        testContext.set(ContextKey.SHARED_RESOURCE_ID, selected.getFirst().getId());
        testContext.set(ContextKey.SHARED_RESOURCE, selected.getFirst());
        testContext.set(ContextKey.SHARED_AVAILABLE_RESOURCES, new ArrayList<>(selected));
        log.info("Tech map resources ready: {}", selected.size());
    }

    @Step("ADMIN: встановити режим редагування техкарт для локації {storageId} → {mode}")
    public StorageTechnologicalMapModeResponse setMode(
            Long storageId,
            StorageTechnologicalMapMode mode) {
        StorageTechnologicalMapModeRequest request = StorageTechnologicalMapModeRequest.builder()
                .storageId(storageId)
                .mode(mode)
                .build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_MODE_UPDATE,
                UserRole.ADMIN,
                request);
        validateSuccess(response, "Update tech map mode for storage " + storageId);

        return response.as(StorageTechnologicalMapModeResponse.class);
    }

    @Step("Перевірити режим редагування техкарт для локації {storageId}")
    public void assertMode(Long storageId, UserRole role, StorageTechnologicalMapMode expectedMode) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_MODE_GET,
                role,
                String.valueOf(storageId));
        validateSuccess(response, "Get tech map mode for storage " + storageId);

        StorageTechnologicalMapModeResponse modeResponse = response.as(StorageTechnologicalMapModeResponse.class);
        assertThat(modeResponse.getStorageId()).isEqualTo(storageId);
        assertThat(modeResponse.getMode()).isEqualTo(expectedMode);
    }

    @Step("GET tech maps for storage {storageId} by name")
    public List<TechnologicalMapResponse> getTechMapsByName(Long storageId, UserRole role, String name) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_GET_BY_STORAGE_AND_NAME,
                role,
                null,
                String.valueOf(storageId),
                name);
        validateSuccess(response, "Get tech maps for storage " + storageId + " name=" + name);
        return DatabaseIntegrityValidator.extractList(response, TechnologicalMapResponse.class);
    }

    public long countTechMapsByName(Long storageId, UserRole role, String name) {
        return getTechMapsByName(storageId, role, name).size();
    }

    @Step("GET active tech maps for storage {storageId} by name")
    public List<TechnologicalMapResponse> getActiveTechMapsByName(Long storageId, UserRole role, String name) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_GET_ACTIVE_BY_STORAGE_AND_NAME,
                role,
                null,
                String.valueOf(storageId),
                name);
        validateSuccess(response, "Get active tech maps for storage " + storageId + " name=" + name);
        return DatabaseIntegrityValidator.extractList(response, TechnologicalMapResponse.class);
    }

    public long countActiveTechMapsByName(Long storageId, UserRole role, String name) {
        return getActiveTechMapsByName(storageId, role, name).size();
    }

    @Step("Створити техкарту для локації {storageId} (режим EDIT_ALLOWED)")
    public TechnologicalMapResponse createTechMapAs(UserRole role, Long storageId) {
        setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
        return createTechMapWithRequest(role, buildOwner1CreateRequest());
    }

    @Step("Створити техкарту з кастомним запитом")
    public TechnologicalMapResponse createTechMapWithRequest(UserRole role, TechnologicalMapRequest request) {
        if (request.getStorageIds() != null && !request.getStorageIds().isEmpty()) {
            for (Long storageId : request.getStorageIds()) {
                setMode(storageId, StorageTechnologicalMapMode.EDIT_ALLOWED);
            }
        } else {
            setMode(getOwner1StorageId(), StorageTechnologicalMapMode.EDIT_ALLOWED);
        }

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_CREATE,
                role,
                request);
        validateSuccess(response, "Create tech map");

        return response.as(TechnologicalMapResponse.class);
    }

    @Step("Перевірити відмову через закритий режим редагування для локації {storageId}")
    public void assertEditForbidden(Response response, Long storageId) {
        assertThat(response.statusCode()).isEqualTo(400);
        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage)
                .as("Повідомлення про заборону редагування")
                .contains("закрито")
                .contains(String.valueOf(storageId));
    }

    @Step("Перевірити відмову: техкарта використовується в актуальному плані")
    public void assertUsedInPlanRejection(Response response) {
        assertThat(response.statusCode()).isEqualTo(400);
        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage)
                .as("Повідомлення про використання техкарти в плані")
                .contains("плані");
    }

    @Step("Перевірити відмову: техкарта використовується в глобальному плані")
    public void assertUsedInGlobalPlanRejection(Response response, String expectedPlanDescription) {
        assertThat(response.statusCode()).isEqualTo(400);
        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage)
                .as("Повідомлення про використання техкарти в глобальному плані")
                .contains("глобальному плані")
                .contains(expectedPlanDescription);
    }

    @Step("Перевірити відмову: ресурс одночасно у input і output")
    public void assertInputOutputOverlapRejection(Response response, String expectedResourceName) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("errors[0].field"))
                .as("Поле помилки валідації")
                .isEqualTo("output");
        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage)
                .as("Повідомлення про перетин input/output")
                .contains("не може бути одночасно вхідним і вихідним")
                .contains(expectedResourceName);
    }

    @Step("{role}: DELETE deactivate tech map {techMapId} at storage {storageId}")
    public Response deactivateTechMap(UserRole role, Long techMapId, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_DEACTIVATE,
                role,
                null,
                String.valueOf(techMapId),
                String.valueOf(storageId));
    }

    @Step("API: DELETE per-location plan {planId}")
    public void deleteLocationPlan(Long planId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_DELETE,
                UserRole.ADMIN,
                String.valueOf(planId));
        assertThat(response.statusCode()).isBetween(200, 299);
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

    @Step("Знайти вільний місяць для плану локації {storageId} починаючи з {start}")
    public YearMonth nextFreeLocationPlanPeriod(Long storageId, YearMonth start) {
        return nextFreeLocationPlanPeriod(storageId, start, true);
    }

    @Step("Знайти вільний місяць для плану локації {storageId} починаючи з {start} (backward={searchBackward})")
    public YearMonth nextFreeLocationPlanPeriod(Long storageId, YearMonth start, boolean searchBackward) {
        Set<String> occupied = new HashSet<>();
        for (PlanResponse plan : getLocationPlans(storageId)) {
            occupied.add(plan.getYear() + "-" + plan.getMonth());
        }
        YearMonth candidate = start;
        int attempts = 0;
        while (occupied.contains(candidate.getYear() + "-" + candidate.getMonthValue())) {
            candidate = searchBackward ? candidate.minusMonths(1) : candidate.plusMonths(1);
            attempts++;
            if (attempts > 120) {
                throw new IllegalStateException("No free location plan period found within 10 years for storage " + storageId);
            }
        }
        return candidate;
    }

    @Step("API: створити per-location план для ресурсу {resourceId} на {period}")
    public PlanResponse createLocationPlan(Long storageId, Long resourceId, YearMonth period, double amount) {
        PlanRequest request = PlanDataFactory.createSimplePlan(
                storageId,
                resourceId,
                period.getMonthValue(),
                period.getYear(),
                amount).build();

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_POST_CREATE,
                UserRole.ADMIN,
                request);
        validateSuccess(response, "Create location plan for resource " + resourceId);
        return response.as(PlanResponse.class);
    }

    @Step("API: створити актуальний per-location план для ресурсу {resourceId}")
    public PlanResponse createActiveLocationPlan(Long storageId, Long resourceId, YearMonth startHint, double amount) {
        YearMonth minimum = YearMonth.now().plusMonths(1);
        YearMonth period = nextFreeLocationPlanPeriod(
                storageId,
                startHint.isBefore(minimum) ? minimum : startHint);
        return createLocationPlan(storageId, resourceId, period, amount);
    }

    @Value
    @Builder
    public static class IsolatedTechMapContext {
        ResourceResponse product;
        TechnologicalMapResponse techMap;
    }

    @Step("API: PATCH notes для техкарти {techMapId} на локації {storageId}")
    public TechnologicalMapResponse updateNotes(UserRole role, Long techMapId, Long storageId, String notes) {
        UpdateNotesRequest request = UpdateNotesRequest.builder().notes(notes).build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_PATCH_NOTES,
                role,
                request,
                techMapId,
                storageId);
        validateSuccess(response, "Patch tech map notes id=" + techMapId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.TECH_MAP_PATCH_NOTES);
        return response.as(TechnologicalMapResponse.class);
    }

    @Step("API: GET technological-maps з query-фільтрами")
    public List<TechnologicalMapResponse> listByQuery(TechnologicalMapListQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.TECH_MAP_GET_ALL,
                UserRole.OWNER_1,
                query.toQueryParams());
        validateSuccess(response, "List technological maps");
        return DatabaseIntegrityValidator.extractList(response, TechnologicalMapResponse.class);
    }

    @Step("API: GET tag-statistics для tech maps")
    public List<ProductionProcessTagStatisticResponse> getTagStatistics(TechnologicalMapListQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.TECH_MAP_TAG_STATISTICS_GET,
                UserRole.OWNER_1,
                query.toQueryParams());
        validateSuccess(response, "Get technological map tag statistics");
        return DatabaseIntegrityValidator.extractList(response, ProductionProcessTagStatisticResponse.class);
    }

    @Step("API: GET каталог technological-map-tags для storageId={storageId}")
    public Collection<String> getTechnologicalMapTags(long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.APP_CONFIG_TECHNOLOGICAL_MAP_TAGS_GET,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        validateSuccess(response, "Get technological map tags catalog");
        List<String> tags = response.jsonPath().getList("$", String.class);
        return tags != null ? tags : List.of();
    }

    @Step("API: GET tech map {techMapId} для локації {storageId}")
    public TechnologicalMapResponse getById(UserRole role, Long techMapId, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_GET_BY_ID,
                role,
                null,
                techMapId,
                storageId);
        validateSuccess(response, "Get tech map id=" + techMapId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.TECH_MAP_GET_BY_ID);
        return response.as(TechnologicalMapResponse.class);
    }

    @Step("Отримати notes техкарти {techMapId} для локації {storageId}")
    public Optional<String> getStorageNotes(UserRole role, Long techMapId, Long storageId) {
        TechnologicalMapResponse techMap = getById(role, techMapId, storageId);
        if (techMap.getStorages() == null) {
            return Optional.empty();
        }
        return techMap.getStorages().stream()
                .filter(storage -> storageId.equals(storage.getId()))
                .findFirst()
                .map(SimpleEntityResponse::getNotes)
                .filter(notes -> notes != null && !notes.isBlank());
    }

    @Step("Забезпечити запас сировини для ізольованої техкарти на складі {storageId}")
    public void seedStockForIsolatedTechMap(ProductionFixture productionFixture,
                                            Long storageId,
                                            TechnologicalMapResponse techMap,
                                            double minimum) {
        List<Long> inputIds = techMap.getInput().stream()
                .map(usage -> usage.getResource().getId())
                .toList();
        if (inputIds.size() < 2) {
            throw new IllegalStateException("Isolated tech map must have at least 2 inputs");
        }
        productionFixture.ensureInputStockAtLeast(storageId, inputIds.get(0), inputIds.get(1), minimum);
    }

    @Step("Створити ізольовану production техкарту для локації {storageId}")
    public IsolatedTechMapContext createIsolatedProductionTechMap(UserRole role, Long storageId) {
        String suffix = String.valueOf(System.currentTimeMillis());
        ResourceResponse in1 = resourceFixture.createUniqueResource("TM-IN1-" + suffix);
        ResourceResponse in2 = resourceFixture.createUniqueResource("TM-IN2-" + suffix);
        ResourceResponse product = resourceFixture.createUniqueResource("TM-OUT-" + suffix);

        TechnologicalMapRequest request = TechnologicalMapDataFactory.createProductionMapWithStorages(
                "TM-PlanGuard",
                List.of(
                        new ResourceUsageRequest(in1.getId(), 2.0),
                        new ResourceUsageRequest(in2.getId(), 1.0)),
                List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                Set.of(storageId)).build();

        TechnologicalMapResponse techMap = createTechMapWithRequest(role, request);
        return IsolatedTechMapContext.builder()
                .product(product)
                .techMap(techMap)
                .build();
    }

    @Step("Створити другу активну техкарту на той самий продукт")
    public TechnologicalMapResponse createAlternateActiveTechMap(UserRole role, TechnologicalMapResponse source) {
        TechnologicalMapRequest cloneRequest = TechnologicalMapDataFactory.cloneFrom(source);
        return createTechMapWithRequest(role, cloneRequest);
    }

    public Long getOutputResourceId(TechnologicalMapResponse techMap) {
        return techMap.getOutput().getFirst().getResource().getId();
    }

    @Step("Побудувати запит на створення техкарти для локації Owner1")
    public TechnologicalMapRequest buildOwner1CreateRequest() {
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        if (resources == null || resources.size() < REQUIRED_RESOURCES) {
            throw new IllegalStateException("SHARED_AVAILABLE_RESOURCES missing or too small");
        }
        return TechnologicalMapDataFactory
                .createProductionTechMap(resources, getOwner1StorageId())
                .build();
    }

    @Step("Створити 4 унікальні ресурси для техкарти з альтернативною групою")
    public List<ResourceResponse> createAltGroupResources() {
        String suffix = String.valueOf(System.currentTimeMillis());
        return List.of(
                resourceFixture.createUniqueResource("ALT-FIXED-" + suffix),
                resourceFixture.createUniqueResource("ALT-DEF-" + suffix),
                resourceFixture.createUniqueResource("ALT-OTHER-" + suffix),
                resourceFixture.createUniqueResource("ALT-OUT-" + suffix));
    }

    @Step("Створити 6 унікальних ресурсів для техкарти з двома альтернативними групами")
    public List<ResourceResponse> createTwoGroupAltResources() {
        String suffix = String.valueOf(System.currentTimeMillis());
        return List.of(
                resourceFixture.createUniqueResource("ALT2-FIXED-" + suffix),
                resourceFixture.createUniqueResource("ALT2-GLUE-DEF-" + suffix),
                resourceFixture.createUniqueResource("ALT2-GLUE-ALT-" + suffix),
                resourceFixture.createUniqueResource("ALT2-FUEL-DEF-" + suffix),
                resourceFixture.createUniqueResource("ALT2-FUEL-ALT-" + suffix),
                resourceFixture.createUniqueResource("ALT2-OUT-" + suffix));
    }

    @Step("GET versions for tech map group {groupId} at storage {storageId}")
    public List<TechnologicalMapResponse> getVersionsByGroupId(UserRole role, String groupId, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.TECH_MAP_GET_VERSIONS,
                role,
                null,
                groupId,
                String.valueOf(storageId));
        validateSuccess(response, "Get tech map versions for groupId=" + groupId);
        return DatabaseIntegrityValidator.extractList(response, TechnologicalMapResponse.class);
    }

    @Step("Створити PRODUCTION техкарту з альтернативною групою на локації {storageId}")
    public TechnologicalMapResponse createTechMapWithAlternativeGroup(UserRole role, Long storageId) {
        List<ResourceResponse> resources = createAltGroupResources();
        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithAlternativeGroup(resources, storageId);
        return createTechMapWithRequest(role, request);
    }

    @Step("Перевірити відмову: у групі має бути рівно один default")
    public void assertGroupDefaultRequiredRejection(Response response) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("errors[0].field"))
                .as("Поле помилки валідації default у групі")
                .contains("default");
        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage)
                .as("Повідомлення про обов'язковий default у групі")
                .contains("рівно один ресурс за замовчуванням");
    }

    @Step("Перевірити відмову валідації альтернативної групи (поле містить {expectedFieldFragment})")
    public void assertGroupValidationRejection(Response response, String expectedFieldFragment, String expectedMessageFragment) {
        assertThat(response.statusCode()).isEqualTo(400);
        String field = response.jsonPath().getString("errors[0].field");
        assertThat(field)
                .as("Поле помилки валідації групи")
                .contains(expectedFieldFragment);
        String errorMessage = response.jsonPath().getString("errors[0].messages[0]");
        assertThat(errorMessage)
                .as("Повідомлення валідації групи")
                .contains(expectedMessageFragment);
    }
}
