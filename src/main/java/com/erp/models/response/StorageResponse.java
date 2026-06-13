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
    /** Backend {@code UnitType} name, e.g. SUPPLIER, STORAGE, PRODUCTION. */
    private String type;
    private List<StorageItemResponse> items;
}
