package com.erp.data.factories.relocation;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Seeds stock via {@code POST /api/v1/relocations/receive} (SUPPLIER → storage, AUTO_FINISHED).
 * Mirrors backend {@link org.pm.tk.service.impl.RelocationServiceImpl#receive}.
 */
@UtilityClass
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

        Response response = apiExecutor.executeRelocationReceive(request, role);
        int status = response.statusCode();
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
        List<StorageResponse> storages = DatabaseIntegrityValidator.extractList(response, StorageResponse.class);
        return storages.stream()
                .filter(s -> s != null && s.getId() != null)
                .map(StorageResponse::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No SUPPLIER storage found. Cannot seed stock via relocation receive."));
    }
}
