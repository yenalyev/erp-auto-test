package com.erp.models.request;

import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageRequest {
    private String name;
    private String alias;
    private Long parentId;
    private UnitType type;
    private StorageRelation relation;
    private String identifierNumber;
    private StorageAccessMode accessMode;
    private String nameForInvoices;
}
