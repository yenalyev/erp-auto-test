package com.erp.data.factories.tech_map;

import com.erp.data.FakerProvider;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapAlternativeGroupRequest;
import com.erp.models.request.TechnologicalMapAlternativeGroupResourceRequest;
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
     * PRODUCTION map: 1 fixed input + 1 alt group (default + non-default) + 1 output.
     *
     * @param resources [fixedInput, defaultAlt, nonDefaultAlt, output] — need ≥ 4
     */
    public static TechnologicalMapRequest createProductionMapWithAlternativeGroup(
            List<ResourceResponse> resources,
            Long storageId) {
        if (resources == null || resources.size() < 4) {
            throw new IllegalStateException("Need at least 4 resources: fixed, defaultAlt, alt, output");
        }
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map request");
        }

        ResourceResponse fixed = resources.get(0);
        ResourceResponse defaultAlt = resources.get(1);
        ResourceResponse otherAlt = resources.get(2);
        ResourceResponse output = resources.get(3);

        return TechnologicalMapRequest.builder()
                .name("AltGroup-TM-" + System.currentTimeMillis())
                .type(TYPE_PRODUCTION)
                .input(List.of(new ResourceUsageRequest(fixed.getId(), 1.0)))
                .output(List.of(new ResourceUsageRequest(output.getId(), 1.0)))
                .storageIds(Set.of(storageId))
                .groups(List.of(alternativeGroup(
                        "Клей",
                        alternativeResource(defaultAlt.getId(), 2.0, true),
                        alternativeResource(otherAlt.getId(), 2.5, false))))
                .build();
    }

    /**
     * PRODUCTION map with only alternative groups (no fixed inputs).
     *
     * @param resources [defaultAlt, nonDefaultAlt, output] — need ≥ 3
     */
    public static TechnologicalMapRequest createProductionMapGroupsOnly(
            List<ResourceResponse> resources,
            Long storageId) {
        if (resources == null || resources.size() < 3) {
            throw new IllegalStateException("Need at least 3 resources: defaultAlt, alt, output");
        }
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map request");
        }

        return TechnologicalMapRequest.builder()
                .name("AltGroupOnly-TM-" + System.currentTimeMillis())
                .type(TYPE_PRODUCTION)
                .input(List.of())
                .output(List.of(new ResourceUsageRequest(resources.get(2).getId(), 1.0)))
                .storageIds(Set.of(storageId))
                .groups(List.of(alternativeGroup(
                        "Пальне",
                        alternativeResource(resources.get(0).getId(), 1.5, true),
                        alternativeResource(resources.get(1).getId(), 1.8, false))))
                .build();
    }

    public static TechnologicalMapAlternativeGroupRequest alternativeGroup(
            String name,
            TechnologicalMapAlternativeGroupResourceRequest... resources) {
        return TechnologicalMapAlternativeGroupRequest.builder()
                .name(name)
                .alternativeResources(List.of(resources))
                .build();
    }

    public static TechnologicalMapAlternativeGroupResourceRequest alternativeResource(
            Long resourceId, double amount, boolean isDefault) {
        return TechnologicalMapAlternativeGroupResourceRequest.builder()
                .resourceId(resourceId)
                .amount(amount)
                .isDefault(isDefault)
                .build();
    }

    public static TechnologicalMapRequest withZeroDefaultsInGroup(
            List<ResourceResponse> resources, Long storageId) {
        TechnologicalMapRequest request = createProductionMapWithAlternativeGroup(resources, storageId);
        request.getGroups().getFirst().getAlternativeResources()
                .forEach(r -> r.setIsDefault(false));
        return request;
    }

    public static TechnologicalMapRequest withTwoDefaultsInGroup(
            List<ResourceResponse> resources, Long storageId) {
        TechnologicalMapRequest request = createProductionMapWithAlternativeGroup(resources, storageId);
        request.getGroups().getFirst().getAlternativeResources()
                .forEach(r -> r.setIsDefault(true));
        return request;
    }

    public static TechnologicalMapRequest withEmptyAlternativeResources(
            List<ResourceResponse> resources, Long storageId) {
        TechnologicalMapRequest request = createProductionMapWithAlternativeGroup(resources, storageId);
        request.getGroups().getFirst().setAlternativeResources(List.of());
        return request;
    }

    public static TechnologicalMapRequest withDuplicateResourceInGroup(
            List<ResourceResponse> resources, Long storageId) {
        TechnologicalMapRequest request = createProductionMapWithAlternativeGroup(resources, storageId);
        Long duplicatedId = resources.get(1).getId();
        request.getGroups().getFirst().setAlternativeResources(List.of(
                alternativeResource(duplicatedId, 2.0, true),
                alternativeResource(duplicatedId, 2.5, false)));
        return request;
    }

    /**
     * PRODUCTION map with two alternative groups (fixed input + 2 groups + output).
     *
     * @param resources [fixed, glueDefault, glueAlt, fuelDefault, fuelAlt, output] — need ≥ 6
     */
    public static TechnologicalMapRequest createProductionMapWithTwoGroups(
            List<ResourceResponse> resources,
            Long storageId) {
        if (resources == null || resources.size() < 6) {
            throw new IllegalStateException("Need at least 6 resources for two-group tech map");
        }
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map request");
        }

        return TechnologicalMapRequest.builder()
                .name("AltTwoGroups-TM-" + System.currentTimeMillis())
                .type(TYPE_PRODUCTION)
                .input(List.of(new ResourceUsageRequest(resources.get(0).getId(), 1.0)))
                .output(List.of(new ResourceUsageRequest(resources.get(5).getId(), 1.0)))
                .storageIds(Set.of(storageId))
                .groups(List.of(
                        alternativeGroup(
                                "Клей",
                                alternativeResource(resources.get(1).getId(), 2.0, true),
                                alternativeResource(resources.get(2).getId(), 2.5, false)),
                        alternativeGroup(
                                "Пальне",
                                alternativeResource(resources.get(3).getId(), 1.5, true),
                                alternativeResource(resources.get(4).getId(), 1.8, false))))
                .build();
    }

    public static TechnologicalMapRequest withChangedAltAmount(
            @NonNull TechnologicalMapResponse existing,
            int groupIndex,
            int resourceIndex,
            double newAmount) {
        TechnologicalMapRequest request = fromExisting(existing).build();
        if (request.getGroups() == null || request.getGroups().size() <= groupIndex) {
            throw new IllegalStateException("Tech map has no group at index " + groupIndex);
        }
        var resources = request.getGroups().get(groupIndex).getAlternativeResources();
        if (resources == null || resources.size() <= resourceIndex) {
            throw new IllegalStateException("Group has no alternative resource at index " + resourceIndex);
        }
        resources.get(resourceIndex).setAmount(newAmount);
        return request;
    }

    public static TechnologicalMapRequest withDuplicateGroupNames(
            List<ResourceResponse> resources, Long storageId) {
        if (resources == null || resources.size() < 4) {
            throw new IllegalStateException("Need at least 4 resources");
        }
        TechnologicalMapRequest request = createProductionMapWithAlternativeGroup(resources, storageId);
        request.setGroups(List.of(
                alternativeGroup("Клей",
                        alternativeResource(resources.get(1).getId(), 2.0, true),
                        alternativeResource(resources.get(2).getId(), 2.5, false)),
                alternativeGroup("клей",
                        alternativeResource(resources.get(0).getId(), 1.0, true))));
        return request;
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
     * Swap which alternative resource is the default (for update / version bump tests).
     */
    public static TechnologicalMapRequest withSwappedDefault(@NonNull TechnologicalMapResponse existing) {
        TechnologicalMapRequest request = fromExisting(existing).build();
        if (request.getGroups() == null || request.getGroups().isEmpty()) {
            throw new IllegalStateException("Tech map has no alternative groups to swap");
        }
        var resources = request.getGroups().getFirst().getAlternativeResources();
        if (resources == null || resources.size() < 2) {
            throw new IllegalStateException("Alternative group must have at least 2 resources");
        }
        resources.get(0).setIsDefault(false);
        resources.get(1).setIsDefault(true);
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
                .output(DtoMapper.mapToRequestList(existing.getOutput()))
                .groups(DtoMapper.mapGroupsToRequest(existing.getGroups()));
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
