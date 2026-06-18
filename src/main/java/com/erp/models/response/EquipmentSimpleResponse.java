package com.erp.models.response;

import com.erp.enums.EquipmentStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentSimpleResponse {
    private Long id;
    private String name;
    private String inventoryNumber;
    private String serialNumber;
    private EquipmentStatus status;
    private SimpleEntityResponse category;
    private SimpleEntityResponse storage;
}
