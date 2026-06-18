package com.erp.models.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Per-batch breakdown of a defect write-off ("Списання браку").
 * Mirrors the nested {@code Batch} record in backend {@code DefectWriteOffRequest} / {@code DefectWriteOffResponse}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefectWriteOffBatch {
    private String batchNumber;
    private BigDecimal amount;
}
