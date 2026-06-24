package com.erp.models.common;

import com.erp.models.response.RelocationResponse;
import lombok.Builder;
import lombok.Value;

import java.util.Objects;

/** Normalized relocation journal row for UI vs API cross-check. */
@Value
@Builder
public class RelocationJournalRow {

    String senderName;
    String recipientName;
    String description;
    String invoiceNumber;

    public static RelocationJournalRow fromApi(RelocationResponse relocation) {
        Objects.requireNonNull(relocation, "relocation");
        return RelocationJournalRow.builder()
                .senderName(relocation.getSender() != null ? relocation.getSender().getName() : null)
                .recipientName(relocation.getRecipient() != null ? relocation.getRecipient().getName() : null)
                .description(relocation.getDescription())
                .invoiceNumber(relocation.getInvoiceNumber())
                .build();
    }
}
