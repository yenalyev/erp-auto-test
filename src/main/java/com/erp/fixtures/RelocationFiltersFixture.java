package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.RelocationState;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.UserRole;
import com.erp.models.request.*;
import com.erp.models.response.*;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.XlsxWorkbookReader;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Owned mixed cargo and two independent location subtrees; no shared catalog mutations. */
public class RelocationFiltersFixture extends BaseFixture {
    public final StorageFixture storages;
    public final ResourceFixture resources;
    public final RelocationFixture relocations;
    private final IncidentFixture incidents;
    private final EquipmentFixture equipment;
    private final List<RelocationResponse> owned = new ArrayList<>();
    private final List<Long> lost = new ArrayList<>();
    private final List<ResourceResponse> ownedResources = new ArrayList<>();
    private final List<Long> categories = new ArrayList<>();
    private final List<Long> equipmentReceipts = new ArrayList<>();
    private final List<EquipmentResponse> ownedEquipment = new ArrayList<>();
    public final Map<String, List<Long>> ids = new LinkedHashMap<>();
    public final Map<String, List<Long>> equipmentIds = new LinkedHashMap<>();
    public final String marker = "rel-filter-" + UUID.randomUUID().toString().substring(0, 8);
    public final LocalDate today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
    public StorageResponse fromParent, toParent, alternateParent, source, destination, sibling;
    public ResourceResponse first, sameCategory, otherCategory;
    public Long categoryA, categoryB, modelA, modelB;
    public Long siblingRelocationId, oldRelocationId;
    private Long cleanupCategory;
    public String modelNameA, modelNameB;

    public RelocationFiltersFixture(TestContext context, ApiExecutor executor) {
        super(context, executor);
        storages = new StorageFixture(context, executor);
        resources = new ResourceFixture(context, executor);
        relocations = new RelocationFixture(context, executor);
        incidents = new IncidentFixture(context, executor);
        equipment = new EquipmentFixture(context, executor);
    }

    @Step("Arrange: піддерева локацій, змішані ресурси й переміщення всіх станів")
    public void prepare() {
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        cleanupCategory = testContext.get(ContextKey.SHARED_RESOURCE_CATEGORY_ID);
        fromParent = storages.createChildStorage(marker + "-from-");
        toParent = storages.createChildStorage(marker + "-to-");
        alternateParent = storages.createChildStorage(marker + "-alt-");
        source = storages.createChildStorage(fromParent.getId(), marker + "-src-");
        destination = storages.createChildStorage(toParent.getId(), marker + "-dst-");
        sibling = storages.createChildStorage(toParent.getId(), marker + "-sibling-");
        categoryA = category("A");
        categoryB = category("B");
        first = resource("first", categoryA);
        sameCategory = resource("same", categoryA);
        otherCategory = resource("other", categoryB);
        Long supplier = RelocationStockSeeder.resolveSupplierStorageId(apiExecutor, UserRole.ADMIN);
        List<ResourceUsageRequest> seedItems = ownedResources.stream().map(r ->
                RelocationDataFactory.usageForExternalBatch(r.getId(), 100,
                        marker + "-batch-" + r.getId(), false)).toList();
        Response received = apiExecutor.executeRelocationReceive(RelocationDataFactory.buildReceiveRequest(
                supplier, source.getId(), seedItems, marker + "-seed").toBuilder()
                .date(today.minusDays(3)).build(), UserRole.ADMIN);
        validateSuccess(received, "Seed owned resource stock");
        owned.add(received.as(RelocationResponse.class));
        for (RelocationState state : List.of(RelocationState.CREATED, RelocationState.CANCELLED,
                RelocationState.FINISHED, RelocationState.RETURNED, RelocationState.LOST)) {
            RelocationResponse sent = sendMixed(destination.getId(), today, state.name());
            if (state == RelocationState.LOST) {
                incidents.createIncident(UserRole.ADMIN, sent, marker + "-lost");
                lost.add(sent.getId());
                sent.setState(RelocationState.LOST);
            } else if (state != RelocationState.CREATED) {
                if (state == RelocationState.RETURNED) resolve(sent, RelocationState.CANCELLED);
                resolve(sent, state);
            }
            String key = state == RelocationState.CREATED || state == RelocationState.CANCELLED ? "active"
                    : state == RelocationState.LOST ? "lost" : "history";
            ids.computeIfAbsent(key, ignored -> new ArrayList<>()).add(sent.getId());
        }
        // Same resources, date and sender; must be excluded by recipient AND filtering.
        RelocationResponse control = sendMixed(sibling.getId(), today, "sibling");
        siblingRelocationId = control.getId();
        resolve(control, RelocationState.FINISHED);
        RelocationResponse old = sendMixed(destination.getId(), today.minusDays(2), "old");
        oldRelocationId = old.getId();
        resolve(old, RelocationState.FINISHED);
    }

    @Step("Arrange: переміщення двох моделей обладнання")
    public void prepareEquipment() {
        equipment.prepareCategoryContext();
        Long category = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        modelNameA = marker + "-model-A";
        modelNameB = marker + "-model-B";
        List<EquipmentResponse> a = equipment.createEquipmentGroup(UserRole.ADMIN, source.getId(),
                category, modelNameA, inventoryNumbers("A", 3));
        equipmentReceipts.add(a.getFirst().getId());
        ownedEquipment.addAll(a);
        List<EquipmentResponse> b = equipment.createEquipmentGroup(UserRole.ADMIN, source.getId(),
                category, modelNameB, inventoryNumbers("B", 3));
        equipmentReceipts.add(b.getFirst().getId());
        ownedEquipment.addAll(b);
        modelA = modelId(modelNameA);
        modelB = modelId(modelNameB);
        for (int i = 0; i < 3; i++) {
            EquipmentRelocationSendRequest request = EquipmentRelocationSendRequest.builder()
                    .fromStorageId(source.getId()).toStorageId(destination.getId()).date(today)
                    .description(marker + "-equipment-" + i)
                    .equipmentIds(List.of(a.get(i).getId(), b.get(i).getId())).build();
            Response response = equipment.sendEquipmentRaw(UserRole.ADMIN, request);
            validateSuccess(response, "Send owned equipment");
            RelocationResponse sent = response.as(RelocationResponse.class);
            owned.add(sent);
            if (i == 1) resolve(sent, RelocationState.CANCELLED);
            if (i == 2) resolve(sent, RelocationState.FINISHED);
            equipmentIds.computeIfAbsent(i < 2 ? "active" : "history", ignored -> new ArrayList<>())
                    .add(sent.getId());
        }
    }

    private List<String> inventoryNumbers(String suffix, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> marker + suffix + i).toList();
    }

    private Long modelId(String name) {
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                UserRole.ADMIN, Map.of("modelName", name, "size", 50));
        validateSuccess(response, "Resolve owned equipment model");
        return response.jsonPath().getList("content", EquipmentModelResponse.class).stream()
                .filter(m -> name.equals(m.getName())).findFirst().orElseThrow().getId();
    }

    private Long category(String suffix) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_CREATE, UserRole.ADMIN,
                ResourceCategoryRequest.builder().name(marker + "-category-" + suffix).build());
        validateSuccess(response, "Create owned category");
        Long id = response.as(ResourceCategoryResponse.class).getId();
        categories.add(id);
        return id;
    }

    private ResourceResponse resource(String suffix, Long category) {
        ResourceResponse resource = resources.createUniqueResource(marker + "-" + suffix + "-", category);
        ownedResources.add(resource);
        return resource;
    }

    private RelocationResponse sendMixed(Long to, LocalDate date, String suffix) {
        Response response = relocations.sendRaw(UserRole.ADMIN, RelocationDataFactory.buildSendMultiItem(
                source.getId(), to, List.of(RelocationDataFactory.usage(first.getId(), 2),
                        RelocationDataFactory.usage(sameCategory.getId(), 3),
                        RelocationDataFactory.usage(otherCategory.getId(), 5)), marker + "-" + suffix)
                .toBuilder().date(date).build());
        validateSuccess(response, "Send mixed resources");
        RelocationResponse sent = response.as(RelocationResponse.class);
        owned.add(sent);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        return sent;
    }

    private void resolve(RelocationResponse sent, RelocationState state) {
        Long actorStorage = state == RelocationState.RETURNED ? source.getId() : sent.getRecipient().getId();
        Response response = apiExecutor.executeRelocationResolve(sent.getId(), actorStorage,
                RelocationUpdateRequest.builder().state(state).description(marker + "-" + state)
                        .receivedDate(state == RelocationState.FINISHED ? today : null).build(), UserRole.ADMIN);
        validateSuccess(response, "Resolve " + state);
        assertThat(response.as(RelocationResponse.class).getState()).isEqualTo(state);
        sent.setState(state);
    }

    public Map<String, Object> query(String tab) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("states", switch (tab) {
            case "active" -> List.of("CREATED", "CANCELLED");
            case "lost" -> List.of("LOST");
            default -> List.of("FINISHED", "AUTO_FINISHED", "RETURNED");
        });
        query.put("fromIds", List.of(fromParent.getId()));
        query.put("toIds", List.of(destination.getId()));
        query.put("startDate", today.toString());
        query.put("endDate", today.toString());
        query.put("type", "RESOURCE");
        return query;
    }

    @Step("GET журнал із точними фільтрами")
    public List<RelocationResponse> rows(Map<String, Object> filters) {
        Map<String, Object> params = new LinkedHashMap<>(filters);
        params.putIfAbsent("size", 100);
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_PAGE,
                UserRole.ADMIN, params);
        validateSuccess(response, "Filtered journal");
        List<RelocationResponse> rows = response.jsonPath().getList("content", RelocationResponse.class);
        assertThat(rows.size()).as("Fixture must fit in a page; no silently truncated checks")
                .isEqualTo(response.jsonPath().getInt("page.totalElements"));
        return rows;
    }

    @Step("GET Excel із тими самими фільтрами")
    public List<List<String>> excel(Map<String, Object> filters) {
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_EXPORT,
                UserRole.ADMIN, filters);
        validateSuccess(response, "Filtered XLSX");
        byte[] bytes = response.asByteArray();
        io.qameta.allure.Allure.addAttachment("Filtered relocation Excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new java.io.ByteArrayInputStream(bytes), "xlsx");
        return XlsxWorkbookReader.firstSheet(bytes);
    }

    public List<Long> expectedIds(String tab) { return ids.get(tab.equals("sent") || tab.equals("received") ? "history" : tab); }
    public List<Long> expectedEquipmentIds(String tab) {
        return equipmentIds.getOrDefault(tab.equals("sent") || tab.equals("received") ? "history" : tab, List.of());
    }

    public ResourceResponse updateFirst(String name, Long category) {
        return resources.update(UserRole.ADMIN, first.getId(), ResourceRequest.builder().name(name)
                .measurementUnitId(first.getUnit().getId()).categoryId(category).build());
    }

    @Step("Cleanup: завершити власні операції, обнулити запаси та архівувати довідники")
    public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        for (Long id : lost) attempt(errors, () -> {
            incidents.deleteIncident(UserRole.ADMIN, id);
            owned.stream().filter(r -> id.equals(r.getId())).forEach(r -> r.setState(RelocationState.CREATED));
        });
        // validateForDelete accepts only AUTO_FINISHED. Settled internal history is immutable;
        // complete active sends so they cannot block resources or leave equipment IN_TRANSIT.
        for (RelocationResponse row : owned) attempt(errors, () -> {
            if (row.getState() == RelocationState.CREATED) resolve(row, RelocationState.FINISHED);
            else if (row.getState() == RelocationState.CANCELLED) resolve(row, RelocationState.RETURNED);
        });
        for (EquipmentResponse item : ownedEquipment) attempt(errors, () ->
                equipment.changeEquipmentStatus(UserRole.ADMIN, item.getId(), EquipmentStatus.RETIRED));
        InventoryFixture inventory = new InventoryFixture(testContext, apiExecutor);
        for (StorageResponse storage : Arrays.asList(source, destination, sibling)) {
            if (storage != null) attempt(errors, () -> {
                inventory.ensureClosed(storage.getId());
                inventory.openSession(storage.getId());
                try {
                    // Current inventory contract requires explicit versioned removal;
                    // an omitted row is preserved to protect concurrent receipts.
                    List<StorageItemResponse> current = inventory.listItems(storage.getId(), UserRole.ADMIN,
                            Map.of("size", 1000, "page", 0, "showZeroStock", true));
                    List<Long> ownedIds = ownedResources.stream().map(ResourceResponse::getId).toList();
                    assertThat(current).allSatisfy(item -> assertThat(ownedIds)
                            .as("Only fixture-owned stock may be removed").contains(item.getResource().getId()));
                    inventory.conductInventory(storage.getId(), UserRole.ADMIN,
                            InventoryRequest.builder().resources(current.stream().map(item ->
                                            ResourceUsageRequest.builder().resourceId(item.getResource().getId())
                                                    .amount(java.math.BigDecimal.ZERO).version(item.getVersion())
                                                    .removed(true).build()).toList())
                                    .knownResourceIds(current.stream().map(item -> item.getResource().getId()).toList())
                                    .comment(marker + " cleanup").build());
                } finally { inventory.closeSession(storage.getId()); }
                assertThat(inventory.listItems(storage.getId(), UserRole.ADMIN,
                        Map.of("size", 1000, "page", 0, "showZeroStock", true))).isEmpty();
            });
        }
        for (ResourceResponse resource : ownedResources) attempt(errors, () -> {
            resources.update(UserRole.ADMIN, resource.getId(), ResourceRequest.builder().name(resource.getName())
                    .measurementUnitId(resource.getUnit().getId()).categoryId(cleanupCategory).build());
            Response response = resources.deactivate(UserRole.ADMIN, resource.getId());
            validateSuccess(response, "Deactivate owned resource");
        });
        for (Long category : categories) attempt(errors, () -> validateSuccess(apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_CATEGORY_DELETE, UserRole.ADMIN, null, category), "Delete owned category"));
        for (StorageResponse storage : Arrays.asList(sibling, destination, source, alternateParent, toParent, fromParent)) {
            if (storage != null) attempt(errors, () -> assertThat(storages.archiveStorage(UserRole.ADMIN, storage.getId()))
                    .as("Archive owned location %s", storage.getId()).isTrue());
        }
        io.qameta.allure.Allure.addAttachment("Retained immutable filter-test history",
                "Internal FINISHED/RETURNED history and consumed stock receipts cannot be deleted through the API. IDs: "
                        + owned.stream().map(RelocationResponse::getId).toList() + "; equipment receipt anchors: "
                        + equipmentReceipts + "; owned locations archived; equipment retired; resources archived.");
        if (!errors.isEmpty()) {
            AssertionError failure = new AssertionError("Relocation filter cleanup failed");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (RuntimeException | AssertionError e) { errors.add(e); }
    }
}
