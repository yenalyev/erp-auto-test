package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class StorageFixture extends BaseFixture {

    private final Set<Long> storagesToCleanup = new LinkedHashSet<>();

    /**
     * Matches tk {@code StorageController} {@code ALL_DATA_PAGE_SIZE} so /names is not truncated.
     * A fixture {@code size=500} dropped newly created test storages on populated envs (sort=name).
     */
    private static final int NAMES_PAGE_SIZE = 999_999_999;

    public StorageFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("FIXTURE: Підготовка базового складу")
    public void prepareContext() {
        if (testContext.get(ContextKey.DYNAMIC_STORAGE) == null) {
            setupSharedStorage();
        }
    }

    @Step("FIXTURE: Створення спільного складу")
    public StorageResponse setupSharedStorage() {
        StorageResponse parent = resolveParentUnit();
        StorageRequest request = StorageDataFactory.childStorage(parent.getId(), "shared-").build();

        Response createResponse = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(createResponse, "Create shared storage");
        StorageResponse response = createResponse.as(StorageResponse.class);
        trackForCleanup(response.getId());

        List<StorageResponse> storageResponseList = List.of(response);

        testContext.set(ContextKey.DYNAMIC_STORAGE, response);
        testContext.set(ContextKey.SHARED_STORAGE_LIST, storageResponseList);
        log.info("Shared Storage created: {} (ID: {})", response.getName(), response.getId());
        return response;
    }

    @Step("FIXTURE: Забезпечення наявності списку складів (мінімум {count})")
    public List<StorageResponse> setupSharedStorageList(int count) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, UserRole.ADMIN);

        List<StorageResponse> allStorages = new ArrayList<>();
        if (response.statusCode() == 200) {
            List<StorageResponse> existing = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
            if (existing != null) {
                allStorages.addAll(existing);
            }
        }

        if (allStorages.size() < count) {
            int needed = count - allStorages.size();
            log.info("Database has {} storages. Creating {} more to reach {}", allStorages.size(), needed, count);

            for (int i = 0; i < needed; i++) {
                allStorages.add(setupSharedStorage());
            }
        }

        testContext.set(ContextKey.SHARED_STORAGE_LIST, allStorages);

        if (!allStorages.isEmpty()) {
            testContext.set(ContextKey.DYNAMIC_STORAGE, allStorages.getFirst());
        }

        return allStorages;
    }

    @Step("API: альтернативний parent (SUPPLIER) для оновлення ієрархії")
    public StorageResponse resolveSupplierParent() {
        Response supplierResponse = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_SUPPLIER, UserRole.ADMIN);
        validateSuccess(supplierResponse, "Get SUPPLIER storage for alternate parent");
        List<StorageResponse> suppliers = DatabaseIntegrityValidator.extractList(supplierResponse, StorageResponse.class);
        if (suppliers == null || suppliers.isEmpty()) {
            throw new IllegalStateException("No SUPPLIER storage available for alternate parent");
        }
        return suppliers.getFirst();
    }

    @Step("API: альтернативний parent з /storages/names (як у формі оновлення UI)")
    public StorageResponse resolveAlternateParent(Long currentParentId, Long selfId) {
        List<StorageResponse> names = getNames(UserRole.ADMIN, true, null);
        return names.stream()
                .filter(s -> !Objects.equals(s.getId(), selfId))
                .filter(s -> !Objects.equals(s.getId(), currentParentId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No alternate parent in /storages/names (currentParentId=" + currentParentId + ")"));
    }

    @Step("API: знайти батьківську локацію для дочірнього STORAGE")
    public StorageResponse resolveParentUnit() {
        Long ownerStorageId = ConfigProvider.getOwner1StorageId();
        if (ownerStorageId != null) {
            Response byId = apiExecutor.execute(
                    ApiEndpointDefinition.STORAGE_GET_BY_ID, UserRole.ADMIN, String.valueOf(ownerStorageId));
            if (byId.statusCode() == 200) {
                return byId.as(StorageResponse.class);
            }
            log.warn("OWNER_1 storage {} not available as parent, status={}", ownerStorageId, byId.statusCode());
        }

        Response supplierResponse = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_SUPPLIER, UserRole.ADMIN);
        validateSuccess(supplierResponse, "Get SUPPLIER storage for parent");
        List<StorageResponse> suppliers = DatabaseIntegrityValidator.extractList(supplierResponse, StorageResponse.class);
        if (suppliers != null && !suppliers.isEmpty()) {
            return suppliers.getFirst();
        }

        Response allResponse = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_ALL, UserRole.ADMIN);
        validateSuccess(allResponse, "Get storages for parent fallback");
        List<StorageResponse> all = DatabaseIntegrityValidator.extractList(allResponse, StorageResponse.class);
        if (all == null || all.isEmpty()) {
            throw new IllegalStateException("No storages available to use as parent");
        }
        return all.getFirst();
    }

    @Step("API: створити дочірню локацію «{namePrefix}»")
    public StorageResponse createChildStorage(String namePrefix) {
        StorageResponse parent = resolveParentUnit();
        return createChildStorage(parent.getId(), namePrefix);
    }

    @Step("API: POST створити локацію (автоматично в cleanup-чергу для архівації)")
    public StorageResponse createStorage(StorageRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create storage");
        StorageResponse created = response.as(StorageResponse.class);
        trackForCleanup(created.getId());
        return created;
    }

    public void trackForCleanup(Long storageId) {
        if (storageId != null) {
            storagesToCleanup.add(storageId);
        }
    }

    /** Виключити локацію з cleanup (наприклад, спільний контекст suite). */
    public void untrackForCleanup(Long storageId) {
        if (storageId != null) {
            storagesToCleanup.remove(storageId);
        }
    }

    public void clearTrackedStorages() {
        storagesToCleanup.clear();
    }

    @Step("FIXTURE: деактивувати тестові локації після тесту")
    public void deactivateTrackedStorages(UserRole role) {
        if (storagesToCleanup.isEmpty()) {
            return;
        }
        for (Long storageId : List.copyOf(storagesToCleanup)) {
            try {
                Response response = deactivate(role, storageId);
                if (response.statusCode() == 200) {
                    log.debug("Cleanup: deactivated test storage id={}", storageId);
                } else {
                    log.warn("Cleanup: deactivate storage id={} returned HTTP {}", storageId, response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Cleanup: failed to deactivate storage id={}: {}", storageId, e.getMessage());
            }
        }
        storagesToCleanup.clear();
    }

    @Step("API: створити дочірню локацію parentId={parentId}, prefix={namePrefix}")
    public StorageResponse createChildStorage(Long parentId, String namePrefix) {
        StorageRequest request = StorageDataFactory.childStorage(parentId, namePrefix).build();
        return createStorage(request);
    }

    /** CPMA-711: gathering candidate — STORAGE + {@code orderHub=true}. */
    @Step("API: створити orderHub STORAGE parentId={parentId}, prefix={namePrefix}")
    public StorageResponse createOrderHubStorage(Long parentId, String namePrefix) {
        StorageRequest request = StorageDataFactory.childStorage(parentId, namePrefix)
                .orderHub(true)
                .build();
        return createStorage(request);
    }

    @Step("API: створити підрозділ UNIT parentId={parentId}, prefix={namePrefix}")
    public StorageResponse createUnitStorage(Long parentId, String namePrefix) {
        return createStorage(StorageDataFactory.unitStorage(parentId, namePrefix).build());
    }

    @Step("API: створити екіпаж CREW parentId={parentId}, prefix={namePrefix}")
    public StorageResponse createCrewStorage(Long parentId, String namePrefix) {
        return createStorage(StorageDataFactory.crewStorage(parentId, namePrefix).build());
    }

    @Step("API: створити точку вильоту FLY_POINT parentId={parentId}, prefix={namePrefix}")
    public StorageResponse createFlyPointStorage(Long parentId, String namePrefix) {
        return createStorage(StorageDataFactory.flyPointStorage(parentId, namePrefix).build());
    }

    @Step("API: створити EXTERNAL дочірню локацію parentId={parentId}, prefix={namePrefix}")
    public StorageResponse createExternalChildStorage(Long parentId, String namePrefix) {
        StorageRequest request = StorageDataFactory.externalStorage(parentId, namePrefix).build();
        return createStorage(request);
    }

    @Step("API: POST створити локацію type={type}, relation={relation}, prefix={namePrefix}")
    public StorageResponse createChildStorage(
            Long parentId,
            String namePrefix,
            UnitType type,
            StorageRelation relation) {
        StorageRequest request = StorageDataFactory.childStorage(parentId, namePrefix, type, relation).build();
        return createStorage(request);
    }

    @Step("API: створити ізольовану локацію «{namePrefix}»")
    public StorageResponse createUniqueStorage(String namePrefix) {
        return createChildStorage(namePrefix);
    }

    @Step("API: GET локація id={storageId}")
    public StorageResponse getById(UserRole role, Long storageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_GET_BY_ID, role, null, String.valueOf(storageId));
        validateSuccess(response, "Get storage by id " + storageId);
        return response.as(StorageResponse.class);
    }

    @Step("API: PUT оновити локацію id={storageId}")
    public Response update(UserRole role, Long storageId, StorageRequest body) {
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_PUT_UPDATE, role, body, String.valueOf(storageId));
    }

    @Step("API: PUT accessMode={mode} на локацію id={storageId}")
    public StorageResponse setAccessMode(UserRole role, Long storageId, StorageAccessMode mode) {
        StorageResponse existing = getById(role, storageId);
        if (mode.name().equals(existing.getAccessMode())) {
            return existing;
        }
        StorageRequest body = StorageDataFactory.withAccessMode(existing, mode);
        Response response = update(role, storageId, body);
        validateSuccess(response, "Set accessMode " + mode + " on storage " + storageId);
        return response.as(StorageResponse.class);
    }

    /** CPMA-711: gathering candidates require {@code storage.orderHub == true}. */
    @Step("API: ensure storage {storageId} is orderHub")
    public StorageResponse ensureOrderHub(UserRole role, Long storageId) {
        StorageResponse existing = getById(role, storageId);
        if (Boolean.TRUE.equals(existing.getOrderHub())) {
            return existing;
        }
        StorageRequest body = StorageDataFactory.updateFromExisting(existing, builder -> builder.orderHub(true));
        Response response = update(role, storageId, body);
        validateSuccess(response, "Enable orderHub on storage " + storageId);
        return response.as(StorageResponse.class);
    }

    /**
     * Змінює {@code parentId} локації (напр. CREW → інша FLY_POINT).
     * При прикріпленні CREW до FLY_POINT backend авто-переміщує залишок екіпажу на точку.
     */
    @Step("API: reparent локації id={storageId} → parentId={newParentId}")
    public StorageResponse reparent(UserRole role, Long storageId, Long newParentId) {
        StorageResponse existing = getById(role, storageId);
        StorageRequest body = StorageDataFactory.updateFromExisting(
                existing, builder -> builder.parentId(newParentId));
        Response response = update(role, storageId, body);
        validateSuccess(response, "Reparent storage " + storageId + " → parent " + newParentId);
        return response.as(StorageResponse.class);
    }

    @Step("API: DELETE деактивувати локацію id={storageId}")
    public Response deactivate(UserRole role, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_DELETE_DEACTIVATE, role, null, String.valueOf(storageId));
    }

    @Step("API: PUT розархівувати локацію id={storageId}")
    public Response unarchive(UserRole role, Long storageId) {
        return apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_PUT_UNARCHIVE, role, null, String.valueOf(storageId));
    }

    @Step("API: GET /storages/names isActive={isActive} (raw response)")
    public Response getNamesRaw(UserRole role, Boolean isActive) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", NAMES_PAGE_SIZE);
        if (isActive != null) {
            params.put("isActive", isActive);
        }
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_NAMES, role, params);
    }

    @Step("API: GET /storages/names isActive={isActive}")
    public List<StorageResponse> getNames(UserRole role, Boolean isActive, String nameFilter) {
        return getNames(role, isActive, null, null, nameFilter, null);
    }

    /**
     * Same contract as tk-ui send form: {@code GET /storages/{contextStorageId}/names}.
     * Unscoped {@code /storages/names} ignores REGIONS when the JWT looks like full-access.
     */
    @Step("API: GET /storages/{contextStorageId}/names isActive={isActive}")
    public List<StorageResponse> getNamesInStorageContext(UserRole role, Long contextStorageId, Boolean isActive) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", NAMES_PAGE_SIZE);
        if (isActive != null) {
            params.put("isActive", isActive);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_NAMES_FOR_STORAGE,
                role,
                params,
                String.valueOf(contextStorageId));
        validateSuccess(response, "Get storage names in context of " + contextStorageId);
        return DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
    }

    @Step("API: GET /storages/names isActive={isActive} id={storageId}")
    public List<StorageResponse> getNames(UserRole role, Boolean isActive, String nameFilter, Long storageId) {
        return getNames(role, isActive, null, null, nameFilter, storageId);
    }

    @Step("API: GET /storages/names relation={relation}")
    public List<StorageResponse> getNames(UserRole role,
                                         Boolean isActive,
                                         StorageRelation relation,
                                         List<UnitType> types,
                                         String nameFilter,
                                         Long storageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", 0);
        params.put("size", NAMES_PAGE_SIZE);
        if (isActive != null) {
            params.put("isActive", isActive);
        }
        if (relation != null) {
            params.put("relation", relation.name());
        }
        if (types != null && !types.isEmpty()) {
            params.put("types", types.stream().map(UnitType::name).toList());
        }
        if (nameFilter != null && !nameFilter.isBlank()) {
            params.put("name", nameFilter);
        }
        if (storageId != null) {
            params.put("id", storageId);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_GET_NAMES, role, params);
        validateSuccess(response, "Get storage names isActive=" + isActive + " relation=" + relation);
        return DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
    }

    @Step("API: GET /storages/names/my-units")
    public List<StorageResponse> getMyUnits(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_MY_UNITS, role);
        validateSuccess(response, "Get my internal units");
        return DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
    }

    @Step("API: GET /storages з query params")
    public Response getPage(UserRole role, Map<String, Object> queryParams) {
        Map<String, Object> params = new HashMap<>(queryParams);
        params.putIfAbsent("page", 0);
        params.putIfAbsent("size", 500);
        return apiExecutor.executeWithQueryParams(ApiEndpointDefinition.STORAGE_GET_ALL, role, params);
    }

    @Step("API: GET /storages content з query params")
    public List<StorageResponse> getPageContent(UserRole role, Map<String, Object> queryParams) {
        Response response = getPage(role, queryParams);
        validateSuccess(response, "Get storages page");
        return DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
    }

    public boolean isPresentInNames(UserRole role, Long storageId, Boolean isActive, String nameFilter) {
        return getNames(role, isActive, nameFilter, storageId).stream()
                .anyMatch(s -> Objects.equals(s.getId(), storageId));
    }

    public static void assertValidationError(Response response, String expectedField, String expectedMessageFragment) {
        assertThat(response.statusCode()).as("Очікувався статус 400").isEqualTo(400);
        assertThat(response.jsonPath().getString("errors[0].field")).isEqualTo(expectedField);
        List<String> messages = response.jsonPath().getList("errors[0].messages");
        assertThat(messages).isNotNull().isNotEmpty();
        assertThat(messages.getFirst()).contains(expectedMessageFragment);
    }
}
