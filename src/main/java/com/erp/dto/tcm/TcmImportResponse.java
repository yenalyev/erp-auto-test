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
public class TcmImportResponse {

    private Long runId;
    private int matched;
    private int skippedManual;

    @Builder.Default
    private List<String> unmatched = new ArrayList<>();
}
