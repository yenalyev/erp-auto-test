package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.non_series_production.NonSeriesProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.models.request.NonSeriesProductionRequest;
import com.erp.models.request.NonSeriesProductionResourceUsageRequest;
import com.erp.models.query.NonSeriesProductionQuery;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.models.response.NonSeriesProductionTotalResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class NonSeriesProductionFixture extends BaseFixture {

    public static final double DEFAULT_SEEDED_STOCK = 50.0;

    public NonSeriesProductionFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів несерійного виробництва")
    public void prepareContext() {
        if (testContext.get(ContextKey.NON_SERIES_RESOURCE_ID) != null) {
            return;
        }

        Long storageId = ConfigProvider.getOwner1StorageId();
        ResourceResponse resource = resolveResourceForStorage(storageId);

        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(resource.getId(), DEFAULT_SEEDED_STOCK));

        double stockAfterSeed = getResourceStock(storageId, resource.getId());

        testContext.set(ContextKey.NON_SERIES_RESOURCE_ID, resource.getId());
        testContext.set(ContextKey.NON_SERIES_RESOURCE_NAME, resource.getName());
        testContext.set(ContextKey.NON_SERIES_SEEDED_STOCK, stockAfterSeed);

        log.info("Non-series production fixture ready: storage={}, resource={} ({}), stock={}",
                storageId, resource.getId(), resource.getName(), stockAfterSeed);
    }

    /**
     * Picks a resource already present on the target storage inventory (fast path for UI tests).
     * Falls back to creating a single resource without loading the full resources catalog.
     */
    @Step("FIXTURE: Resolve resource for storage {storageId}")
    private ResourceResponse resolveResourceForStorage(Long storageId) {
        Response invResponse = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(
                invResponse, StorageItemResponse.class);

        for (StorageItemResponse item : items) {
            if (item.getResource() != null && item.getResource().getId() != null) {
                log.info("Using resource from storage inventory: id={}, name={}",
                        item.getResource().getId(), item.getResource().getName());
                return item.getResource();
            }
        }

        log.info("Storage {} has no inventory items — creating a single resource", storageId);
        fetchSharedUnit(1);
        fetchSharedResourceCategory();
        setupSharedResource();
        ResourceResponse created = testContext.get(ContextKey.SHARED_RESOURCE);
        if (created == null) {
            throw new IllegalStateException("Failed to create resource for non-series production fixture");
        }
        return created;
    }

    @Step("FIXTURE: Ensure stock ≥ {minimum} for resource {resourceId} on storage {storageId}")
    public double ensureStockAtLeast(Long storageId, Long resourceId, double minimum) {
        double current = getResourceStock(storageId, resourceId);
        if (current >= minimum) {
            return current;
        }
        double toAdd = minimum - current;
        RelocationStockSeeder.receiveFromSupplier(
                apiExecutor,
                UserRole.OWNER_1,
                storageId,
                Map.of(resourceId, toAdd));
        double after = getResourceStock(storageId, resourceId);
        log.info("Topped up stock for resource {}: {} → {}", resourceId, current, after);
        return after;
    }

    @Step("API: GET non-series production /{id}?storageId={storageId}")
    public NonSeriesProductionResponse getById(Long id, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_BY_ID,
                UserRole.OWNER_1,
                null,
                String.valueOf(id),
                String.valueOf(storageId));
        validateSuccess(response, "Get non-series production by id");

        try {
            return response.as(NonSeriesProductionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse non-series production GET response: " + response.getBody().asString(), e);
        }
    }

    @Step("API: Створити несерійне виробництво (status={status})")
    public NonSeriesProductionResponse createAs(UserRole role,
                                                NonSeriesProductionStatus status,
                                                String product,
                                                double productAmount,
                                                Long resourceId,
                                                double resourceAmountPerUnit) {
        Long storageId = ConfigProvider.getOwner1StorageId();
        NonSeriesProductionRequest request = NonSeriesProductionDataFactory.buildCreateRequest(
                storageId,
                status,
                product,
                productAmount,
                List.of(NonSeriesProductionDataFactory.usage(resourceId, resourceAmountPerUnit)));

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_POST_CREATE, role, request);
        validateSuccess(response, "Create non-series production");

        try {
            return response.as(NonSeriesProductionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse non-series production response: " + response.getBody().asString(), e);
        }
    }

    @Step("API: Видалити несерійне виробництво /{id}?storageId={storageId}")
    public void deleteAs(UserRole role, Long id, Long storageId) {
        Response response = deleteRaw(role, id, storageId);
        validateSuccess(response, "Delete non-series production");
    }

    @Step("API: DELETE non-series production (raw response)")
    public Response deleteRaw(UserRole role, Long id, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_DELETE,
                role,
                null,
                String.valueOf(id),
                String.valueOf(storageId));
    }

    @Step("API: Оновити несерійне виробництво /{id}")
    public NonSeriesProductionResponse updateAs(UserRole role, Long id, NonSeriesProductionRequest request) {
        Response response = updateRaw(role, id, request);
        validateSuccess(response, "Update non-series production");
        try {
            return response.as(NonSeriesProductionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse non-series production update response: " + response.getBody().asString(), e);
        }
    }

    @Step("API: PUT non-series production (raw response)")
    public Response updateRaw(UserRole role, Long id, NonSeriesProductionRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_PUT_UPDATE,
                role,
                request,
                String.valueOf(id));
    }

    /** Builds a PUT body from GET/create response (keeps resource usage amounts). */
    public static NonSeriesProductionRequest toUpdateRequest(NonSeriesProductionResponse source, Long storageId) {
        List<NonSeriesProductionResourceUsageRequest> usages = source.getResourceUsageList() == null
                ? List.of()
                : source.getResourceUsageList().stream()
                .map(usage -> NonSeriesProductionResourceUsageRequest.builder()
                        .resourceId(usage.getResource() != null ? usage.getResource().getId() : null)
                        .amount(usage.getAmount())
                        .build())
                .toList();
        return NonSeriesProductionRequest.builder()
                .storageId(storageId)
                .start(source.getStart())
                .end(source.getEnd())
                .workerQty(source.getWorkerQty())
                .product(source.getProduct())
                .amount(source.getAmount())
                .description(source.getDescription())
                .status(source.getStatus())
                .resourceUsageList(usages)
                .build();
    }

    @Step("API: Отримати залишок ресурсу {resourceId} на складі {storageId}")
    public double getResourceStock(Long storageId, Long resourceId) {
        return com.erp.utils.helpers.ProductionStockAssertions.resourceStockExact(
                apiExecutor, storageId, UserRole.OWNER_1, resourceId);
    }

    @Step("API: Знімок залишків усіх ресурсів на складі {storageId}")
    public Map<Long, Double> getInventorySnapshot(Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(
                response, StorageItemResponse.class);

        Map<Long, Double> snapshot = new LinkedHashMap<>();
        for (StorageItemResponse item : items) {
            if (item.getResource() != null && item.getResource().getId() != null) {
                snapshot.put(item.getResource().getId(),
                        item.getAmount() != null ? item.getAmount() : 0.0);
            }
        }
        return snapshot;
    }

    @Step("API: Перевірити, що залишки на складі {storageId} не змінились")
    public void assertInventoryUnchanged(Long storageId, Map<Long, Double> before) {
        Map<Long, Double> after = getInventorySnapshot(storageId);
        assertThat(after)
                .as("Знімок залишків після операції має збігатися зі знімком до неї")
                .isEqualTo(before);
    }

    @Step("API: Знайти несерійне виробництво за назвою «{product}»")
    public NonSeriesProductionResponse findByProduct(String product) {
        Long storageId = ConfigProvider.getOwner1StorageId();
        NonSeriesProductionQuery query = NonSeriesProductionQuery.builder()
                .storageId(storageId)
                .productSearch(product)
                .pageSize(50)
                .build();
        return getList(query).stream()
                .filter(record -> product.equals(record.getProduct()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Non-series production not found for product: " + product));
    }

    @Step("API: GET non-series production list with filters")
    public List<NonSeriesProductionResponse> getList(NonSeriesProductionQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_ALL,
                UserRole.OWNER_1,
                query.toListQueryParams());
        validateSuccess(response, "Get non-series production list");

        return DatabaseIntegrityValidator.extractList(response, NonSeriesProductionResponse.class);
    }

    @Step("API: GET non-series production total with filters")
    public BigDecimal getTotalAmount(NonSeriesProductionQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_TOTAL,
                UserRole.OWNER_1,
                query.toTotalQueryParams());
        validateSuccess(response, "Get non-series production total");

        NonSeriesProductionTotalResponse total = response.as(NonSeriesProductionTotalResponse.class);
        return total.getTotalAmount() != null ? total.getTotalAmount() : BigDecimal.ZERO;
    }

    public static BigDecimal sumAmounts(List<NonSeriesProductionResponse> records) {
        return records.stream()
                .map(NonSeriesProductionResponse::getAmount)
                .map(amount -> amount != null ? amount : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
