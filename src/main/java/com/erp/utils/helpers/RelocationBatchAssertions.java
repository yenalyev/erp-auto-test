package com.erp.utils.helpers;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageItemBatchResponse;
import io.qameta.allure.Allure;
import lombok.experimental.UtilityClass;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@UtilityClass
public class RelocationBatchAssertions {

    public record BatchSnapshot(String batchNumber, double amount, Long resourceId) {
    }

    public static BatchSnapshot captureBatch(ApiExecutor apiExecutor,
                                             Long storageId,
                                             UserRole role,
                                             Long resourceId,
                                             String batchNumber,
                                             boolean isProduced,
                                             String phaseLabel) {
        return Allure.step("Знімок партії «" + batchNumber + "» (" + phaseLabel + ")", () -> {
            double amount = findBatch(apiExecutor, storageId, role, resourceId, batchNumber, isProduced)
                    .map(StorageItemBatchResponse::getAmount)
                    .orElse(0.0);

            Allure.parameter("batchNumber", batchNumber);
            Allure.parameter("batchAmount", amount);
            Allure.parameter("resourceId", resourceId);

            String details = String.format(
                    "Партія «%s» ресурсу id=%d: %.2f од. (%s)",
                    batchNumber, resourceId, amount, phaseLabel);
            Allure.addAttachment("Партія — " + phaseLabel, "text/plain", details);

            return new BatchSnapshot(batchNumber, amount, resourceId);
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
            Optional<StorageItemBatchResponse> batch = findBatch(
                    apiExecutor, storageId, role, resourceId, batchNumber, isProduced);
            if (batch.isPresent() && batch.get().getAmount() != null) {
                assertThat(batch.get().getAmount())
                        .as("Партія «%s» має бути нульовою", batchNumber)
                        .isCloseTo(0.0, within(0.01));
            }
        });
    }

    /** Exact lookup by {@code storageId + resourceId} — no {@code storageItemId} resolution needed. */
    public static Optional<StorageItemBatchResponse> findBatch(ApiExecutor apiExecutor,
                                                               Long storageId,
                                                               UserRole role,
                                                               Long resourceId,
                                                               String batchNumber,
                                                               boolean isProduced) {
        return ProductionStockAssertions.findBatch(apiExecutor, storageId, role, resourceId, batchNumber, isProduced);
    }
}
