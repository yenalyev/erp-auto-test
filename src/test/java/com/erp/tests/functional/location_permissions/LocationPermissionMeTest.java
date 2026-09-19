package com.erp.tests.functional.location_permissions;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.AccessFixture;
import com.erp.fixtures.LocationPermissionSupport;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.access.GrantScopeKind;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.response.UserMeResponse;
import com.erp.models.response.UserModelResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPMA-644: {@code GET /users/me} contract for LOCATION_MIXED (N full + M RO).
 */
@Slf4j
@Epic("Administration")
@Feature("REQ-LOC-PERM")
@Story("Session /users/me")
public class LocationPermissionMeTest extends BaseFunctionalTest {

    private UserFixture userFixture;
    private AccessFixture accessFixture;
    private StorageFixture storageFixture;
    private UserFixture.LocationPermissionIds ids;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void ensureMixedUser() {
        userFixture = new UserFixture(testContext, apiExecutor);
        accessFixture = new AccessFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        long ro2 = LocationPermissionSupport.resolveRo2StorageId(storageFixture);
        ids = userFixture.ensureLocationMixedUser(getPlaywrightSessionProvider(), ro2);
        log.info("LOCATION_MIXED ids: full=[{},{}] ro=[{},{}]",
                ids.fullA1(), ids.fullA2(), ids.roB1(), ids.roB2());
    }

    @Test
    @TestCaseId("TC-LOC-ME-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            LOCATION_MIXED (A1,A2 full + B1,B2 RO):
            allowedStorageIds = union; mutate permissions лише на full ids.
            """)
    public void meExposesUnionAndMutateOnlyOnFull() {
        UserMeResponse me = userFixture.getMe(UserRole.LOCATION_MIXED);

        assertThat(me.getAllowedStorageIds())
                .as("allowedStorageIds must include all full and RO locations")
                .containsAll(ids.allAllowed());

        for (Long fullId : ids.fullIds()) {
            assertThat(me.hasReadOn(fullId))
                    .as("full location %s must have read", fullId)
                    .isTrue();
            assertThat(me.hasMutateOn(fullId))
                    .as("full location %s must have mutate", fullId)
                    .isTrue();
        }
        for (Long roId : ids.roIds()) {
            assertThat(me.hasReadOn(roId))
                    .as("RO location %s must have read", roId)
                    .isTrue();
            assertThat(me.hasMutateOn(roId))
                    .as("RO location %s must NOT have mutate", roId)
                    .isFalse();
        }
    }

    @Test
    @TestCaseId("TC-LOC-ADM-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Ensure LOCATION_MIXED user (2 full + 2 RO + Owner+Viewer) — API setup path for ADM-01.")
    public void ensureMixedUserBindings() {
        assertThat(ids.allAllowed()).hasSize(4);
        UserMeResponse me = userFixture.getMe(UserRole.LOCATION_MIXED);
        assertThat(me.getUsername()).isEqualToIgnoringCase(UserRole.LOCATION_MIXED.getUsername());
        assertThat(me.getAllowedStorageIds()).containsAll(ids.allAllowed());
    }

    @Test
    @TestCaseId("TC-LOC-ME-002")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            AC-05: для локації X одночасно grant ролей «Керівник локації» і
            «Перегляд локації» → повний доступ (full wins).
            """)
    public void overlapFullAndRoOnSameLocationGivesFullAccess() {
        long overlapId = ids.fullA1();
        String username = UserRole.LOCATION_MIXED.getUsername();

        UserModelResponse listed = userFixture.findUserByUsername(username)
                .orElseThrow(() -> new IllegalStateException("LOCATION_MIXED missing"));
        UserModelResponse user = userFixture.getUser(UserRole.ADMIN, listed.getId());

        try {
            accessFixture.ensureGrants(user.getId(), List.of(UserFixture.BUSINESS_UNIT_VIEWER_ROLE_NAME),
                    List.of(), GrantScopeKind.LOCATION, overlapId);
            apiExecutor.clearSessionCache();

            assertThat(accessFixture.grants(user.getId(), false))
                    .as("both full and viewer role grants must exist for the overlap location")
                    .filteredOn(grant -> grant.getStorage() != null
                            && overlapId == grant.getStorage().getId())
                    .extracting(grant -> grant.getRole().getName())
                    .contains(UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME,
                            UserFixture.BUSINESS_UNIT_VIEWER_ROLE_NAME);

            UserMeResponse me = userFixture.getMe(UserRole.LOCATION_MIXED);
            assertThat(me.getAllowedStorageIds())
                    .as("overlap location must stay in allowedStorageIds")
                    .contains(overlapId);
            assertThat(me.hasReadOn(overlapId))
                    .as("overlap location must have read")
                    .isTrue();
            assertThat(me.hasMutateOn(overlapId))
                    .as("full+_ro on same location → mutate (full wins)")
                    .isTrue();

            Response create = apiExecutor.execute(
                    ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                    UserRole.LOCATION_MIXED,
                    ManufacturingListRequest.builder().items(List.of()).build(),
                    String.valueOf(overlapId));
            assertThat(create.statusCode())
                    .as("POST production on overlap full+_ro must not be RBAC 403")
                    .isNotEqualTo(403);
        } finally {
            ids = userFixture.ensureLocationMixedUser(getPlaywrightSessionProvider(), ids.roB2());
            apiExecutor.clearSessionCache();
        }
    }
}
