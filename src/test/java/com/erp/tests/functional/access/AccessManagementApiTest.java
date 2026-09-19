package com.erp.tests.functional.access;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.AccessFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.access.AccessScopeKind;
import com.erp.models.access.GrantScopeKind;
import com.erp.models.response.AccessGrantResponse;
import com.erp.models.response.AccessPermissionResponse;
import com.erp.models.response.AccessRoleResponse;
import com.erp.models.response.UserModelResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Administration")
@Feature("DB-backed access roles and grants")
public class AccessManagementApiTest extends BaseFunctionalTest {

    private static final String LOCATION_PERMISSION = "production-order.allocate";

    private AccessFixture access;
    private UserFixture users;
    private UserModelResponse user;
    private Long grantId;
    private long allowedStorageId;
    private long foreignStorageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void arrangeUser() {
        access = new AccessFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        user = users.createTestUser("access-api-");
        allowedStorageId = ConfigProvider.getOwner1StorageId();
        foreignStorageId = ConfigProvider.getOwner2StorageId();
        assertThat(foreignStorageId).isNotEqualTo(allowedStorageId);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupUser() {
        if (users != null) {
            users.deactivateTrackedUsers();
        }
    }

    @Test(priority = 1)
    @TestCaseId("TC-ACCESS-API-001")
    @Severity(SeverityLevel.CRITICAL)
    public void catalogAndSystemRolesUseCanonicalPermissions() {
        List<AccessPermissionResponse> catalog = access.listCatalog();
        assertThat(catalog)
                .hasSizeGreaterThan(100)
                .extracting(AccessPermissionResponse::getKey)
                .doesNotHaveDuplicates()
                .allMatch(key -> key.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)+"))
                .contains(LOCATION_PERMISSION, "access.manage", "users.read");

        AccessRoleResponse locationHead = access.roleByName(UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME);
        assertThat(locationHead.isSystem()).isTrue();
        assertThat(locationHead.getScopeKind()).isEqualTo(AccessScopeKind.LOCATION);
        assertThat(locationHead.getPermissionKeys())
                .contains("location.read", "order.create")
                .doesNotContain("access.manage");
    }

    @Test(priority = 2, dependsOnMethods = "catalogAndSystemRolesUseCanonicalPermissions")
    @TestCaseId("TC-ACCESS-API-002")
    @Severity(SeverityLevel.CRITICAL)
    public void locationGrantIsEffectiveOnlyInsideItsScope() {
        List<AccessGrantResponse> created = access.ensureGrants(
                user.getId(), List.of(), List.of(LOCATION_PERMISSION),
                GrantScopeKind.LOCATION, allowedStorageId);

        assertThat(created).hasSize(1);
        AccessGrantResponse grant = created.getFirst();
        grantId = grant.getId();
        assertThat(grant.getPermissionKey()).isEqualTo(LOCATION_PERMISSION);
        assertThat(grant.getScopeKind()).isEqualTo(GrantScopeKind.LOCATION);
        assertThat(grant.getStorage().getId()).isEqualTo(allowedStorageId);
        assertThat(access.hasEffectivePermission(user.getId(), LOCATION_PERMISSION, allowedStorageId)).isTrue();
        assertThat(access.hasEffectivePermission(user.getId(), LOCATION_PERMISSION, foreignStorageId)).isFalse();
    }

    @Test(priority = 3, dependsOnMethods = "locationGrantIsEffectiveOnlyInsideItsScope")
    @TestCaseId("TC-ACCESS-API-003")
    @Severity(SeverityLevel.CRITICAL)
    public void revokedGrantStopsBeingEffectiveImmediately() {
        AccessGrantResponse revoked = access.revoke(grantId, "Access lifecycle test");

        assertThat(revoked.getRevokedAt()).isNotBlank();
        assertThat(access.grants(user.getId(), false)).isEmpty();
        assertThat(access.grants(user.getId(), true))
                .filteredOn(grant -> grantId.equals(grant.getId()))
                .singleElement()
                .extracting(AccessGrantResponse::isActive)
                .isEqualTo(false);
        assertThat(access.hasEffectivePermission(user.getId(), LOCATION_PERMISSION, allowedStorageId)).isFalse();
    }
}
