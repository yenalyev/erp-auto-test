package com.erp.data.factories.tech_map;

import com.erp.data.FakerProvider;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.utils.DtoMapper;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TechnologicalMapDataFactory {

    public static final String TYPE_PRODUCTION = "PRODUCTION";
    public static final String TYPE_DISASSEMBLE = "DISASSEMBLE";

    /**
     * @param resourceResponseList список доступних ресурсів
     * @param storageId            локація техкарти (обов'язкове поле в API)
     * @return TechnologicalMapRequestBuilder з 2 input та 1 output
     */
    public static TechnologicalMapRequest.TechnologicalMapRequestBuilder createSimpleTechMap(
            List<ResourceResponse> resourceResponseList,
            Long storageId) {

        if (resourceResponseList == null || resourceResponseList.size() < 3) {
            throw new IllegalStateException("ERROR - Test Setup Error: 'resourceResponseList' must " +
                    "have at least 3 resources. Current size: " +
                    (resourceResponseList == null ? "null" : resourceResponseList.size()));
        }
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map request");
        }

        List<ResourceUsageRequest> input = new ArrayList<>();
        input.add(new ResourceUsageRequest(resourceResponseList.get(0).getId(),
                FakerProvider.ukrainian().number().randomDouble(3, 1, 10)));
        input.add(new ResourceUsageRequest(resourceResponseList.get(1).getId(),
                FakerProvider.ukrainian().number().randomDouble(3, 1, 10)));

        List<ResourceUsageRequest> output = new ArrayList<>();
        output.add(new ResourceUsageRequest(resourceResponseList.get(2).getId(),
                FakerProvider.ukrainian().number().randomDouble(2, 1, 10)));

        return TechnologicalMapRequest.builder()
                .name("Tech Map " + FakerProvider.ukrainian().commerce().productName() + " " + System.currentTimeMillis())
                .type(TYPE_PRODUCTION)
                .input(input)
                .output(output)
                .storageIds(Set.of(storageId));
    }

    /**
     * Production tech map with explicit inputs/outputs bound to one or more storages.
     */
    public static TechnologicalMapRequest.TechnologicalMapRequestBuilder createProductionMapWithStorages(
            String namePrefix,
            List<ResourceUsageRequest> input,
            List<ResourceUsageRequest> output,
            Set<Long> storageIds) {
        if (storageIds == null || storageIds.isEmpty()) {
            throw new IllegalStateException("storageIds is required for technological map request");
        }
        return TechnologicalMapRequest.builder()
                .name(namePrefix + "-" + System.currentTimeMillis())
                .type(TYPE_PRODUCTION)
                .input(input)
                .output(output)
                .storageIds(storageIds);
    }

    /**
     * Production tech map with fixed coefficients (predictable stock deltas).
     */
    public static TechnologicalMapRequest.TechnologicalMapRequestBuilder createProductionTechMap(
            List<ResourceResponse> resourceResponseList,
            Long storageId) {

        if (resourceResponseList == null || resourceResponseList.size() < 3) {
            throw new IllegalStateException("ERROR - Test Setup Error: need at least 3 resources");
        }
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map request");
        }

        List<ResourceUsageRequest> input = List.of(
                new ResourceUsageRequest(resourceResponseList.get(0).getId(), 10.0),
                new ResourceUsageRequest(resourceResponseList.get(1).getId(), 5.0)
        );
        List<ResourceUsageRequest> output = List.of(
                new ResourceUsageRequest(resourceResponseList.get(2).getId(), 1.0)
        );

        return TechnologicalMapRequest.builder()
                .name("Production-TM-" + System.currentTimeMillis())
                .type(TYPE_PRODUCTION)
                .input(input)
                .output(output)
                .storageIds(Set.of(storageId));
    }

    /**
     * Disassemble tech map: input = resource to disassemble, output = produced resource.
     * Coefficients are fixed for predictable stock/history assertions.
     */
    public static TechnologicalMapRequest.TechnologicalMapRequestBuilder createDisassembleTechMap(
            List<ResourceResponse> resourceResponseList,
            Long storageId) {

        if (resourceResponseList == null || resourceResponseList.size() < 2) {
            throw new IllegalStateException("ERROR - Test Setup Error: need at least 2 resources for disassemble tech map");
        }
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map request");
        }

        List<ResourceUsageRequest> input = List.of(
                new ResourceUsageRequest(resourceResponseList.get(0).getId(), 1.0)
        );
        List<ResourceUsageRequest> output = List.of(
                new ResourceUsageRequest(resourceResponseList.get(1).getId(), 0.5)
        );

        return TechnologicalMapRequest.builder()
                .name("Disassemble-TM-" + System.currentTimeMillis())
                .type(TYPE_DISASSEMBLE)
                .input(input)
                .output(output)
                .storageIds(Set.of(storageId));
    }

    /**
     * Invalid request: overlapping resource appears in both input and output.
     *
     * @param otherInput optional second input row (for multi-input overlap scenario); may be null
     */
    public static TechnologicalMapRequest withInputOutputOverlap(
            @NonNull ResourceResponse overlappingResource,
            ResourceResponse otherInput,
            Long storageId,
            String type) {
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map request");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalStateException("type is required for technological map request");
        }

        List<ResourceUsageRequest> input = new ArrayList<>();
        if (otherInput != null) {
            input.add(new ResourceUsageRequest(otherInput.getId(), 2.0));
        }
        input.add(new ResourceUsageRequest(overlappingResource.getId(), 1.0));

        List<ResourceUsageRequest> output = List.of(
                new ResourceUsageRequest(overlappingResource.getId(), 1.0));

        return TechnologicalMapRequest.builder()
                .name("Overlap-TM-" + System.currentTimeMillis())
                .type(type)
                .input(input)
                .output(output)
                .storageIds(Set.of(storageId))
                .build();
    }

    /**
     * Update request derived from existing map with output resourceId set to first input resource (overlap).
     */
    public static TechnologicalMapRequest withIntroducedOverlap(@NonNull TechnologicalMapResponse existing) {
        TechnologicalMapRequest request = fromExisting(existing).build();
        Long overlapResourceId = request.getInput().getFirst().getResourceId();
        request.getOutput().getFirst().setResourceId(overlapResourceId);
        return request;
    }

    /**
     * Клон техкарти (як у UI: нова назва, ті самі input/output).
     */
    public static TechnologicalMapRequest cloneFrom(@NonNull TechnologicalMapResponse source) {
        return fromExisting(source)
                .name(source.getName() + " - Копія " + System.currentTimeMillis())
                .build();
    }

    /**
     * Update request з новою назвою (input/output без змін).
     */
    public static TechnologicalMapRequest withRenamed(@NonNull TechnologicalMapResponse source, String newName) {
        return fromExisting(source).name(newName).build();
    }

    /**
     * Створює request на основі існуючої відповіді API (для update).
     */
    public static TechnologicalMapRequest.TechnologicalMapRequestBuilder fromExisting(
            @NonNull TechnologicalMapResponse existing) {

        Set<Long> storageIds = existing.getStorages() == null
                ? null
                : existing.getStorages().stream()
                        .map(SimpleEntityResponse::getId)
                        .collect(Collectors.toSet());

        return TechnologicalMapRequest.builder()
                .name(existing.getName())
                .type(existing.getType())
                .storageIds(storageIds)
                .input(DtoMapper.mapToRequestList(existing.getInput()))
                .output(DtoMapper.mapToRequestList(existing.getOutput()));
    }

    /**
     * Опис коефіцієнтів техкарти для Allure: скільки input на 1 од. output.
     */
    public static String formatCoefficientsPerOutputUnit(TechnologicalMapResponse map) {
        if (map == null || map.getOutput() == null || map.getOutput().isEmpty()) {
            return "коефіцієнти техкарти: н/д";
        }
        ResourceUsageResponse outputUsage = map.getOutput().getFirst();
        Double outputBase = outputUsage.getAmount();
        if (outputBase == null || outputBase == 0.0) {
            return "коефіцієнти техкарти: н/д";
        }

        String productLabel = resourceLabel(outputUsage);
        String inputs = map.getInput() == null ? "" : map.getInput().stream()
                .map(input -> {
                    double amount = input.getAmount() != null ? input.getAmount() : 0.0;
                    return String.format("«%s» %s", resourceLabel(input), formatUnits(amount / outputBase));
                })
                .collect(Collectors.joining(", "));

        return String.format("на 1 од. «%s»: input %s", productLabel, inputs.isEmpty() ? "—" : inputs);
    }

    private static String resourceLabel(ResourceUsageResponse usage) {
        if (usage.getResource() != null && usage.getResource().getName() != null) {
            return usage.getResource().getName().trim();
        }
        if (usage.getResource() != null && usage.getResource().getId() != null) {
            return "ресурс id=" + usage.getResource().getId();
        }
        return "невідомий ресурс";
    }

    private static String formatUnits(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.format("%.0f од.", value);
        }
        return String.format("%.2f од.", value);
    }
}
