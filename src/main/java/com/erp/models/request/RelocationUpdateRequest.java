package com.erp.models.request;

import com.erp.enums.RelocationState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelocationUpdateRequest {
    private RelocationState state;
    private String description;
    /** Date selected in the «Прийняти» dialog for a FINISHED internal relocation. */
    private LocalDate receivedDate;
}
