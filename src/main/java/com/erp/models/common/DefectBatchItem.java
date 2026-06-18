package com.erp.models.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Explicit per-batch breakdown used both in {@code DefectRequest} (input) and
 * {@code DefectResponse} (output). Mirrors backend {@code org.pm.tk.dto.common.DefectBatchItem}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefectBatchItem {
    private String batchNumber;
    private Boolean isProduced;
    private BigDecimal amount;
}
