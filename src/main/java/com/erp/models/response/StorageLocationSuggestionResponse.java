package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageLocationSuggestionResponse {
    private Long id;
    private String name;
    private String type;
    /** JSON array of regions from backend {@code json_agg}; kept as tree for flexible parsing. */
    private JsonNode linkedByRegions;
}
