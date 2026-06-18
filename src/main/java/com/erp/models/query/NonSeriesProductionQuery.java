package com.erp.models.query;

import com.erp.enums.NonSeriesProductionStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query parameters for {@code GET /api/v1/non-series-production} and {@code /total}.
 */
@Value
@Builder
public class NonSeriesProductionQuery {

    private static final String BASE_PATH = "/api/v1/non-series-production";

    Long storageId;
    String productSearch;
    LocalDate startDate;
    LocalDate endDate;
    @Builder.Default
    List<NonSeriesProductionStatus> statuses = List.of();
    Integer pageSize;

    public Map<String, Object> toListQueryParams() {
        Map<String, Object> params = baseParams();
        params.put("size", pageSize != null ? pageSize : 500);
        return params;
    }

    public Map<String, Object> toTotalQueryParams() {
        return baseParams();
    }

    public String listPath() {
        return BASE_PATH;
    }

    public String totalPath() {
        return BASE_PATH + "/total";
    }

    private Map<String, Object> baseParams() {
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for non-series production queries");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageIds", storageId);
        if (productSearch != null && !productSearch.isBlank()) {
            params.put("productSearch", productSearch);
        }
        if (startDate != null) {
            params.put("startDate", startDate);
        }
        if (endDate != null) {
            params.put("endDate", endDate);
        }
        if (statuses != null && !statuses.isEmpty()) {
            params.put("statuses", statuses.stream().map(Enum::name).toList());
        }
        return params;
    }
}
