package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Three owned locations form A -> B -> C -> A; each side has a positive and two controls. */
public class RelocationSidebarFixture {
    private final StorageFixture storages;
    private final StorageRegionFixture regions;
    private final ResourceFixture resources;
    private final RelocationFixture relocations;
    private final InventoryFixture inventory;
    public final String marker = "rel-sidebar-" + UUID.randomUUID().toString().substring(0, 8);
    public final List<StorageResponse> locations = new ArrayList<>();
    public final List<RelocationResponse> transfers = new ArrayList<>();
    private ResourceResponse resource;

    public RelocationSidebarFixture(TestContext context, ApiExecutor executor) {
        storages = new StorageFixture(context, executor);
        regions = new StorageRegionFixture(context, executor);
        resources = new ResourceFixture(context, executor);
        relocations = new RelocationFixture(context, executor);
        inventory = new InventoryFixture(context, executor);
    }

    @Step("Arrange: три власні локації та завершені передачі A → B → C → A")
    public void prepare() {
        for (String suffix : List.of("A", "B", "C")) {
            locations.add(storages.createChildStorage(marker + "-" + suffix + "-"));
        }
        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        resource = resources.createUniqueResource(marker + "-resource-");
        relocations.seedBatchOnStorage(locations.getFirst().getId(), resource.getId(), 10, marker + "-seed");
        for (int i = 0; i < locations.size(); i++) {
            StorageResponse sender = locations.get(i);
            StorageResponse recipient = locations.get((i + 1) % locations.size());
            RelocationResponse sent = relocations.createSendWithDescription(UserRole.ADMIN,
                    sender.getId(), recipient.getId(), resource.getId(), 3 - i, transferMarker(i));
            transfers.add(sent); // Track before resolve, including an interrupted arrange.
            RelocationResponse finished = relocations.resolve(UserRole.ADMIN, sent.getId(),
                    recipient.getId(), RelocationState.FINISHED, transferMarker(i));
            sent.setState(finished.getState());
            assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);
        }
    }

    public String transferMarker(int index) { return marker + "-transfer-" + index; }

    public List<String> expectedMarkers(String side, Long locationId) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < transfers.size(); i++) {
            RelocationResponse transfer = transfers.get(i);
            Long id = side.equals("Від кого") ? transfer.getSender().getId() : transfer.getRecipient().getId();
            if (locationId == null || locationId.equals(id)) result.add(transferMarker(i));
        }
        return result;
    }

    @Step("Cleanup: завершити власні передачі, прибрати залишки й архівувати ресурс та локації")
    public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        for (RelocationResponse transfer : transfers) attempt(errors, () -> {
            if (transfer.getState() == RelocationState.CREATED) {
                relocations.resolve(UserRole.ADMIN, transfer.getId(), transfer.getRecipient().getId(),
                        RelocationState.FINISHED);
            }
        });
        if (resource != null) {
            for (StorageResponse location : locations) attempt(errors, () ->
                    inventory.removeResourceFromStorage(location.getId(), resource.getId(), UserRole.ADMIN));
            attempt(errors, () -> assertThat(resources.deactivate(UserRole.ADMIN, resource.getId()).statusCode())
                    .isBetween(200, 299));
        }
        attempt(errors, () -> TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages));
        Allure.addAttachment("Retained completed transfers", transfers.stream().map(RelocationResponse::getId).toList().toString());
        if (!errors.isEmpty()) {
            AssertionError failure = new AssertionError("Relocation sidebar cleanup failed");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (Throwable error) { errors.add(error); }
    }
}
