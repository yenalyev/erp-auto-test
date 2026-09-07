package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.ResourceReconciliationRequest;
import com.erp.models.request.SaveImplicitResourcesRequest;
import com.erp.models.response.FaitaResourceResponse;
import com.erp.models.response.ResourceReconciliationResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.ApiResponseHelper;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FAITA list / FLIGHT reconciliation / implicit-resources helpers.
 * Product appears in {@code GET /integrations/faita/resources} only after a FLIGHT mapping.
 */
@Slf4j
public class FaitaResourceFixture extends BaseFixture {

    public static final String SOURCE_FLIGHT = "FLIGHT";

    public FaitaResourceFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("API: probe GET /integrations/faita/resources")
    public boolean probeAvailable() {
        Response probe = apiExecutor.execute(ApiEndpointDefinition.FAITA_RESOURCES_GET, UserRole.ADMIN);
        boolean available = probe.statusCode() == 200;
        log.info("FAITA integrations API probe: status={} available={}", probe.statusCode(), available);
        return available;
    }

    public String newExternalId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    @Step("API: create FLIGHT reconciliation {externalId} → {resourceIds}")
    public List<Long> createFlightReconciliation(
            String externalId, String externalName, Long... resourceIds) {
        List<Long> ids = Arrays.asList(resourceIds);
        ResourceReconciliationRequest body = ResourceReconciliationRequest.builder()
                .source(SOURCE_FLIGHT)
                .externalId(externalId)
                .externalName(externalName)
                .resourceIds(new ArrayList<>(ids))
                .build();
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_RECONCILIATION_CREATE, UserRole.ADMIN, body);
        assertThat(response.statusCode())
                .as("POST /resources/reconciliations для %s. Body: %s",
                        externalId, response.getBody().asString())
                .isEqualTo(200);
        List<ResourceReconciliationResponse> created = ApiResponseHelper.parseList(
                response, ResourceReconciliationResponse.class, "Create FLIGHT reconciliation");
        assertThat(created)
                .as("create reconciliations має повернути id для %s", externalId)
                .isNotEmpty();
        return created.stream().map(ResourceReconciliationResponse::getId).toList();
    }

    @Step("API: DELETE reconciliation id={id}")
    public void deleteReconciliationById(Long id) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_RECONCILIATION_DELETE_BY_ID,
                UserRole.ADMIN,
                null,
                String.valueOf(id));
        assertThat(response.statusCode())
                .as("DELETE /resources/reconciliations/%s. Body: %s", id, response.getBody().asString())
                .isIn(200, 204);
    }

    public void deleteReconciliationsQuietly(Collection<Long> ids) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            try {
                apiExecutor.execute(
                        ApiEndpointDefinition.RESOURCE_RECONCILIATION_DELETE_BY_ID,
                        UserRole.ADMIN,
                        null,
                        String.valueOf(id));
            } catch (Exception e) {
                log.warn("Failed to delete reconciliation id={}: {}", id, e.getMessage());
            }
        }
    }

    @Step("API: GET FAITA resources")
    public List<FaitaResourceResponse> listResources() {
        Response get = apiExecutor.execute(ApiEndpointDefinition.FAITA_RESOURCES_GET, UserRole.ADMIN);
        assertThat(get.statusCode())
                .as("GET /integrations/faita/resources. Body: %s", get.getBody().asString())
                .isEqualTo(200);
        return ApiResponseHelper.parseList(get, FaitaResourceResponse.class, "GET FAITA resources");
    }

    @Step("API: find FAITA resource {externalId}")
    public FaitaResourceResponse requireByExternalId(String externalId) {
        return listResources().stream()
                .filter(r -> externalId.equals(r.getResourceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "FAITA list не містить externalId=" + externalId));
    }

    @Step("API: PUT implicit-resources for {externalId}")
    public FaitaResourceResponse putImplicitResources(
            String externalId,
            String externalName,
            List<FaitaResourceResponse> implicitResources) {
        SaveImplicitResourcesRequest body = SaveImplicitResourcesRequest.builder()
                .externalId(externalId)
                .externalName(externalName)
                .implicitResources(implicitResources != null ? implicitResources : List.of())
                .build();
        Response put = apiExecutor.execute(
                ApiEndpointDefinition.FAITA_IMPLICIT_RESOURCES_PUT,
                UserRole.ADMIN,
                body,
                externalId);
        assertThat(put.statusCode())
                .as("PUT implicit-resources %s. Body: %s", externalId, put.getBody().asString())
                .isEqualTo(200);
        return put.as(FaitaResourceResponse.class);
    }

    public void clearImplicitQuietly(String externalId, String externalName) {
        if (externalId == null) {
            return;
        }
        try {
            putImplicitResources(externalId, externalName, List.of());
        } catch (Exception e) {
            log.warn("Failed to clear implicit resources for {}: {}", externalId, e.getMessage());
        }
    }

    public static FaitaResourceResponse implicitRef(String externalId, String externalName) {
        return FaitaResourceResponse.builder()
                .resourceId(externalId)
                .resourceName(externalName)
                .build();
    }

    public static List<String> reconciliationResourceNames(FaitaResourceResponse product) {
        if (product.getReconciliations() == null) {
            return List.of();
        }
        return product.getReconciliations().stream()
                .map(rec -> rec.getResource() != null ? rec.getResource().getName() : null)
                .toList();
    }

    public static List<Long> reconciliationResourceIds(FaitaResourceResponse product) {
        if (product.getReconciliations() == null) {
            return List.of();
        }
        return product.getReconciliations().stream()
                .map(rec -> rec.getResource() != null ? rec.getResource().getId() : null)
                .toList();
    }

    public static List<String> implicitExternalIds(FaitaResourceResponse product) {
        if (product.getImplicitResources() == null) {
            return List.of();
        }
        return product.getImplicitResources().stream()
                .map(FaitaResourceResponse::getResourceId)
                .toList();
    }
}
