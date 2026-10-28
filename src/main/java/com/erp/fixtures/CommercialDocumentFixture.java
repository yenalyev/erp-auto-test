package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.PdfTestData;
import com.erp.enums.UserRole;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.TestContext;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Own ledger with two resources; no shared receipt or document is changed. */
public class CommercialDocumentFixture {
    private final ApiExecutor api;
    private final StorageFixture storages;
    private final StorageRegionFixture regions;
    private final ResourceFixture resources;
    private final List<ResourceResponse> createdResources = new ArrayList<>();
    private final List<Long> entryIds = new ArrayList<>();
    private final List<Long> receiptIds = new ArrayList<>();
    private final Set<Long> documentIds = new LinkedHashSet<>();
    public Long storageId;
    public final String date = LocalDate.now().minusDays(1).toString();

    public CommercialDocumentFixture(TestContext context, ApiExecutor api) {
        this.api = api;
        storages = new StorageFixture(context, api);
        regions = new StorageRegionFixture(context, api);
        resources = new ResourceFixture(context, api);
    }

    public void prepare() {
        Long parentId = storages.resolveParentUnit().getId();
        storageId = storages.createChildStorage(parentId, "commercial-doc").getId();
        Long supplierId = storages.createExternalChildStorage(parentId, "commercial-supplier").getId();
        resources.fetchSharedUnit(5);
        resources.fetchSharedResourceCategory();
        for (int i = 0; i < 2; i++) {
            var resource = resources.createUniqueResource("commercial-resource-" + i);
            createdResources.add(resource);
            var entry = api.execute(ApiEndpointDefinition.VIRTUAL_STORAGE_CREATE, UserRole.ADMIN,
                    Map.of("resourceId", resource.getId(), "supplierId", supplierId), storageId);
            ok(entry);
            Long entryId = entry.jsonPath().getLong("id");
            entryIds.add(entryId);
            var receipt = api.execute(ApiEndpointDefinition.VIRTUAL_RECEIPT_CREATE, UserRole.ADMIN,
                    Map.of("amount", i + 2, "receivedDate", date), storageId, entryId);
            ok(receipt);
            receiptIds.add(receipt.jsonPath().getLong("id"));
        }
    }

    public String resourceName(int index) { return createdResources.get(index).getName(); }
    public Long resourceId(int index) { return createdResources.get(index).getId(); }
    public Long receiptId(int index) { return receiptIds.get(index); }

    /** Same JSON part as VirtualStorageCommercialDocumentDialog.handleSave; optional values omitted. */
    public Map<String, Object> request() {
        var body = new LinkedHashMap<String, Object>();
        body.put("number", "AUTO-CD-" + UUID.randomUUID());
        body.put("documentDate", date);
        body.put("receiptIds", List.of(receiptId(0)));
        body.put("lines", new ArrayList<>(List.of(line(0))));
        return body;
    }

    public Map<String, Object> line(int index) {
        var line = new LinkedHashMap<String, Object>();
        line.put("resourceId", resourceId(index));
        line.put("total", 0);
        line.put("documentResourceName", "Ресурс за документом " + index);
        return line;
    }

    public Response create(Map<String, Object> body, byte[] file) {
        var response = api.executeCommercialDocumentCreate(storageId, UserRole.ADMIN, body, file);
        if (response.statusCode() == 200) trackDocument(response.jsonPath().getLong("id"));
        return response;
    }

    public void trackDocument(Long id) { documentIds.add(Objects.requireNonNull(id)); }

    public List<Map<String, Object>> documents() {
        var response = api.execute(ApiEndpointDefinition.COMMERCIAL_DOCUMENT_LIST, UserRole.ADMIN,
                null, storageId);
        ok(response);
        return response.jsonPath().getList("");
    }

    public List<Map<String, Object>> uncovered() {
        var response = api.execute(ApiEndpointDefinition.COMMERCIAL_DOCUMENT_UNCOVERED_RECEIPTS,
                UserRole.ADMIN, null, storageId);
        ok(response);
        return response.jsonPath().getList("");
    }

    public List<Map<String, Object>> ledger() {
        var response = api.execute(ApiEndpointDefinition.VIRTUAL_STORAGE_LIST, UserRole.ADMIN, null, storageId);
        ok(response);
        return response.jsonPath().getList("");
    }

    public Map<String, Object> document(Long id) {
        return documents().stream().filter(d -> ((Number) d.get("id")).longValue() == id)
                .findFirst().orElseThrow(() -> new AssertionError("Document absent: " + id));
    }

    public static byte[] file() { return PdfTestData.document("Commercial document required fields"); }

    public void cleanupDocuments() {
        for (Long id : new ArrayList<>(documentIds)) {
            deleted(api.execute(ApiEndpointDefinition.COMMERCIAL_DOCUMENT_DELETE,
                    UserRole.ADMIN, null, storageId, id));
            documentIds.remove(id);
        }
    }

    public void cleanup() {
        List<Throwable> failures = new ArrayList<>();
        attempt(failures, this::cleanupDocuments);
        for (int i = 0; i < receiptIds.size(); i++) {
            int index = i;
            attempt(failures, () -> deleted(api.execute(ApiEndpointDefinition.VIRTUAL_RECEIPT_DELETE,
                    UserRole.ADMIN, null, new Object[]{storageId, entryIds.get(index), receiptIds.get(index)})));
        }
        for (Long id : entryIds) attempt(failures, () -> ok(api.execute(
                ApiEndpointDefinition.VIRTUAL_STORAGE_ARCHIVE, UserRole.ADMIN, null, storageId, id)));
        for (var resource : createdResources) attempt(failures,
                () -> deleted(resources.deactivate(UserRole.ADMIN, resource.getId())));
        attempt(failures, () -> TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages));
        assertThat(failures).as("Commercial document fixture cleanup").isEmpty();
    }

    private static void ok(Response response) {
        assertThat(response.statusCode()).as("API response: %s", response.asString()).isEqualTo(200);
    }
    private static void deleted(Response response) {
        assertThat(response.statusCode()).as("Cleanup: %s", response.asString()).isIn(200, 204, 404);
    }
    private static void attempt(List<Throwable> failures, Runnable action) {
        try { action.run(); } catch (Throwable failure) { failures.add(failure); }
    }
}
