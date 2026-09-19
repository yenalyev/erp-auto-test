package com.erp.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMeResponse {
    private String username;
    private String name;
    private String rank;
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    @Builder.Default
    private List<Long> allowedStorageIds = new ArrayList<>();
    private Boolean isAdmin;
    private boolean subscribedToNotifications;
    @Builder.Default
    private List<AccessGrantSummaryResponse> grants = new ArrayList<>();

    /** Supports both legacy per-location permissions and the current aggregate permission contract. */
    public boolean hasReadOn(long storageId) {
        return hasLegacyStoragePermission(storageId, Set.of("read", "view"))
                || ((isStorageAllowed(storageId) || hasLocationGrant(storageId))
                && hasAggregateOperation(Set.of("read", "view")));
    }

    /**
     * True when expanded permissions include a mutate op ({@code create}/{@code update}/{@code delete})
     * for the given storage id.
     */
    public boolean hasMutateOn(long storageId) {
        Set<String> operations = Set.of("create", "update", "delete", "manage");
        return hasLegacyStoragePermission(storageId, operations)
                || (hasAggregateOperation(operations)
                && hasFullLocationGrant(storageId));
    }

    /** True when expanded permissions include {@code order::<storageId>::create}. */
    public boolean hasOrderCreateOn(long storageId) {
        String expected = "order::" + storageId + "::create";
        return permissions != null && (permissions.stream().anyMatch(expected::equals)
                || (permissions.contains("order::create")
                && hasFullLocationGrant(storageId)));
    }

    private boolean isStorageAllowed(long storageId) {
        return allowedStorageIds != null && allowedStorageIds.contains(storageId);
    }

    private boolean hasFullLocationGrant(long storageId) {
        return grants != null && grants.stream().anyMatch(grant -> grant.getStorage() != null
                && Long.valueOf(storageId).equals(grant.getStorage().getId())
                && "Керівник локації".equals(grant.getName()));
    }

    private boolean hasLocationGrant(long storageId) {
        return grants != null && grants.stream().anyMatch(grant -> grant.getStorage() != null
                && Long.valueOf(storageId).equals(grant.getStorage().getId()));
    }

    private boolean hasLegacyStoragePermission(long storageId, Set<String> operations) {
        String id = String.valueOf(storageId);
        return permissions != null && permissions.stream().anyMatch(permission -> {
            String[] parts = permission.split("::");
            return parts.length == 3 && id.equals(parts[1]) && operations.contains(parts[2]);
        });
    }

    private boolean hasAggregateOperation(Set<String> operations) {
        return permissions != null && permissions.stream().anyMatch(permission -> {
            String[] parts = permission.split("::");
            return parts.length == 2 && operations.contains(parts[1]);
        });
    }
}
