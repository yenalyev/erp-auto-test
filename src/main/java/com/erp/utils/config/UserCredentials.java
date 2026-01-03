package com.erp.utils.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentials {
    private String username;
    private String password;

    public static UserCredentials of(String username, String password) {
        return new UserCredentials(username, password);
    }
}