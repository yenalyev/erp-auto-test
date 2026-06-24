package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RelocationFixture extends BaseFixture {

    private static final double DEFAULT_SEED_STOCK = 200.0;

    public RelocationFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка середовища для тестів переміщень")
    public void prepareContext() {
        if (testContext.get(ContextKey.RELOCATION_RESOURCE_ID) != null) {
            return;
        }
        fetchSharedUnit(3);
        fetchSharedResourceCategory();
        setupSharedResourceList(3);

        Long owner1Storage = ConfigProvider.getOwner1StorageId();
        Long owner2Storage = ConfigProvider.getOwner2StorageId();
        testContext.set(ContextKey.OWNER_1_STORAGE_ID, owner1Storage);
        testContext.set(ContextKey.OWNER_2_STORAGE_ID, owner2Storage);

        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        Long resourceId = resources.getFirst().getId();
        testContext.set(ContextKey.RELOCATION_RESOURCE_ID, resourceId);

        Long supplierId = RelocationStockSeeder.resolveSupplierStorageId(apiExecutor, UserRole.OWNER_1);
        testContext.set(ContextKey.RELOCATION_SUPPLIER_ID, supplierId);

        Long unitId = resolveUnitStorageId(UserRole.ADMIN);
        testContext.set(ContextKey.RELOCATION_UNIT_STORAGE_ID, unitId);

        ensureStock(owner1Storage, resourceId, DEFAULT_SEED_STOCK);
        log.info("Relocation fixture ready: resource={}, supplier={}, unit={}", resourceId, supplierId, unitId);
    }

    @Step("API: поповнити залишок ресурсу {resourceId} на складі {storageId}")
    public void ensureStock(Long storageId, Long resourceId, double minAmount) {
        double current = getResourceStock(storageId, resourceId, UserRole.OWNER_1);
        if (current < minAmount) {
            double toSeed = minAmount - current + 50;
            RelocationStockSeeder.receiveFromSupplier(
                    apiExecutor, UserRole.OWNER_1, storageId, Map.of(resourceId, toSeed));
        }
    }

    @Step("API: seed batch {batchNumber} на складі {storageId}")
    public void seedBatchOnStorage(Long storageId,
                                 Long resourceId,
                                 double amount,
                                 String batchNumber) {
        Long supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        RelocationInputRequest request = RelocationDataFactory.buildReceiveRequest(
                supplierId, storageId, resourceId, amount, batchNumber);
        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        validateSuccess(response, "Seed batch via receive");
    }

    @Step("API: зовнішнє отримання SUPPLIER→storage, {amount} од., партія {batchNumber}")
    public RelocationResponse createExternalReceive(UserRole role,
                                                    Long recipientId,
                                                    Long resourceId,
                                                    double amount,
                                                    String batchNumber) {
        Long supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        RelocationInputRequest request = RelocationDataFactory.buildReceiveRequest(
                supplierId, recipientId, resourceId, amount, batchNumber);
        Response response = apiExecutor.executeRelocationReceive(request, role);
        validateSuccess(response, "External receive");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_POST_RECEIVE);
        RelocationResponse relocation = response.as(RelocationResponse.class);
        testContext.set(ContextKey.RELOCATION_ID, relocation.getId());
        return relocation;
    }

    @Step("API: видача storage→storage, {amount} од.")
    public RelocationResponse createSend(UserRole role,
                                         Long senderId,
                                         Long recipientId,
                                         Long resourceId,
                                         double amount) {
        return createSendWithDescription(role, senderId, recipientId, resourceId, amount, null);
    }

    @Step("API: видача з маркером у примітках")
    public RelocationResponse createSendWithDescription(UserRole role,
                                                        Long senderId,
                                                        Long recipientId,
                                                        Long resourceId,
                                                        double amount,
                                                        String description) {
        RelocationOutputRequest request = description != null
                ? RelocationDataFactory.buildSendRequest(
                senderId, recipientId, resourceId, amount, description)
                : RelocationDataFactory.buildSendRequest(
                senderId, recipientId, resourceId, amount);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, role, request);
        validateSuccess(response, "Send relocation");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_POST_SEND);
        RelocationResponse relocation = response.as(RelocationResponse.class);
        testContext.set(ContextKey.RELOCATION_ID, relocation.getId());
        return relocation;
    }

    @Step("API: видача з партією {batchNumber}")
    public RelocationResponse createSendWithBatch(UserRole role,
                                                  Long senderId,
                                                  Long recipientId,
                                                  Long resourceId,
                                                  double amount,
                                                  String batchNumber,
                                                  boolean isProduced) {
        RelocationOutputRequest request = RelocationDataFactory.buildSendWithBatch(
                senderId, recipientId, resourceId, amount, batchNumber, isProduced);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, role, request);
        validateSuccess(response, "Send with batch");
        return response.as(RelocationResponse.class);
    }

    @Step("API: resolve relocation {relocationId} → {state}")
    public RelocationResponse resolve(UserRole role,
                                      Long relocationId,
                                      Long storageId,
                                      RelocationState state) {
        return resolve(role, relocationId, storageId, state, "erp-auto-test resolve");
    }

    public RelocationResponse resolve(UserRole role,
                                      Long relocationId,
                                      Long storageId,
                                      RelocationState state,
                                      String description) {
        RelocationUpdateRequest request = RelocationUpdateRequest.builder()
                .state(state)
                .description(description)
                .build();
        Response response = apiExecutor.executeRelocationResolve(relocationId, storageId, request, role);
        validateSuccess(response, "Resolve relocation");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_PUT_RESOLVE);
        return response.as(RelocationResponse.class);
    }

    @Step("API: редагування зовнішнього receive id={relocationId} роллю {role}")
    public RelocationResponse editExternalReceive(UserRole role,
                                                  Long relocationId,
                                                  Long storageId,
                                                  RelocationInputEditRequest editRequest) {
        Response response = apiExecutor.executeRelocationUpdateReceive(
                relocationId, storageId, editRequest, role);
        validateSuccess(response, "Edit external receive");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_PUT_UPDATE_RECEIVE);
        return response.as(RelocationResponse.class);
    }

    @Step("API: редагування send id={relocationId}")
    public RelocationResponse editSend(UserRole role,
                                       Long relocationId,
                                       Long storageId,
                                       RelocationOutputEditRequest editRequest) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_PUT_UPDATE_SEND,
                role, editRequest, relocationId, storageId);
        validateSuccess(response, "Edit send");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_PUT_UPDATE_SEND);
        return response.as(RelocationResponse.class);
    }

    @Step("API: видалення relocation id={relocationId} роллю {role}")
    public void deleteRelocation(UserRole role, Long relocationId, Long storageId) {
        Response response = deleteRelocationRaw(role, relocationId, storageId);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Delete relocation failed: " + response.statusCode()
                    + " " + response.getBody().asString());
        }
    }

    public Response deleteRelocationRaw(UserRole role, Long relocationId, Long storageId) {
        return apiExecutor.executeRelocationDelete(relocationId, storageId, role);
    }

    public Response sendRaw(UserRole role, RelocationOutputRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, role, request);
    }

    public Response resolveRaw(UserRole role,
                               Long relocationId,
                               Long storageId,
                               RelocationState state) {
        RelocationUpdateRequest request = RelocationUpdateRequest.builder()
                .state(state)
                .description("erp-auto-test resolve")
                .build();
        return apiExecutor.executeRelocationResolve(relocationId, storageId, request, role);
    }

    public Response editSendRaw(UserRole role,
                                Long relocationId,
                                Long storageId,
                                RelocationOutputEditRequest editRequest) {
        return apiExecutor.execute(
                ApiEndpointDefinition.RELOCATION_PUT_UPDATE_SEND,
                role, editRequest, relocationId, storageId);
    }

    public Response editReceiveRaw(UserRole role,
                                   Long relocationId,
                                   Long storageId,
                                   RelocationInputEditRequest editRequest) {
        return apiExecutor.executeRelocationUpdateReceive(
                relocationId, storageId, editRequest, role);
    }

    @Step("API: CANCELLED → RETURNED lifecycle")
    public RelocationResponse cancelThenReturn(UserRole senderRole,
                                               UserRole recipientRole,
                                               Long senderId,
                                               Long recipientId,
                                               Long resourceId,
                                               double amount) {
        RelocationResponse sent = createSend(senderRole, senderId, recipientId, resourceId, amount);
        resolve(recipientRole, sent.getId(), recipientId, RelocationState.CANCELLED);
        return resolve(senderRole, sent.getId(), senderId, RelocationState.RETURNED);
    }

    public List<Long> listSupplierIds(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_SUPPLIER, role);
        List<StorageResponse> storages = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        return storages.stream()
                .filter(s -> s != null && s.getId() != null)
                .map(StorageResponse::getId)
                .toList();
    }

    public Long getSharedCategoryId() {
        return testContext.get(ContextKey.SHARED_RESOURCE_CATEGORY_ID);
    }

    public String getSharedCategoryName() {
        Long categoryId = getSharedCategoryId();
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN);
        List<ResourceCategoryResponse> categories =
                DatabaseIntegrityValidator.extractList(response, ResourceCategoryResponse.class);
        return categories.stream()
                .filter(c -> categoryId.equals(c.getId()))
                .map(ResourceCategoryResponse::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Category not found for id=" + categoryId));
    }

    @Step("API: GET totalElements журналу переміщень")
    public long getJournalTotalElements(RelocationJournalQuery query, UserRole role) {
        Response response = getJournalPageResponse(query, role);
        validateSuccess(response, "Get relocation journal total elements");
        return DatabaseIntegrityValidator.extractPageTotalElements(response);
    }

    @Step("API: GET журнал переміщень (сторінка {query.page}, size={query.pageSize})")
    public List<RelocationResponse> getJournalPage(RelocationJournalQuery query, UserRole role) {
        Response response = getJournalPageResponse(query, role);
        validateSuccess(response, "Get relocation journal page");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_GET_PAGE);
        return DatabaseIntegrityValidator.extractList(response, RelocationResponse.class);
    }

    public Response getJournalPageResponse(RelocationJournalQuery query, UserRole role) {
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE,
                role,
                query.toQueryParams());
    }

    public Long secondResourceId() {
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        if (resources == null || resources.size() < 2) {
            throw new IllegalStateException("Need at least 2 shared resources for multi-item tests");
        }
        return resources.get(1).getId();
    }

    public double getResourceStock(Long storageId, Long resourceId, UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET, role, String.valueOf(storageId));
        List<com.erp.models.response.StorageItemResponse> items =
                DatabaseIntegrityValidator.extractList(response, com.erp.models.response.StorageItemResponse.class);
        return items.stream()
                .filter(i -> i.getResource() != null && resourceId.equals(i.getResource().getId()))
                .mapToDouble(i -> i.getAmount() != null ? i.getAmount() : 0.0)
                .findFirst()
                .orElse(0.0);
    }

    @Step("Знайти UNIT storage")
    public Long resolveUnitStorageId(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, role);
        List<StorageResponse> storages = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        return storages.stream()
                .filter(s -> "UNIT".equalsIgnoreCase(s.getType()))
                .map(StorageResponse::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No UNIT storage found for relocation tests"));
    }

    public Set<Long> trackedResource(Long resourceId) {
        return Set.of(resourceId);
    }
}
