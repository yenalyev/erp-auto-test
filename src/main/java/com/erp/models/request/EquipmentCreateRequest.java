package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Multipart part {@code request} for {@code POST /api/v1/equipment}.
 * Matches backend {@code org.pm.tk.dto.request.EquipmentCreateRequest}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentCreateRequest {
    private Long storageId;
    private Long senderStorageId;
    private String invoiceNumber;
    private Boolean isPaidByCash;
    private BigDecimal paidAmount;
    private List<EquipmentRequest> items;
}
