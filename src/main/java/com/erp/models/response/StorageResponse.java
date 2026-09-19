package com.erp.models.response;

import com.erp.enums.LocationFeature;
import com.erp.enums.StorageKind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageResponse {
    private Long id;
    private String name;
    private String alias;
    private StorageKind kind;
    private Set<LocationFeature> features;
    private String milUnitType;
    private Integer milUnitNumber;
    private String relation;
    private Boolean active;
    private String identifierNumber;
    private String accessMode;
    private String nameForInvoices;
    private Boolean orderHub;
    private Boolean productionGroup;
    private SimpleEntityResponse parent;
    private List<StorageItemResponse> items;
}
