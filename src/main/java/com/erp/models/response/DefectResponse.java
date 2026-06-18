package com.erp.models.response;

import com.erp.enums.DefectType;
import com.erp.models.common.DefectBatchItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for {@code /api/v1/defects} endpoints. Mirrors backend {@code DefectResponse}.
 *
 * <p>{@code amount} is the <b>remaining</b> defect quantity after write-offs;
 * {@code writeOffAmount} is the cumulative written-off quantity.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DefectResponse {
    private Long id;
    private LocalDate date;
    private SimpleEntityResponse storage;
    private SimpleEntityResponse resource;
    private String description;
    private BigDecimal amount;
    private DefectType type;
    private Long relocationId;
    private Long productionProcessId;
    private SimpleEntityResponse sender;
    private Boolean isProduced;
    @Builder.Default
    private List<DefectBatchItem> defectBatches = new ArrayList<>();
    private BigDecimal writeOffAmount;
}
