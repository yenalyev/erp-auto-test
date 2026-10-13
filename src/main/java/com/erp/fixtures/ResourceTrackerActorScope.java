package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.data.BusinessRoleCatalog;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.models.access.AccessScopeKind;
import com.erp.models.response.AccessRoleResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.auth.PlaywrightSessionProvider;

import java.util.List;

/** Isolated user with only the built-in resource-tracker role on a test location. */
public class ResourceTrackerActorScope {

    private static final String ROLE_NAME = "Відстеження ресурсів";

    private final ApiExecutor apiExecutor;
    private final PlaywrightSessionProvider playwright;
    private final UserFixture userFixture;
    private final LocationProfileFixture locationFixture;
    private final AccessFixture accessFixture;
    private UserFixture.BusinessActor actor;

    public ResourceTrackerActorScope(TestContext testContext, ApiExecutor apiExecutor,
                                     PlaywrightSessionProvider playwright) {
        this.apiExecutor = apiExecutor;
        this.playwright = playwright;
        this.userFixture = new UserFixture(testContext, apiExecutor);
        this.locationFixture = new LocationProfileFixture(testContext, apiExecutor);
        this.accessFixture = new AccessFixture(testContext, apiExecutor);
    }

    public UserFixture.BusinessActor acquire() {
        if (actor != null) {
            throw new IllegalStateException("resource-tracker actor already acquired");
        }
        AccessRoleResponse role = accessFixture.roleByName(ROLE_NAME);
        if (!role.isSystem() || role.getScopeKind() != AccessScopeKind.LOCATION
                || !role.getPermissionKeys().containsAll(List.of("resource-viewer.read", "resource.read"))
                || !BusinessRoleCatalog.definition(BusinessRole.RESOURCE_VIEWER)
                        .accessRoles().equals(List.of(ROLE_NAME))) {
            throw new IllegalStateException("System resource-tracker role or its permissions have changed");
        }

        var location = locationFixture.create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst();
        // Do not add the default location-head role: this actor has only resource-tracker.
        actor = userFixture.createLowPrivilegeBusinessActor(
                playwright, BusinessRole.RESOURCE_VIEWER, List.of(location));
        var grants = accessFixture.grants(actor.userId(), false);
        if (grants.size() != 1
                || grants.getFirst().getRole() == null
                || !ROLE_NAME.equals(grants.getFirst().getRole().getName())
                || grants.getFirst().getPermissionKey() != null
                || grants.getFirst().getStorage() == null
                || !location.getId().equals(grants.getFirst().getStorage().getId())) {
            throw new IllegalStateException("resource-tracker actor must have only the scoped system role");
        }
        apiExecutor.setSessionForRole(UserRole.RESOURCE_VIEWER,
                actor.username(), actor.password());
        return actor;
    }

    public void release() {
        try {
            if (actor != null) {
                apiExecutor.restoreDefaultSessionForRole(UserRole.RESOURCE_VIEWER);
            }
        } finally {
            try {
                userFixture.deactivateTrackedUsers();
            } finally {
                locationFixture.cleanup();
                actor = null;
            }
        }
    }
}
