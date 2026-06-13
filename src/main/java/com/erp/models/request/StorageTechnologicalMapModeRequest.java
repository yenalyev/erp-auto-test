package com.erp.models.request;

import com.erp.enums.StorageTechnologicalMapMode;
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
public class StorageTechnologicalMapModeRequest {
    private Long storageId;
    private StorageTechnologicalMapMode mode;
}
