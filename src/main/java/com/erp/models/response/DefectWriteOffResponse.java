package com.erp.models.response;

import com.erp.models.common.DefectWriteOffBatch;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for defect write-off endpoints. Mirrors backend {@code DefectWriteOffResponse}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefectWriteOffResponse {
    private Long id;
    private Long defectId;
    private SimpleEntityResponse resource;
    private BigDecimal amount;
    private String description;
    private Instant createdAt;
    @Builder.Default
    private List<DefectWriteOffBatch> batches = new ArrayList<>();
}
