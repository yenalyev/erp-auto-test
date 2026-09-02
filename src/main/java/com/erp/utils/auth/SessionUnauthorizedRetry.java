package com.erp.utils.auth;

import com.erp.enums.UserRole;

/**
 * Dead JSESSIONID on remote envs returns 401 (empty body). Tests must re-login
 * and retry — except {@link UserRole#ANONYMOUS}, where 401 is the assertion.
 */
public final class SessionUnauthorizedRetry {

    private SessionUnauthorizedRetry() {
    }

    public static boolean shouldRelogin(UserRole role, int statusCode) {
        return role != null && role != UserRole.ANONYMOUS && statusCode == 401;
    }
}
