package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TechnologicalMapResponse {
    private Long id;
    private String name;
    private String type;
    private Long version;
    private String groupId;
    private Instant dateTime;
    private Set<SimpleEntityResponse> storages;

    private List<ResourceUsageResponse> input;
    private List<ResourceUsageResponse> output;

    @Builder.Default
    private List<TechnologicalMapAlternativeGroupResponse> groups = new ArrayList<>();
}
