package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.EquipmentCategoryRequest;
import com.erp.models.response.*;
import com.erp.test_context.TestContext;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.util.*;

/** Controlled model/category graph; only this fixture's receive relocations are deleted. */
public class EquipmentDictionaryFixture extends BaseFixture {
    private final EquipmentFixture equipment;
    private final StorageFixture storages;
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> receiveEquipmentIds = new ArrayList<>();
    private Long storageId;
    public final String marker = "eqdict-" + UUID.randomUUID().toString().substring(0, 8);
    public final String alpha = "А " + marker;
    public final String ghe = "Ґ " + marker;
    public final String ye = "Є " + marker;
    public final String omega = "Я " + marker;

    public EquipmentDictionaryFixture(TestContext context, ApiExecutor executor) {
        super(context, executor);
        equipment = new EquipmentFixture(context, executor);
        storages = new StorageFixture(context, executor);
    }

    @Step("API: підготувати граф моделей і категорій довідника")
    public void prepare() {
        storageId = storages.createChildStorage("eq-dict-").getId();
        for (String suffix : List.of("A", "B", "unused")) {
            Response response = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_POST_CREATE,
                    UserRole.ADMIN, EquipmentCategoryRequest.builder().name(marker + "-" + suffix).build());
            validateSuccess(response, "Create dictionary category");
            categoryIds.add(response.as(EquipmentCategoryResponse.class).getId());
        }
        // Creation order deliberately differs from the expected Ukrainian name order.
        create(omega, categoryB(), 1);
        create(ye, categoryB(), 1);
        create(ghe, categoryA(), 1);
        create(alpha, categoryA(), 2);
        create(alpha, categoryB(), 1);
    }

    private void create(String name, Long category, int count) {
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < count; i++) numbers.add("ED-" + UUID.randomUUID());
        List<EquipmentResponse> created = equipment.createEquipmentGroup(
                UserRole.ADMIN, storageId, category, name, numbers);
        receiveEquipmentIds.add(created.getFirst().getId());
    }

    @Step("API: прочитати довідник моделей")
    public EquipmentModelPageResponse list(Map<String, ?> filters) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("modelName", marker);
        params.put("size", 20);
        params.putAll(filters);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE, UserRole.ADMIN, params);
        validateSuccess(response, "Get equipment model dictionary");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE);
        return response.as(EquipmentModelPageResponse.class);
    }

    public List<String> ascendingNames() { return List.of(alpha, ghe, ye, omega); }
    public Long categoryA() { return categoryIds.get(0); }
    public Long categoryB() { return categoryIds.get(1); }
    public Long unusedCategory() { return categoryIds.get(2); }
    public Long storageId() { return storageId; }
    public String categoryName(Long id) {
        return marker + "-" + (id.equals(categoryA()) ? "A" : id.equals(categoryB()) ? "B" : "unused");
    }

    @Step("API: прибрати отримання, категорії та склад поточного fixture")
    public void cleanup() {
        List<RuntimeException> errors = new ArrayList<>();
        // One confirmed equipment ID per owned receive, even for a multi-item batch.
        for (Long id : receiveEquipmentIds) {
            try {
                Long relocation = equipment.findEquipmentRelocationId(UserRole.ADMIN, storageId, id, true);
                equipment.deleteRelocation(UserRole.ADMIN, relocation, storageId);
            } catch (RuntimeException ex) { errors.add(ex); }
        }
        for (Long category : categoryIds) {
            try {
                Response response = apiExecutor.execute(ApiEndpointDefinition.EQUIPMENT_CATEGORY_DELETE,
                        UserRole.ADMIN, null, category);
                validateSuccess(response, "Delete owned dictionary category");
            } catch (RuntimeException ex) { errors.add(ex); }
        }
        try { storages.deactivateTrackedStorages(UserRole.ADMIN); }
        catch (RuntimeException ex) { errors.add(ex); }
        if (!errors.isEmpty()) {
            IllegalStateException failure = new IllegalStateException("Dictionary cleanup failed");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
