package com.erp.utils.helpers;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageItemBatchResponse;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@UtilityClass
public class RelocationBatchAssertions {

    public record BatchSnapshot(String batchNumber, double amount, Long resourceId, Long storageItemId) {
    }

    public static BatchSnapshot captureBatch(ApiExecutor apiExecutor,
                                             Long storageId,
                                             UserRole role,
                                             Long resourceId,
                                             String batchNumber,
                                             boolean isProduced,
                                             String phaseLabel) {
        return Allure.step("Знімок партії «" + batchNumber + "» (" + phaseLabel + ")", () -> {
            Long storageItemId = ProductionStockAssertions.findStorageItemId(
                    apiExecutor, storageId, role, resourceId);
            double amount = loadBatchAmount(apiExecutor, storageId, role, storageItemId, batchNumber, isProduced);

            Allure.parameter("batchNumber", batchNumber);
            Allure.parameter("batchAmount", amount);
            Allure.parameter("resourceId", resourceId);
            Allure.parameter("storageItemId", storageItemId);

            String details = String.format(
                    "Партія «%s» ресурсу id=%d: %.2f од. (%s)",
                    batchNumber, resourceId, amount, phaseLabel);
            Allure.addAttachment("Партія — " + phaseLabel, "text/plain", details);

            return new BatchSnapshot(batchNumber, amount, resourceId, storageItemId);
        });
    }

    public static void assertBatchCredited(BatchSnapshot before,
                                           BatchSnapshot after,
                                           double expectedDelta,
                                           String explanation) {
        Allure.step(String.format(
                "Партія «%s» збільшилась на +%.2f од. — %s",
                after.batchNumber(), expectedDelta, explanation), () -> {
            double actualDelta = after.amount() - before.amount();
            Allure.parameter("batchBefore", before.amount());
            Allure.parameter("batchAfter", after.amount());
            Allure.parameter("expectedDelta", expectedDelta);
            assertThat(actualDelta)
                    .as("Приріст партії «%s»", after.batchNumber())
                    .isCloseTo(expectedDelta, within(0.01));
        });
    }

    public static void assertBatchDebited(BatchSnapshot before,
                                          BatchSnapshot after,
                                          double expectedDebit,
                                          String explanation) {
        Allure.step(String.format(
                "Партія «%s» зменшилась на −%.2f од. — %s",
                after.batchNumber(), expectedDebit, explanation), () -> {
            double actualDelta = after.amount() - before.amount();
            assertThat(actualDelta)
                    .as("Списання з партії «%s»", after.batchNumber())
                    .isCloseTo(-expectedDebit, within(0.01));
        });
    }

    public static void assertBatchAbsent(ApiExecutor apiExecutor,
                                         Long storageId,
                                         UserRole role,
                                         Long resourceId,
                                         String batchNumber,
                                         boolean isProduced) {
        Allure.step("Партія «" + batchNumber + "» має бути відсутня або нульова", () -> {
            Long storageItemId = ProductionStockAssertions.findStorageItemId(
                    apiExecutor, storageId, role, resourceId);
            Optional<StorageItemBatchResponse> batch = findBatch(
                    apiExecutor, storageId, role, storageItemId, batchNumber, isProduced);
            if (batch.isPresent() && batch.get().getAmount() != null) {
                assertThat(batch.get().getAmount())
                        .as("Партія «%s» має бути нульовою", batchNumber)
                        .isCloseTo(0.0, within(0.01));
            }
        });
    }

    public static Optional<StorageItemBatchResponse> findBatch(ApiExecutor apiExecutor,
                                                               Long storageId,
                                                               UserRole role,
                                                               Long storageItemId,
                                                               String batchNumber,
                                                               boolean isProduced) {
        ApiEndpointDefinition endpoint = isProduced
                ? ApiEndpointDefinition.STORAGE_INVENTORY_BATCHES_GET
                : ApiEndpointDefinition.STORAGE_INVENTORY_BATCHES_GET_NON_PRODUCED;
        Response response = apiExecutor.execute(endpoint, role, null, storageId, storageItemId);
        List<StorageItemBatchResponse> batches = response.jsonPath()
                .getList("", StorageItemBatchResponse.class);
        if (batches == null) {
            return Optional.empty();
        }
        return batches.stream()
                .filter(b -> batchNumber.equals(b.getBatchNumber()))
                .findFirst();
    }

    private static double loadBatchAmount(ApiExecutor apiExecutor,
                                          Long storageId,
                                          UserRole role,
                                          Long storageItemId,
                                          String batchNumber,
                                          boolean isProduced) {
        return findBatch(apiExecutor, storageId, role, storageItemId, batchNumber, isProduced)
                .map(StorageItemBatchResponse::getAmount)
                .orElse(0.0);
    }
}
