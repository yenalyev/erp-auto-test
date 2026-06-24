package com.erp.models.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query parameters for {@code GET /api/v1/relocations} aligned with the relocation journal UI
 * ({@code /relocations}, tabs «Видано» / «Отримано», sort fields {@code sender.name} / {@code recipient.name}).
 */
@Value
@Builder(toBuilder = true)
public class RelocationJournalQuery {

    public static final int DEFAULT_UI_PAGE_SIZE = 10;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<String> HISTORY_STATES = List.of(
            "RETURNED", "FINISHED", "AUTO_FINISHED");

    public enum Perspective {
        /** Tab «Отримано» — {@code receiverIds} + history states. */
        RECEIVED,
        /** Tab «Видано» — {@code senderIds} + history states. */
        SENT
    }

    /** Sort columns mapped in tk-ui {@code server-table.tsx}. */
    public enum SortField {
        SENDER_NAME("sender.name"),
        RECIPIENT_NAME("recipient.name");

        private final String apiProperty;

        SortField(String apiProperty) {
            this.apiProperty = apiProperty;
        }

        public String apiProperty() {
            return apiProperty;
        }
    }

    Long storageId;
    Perspective perspective;
    Long categoryId;
    Long productId;
    Long relocationAgentId;
    LocalDate startDate;
    LocalDate endDate;
    SortField sortField;
    @Builder.Default
    boolean sortDesc = false;
    @Builder.Default
    int page = 0;
    @Builder.Default
    int pageSize = DEFAULT_UI_PAGE_SIZE;

    public static RelocationJournalQuery receivedHistoryUi(long storageId) {
        return builder()
                .storageId(storageId)
                .perspective(Perspective.RECEIVED)
                .build();
    }

    public static RelocationJournalQuery sentHistoryUi(long storageId) {
        return builder()
                .storageId(storageId)
                .perspective(Perspective.SENT)
                .build();
    }

    public Map<String, Object> toQueryParams() {
        if (storageId == null) {
            throw new IllegalStateException("storageId is required for relocation journal queries");
        }
        if (perspective == null) {
            throw new IllegalStateException("perspective is required for relocation journal queries");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("size", pageSize);
        params.put("states", HISTORY_STATES);

        if (perspective == Perspective.RECEIVED) {
            params.put("receiverIds", storageId);
        } else {
            params.put("senderIds", storageId);
        }

        if (sortField != null) {
            String direction = sortDesc ? "DESC" : "ASC";
            params.put("sort", List.of(sortField.apiProperty() + "," + direction));
        }
        if (categoryId != null) {
            params.put("category", categoryId);
        }
        if (productId != null) {
            params.put("productIds", List.of(productId));
        }
        if (relocationAgentId != null) {
            params.put("relocationAgentId", relocationAgentId);
        }
        if (startDate != null) {
            params.put("startDate", startDate.format(ISO_DATE));
        }
        if (endDate != null) {
            params.put("endDate", endDate.format(ISO_DATE));
        }
        return params;
    }
}
