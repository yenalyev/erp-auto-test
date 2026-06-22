package com.erp.models.common;

import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GlobalPlanChainContext {
    private Long l1StorageId;
    private Long l2StorageId;
    private ResourceResponse resourceA;
    private ResourceResponse resourceB;
    private ResourceResponse resourceC;
    private ResourceResponse resourceX;
    private ResourceResponse resourceY;
    private ResourceResponse resourceZ;
    private TechnologicalMapResponse mapM1;
    private TechnologicalMapResponse mapM2;
    private TechnologicalMapResponse mapM3;
}
