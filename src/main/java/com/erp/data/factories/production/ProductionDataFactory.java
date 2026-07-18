package com.erp.data.factories.production;

import com.erp.models.request.AlternativeInputRequest;
import com.erp.models.request.ManufacturingItemRequest;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.request.ProcessResourceOutputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.utils.DtoMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ProductionDataFactory {

    public static ManufacturingListRequest buildCreateRequest(TechnologicalMapResponse techMap, double productionAmount) {
        return buildCreateRequest(techMap, productionAmount, LocalDate.now(), uniqueBatchNumber());
    }

    public static ManufacturingListRequest buildCreateRequest(TechnologicalMapResponse techMap,
                                                              double productionAmount,
                                                              LocalDate date,
                                                              String batchNumber) {
        return buildCreateRequest(techMap, productionAmount, date, batchNumber, null);
    }

    /**
     * @param chosenAlternatives explicit groupId → resourceId overrides; null = use defaults from tech map
     */
    public static ManufacturingListRequest buildCreateRequest(TechnologicalMapResponse techMap,
                                                              double productionAmount,
                                                              LocalDate date,
                                                              String batchNumber,
                                                              List<AlternativeInputRequest> chosenAlternatives) {
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

        List<ResourceUsageRequest> inputs = DtoMapper.mapToRequestList(techMap.getInput());
        List<AlternativeInputRequest> alternativeInputs = chosenAlternatives != null
                ? new ArrayList<>(chosenAlternatives)
                : defaultAlternativeInputs(techMap);

        ManufacturingItemRequest item = ManufacturingItemRequest.builder()
                .productId(productId)
                .techMapId(techMap.getId())
                .amount(productionAmount)
                .date(date)
                .batchNumber(batchNumber)
                .outputs(new ArrayList<>(outputs))
                .inputs(new ArrayList<>(inputs))
                .alternativeInputs(alternativeInputs)
                .build();

        return ManufacturingListRequest.builder()
                .items(List.of(item))
                .build();
    }

    public static List<AlternativeInputRequest> defaultAlternativeInputs(TechnologicalMapResponse techMap) {
        if (techMap.getGroups() == null || techMap.getGroups().isEmpty()) {
            return new ArrayList<>();
        }
        List<AlternativeInputRequest> result = new ArrayList<>();
        for (TechnologicalMapAlternativeGroupResponse group : techMap.getGroups()) {
            TechnologicalMapAlternativeGroupResourceResponse chosen = group.getAlternativeResources().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No default alternative in group: " + group.getName()));
            result.add(DtoMapper.toAlternativeInput(group, chosen));
        }
        return result;
    }

    public static List<AlternativeInputRequest> alternativeInputsChoosing(
            TechnologicalMapResponse techMap, Long groupId, Long resourceId) {
        List<AlternativeInputRequest> result = new ArrayList<>();
        for (TechnologicalMapAlternativeGroupResponse group : techMap.getGroups()) {
            if (Objects.equals(group.getId(), groupId)) {
                TechnologicalMapAlternativeGroupResourceResponse chosen = group.getAlternativeResources().stream()
                        .filter(r -> r.getResource() != null
                                && Objects.equals(r.getResource().getId(), resourceId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Resource " + resourceId + " not in group " + groupId));
                result.add(DtoMapper.toAlternativeInput(group, chosen));
            } else {
                TechnologicalMapAlternativeGroupResourceResponse def = group.getAlternativeResources().stream()
                        .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                        .findFirst()
                        .orElseThrow();
                result.add(DtoMapper.toAlternativeInput(group, def));
            }
        }
        return result;
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
        return buildCreateRequest(techMap, -5.0);
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
