package com.erp.data.factories.tech_map;

import com.erp.data.FakerProvider;
import com.erp.models.request.*;
import com.erp.models.response.AlternativeGroupResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.utils.DtoMapper;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TechnologicalMapDataFactory {

    /**
     * @param resourceResponseList список доступних ресурсів
     * @return TechnologicalMapRequestBuilder з 2 ресурсами на вході та 1 на виході
     * без альтернативних ресурсів
     */
    public static TechnologicalMapRequest.TechnologicalMapRequestBuilder createSimpleTechMap(
            List<ResourceResponse> resourceResponseList) {

        if (resourceResponseList == null || resourceResponseList.size() < 3) {
            throw new IllegalStateException("ERROR - Test Setup Error: 'resourceResponseList' must " +
                    "have at least 3 resources. Current size: " +
                    (resourceResponseList == null ? "null" : resourceResponseList.size()));
        }

        List<ResourceUsageRequest> input = new ArrayList<>();
        // Faker для кількості, щоб уникнути 0.0 та довгих дробів
        input.add(new ResourceUsageRequest(resourceResponseList.get(0).getId(),
                FakerProvider.ukrainian().number().randomDouble(3, 1, 10)));
        input.add(new ResourceUsageRequest(resourceResponseList.get(1).getId(),
                FakerProvider.ukrainian().number().randomDouble(3, 1, 10)));

        List<ResourceUsageRequest> output = new ArrayList<>();
        output.add(new ResourceUsageRequest(resourceResponseList.get(2).getId(),
                FakerProvider.ukrainian().number().randomDouble(2, 1, 10)));

        return TechnologicalMapRequest.builder()
                .name("Tech Map " + FakerProvider.ukrainian().commerce().productName() + " " + System.currentTimeMillis())
                .input(input)
                .output(output)
                .alternatives(new ArrayList<>());
    }


    /**
     * 🔥 Створює реквест на основі існуючої відповіді.
     * Це дозволяє взяти існуючий об'єкт і змінити в ньому лише одне поле.
     */
    public static TechnologicalMapRequest.TechnologicalMapRequestBuilder fromExisting(
            @NonNull TechnologicalMapResponse existing) {

        return TechnologicalMapRequest.builder()
                .name(existing.getName())
                .input(DtoMapper.mapToRequestList(existing.getInput()))
                .output(DtoMapper.mapToRequestList(existing.getOutput()))
                .alternatives(existing.getAlternatives().stream()
                        .map(DtoMapper::mapToRequest)
                        .collect(Collectors.toList()));
    }
}