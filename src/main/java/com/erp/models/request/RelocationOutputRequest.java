package com.erp.models.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class RelocationOutputRequest extends RelocationRequest {
    private String sendingPersonName;
    private String sendingPersonRank;
    private String receivingPersonName;
    private String receivingPersonRank;
}
