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
public class UserModelResponse {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String rank;
    private boolean enabled;
    @Builder.Default
    private List<SimpleEntityResponse> storages = new ArrayList<>();
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    @Builder.Default
    private List<RoleModelResponse> realmRoles = new ArrayList<>();
    private String createdTimestamp;
}
