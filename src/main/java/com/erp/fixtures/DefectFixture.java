package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.ResourceDataFactory;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.models.query.DefectQuery;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.DefectWriteOffRequest;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.DefectWriteOffResponse;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

/**
 * FIXTURE for defect ("Брак") tests. Composes {@link ProductionFixture} (production-type defects)
 * and {@link RelocationFixture} (relocation / storage-type defects) over a shared {@link TestContext}.
 */
@Slf4j
@Getter
public class DefectFixture extends BaseFixture {

    private final ProductionFixture productionFixture;
    private final RelocationFixture relocationFixture;
    private Long storageId;

    public DefectFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.productionFixture = new ProductionFixture(testContext, apiExecutor);
        this.relocationFixture = new RelocationFixture(testContext, apiExecutor);
        this.storageId = ConfigProvider.getOwner1StorageId();
    }

    @Step("FIXTURE: Підготовка середовища для тестів браку")
    public void prepareContext() {
        this.storageId = ConfigProvider.getOwner1StorageId();
        if (testContext.get(ContextKey.DEFECT_RESOURCE_ID) != null) {
            return;
        }

        relocationFixture.prepareContext();
        productionFixture.prepareContext();

        // Non-produced, stocked resource used for STORAGE / RELOCATION defects and RBAC create body.
        Long resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        testContext.set(ContextKey.DEFECT_RESOURCE_ID, resourceId);

        log.info("Defect fixture ready: storage={}, defectResource={}, outputResource={}",
                storageId, resourceId, testContext.get(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID));
    }

    // --- Context accessors -------------------------------------------------

    public Long defectResourceId() {
        return testContext.get(ContextKey.DEFECT_RESOURCE_ID);
    }

    public Long outputResourceId() {
        return testContext.get(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID);
    }

    public ProductionFixture getProductionFixture() {
        return productionFixture;
    }

    public TechnologicalMapResponse techMap() {
        return testContext.get(ContextKey.PRODUCTION_TECH_MAP);
    }

    public double outputCoef() {
        return techMap().getOutput().getFirst().getAmount();
    }

    public Long supplierId() {
        return testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
    }

    public Long unitStorageId() {
        return testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID);
    }

    // --- Prerequisite builders --------------------------------------------

    @Step("FIXTURE: створити виробництво {amount} од., партія «{batchNumber}» (для браку на виробництві)")
    public ManufacturingItemResponse createProduction(double amount, String batchNumber) {
        return createProductionAs(UserRole.OWNER_1, amount, batchNumber, LocalDate.now());
    }

    @Step("FIXTURE: створити виробництво {amount} од. з датою {date}")
    public ManufacturingItemResponse createProduction(double amount, String batchNumber, LocalDate date) {
        return createProductionAs(UserRole.OWNER_1, amount, batchNumber, date);
    }

    @Step("FIXTURE: створити виробництво роллю {role}, {amount} од., дата {date}")
    public ManufacturingItemResponse createProductionAs(UserRole role,
                                                        double amount,
                                                        String batchNumber,
                                                        LocalDate date) {
        return productionFixture.createAs(role, storageId, techMap(), amount, batchNumber, date);
    }

    /** Raw production create (no success validation) — для перевірки відхилення некоректних дат. */
    @Step("FIXTURE: raw створення виробництва {amount} од., дата {date}")
    public Response createProductionRaw(UserRole role, double amount, String batchNumber, LocalDate date) {
        Object request = com.erp.data.factories.production.ProductionDataFactory
                .buildCreateRequest(techMap(), amount, date, batchNumber);
        return apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE, role, request, String.valueOf(storageId));
    }

    @Step("FIXTURE: зовнішнє отримання {amount} од. ресурсу {resourceId}, партія «{batchNumber}»")
    public RelocationResponse createExternalReceipt(Long resourceId, double amount, String batchNumber) {
        return createExternalReceipt(resourceId, amount, batchNumber, false);
    }

    @Step("FIXTURE: отримання {amount} од. ресурсу {resourceId}, партія «{batchNumber}», isProduced={isProduced}")
    public RelocationResponse createExternalReceipt(Long resourceId,
                                                    double amount,
                                                    String batchNumber,
                                                    boolean isProduced) {
        return relocationFixture.createExternalReceive(
                UserRole.OWNER_1, storageId, resourceId, amount, batchNumber, isProduced);
    }

    /** Creates a brand-new resource with zero starting stock (для чистих FIFO-сценаріїв). */
    @Step("FIXTURE: створити новий ресурс без початкових залишків")
    public Long createFreshResource() {
        Long unitId = testContext.get(ContextKey.SHARED_UNIT_ID);
        Long categoryId = testContext.get(ContextKey.SHARED_RESOURCE_CATEGORY_ID);
        ResourceRequest body = ResourceDataFactory.uniqueResource("def-fresh-", unitId, categoryId);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CREATE, UserRole.ADMIN, body);
        validateSuccess(response, "Create fresh resource");
        ResourceResponse resource = response.as(ResourceResponse.class);
        return resource.getId();
    }

    /** External receipt (SUPPLIER → storage) with two resources in a single relocation. */
    @Step("FIXTURE: зовнішнє отримання з двома ресурсами")
    public RelocationResponse createMultiResourceReceipt(Long resourceA, double amountA, String batchA,
                                                         Long resourceB, double amountB, String batchB) {
        RelocationInputRequest request = RelocationInputRequest.builder()
                .senderId(supplierId())
                .recipientId(storageId)
                .description("erp-auto-test multi-resource receipt")
                .invoiceNumber(RelocationDataFactory.uniqueInvoiceNumber())
                .date(LocalDate.now())
                .items(List.of(
                        RelocationDataFactory.usageWithBatch(resourceA, amountA, batchA, false),
                        RelocationDataFactory.usageWithBatch(resourceB, amountB, batchB, false)))
                .build();
        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        validateSuccess(response, "Create multi-resource receipt");
        return response.as(RelocationResponse.class);
    }

    /** Outbound send consuming a specific batch (storage → recipient). */
    @Step("FIXTURE: видача {amount} од. з партії «{batchNumber}» на {recipientId}")
    public RelocationResponse sendFromBatch(Long recipientId, Long resourceId, double amount,
                                            String batchNumber, boolean isProduced) {
        return relocationFixture.createSendWithBatch(
                UserRole.OWNER_1, storageId, recipientId, resourceId, amount, batchNumber, isProduced);
    }

    /**
     * Issue to UNIT (OWNER_1), return to main storage (ADMIN — unit sender requires elevated scope on staging).
     * Returns the return relocation for {@code RELOCATION_FROM_UNIT} defect source.
     */
    @Step("FIXTURE: видача на підрозділ і повернення партії «{batchNumber}»")
    public RelocationResponse sendToUnitAndReturn(Long resourceId, double amount, String batchNumber) {
        Long unitId = unitStorageId();
        RelocationResponse toUnit = relocationFixture.createSendWithBatch(
                UserRole.OWNER_1, storageId, unitId, resourceId, amount, batchNumber, false);
        if (toUnit.getState() == RelocationState.CREATED) {
            relocationFixture.resolve(UserRole.ADMIN, toUnit.getId(), unitId, RelocationState.FINISHED);
        }
        RelocationResponse fromUnit = relocationFixture.createSendWithBatch(
                UserRole.ADMIN, unitId, storageId, resourceId, amount, batchNumber, false);
        if (fromUnit.getState() == RelocationState.CREATED) {
            return relocationFixture.resolve(
                    UserRole.OWNER_1, fromUnit.getId(), storageId, RelocationState.FINISHED);
        }
        return fromUnit;
    }

    @Step("FIXTURE: видача на зовнішній склад і завершення отримання")
    public RelocationResponse sendAndFinishAtRecipient(UserRole recipientRole,
                                                     Long recipientStorageId,
                                                     Long resourceId,
                                                     double amount,
                                                     String batchNumber,
                                                     boolean isProduced) {
        RelocationResponse sent = sendFromBatch(recipientStorageId, resourceId, amount, batchNumber, isProduced);
        if (sent.getState() == RelocationState.CREATED) {
            return relocationFixture.resolve(
                    recipientRole, sent.getId(), recipientStorageId, RelocationState.FINISHED);
        }
        return sent;
    }

    @Step("FIXTURE: поповнити залишок ресурсу {resourceId} ≥ {minAmount}")
    public void ensureStock(Long resourceId, double minAmount) {
        relocationFixture.ensureStock(storageId, resourceId, minAmount);
    }

    // --- Defect API actions ------------------------------------------------

    @Step("API: створити запис про брак роллю {role}")
    public DefectResponse createAs(UserRole role, DefectRequest request) {
        Response response = createRaw(role, request);
        validateSuccess(response, "Create defect");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.DEFECT_POST_CREATE);
        DefectResponse created = response.as(DefectResponse.class);
        testContext.set(ContextKey.DEFECT_ID, created.getId());
        return created;
    }

    public Response createRaw(UserRole role, DefectRequest request) {
        return apiExecutor.executeDefectCreate(request, role);
    }

    @Step("API: оновити запис про брак id={defectId} роллю {role}")
    public DefectResponse updateAs(UserRole role, Long defectId, DefectRequest request) {
        Response response = updateRaw(role, defectId, request);
        validateSuccess(response, "Update defect id=" + defectId);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.DEFECT_PUT_UPDATE);
        return response.as(DefectResponse.class);
    }

    public Response updateRaw(UserRole role, Long defectId, DefectRequest request) {
        return apiExecutor.executeDefectUpdate(defectId, request, role);
    }

    @Step("API: видалити запис про брак id={defectId} роллю {role}")
    public void deleteAs(UserRole role, Long defectId) {
        Response response = deleteRaw(role, defectId);
        validateSuccess(response, "Delete defect id=" + defectId);
    }

    public Response deleteRaw(UserRole role, Long defectId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.DEFECT_DELETE, role, null,
                String.valueOf(defectId), String.valueOf(storageId));
    }

    @Step("API: GET брак id={defectId}")
    public DefectResponse getById(UserRole role, Long defectId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.DEFECT_GET_BY_ID, role, null,
                String.valueOf(defectId), String.valueOf(storageId));
        validateSuccess(response, "Get defect by id=" + defectId);
        return response.as(DefectResponse.class);
    }

    public Response getByIdRaw(UserRole role, Long defectId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.DEFECT_GET_BY_ID, role, null,
                String.valueOf(defectId), String.valueOf(storageId));
    }

    @Step("API: GET список браку")
    public List<DefectResponse> listDefects(DefectQuery query) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.DEFECT_GET_PAGE, UserRole.OWNER_1, query.toListQueryParams());
        validateSuccess(response, "List defects");
        return DatabaseIntegrityValidator.extractList(response, DefectResponse.class);
    }

    @Step("API: GET linked-relocation-ids (resource={resourceId}, date={date})")
    public List<Long> getLinkedRelocationIds(UserRole role, Long resourceId, LocalDate date) {
        DefectQuery query = DefectQuery.builder().storageId(storageId).build();
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.DEFECT_GET_LINKED_RELOCATION_IDS, role,
                query.toLinkedQueryParams(resourceId, date));
        validateSuccess(response, "Get linked relocation ids");
        List<Long> ids = response.jsonPath().getList("", Long.class);
        return ids != null ? ids : List.of();
    }

    public Response getLinkedRelocationIdsRaw(UserRole role, Long resourceId, LocalDate date) {
        DefectQuery query = DefectQuery.builder().storageId(storageId).build();
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.DEFECT_GET_LINKED_RELOCATION_IDS, role,
                query.toLinkedQueryParams(resourceId, date));
    }

    @Step("API: GET linked-production-ids (resource={resourceId}, date={date})")
    public List<Long> getLinkedProductionIds(UserRole role, Long resourceId, LocalDate date) {
        DefectQuery query = DefectQuery.builder().storageId(storageId).build();
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.DEFECT_GET_LINKED_PRODUCTION_IDS, role,
                query.toLinkedQueryParams(resourceId, date));
        validateSuccess(response, "Get linked production ids");
        List<Long> ids = response.jsonPath().getList("", Long.class);
        return ids != null ? ids : List.of();
    }

    @Step("API: списати брак роллю {role}")
    public DefectWriteOffResponse writeOffAs(UserRole role, DefectWriteOffRequest request) {
        Response response = writeOffRaw(role, request);
        validateSuccess(response, "Write off defect id=" + request.getDefectId());
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.DEFECT_POST_WRITE_OFF);
        return response.as(DefectWriteOffResponse.class);
    }

    public Response writeOffRaw(UserRole role, DefectWriteOffRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.DEFECT_POST_WRITE_OFF, role, request);
    }

    @Step("API: GET списання браку id={defectId}")
    public List<DefectWriteOffResponse> getWriteOffs(UserRole role, Long defectId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.DEFECT_GET_WRITE_OFFS, role, null,
                String.valueOf(defectId), String.valueOf(storageId));
        validateSuccess(response, "Get write-offs for defect id=" + defectId);
        return DatabaseIntegrityValidator.extractList(response, DefectWriteOffResponse.class);
    }

    @Step("API: скасувати списання браку id={writeOffId}")
    public void deleteWriteOff(UserRole role, Long writeOffId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.DEFECT_DELETE_WRITE_OFF, role, null,
                String.valueOf(writeOffId), String.valueOf(storageId));
        validateSuccess(response, "Delete write-off id=" + writeOffId);
    }

    public double resourceStock(Long resourceId) {
        return relocationFixture.getResourceStock(storageId, resourceId, UserRole.OWNER_1);
    }

    /** Seeds a defect record (for RBAC matrix) and stores {@code DEFECT_ID}. */
    @Step("FIXTURE: seed defect record for RBAC matrix")
    public DefectResponse seedDefectForRbac() {
        Long resourceId = defectResourceId();
        ensureStock(resourceId, 50.0);
        DefectRequest request = com.erp.data.factories.defect.DefectDataFactory.buildStorageFifoDefect(
                storageId, resourceId, 2.0);
        return createAs(UserRole.OWNER_1, request);
    }
}
