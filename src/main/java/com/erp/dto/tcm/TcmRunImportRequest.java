package com.erp.dto.tcm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TcmRunImportRequest {

    private Long testPlanId;
    private Long featureId;
    private Long acId;
    private Long projectId;
    private String runName;
    private String environment;
    private String version;
    private String suite;
    private String buildName;

    @Builder.Default
    private List<TcmResultDto> results = new ArrayList<>();
}
