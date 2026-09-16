package com.erp.models.response;

import com.erp.enums.OrderRelocationTaskState;
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
public class OrderRelocationTaskResponse {
    private Long id;
    private Long orderId;
    private OrderRelocationTaskState state;
    private SimpleEntityResponse sourceStorage;
    private SimpleEntityResponse gatheringStorage;
    private Long relocationId;
    private String relocationState;
    private String createdBy;
    private Instant createdAt;
    @Builder.Default
    private List<OrderRelocationTaskLineResponse> lines = new ArrayList<>();
}
