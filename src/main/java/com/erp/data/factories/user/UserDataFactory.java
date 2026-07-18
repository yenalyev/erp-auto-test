package com.erp.data.factories.user;

import com.erp.models.request.UserRequest;
import com.erp.models.response.UserModelResponse;

import java.util.List;

public final class UserDataFactory {

    private UserDataFactory() {
    }

    public static UserRequest createRandom(String prefix) {
        String username = prefix + System.currentTimeMillis();
        return UserRequest.builder()
                .username(username)
                .firstName("Auto")
                .lastName("Test")
                .rank("")
                .enabled(true)
                .storages(List.of())
                .permissions(List.of())
                .realmRoles(List.of())
                .build();
    }

    public static UserRequest fromExisting(UserModelResponse existing) {
        return UserRequest.builder()
                .username(existing.getUsername())
                .firstName(existing.getFirstName())
                .lastName(existing.getLastName())
                .rank(existing.getRank() != null ? existing.getRank() : "")
                .enabled(existing.isEnabled())
                .storages(existing.getStorages() != null ? existing.getStorages() : List.of())
                .permissions(existing.getPermissions() != null ? existing.getPermissions() : List.of())
                .realmRoles(existing.getRealmRoles() != null ? existing.getRealmRoles() : List.of())
                .build();
    }

    public static UserRequest deactivated(UserModelResponse existing) {
        return fromExisting(existing).toBuilder()
                .enabled(false)
                .build();
    }
}
