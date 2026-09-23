package com.erp.data.factories.relocation;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.LocationFeature;
import com.erp.enums.StorageRelation;
import com.erp.enums.UserRole;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Seeds stock via {@code POST /api/v1/relocations/receive} (SUPPLIER → storage, AUTO_FINISHED).
 * Mirrors backend {@link org.pm.tk.service.impl.RelocationServiceImpl#receive}.
 */
@UtilityClass
@Slf4j
public class RelocationStockSeeder {

    @Step("Receive resources from SUPPLIER into storage {recipientStorageId}")
    public static void receiveFromSupplier(ApiExecutor apiExecutor,
                                           UserRole role,
                                           Long recipientStorageId,
                                           Map<Long, Double> amountsByResourceId) {
        Long supplierId = resolveSupplierStorageId(apiExecutor, role);

        List<ResourceUsageRequest> items = amountsByResourceId.entrySet().stream()
                .map(e -> toUsageRequest(e.getKey(), e.getValue()))
                .toList();

        RelocationInputRequest request = RelocationInputRequest.builder()
                .senderId(supplierId)
                .recipientId(recipientStorageId)
                .description("erp-auto-test: seed stock via relocation receive")
                .invoiceNumber("erp-auto-test-seed")
                .date(LocalDate.now())
                .items(items)
                .build();

        log.info("Seeding stock via relocation receive: storage={}, resources={}", recipientStorageId, amountsByResourceId);
        Response response = apiExecutor.executeRelocationReceive(request, role);
        int status = response.statusCode();
        log.info("Relocation receive completed with status {}", status);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "Relocation receive failed (status=" + status + "): " + response.getBody().asString());
        }
    }

    @Step("Resolve SUPPLIER storage id")
    private static ResourceUsageRequest toUsageRequest(Long resourceId, Double amount) {
        BigDecimal qty = BigDecimal.valueOf(amount);
        String batchNumber = "seed-" + resourceId;
        return ResourceUsageRequest.builder()
                .resourceId(resourceId)
                .amount(qty)
                .batches(List.of(RelocationItemBatchRequest.builder()
                        .batchNumber(batchNumber)
                        .amount(qty)
                        .isProduced(false)
                        .build()))
                .build();
    }

    public static Long resolveSupplierStorageId(ApiExecutor apiExecutor, UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_SUPPLIER, role);
        if (response.statusCode() == 403 && role != UserRole.ADMIN) {
            // Supplier discovery is fixture setup. Some business roles cannot list
            // suppliers, even when the test operation itself is permitted.
            response = apiExecutor.execute(ApiEndpointDefinition.STORAGE_GET_SUPPLIER, UserRole.ADMIN);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Cannot resolve supplier location as " + role
                    + " (HTTP " + response.statusCode() + "): "
                    + response.getBody().asString().substring(
                            0, Math.min(200, response.getBody().asString().length())));
        }
        List<StorageResponse> storages = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        return storages.stream()
                .filter(s -> s != null && s.getId() != null)
                .filter(s -> s.getFeatures() != null
                        && s.getFeatures().contains(LocationFeature.RELOCATIONS))
                .filter(s -> StorageRelation.EXTERNAL.name().equals(s.getRelation()))
                .filter(s -> !Boolean.FALSE.equals(s.getActive()))
                .map(StorageResponse::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No active EXTERNAL SUPPLIER storage with RELOCATIONS feature found. "
                                + "Cannot seed stock via relocation receive."));
    }
}
