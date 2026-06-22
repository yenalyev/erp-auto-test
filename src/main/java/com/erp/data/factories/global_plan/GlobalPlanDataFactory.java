package com.erp.data.factories.global_plan;

import com.erp.data.FakerProvider;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.ResourceResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public class GlobalPlanDataFactory {

    public static GlobalPlanRequest.GlobalPlanRequestBuilder createPlan(
            int month,
            int year,
            Long resourceId,
            double amount) {
        return GlobalPlanRequest.builder()
                .description("GP-" + month + "/" + year + "-" + System.currentTimeMillis())
                .month(month)
                .year(year)
                .output(List.of(new ResourceUsageRequest(resourceId, amount)));
    }

    public static GlobalPlanRequest.GlobalPlanRequestBuilder fromExisting(GlobalPlanResponse response) {
        return GlobalPlanRequest.builder()
                .description(response.getDescription() + " UPDATED")
                .month(response.getMonth())
                .year(response.getYear())
                .output(response.getOutput().stream()
                        .map(u -> new ResourceUsageRequest(u.getResource().getId(), u.getAmount()))
                        .toList());
    }

    public static YearMonth uniquePlanPeriod(int monthsAhead) {
        return YearMonth.now().plusMonths(monthsAhead);
    }

    public static DecompositionRequest.DecompositionAssignmentRequest assignment(
            Long storageId, Long technologicalMapId, String amount) {
        return DecompositionRequest.DecompositionAssignmentRequest.builder()
                .storageId(storageId)
                .technologicalMapId(technologicalMapId)
                .amount(new BigDecimal(amount))
                .build();
    }

    public static DecompositionRequest.DecompositionItemRequest item(
            Long resourceId,
            DecompositionRequest.DecompositionAssignmentRequest... assignments) {
        return DecompositionRequest.DecompositionItemRequest.builder()
                .resourceId(resourceId)
                .assignments(List.of(assignments))
                .build();
    }

    public static DecompositionRequest.DecompositionBlockRequest block(
            DecompositionRequest.DecompositionItemRequest... items) {
        return DecompositionRequest.DecompositionBlockRequest.builder()
                .items(List.of(items))
                .build();
    }

    public static DecompositionRequest completeDecomposition(GlobalPlanChainContext chain) {
        return DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM1().getId(), "10"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceB().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM2().getId(), "12"),
                                GlobalPlanDataFactory.assignment(chain.getL2StorageId(), chain.getMapM2().getId(), "8"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceC().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM3().getId(), "10")))
                ))
                .build();
    }

    public static DecompositionRequest emptyFirstBlock(GlobalPlanChainContext chain) {
        return DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceA().getId()))
                ))
                .build();
    }

    public static GlobalPlanRequest nonProducibleOutput(ResourceResponse resource, int month, int year) {
        return GlobalPlanRequest.builder()
                .description("Non-producible " + FakerProvider.ukrainian().commerce().department())
                .month(month)
                .year(year)
                .output(List.of(new ResourceUsageRequest(resource.getId(), 10.0)))
                .build();
    }
}
