package com.erp.data.factories.inventory;

import com.erp.models.request.InventoryRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.StorageItemResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InventoryDataFactory {

    public static InventoryRequest mergeWithExisting(List<StorageItemResponse> existingItems,
                                                     Map<Long, Double> targetAmountsByResourceId) {
        Map<Long, Double> merged = new LinkedHashMap<>();

        if (existingItems != null) {
            for (StorageItemResponse item : existingItems) {
                if (item.getResource() != null && item.getResource().getId() != null) {
                    merged.put(item.getResource().getId(), item.getAmount());
                }
            }
        }

        targetAmountsByResourceId.forEach(merged::put);

        List<ResourceUsageRequest> resources = new ArrayList<>();
        merged.forEach((resourceId, amount) ->
                resources.add(new ResourceUsageRequest(resourceId, amount)));

        return InventoryRequest.builder().resources(resources).build();
    }

    public static InventoryRequest seedAmounts(Map<Long, Double> amountsByResourceId) {
        List<ResourceUsageRequest> resources = amountsByResourceId.entrySet().stream()
                .map(e -> new ResourceUsageRequest(e.getKey(), e.getValue()))
                .toList();
        return InventoryRequest.builder().resources(resources).build();
    }
}
