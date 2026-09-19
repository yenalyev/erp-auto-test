package com.erp.models.request;

import com.erp.enums.MilUnitType;
import com.erp.enums.LocationFeature;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageKind;
import com.erp.enums.StorageRelation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
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
    private StorageKind kind;
    private Set<LocationFeature> features;
    private MilUnitType milUnitType;
    private Integer milUnitNumber;
    private StorageRelation relation;
    private String identifierNumber;
    private StorageAccessMode accessMode;
    private String nameForInvoices;
    /** Backend field is primitive {@code boolean}; omit it and Jackson returns empty 400. */
    private boolean orderHub;
    /** Same as {@link #orderHub}: UI always sends it; omit it and POST /storages returns empty 400. */
    private boolean productionGroup;
}
