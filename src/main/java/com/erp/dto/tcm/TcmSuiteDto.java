package com.erp.dto.tcm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TcmSuiteDto {

    private String scopeType;
    private Long scopeId;
    private String scopeCode;
    private String scopeTitle;
    private Long testPlanId;
    private List<String> automationTestIds = new ArrayList<>();
    private int automationCount;
    private int eligibleTestCaseCount;
}
