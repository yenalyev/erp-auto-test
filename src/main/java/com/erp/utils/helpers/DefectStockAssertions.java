package com.erp.utils.helpers;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.StorageItemResponse;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Stock / batch assertions for defect ("Брак") flows. Defects debit storage on create and
 * credit it back on delete, so tests capture before/after snapshots of the resource amount and,
 * for FIFO scenarios, the ordered list of batches.
 */
@UtilityClass
public class DefectStockAssertions {

    /** Total amount of a single resource on a storage. */
    public static double resourceStock(ApiExecutor apiExecutor,
                                       Long storageId,
                                       UserRole role,
                                       Long resourceId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_GET, role, String.valueOf(storageId));
        List<StorageItemResponse> items = DatabaseIntegrityValidator.extractList(
                response, StorageItemResponse.class);
        return items.stream()
                .filter(i -> i.getResource() != null && resourceId.equals(i.getResource().getId()))
                .map(i -> i.getAmount() != null ? i.getAmount() : 0.0)
                .findFirst()
                .orElse(0.0);
    }

    public static void assertStockDebited(double before,
                                          double after,
                                          double expectedDebit,
                                          String context) {
        Allure.step(String.format(
                "Залишок зменшився на %.2f од. (%s): %.2f → %.2f", expectedDebit, context, before, after), () ->
                assertThat(after)
                        .as("Залишок після створення браку (%s)", context)
                        .isCloseTo(before - expectedDebit, within(0.01)));
    }

    public static void assertStockRestored(double before,
                                           double after,
                                           String context) {
        Allure.step(String.format(
                "Залишок відновлено (%s): було %.2f, стало %.2f", context, before, after), () ->
                assertThat(after)
                        .as("Залишок після видалення/скасування браку (%s)", context)
                        .isCloseTo(before, within(0.01)));
    }

    /** Ordered list of non-produced batches for a resource (FIFO order as returned by the API). */
    public static List<StorageItemBatchResponse> nonProducedBatches(ApiExecutor apiExecutor,
                                                                    Long storageId,
                                                                    UserRole role,
                                                                    Long resourceId,
                                                                    String phaseLabel) {
        return batches(apiExecutor, storageId, role, resourceId,
                ApiEndpointDefinition.STORAGE_INVENTORY_BATCHES_GET_NON_PRODUCED, phaseLabel);
    }

    /** Ordered list of produced batches for a resource. */
    public static List<StorageItemBatchResponse> producedBatches(ApiExecutor apiExecutor,
                                                                 Long storageId,
                                                                 UserRole role,
                                                                 Long resourceId,
                                                                 String phaseLabel) {
        return batches(apiExecutor, storageId, role, resourceId,
                ApiEndpointDefinition.STORAGE_INVENTORY_BATCHES_GET, phaseLabel);
    }

    public static double batchAmount(List<StorageItemBatchResponse> batches, String batchNumber) {
        return batches.stream()
                .filter(b -> batchNumber.equals(b.getBatchNumber()))
                .map(b -> b.getAmount() != null ? b.getAmount() : 0.0)
                .findFirst()
                .orElse(0.0);
    }

    public static List<String> batchOrder(List<StorageItemBatchResponse> batches) {
        return batches.stream().map(StorageItemBatchResponse::getBatchNumber).toList();
    }

    private static List<StorageItemBatchResponse> batches(ApiExecutor apiExecutor,
                                                         Long storageId,
                                                         UserRole role,
                                                         Long resourceId,
                                                         ApiEndpointDefinition endpoint,
                                                         String phaseLabel) {
        Long storageItemId = ProductionStockAssertions.findStorageItemId(
                apiExecutor, storageId, role, resourceId);
        if (storageItemId == null) {
            return List.of();
        }
        Response response = apiExecutor.execute(endpoint, role, null, storageId, storageItemId);
        List<StorageItemBatchResponse> batches = response.jsonPath()
                .getList("", StorageItemBatchResponse.class);
        if (batches == null) {
            batches = List.of();
        }
        String table = batches.stream()
                .map(b -> String.format("«%s»: %.2f", b.getBatchNumber(),
                        b.getAmount() != null ? b.getAmount() : 0.0))
                .collect(Collectors.joining("\n"));
        Allure.addAttachment("Партії ресурсу id=" + resourceId + " — " + phaseLabel, "text/plain", table);
        return batches;
    }
}
