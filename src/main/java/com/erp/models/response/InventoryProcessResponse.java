package com.erp.models.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryProcessResponse {
    private Long id;
    private SimpleEntityResponse storage;
    private String state;
    private Instant requestedAt;
    private String requestedBy;
    private String requestedComment;
    private Instant openedAt;
    private Instant closedAt;
    private String resolutionComment;
    private String resolutionBy;
    private JsonNode diff;
}
