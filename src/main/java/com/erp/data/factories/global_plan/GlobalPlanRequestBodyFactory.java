package com.erp.data.factories.global_plan;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.models.common.GlobalPlanChainContext;
import com.erp.models.request.GlobalPlanRequest;
import com.erp.models.request.RequirementsExportRequest;
import com.erp.models.response.GlobalPlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import lombok.extern.slf4j.Slf4j;

import java.time.YearMonth;
import java.util.List;

import static com.erp.data.RequestBodyFactory.register;

@Slf4j
public class GlobalPlanRequestBodyFactory {

    public static void registerStrategies() {
        register(ApiEndpointDefinition.GLOBAL_PLAN_POST_CREATE, context -> {
            GlobalPlanChainContext chain = context.get(ContextKey.GLOBAL_PLAN_CHAIN);
            if (chain != null) {
                YearMonth period = GlobalPlanDataFactory.uniquePlanPeriod(6);
                return GlobalPlanDataFactory.createPlan(
                        period.getMonthValue(),
                        period.getYear(),
                        chain.getResourceA().getId(),
                        10.0).build();
            }
            return rbacFallbackCreate(context);
        });

        register(ApiEndpointDefinition.GLOBAL_PLAN_PUT_UPDATE, context -> {
            GlobalPlanResponse existing = context.get(ContextKey.GLOBAL_PLAN);
            if (existing != null) {
                return GlobalPlanDataFactory.fromExisting(existing).build();
            }
            throw new IllegalStateException("GLOBAL_PLAN is required for UPDATE_GLOBAL_PLAN body");
        });

        register(ApiEndpointDefinition.GLOBAL_PLAN_DECOMPOSE, context -> {
            GlobalPlanChainContext chain = requireChain(context);
            return GlobalPlanDataFactory.completeDecomposition(chain);
        });

        register(ApiEndpointDefinition.GLOBAL_PLAN_GENERATE, context -> {
            GlobalPlanChainContext chain = requireChain(context);
            return GlobalPlanDataFactory.completeDecomposition(chain);
        });

        register(ApiEndpointDefinition.GLOBAL_PLAN_REQUIREMENTS_EXPORT, context ->
                RequirementsExportRequest.builder()
                        .periodLabel("RBAC export")
                        .semiFinished(List.of(
                                RequirementsExportRequest.RequirementsExportRow.builder()
                                        .name("Test semi")
                                        .requiredAmount(1.0)
                                        .unitShortName("од")
                                        .totalStock(0.0)
                                        .build()))
                        .build());
    }

    private static GlobalPlanChainContext requireChain(com.erp.test_context.TestContext context) {
        GlobalPlanChainContext chain = context.get(ContextKey.GLOBAL_PLAN_CHAIN);
        if (chain == null) {
            throw new IllegalStateException("GLOBAL_PLAN_CHAIN is required");
        }
        return chain;
    }

    private static GlobalPlanRequest rbacFallbackCreate(com.erp.test_context.TestContext context) {
        ResourceResponse resource = context.get(ContextKey.SHARED_RESOURCE);
        TechnologicalMapResponse techMap = context.get(ContextKey.DYNAMIC_TECH_MAP);
        Long resourceId = techMap != null && techMap.getOutput() != null && !techMap.getOutput().isEmpty()
                ? techMap.getOutput().getFirst().getResource().getId()
                : resource.getId();
        YearMonth period = GlobalPlanDataFactory.uniquePlanPeriod(6);
        return GlobalPlanDataFactory.createPlan(
                period.getMonthValue(),
                period.getYear(),
                resourceId,
                10.0).build();
    }
}
