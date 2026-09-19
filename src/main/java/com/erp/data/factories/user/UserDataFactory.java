package com.erp.data.factories.user;

import com.erp.models.request.UserRequest;
import com.erp.models.response.UserModelResponse;

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
                .build();
    }

    public static UserRequest fromExisting(UserModelResponse existing) {
        return UserRequest.builder()
                .username(existing.getUsername())
                .firstName(existing.getFirstName())
                .lastName(existing.getLastName())
                .rank(existing.getRank() != null ? existing.getRank() : "")
                .enabled(existing.isEnabled())
                .build();
    }

    public static UserRequest deactivated(UserModelResponse existing) {
        return fromExisting(existing).toBuilder()
                .enabled(false)
                .build();
    }
}
