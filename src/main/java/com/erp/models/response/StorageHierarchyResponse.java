package com.erp.models.response;

import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
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
public class StorageHierarchyResponse {
    private Long id;
    private String name;
    private UnitType unitType;
    private StorageRelation relation;
    @Builder.Default
    private List<StorageHierarchyResponse> children = new ArrayList<>();
    private String identityNumber;
    private Boolean active;
}
