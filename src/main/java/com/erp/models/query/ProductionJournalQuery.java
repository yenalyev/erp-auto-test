package com.erp.models.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query parameters for {@code GET /api/v1/productions} aligned with the production journal UI
 * ({@code /production}, filter «Виготовлення», default sort by date desc).
 */
@Value
@Builder(toBuilder = true)
public class ProductionJournalQuery {

    public static final int DEFAULT_UI_PAGE_SIZE = 100;

    private static final String BASE_PATH = "/api/v1/productions";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    Long storageId;
    String product;
    Long categoryId;
    LocalDate startDate;
    LocalDate endDate;
    List<String> tags;
    @Builder.Default
    int page = 0;
    @Builder.Default
    int pageSize = DEFAULT_UI_PAGE_SIZE;

    /** Default journal query: first page, UI page size, date desc (matches {@code useProductionListPage}). */
    public static ProductionJournalQuery uiDefaults(long storageId) {
        return builder().storageId(storageId).build();
    }

    public String path() {
        return BASE_PATH;
    }

    public Map<String, Object> toQueryParams() {
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for production journal queries");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("storageIds", storageId);
        params.put("page", page);
        params.put("size", pageSize);
        params.put("sort", List.of("date,time,desc", "updatedAt,desc"));
        if (product != null && !product.isBlank()) {
            params.put("product", product);
        }
        if (categoryId != null) {
            params.put("categoryId", categoryId);
        }
        if (startDate != null) {
            params.put("startDate", startDate.format(ISO_DATE));
        }
        if (endDate != null) {
            params.put("endDate", endDate.format(ISO_DATE));
        }
        if (tags != null && !tags.isEmpty()) {
            params.put("tags", new ArrayList<>(tags));
        }
        return params;
    }
}
