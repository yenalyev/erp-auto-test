package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelocationItemBatchRequest {
    private UUID batchUuid;
    private String batchNumber;
    private BigDecimal amount;
    private Boolean isProduced;
    private String accResourceId;
    private BigDecimal paidAmount;
}
