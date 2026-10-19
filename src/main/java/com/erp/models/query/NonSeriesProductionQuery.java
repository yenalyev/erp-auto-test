package com.erp.models.query;

import com.erp.enums.NonSeriesProductionStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query parameters for {@code GET /api/v1/non-series-production} and {@code /total}.
 */
@Value
@Builder(toBuilder = true)
public class NonSeriesProductionQuery {

    private static final String BASE_PATH = "/api/v1/non-series-production";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    Long storageId;
    String productSearch;
    String resourceSearch;
    LocalDate startDate;
    LocalDate endDate;
    @Builder.Default
    List<NonSeriesProductionStatus> statuses = List.of();
    Integer pageSize;
    Integer page;

    public Map<String, Object> toListQueryParams() {
        Map<String, Object> params = baseParams();
        params.put("size", pageSize != null ? pageSize : 500);
        if (page != null) {
            params.put("page", page);
        }
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
        if (resourceSearch != null) {
            params.put("resourceSearch", resourceSearch);
        }
        if (startDate != null) {
            params.put("startDate", startDate.format(ISO_DATE));
        }
        if (endDate != null) {
            params.put("endDate", endDate.format(ISO_DATE));
        }
        if (statuses != null && !statuses.isEmpty()) {
            params.put("statuses", statuses.stream().map(Enum::name).toList());
        }
        return params;
    }
}
