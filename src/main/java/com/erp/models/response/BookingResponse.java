package com.erp.models.response;

import com.erp.enums.BookingState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingResponse {
    private Long id;
    private Long orderLineId;
    private Long resourceId;
    private String resourceName;
    private Long sourceStorageId;
    private String sourceStorageName;
    private BigDecimal amount;
    private BookingState state;
    private boolean prepared;
    private Instant preparedAt;
    private String preparedBy;
}
