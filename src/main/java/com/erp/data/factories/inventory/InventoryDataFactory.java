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

    /** PUT payload that sets every listed resource amount to 0 so the location can be archived. */
    public static InventoryRequest zeroAll(List<StorageItemResponse> existingItems) {
        Map<Long, Double> zeros = new LinkedHashMap<>();
        if (existingItems != null) {
            for (StorageItemResponse item : existingItems) {
                if (item.getResource() != null && item.getResource().getId() != null
                        && item.getAmount() != null && item.getAmount() > 0) {
                    zeros.put(item.getResource().getId(), 0.0);
                }
            }
        }
        return seedAmounts(zeros);
    }

    public static InventoryRequest copyExcept(List<StorageItemResponse> existingItems, Long excludeResourceId) {
        Map<Long, Double> amounts = new LinkedHashMap<>();
        if (existingItems != null) {
            for (StorageItemResponse item : existingItems) {
                if (item.getResource() != null && item.getResource().getId() != null
                        && !item.getResource().getId().equals(excludeResourceId)) {
                    amounts.put(item.getResource().getId(), item.getAmount());
                }
            }
        }
        return seedAmounts(amounts);
    }
}
