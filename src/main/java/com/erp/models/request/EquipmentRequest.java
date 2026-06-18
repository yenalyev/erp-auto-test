package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentRequest {
    private String name;
    private String inventoryNumber;
    private String serialNumber;
    private String description;
    private Long categoryId;
    private Long storageId;
    private Long senderStorageId;
    private String invoiceNumber;
    private Boolean isPaidByCash;
    private BigDecimal paidAmount;
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();
}
