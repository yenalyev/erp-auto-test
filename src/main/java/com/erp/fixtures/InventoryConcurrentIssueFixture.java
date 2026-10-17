package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.models.request.InventoryRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Step;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Owns all stock and locations; the issue happens after the captured form snapshot. */
public class InventoryConcurrentIssueFixture extends BaseFixture {
    public final InventoryFixture inventory;
    public final RelocationFixture relocations;
    private final StorageFixture storages;
    private final ResourceFixture resources;
    private final List<ResourceResponse> ownedResources = new ArrayList<>();
    private final List<Long> issuedIds = new ArrayList<>();
    public StorageResponse source, recipient;
    public ResourceResponse edited, partial, exhausted;
    public InventoryProcessResponse process;
    public final String marker = "inv-concurrent-" + UUID.randomUUID().toString().substring(0, 8);

    public InventoryConcurrentIssueFixture(TestContext context, ApiExecutor executor) {
        super(context, executor);
        inventory = new InventoryFixture(context, executor);
        relocations = new RelocationFixture(context, executor);
        storages = new StorageFixture(context, executor);
        resources = new ResourceFixture(context, executor);
    }

    public void prepare() {
        source = storages.createChildStorage(ConfigProvider.getOwner1StorageId(), marker + "-source-");
        recipient = storages.createChildStorage(ConfigProvider.getOwner1StorageId(), marker + "-recipient-");
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        edited = resource("edited-");
        partial = resource("partial-");
        exhausted = resource("exhausted-");
    }

    private ResourceResponse resource(String suffix) {
        // The stock table abbreviates long names; keep the distinguishing part first.
        ResourceResponse resource = resources.createUniqueResource(suffix + marker + "-");
        ownedResources.add(resource);
        return resource;
    }

    @Step("Arrange: A=10, B=20, C=30; відкрити нову інвентаризацію")
    public void reset() {
        settleIssues();
        inventory.ensureClosed(source.getId());
        inventory.openSession(source.getId());
        inventory.conductInventory(source.getId(), UserRole.ADMIN,
                com.erp.data.factories.inventory.InventoryDataFactory.seedAmounts(Map.of(
                        edited.getId(), 10.0, partial.getId(), 20.0, exhausted.getId(), 30.0)));
        process = inventory.requestInventory(source.getId(), UserRole.ADMIN, marker + " request");
        inventory.approveInventory(source.getId(), UserRole.ADMIN, marker + " approve");
    }

    public List<StorageItemResponse> snapshot() {
        // Same endpoint as the UI; inventory-list DTOs must not hide a missing version.
        List<StorageItemResponse> items = storages.getById(UserRole.ADMIN, source.getId()).getItems();
        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> assertThat(item.getVersion())
                .as("Version of stock resource %s", item.getResource().getId()).isNotNull());
        return items;
    }

    public InventoryRequest request(List<StorageItemResponse> snapshot, Map<Long, Double> changes, String comment) {
        return InventoryRequest.builder().comment(comment)
                .knownResourceIds(snapshot.stream().map(i -> i.getResource().getId()).toList())
                .resources(snapshot.stream().map(item -> ResourceUsageRequest.builder()
                        .resourceId(item.getResource().getId()).version(item.getVersion())
                        .amount(BigDecimal.valueOf(changes.getOrDefault(item.getResource().getId(), item.getAmount())))
                        .build()).toList()).build();
    }

    @Step("Інший користувач видає B=7 і C=30 після завантаження форми")
    public void issueMixed() {
        trackIssue(relocations.createSendMultiItem(UserRole.ADMIN, source.getId(), recipient.getId(), List.of(
                ResourceUsageRequest.builder().resourceId(partial.getId()).amount(BigDecimal.valueOf(7)).build(),
                ResourceUsageRequest.builder().resourceId(exhausted.getId()).amount(BigDecimal.valueOf(30)).build()),
                marker + " issue").getId());
        assertStock(10, 13, 0);
    }

    public void issueAgain() {
        trackIssue(relocations.createSend(UserRole.ADMIN, source.getId(), recipient.getId(), partial.getId(), 2.0).getId());
    }

    public void trackIssue(long id) { issuedIds.add(id); }

    public Map<Long, Double> stock() {
        return storages.getById(UserRole.ADMIN, source.getId()).getItems().stream()
                .collect(Collectors.toMap(i -> i.getResource().getId(), StorageItemResponse::getAmount));
    }

    public void assertStock(double a, double b, double c) {
        Map<Long, Double> actual = stock();
        assertThat(actual.getOrDefault(edited.getId(), 0.0)).isEqualTo(a);
        assertThat(actual.getOrDefault(partial.getId(), 0.0)).isEqualTo(b);
        assertThat(actual.getOrDefault(exhausted.getId(), 0.0)).isEqualTo(c);
        assertThat(actual.keySet()).isSubsetOf(edited.getId(), partial.getId(), exhausted.getId());
    }

    public InventoryProcessResponse currentProcess() {
        return inventory.listInventoryProcesses(UserRole.ADMIN, Map.of("parentStorageId", source.getId(),
                        "page", 0, "size", 100)).stream().filter(p -> p.getId().equals(process.getId()))
                .findFirst().orElseThrow();
    }

    public void assertOnlyEditedDiff(double delta, String comment) {
        InventoryProcessResponse completed = currentProcess();
        assertThat(completed.getState()).isEqualTo("CLOSED");
        assertThat(completed.getCompletedComment()).isEqualTo(comment);
        assertThat(completed.getDiff().isArray()).isTrue();
        assertThat(completed.getDiff().size()).isEqualTo(1);
        var row = completed.getDiff().get(0);
        assertThat(row.path("resourceId").asLong()).isEqualTo(edited.getId());
        assertThat(row.path("diffAmount").asDouble()).isEqualTo(delta);
    }

    public List<ResourceHistoryResponse> inventoryHistory() {
        return inventory.parseOperationHistory(inventory.getOperationHistoryToday(source.getId(), UserRole.ADMIN))
                .getOperationHistoryList().stream()
                .filter(r -> Set.of("ADDED_INV", "REMOVED_INV").contains(r.getResourceOperationType())).toList();
    }

    public void closeSession() {
        if (source != null) inventory.ensureClosed(source.getId());
    }

    private void settleIssues() {
        for (Long id : List.copyOf(issuedIds)) {
            relocations.resolve(UserRole.ADMIN, id, recipient.getId(), RelocationState.FINISHED);
            issuedIds.remove(id);
        }
    }

    public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        attempt(errors, this::settleIssues);
        for (StorageResponse storage : Arrays.asList(source, recipient)) {
            if (storage != null) attempt(errors, () -> {
                inventory.ensureClosed(storage.getId());
                inventory.openSession(storage.getId());
                inventory.conductInventory(storage.getId(), UserRole.ADMIN,
                        InventoryRequest.builder().resources(List.of()).comment(marker + " cleanup").build());
            });
        }
        for (ResourceResponse resource : ownedResources) attempt(errors, () ->
                validateSuccess(resources.deactivate(UserRole.ADMIN, resource.getId()), "Archive owned resource"));
        for (StorageResponse storage : Arrays.asList(recipient, source)) {
            if (storage != null) attempt(errors, () ->
                    assertThat(storages.archiveStorage(UserRole.ADMIN, storage.getId())).isTrue());
        }
        if (!errors.isEmpty()) {
            AssertionError failure = new AssertionError("Concurrent inventory fixture cleanup failed");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (RuntimeException | AssertionError e) { errors.add(e); }
    }
}
