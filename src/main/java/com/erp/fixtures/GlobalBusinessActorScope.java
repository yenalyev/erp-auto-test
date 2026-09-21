package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.test_context.TestContext;
import com.erp.utils.auth.PlaywrightSessionProvider;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

/** Per-test-class lifecycle for an isolated global actor bound to a {@link UserRole} slot. */
@Slf4j
public class GlobalBusinessActorScope {

    private final UserFixture userFixture;
    private final ApiExecutor apiExecutor;
    private final PlaywrightSessionProvider playwright;
    private UserRole boundRole;
    private UserFixture.BusinessActor actor;

    public GlobalBusinessActorScope(
            TestContext testContext,
            ApiExecutor apiExecutor,
            PlaywrightSessionProvider playwright) {
        this.userFixture = new UserFixture(testContext, apiExecutor);
        this.apiExecutor = apiExecutor;
        this.playwright = playwright;
    }

    @Step("FIXTURE: створити й підключити глобального актора {businessRole} як {role}")
    public UserFixture.BusinessActor acquire(UserRole role, BusinessRole businessRole) {
        if (boundRole != null) {
            throw new IllegalStateException("Global actor scope is already bound to " + boundRole);
        }
        actor = userFixture.createGlobalBusinessActor(playwright, businessRole);
        apiExecutor.setSessionForRole(role, actor.username(), actor.password());
        boundRole = role;
        log.info("Bound global actor {} businessRole={} to {}", actor.username(), businessRole, role);
        return actor;
    }

    public UserFixture.BusinessActor actor() {
        if (actor == null) {
            throw new IllegalStateException("Call acquire() before requesting the global actor");
        }
        return actor;
    }

    @Step("FIXTURE: відновити role binding і деактивувати глобального актора")
    public void release() {
        if (boundRole != null) {
            try {
                apiExecutor.restoreDefaultSessionForRole(boundRole);
            } catch (RuntimeException e) {
                log.warn("Failed to restore default session for {}: {}", boundRole, e.getMessage());
            }
        }
        boundRole = null;
        actor = null;
        userFixture.deactivateTrackedUsers();
    }
}
