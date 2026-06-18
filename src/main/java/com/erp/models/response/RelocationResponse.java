package com.erp.models.response;

import com.erp.enums.RelocationState;
import com.erp.enums.RelocationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelocationResponse {
    private Long id;
    private RelocationType type;
    private RelocationState state;
    private String description;
    private String invoiceNumber;
    @Builder.Default
    private List<RelocationItemResponse> items = new ArrayList<>();
    @Builder.Default
    private List<EquipmentSimpleResponse> equipmentItems = new ArrayList<>();
    private SimpleEntityResponse sender;
    private SimpleEntityResponse recipient;
    private LocalDate date;
    private Boolean canGenerateInvoice;
    private Boolean hasExternalInvoicePhoto;
    private Boolean isPaidByCash;
    private BigDecimal paidAmount;
    private String sendingPersonName;
    private String sendingPersonRank;
    private String receivingPersonName;
    private String receivingPersonRank;
    private Instant createdAt;
}
