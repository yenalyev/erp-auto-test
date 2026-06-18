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
public class ProductionBatchAssertions {

    public record BatchSnapshot(String batchNumber, double amount, Long outputResourceId, Long storageItemId) {
    }

    public static BatchSnapshot captureProducedBatch(ApiExecutor apiExecutor,
                                                     Long storageId,
                                                     UserRole role,
                                                     Long outputResourceId,
                                                     String batchNumber,
                                                     String phaseLabel) {
        return Allure.step("Знімок виробничої партії «" + batchNumber + "» (" + phaseLabel + ")", () -> {
            Long storageItemId = ProductionStockAssertions.findStorageItemId(
                    apiExecutor, storageId, role, outputResourceId);
            double amount = loadProducedBatchAmount(apiExecutor, storageId, role, storageItemId, batchNumber);

            Allure.parameter("batchNumber", batchNumber);
            Allure.parameter("batchAmount", amount);
            Allure.parameter("outputResourceId", outputResourceId);
            Allure.parameter("storageItemId", storageItemId);

            String details = String.format(
                    "Партія «%s» продукту id=%d: %.2f од. (%s)",
                    batchNumber, outputResourceId, amount, phaseLabel);
            Allure.addAttachment("Партія — " + phaseLabel, "text/plain", details);
            Allure.step(details);

            return new BatchSnapshot(batchNumber, amount, outputResourceId, storageItemId);
        });
    }

    public static void assertProducedBatchAmount(BatchSnapshot before,
                                                BatchSnapshot after,
                                                double expectedAfterAmount,
                                                String explanation) {
        Allure.step(String.format(
                "Партія «%s»: було %.2f од. → очікується %.2f од. (%s)",
                after.batchNumber(), before.amount(), expectedAfterAmount, explanation), () -> {
            Allure.parameter("batchBefore", before.amount());
            Allure.parameter("batchAfterActual", after.amount());
            Allure.parameter("batchAfterExpected", expectedAfterAmount);

            assertThat(after.amount())
                    .as("Розмір виробничої партії «%s»", after.batchNumber())
                    .isCloseTo(expectedAfterAmount, within(0.01));
        });
    }

    public static void assertProducedBatchIncreasedBy(BatchSnapshot before,
                                                    BatchSnapshot after,
                                                    double expectedDelta,
                                                    String explanation) {
        double actualDelta = after.amount() - before.amount();
        Allure.step(String.format(
                "Партія «%s» збільшилась на %s (факт %s, очікувано +%s) — %s",
                after.batchNumber(),
                formatSigned(expectedDelta),
                formatSigned(actualDelta),
                formatSigned(expectedDelta),
                explanation), () -> {
            Allure.parameter("batchBefore", before.amount());
            Allure.parameter("batchAfter", after.amount());
            Allure.parameter("expectedDelta", expectedDelta);
            Allure.parameter("actualDelta", actualDelta);

            assertThat(actualDelta)
                    .as("Приріст партії «%s»", after.batchNumber())
                    .isCloseTo(expectedDelta, within(0.01));
        });
    }

    public static void assertProducedBatchAbsent(ApiExecutor apiExecutor,
                                                 Long storageId,
                                                 UserRole role,
                                                 Long outputResourceId,
                                                 String batchNumber) {
        Allure.step("Партія «" + batchNumber + "» має бути видалена зі складу", () -> {
            Long storageItemId = ProductionStockAssertions.findStorageItemId(
                    apiExecutor, storageId, role, outputResourceId);
            Optional<StorageItemBatchResponse> batch = findProducedBatch(
                    apiExecutor, storageId, role, storageItemId, batchNumber);

            assertThat(batch)
                    .as("Виробнича партія «%s» не повинна існувати", batchNumber)
                    .isEmpty();
            Allure.step("Партія «" + batchNumber + "» відсутня серед produced batches — OK");
        });
    }

    public static Optional<StorageItemBatchResponse> findProducedBatch(ApiExecutor apiExecutor,
                                                                     Long storageId,
                                                                     UserRole role,
                                                                     Long storageItemId,
                                                                     String batchNumber) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.STORAGE_INVENTORY_BATCHES_GET,
                role,
                null,
                storageId,
                storageItemId);
        List<StorageItemBatchResponse> batches = response.jsonPath()
                .getList("", StorageItemBatchResponse.class);
        if (batches == null) {
            return Optional.empty();
        }
        return batches.stream()
                .filter(b -> batchNumber.equals(b.getBatchNumber()))
                .findFirst();
    }

    private static double loadProducedBatchAmount(ApiExecutor apiExecutor,
                                                  Long storageId,
                                                  UserRole role,
                                                  Long storageItemId,
                                                  String batchNumber) {
        return findProducedBatch(apiExecutor, storageId, role, storageItemId, batchNumber)
                .map(StorageItemBatchResponse::getAmount)
                .orElse(0.0);
    }

    private static String formatSigned(double value) {
        if (value > 0) {
            return "+" + String.format("%.2f", value);
        }
        return String.format("%.2f", value);
    }
}
