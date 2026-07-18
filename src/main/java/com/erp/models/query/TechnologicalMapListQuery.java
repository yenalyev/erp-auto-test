package com.erp.models.query;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query parameters for {@code GET /api/v1/technological-maps} and
 * {@code GET /api/v1/technological-maps/tag-statistics}.
 */
@Value
@Builder(toBuilder = true)
public class TechnologicalMapListQuery {

    public static final int DEFAULT_PAGE_SIZE = 100;

    Long storageId;
    String name;
    String productTerm;
    Boolean isActive;
    /** PRODUCTION / DISASSEMBLE — required for tag-statistics (backend SpEL calls type.name()). */
    String type;
    List<String> tags;
    @Builder.Default
    int page = 0;
    @Builder.Default
    int pageSize = DEFAULT_PAGE_SIZE;

    public static TechnologicalMapListQuery forStorage(long storageId) {
        return builder().storageId(storageId).build();
    }

    /** Defaults for tag-statistics: PRODUCTION type avoids backend NPE on null type.name(). */
    public static TechnologicalMapListQuery forTagStatistics(long storageId) {
        return builder().storageId(storageId).type("PRODUCTION").build();
    }

    public Map<String, Object> toQueryParams() {
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for technological map list queries");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageIds", storageId);
        params.put("page", page);
        params.put("size", pageSize);
        if (name != null && !name.isBlank()) {
            params.put("name", name);
        }
        if (productTerm != null && !productTerm.isBlank()) {
            params.put("productTerm", productTerm);
        }
        if (isActive != null) {
            params.put("isActive", isActive);
        }
        if (type != null && !type.isBlank()) {
            params.put("type", type);
        }
        if (tags != null && !tags.isEmpty()) {
            params.put("tags", new ArrayList<>(tags));
        }
        return params;
    }
}
