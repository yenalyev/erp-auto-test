package com.erp.dto.tcm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TcmResultDto {

    private String testCaseId;
    private String status;
    private Long durationMs;
    private String errorMessage;
    private String executedAt;
}
