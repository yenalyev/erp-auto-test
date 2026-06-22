package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecompositionRequest {
    @Builder.Default
    private List<DecompositionBlockRequest> blocks = new ArrayList<>();

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DecompositionBlockRequest {
        @Builder.Default
        private List<DecompositionItemRequest> items = new ArrayList<>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DecompositionItemRequest {
        private Long resourceId;
        @Builder.Default
        private List<DecompositionAssignmentRequest> assignments = new ArrayList<>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DecompositionAssignmentRequest {
        private Long storageId;
        private Long technologicalMapId;
        private BigDecimal amount;
    }
}
