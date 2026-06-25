package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageRegionResponse {
    private Long id;
    private String name;
    private String accessMode;
    private SimpleEntityResponse recipientStorage;
    private Integer locationsCount;
    private Integer membersCount;
    private Integer resourcesCount;
}
