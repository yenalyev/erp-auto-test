package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentModelResponse {
    private Long id;
    private String name;
    private Instant createdAt;
    private String createdBy;
    private List<SimpleEntityResponse> categories;
}
