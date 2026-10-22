package com.erp.data.factories.inventory;

import com.erp.models.request.InventoryRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.StorageItemResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InventoryDataFactory {

    public static final int COMMENT_MAX_LENGTH = 1000;

    public static InventoryRequest withComment(InventoryRequest request, String comment) {
        if (request == null) {
            return InventoryRequest.builder().comment(comment).build();
        }
        return request.toBuilder().comment(comment).build();
    }

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

    public static InventoryRequest mergeWithExisting(List<StorageItemResponse> existingItems,
                                                     Map<Long, Double> targetAmountsByResourceId,
                                                     String comment) {
        return withComment(mergeWithExisting(existingItems, targetAmountsByResourceId), comment);
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

    /** Match form deletion: retain the removed row's snapshot version and send an explicit tombstone. */
    public static InventoryRequest copyExcept(List<StorageItemResponse> existingItems, Long excludeResourceId) {
        List<ResourceUsageRequest> resources = new ArrayList<>();
        List<Long> knownResourceIds = new ArrayList<>();
        if (existingItems != null) {
            for (StorageItemResponse item : existingItems) {
                if (item.getResource() != null && item.getResource().getId() != null) {
                    Long resourceId = item.getResource().getId();
                    boolean removed = resourceId.equals(excludeResourceId);
                    knownResourceIds.add(resourceId);
                    BigDecimal amount = item.getAmount() == null ? null : BigDecimal.valueOf(item.getAmount());
                    resources.add(ResourceUsageRequest.builder()
                            .resourceId(resourceId)
                            .amount(removed ? BigDecimal.ZERO : amount)
                            .version(item.getVersion())
                            .removed(removed ? Boolean.TRUE : null)
                            .build());
                }
            }
        }
        return InventoryRequest.builder().resources(resources).knownResourceIds(knownResourceIds).build();
    }

    public static String commentOfExactLength(int length) {
        if (length <= 0) {
            return "";
        }
        return "x".repeat(length);
    }
}
