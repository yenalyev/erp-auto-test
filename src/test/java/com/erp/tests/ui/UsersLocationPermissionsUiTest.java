package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.user.UserDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationPermissionSupport;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.UserRequest;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.models.response.UserModelResponse;
import com.erp.pages.UsersAdminPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPMA-644: admin create/edit of users with mixed full + view-only location bindings.
 */
@Slf4j
@Epic("Administration")
@Feature("REQ-LOC-PERM")
@Story("Users admin location permissions")
public class UsersLocationPermissionsUiTest extends BaseUITest {

    private UserFixture userFixture;
    private StorageFixture storageFixture;
    private UserFixture.LocationPermissionIds ids;
    private long ro2StorageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        userFixture = new UserFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        ro2StorageId = LocationPermissionSupport.resolveRo2StorageId(storageFixture);
        ids = userFixture.ensureLocationMixedUser(getPlaywrightSessionProvider(), ro2StorageId);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupUsers() {
        userFixture.deactivateTrackedUsers();
    }

    @Test(priority = 1)
    @TestCaseId("TC-LOC-ADM-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API create path for MIXED_MULTI (ensure) + user visible in /users.
            Location bindings asserted via GET /users/me.
            """)
    public void mixedUserVisibleInAdminList() {
        prepareAdminSession();
        String username = UserRole.LOCATION_MIXED.getUsername();
        UsersAdminPage usersPage = new UsersAdminPage(page).open()
                .searchByUsername(username);

        assertThat(usersPage.isUsernameVisibleInTable(username))
                .as("LOCATION_MIXED user must be listed in /users")
                .isTrue();
        usersPage.attachScreenshot("TC-LOC-ADM-001 — list");

        UserModelResponse listed = userFixture.findUserByUsername(username)
                .orElseThrow(() -> new IllegalStateException("LOCATION_MIXED missing"));
        UserModelResponse user = userFixture.getUser(UserRole.ADMIN, listed.getId());
        assertThat(user.getPermissions())
                .as("User must retain RO bindings in admin GET")
                .contains(
                        UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB1(),
                        UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB2());

        assertLocationBindingsIntact(userFixture.getMe(UserRole.LOCATION_MIXED));
    }

    @Test(priority = 2)
    @TestCaseId("TC-LOC-ADM-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            UI edit firstName (патерн TC-UI-USR-006): ім'я зберігається; _ro у permissions лишається.
            Full-локації після Save перевіряє TC-LOC-ADM-003 (окремий regression на wipe).
            """)
    public void editPreservesRoPermissions() {
        try {
            prepareAdminSession();
            String username = UserRole.LOCATION_MIXED.getUsername();
            String updatedFirstName = "LocEdit" + System.currentTimeMillis();

            UsersAdminPage usersPage = new UsersAdminPage(page).open()
                    .searchByUsername(username)
                    .clickUsernameLink(username);

            usersPage.updateFirstName(updatedFirstName).saveUser()
                    .searchByUsername(username)
                    .clickUsernameLink(username);

            assertThat(usersPage.getFirstNameFieldValue()).contains(updatedFirstName);
            usersPage.attachScreenshot("TC-LOC-ADM-002 — edit");

            UserModelResponse user = loadUserByUsername(username);
            assertThat(user.getFirstName()).contains(updatedFirstName);
            assertThat(user.getPermissions())
                    .as("_ro bindings must survive UI Save")
                    .contains(
                            UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB1(),
                            UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB2());
        } finally {
            restoreLocationMixedBindings();
        }
    }

    @Test(priority = 3)
    @TestCaseId("TC-LOC-ADM-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Regression: UI Save (лише зміна Ім'я) не повинен затирати full-локації та _ro.

            Відомий дефект (tk-ui UserUpdatePage):
            storages payload = options.filter(id in storageIds) — id поза getNames → [];
            KeycloakMapper відкидає var_business_unit_id::* з поля «Дозволи».
            Handoff: злити user.storages у options; Save map storageIds з union.

            Очікувана поведінка після фіксу продукту — assert нижче зелений.
            Патерн UI як TC-UI-USR-006.
            """)
    public void uiSaveMustNotWipeLocationBindings() {
        try {
            UserMeResponse meBefore = userFixture.getMe(UserRole.LOCATION_MIXED);
            assertLocationBindingsIntact(meBefore);

            prepareAdminSession();
            String username = UserRole.LOCATION_MIXED.getUsername();
            String updatedFirstName = "WipeCheck" + System.currentTimeMillis();

            UsersAdminPage usersPage = new UsersAdminPage(page).open()
                    .searchByUsername(username)
                    .clickUsernameLink(username);

            assertThat(usersPage.hasSelectedLocationChips())
                    .as("Локації must show selected chips before Save (empty selector = wipe risk on Save)")
                    .isTrue();

            usersPage.updateFirstName(updatedFirstName).saveUser()
                    .searchByUsername(username)
                    .clickUsernameLink(username);

            assertThat(usersPage.getFirstNameFieldValue()).contains(updatedFirstName);
            assertThat(usersPage.hasSelectedLocationChips())
                    .as("Локації must still show selected chips after Save+reopen")
                    .isTrue();
            usersPage.attachScreenshot("TC-LOC-ADM-003 — after UI Save");

            apiExecutor.clearSessionCache();
            UserMeResponse meAfter = userFixture.getMe(UserRole.LOCATION_MIXED);
            assertLocationBindingsIntact(meAfter);

            UserModelResponse user = loadUserByUsername(username);
            assertThat(user.getPermissions())
                    .as("_ro must remain in admin GET after UI Save")
                    .contains(
                            UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB1(),
                            UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB2());
        } finally {
            restoreLocationMixedBindings();
        }
    }

    private void assertLocationBindingsIntact(UserMeResponse me) {
        assertThat(me.getAllowedStorageIds())
                .as("allowedStorageIds must include all full+RO locations")
                .containsAll(ids.allAllowed());
        for (Long fullId : ids.fullIds()) {
            assertThat(me.hasReadOn(fullId))
                    .as("full %s must have read", fullId)
                    .isTrue();
            assertThat(me.hasMutateOn(fullId))
                    .as("full %s must have mutate (UI Save must not wipe var_business_unit_id)", fullId)
                    .isTrue();
        }
        for (Long roId : ids.roIds()) {
            assertThat(me.hasReadOn(roId))
                    .as("RO %s must have read", roId)
                    .isTrue();
            assertThat(me.hasMutateOn(roId))
                    .as("RO %s must not have mutate", roId)
                    .isFalse();
        }
    }

    private UserModelResponse loadUserByUsername(String username) {
        UserModelResponse listed = userFixture.findUserByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User missing: " + username));
        return userFixture.getUser(UserRole.ADMIN, listed.getId());
    }

    private void restoreLocationMixedBindings() {
        try {
            UserModelResponse user = loadUserByUsername(UserRole.LOCATION_MIXED.getUsername());
            UserRequest restore = UserDataFactory.fromExisting(user).toBuilder()
                    .firstName("Location")
                    .storages(List.of(
                            SimpleEntityResponse.builder().id(ids.fullA1()).name("full-a1").build(),
                            SimpleEntityResponse.builder().id(ids.fullA2()).name("full-a2").build()))
                    .permissions(List.of(
                            UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB1(),
                            UserFixture.BUSINESS_UNIT_RO_PREFIX + ids.roB2()))
                    .realmRoles(List.of(
                            userFixture.fetchRealmRole(UserFixture.BUSINESS_UNIT_OWNER_ROLE_NAME),
                            userFixture.fetchRealmRole(UserFixture.BUSINESS_UNIT_VIEWER_ROLE_NAME)))
                    .build();
            userFixture.updateUser(UserRole.ADMIN, user.getId(), restore);
            apiExecutor.clearSessionCache();
            ids = userFixture.ensureLocationMixedUser(getPlaywrightSessionProvider(), ro2StorageId);
        } catch (Exception e) {
            log.warn("Failed to restore LOCATION_MIXED bindings: {}", e.getMessage());
        }
    }

    private void prepareAdminSession() {
        injectAllLocationsView();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
    }
}
