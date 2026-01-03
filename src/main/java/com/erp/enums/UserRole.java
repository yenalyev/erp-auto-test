package com.erp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
    ADMIN("admin", "karta33#"),
    OWNER_1("owner1", "karta33#"),
    OWNER_2("owner2", "karta33#"),
    OWNER_3("owner3", "karta33#"),
    ANONYMOUS("", "");

    private final String username;
    private final String password;
}
