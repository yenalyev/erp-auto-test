package com.erp.models.response;

import com.erp.models.access.GrantScopeKind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectivePermissionSourceResponse {
    private Long grantId;
    private String grantName;
    private GrantScopeKind grantScopeKind;
    private SimpleEntityResponse storage;
}
