package com.erp.data.factories.defect;

import com.erp.enums.DefectType;
import com.erp.models.common.DefectBatchItem;
import com.erp.models.common.DefectWriteOffBatch;
import com.erp.models.request.DefectRequest;
import com.erp.models.request.DefectWriteOffRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Static builders for defect ("Брак") request bodies, mirroring backend {@code DefectRequest} /
 * {@code DefectWriteOffRequest} shapes used in {@code DefectControllerIT}.
 */
public final class DefectDataFactory {

    private DefectDataFactory() {
    }

    /** Production defect: linked to a production process; batch resolved from the production batchNumber. */
    public static DefectRequest buildProductionDefect(Long storageId,
                                                      Long resourceId,
                                                      Long productionProcessId,
                                                      double amount,
                                                      LocalDate date) {
        return DefectRequest.builder()
                .date(date)
                .storageId(storageId)
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .type(DefectType.PRODUCTION)
                .productionProcessId(productionProcessId)
                .description("erp-auto-test production defect")
                .build();
    }

    /** Storage defect with no explicit batches: backend consumes stock by FIFO (non-produced resources). */
    public static DefectRequest buildStorageFifoDefect(Long storageId,
                                                       Long resourceId,
                                                       double amount) {
        return DefectRequest.builder()
                .date(LocalDate.now())
                .storageId(storageId)
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .type(DefectType.STORAGE)
                .isProduced(false)
                .description("erp-auto-test storage FIFO defect")
                .build();
    }

    /** Storage defect with explicit produced batches (isProduced = true). */
    public static DefectRequest buildStorageExplicitBatchesDefect(Long storageId,
                                                                  Long resourceId,
                                                                  double totalAmount,
                                                                  List<DefectBatchItem> batches) {
        return DefectRequest.builder()
                .date(LocalDate.now())
                .storageId(storageId)
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(totalAmount))
                .type(DefectType.STORAGE)
                .isProduced(true)
                .defectBatches(batches)
                .description("erp-auto-test storage explicit-batch defect")
                .build();
    }

    /** Relocation defect (inbound receipt): backend consumes from the relocation's batches (FIFO when none given). */
    public static DefectRequest buildRelocationDefect(Long storageId,
                                                      Long resourceId,
                                                      Long relocationId,
                                                      double amount,
                                                      LocalDate date) {
        return DefectRequest.builder()
                .date(date)
                .storageId(storageId)
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .type(DefectType.RELOCATION)
                .relocationId(relocationId)
                .description("erp-auto-test relocation defect")
                .build();
    }

    /** Return-from-unit defect: consumes from the specified returned relocation. */
    public static DefectRequest buildRelocationFromUnitDefect(Long storageId,
                                                              Long resourceId,
                                                              Long relocationId,
                                                              double amount,
                                                              LocalDate date) {
        return DefectRequest.builder()
                .date(date)
                .storageId(storageId)
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .type(DefectType.RELOCATION_FROM_UNIT)
                .relocationId(relocationId)
                .description("erp-auto-test relocation-from-unit defect")
                .build();
    }

    public static DefectBatchItem batch(String batchNumber, boolean isProduced, double amount) {
        return DefectBatchItem.builder()
                .batchNumber(batchNumber)
                .isProduced(isProduced)
                .amount(BigDecimal.valueOf(amount))
                .build();
    }

    public static DefectWriteOffRequest buildWriteOff(Long defectId,
                                                      Long storageId,
                                                      double amount,
                                                      String description) {
        return DefectWriteOffRequest.builder()
                .defectId(defectId)
                .storageId(storageId)
                .amount(BigDecimal.valueOf(amount))
                .description(description)
                .build();
    }

    public static DefectWriteOffRequest buildWriteOffWithBatches(Long defectId,
                                                                 Long storageId,
                                                                 double amount,
                                                                 List<DefectWriteOffBatch> batches) {
        return DefectWriteOffRequest.builder()
                .defectId(defectId)
                .storageId(storageId)
                .amount(BigDecimal.valueOf(amount))
                .batches(batches)
                .description("erp-auto-test write-off with batches")
                .build();
    }
}
