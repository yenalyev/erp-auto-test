package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.models.query.NonSeriesProductionQuery;
import com.erp.models.request.*;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Owned locations, resources and operations; no existing inventory is consumed. */
public class NonSeriesResourceFilterFixture extends BaseFixture {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NonSeriesResourceFilterFixture.class);
    private final StorageFixture storages;
    private final ResourceFixture resources;
    private final List<ResourceResponse> ownedResources = new ArrayList<>();
    private final List<NonSeriesProductionResponse> productions = new ArrayList<>();
    private final Map<Long, Long> receives = new LinkedHashMap<>();
    public final String marker = "nspfilter-" + UUID.randomUUID().toString().substring(0, 8);
    public final String materialTerm = "Мідь-" + marker;
    public final LocalDate today = LocalDate.now();
    public Long storageId;
    public Long otherStorageId;
    public ResourceResponse materialA;
    public ResourceResponse materialA2;
    public ResourceResponse materialB;
    public NonSeriesProductionResponse alpha;
    public NonSeriesProductionResponse both;
    public NonSeriesProductionResponse decoy;
    public NonSeriesProductionResponse noResources;
    public NonSeriesProductionResponse older;
    public NonSeriesProductionResponse elsewhere;

    public NonSeriesResourceFilterFixture(TestContext context, ApiExecutor executor) {
        super(context, executor);
        storages = new StorageFixture(context, executor);
        resources = new ResourceFixture(context, executor);
    }

    @Step("Підготувати ізольовані записи для фільтра використаної сировини")
    public void prepare() {
        fetchSharedUnit(1);
        fetchSharedResourceCategory();
        Long parentId = storages.resolveParentUnit().getId();
        storageId = storages.createProductionStorage(parentId, "nsp-filter-").getId();
        otherStorageId = storages.createProductionStorage(parentId, "nsp-filter-other-").getId();
        materialA = resource(materialTerm + " дріт-");
        materialA2 = resource(materialTerm + " лист-");
        materialB = resource("Сталь-" + marker + "-");
        seed(storageId);
        seed(otherStorageId);
        alpha = create(storageId, "Альфа " + marker, 2, NonSeriesProductionStatus.IN_PROGRESS,
                today, usage(materialA));
        both = create(storageId, "Бета " + marker, 3, NonSeriesProductionStatus.DONE,
                today, usage(materialA), usage(materialA2), usage(materialB));
        // Resource term occurs in product/description, but not in actual usage.
        decoy = create(storageId, materialTerm + " виріб", 5, NonSeriesProductionStatus.IN_PROGRESS,
                today, usage(materialB));
        noResources = create(storageId, "Без сировини " + marker, 7, NonSeriesProductionStatus.IN_PROGRESS,
                today);
        older = create(storageId, "Раніше " + marker, 11, NonSeriesProductionStatus.IN_PROGRESS,
                today.minusDays(10), usage(materialA));
        elsewhere = create(otherStorageId, "Інша локація " + marker, 13,
                NonSeriesProductionStatus.IN_PROGRESS, today, usage(materialA));
    }

    private ResourceResponse resource(String prefix) {
        ResourceResponse result = resources.createUniqueResource(prefix);
        ownedResources.add(result);
        return result;
    }

    private NonSeriesProductionResourceUsageRequest usage(ResourceResponse resource) {
        return NonSeriesProductionDataFactory.usage(resource.getId(), 1);
    }

    private void seed(Long location) {
        var items = ownedResources.stream().map(resource -> ResourceUsageRequest.builder()
                .resourceId(resource.getId()).amount(BigDecimal.valueOf(100))
                .batches(List.of(RelocationItemBatchRequest.builder().batchNumber(marker + "-" + resource.getId())
                        .amount(BigDecimal.valueOf(100)).isProduced(false).build())).build()).toList();
        var request = RelocationDataFactory.buildReceiveRequest(
                RelocationStockSeeder.resolveSupplierStorageId(apiExecutor, UserRole.ADMIN),
                location, items, marker);
        Response response = apiExecutor.executeRelocationReceive(request, UserRole.ADMIN);
        validateSuccess(response, "Seed isolated raw materials");
        receives.put(response.jsonPath().getLong("id"), location);
    }

    public NonSeriesProductionResponse create(Long location, String product, double amount,
            NonSeriesProductionStatus status, LocalDate date,
            NonSeriesProductionResourceUsageRequest... usages) {
        var request = NonSeriesProductionDataFactory.buildCreateRequest(location, status, product,
                        amount, List.of(usages)).toBuilder().start(date).end(today).description(materialTerm).build();
        Response response = apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE,
                UserRole.ADMIN, request);
        validateSuccess(response, "Create filter fixture record");
        var result = response.as(NonSeriesProductionResponse.class);
        productions.add(result);
        return result;
    }

    public NonSeriesProductionQuery query(String term) {
        return NonSeriesProductionQuery.builder().storageId(storageId).resourceSearch(term).build();
    }

    public Response listResponse(NonSeriesProductionQuery query) {
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_ALL,
                UserRole.ADMIN, query.toListQueryParams());
        validateSuccess(response, "List by raw material");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_ALL);
        return response;
    }

    public List<NonSeriesProductionResponse> list(NonSeriesProductionQuery query) {
        return DatabaseIntegrityValidator.extractList(listResponse(query), NonSeriesProductionResponse.class);
    }

    public BigDecimal total(NonSeriesProductionQuery query) {
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_TOTAL,
                UserRole.ADMIN, query.toTotalQueryParams());
        validateSuccess(response, "Total by raw material");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_TOTAL);
        return response.as(NonSeriesProductionTotalResponse.class).getTotalAmount();
    }

    public Map<Long, Double> inventorySnapshot(Long location) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.ADMIN, location.toString());
        validateSuccess(response, "Read owned location inventory as ADMIN");
        Map<Long, Double> result = new LinkedHashMap<>();
        for (var item : DatabaseIntegrityValidator.extractList(response, StorageItemResponse.class)) {
            result.put(item.getResource().getId(), item.getAmount());
        }
        return result;
    }

    public void expect(NonSeriesProductionQuery query, int amount, NonSeriesProductionResponse... expected) {
        assertThat(list(query)).extracting(NonSeriesProductionResponse::getId)
                .containsExactlyInAnyOrder(Arrays.stream(expected).map(NonSeriesProductionResponse::getId).toArray(Long[]::new));
        assertThat(total(query)).isEqualByComparingTo(BigDecimal.valueOf(amount));
    }

    public List<String> matchingProducts() { return List.of(alpha.getProduct(), both.getProduct(), older.getProduct()); }
    public List<String> allProducts() { return List.of(alpha.getProduct(), both.getProduct(), decoy.getProduct(),
            noResources.getProduct(), older.getProduct()); }

    @Step("Прибрати лише дані фікстури фільтра сировини")
    public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        for (var production : productions.reversed()) {
            try {
                validateSuccess(apiExecutor.execute(ApiEndpointDefinition.NON_SERIES_PRODUCTION_DELETE,
                        UserRole.ADMIN, null, production.getId(), production.getStorage().getId()), "Delete owned production");
            } catch (Exception | AssertionError e) { errors.add(e); }
        }
        for (var receive : receives.entrySet()) {
            try { new RelocationFixture(testContext, apiExecutor)
                    .deleteRelocation(UserRole.ADMIN, receive.getKey(), receive.getValue()); }
            catch (Exception | AssertionError e) { errors.add(e); }
        }
        for (Long location : Arrays.asList(storageId, otherStorageId)) {
            if (location == null) continue;
            try {
                assertThat(inventorySnapshot(location).values()).as("No stock remains after deleting owned receives")
                        .allSatisfy(amount -> assertThat(amount).isZero());
            } catch (Exception | AssertionError e) { errors.add(e); }
        }
        try { storages.deactivateTrackedStorages(UserRole.ADMIN); }
        catch (Exception | AssertionError e) { errors.add(e); }
        for (var resource : ownedResources) {
            try {
                Response response = resources.deactivate(UserRole.ADMIN, resource.getId());
                // ResourceValidator rejects even zero-valued storage_item rows; no API removes them.
                if (response.statusCode() == 400 && response.asString().contains("знаходиться на складі")) {
                    log.warn("Resource {} retained: ERP blocks deactivation of zero-stock rows on archived test locations",
                            resource.getId());
                } else {
                    validateSuccess(response, "Deactivate owned resource");
                }
            } catch (Exception | AssertionError e) { errors.add(e); }
        }
        if (!errors.isEmpty()) {
            var failure = new IllegalStateException("Raw material filter cleanup failed");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
