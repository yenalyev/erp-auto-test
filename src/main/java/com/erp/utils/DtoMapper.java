package com.erp.utils;

import com.erp.models.request.*;
import com.erp.models.response.*;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@UtilityClass // Робить клас final, створює приватний конструктор і робить методи static
public class DtoMapper {

    /**
     * Конвертує Response-модель використання ресурсу в Request-модель (ID-based)
     */
    public static ResourceUsageRequest mapToRequest(ResourceUsageResponse response) {
        if (response == null || response.getResource() == null) return null;
        return new ResourceUsageRequest(
                response.getResource().getId(),
                response.getAmount()
        );
    }

    public static List<ResourceUsageRequest> mapToRequestList(List<ResourceUsageResponse> responseList) {
        if (responseList == null) return Collections.emptyList();
        return responseList.stream()
                .map(DtoMapper::mapToRequest)
                .collect(Collectors.toList());
    }

    /**
     * Конвертує групу альтернатив (Response -> Request)
     */
    public static AlternativeGroupRequest mapToRequest(AlternativeGroupResponse group) {
        if (group == null) return null;
        return AlternativeGroupRequest.builder()
                .name(group.getName())
                .required(group.getRequired())
                .options(mapOptionsToRequest(group.getOptions()))
                .build();
    }

    private static List<AlternativeOptionRequest> mapOptionsToRequest(List<AlternativeOptionResponse> options) {
        if (options == null) return Collections.emptyList();
        return options.stream()
                .map(opt -> AlternativeOptionRequest.builder()
                        .resourceId(opt.getResource().getId())
                        .amount(opt.getAmount())
                        .mainOption(opt.getMainOption())
                        .build())
                .collect(Collectors.toList());
    }
}