package com.erp.data.factories.plan;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.FakerProvider;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

import static com.erp.data.RequestBodyFactory.register;

@Slf4j
public class PlanRequestBodyFactory {
    public static void registerStrategies() {
        // POST /api/v1/plan?storageId={id} - create simple plan
        register(ApiEndpointDefinition.PLAN_POST_CREATE, context -> {
            Long storeId = context.get(ContextKey.OWNER_1_STORAGE_ID);
            ResourceResponse resource = context.get(ContextKey.SHARED_RESOURCE);
            LocalDate from = LocalDate.of(2025, 11, 1);
            LocalDate to = LocalDate.of(2025, 11, 30);

            return PlanDataFactory.createSimplePlan(
                    storeId,
                    resource.getId(),
                    from,
                    to,
                    FakerProvider.price(10D, 1000D))
                    .build();
                }
        );


        register(ApiEndpointDefinition.PLAN_PUT_UPDATE, context -> {
                    List<PlanResponse> existingPlansFromContext = context.get(ContextKey.DYNAMIC_PLAN_LIST);
                    PlanResponse existingPlan = existingPlansFromContext.getFirst();

                    log.info("Existing plan for update {}", existingPlan);

                    LocalDate from = existingPlan.getFrom().plusDays(1);
                    LocalDate to = existingPlan.getTo().plusDays(1);

                    return PlanDataFactory.fromExisting(existingPlan)
                            .from(from)
                            .to(to)
                            .build();
                }
        );
    }


}
