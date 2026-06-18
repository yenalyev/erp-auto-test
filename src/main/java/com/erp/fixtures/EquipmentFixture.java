package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.FakerProvider;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.UserRole;
import com.erp.models.request.EquipmentRelocationReceiveEditRequest;
import com.erp.models.request.EquipmentRelocationSendEditRequest;
import com.erp.models.request.EquipmentRelocationSendRequest;
import com.erp.models.request.EquipmentRequest;
import com.erp.models.request.EquipmentStatusUpdateRequest;
import com.erp.models.request.RelocationUpdateRequest;
import com.erp.enums.RelocationState;
import com.erp.models.response.EquipmentCategoryResponse;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.EquipmentSimpleResponse;
import com.erp.models.response.PagedEquipmentResponse;
import com.erp.models.response.PagedRelocationResponse;
import com.erp.models.response.RelocationResponse;
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
        Long categoryId = resolveOrCreateCategory();
        testContext.set(ContextKey.EQUIPMENT_CATEGORY_ID, categoryId);

        EquipmentResponse equipment = createEquipmentOnStorage(UserRole.ADMIN, storageId, categoryId);
        testContext.set(ContextKey.EQUIPMENT_ID, equipment.getId());
        log.info("Equipment fixture ready: id={}, storage={}", equipment.getId(), storageId);
    }

    @Step("API: створити обладнання на складі {storageId}")
    public EquipmentResponse createEquipmentOnStorage(UserRole role, Long storageId, Long categoryId) {
        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        EquipmentRequest request = EquipmentRequest.builder()
                .name("erp-test-equipment-" + suffix)
                .inventoryNumber("INV-ERP-" + suffix)
                .serialNumber("SN-ERP-" + suffix)
                .description("erp-auto-test equipment")
                .categoryId(categoryId)
                .storageId(storageId)
                .build();
        Response response = apiExecutor.executeEquipmentCreate(request, role);
        validateSuccess(response, "Create equipment");
        return response.as(EquipmentResponse.class);
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

    @Step("Отримати статус обладнання id={equipmentId}")
    public EquipmentStatus getEquipmentStatus(UserRole role, Long storageId, Long equipmentId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EQUIPMENT_GET_PAGE,
                role,
                Map.of("storageIds", storageId, "size", 50));
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
        EquipmentRequest request = EquipmentRequest.builder()
                .name("erp-supplier-eq-" + suffix)
                .inventoryNumber("INV-SUP-" + suffix)
                .serialNumber("SN-SUP-" + suffix)
                .description("erp-auto-test supplier equipment")
                .categoryId(categoryId)
                .storageId(recipientStorageId)
                .senderStorageId(supplierId)
                .invoiceNumber("INV-EQ-" + suffix)
                .isPaidByCash(false)
                .build();
        Response response = apiExecutor.executeEquipmentCreate(request, role);
        validateSuccess(response, "Create equipment from supplier");
        return response.as(EquipmentResponse.class);
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
        Response response = apiExecutor.executeEquipmentRelocationUpdateReceive(
                relocationId, storageId, request, role);
        validateSuccess(response, "Edit equipment receive");
        return response.as(RelocationResponse.class);
    }

    public RelocationResponse editEquipmentSend(UserRole role,
                                                Long relocationId,
                                                Long storageId,
                                                EquipmentRelocationSendEditRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_RELOCATION_PUT_UPDATE_SEND,
                role, request, relocationId, storageId);
        validateSuccess(response, "Edit equipment send");
        return response.as(RelocationResponse.class);
    }

    public Long supplierId() {
        return testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
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
}
