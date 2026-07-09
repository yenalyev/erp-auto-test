package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourceAlertRequest;
import com.erp.models.request.StorageAlertRequest;
import com.erp.models.response.ResourceAlertResponse;
import com.erp.models.response.StorageAlertResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class AlertFixture extends BaseFixture {

    public AlertFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public record AlertSnapshot(boolean existed, Long alertId, StorageAlertResponse snapshot) {}

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
