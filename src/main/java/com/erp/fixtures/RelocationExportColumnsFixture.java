package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.*;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.*;
import com.erp.test_context.*;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.utils.helpers.RemainderExportParents;
import io.qameta.allure.Allure;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Only owned locations/resources are mutated. Group settings are a read-only oracle. */
public class RelocationExportColumnsFixture {
    public record Transfer(RelocationResponse relocation, StorageResponse sender, StorageResponse recipient,
                           String senderGroup, String recipientGroup, String kind) { }
    private final StorageFixture storages;
    private final ResourceFixture resources;
    private final RelocationFixture relocations;
    private final InventoryFixture inventory;
    private final StorageRegionFixture regions;
    private final ApiExecutor executor;
    private final List<StorageResponse> locations = new ArrayList<>();
    private final List<RelocationResponse> created = new ArrayList<>();
    private final List<ResourceResponse> products = new ArrayList<>();
    private final List<Transfer> transfers = new ArrayList<>();

    public RelocationExportColumnsFixture(TestContext context, ApiExecutor executor) {
        this.executor = executor;
        storages = new StorageFixture(context, executor);
        resources = new ResourceFixture(context, executor);
        relocations = new RelocationFixture(context, executor);
        inventory = new InventoryFixture(context, executor);
        regions = new StorageRegionFixture(context, executor);
        context.set(ContextKey.RELOCATION_SUPPLIER_ID,
                RelocationStockSeeder.resolveSupplierStorageId(executor, UserRole.ADMIN));
    }

    public void arrangeKinds(long parent) {
        initProducts();
        var sender = location(parent, StorageKind.LOCATION);
        var recipients = new ArrayList<StorageResponse>();
        for (var kind : StorageKind.values()) {
            var recipient = location(parent, kind);
            recipients.add(recipient);
            send(sender, recipient, null, null);
        }
        // Two distinct relocations with identical business cells must both survive export.
        send(sender, recipients.getFirst(), null, null);
    }

    public void arrangeGroups(Connection connection) throws Exception {
        initProducts();
        var parents = new ArrayList<>(RemainderExportParents.resolve(connection).entrySet());
        for (int i = 0; i < parents.size(); i++) {
            var recipientParent = parents.get(i);
            var senderParent = parents.get((i + 1) % parents.size());
            var sender = location(senderParent.getValue(), StorageKind.LOCATION);
            for (var kind : StorageKind.values()) {
                send(sender, location(recipientParent.getValue(), kind),
                        senderParent.getKey(), recipientParent.getKey());
            }
        }
    }

    private void initProducts() {
        if (!products.isEmpty()) return;
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        products.add(resources.createUniqueResource("rel-columns-A-"));
        products.add(resources.createUniqueResource("rel-columns-B-"));
    }

    private StorageResponse location(long parent, StorageKind kind) {
        var storage = storages.createStorage(StorageDataFactory.childStorage(parent, "rel-columns-")
                .kind(kind).features(kind == StorageKind.LOCATION
                        ? Set.of(LocationFeature.RELOCATIONS) : Set.of()).build());
        locations.add(storage);
        return storage;
    }

    private void send(StorageResponse sender, StorageResponse recipient, String senderGroup, String recipientGroup) {
        List<ResourceUsageRequest> items = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            var product = products.get(i);
            List<RelocationItemBatchRequest> batches = new ArrayList<>();
            for (double amount : i == 0 ? new double[]{1.25, 2.5} : new double[]{4.75}) {
                String batch = "rel-col-" + UUID.randomUUID();
                created.add(relocations.createExternalReceive(UserRole.ADMIN, sender.getId(), product.getId(), amount, batch));
                var uuid = ProductionStockAssertions.requireBatchUuid(executor, sender.getId(), UserRole.ADMIN,
                        product.getId(), batch, false);
                batches.add(RelocationItemBatchRequest.builder().batchUuid(uuid).batchNumber(batch)
                        .amount(BigDecimal.valueOf(amount)).isProduced(false).build());
            }
            items.add(ResourceUsageRequest.builder().resourceId(product.getId())
                    .amount(new BigDecimal(i == 0 ? "3.75" : "4.75")).batches(batches).build());
        }
        var sent = relocations.createSendMultiItem(UserRole.ADMIN, sender.getId(), recipient.getId(), items,
                "rel-columns-" + UUID.randomUUID());
        created.add(sent);
        if (sent.getState() == RelocationState.CREATED) {
            sent = relocations.resolve(UserRole.ADMIN, sent.getId(),
                    recipient.getKind() == StorageKind.LOCATION ? recipient.getId() : sender.getId(),
                    RelocationState.FINISHED);
        }
        assertThat(sent.getState()).isIn(RelocationState.FINISHED, RelocationState.AUTO_FINISHED);
        transfers.add(new Transfer(sent, sender, recipient, senderGroup, recipientGroup, switch (recipient.getKind()) {
            case LOCATION -> "Локація";
            case FLY_POINT -> "Точка вильоту";
            case CREW -> "Екіпаж";
        }));
    }

    public List<Transfer> transfers() { return List.copyOf(transfers); }
    public List<ResourceResponse> products() { return List.copyOf(products); }

    /** Follow all pages in BOTH journal perspectives and compare IDs before flattening items. */
    public List<RelocationResponse> journalOracle() {
        Map<Long, RelocationResponse> sent = new LinkedHashMap<>();
        Map<Long, RelocationResponse> received = new LinkedHashMap<>();
        for (var perspective : RelocationJournalQuery.Perspective.values()) {
            var locationsToRead = transfers.stream().map(t -> perspective == RelocationJournalQuery.Perspective.SENT
                    ? t.sender().getId() : t.recipient().getId()).distinct().toList();
            var target = perspective == RelocationJournalQuery.Perspective.SENT ? sent : received;
            for (var storageId : locationsToRead) {
                for (int page = 0; page < 100; page++) {
                    var rows = relocations.getJournalPage(RelocationJournalQuery.builder().storageId(storageId)
                            .perspective(perspective).page(page).pageSize(2).build(), UserRole.ADMIN);
                    for (var row : rows) {
                        if (transfers.stream().anyMatch(t -> t.relocation().getId().equals(row.getId()))) {
                            assertThat(target.put(row.getId(), row)).as("Journal ID repeated across pages").isNull();
                        }
                    }
                    if (rows.size() < 2) break;
                    assertThat(page).as("Journal pagination must terminate").isLessThan(99);
                }
            }
        }
        var expectedIds = transfers.stream().map(t -> t.relocation().getId()).toList();
        assertThat(sent.keySet()).containsExactlyInAnyOrderElementsOf(expectedIds);
        assertThat(received.keySet()).containsExactlyInAnyOrderElementsOf(expectedIds);
        for (long id : expectedIds) assertThat(received.get(id).getItems()).isEqualTo(sent.get(id).getItems());
        Allure.addAttachment("Journal IDs: sent and received", expectedIds.toString());
        return List.copyOf(sent.values());
    }

    public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        // Finished history is immutable on dev. Clear owned stock and archive its entities;
        // do not attempt to reverse deliveries and then delete seed receipts still in use.
        Allure.addAttachment("Retained completed relocation history",
                created.stream().map(RelocationResponse::getId).toList().toString());
        for (var location : locations) for (var product : products) {
            try { inventory.removeResourceFromStorage(location.getId(), product.getId(), UserRole.ADMIN); }
            catch (Throwable error) { errors.add(error); }
        }
        for (var product : products) {
            try { assertThat(resources.deactivate(UserRole.ADMIN, product.getId()).statusCode()).isBetween(200, 299); }
            catch (Throwable error) { errors.add(error); }
        }
        try { TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages); }
        catch (Throwable error) { errors.add(error); }
        if (!errors.isEmpty()) {
            var failure = new AssertionError("Relocation columns cleanup failed");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
