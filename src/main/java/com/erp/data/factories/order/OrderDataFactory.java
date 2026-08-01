package com.erp.data.factories.order;

import com.erp.data.FakerProvider;
import com.erp.models.request.BookingRequest;
import com.erp.models.request.GatheringStorageRequest;
import com.erp.models.request.OrderCommentRequest;
import com.erp.models.request.OrderLineRequest;
import com.erp.models.request.OrderRequest;
import com.erp.models.request.PreparedRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.ResourceUsageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OrderDataFactory {

    private OrderDataFactory() {
    }

    public static OrderLineRequest line(Long resourceId, double quantity) {
        return OrderLineRequest.builder()
                .resourceId(resourceId)
                .quantity(BigDecimal.valueOf(quantity))
                .build();
    }

    public static OrderRequest buildOrderRequest(Long storageId, Long resourceId, double quantity) {
        return OrderRequest.builder()
                .storageId(storageId)
                .lines(List.of(line(resourceId, quantity)))
                .build();
    }

    public static OrderRequest buildMultiLineOrderRequest(Long storageId, Map<Long, Double> resourceQuantities) {
        List<OrderLineRequest> lines = new ArrayList<>();
        resourceQuantities.forEach((resourceId, qty) -> lines.add(line(resourceId, qty)));
        return OrderRequest.builder()
                .storageId(storageId)
                .lines(lines)
                .build();
    }

    public static BookingRequest buildBookingRequest(Long resourceId, double amount) {
        return BookingRequest.builder()
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .build();
    }

    public static GatheringStorageRequest buildGatheringStorageRequest(Long gatheringStorageId) {
        return GatheringStorageRequest.builder()
                .gatheringStorageId(gatheringStorageId)
                .build();
    }

    public static PreparedRequest buildPreparedRequest(boolean prepared) {
        return PreparedRequest.builder()
                .prepared(prepared)
                .build();
    }

    public static OrderCommentRequest buildCommentRequest(String text) {
        return OrderCommentRequest.builder()
                .text(text)
                .build();
    }

    public static OrderCommentRequest buildCommentRequest() {
        return buildCommentRequest(FakerProvider.ukrainian().lorem().sentence(6));
    }

    public static RelocationOutputRequest buildShipRequest(Long orderId,
                                                           Long gatheringStorageId,
                                                           Long requesterStorageId,
                                                           Long resourceId,
                                                           double amount) {
        return RelocationOutputRequest.builder()
                .orderId(orderId)
                .senderId(gatheringStorageId)
                .recipientId(requesterStorageId)
                .description("order-shipment")
                .date(LocalDate.now())
                .items(List.of(ResourceUsageRequest.builder()
                        .resourceId(resourceId)
                        .amount(BigDecimal.valueOf(amount))
                        .build()))
                .sendingPersonName("Test Sender")
                .sendingPersonRank("Сержант")
                .receivingPersonName("Test Receiver")
                .receivingPersonRank("Лейтенант")
                .build();
    }

    public static RelocationOutputRequest buildShipMultiItemRequest(Long orderId,
                                                                    Long gatheringStorageId,
                                                                    Long requesterStorageId,
                                                                    Map<Long, Double> resourceAmounts) {
        List<ResourceUsageRequest> items = new ArrayList<>();
        resourceAmounts.forEach((resourceId, amount) -> items.add(ResourceUsageRequest.builder()
                .resourceId(resourceId)
                .amount(BigDecimal.valueOf(amount))
                .build()));
        return RelocationOutputRequest.builder()
                .orderId(orderId)
                .senderId(gatheringStorageId)
                .recipientId(requesterStorageId)
                .description("order-shipment")
                .date(LocalDate.now())
                .items(items)
                .sendingPersonName("Test Sender")
                .sendingPersonRank("Сержант")
                .receivingPersonName("Test Receiver")
                .receivingPersonRank("Лейтенант")
                .build();
    }
}
