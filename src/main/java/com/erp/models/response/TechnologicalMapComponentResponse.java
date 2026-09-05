package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TechnologicalMapComponentResponse {
    private SimpleEntityResponse resource;
    private String unit;
    private Double amount;
    @Builder.Default
    private List<TechnologicalMapRefResponse> technolMaps = new ArrayList<>();
    private Long selectedTmId;
    @JsonProperty("isRequiresChoice")
    private Boolean isRequiresChoice;
    @Builder.Default
    private List<TechnologicalMapComponentResponse> components = new ArrayList<>();
}
