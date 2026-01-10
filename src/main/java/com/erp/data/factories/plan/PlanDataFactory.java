package com.erp.data.factories.plan;

import com.erp.data.FakerProvider;
import com.erp.models.request.PlanRequest;
import com.erp.models.response.PlanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;

import java.time.LocalDate;

public class PlanDataFactory {

    public static PlanRequest.PlanRequestBuilder createSimplePlan(Long storageId,
                                                            Long resourceId,
                                                            LocalDate from,
                                                            LocalDate to,
                                                            Double amount) {
        if (null==storageId){
            throw new IllegalStateException("ERROR - Test Setup Error: 'storageId' is null ");
        }

        if (null==resourceId){
            throw new IllegalStateException("ERROR - Test Setup Error: 'resourceId' is null ");
        }

        if (null==from){
            throw new IllegalStateException("ERROR - Test Setup Error: LocalDate 'from' is null ");
        }

        if (null==to){
            throw new IllegalStateException("ERROR - Test Setup Error: LocalDate 'to' is null ");
        }

        if (null==amount){
            throw new IllegalStateException("ERROR - Test Setup Error: 'amount' is null ");
        }


        return PlanRequest.builder()
                .description(FakerProvider.ukrainian().commerce().department())
                .storageId(storageId)
                .resourceId(resourceId)
                .from(from)
                .to(to)
                .amount(amount);
    }


    /**
     * 🔥 Створює реквест на основі існуючої відповіді.
     * Це дозволяє взяти існуючий об'єкт і змінити в ньому лише одне поле.
     */
    public static PlanRequest.PlanRequestBuilder fromExisting(PlanResponse response) {
        return PlanRequest.builder()
                .description(response.getDescription() + " UPDATED")
                .storageId(response.getStorageId())
                .resourceId(response.getResourceId())
                .amount(response.getAmount())
                .from(response.getFrom())
                .to(response.getTo());
    }
}
