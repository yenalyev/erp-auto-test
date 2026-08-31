package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.RequestBodyFactory;
import com.erp.data.factories.ResourceDataFactory;
import com.erp.data.factories.inventory.InventoryDataFactory;
import com.erp.models.request.ResourceRequest;
import com.erp.test_context.ContextKey;
import com.erp.enums.UserRole;
import com.erp.models.request.InventoryRequest;
import com.erp.models.response.InventorySessionStatus;
import com.erp.models.response.MultiLocationStorageItemResponse;
import com.erp.models.response.ProductionProcessTagStatisticResponse;
import com.erp.models.response.ResourceHistoryGroupResponse;
import com.erp.models.response.ResourceHistoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.HashtagTestData;
import com.erp.utils.helpers.PollUtils;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class InventoryFixture extends BaseFixture {

    public InventoryFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public record TagOrFilterSeed(
            ResourceResponse resourceA,
            ResourceResponse resourceB,
            ResourceResponse resourceC,
            String tagA,
            String tagB) {}

    @Step("FIXTURE: Підготовка контексту інвентаризації")
    public void prepareContext() {
        // stock seeding delegated to RelocationFixture when needed
    }

    /**
     * Three unique catalog resources with stock: A tagged tagA, B tagged tagB, C untagged.
     * Tags are unique so inventory info-chips do not collide with shared staging data.
     */
    @Step("FIXTURE: три ресурси з унікальними тегами для OR-фільтра на складі {storageId}")
    public TagOrFilterSeed seedTagOrFilterResources(long storageId, RelocationFixture relocationFixture) {
        ResourceFixture resourceFixture = new ResourceFixture(testContext, apiExecutor);
        String tagA = HashtagTestData.uniqueTag("ora");
        String tagB = HashtagTestData.uniqueTag("orb");

        ResourceResponse resourceA = resourceFixture.createUniqueResource("inv-ora-");
        resourceFixture.updateNotes(UserRole.ADMIN, resourceA.getId(), HashtagTestData.notesWithTags(tagA));
        relocationFixture.ensureStock(storageId, resourceA.getId(), 5.0);

        ResourceResponse resourceB = resourceFixture.createUniqueResource("inv-orb-");
        resourceFixture.updateNotes(UserRole.ADMIN, resourceB.getId(), HashtagTestData.notesWithTags(tagB));
        relocationFixture.ensureStock(storageId, resourceB.getId(), 5.0);

        ResourceResponse resourceC = resourceFixture.createUniqueResource("inv-orc-");
        relocationFixture.ensureStock(storageId, resourceC.getId(), 5.0);

        PollUtils.waitUntil(
                () -> getTagStatistics(storageId, UserRole.ADMIN),
                stats -> stats.stream().map(ProductionProcessTagStatisticResponse::getTag)
                        .anyMatch(tagA::equals)
                        && stats.stream().map(ProductionProcessTagStatisticResponse::getTag)
                        .anyMatch(tagB::equals),
                15_000,
                "inventory tag-statistics contains " + tagA + " and " + tagB);

        return new TagOrFilterSeed(resourceA, resourceB, resourceC, tagA, tagB);
    }

    @Step("API: Список залишків на складі {storageId}")
    public List<StorageItemResponse> listItems(long storageId, UserRole role) {
        return listItems(storageId, role, Map.of());
    }

    @Step("API: Список залишків на складі {storageId} з фільтрами")
    public List<StorageItemResponse> listItems(long storageId, UserRole role, Map<String, ?> queryParams) {
        Response response = queryParams.isEmpty()
                ? apiExecutor.execute(ApiEndpointDefinition.STORAGE_INVENTORY_GET, role, String.valueOf(storageId))
                : apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.STORAGE_INVENTORY_GET,
                        role,
                        queryParams,
                        String.valueOf(storageId));
        validateSuccess(response, "GET storage inventory");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_INVENTORY_GET);
        List<StorageItemResponse> items = response.jsonPath().getList("content", StorageItemResponse.class);
        return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    @Step("API: Залишок ресурсу {resourceId} на складі {storageId}")
    public double getResourceStock(long storageId, long resourceId, UserRole role) {
        return com.erp.utils.helpers.ProductionStockAssertions.resourceStockExact(
                apiExecutor, storageId, role, resourceId);
    }

    @Step("API: Перший ресурс з ненульовим залишком на складі {storageId}")
    public StorageItemResponse requireItemWithStock(long storageId, UserRole role) {
        return listItems(storageId, role).stream()
                .filter(i -> i.getResource() != null && i.getAmount() != null && i.getAmount() > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No inventory item with stock > 0 on storage " + storageId));
    }

    @Step("API: Рядок залишку для ресурсу {resourceId} на складі {storageId}")
    public StorageItemResponse requireItemForResource(long storageId, long resourceId, UserRole role) {
        return requireItemForResourceWithRetry(storageId, resourceId, role, 0);
    }

    @Step("API: Рядок залишку для ресурсу {resourceId} на складі {storageId} (retry)")
    public StorageItemResponse requireItemForResourceWithRetry(long storageId,
                                                               long resourceId,
                                                               UserRole role,
                                                               long timeoutMs) {
        StorageItemResponse item = findItemForResource(storageId, resourceId, role);
        if (item != null) {
            return item;
        }
        if (timeoutMs <= 0) {
            throw new IllegalStateException(
                    "Resource " + resourceId + " not found on storage " + storageId);
        }
        return PollUtils.waitUntil(
                () -> findItemForResource(storageId, resourceId, role),
                Objects::nonNull,
                timeoutMs,
                "Resource " + resourceId + " with stock on storage " + storageId);
    }

    private StorageItemResponse findItemForResource(long storageId, long resourceId, UserRole role) {
        return listItems(storageId, role).stream()
                .filter(i -> i.getResource() != null && resourceId == i.getResource().getId())
                .filter(i -> i.getAmount() != null && i.getAmount() > 0)
                .findFirst()
                .orElse(null);
    }

    @Step("API: Ресурс з довідника, якого немає на складі {storageId}")
    public ResourceResponse pickResourceNotOnStorage(long storageId,
                                                     UserRole role,
                                                     List<ResourceResponse> catalog) {
        List<Long> onStorage = listItems(storageId, role).stream()
                .filter(i -> i.getResource() != null && i.getResource().getId() != null)
                .map(i -> i.getResource().getId())
                .toList();
        return catalog.stream()
                .filter(r -> r.getId() != null && !onStorage.contains(r.getId()))
                .findFirst()
                .orElseGet(() -> createCatalogResourceAbsentFromStorage(storageId, role));
    }

    @Step("API: Створити ресурс, якого немає на складі {storageId}")
    public ResourceResponse createCatalogResourceAbsentFromStorage(long storageId, UserRole role) {
        Object body = RequestBodyFactory.generate(ApiEndpointDefinition.RESOURCE_CREATE, testContext);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, body);
        validateSuccess(response, "Create catalog resource for inventory test");
        ResourceResponse created = response.as(ResourceResponse.class);
        if (getResourceStock(storageId, created.getId(), role) > 0) {
            throw new IllegalStateException(
                    "Newly created resource " + created.getId() + " already has stock on storage " + storageId);
        }
        log.info("Created catalog resource {} absent from storage {}", created.getId(), storageId);
        return created;
    }

    @Step("API: Створити унікальний ресурс «{namePrefix}*», якого немає на складі {storageId}")
    public ResourceResponse createUniqueCatalogResourceAbsentFromStorage(long storageId,
                                                                         UserRole role,
                                                                         String namePrefix) {
        Long unitId = testContext.get(ContextKey.SHARED_UNIT_ID);
        Long categoryId = testContext.get(ContextKey.SHARED_RESOURCE_CATEGORY_ID);
        if (unitId == null || categoryId == null) {
            throw new IllegalStateException(
                    "SHARED_UNIT_ID and SHARED_RESOURCE_CATEGORY_ID required for unique resource creation");
        }
        ResourceRequest body = ResourceDataFactory.uniqueResource(namePrefix, unitId, categoryId);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, body);
        validateSuccess(response, "Create unique catalog resource for inventory UI test");
        ResourceResponse created = response.as(ResourceResponse.class);
        if (getResourceStock(storageId, created.getId(), role) > 0) {
            throw new IllegalStateException(
                    "Newly created resource " + created.getId() + " already has stock on storage " + storageId);
        }
        log.info("Created unique catalog resource {} ({}) absent from storage {}",
                created.getId(), created.getName(), storageId);
        return created;
    }

    @Step("API: Статус сесії інвентаризації на складі {storageId}")
    public InventorySessionStatus getStatus(long storageId, UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_GET,
                role,
                String.valueOf(storageId));
        validateSuccess(response, "GET inventory session status");
        return response.as(InventorySessionStatus.class);
    }

    @Step("API: PUT статус сесії open={open} на складі {storageId}")
    public Response putStatus(long storageId, UserRole role, boolean open) {
        InventorySessionStatus request = InventorySessionStatus.builder().open(open).build();
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_PUT,
                role,
                request,
                String.valueOf(storageId));
    }

    @Step("API: Закрити сесію інвентаризації на складі {storageId}, якщо відкрита")
    public void ensureClosed(long storageId) {
        InventorySessionStatus status = getStatus(storageId, UserRole.ADMIN);
        if (Boolean.TRUE.equals(status.getOpen())) {
            closeSession(storageId);
        }
    }

    @Step("API: Відкрити сесію інвентаризації на складі {storageId}")
    public InventorySessionStatus openSession(long storageId) {
        Response response = putStatus(storageId, UserRole.ADMIN, true);
        validateSuccess(response, "PUT open inventory session");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_INVENTORY_STATUS_PUT);
        InventorySessionStatus body = response.as(InventorySessionStatus.class);
        assertThat(body.getOpen()).isTrue();
        return body;
    }

    @Step("API: Закрити сесію інвентаризації на складі {storageId}")
    public InventorySessionStatus closeSession(long storageId) {
        Response response = putStatus(storageId, UserRole.ADMIN, false);
        validateSuccess(response, "PUT close inventory session");
        InventorySessionStatus body = response.as(InventorySessionStatus.class);
        assertThat(body.getOpen()).isFalse();
        return body;
    }

    @Step("API: Статус сесії інвентаризації обладнання на складі {storageId}")
    public InventorySessionStatus getEquipmentStatus(long storageId, UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_EQUIPMENT_INVENTORY_STATUS_GET,
                role,
                String.valueOf(storageId));
        validateSuccess(response, "GET equipment inventory session status");
        return response.as(InventorySessionStatus.class);
    }

    @Step("API: PUT статус сесії обладнання open={open} на складі {storageId}")
    public Response putEquipmentStatus(long storageId, UserRole role, boolean open) {
        InventorySessionStatus request = InventorySessionStatus.builder().open(open).build();
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_EQUIPMENT_INVENTORY_STATUS_PUT,
                role,
                request,
                String.valueOf(storageId));
    }

    @Step("API: Закрити сесію інвентаризації обладнання на складі {storageId}, якщо відкрита")
    public void ensureEquipmentClosed(long storageId) {
        InventorySessionStatus status = getEquipmentStatus(storageId, UserRole.ADMIN);
        if (Boolean.TRUE.equals(status.getOpen())) {
            closeEquipmentSession(storageId);
        }
    }

    @Step("API: Відкрити сесію інвентаризації обладнання на складі {storageId}")
    public InventorySessionStatus openEquipmentSession(long storageId) {
        Response response = putEquipmentStatus(storageId, UserRole.ADMIN, true);
        validateSuccess(response, "PUT open equipment inventory session");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_EQUIPMENT_INVENTORY_STATUS_PUT);
        InventorySessionStatus body = response.as(InventorySessionStatus.class);
        assertThat(body.getOpen()).isTrue();
        return body;
    }

    @Step("API: Закрити сесію інвентаризації обладнання на складі {storageId}")
    public InventorySessionStatus closeEquipmentSession(long storageId) {
        Response response = putEquipmentStatus(storageId, UserRole.ADMIN, false);
        validateSuccess(response, "PUT close equipment inventory session");
        InventorySessionStatus body = response.as(InventorySessionStatus.class);
        assertThat(body.getOpen()).isFalse();
        return body;
    }

    @Step("API: Провести інвентаризацію на складі {storageId}")
    public StorageResponse conductInventory(long storageId, UserRole role, InventoryRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_PUT,
                role,
                request,
                String.valueOf(storageId));
        validateSuccess(response, "PUT conduct inventory");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STORAGE_INVENTORY_PUT);
        return response.as(StorageResponse.class);
    }

    @Step("API: Провести інвентаризацію (очікуваний HTTP {expectedStatus})")
    public Response conductInventoryRaw(long storageId, UserRole role, InventoryRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_PUT,
                role,
                request,
                String.valueOf(storageId));
    }

    @Step("API: Оновити залишок ресурсу {resourceId} до {targetAmount}")
    public void setResourceAmount(long storageId,
                                  UserRole role,
                                  long resourceId,
                                  double targetAmount) {
        List<StorageItemResponse> items = listItems(storageId, role);
        InventoryRequest request = InventoryDataFactory.mergeWithExisting(
                items, Map.of(resourceId, targetAmount));
        conductInventory(storageId, role, request);
    }

    @Step("API: Відновити залишок ресурсу {resourceId} до {targetAmount}")
    public void resetResourceStock(long storageId, long resourceId, double targetAmount, UserRole role) {
        ensureClosed(storageId);
        openSession(storageId);
        try {
            setResourceAmount(storageId, role, resourceId, targetAmount);
        } finally {
            closeSession(storageId);
        }
    }

    /**
     * Zero every positive stock line so {@code DELETE} deactivate can archive the location.
     * No-op when the location has no leftover inventory.
     */
    @Step("API: Обнулити залишки на складі {storageId} інвентаризацією")
    public void clearStock(long storageId) {
        List<StorageItemResponse> items = listItems(storageId, UserRole.ADMIN, Map.of("size", 1000, "page", 0));
        boolean hasStock = items.stream()
                .anyMatch(i -> i.getAmount() != null && i.getAmount() > 0);
        if (!hasStock) {
            return;
        }
        ensureClosed(storageId);
        openSession(storageId);
        try {
            conductInventory(storageId, UserRole.ADMIN, InventoryDataFactory.zeroAll(items));
        } finally {
            closeSession(storageId);
        }
        log.info("Cleared leftover inventory on storage id={}", storageId);
    }

    @Step("API: Прибрати ресурс {resourceId} зі складу {storageId}")
    public void removeResourceFromStorage(long storageId, long resourceId, UserRole role) {
        boolean onStorage = listItems(storageId, role).stream()
                .anyMatch(i -> i.getResource() != null
                        && Objects.equals(resourceId, i.getResource().getId()));
        if (!onStorage) {
            return;
        }
        ensureClosed(storageId);
        openSession(storageId);
        try {
            List<StorageItemResponse> items = listItems(storageId, role);
            InventoryRequest request = InventoryDataFactory.copyExcept(items, resourceId);
            conductInventory(storageId, role, request);
        } finally {
            closeSession(storageId);
        }
    }

    @Step("API: Мульти-локаційний GET inventory")
    public Response getMultiLocationInventory(UserRole role, String locationsCsv) {
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET,
                role,
                Map.of("locations", locationsCsv, "size", 50));
    }

    @Step("API: Hierarchy GET inventory parentStorageId={parentStorageId}")
    public Response getHierarchyInventory(long parentStorageId, UserRole role) {
        return getHierarchyInventory(parentStorageId, role, Map.of());
    }

    @Step("API: Hierarchy GET inventory parentStorageId={parentStorageId} з доп. params")
    public Response getHierarchyInventory(long parentStorageId, UserRole role, Map<String, ?> extraParams) {
        Map<String, Object> params = new HashMap<>();
        params.put("parentStorageId", parentStorageId);
        params.put("size", 50);
        params.put("sort", "resourceId,asc");
        if (extraParams != null) {
            params.putAll(extraParams);
        }
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_HIERARCHY_GET,
                role,
                params);
    }

    @Step("API: Hierarchy GET inventory parentStorageId={parentStorageId} tags={tags}")
    public List<MultiLocationStorageItemResponse> listHierarchyByTags(
            long parentStorageId, UserRole role, List<String> tags) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("size", 200);
        extra.put("sort", "resource.name,asc");
        if (tags != null && !tags.isEmpty()) {
            extra.put("tags", tags);
        }
        Response response = getHierarchyInventory(parentStorageId, role, extra);
        validateSuccess(response, "GET hierarchy inventory tags=" + tags);
        List<MultiLocationStorageItemResponse> content =
                response.jsonPath().getList("content", MultiLocationStorageItemResponse.class);
        return content == null ? List.of() : content.stream().filter(Objects::nonNull).toList();
    }

    @Step("API: GET tag-statistics залишків parentStorageId={parentStorageId}")
    public List<ProductionProcessTagStatisticResponse> getTagStatistics(long parentStorageId, UserRole role) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_TAG_STATISTICS_GET,
                role,
                Map.of("parentStorageId", parentStorageId));
        validateSuccess(response, "GET inventory tag-statistics");
        return DatabaseIntegrityValidator.extractList(response, ProductionProcessTagStatisticResponse.class);
    }

    @Step("API: Експорт залишків складу {storageId}")
    public Response exportRemainders(long storageId, UserRole role) {
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EXPORT_REMAINDER_GET,
                role,
                Map.of("storageId", storageId));
    }

    @Step("API: Історія операцій за сьогодні для складу {storageId}")
    public Response getOperationHistoryToday(long storageId, UserRole role) {
        LocalDate today = LocalDate.now();
        Map<String, Object> params = new HashMap<>();
        params.put("storageIds", storageId);
        params.put("from", today.toString());
        params.put("to", today.toString());
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_OPERATION_HISTORY_GET,
                role,
                params);
    }

    @Step("API: розпарсити resource-operation-history")
    public ResourceHistoryGroupResponse parseOperationHistory(Response response) {
        validateSuccess(response, "GET resource operation history");
        return response.as(ResourceHistoryGroupResponse.class);
    }

    @Step("API: comment інвентаризації в історії для resourceId={resourceId}")
    public String findInventoryHistoryComment(long storageId, long resourceId, UserRole role) {
        Response historyResponse = getOperationHistoryToday(storageId, role);
        if (historyResponse.statusCode() == 403) {
            throw new org.testng.SkipException("Current role lacks resource-operation-history read permission");
        }
        ResourceHistoryGroupResponse history = parseOperationHistory(historyResponse);
        if (history.getOperationHistoryList() == null) {
            return null;
        }
        // Stream.reduce cannot yield null (Optional.of NPE) — omit/blank comment is a valid last row.
        return history.getOperationHistoryList().stream()
                .filter(entry -> entry.getResource() != null
                        && Objects.equals(resourceId, entry.getResource().getId())
                        && isInventoryOperation(entry.getResourceOperationType()))
                .reduce((first, second) -> second)
                .map(ResourceHistoryResponse::getComment)
                .orElse(null);
    }

    private static boolean isInventoryOperation(String operationType) {
        return "ADDED_INV".equals(operationType) || "REMOVED_INV".equals(operationType);
    }

    @Step("API: Партії ресурсу (isProduced=false)")
    public Response getBatches(long storageId, long storageItemId, UserRole role) {
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_BATCHES_GET_NON_PRODUCED,
                role,
                String.valueOf(storageId),
                storageItemId);
    }
}
