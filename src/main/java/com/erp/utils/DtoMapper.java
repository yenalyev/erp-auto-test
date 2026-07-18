package com.erp.utils;

import com.erp.models.request.AlternativeInputRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapAlternativeGroupRequest;
import com.erp.models.request.TechnologicalMapAlternativeGroupResourceRequest;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResourceResponse;
import com.erp.models.response.TechnologicalMapAlternativeGroupResponse;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
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

    public static TechnologicalMapAlternativeGroupRequest mapToRequest(TechnologicalMapAlternativeGroupResponse group) {
        if (group == null) return null;
        return TechnologicalMapAlternativeGroupRequest.builder()
                .id(group.getId())
                .name(group.getName())
                .alternativeResources(mapAlternativeResourcesToRequest(group.getAlternativeResources()))
                .build();
    }

    public static List<TechnologicalMapAlternativeGroupRequest> mapGroupsToRequest(
            List<TechnologicalMapAlternativeGroupResponse> groups) {
        if (groups == null) return Collections.emptyList();
        return groups.stream()
                .map(DtoMapper::mapToRequest)
                .collect(Collectors.toList());
    }

    public static AlternativeInputRequest toAlternativeInput(
            TechnologicalMapAlternativeGroupResponse group,
            TechnologicalMapAlternativeGroupResourceResponse resource) {
        if (group == null || resource == null || resource.getResource() == null) {
            return null;
        }
        return AlternativeInputRequest.builder()
                .groupId(group.getId())
                .resourceId(resource.getResource().getId())
                .amount(resource.getAmount())
                .build();
    }

    private static List<TechnologicalMapAlternativeGroupResourceRequest> mapAlternativeResourcesToRequest(
            List<TechnologicalMapAlternativeGroupResourceResponse> resources) {
        if (resources == null) return Collections.emptyList();
        return resources.stream()
                .map(resource -> TechnologicalMapAlternativeGroupResourceRequest.builder()
                        .id(resource.getId())
                        .resourceId(resource.getResource() != null ? resource.getResource().getId() : null)
                        .amount(resource.getAmount())
                        .isDefault(resource.getIsDefault())
                        .build())
                .collect(Collectors.toList());
    }
}
