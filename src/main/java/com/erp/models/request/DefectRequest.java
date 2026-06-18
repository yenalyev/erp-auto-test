package com.erp.models.request;

import com.erp.enums.DefectType;
import com.erp.models.common.DefectBatchItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Body for {@code POST/PUT /api/v1/defects}. Mirrors backend {@code DefectRequest}.
 *
 * <p>Optional fields are omitted from the payload ({@link JsonInclude.Include#NON_NULL}):
 * <ul>
 *   <li>{@code relocationId} — set for {@code RELOCATION}/{@code RELOCATION_FROM_UNIT}</li>
 *   <li>{@code productionProcessId} — set for {@code PRODUCTION}</li>
 *   <li>{@code isProduced} / {@code defectBatches} — relevant for explicit-batch {@code STORAGE} defects</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefectRequest {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    private Long storageId;
    private Long resourceId;
    private String description;
    private BigDecimal amount;
    private DefectType type;

    private Long relocationId;
    private Long productionProcessId;
    private Boolean isProduced;
    private List<DefectBatchItem> defectBatches;
}
