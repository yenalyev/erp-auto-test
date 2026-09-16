package com.erp.models.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderRelocationTaskRequest {
    private Long sourceStorageId;
    @Builder.Default
    private List<OrderRelocationTaskLineRequest> lines = new ArrayList<>();
}
