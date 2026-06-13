package com.erp.utils.helpers;



import com.erp.api.clients.ApiExecutor;

import com.erp.api.endpoints.ApiEndpointDefinition;

import com.erp.enums.UserRole;

import com.erp.models.response.StorageItemResponse;

import io.qameta.allure.Allure;

import io.restassured.response.Response;

import lombok.experimental.UtilityClass;



import java.util.HashMap;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

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



    public static StockSnapshot capture(ApiExecutor apiExecutor,

                                        Long storageId,

                                        UserRole role,

                                        Set<Long> resourceIds,

                                        String phaseLabel) {

        return Allure.step("Знімок залишків на складі " + storageId + " (" + phaseLabel + ")", () -> {

            Map<Long, Double> amounts = new LinkedHashMap<>();

            Map<Long, String> names = new LinkedHashMap<>();



            Map<Long, Double> tracked = loadAmountsForResources(

                    apiExecutor, storageId, role, resourceIds,

                    ApiEndpointDefinition.STORAGE_INVENTORY_GET_TRACKED, names);

            amounts.putAll(tracked);



            if (!amounts.keySet().containsAll(resourceIds)) {

                loadAmountsForResources(

                        apiExecutor, storageId, role, resourceIds,

                        ApiEndpointDefinition.STORAGE_INVENTORY_GET, names)

                        .forEach((id, amount) -> amounts.putIfAbsent(id, amount));

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



    public static Long findStorageItemId(ApiExecutor apiExecutor,

                                         Long storageId,

                                         UserRole role,

                                         Long resourceId) {

        Long fromPage = findStorageItemIdInResponse(apiExecutor, storageId, role, resourceId,

                ApiEndpointDefinition.STORAGE_INVENTORY_GET_TRACKED);

        if (fromPage != null) {

            return fromPage;

        }

        return findStorageItemIdInResponse(apiExecutor, storageId, role, resourceId,

                ApiEndpointDefinition.STORAGE_INVENTORY_GET);

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



    private static Map<Long, Double> loadAmountsForResources(ApiExecutor apiExecutor,

                                                             Long storageId,

                                                             UserRole role,

                                                             Set<Long> resourceIds,

                                                             ApiEndpointDefinition endpoint,

                                                             Map<Long, String> namesOut) {

        Response response = apiExecutor.execute(endpoint, role, String.valueOf(storageId));

        List<StorageItemResponse> items = ApiResponseHelper.parseList(

                response, StorageItemResponse.class, "GET inventory for storage " + storageId);

        Map<Long, Double> amounts = new HashMap<>();

        if (items == null) {

            return amounts;

        }

        for (StorageItemResponse item : items) {

            if (item.getResource() != null && resourceIds.contains(item.getResource().getId())) {

                Long id = item.getResource().getId();

                amounts.put(id, item.getAmount() != null ? item.getAmount() : 0.0);

                if (item.getResource().getName() != null) {

                    namesOut.put(id, item.getResource().getName().trim());

                }

            }

        }

        return amounts;

    }



    private static Long findStorageItemIdInResponse(ApiExecutor apiExecutor,

                                                    Long storageId,

                                                    UserRole role,

                                                    Long resourceId,

                                                    ApiEndpointDefinition endpoint) {

        Response response = apiExecutor.execute(endpoint, role, String.valueOf(storageId));

        List<StorageItemResponse> items = ApiResponseHelper.parseList(

                response, StorageItemResponse.class, "GET inventory item id");

        if (items == null) {

            return null;

        }

        return items.stream()

                .filter(i -> i.getResource() != null && resourceId.equals(i.getResource().getId()))

                .map(StorageItemResponse::getId)

                .findFirst()

                .orElse(null);

    }

}


