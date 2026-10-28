package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.models.request.EquipmentCategoryRequest;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import io.restassured.response.Response;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Owns all equipment and relocations; never modifies pre-existing stock. */
public class EquipmentModelArchiveFixture extends BaseFixture {
    public record Sample(Long modelId, Long equipmentId, String name) {}
    private final EquipmentFixture equipment;
    private final StorageFixture storages;
    private final List<Sample> samples = new ArrayList<>();
    private final List<Long> receipts = new ArrayList<>(), sends = new ArrayList<>();
    private final Set<Long> movedEquipment = new HashSet<>();
    public final String marker = "eqarchive-" + UUID.randomUUID().toString().substring(0, 8);
    public Long storageId, destinationId, categoryId;

    public EquipmentModelArchiveFixture(TestContext context, ApiExecutor executor) {
        super(context, executor);
        equipment = new EquipmentFixture(context, executor);
        storages = new StorageFixture(context, executor);
    }

    public void prepare() {
        storageId = storages.createChildStorage(marker + "-source-").getId();
        destinationId = storages.createChildStorage(marker + "-target-").getId();
        Response response = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_POST_CREATE,
                UserRole.ADMIN, EquipmentCategoryRequest.builder().name(marker).build());
        validateSuccess(response, "Create owned archive category");
        categoryId = response.as(EquipmentCategoryResponse.class).getId();
    }

    public Sample create(String suffix, EquipmentStatus status) {
        String name = marker + " " + suffix;
        var item = equipment.createEquipmentGroup(UserRole.ADMIN, storageId, categoryId, name,
                List.of("EA-" + UUID.randomUUID())).getFirst();
        receipts.add(equipment.findEquipmentReceiveRelocationId(UserRole.ADMIN, storageId, item.getId()));
        var model = list(Map.of("modelName", name)).getContent().stream()
                .filter(m -> name.equals(m.getName())).findFirst().orElseThrow();
        var sample = new Sample(model.getId(), item.getId(), name);
        samples.add(sample);
        if (status != EquipmentStatus.AVAILABLE) retireOrChange(sample, status);
        return sample;
    }

    public void retireOrChange(Sample sample, EquipmentStatus status) {
        equipment.changeEquipmentStatus(UserRole.ADMIN, sample.equipmentId(), status);
    }

    public Long send(Sample sample) {
        Long id = equipment.sendEquipment(UserRole.ADMIN, storageId, destinationId, sample.equipmentId()).getId();
        sends.add(id);
        movedEquipment.add(sample.equipmentId());
        assertThat(equipment.getEquipmentStatus(UserRole.ADMIN, storageId, sample.equipmentId()))
                .isEqualTo(EquipmentStatus.IN_TRANSIT);
        return id;
    }

    public Response archive(Sample sample) {
        return apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_MODEL_ARCHIVE, UserRole.ADMIN, null, sample.modelId());
    }

    public EquipmentModelPageResponse list(Map<String, ?> filters) {
        Map<String, Object> query = new LinkedHashMap<>(Map.of("modelName", marker, "size", 100));
        query.putAll(filters);
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                UserRole.ADMIN, query);
        assertThat(response.statusCode()).as("Dictionary response: %s", response.asString()).isEqualTo(200);
        return response.as(EquipmentModelPageResponse.class);
    }

    public EquipmentModelResponse model(Sample sample) {
        return list(Map.of("modelName", sample.name())).getContent().stream()
                .filter(m -> m.getId().equals(sample.modelId())).findFirst().orElseThrow();
    }

    public List<String> suggestions(String query) {
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_SUGGESTIONS,
                UserRole.ADMIN, Map.of("name", query, "limit", 100));
        assertThat(response.statusCode()).as("Suggestions response: %s", response.asString()).isEqualTo(200);
        return response.jsonPath().getList("name", String.class);
    }

    public EquipmentResponse equipment(Sample sample) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_GET_BY_ID,
                UserRole.ADMIN, null, sample.equipmentId());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.as(EquipmentResponse.class);
    }

    public void cleanup() {
        List<Throwable> errors = new ArrayList<>();
        // CREATED internal sends cannot be deleted on dev. Settle them before retiring their equipment.
        for (Long id : sends) attempt(errors, () -> assertThat(equipment.resolveEquipment(
                UserRole.ADMIN, id, destinationId, RelocationState.FINISHED).getState()).isEqualTo(RelocationState.FINISHED));
        for (Sample sample : samples) attempt(errors, () -> {
            if (equipment(sample).getStatus() != EquipmentStatus.RETIRED) retireOrChange(sample, EquipmentStatus.RETIRED);
            assertThat(equipment(sample).getStatus()).isEqualTo(EquipmentStatus.RETIRED);
        });
        // Preserve receipt anchors for settled internal movements; delete independent receipts.
        for (int index = samples.size() - 1; index >= 0; index--) {
            if (!movedEquipment.contains(samples.get(index).equipmentId())) {
                Long receipt = receipts.get(index);
                attempt(errors, () -> equipment.deleteRelocation(UserRole.ADMIN, receipt, storageId));
            }
        }
        // Receipt deletion retains the model dictionary row. Archive only models owned by this run.
        for (Sample sample : samples) attempt(errors, () -> {
            if ("ACTIVE".equals(model(sample).getStatus())) assertThat(archive(sample).statusCode()).isEqualTo(204);
            assertThat(model(sample).getStatus()).isEqualTo("ARCHIVED");
            assertThat(model(sample).getEquipmentCount()).isZero();
        });
        if (categoryId != null && sends.isEmpty()) attempt(errors, () -> validateSuccess(apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_CATEGORY_DELETE, UserRole.ADMIN, null, categoryId), "Delete owned category"));
        attempt(errors, () -> storages.deactivateTrackedStorages(UserRole.ADMIN));
        io.qameta.allure.Allure.addAttachment("Equipment archive cleanup", "marker=" + marker
                + "; settled immutable internal sends=" + sends + "; retired historical equipment=" + movedEquipment
                + "; retained historical category=" + (sends.isEmpty() ? null : categoryId)
                + "; all models ARCHIVED with equipmentCount=0; own locations deactivated; errors=" + errors.size());
        if (!errors.isEmpty()) {
            AssertionError failure = new AssertionError("Equipment archive cleanup failed; marker=" + marker);
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    /** Explicit recovery manifest from this task; verifies ownership before settling previous test sends. */
    public static void recoverOwnedTransit(TestContext context, ApiExecutor executor, String manifestPath) {
        try {
            var entries = new com.fasterxml.jackson.databind.ObjectMapper().readTree(new java.io.File(manifestPath));
            assertThat(System.getProperty("env")).isEqualTo("dev");
            if (entries.isObject()) {
                recoverPendingStock(context, executor, manifestPath, entries);
                return;
            }
            for (var entry : entries) {
                String marker = entry.get("marker").asText();
                assertThat(marker).matches("eqarchive-[0-9a-f]{8}");
                Long source = entry.get("source").asLong(), target = entry.get("target").asLong();
                Long itemId = entry.get("equipment").asLong(), modelId = entry.get("model").asLong();
                Long sendId = entry.get("send").asLong();
                var items = new EquipmentFixture(context, executor);
                var locations = new StorageFixture(context, executor);
                for (Long id : List.of(source, target)) {
                    var response = executor.execute(ApiEndpointDefinition.STORAGE_GET_BY_ID, UserRole.ADMIN, null, id);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.jsonPath().getString("name")).contains(marker);
                    if (!response.jsonPath().getBoolean("active"))
                        validateRecovery(locations.unarchive(UserRole.ADMIN, id));
                }
                var item = executor.execute(ApiEndpointDefinition.EQUIPMENT_GET_BY_ID, UserRole.ADMIN, null, itemId);
                assertThat(item.statusCode()).isEqualTo(200);
                assertThat(item.jsonPath().getString("name")).startsWith(marker + " ");
                var sends = executor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_PAGE,
                        UserRole.ADMIN, Map.of("senderIds", source, "size", 100)).as(PagedRelocationResponse.class);
                var send = sends.getContent().stream().filter(r -> r.getId().equals(sendId)).findFirst().orElseThrow();
                assertThat(send.getRecipient().getId()).isEqualTo(target);
                assertThat(send.getEquipmentItems()).extracting(EquipmentSimpleResponse::getId).containsExactly(itemId);
                if (send.getState() == RelocationState.CREATED)
                    items.resolveEquipment(UserRole.ADMIN, sendId, target, RelocationState.FINISHED);
                else assertThat(send.getState()).isEqualTo(RelocationState.FINISHED);
                if (item.jsonPath().getString("status").equals("IN_TRANSIT")
                        || item.jsonPath().getString("status").equals("AVAILABLE"))
                    items.changeEquipmentStatus(UserRole.ADMIN, itemId, EquipmentStatus.RETIRED);
                var models = executor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                        UserRole.ADMIN, Map.of("modelName", marker, "size", 100)).as(EquipmentModelPageResponse.class);
                var model = models.getContent().stream().filter(m -> m.getId().equals(modelId)).findFirst().orElseThrow();
                assertThat(model.getEquipmentCount()).isZero();
                if (model.getStatus().equals("ACTIVE")) assertThat(executor.execute(
                        ApiEndpointDefinition.EQUIPMENT_MODEL_ARCHIVE, UserRole.ADMIN, null, modelId).statusCode()).isEqualTo(204);
                // These IDs belong to the previous suite, so its registry is no longer in memory.
                // Recovery is limited to the explicit manifest and ownership checks above.
                validateRecovery(locations.deactivate(UserRole.ADMIN, source));
                validateRecovery(locations.deactivate(UserRole.ADMIN, target));
                io.qameta.allure.Allure.addAttachment("Recovered previous owned transit", entry.toString());
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read recovery manifest", error); }
    }

    /** Recover only the explicit owned stock left by an interrupted suite, with readback evidence. */
    private static void recoverPendingStock(TestContext context, ApiExecutor executor, String manifestPath,
                                           com.fasterxml.jackson.databind.JsonNode manifest) throws java.io.IOException {
        assertThat(manifest.get("environment").asText()).isEqualTo("dev");
        String ownedMarker = manifest.get("marker").asText();
        assertThat(ownedMarker).matches("eqarchive-[0-9a-f]{8}");
        assertThat(manifest.get("activeSends").size()).isZero();
        Long source = manifest.get("source").asLong(), target = manifest.get("target").asLong();
        Long category = manifest.get("category").asLong();
        var items = new EquipmentFixture(context, executor);
        var locations = new StorageFixture(context, executor);
        var receipts = new LinkedHashMap<Long, Long>();
        // Validate every manifest entity before the first mutation.
        for (Long id : List.of(source, target)) {
            var response = executor.execute(ApiEndpointDefinition.STORAGE_GET_BY_ID, UserRole.ADMIN, null, id);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getString("name")).contains(ownedMarker);
        }
        var modelResponse = executor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                UserRole.ADMIN, Map.of("modelName", ownedMarker, "size", 100));
        assertThat(modelResponse.statusCode()).isEqualTo(200);
        var models = modelResponse.as(EquipmentModelPageResponse.class).getContent();
        for (var entry : manifest.get("items")) {
            Long itemId = entry.get("equipment").asLong(), modelId = entry.get("model").asLong();
            String expectedName = entry.get("name").asText();
            assertThat(expectedName).startsWith(ownedMarker + " ");
            var model = models.stream().filter(m -> m.getId().equals(modelId)).findFirst().orElseThrow();
            assertThat(model.getName()).isEqualTo(expectedName);
            var response = executor.execute(ApiEndpointDefinition.EQUIPMENT_GET_BY_ID, UserRole.ADMIN, null, itemId);
            assertThat(response.statusCode()).isIn(200, 404);
            if (response.statusCode() == 200) {
                var item = response.as(EquipmentResponse.class);
                assertThat(item.getName()).isEqualTo(expectedName);
                assertThat(item.getCategory().getId()).isEqualTo(category);
                assertThat(item.getStorage().getId()).isEqualTo(source);
                assertThat(item.getStatus()).isNotEqualTo(EquipmentStatus.IN_TRANSIT);
                receipts.put(itemId, items.findEquipmentReceiveRelocationId(UserRole.ADMIN, source, itemId));
            }
        }
        var categoryResponse = executor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_GET_BY_ID,
                UserRole.ADMIN, null, category);
        assertThat(categoryResponse.statusCode()).isIn(200, 404);
        if (categoryResponse.statusCode() == 200)
            assertThat(categoryResponse.jsonPath().getString("name")).isEqualTo(ownedMarker);
        for (var receipt : receipts.entrySet()) {
            var response = executor.execute(ApiEndpointDefinition.EQUIPMENT_GET_BY_ID,
                    UserRole.ADMIN, null, receipt.getKey());
            assertThat(response.statusCode()).isEqualTo(200);
            if (response.as(EquipmentResponse.class).getStatus() != EquipmentStatus.RETIRED)
                items.changeEquipmentStatus(UserRole.ADMIN, receipt.getKey(), EquipmentStatus.RETIRED);
            items.deleteRelocation(UserRole.ADMIN, receipt.getValue(), source);
            assertThat(executor.execute(ApiEndpointDefinition.EQUIPMENT_GET_BY_ID,
                    UserRole.ADMIN, null, receipt.getKey()).statusCode()).isEqualTo(404);
        }
        for (var model : models) {
            if ("ACTIVE".equals(model.getStatus())) assertThat(executor.execute(
                    ApiEndpointDefinition.EQUIPMENT_MODEL_ARCHIVE, UserRole.ADMIN, null, model.getId()).statusCode()).isEqualTo(204);
        }
        var verified = executor.executeWithQueryParams(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE,
                UserRole.ADMIN, Map.of("modelName", ownedMarker, "size", 100));
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(verified.as(EquipmentModelPageResponse.class).getContent()).hasSize(manifest.get("items").size())
                .allSatisfy(m -> { assertThat(m.getStatus()).isEqualTo("ARCHIVED"); assertThat(m.getEquipmentCount()).isZero(); });
        if (categoryResponse.statusCode() == 200) validateRecovery(executor.execute(
                ApiEndpointDefinition.EQUIPMENT_CATEGORY_DELETE, UserRole.ADMIN, null, category));
        assertThat(executor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_GET_BY_ID,
                UserRole.ADMIN, null, category).statusCode()).isEqualTo(404);
        for (Long id : List.of(source, target)) {
            var response = executor.execute(ApiEndpointDefinition.STORAGE_GET_BY_ID, UserRole.ADMIN, null, id);
            assertThat(response.statusCode()).isEqualTo(200);
            if (response.jsonPath().getBoolean("active")) validateRecovery(locations.deactivate(UserRole.ADMIN, id));
            response = executor.execute(ApiEndpointDefinition.STORAGE_GET_BY_ID, UserRole.ADMIN, null, id);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("active")).isFalse();
        }
        var completed = ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).deepCopy();
        completed.put("status", "COMPLETED");
        completed.put("completedAt", java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Kyiv")).toString());
        completed.put("equipmentDeleted", receipts.size());
        completed.put("modelsArchived", models.size());
        completed.put("categoryDeleted", true);
        completed.put("locationsDeactivated", true);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter();
        var path = java.nio.file.Path.of(manifestPath);
        mapper.writeValue(path.resolveSibling("equipment-model-archive-cleanup-recovery-result.json").toFile(), completed);
        mapper.writeValue(path.toFile(), completed);
        io.qameta.allure.Allure.addAttachment("Recovered previous owned stock", completed.toString());
    }

    private static void validateRecovery(Response response) { assertThat(response.statusCode()).isBetween(200, 299); }

    private void attempt(List<Throwable> errors, Runnable action) {
        try { action.run(); } catch (RuntimeException | AssertionError error) { errors.add(error); }
    }
}
