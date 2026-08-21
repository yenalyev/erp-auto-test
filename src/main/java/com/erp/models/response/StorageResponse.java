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
public class StorageResponse {
    private Long id;
    private String name;
    private String alias;
    /** Backend {@code UnitType} name, e.g. SUPPLIER, STORAGE, PRODUCTION. */
    private String type;
    private String relation;
    private Boolean active;
    private String identifierNumber;
    private String accessMode;
    private String nameForInvoices;
    private Boolean orderHub;
    private SimpleEntityResponse parent;
    private List<StorageItemResponse> items;
}
