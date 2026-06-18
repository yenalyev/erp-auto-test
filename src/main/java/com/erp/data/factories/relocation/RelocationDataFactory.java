package com.erp.data.factories.relocation;

import com.erp.data.FakerProvider;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationItemBatchRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.RelocationInputEditRequest;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.ResourceResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RelocationDataFactory {

    private RelocationDataFactory() {
    }

    public static String uniqueBatchNumber() {
        return "rel-batch-" + System.currentTimeMillis();
    }

    public static String uniqueInvoiceNumber() {
        return "INV-REL-" + System.currentTimeMillis();
    }

    public static ResourceUsageRequest usage(Long resourceId, double amount) {
        return ResourceUsageRequest.builder()
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .build();
    }

    public static ResourceUsageRequest usageWithBatch(Long resourceId,
                                                      double amount,
                                                      String batchNumber,
                                                      boolean isProduced) {
        return ResourceUsageRequest.builder()
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .batches(List.of(RelocationItemBatchRequest.builder()
                        .batchNumber(batchNumber)
                        .amount(BigDecimal.valueOf(amount))
                        .isProduced(isProduced)
                        .build()))
                .build();
    }

    public static RelocationOutputRequest buildSendRequest(Long senderId,
                                                           Long recipientId,
                                                           Long resourceId,
                                                           double amount) {
        return RelocationOutputRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .description(FakerProvider.ukrainian().commerce().department())
                .date(LocalDate.now())
                .items(List.of(usage(resourceId, amount)))
                .sendingPersonName("Test Sender")
                .sendingPersonRank("Сержант")
                .receivingPersonName("Test Receiver")
                .receivingPersonRank("Лейтенант")
                .build();
    }

    public static RelocationOutputRequest buildSendWithBatch(Long senderId,
                                                             Long recipientId,
                                                             Long resourceId,
                                                             double amount,
                                                             String batchNumber,
                                                             boolean isProduced) {
        return RelocationOutputRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .description("erp-auto-test send with batch")
                .date(LocalDate.now())
                .items(List.of(usageWithBatch(resourceId, amount, batchNumber, isProduced)))
                .build();
    }

    public static RelocationInputRequest buildReceiveRequest(Long supplierId,
                                                             Long recipientId,
                                                             Long resourceId,
                                                             double amount,
                                                             String batchNumber) {
        return RelocationInputRequest.builder()
                .senderId(supplierId)
                .recipientId(recipientId)
                .description("erp-auto-test external receive")
                .invoiceNumber(uniqueInvoiceNumber())
                .date(LocalDate.now())
                .items(List.of(usageWithBatch(resourceId, amount, batchNumber, false)))
                .build();
    }

    public static RelocationInputEditRequest buildReceiveEditRequest(Long resourceId,
                                                                     double amount,
                                                                     String batchNumber,
                                                                     String description) {
        return RelocationInputEditRequest.builder()
                .description(description)
                .date(LocalDate.now())
                .items(List.of(usageWithBatch(resourceId, amount, batchNumber, false)))
                .build();
    }

    public static RelocationOutputEditRequest buildSendEditRequest(Long resourceId, double amount, String description) {
        return RelocationOutputEditRequest.builder()
                .description(description)
                .date(LocalDate.now())
                .items(List.of(usage(resourceId, amount)))
                .build();
    }

    public static RelocationOutputEditRequest buildSendEditRequest(Long resourceId,
                                                                   double amount,
                                                                   String description,
                                                                   Long recipientId) {
        return RelocationOutputEditRequest.builder()
                .description(description)
                .date(LocalDate.now())
                .recipientId(recipientId)
                .items(List.of(usage(resourceId, amount)))
                .build();
    }

    public static RelocationOutputRequest buildSendMultiItem(Long senderId,
                                                             Long recipientId,
                                                             List<ResourceUsageRequest> items) {
        return RelocationOutputRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .description("erp-auto-test multi-item send")
                .date(LocalDate.now())
                .items(items)
                .build();
    }

    /** @deprecated use {@link #buildSendRequest} */
    @Deprecated
    public static RelocationOutputRequest.RelocationOutputRequestBuilder simpleRelocation(Long fromStorageId,
                                                                                          Long toStorageId,
                                                                                          ResourceResponse resource,
                                                                                          BigDecimal amount) {
        return RelocationOutputRequest.builder()
                .description(FakerProvider.ukrainian().commerce().department())
                .senderId(fromStorageId)
                .recipientId(toStorageId)
                .date(LocalDate.now())
                .items(List.of(ResourceUsageRequest.builder()
                        .resourceId(resource.getId())
                        .amount(amount)
                        .build()));
    }
}
