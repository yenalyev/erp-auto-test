package com.erp.models.response;

import com.erp.models.access.AccessScopeKind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessPermissionResponse {
    private String key;
    private String module;
    private String name;
    private String description;
    private AccessScopeKind scopeKind;
    private boolean baseline;
    @Builder.Default
    private List<String> roleNames = new ArrayList<>();
}
