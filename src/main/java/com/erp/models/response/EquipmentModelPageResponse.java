package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentModelPageResponse {
    private List<EquipmentModelResponse> content;
    private PageMetadata page;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageMetadata {
        private int size;
        private int number;
        private int totalPages;
        private long totalElements;
    }
}
