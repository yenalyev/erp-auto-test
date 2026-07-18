package com.erp.utils.helpers;



import com.erp.api.clients.ApiExecutor;

import com.erp.api.endpoints.ApiEndpointDefinition;

import com.erp.enums.UserRole;

import com.erp.models.response.MultiLocationStorageItemResponse;

import com.erp.models.response.StorageAmountResponse;

import com.erp.models.response.StorageItemBatchResponse;

import io.qameta.allure.Allure;

import io.restassured.response.Response;

import lombok.experimental.UtilityClass;



import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.Objects;

import java.util.Optional;

import java.util.Set;

import java.util.stream.Collectors;



import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.within;



@UtilityClass

public class ProductionStockAssertions {



    public record StockSnapshot(Map<Long, Double> amounts, Map<Long, String> resourceNames) {

        public double amountOf(Long resourceId) {

            return amounts.getOrDefault(resourceId, 0.0);

        }



        public String nameOf(Long resourceId) {

            return resourceNames.getOrDefault(resourceId, "ресурс id=" + resourceId);

        }

    }



    /**
     * Exact stock snapshot for a set of resources on a single storage — uses the backend's
     * {@code resourceIds} filter (no scanning/pagination, works regardless of storage size).
     */
    public static StockSnapshot capture(ApiExecutor apiExecutor,

                                        Long storageId,

                                        UserRole role,

                                        Set<Long> resourceIds,

                                        String phaseLabel) {

        return Allure.step("Знімок залишків на складі " + storageId + " (" + phaseLabel + ")", () -> {

            Map<Long, Double> amounts = new LinkedHashMap<>();

            Map<Long, String> names = new LinkedHashMap<>();

            Response response = apiExecutor.executeWithQueryParams(
                    ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET,
                    role,
                    Map.of(
                            "locations", storageId,
                            "resourceIds", List.copyOf(resourceIds),
                            "size", Math.max(resourceIds.size(), 1)));
            List<MultiLocationStorageItemResponse> content = parseMultiInventoryContent(response);

            if (content != null) {
                for (MultiLocationStorageItemResponse item : content) {
                    if (item == null || item.getResource() == null || item.getResource().getId() == null) {
                        continue;
                    }
                    Long resourceId = item.getResource().getId();
                    if (!resourceIds.contains(resourceId)) {
                        continue;
                    }
                    double amount = item.getLocations() == null ? 0.0 : item.getLocations().stream()
                            .filter(loc -> loc.getStorage() != null && storageId.equals(loc.getStorage().getId()))
                            .map(StorageAmountResponse::getAmount)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(0.0);
                    amounts.put(resourceId, amount);
                    if (item.getResource().getName() != null) {
                        names.put(resourceId, item.getResource().getName().trim());
                    }
                }
            }

            for (Long resourceId : resourceIds) {

                amounts.putIfAbsent(resourceId, 0.0);

                names.putIfAbsent(resourceId, "ресурс id=" + resourceId);

            }



            logStockPhase(amounts, names, phaseLabel);

            attachStockTable(phaseLabel, amounts, names);

            return new StockSnapshot(amounts, names);

        });

    }



    public static void assertDelta(StockSnapshot before,

                                   StockSnapshot after,

                                   Map<Long, Double> expectedDelta,

                                   Long outputResourceId) {

        Allure.step("Перевірка зміни залишків після виробництва", () -> {

            StringBuilder report = new StringBuilder();

            report.append("Ресурс | До | Після | Факт Δ | Очікувано Δ\n");

            report.append("---|---|---:|---:|---:\n");



            expectedDelta.forEach((resourceId, expectedChange) -> {

                double beforeAmount = before.amountOf(resourceId);

                double afterAmount = after.amountOf(resourceId);

                double actualChange = afterAmount - beforeAmount;

                String name = before.nameOf(resourceId);

                String kind = resourceKind(resourceId, outputResourceId);



                String stepTitle = String.format(

                        "Запас %s «%s» (id=%d): до виробництва %s, після виробництва %s — зміна %s (очікувано %s)",

                        kind, name, resourceId,

                        formatUnits(beforeAmount),

                        formatUnits(afterAmount),

                        formatSignedUnits(actualChange),

                        formatSignedUnits(expectedChange));



                Allure.step(stepTitle, () ->

                        assertThat(actualChange)

                                .as("Зміна залишку для %s «%s» (id=%d)", kind, name, resourceId)

                                .isCloseTo(expectedChange, within(0.01)));



                report.append(String.format(

                        "%s «%s» | %s | %s | %s | %s%n",

                        kind, name,

                        formatUnits(beforeAmount),

                        formatUnits(afterAmount),

                        formatSignedUnits(actualChange),

                        formatSignedUnits(expectedChange)));

            });



            Allure.addAttachment("Підсумок залишків", "text/plain", report.toString());

        });

    }



    /**
     * Exact stock lookup for a single resource on a single storage — uses the backend's
     * {@code resourceIds} filter (no scanning/pagination, works regardless of storage size).
     */
    public static double resourceStockExact(ApiExecutor apiExecutor,
                                            Long storageId,
                                            UserRole role,
                                            Long resourceId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_INVENTORY_MULTI_GET,
                role,
                Map.of("locations", storageId, "resourceIds", resourceId, "size", 1));
        List<MultiLocationStorageItemResponse> content = parseMultiInventoryContent(response);
        if (content.isEmpty()) {
            return 0.0;
        }
        return content.stream()
                .filter(Objects::nonNull)
                .map(MultiLocationStorageItemResponse::getLocations)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(loc -> loc.getStorage() != null && storageId.equals(loc.getStorage().getId()))
                .map(StorageAmountResponse::getAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0.0);
    }

    /**
     * Exact batch lookup by {@code storageId + resourceId} — no {@code storageItemId} resolution
     * needed, so it can never hit {@code .../inventory/null/batches}.
     */
    public static List<StorageItemBatchResponse> queryBatches(ApiExecutor apiExecutor,
                                                              Long storageId,
                                                              UserRole role,
                                                              Long resourceId,
                                                              Boolean isProduced,
                                                              String batchNumber) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageId", storageId);
        params.put("resourceId", resourceId);
        if (isProduced != null) {
            params.put("isProduced", isProduced);
        }
        if (batchNumber != null) {
            params.put("batchNumber", batchNumber);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.STORAGE_ITEM_BATCHES_GET_BY_RESOURCE, role, params);
        return ApiResponseHelper.parseList(response, StorageItemBatchResponse.class,
                "GET storage-items/batches (storageId=" + storageId + ", resourceId=" + resourceId + ")");
    }

    public static Optional<StorageItemBatchResponse> findBatch(ApiExecutor apiExecutor,
                                                               Long storageId,
                                                               UserRole role,
                                                               Long resourceId,
                                                               String batchNumber,
                                                               Boolean isProduced) {
        return queryBatches(apiExecutor, storageId, role, resourceId, isProduced, batchNumber).stream()
                .filter(b -> batchNumber.equals(b.getBatchNumber()))
                .findFirst();
    }



  /**
   * CREW storages may return 403 (plain text) when JWT lacks {@code inventory-list::{crew}::read}
   * or the location is empty — treat as zero stock instead of failing JSON parsing.
   */
    private static List<MultiLocationStorageItemResponse> parseMultiInventoryContent(Response response) {
        if (response == null) {
            return List.of();
        }
        int status = response.statusCode();
        if (status == 403 || status == 404) {
            return List.of();
        }
        String contentType = response.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("json")) {
            return List.of();
        }
        List<MultiLocationStorageItemResponse> content = response.jsonPath()
                .getList("content", MultiLocationStorageItemResponse.class);
        return content != null ? content : List.of();
    }

    private static void logStockPhase(Map<Long, Double> amounts,

                                      Map<Long, String> names,

                                      String phaseLabel) {

        for (Map.Entry<Long, Double> entry : amounts.entrySet()) {

            Long resourceId = entry.getKey();

            String name = names.get(resourceId);

            String message = String.format(

                    "Запас «%s» (id=%d) %s: %s",

                    name, resourceId, phaseLabel, formatUnits(entry.getValue()));

            Allure.step(message);

        }

    }



    private static void attachStockTable(String phaseLabel,

                                         Map<Long, Double> amounts,

                                         Map<Long, String> names) {

        String table = amounts.entrySet().stream()

                .map(e -> String.format("«%s» (id=%d): %s",

                        names.get(e.getKey()), e.getKey(), formatUnits(e.getValue())))

                .collect(Collectors.joining("\n"));

        Allure.addAttachment("Залишки — " + phaseLabel, "text/plain", table);

    }



    private static String resourceKind(Long resourceId, Long outputResourceId) {

        if (outputResourceId != null && outputResourceId.equals(resourceId)) {

            return "продукції";

        }

        return "компонента";

    }



    private static String formatUnits(double value) {

        if (Math.abs(value - Math.rint(value)) < 0.001) {

            return String.format("%.0f од.", value);

        }

        return String.format("%.2f од.", value);

    }



    private static String formatSignedUnits(double value) {

        if (value > 0) {

            return "+" + formatUnits(value);

        }

        return formatUnits(value);

    }



}


