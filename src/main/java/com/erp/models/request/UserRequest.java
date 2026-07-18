package com.erp.models.request;

import com.erp.models.response.RoleModelResponse;
import com.erp.models.response.SimpleEntityResponse;
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
public class UserRequest {
    private String username;
    private String firstName;
    private String lastName;
    private String rank;
    @Builder.Default
    private boolean enabled = true;
    @Builder.Default
    private List<SimpleEntityResponse> storages = new ArrayList<>();
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    @Builder.Default
    private List<RoleModelResponse> realmRoles = new ArrayList<>();
}
