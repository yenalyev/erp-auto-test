package com.erp.models.request;

import com.erp.models.access.GrantScopeKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AccessGrantScopeRequest {
    private GrantScopeKind scopeKind;
    private Long storageId;
}
