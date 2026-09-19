package com.erp.models.request;

import com.erp.models.access.GrantScopeKind;
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
public class AccessGrantRequest {
    @Builder.Default
    private List<Long> roleIds = new ArrayList<>();
    @Builder.Default
    private List<String> permissionKeys = new ArrayList<>();
    private GrantScopeKind scopeKind;
    private Long storageId;
    private String comment;
}
