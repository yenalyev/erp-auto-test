package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PagedResourceRelocationViewerResponse {
    @Builder.Default
    private List<ResourceRelocationViewerResponse> content = new ArrayList<>();
    private PageMetadata page;
    @Builder.Default
    private List<ResourceRelocationSumViewerResponse> sums = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMetadata {
        private Integer size;
        private Integer number;
        private Long totalElements;
        private Long totalPages;
    }
}
