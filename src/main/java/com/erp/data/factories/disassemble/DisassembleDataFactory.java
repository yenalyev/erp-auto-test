package com.erp.data.factories.disassemble;

import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.models.request.DisassembleItemRequest;
import com.erp.models.request.DisassembleListRequest;
import com.erp.models.request.ProcessResourceOutputRequest;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.TechnologicalMapResponse;

import java.time.LocalDate;
import java.util.List;

public final class DisassembleDataFactory {

    private DisassembleDataFactory() {
    }

    public static DisassembleListRequest buildCreateRequest(TechnologicalMapResponse techMap,
                                                              double disassembleAmount,
                                                              double actualTotalProduced) {
        return buildCreateRequest(
                techMap,
                disassembleAmount,
                actualTotalProduced,
                LocalDate.now(),
                ProductionDataFactory.uniqueBatchNumber());
    }

    public static DisassembleListRequest buildCreateRequest(TechnologicalMapResponse techMap,
                                                              double disassembleAmount,
                                                              double actualTotalProduced,
                                                              LocalDate date,
                                                              String batchNumber) {
        if (techMap == null) {
            throw new IllegalStateException("ERROR - Test Setup Error: 'techMap' is null");
        }
        if (techMap.getInput() == null || techMap.getInput().isEmpty()) {
            throw new IllegalStateException("ERROR - Test Setup Error: disassemble tech map has no input resources");
        }
        if (techMap.getOutput() == null || techMap.getOutput().isEmpty()) {
            throw new IllegalStateException("ERROR - Test Setup Error: disassemble tech map has no output resources");
        }

        ResourceUsageResponse inputUsage = techMap.getInput().getFirst();
        ResourceUsageResponse outputUsage = techMap.getOutput().getFirst();

        List<ProcessResourceOutputRequest> outputs = List.of(
                ProcessResourceOutputRequest.builder()
                        .resourceId(outputUsage.getResource().getId())
                        .amount(outputUsage.getAmount())
                        .totalAmount(actualTotalProduced)
                        .build()
        );

        DisassembleItemRequest item = DisassembleItemRequest.builder()
                .disassembledItemId(inputUsage.getResource().getId())
                .techMapId(techMap.getId())
                .amount(disassembleAmount)
                .date(date)
                .batchNumber(batchNumber)
                .outputs(outputs)
                .build();

        return DisassembleListRequest.builder()
                .items(List.of(item))
                .build();
    }
}
