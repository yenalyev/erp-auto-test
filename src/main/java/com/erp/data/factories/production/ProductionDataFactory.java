package com.erp.data.factories.production;

import com.erp.data.FakerProvider;
import com.erp.models.request.ManufacturingItemRequest;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.request.ProcessResourceOutputRequest;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.TechnologicalMapResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductionDataFactory {

    public static ManufacturingListRequest buildCreateRequest(TechnologicalMapResponse techMap, double productionAmount) {
        return buildCreateRequest(techMap, productionAmount, LocalDate.now(), uniqueBatchNumber());
    }

    public static ManufacturingListRequest buildCreateRequest(TechnologicalMapResponse techMap,
                                                              double productionAmount,
                                                              LocalDate date,
                                                              String batchNumber) {
        if (techMap == null) {
            throw new IllegalStateException("ERROR - Test Setup Error: 'techMap' is null");
        }
        if (techMap.getOutput() == null || techMap.getOutput().isEmpty()) {
            throw new IllegalStateException("ERROR - Test Setup Error: tech map has no output resources");
        }

        ResourceUsageResponse outputUsage = techMap.getOutput().getFirst();
        Long productId = outputUsage.getResource().getId();
        double outputCoef = outputUsage.getAmount();

        List<ProcessResourceOutputRequest> outputs = List.of(
                ProcessResourceOutputRequest.builder()
                        .resourceId(productId)
                        .amount(outputCoef)
                        .totalAmount(productionAmount * outputCoef)
                        .build()
        );

        ManufacturingItemRequest item = ManufacturingItemRequest.builder()
                .productId(productId)
                .techMapId(techMap.getId())
                .amount(productionAmount)
                .date(date)
                .batchNumber(batchNumber)
                .outputs(new ArrayList<>(outputs))
                .build();

        return ManufacturingListRequest.builder()
                .items(List.of(item))
                .build();
    }

    public static ManufacturingListRequest emptyItemRequest() {
        return ManufacturingListRequest.builder()
                .items(List.of(new ManufacturingItemRequest()))
                .build();
    }

    public static ManufacturingListRequest invalidTechMapRequest(TechnologicalMapResponse techMap) {
        ManufacturingListRequest request = buildCreateRequest(techMap, 5.0);
        request.getItems().getFirst().setTechMapId(9_999_999L);
        return request;
    }

    public static ManufacturingListRequest negativeAmountRequest(TechnologicalMapResponse techMap) {
        ManufacturingListRequest request = buildCreateRequest(techMap, -5.0);
        return request;
    }

    public static ManufacturingListRequest missingBatchRequest(TechnologicalMapResponse techMap) {
        return buildCreateRequest(techMap, 5.0, LocalDate.now(), null);
    }

    public static ManufacturingListRequest missingDateRequest(TechnologicalMapResponse techMap) {
        return buildCreateRequest(techMap, 5.0, null, uniqueBatchNumber());
    }

    public static String uniqueBatchNumber() {
        return "AT-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
