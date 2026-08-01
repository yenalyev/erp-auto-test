package com.erp.tests.functional.location_permissions;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.user.UserDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationPermissionSupport;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.request.UserRequest;
import com.erp.models.response.SimpleEntityResponse;
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

import java.util.ArrayList;
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
    private StorageFixture storageFixture;
    private UserFixture.LocationPermissionIds ids;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void ensureMixedUser() {
        userFixture = new UserFixture(testContext, apiExecutor);
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
            AC-05: для локації X одночасно var_business_unit_id::X (Локації) і
            var_business_unit_id_ro::X (Дозволи) → повний доступ (full wins).
            """)
    public void overlapFullAndRoOnSameLocationGivesFullAccess() {
        long overlapId = ids.fullA1();
        String overlapRo = UserFixture.BUSINESS_UNIT_RO_PREFIX + overlapId;
        String username = UserRole.LOCATION_MIXED.getUsername();

        UserModelResponse listed = userFixture.findUserByUsername(username)
                .orElseThrow(() -> new IllegalStateException("LOCATION_MIXED missing"));
        // Page search omits storages — load full card before PUT.
        // Staging GET often returns storages=null while raw permissions still hold
        // var_business_unit_id::*; PUT must re-send storages explicitly or full bindings are wiped.
        UserModelResponse user = userFixture.getUser(UserRole.ADMIN, listed.getId());

        try {
            List<String> permissions = new ArrayList<>(
                    user.getPermissions() != null ? user.getPermissions() : List.of());
            if (!permissions.contains(overlapRo)) {
                permissions.add(overlapRo);
            }
            UserRequest update = UserDataFactory.fromExisting(user).toBuilder()
                    .storages(List.of(
                            SimpleEntityResponse.builder().id(ids.fullA1()).name("full-a1").build(),
                            SimpleEntityResponse.builder().id(ids.fullA2()).name("full-a2").build()))
                    .permissions(permissions)
                    .build();
            userFixture.updateUser(UserRole.ADMIN, user.getId(), update);
            apiExecutor.clearSessionCache();

            UserModelResponse afterSetup = userFixture.getUser(UserRole.ADMIN, user.getId());
            String fullAttr = "var_business_unit_id::" + overlapId;
            assertThat(afterSetup.getPermissions())
                    .as("raw permissions must keep full + _ro for same location X")
                    .contains(fullAttr, overlapRo);

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
