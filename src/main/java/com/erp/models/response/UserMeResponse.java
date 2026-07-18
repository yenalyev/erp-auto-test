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
}
