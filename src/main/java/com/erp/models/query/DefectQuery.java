package com.erp.models.query;

import com.erp.enums.DefectType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query parameters for {@code GET /api/v1/defects} and the linked-id lookups.
 * Mirrors backend {@code DefectController} request params.
 */
@Value
@Builder
public class DefectQuery {

    Long storageId;
    String resourceSearch;
    LocalDate startDate;
    LocalDate endDate;
    @Builder.Default
    List<DefectType> types = List.of();
    Integer pageSize;

    /** Params for the paged list ({@code GET /api/v1/defects}). */
    public Map<String, Object> toListQueryParams() {
        Map<String, Object> params = baseParams();
        params.put("size", pageSize != null ? pageSize : 500);
        return params;
    }

    /** Params for {@code GET /api/v1/defects/linked-production-ids} and {@code linked-relocation-ids}. */
    public Map<String, Object> toLinkedQueryParams(Long resourceId, LocalDate date) {
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for defect linked-id queries");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageIds", storageId);
        if (date != null) {
            params.put("date", date);
        }
        if (resourceId != null) {
            params.put("resourceId", resourceId);
        }
        return params;
    }

    private Map<String, Object> baseParams() {
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for defect queries");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageIds", storageId);
        if (resourceSearch != null && !resourceSearch.isBlank()) {
            params.put("resourceSearch", resourceSearch);
        }
        if (startDate != null) {
            params.put("startDate", startDate);
        }
        if (endDate != null) {
            params.put("endDate", endDate);
        }
        if (types != null && !types.isEmpty()) {
            params.put("types", types.stream().map(Enum::name).toList());
        }
        return params;
    }
}
