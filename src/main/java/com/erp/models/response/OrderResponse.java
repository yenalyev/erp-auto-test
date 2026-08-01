package com.erp.models.response;

import com.erp.enums.OrderState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderResponse {
    private Long id;
    private OrderState state;
    private SimpleEntityResponse storage;
    private SimpleEntityResponse gatheringStorage;
    private Integer activeBookings;
    private Integer preparedBookings;
    private String createdBy;
    private Instant createdAt;
    @Builder.Default
    private List<OrderLineResponse> lines = new ArrayList<>();
    @Builder.Default
    private List<OrderCommentResponse> comments = new ArrayList<>();
}
