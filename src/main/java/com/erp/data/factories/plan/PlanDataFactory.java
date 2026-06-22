package com.erp.data.factories.plan;

import com.erp.data.FakerProvider;
import com.erp.models.request.PlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import lombok.NonNull;

import java.util.List;

public class PlanDataFactory {

    public static PlanRequest.PlanRequestBuilder createSimplePlan(Long storageId,
                                                                  Long resourceId,
                                                                  int month,
                                                                  int year,
                                                                  Double amount) {
        if (storageId == null) {
            throw new IllegalStateException("ERROR - Test Setup Error: 'storageId' is null");
        }
        if (resourceId == null) {
            throw new IllegalStateException("ERROR - Test Setup Error: 'resourceId' is null");
        }
        if (amount == null) {
            throw new IllegalStateException("ERROR - Test Setup Error: 'amount' is null");
        }

        return PlanRequest.builder()
                .description(FakerProvider.ukrainian().commerce().department())
                .storageId(storageId)
                .month(month)
                .year(year)
                .output(List.of(new ResourceUsageRequest(resourceId, amount)));
    }

    public static PlanRequest.PlanRequestBuilder fromExisting(@NonNull PlanResponse response) {
        List<ResourceUsageRequest> output = response.getOutput() == null
                ? List.of()
                : response.getOutput().stream()
                .map(u -> new ResourceUsageRequest(u.getResource().getId(), u.getAmount()))
                .toList();

        return PlanRequest.builder()
                .description(response.getDescription() + " UPDATED")
                .storageId(response.getStorage() != null ? response.getStorage().getId() : null)
                .month(response.getMonth())
                .year(response.getYear())
                .output(output);
    }

    public static PlanRequest.PlanRequestBuilder forResource(Long storageId,
                                                             ResourceResponse resource,
                                                             int month,
                                                             int year,
                                                             double amount) {
        return createSimplePlan(storageId, resource.getId(), month, year, amount);
    }
}
