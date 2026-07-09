package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Item DTO for equipment create/update.
 * Batch create wraps items in {@link EquipmentCreateRequest}; invoice/storage
 * batch fields live on the wrapper for {@code POST /api/v1/equipment}.
 */
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
    /** Used on PUT update; ignored by backend create items (batch wrapper owns storageId). */
    private Long storageId;
    private Long senderStorageId;
    private String invoiceNumber;
    private Boolean isPaidByCash;
    private BigDecimal paidAmount;
    private Long assigneeId;
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();
}
