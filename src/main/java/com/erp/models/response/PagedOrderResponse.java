package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PagedOrderResponse {
    @Builder.Default
    private List<OrderResponse> content = new ArrayList<>();
    private PageMetadata page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMetadata {
        private Integer size;
        private Integer number;
        private Long totalElements;
        private Integer totalPages;
    }

    public Long resolveTotalElements() {
        if (page != null && page.getTotalElements() != null) {
            return page.getTotalElements();
        }
        return totalElements;
    }

    public Integer resolveTotalPages() {
        if (page != null && page.getTotalPages() != null) {
            return page.getTotalPages();
        }
        return totalPages;
    }

    public Integer resolvePageNumber() {
        if (page != null && page.getNumber() != null) {
            return page.getNumber();
        }
        return null;
    }

    public Integer resolvePageSize() {
        if (page != null && page.getSize() != null) {
            return page.getSize();
        }
        return size;
    }
}
