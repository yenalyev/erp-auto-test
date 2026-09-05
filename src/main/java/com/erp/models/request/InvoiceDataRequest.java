package com.erp.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoiceDataRequest {
    private LocalDate validUntilDate;
    private LocalDate operationDate;
    private String operationType;
    private String operationReason;
    private String sendName;
    private String receiveName;
    private List<InvoiceItemRequest> items;
    private String sendingPersonRank;
    private String sendingPersonName;
    private String receivingPersonRank;
    private String receivingPersonName;
}
