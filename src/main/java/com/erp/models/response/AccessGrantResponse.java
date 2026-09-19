package com.erp.models.response;

import com.erp.models.access.AccessScopeKind;
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
public class AccessGrantResponse {
    private Long id;
    private String userId;
    private String username;
    private SimpleEntityResponse role;
    private String permissionKey;
    private String name;
    private AccessScopeKind targetScopeKind;
    private GrantScopeKind scopeKind;
    private SimpleEntityResponse storage;
    private String storagePath;
    private Integer coveredCount;
    private String comment;
    private String createdBy;
    private String createdAt;
    private String revokedAt;
    private String revokedBy;

    public boolean isActive() {
        return revokedAt == null;
    }
}
