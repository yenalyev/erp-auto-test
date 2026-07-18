package com.erp.data.factories.incident;

import com.erp.enums.IncidentResourceOperation;
import com.erp.models.request.IncidentResourceRequest;
import com.erp.models.request.RelocationIncidentRequest;
import com.erp.models.response.RelocationResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class IncidentDataFactory {

    private IncidentDataFactory() {
    }

    public static RelocationIncidentRequest buildFullCargoLoss(RelocationResponse relocation, String description) {
        Long senderId = relocation.getSender().getId();
        List<IncidentResourceRequest> resources = relocation.getItems().stream()
                .map(item -> IncidentResourceRequest.builder()
                        .storageId(senderId)
                        .resourceId(item.getResource().getId())
                        .operation(IncidentResourceOperation.WRITE_OFF)
                        .amount(item.getAmount())
                        .build())
                .collect(Collectors.toList());
        return RelocationIncidentRequest.builder()
                .dateTime(Instant.now())
                .relocationId(relocation.getId())
                .description(description)
                .resources(resources)
                .build();
    }

    public static RelocationIncidentRequest buildWriteOff(Long relocationId,
                                                          Long senderStorageId,
                                                          Long resourceId,
                                                          double amount,
                                                          String description) {
        return RelocationIncidentRequest.builder()
                .dateTime(Instant.now())
                .relocationId(relocationId)
                .description(description)
                .resources(List.of(IncidentResourceRequest.builder()
                        .storageId(senderStorageId)
                        .resourceId(resourceId)
                        .operation(IncidentResourceOperation.WRITE_OFF)
                        .amount(BigDecimal.valueOf(amount))
                        .build()))
                .build();
    }

    public static String uniqueDescription() {
        return "erp-incident-" + System.currentTimeMillis();
    }
}
