package com.erp.dto.tcm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class TcmImportResponse {

    private Long runId;
    private int matched;
    private int created;
    private int updated;
    private int skippedManual;
    private int skippedOutOfScope;

    @Builder.Default
    private List<String> unmatched = new ArrayList<>();

    @Builder.Default
    private List<String> outOfScope = new ArrayList<>();
}
