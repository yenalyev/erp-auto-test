package com.erp.utils.auth;

import com.erp.enums.UserRole;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionUnauthorizedRetryTest {

    @Test
    public void namedRoleRetriesOn401() {
        assertThat(SessionUnauthorizedRetry.shouldRelogin(UserRole.ADMIN, 401)).isTrue();
        assertThat(SessionUnauthorizedRetry.shouldRelogin(UserRole.OWNER_1, 401)).isTrue();
    }

    @Test
    public void anonymous401IsTheAssertion() {
        assertThat(SessionUnauthorizedRetry.shouldRelogin(UserRole.ANONYMOUS, 401)).isFalse();
    }

    @Test
    public void otherStatusesDoNotRelogin() {
        assertThat(SessionUnauthorizedRetry.shouldRelogin(UserRole.ADMIN, 200)).isFalse();
        assertThat(SessionUnauthorizedRetry.shouldRelogin(UserRole.ADMIN, 403)).isFalse();
        assertThat(SessionUnauthorizedRetry.shouldRelogin(UserRole.ADMIN, 500)).isFalse();
        assertThat(SessionUnauthorizedRetry.shouldRelogin(null, 401)).isFalse();
    }
}
