package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourceAlertRequest;
import com.erp.models.request.StorageAlertRequest;
import com.erp.models.response.ResourceAlertResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageAlertResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.data.DataUtils;
import com.erp.utils.helpers.PollUtils;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class AlertFixture extends BaseFixture {

    public static final int RED_WEIGHT = 100;
    public static final int YELLOW_WEIGHT = 60;
    public static final int GREEN_WEIGHT = 40;
    public static final int ZERO_WEIGHT = 0;
    public static final double DEFAULT_LIMIT = 10.0;

    public AlertFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public record AlertSnapshot(boolean existed, Long alertId, StorageAlertResponse snapshot) {}

    public record StockHighlightSeed(
            StorageResponse storage,
            String searchToken,
            ResourceResponse red,
            ResourceResponse yellow,
            ResourceResponse green,
            ResourceResponse plain) {}

    public record TypedAlertSeed(
            UnitType type,
            StorageResponse storage,
            String searchToken,
            ResourceResponse alerted,
            ResourceResponse plain,
            StorageAlertResponse alert) {
        /** Ресурс із порогом — для перевірок бейджа /alerts. */
        public ResourceResponse resource() {
            return alerted;
        }
    }

    @Step("API: GET сповіщення для складу {storageId}")
    public Response getByStorageIdRaw(long storageId, UserRole role) {
        return apiExecutor.execute(
                ApiEndpointDefinition.ALERT_GET_BY_STORAGE,
                role,
                String.valueOf(storageId));
    }

    @Step("API: GET сповіщення для складу {storageId}")
    public StorageAlertResponse getByStorageId(long storageId, UserRole role) {
        Response response = getByStorageIdRaw(storageId, role);
        if (response.statusCode() != 200) {
            return null;
        }
        String body = response.getBody() != null ? response.getBody().asString() : null;
        // Backend may return 200 with empty/null body (no Content-Type) when alert is absent.
        if (body == null || body.isBlank() || "null".equalsIgnoreCase(body.strip())) {
            return null;
        }
        try {
            StorageAlertResponse parsed = response.as(StorageAlertResponse.class);
            return parsed != null && parsed.getId() != null ? parsed : null;
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("GET alert for storage {} returned unparseable body (treated as absent): {}",
                    storageId, e.getMessage());
            return null;
        }
    }

    @Step("API: зберегти snapshot сповіщень складу {storageId}")
    public AlertSnapshot snapshotStorageAlert(long storageId, UserRole role) {
        StorageAlertResponse current = getByStorageId(storageId, role);
        if (current == null) {
            return new AlertSnapshot(false, null, null);
        }
        return new AlertSnapshot(true, current.getId(), current);
    }

    @Step("API: відновити snapshot сповіщень складу {storageId}")
    public void restoreSnapshot(long storageId, UserRole role, AlertSnapshot snapshot) {
        if (snapshot.existed()) {
            if (snapshot.alertId() != null && snapshot.snapshot() != null) {
                putUpdate(role, snapshot.alertId(), toRequest(snapshot.snapshot()));
            }
            return;
        }
        StorageAlertResponse current = getByStorageId(storageId, role);
        if (current != null && current.getId() != null) {
            deleteAlertById(role, current.getId());
        }
    }

    @Step("API: створити або оновити сповіщення для ресурсу {resourceId} на складі {storageId}")
    public StorageAlertResponse createOrUpdateStockAlert(
            UserRole role, Long storageId, Long resourceId, double limit) {
        StorageAlertResponse existing = getByStorageId(storageId, role);
        List<ResourceAlertRequest> resourceAlerts = new ArrayList<>();
        if (existing != null && existing.getResourceAlerts() != null) {
            for (ResourceAlertResponse alert : existing.getResourceAlerts()) {
                if (alert.getResource() != null
                        && alert.getResource().getId() != null
                        && !Objects.equals(resourceId, alert.getResource().getId())) {
                    resourceAlerts.add(ResourceAlertRequest.builder()
                            .resourceId(alert.getResource().getId())
                            .value(alert.getValue())
                            .build());
                }
            }
        }
        resourceAlerts.add(ResourceAlertRequest.builder()
                .resourceId(resourceId)
                .value(BigDecimal.valueOf(limit))
                .build());

        StorageAlertRequest request = StorageAlertRequest.builder()
                .storageId(storageId)
                .resourceAlerts(resourceAlerts)
                .build();

        if (existing != null && existing.getId() != null) {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.ALERT_PUT_UPDATE,
                    role,
                    request,
                    String.valueOf(existing.getId()));
            validateSuccess(response, "Update stock alert for resource " + resourceId);
            return response.as(StorageAlertResponse.class);
        }

        Response response = apiExecutor.execute(ApiEndpointDefinition.ALERT_POST_CREATE, role, request);
        validateSuccess(response, "Create stock alert for resource " + resourceId);
        return response.as(StorageAlertResponse.class);
    }

    @Step("API: оновити сповіщення id={alertId}")
    public StorageAlertResponse putUpdate(UserRole role, long alertId, StorageAlertRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ALERT_PUT_UPDATE,
                role,
                request,
                String.valueOf(alertId));
        validateSuccess(response, "PUT storage alert " + alertId);
        return response.as(StorageAlertResponse.class);
    }

    @Step("API: видалити сповіщення id={alertId}")
    public void deleteAlertById(UserRole role, long alertId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ALERT_DELETE,
                role,
                null,
                String.valueOf(alertId));
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            log.warn("Delete alert {} returned HTTP {}", alertId, response.statusCode());
        }
    }

    @Step("API: видалити сповіщення для складу {storageId}")
    public void deleteAlertForStorage(long storageId, UserRole role) {
        StorageAlertResponse alert = getByStorageId(storageId, role);
        if (alert != null && alert.getId() != null) {
            deleteAlertById(role, alert.getId());
        }
    }

    @Step("API: замінити всі пороги залишків складу {storageId}")
    public StorageAlertResponse replaceAllResourceAlerts(
            UserRole role, long storageId, Map<Long, Double> limitsByResourceId) {
        List<ResourceAlertRequest> resourceAlerts = new ArrayList<>();
        limitsByResourceId.forEach((resourceId, limit) -> resourceAlerts.add(ResourceAlertRequest.builder()
                .resourceId(resourceId)
                .value(BigDecimal.valueOf(limit))
                .build()));
        StorageAlertRequest request = StorageAlertRequest.builder()
                .storageId(storageId)
                .resourceAlerts(resourceAlerts)
                .build();
        StorageAlertResponse existing = getByStorageId(storageId, role);
        if (existing != null && existing.getId() != null) {
            return putUpdate(role, existing.getId(), request);
        }
        Response response = apiExecutor.execute(ApiEndpointDefinition.ALERT_POST_CREATE, role, request);
        validateSuccess(response, "Replace stock alerts for storage " + storageId);
        return response.as(StorageAlertResponse.class);
    }

    /**
     * Isolated storage with four uniquely named resources so alphabetical order is
     * green → plain → yellow → red, while weight order is red → yellow → green → plain.
     */
    @Step("FIXTURE: ізольований склад з red/yellow/green/plain порогами залишків")
    public StockHighlightSeed seedHighlightScenario(
            StorageFixture storageFixture,
            ResourceFixture resourceFixture,
            RelocationFixture relocationFixture,
            InventoryFixture inventoryFixture) {
        String searchToken = "alrt" + DataUtils.getUniqueSuffix();
        StorageResponse storage = storageFixture.createUniqueStorage("alrt-st-");
        ResourceResponse green = resourceFixture.createUniqueResource(searchToken + "-aaa-ok-");
        ResourceResponse plain = resourceFixture.createUniqueResource(searchToken + "-bbb-plain-");
        ResourceResponse yellow = resourceFixture.createUniqueResource(searchToken + "-mmm-low-");
        ResourceResponse red = resourceFixture.createUniqueResource(searchToken + "-zzz-out-");

        relocationFixture.seedExactStock(storage.getId(), yellow.getId(), 4.0);
        relocationFixture.seedExactStock(storage.getId(), green.getId(), 25.0);
        relocationFixture.seedExactStock(storage.getId(), plain.getId(), 15.0);

        Map<Long, Double> limits = new LinkedHashMap<>();
        limits.put(red.getId(), DEFAULT_LIMIT);
        limits.put(yellow.getId(), DEFAULT_LIMIT);
        limits.put(green.getId(), DEFAULT_LIMIT);
        replaceAllResourceAlerts(UserRole.ADMIN, storage.getId(), limits);

        PollUtils.waitUntil(
                () -> inventoryFixture.findItemIncludingZero(storage.getId(), red.getId(), UserRole.ADMIN),
                item -> item != null && Integer.valueOf(RED_WEIGHT).equals(item.getWeight()),
                20_000,
                "red inventory row weight=" + RED_WEIGHT);
        return new StockHighlightSeed(storage, searchToken, red, yellow, green, plain);
    }

    /**
     * Isolated location of {@code type} plus one resource threshold.
     * CREW / FLY_POINT hang under a fresh UNIT — same shape as inventory comment seeds.
     */
    /**
     * Isolated location: {@code zzz-alert} has a threshold (weight 100 at amount 0),
     * {@code aaa-plain} has stock and no alert (weight 0). Alphabetically plain comes first,
     * so pin-to-top is visible: alerted rows, then the rest.
     */
    @Step("FIXTURE: локація type={type} з порогом і рядком без алерту")
    public TypedAlertSeed seedAlertForType(
            StorageFixture storageFixture,
            ResourceFixture resourceFixture,
            InventoryFixture inventoryFixture,
            Long parentId,
            UnitType type,
            double limit) {
        String searchToken = "alrt" + DataUtils.getUniqueSuffix();
        StorageResponse location = createTypedLocation(
                storageFixture, parentId, type, searchToken + "-st-");
        ResourceResponse plain = resourceFixture.createUniqueResource(searchToken + "-aaa-plain-");
        ResourceResponse alerted = resourceFixture.createUniqueResource(searchToken + "-zzz-alert-");
        StorageAlertResponse alert = createOrUpdateStockAlert(
                UserRole.ADMIN, location.getId(), alerted.getId(), limit);
        inventoryFixture.resetResourceStock(location.getId(), plain.getId(), 15.0, UserRole.ADMIN);
        PollUtils.waitUntil(
                () -> inventoryFixture.findItemIncludingZero(location.getId(), alerted.getId(), UserRole.ADMIN),
                item -> item != null && Integer.valueOf(RED_WEIGHT).equals(item.getWeight()),
                20_000,
                "alerted inventory row weight=" + RED_WEIGHT);
        PollUtils.waitUntil(
                () -> inventoryFixture.findItemIncludingZero(location.getId(), plain.getId(), UserRole.ADMIN),
                item -> item != null && item.getAmount() != null && item.getAmount() >= 15.0,
                20_000,
                "plain inventory row amount>=15");
        return new TypedAlertSeed(type, location, searchToken, alerted, plain, alert);
    }

    @Step("FIXTURE: створити локацію type={type}")
    public StorageResponse createTypedLocation(
            StorageFixture storageFixture, Long parentId, UnitType type, String prefix) {
        return switch (type) {
            case STORAGE, UNIT, PRODUCTION -> storageFixture.createChildStorage(
                    parentId, prefix, type, StorageRelation.INTERNAL);
            case CREW -> {
                StorageResponse unit = storageFixture.createUnitStorage(parentId, prefix + "u-");
                yield storageFixture.createCrewStorage(unit.getId(), prefix + "c-");
            }
            case FLY_POINT -> {
                StorageResponse unit = storageFixture.createUnitStorage(parentId, prefix + "u-");
                yield storageFixture.createFlyPointStorage(unit.getId(), prefix + "fp-");
            }
            case SUPPLIER -> throw new IllegalArgumentException(
                    "SUPPLIER не веде залишки — сповіщення по порогу не налаштовують");
        };
    }

    @Step("API: прибрати ресурс {resourceId} зі сповіщень складу {storageId}")
    public void removeResourceFromAlerts(long storageId, long resourceId, UserRole role) {
        StorageAlertResponse existing = getByStorageId(storageId, role);
        if (existing == null || existing.getId() == null) {
            return;
        }
        List<ResourceAlertRequest> remaining = new ArrayList<>();
        if (existing.getResourceAlerts() != null) {
            for (ResourceAlertResponse alert : existing.getResourceAlerts()) {
                if (alert.getResource() != null
                        && alert.getResource().getId() != null
                        && !Objects.equals(resourceId, alert.getResource().getId())) {
                    remaining.add(ResourceAlertRequest.builder()
                            .resourceId(alert.getResource().getId())
                            .value(alert.getValue())
                            .build());
                }
            }
        }
        if (remaining.isEmpty()) {
            deleteAlertById(role, existing.getId());
            return;
        }
        putUpdate(role, existing.getId(), StorageAlertRequest.builder()
                .storageId(storageId)
                .resourceAlerts(remaining)
                .build());
    }

    private static StorageAlertRequest toRequest(StorageAlertResponse response) {
        List<ResourceAlertRequest> resourceAlerts = new ArrayList<>();
        if (response.getResourceAlerts() != null) {
            for (ResourceAlertResponse alert : response.getResourceAlerts()) {
                if (alert.getResource() != null && alert.getResource().getId() != null) {
                    resourceAlerts.add(ResourceAlertRequest.builder()
                            .resourceId(alert.getResource().getId())
                            .value(alert.getValue())
                            .build());
                }
            }
        }
        Long storageId = response.getStorage() != null ? response.getStorage().getId() : null;
        return StorageAlertRequest.builder()
                .storageId(storageId)
                .resourceAlerts(resourceAlerts)
                .build();
    }
}
