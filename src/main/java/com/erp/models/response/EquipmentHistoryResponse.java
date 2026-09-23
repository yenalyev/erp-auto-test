package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EquipmentHistoryResponse {
    private Instant dateTime;
    private SimpleEntityResponse equipment;
    private SimpleEntityResponse storage;
    private String operation;
    private String message;
    private String invoiceNumber;
    private String invoiceDownloadUrl;
    private String comment;
}
