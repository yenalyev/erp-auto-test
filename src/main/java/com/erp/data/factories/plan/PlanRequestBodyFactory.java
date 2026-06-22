package com.erp.data.factories.plan;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.FakerProvider;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import lombok.extern.slf4j.Slf4j;

import java.time.YearMonth;
import java.util.List;

import static com.erp.data.RequestBodyFactory.register;

@Slf4j
public class PlanRequestBodyFactory {
    public static void registerStrategies() {
        register(ApiEndpointDefinition.PLAN_POST_CREATE, context -> {
            Long storeId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            ResourceResponse resource = context.get(ContextKey.SHARED_RESOURCE);
            YearMonth period = YearMonth.now().plusMonths(1);

            return PlanDataFactory.createSimplePlan(
                    storeId,
                    resource.getId(),
                    period.getMonthValue(),
                    period.getYear(),
                    FakerProvider.price(10D, 1000D))
                    .build();
        });

        register(ApiEndpointDefinition.PLAN_PUT_UPDATE, context -> {
            List<PlanResponse> existingPlansFromContext = context.get(ContextKey.DYNAMIC_PLAN_LIST);
            PlanResponse existingPlan = existingPlansFromContext.getFirst();
            log.info("Existing plan for update {}", existingPlan);

            return PlanDataFactory.fromExisting(existingPlan)
                    .description(existingPlan.getDescription() + " RBAC-UPDATE")
                    .build();
        });
    }
}
