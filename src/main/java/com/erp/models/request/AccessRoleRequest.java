package com.erp.models.request;

import com.erp.models.access.AccessScopeKind;
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
public class AccessRoleRequest {
    private String name;
    private String description;
    private AccessScopeKind scopeKind;
    @Builder.Default
    private List<String> permissionKeys = new ArrayList<>();
}
