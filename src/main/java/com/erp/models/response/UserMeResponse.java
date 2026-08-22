package com.erp.models.response;

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
public class UserMeResponse {
    private String username;
    private String name;
    private String rank;
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    @Builder.Default
    private List<String> roles = new ArrayList<>();
    @Builder.Default
    private List<Long> allowedStorageIds = new ArrayList<>();
    private Boolean isAdmin;

    /** True when expanded permissions include {@code entity::<storageId>::read} (or {@code ::view}). */
    public boolean hasReadOn(long storageId) {
        String id = String.valueOf(storageId);
        return permissions != null && permissions.stream().anyMatch(p -> {
            String[] parts = p.split("::");
            return parts.length == 3
                    && id.equals(parts[1])
                    && ("read".equals(parts[2]) || "view".equals(parts[2]));
        });
    }

    /**
     * True when expanded permissions include a mutate op ({@code create}/{@code update}/{@code delete})
     * for the given storage id.
     */
    public boolean hasMutateOn(long storageId) {
        String id = String.valueOf(storageId);
        return permissions != null && permissions.stream().anyMatch(p -> {
            String[] parts = p.split("::");
            return parts.length == 3
                    && id.equals(parts[1])
                    && ("create".equals(parts[2]) || "update".equals(parts[2]) || "delete".equals(parts[2]));
        });
    }

    /** True when expanded permissions include {@code order::<storageId>::create}. */
    public boolean hasOrderCreateOn(long storageId) {
        String expected = "order::" + storageId + "::create";
        return permissions != null && permissions.stream().anyMatch(expected::equals);
    }
}
