package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageInternalResponse {
    private Long id;
    private String name;
    private List<StorageItemInternalResponse> items;
    private SimpleEntityResponse parent;
    private String type;
    private Boolean active;
    private String identifierNumber;
}
