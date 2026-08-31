package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.UserRole;
import com.erp.models.request.EquipmentAssignmentRequest;
import com.erp.models.request.EquipmentCreateRequest;
import com.erp.models.request.EquipmentRelocationReceiveEditRequest;
import com.erp.models.request.EquipmentRelocationSendEditRequest;
import com.erp.models.request.EquipmentRelocationSendRequest;
import com.erp.models.request.EquipmentRequest;
import com.erp.models.request.EquipmentStatusUpdateRequest;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.enums.RelocationState;
import com.erp.models.response.EquipmentCategoryResponse;
import com.erp.models.response.EquipmentGroupResponse;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.EquipmentSimpleResponse;
import com.erp.models.response.PagedEquipmentGroupResponse;
import com.erp.models.response.PagedEquipmentResponse;
import com.erp.models.response.PagedRelocationResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
public class EquipmentFixture extends BaseFixture {

    public EquipmentFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка обладнання для тестів переміщення")
    public void prepareContext() {
        if (testContext.get(ContextKey.EQUIPMENT_ID) != null) {
            return;
        }
        Long storageId = ConfigProvider.getOwner1StorageId();
        prepareCategoryContext();
        Long categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);

        EquipmentResponse equipment = createEquipmentOnStorage(UserRole.ADMIN, storageId, categoryId);
        testContext.set(ContextKey.EQUIPMENT_ID, equipment.getId());
        log.info("Equipment fixture ready: id={}, storage={}", equipment.getId(), storageId);
    }

    /** Resolves equipment category only — no equipment create (safe for UI batch-create tests). */
    @Step("FIXTURE: категорія обладнання")
    public void prepareCategoryContext() {
        if (testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID) != null) {
            return;
        }
        Long categoryId = resolveOrCreateCategory();
        testContext.set(ContextKey.EQUIPMENT_CATEGORY_ID, categoryId);
        log.info("Equipment category ready: id={}", categoryId);
    }

    public List<String> extractInventoryNumbers(EquipmentGroupResponse group) {
        if (group.getItems() == null) {
            return List.of();
        }
        return group.getItems().stream()
                .map(EquipmentSimpleResponse::getInventoryNumber)
                .filter(inv -> inv != null && !inv.isBlank())
                .toList();
    }

    public EquipmentGroupResponse findGroupByName(UserRole role, Long storageId, String groupName) {
        return getGroupedEquipment(role, storageId, null).stream()
                .filter(g -> groupName.equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Equipment group not found: " + groupName + " on storage " + storageId));
    }

    @Step("API: створити групу обладнання «{groupName}» на складі {storageId}")
    public List<EquipmentResponse> createEquipmentGroup(UserRole role,
                                                        Long storageId,
                                                        Long categoryId,
                                                        String groupName,
                                                        List<String> inventoryNumbers) {
        List<EquipmentRequest> items = inventoryNumbers.stream()
                .map(inv -> EquipmentRequest.builder()
                        .name(groupName)
                        .inventoryNumber(inv)
                        .serialNumber("SN-" + inv)
                        .description("erp-auto-test equipment group item")
                        .categoryId(categoryId)
                        .build())
                .toList();
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .storageId(storageId)
                .senderStorageId(resolveSupplierSenderId())
                .invoiceNumber("INV-EQ-GRP-" + System.currentTimeMillis() % 1_000_000)
                .isPaidByCash(false)
                .items(items)
                .build();
        Response response = apiExecutor.executeEquipmentCreate(request, role);
        validateSuccess(response, "Create equipment group");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EQUIPMENT_POST_CREATE);
        List<EquipmentResponse> created = response.jsonPath().getList(".", EquipmentResponse.class);
        if (created == null || created.size() != inventoryNumbers.size()) {
            throw new IllegalStateException(
                    "Create equipment group: expected " + inventoryNumbers.size()
                            + " items, got " + (created == null ? 0 : created.size()));
        }
        return created;
    }

    /**
     * A sender is mandatory on create unless the target location has an open equipment inventory
     * session ({@code EquipmentValidator}); SUPPLIER is the only always-available allowed type.
     */
    private Long resolveSupplierSenderId() {
        Long cached = testContext.get(ContextKey.EQUIPMENT_SUPPLIER_ID);
        if (cached != null) {
            return cached;
        }
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_SUPPLIER, UserRole.ADMIN);
        validateSuccess(response, "Get SUPPLIER storage for equipment sender");
        List<StorageResponse> suppliers =
                DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        if (suppliers == null || suppliers.isEmpty()) {
            throw new IllegalStateException("No SUPPLIER storage available as equipment sender");
        }
        Long supplierId = suppliers.getFirst().getId();
        testContext.set(ContextKey.EQUIPMENT_SUPPLIER_ID, supplierId);
        return supplierId;
    }

    /**
     * Create without {@code senderStorageId}. Caller must open the equipment-inventory
     * session on {@code storageId} first, otherwise the API returns 400.
     */
    @Step("API: створити обладнання без постачальника на складі {storageId}")
    public EquipmentResponse createEquipmentWithoutSupplier(UserRole role, Long storageId, Long categoryId) {
        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .storageId(storageId)
                .items(List.of(EquipmentRequest.builder()
                        .name("erp-invopen-" + suffix)
                        .inventoryNumber("INV-INVOPEN-" + suffix)
                        .serialNumber("SN-INVOPEN-" + suffix)
                        .description("erp-auto-test inventory equipment")
                        .categoryId(categoryId)
                        .build()))
                .build();
        Response response = apiExecutor.executeEquipmentCreate(request, role);
        validateSuccess(response, "Create equipment without supplier");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EQUIPMENT_POST_CREATE);
        return firstCreatedEquipment(response);
    }

    @Step("API: створити обладнання на складі {storageId}")
    public EquipmentResponse createEquipmentOnStorage(UserRole role, Long storageId, Long categoryId) {
        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .storageId(storageId)
                .senderStorageId(resolveSupplierSenderId())
                .invoiceNumber("INV-EQ-" + suffix)
                .isPaidByCash(false)
                .items(List.of(EquipmentRequest.builder()
                        .name("erp-test-equipment-" + suffix)
                        .inventoryNumber("INV-ERP-" + suffix)
                        .serialNumber("SN-ERP-" + suffix)
                        .description("erp-auto-test equipment")
                        .categoryId(categoryId)
                        .build()))
                .build();
        Response response = apiExecutor.executeEquipmentCreate(request, role);
        validateSuccess(response, "Create equipment");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EQUIPMENT_POST_CREATE);
        return firstCreatedEquipment(response);
    }

    @Step("API: видача обладнання storage→storage")
    public RelocationResponse sendEquipment(UserRole role,
                                            Long fromStorageId,
                                            Long toStorageId,
                                            Long equipmentId) {
        EquipmentRelocationSendRequest request = EquipmentRelocationSendRequest.builder()
                .fromStorageId(fromStorageId)
                .toStorageId(toStorageId)
                .equipmentIds(List.of(equipmentId))
                .date(LocalDate.now())
                .description("erp-auto-test equipment send")
                .sendingPersonName("Test")
                .sendingPersonRank("Сержант")
                .receivingPersonName("Receiver")
                .receivingPersonRank("Лейтенант")
                .build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_RELOCATION_POST_SEND, role, request);
        validateSuccess(response, "Equipment send");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EQUIPMENT_RELOCATION_POST_SEND);
        return response.as(RelocationResponse.class);
    }

    @Step("Отримати статус обладнання id={equipmentId} на складі {storageId}")
    public EquipmentStatus getEquipmentStatus(UserRole role, Long storageId, Long equipmentId) {
        // Prefer GET by id: staging storages often have >50 equipment, so page size=50 misses newly created IDs.
        Response byId = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_GET_BY_ID, role, null, equipmentId);
        if (byId.statusCode() >= 200 && byId.statusCode() < 300) {
            EquipmentResponse equipment = byId.as(EquipmentResponse.class);
            Long actualStorageId = equipment.getStorage() != null ? equipment.getStorage().getId() : null;
            if (!storageId.equals(actualStorageId)) {
                throw new IllegalStateException(
                        "Equipment " + equipmentId + " expected on storage " + storageId
                                + " but is on " + actualStorageId);
            }
            return equipment.getStatus();
        }

        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EQUIPMENT_GET_PAGE,
                role,
                Map.of("storageIds", storageId, "size", 200));
        validateSuccess(response, "Get equipment page for status");
        PagedEquipmentResponse page = response.as(PagedEquipmentResponse.class);
        return page.getContent().stream()
                .filter(e -> equipmentId.equals(e.getId()))
                .map(EquipmentSimpleResponse::getStatus)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Equipment " + equipmentId + " not found"));
    }

    public EquipmentResponse createEquipmentFromSupplier(UserRole role,
                                                         Long recipientStorageId,
                                                         Long supplierId,
                                                         Long categoryId) {
        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .storageId(recipientStorageId)
                .senderStorageId(supplierId)
                .invoiceNumber("INV-EQ-" + suffix)
                .isPaidByCash(false)
                .items(List.of(EquipmentRequest.builder()
                        .name("erp-supplier-eq-" + suffix)
                        .inventoryNumber("INV-SUP-" + suffix)
                        .serialNumber("SN-SUP-" + suffix)
                        .description("erp-auto-test supplier equipment")
                        .categoryId(categoryId)
                        .build()))
                .build();
        Response response = apiExecutor.executeEquipmentCreate(request, role);
        validateSuccess(response, "Create equipment from supplier");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EQUIPMENT_POST_CREATE);
        return firstCreatedEquipment(response);
    }

    public RelocationResponse sendEquipmentToUnit(UserRole role,
                                                  Long fromStorageId,
                                                  Long unitStorageId,
                                                  Long equipmentId) {
        return sendEquipment(role, fromStorageId, unitStorageId, equipmentId);
    }

    public Response sendEquipmentRaw(UserRole role, EquipmentRelocationSendRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_RELOCATION_POST_SEND, role, request);
    }

    public RelocationResponse resolveEquipment(UserRole role,
                                               Long relocationId,
                                               Long storageId,
                                               RelocationState state) {
        RelocationUpdateRequest request = RelocationUpdateRequest.builder()
                .state(state)
                .description("erp-auto-test equipment resolve")
                .build();
        Response response = apiExecutor.executeRelocationResolve(relocationId, storageId, request, role);
        validateSuccess(response, "Resolve equipment relocation");
        return response.as(RelocationResponse.class);
    }

    public Response resolveEquipmentRaw(UserRole role,
                                        Long relocationId,
                                        Long storageId,
                                        RelocationState state) {
        RelocationUpdateRequest request = RelocationUpdateRequest.builder()
                .state(state)
                .description("erp-auto-test equipment resolve")
                .build();
        return apiExecutor.executeRelocationResolve(relocationId, storageId, request, role);
    }

    public void changeEquipmentStatus(UserRole role, Long equipmentId, EquipmentStatus status) {
        EquipmentStatusUpdateRequest request = EquipmentStatusUpdateRequest.builder()
                .status(status)
                .build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_PUT_STATUS, role, request, equipmentId);
        validateSuccess(response, "Change equipment status");
    }

    @Step("API: закріпити обладнання {equipmentId} за співробітником {assigneeId}")
    public EquipmentResponse assignEquipment(UserRole role, Long equipmentId, Long assigneeId) {
        EquipmentAssignmentRequest request = EquipmentAssignmentRequest.builder()
                .assigneeId(assigneeId)
                .note("erp-auto-test assignment")
                .build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_POST_ASSIGNMENT, role, request, equipmentId);
        validateSuccess(response, "Assign equipment");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EQUIPMENT_POST_ASSIGNMENT);
        return response.as(EquipmentResponse.class);
    }

    @Step("API: grouped equipment для storage {storageId}, assigneeId={assigneeId}")
    public List<EquipmentGroupResponse> getGroupedEquipment(UserRole role,
                                                            Long storageId,
                                                            Long assigneeId) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("storageIds", storageId);
        params.put("size", 100);
        params.put("statuses", List.of(
                EquipmentStatus.AVAILABLE.name(),
                EquipmentStatus.ASSIGNED.name(),
                EquipmentStatus.IN_REPAIR.name()));
        if (assigneeId != null) {
            params.put("assigneeId", assigneeId);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EQUIPMENT_GET_GROUPED, role, params);
        validateSuccess(response, "Get grouped equipment");
        PagedEquipmentGroupResponse page = response.as(PagedEquipmentGroupResponse.class);
        return page.getContent() != null ? page.getContent() : List.of();
    }

    public List<String> extractEquipmentNames(List<EquipmentGroupResponse> groups) {
        return groups.stream()
                .map(EquipmentGroupResponse::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    @Step("API: видалення отримання/переміщення обладнання id={relocationId}")
    public void deleteRelocation(UserRole role, Long relocationId, Long storageId) {
        Response response = deleteRelocationRaw(role, relocationId, storageId);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Delete equipment relocation failed: " + response.statusCode()
                    + " " + response.getBody().asString());
        }
    }

    public Response deleteRelocationRaw(UserRole role, Long relocationId, Long storageId) {
        return apiExecutor.executeRelocationDelete(relocationId, storageId, role);
    }

    public Long findEquipmentRelocationId(UserRole role,
                                          Long storageId,
                                          Long equipmentId,
                                          boolean asReceiver) {
        var params = asReceiver
                ? java.util.Map.of("receiverIds", storageId, "size", 50)
                : java.util.Map.of("senderIds", storageId, "size", 50);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE, role, params);
        PagedRelocationResponse page = response.as(PagedRelocationResponse.class);
        return page.getContent().stream()
                .filter(r -> r.getEquipmentItems() != null && r.getEquipmentItems().stream()
                        .anyMatch(e -> equipmentId.equals(e.getId())))
                .map(RelocationResponse::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Equipment relocation not found for equipment " + equipmentId));
    }

    public Long findEquipmentReceiveRelocationId(UserRole role, Long recipientStorageId, Long equipmentId) {
        return findEquipmentRelocationId(role, recipientStorageId, equipmentId, true);
    }

    public RelocationResponse editEquipmentReceive(UserRole role,
                                                   Long relocationId,
                                                   Long storageId,
                                                   EquipmentRelocationReceiveEditRequest request) {
        Response response = editEquipmentReceiveRaw(role, relocationId, storageId, request);
        validateSuccess(response, "Edit equipment receive");
        return response.as(RelocationResponse.class);
    }

    public Response editEquipmentReceiveRaw(UserRole role,
                                            Long relocationId,
                                            Long storageId,
                                            EquipmentRelocationReceiveEditRequest request) {
        return apiExecutor.executeEquipmentRelocationUpdateReceive(
                relocationId, storageId, request, role);
    }

    public RelocationResponse editEquipmentSend(UserRole role,
                                                Long relocationId,
                                                Long storageId,
                                                EquipmentRelocationSendEditRequest request) {
        Response response = editEquipmentSendRaw(role, relocationId, storageId, request);
        validateSuccess(response, "Edit equipment send");
        return response.as(RelocationResponse.class);
    }

    public Response editEquipmentSendRaw(UserRole role,
                                         Long relocationId,
                                         Long storageId,
                                         EquipmentRelocationSendEditRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_RELOCATION_PUT_UPDATE_SEND,
                role, request, relocationId, storageId);
    }

    public Long supplierId() {
        return testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
    }

    @Step("API: ім'я SUPPLIER-локації id={supplierId}")
    public String resolveSupplierName(Long supplierId) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_SUPPLIER, UserRole.ADMIN);
        List<StorageResponse> suppliers = DatabaseIntegrityValidator.extractList(
                response, StorageResponse.class);
        return suppliers.stream()
                .filter(s -> supplierId.equals(s.getId()))
                .map(StorageResponse::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Supplier name not found for id=" + supplierId));
    }

    @Step("API: ім'я категорії обладнання id={categoryId}")
    public String resolveCategoryName(Long categoryId) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_GET_ALL, UserRole.ADMIN);
        List<EquipmentCategoryResponse> categories = DatabaseIntegrityValidator.extractList(
                response, EquipmentCategoryResponse.class);
        return categories.stream()
                .filter(c -> categoryId.equals(c.getId()))
                .map(EquipmentCategoryResponse::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Category name not found for id=" + categoryId));
    }

    /**
     * Finds a receive relocation on {@code storageId} whose equipmentItems contain all given names.
     */
    @Step("API: знайти переміщення з обладнанням {names}")
    public RelocationResponse findRelocationContainingEquipmentNames(UserRole role,
                                                                     Long storageId,
                                                                     List<String> names) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE,
                role,
                Map.of("receiverIds", storageId, "size", 50));
        validateSuccess(response, "Get relocations for equipment batch");
        PagedRelocationResponse page = response.as(PagedRelocationResponse.class);
        return page.getContent().stream()
                .filter(r -> r.getEquipmentItems() != null && !r.getEquipmentItems().isEmpty())
                .filter(r -> {
                    List<String> itemNames = r.getEquipmentItems().stream()
                            .map(EquipmentSimpleResponse::getName)
                            .filter(n -> n != null && !n.isBlank())
                            .toList();
                    return itemNames.containsAll(names);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No relocation containing equipment names " + names + " on storage " + storageId));
    }

    private Long resolveOrCreateCategory() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_GET_ALL, UserRole.ADMIN);
        List<EquipmentCategoryResponse> categories = DatabaseIntegrityValidator.extractList(
                response, EquipmentCategoryResponse.class);
        if (categories != null && !categories.isEmpty()) {
            return categories.getFirst().getId();
        }
        throw new IllegalStateException(
                "No equipment categories on env — create at least one category for equipment relocation tests");
    }

    /** POST /equipment returns HTTP 201 + {@code List<EquipmentResponse>}. */
    private static EquipmentResponse firstCreatedEquipment(Response response) {
        List<EquipmentResponse> created = response.jsonPath().getList(".", EquipmentResponse.class);
        if (created == null || created.isEmpty() || created.getFirst() == null) {
            throw new IllegalStateException(
                    "Create equipment: expected non-empty EquipmentResponse list, body="
                            + response.body().asString());
        }
        return created.getFirst();
    }
}
