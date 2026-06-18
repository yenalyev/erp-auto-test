package com.erp.models.request;

import com.erp.models.common.DefectWriteOffBatch;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for {@code POST /api/v1/defects/write-off} ("Списання браку").
 * Mirrors backend {@code DefectWriteOffRequest}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefectWriteOffRequest {
    private Long defectId;
    private Long storageId;
    private BigDecimal amount;
    private String description;
    private List<DefectWriteOffBatch> batches;
}
