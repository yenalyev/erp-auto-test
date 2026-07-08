package com.erp.data.factories.global_plan;

import com.erp.data.FakerProvider;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.DecompositionRequest;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.ResourceUsageResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * PUT body after Tab 1 edit: keep period/description base, append one more output line.
     */
    public static GlobalPlanRequest withAdditionalOutput(
            GlobalPlanResponse response,
            Long resourceId,
            double amount) {
        List<ResourceUsageRequest> outputs = new ArrayList<>(response.getOutput().stream()
                .map(u -> new ResourceUsageRequest(u.getResource().getId(), u.getAmount()))
                .toList());
        outputs.add(new ResourceUsageRequest(resourceId, amount));
        return GlobalPlanRequest.builder()
                .description(response.getDescription() + " UPDATED")
                .month(response.getMonth())
                .year(response.getYear())
                .output(outputs)
                .build();
    }

    /**
     * First {@code POST /decompose} body after edit Tab 1 save: block 0 = current {@code plan.output}.
     * Assignments are restored only from snapshot <em>block 0</em> (direct outputs at generate time).
     * A resource newly promoted to direct output (was only in deeper snapshot blocks) gets an empty
     * assignment list — matching UI {@code runChain} auto-assign, not block-1 production assignments.
     */
    public static DecompositionRequest uiStartSeed(GlobalPlanResponse plan) {
        Map<Long, List<DecompositionRequest.DecompositionAssignmentRequest>> block0Saved = new HashMap<>();
        DecompositionRequest snapshot = plan.getDecomposition();
        if (snapshot != null && snapshot.getBlocks() != null && !snapshot.getBlocks().isEmpty()) {
            DecompositionRequest.DecompositionBlockRequest block0 = snapshot.getBlocks().getFirst();
            if (block0.getItems() != null) {
                for (DecompositionRequest.DecompositionItemRequest item : block0.getItems()) {
                    List<DecompositionRequest.DecompositionAssignmentRequest> assignments =
                            item.getAssignments() != null ? item.getAssignments() : List.of();
                    block0Saved.put(item.getResourceId(), assignments);
                }
            }
        }

        List<DecompositionRequest.DecompositionItemRequest> items = new ArrayList<>();
        for (ResourceUsageResponse output : plan.getOutput()) {
            Long resourceId = output.getResource().getId();
            items.add(DecompositionRequest.DecompositionItemRequest.builder()
                    .resourceId(resourceId)
                    .assignments(new ArrayList<>(block0Saved.getOrDefault(resourceId, List.of())))
                    .build());
        }

        return DecompositionRequest.builder()
                .blocks(List.of(DecompositionRequest.DecompositionBlockRequest.builder()
                        .items(items)
                        .build()))
                .build();
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

    /**
     * Full A→B→C chain for output A=10 with single-output PRODUCTION maps:
     * M1 needs 20B, M2 needs 20C (single-output maps, no by-product credit on deployed envs).
     */
    public static DecompositionRequest completeDecomposition(GlobalPlanChainContext chain) {
        return DecompositionRequest.builder()
                .blocks(List.of(
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceA().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM1().getId(), "10"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceB().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM2().getId(), "12"),
                                GlobalPlanDataFactory.assignment(chain.getL2StorageId(), chain.getMapM2().getId(), "8"))),
                        GlobalPlanDataFactory.block(GlobalPlanDataFactory.item(chain.getResourceC().getId(),
                                GlobalPlanDataFactory.assignment(chain.getL1StorageId(), chain.getMapM3().getId(), "20")))
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
