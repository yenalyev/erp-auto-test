package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.user.UserDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.UserRequest;
import com.erp.models.response.OneTimeUserCredentialsResponse;
import com.erp.models.response.PagedUserResponse;
import com.erp.models.response.RoleModelResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.models.response.UserModelResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.auth.PlaywrightSessionProvider;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.PollUtils;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class UserFixture extends BaseFixture {

    public static final String ADMINISTRATOR_ROLE_NAME = "Administrator-ROLE";
    public static final String PROJECT_PRODUCTION_ROLE_NAME = "Project-Production-ROLE";
    public static final String BUSINESS_UNIT_OWNER_ROLE_NAME = "Business_Unit_Owner-ROLE";
    public static final String BUSINESS_UNIT_VIEWER_ROLE_NAME = "Business_Unit_Viewer-ROLE";
    public static final String BUSINESS_UNIT_RO_PREFIX = "var_business_unit_id_ro::";

    private final List<String> trackedUserIds = new ArrayList<>();

    public UserFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("API: підготувати контекст користувача для RBAC matrix")
    public void prepareRbacUserContext() {
        if (testContext.get(ContextKey.SHARED_USER_ID) != null) {
            return;
        }
        testContext.set(ContextKey.SHARED_ROLE_NAME, ADMINISTRATOR_ROLE_NAME);
        UserModelResponse user = createTestUser("rbac-matrix-");
        testContext.set(ContextKey.SHARED_USER_ID, user.getId());
        testContext.set(ContextKey.SHARED_USER, user);
    }

    /**
     * Ensures staging/dev project-production users exist with {@link #PROJECT_PRODUCTION_ROLE_NAME}
     * and owner1 storage. Creates via ADMIN {@code POST /users} when missing, then bootstraps
     * the permanent password through Keycloak UPDATE_PASSWORD.
     */
    @Step("FIXTURE: Ensure project-production users (projectprod / projectprodab)")
    public void ensureProjectProductionUsers(PlaywrightSessionProvider playwright) {
        Long storageId = ConfigProvider.getOwner1StorageId();
        ensureUser(
                playwright,
                UserRole.PROJECT_ADMIN.getUsername(),
                UserRole.PROJECT_ADMIN.getPassword(),
                "Проектний",
                "Адмін",
                PROJECT_PRODUCTION_ROLE_NAME,
                storageId);
        ensureUser(
                playwright,
                UserRole.PROJECT_MANAGER.getUsername(),
                UserRole.PROJECT_MANAGER.getPassword(),
                "Проектний",
                "Менеджер",
                PROJECT_PRODUCTION_ROLE_NAME,
                storageId);
    }

    /**
     * Ensures {@link UserRole#LOCATION_MIXED} exists with 2 full + 2 RO business units
     * (CPMA-644): Owner+Viewer roles, storages A1/A2, permissions {@code var_business_unit_id_ro::B*}.
     *
     * @return resolved storage ids (A1, A2 full; B1, B2 read-only)
     */
    @Step("FIXTURE: Ensure LOCATION_MIXED user (full A1/A2 + RO B1/B2)")
    public LocationPermissionIds ensureLocationMixedUser(PlaywrightSessionProvider playwright,
                                                         long ro2StorageId) {
        long a1 = ConfigProvider.getOwner1StorageId();
        long a2 = ConfigProvider.getUnitStorageId();
        long b1 = ConfigProvider.getOwner2StorageId();
        long b2 = ro2StorageId;
        if (b2 <= 0 || b2 == a1 || b2 == a2 || b2 == b1) {
            throw new IllegalArgumentException(
                    "ro2StorageId must be a positive id distinct from A1/A2/B1, got: " + b2);
        }

        String username = UserRole.LOCATION_MIXED.getUsername();
        String password = UserRole.LOCATION_MIXED.getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "user.location-mixed.password is empty — set it in config / env before running LOCATION_MIXED tests");
        }

        RoleModelResponse ownerRole = fetchRealmRole(BUSINESS_UNIT_OWNER_ROLE_NAME);
        RoleModelResponse viewerRole = fetchRealmRole(BUSINESS_UNIT_VIEWER_ROLE_NAME);
        List<SimpleEntityResponse> fullStorages = List.of(
                SimpleEntityResponse.builder().id(a1).name("full-a1").build(),
                SimpleEntityResponse.builder().id(a2).name("full-a2").build());
        List<String> roPermissions = List.of(
                BUSINESS_UNIT_RO_PREFIX + b1,
                BUSINESS_UNIT_RO_PREFIX + b2);

        Optional<UserModelResponse> existing = findUserByUsername(username);
        if (existing.isPresent()) {
            UserModelResponse user = existing.get();
            if (!hasMixedLocationBinding(user, a1, a2, b1, b2)) {
                log.info("Updating LOCATION_MIXED user {} with full=[{},{}] ro=[{},{}]",
                        username, a1, a2, b1, b2);
                UserRequest update = UserDataFactory.fromExisting(user).toBuilder()
                        .enabled(true)
                        .storages(fullStorages)
                        .permissions(mergeNonUnitPermissions(user.getPermissions(), roPermissions))
                        .realmRoles(List.of(ownerRole, viewerRole))
                        .build();
                updateUser(UserRole.ADMIN, user.getId(), update);
                apiExecutor.clearSessionCache();
            } else {
                log.info("LOCATION_MIXED user {} already has expected location bindings", username);
            }
            return new LocationPermissionIds(a1, a2, b1, b2);
        }

        log.info("Creating LOCATION_MIXED user {} full=[{},{}] ro=[{},{}]", username, a1, a2, b1, b2);
        UserRequest request = UserRequest.builder()
                .username(username)
                .firstName("Location")
                .lastName("Mixed")
                .rank("")
                .enabled(true)
                .storages(fullStorages)
                .permissions(roPermissions)
                .realmRoles(List.of(ownerRole, viewerRole))
                .build();

        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create LOCATION_MIXED user");
        OneTimeUserCredentialsResponse credentials = response.as(OneTimeUserCredentialsResponse.class);
        if (playwright == null) {
            throw new IllegalStateException(
                    "LOCATION_MIXED user created but PlaywrightSessionProvider is null — cannot bootstrap password");
        }
        playwright.bootstrapPermanentPassword(username, credentials.getPassword(), password);
        apiExecutor.clearSessionCache();
        return new LocationPermissionIds(a1, a2, b1, b2);
    }

    private static boolean hasMixedLocationBinding(UserModelResponse user,
                                                   long a1, long a2, long b1, long b2) {
        boolean hasFull = userHasStorage(user, a1) && userHasStorage(user, a2);
        List<String> permissions = user.getPermissions() != null ? user.getPermissions() : List.of();
        boolean hasRo = permissions.contains(BUSINESS_UNIT_RO_PREFIX + b1)
                && permissions.contains(BUSINESS_UNIT_RO_PREFIX + b2);
        // No RO on full locations — overlap full+_ro is a separate scenario (TC-LOC-ME-002).
        boolean noOverlapRoOnFull = !permissions.contains(BUSINESS_UNIT_RO_PREFIX + a1)
                && !permissions.contains(BUSINESS_UNIT_RO_PREFIX + a2);
        boolean hasRoles = userHasRole(user, BUSINESS_UNIT_OWNER_ROLE_NAME)
                && userHasRole(user, BUSINESS_UNIT_VIEWER_ROLE_NAME);
        return hasFull && hasRo && noOverlapRoOnFull && hasRoles;
    }

    /**
     * Keep non-unit permission strings (drop raw {@code var_business_unit_id::*} and all
     * {@code var_business_unit_id_ro::*}), then set required RO bindings exactly.
     */
    private static List<String> mergeNonUnitPermissions(List<String> existing, List<String> requiredRo) {
        List<String> merged = new ArrayList<>();
        if (existing != null) {
            for (String p : existing) {
                if (p != null
                        && !p.startsWith("var_business_unit_id::")
                        && !p.startsWith(BUSINESS_UNIT_RO_PREFIX)) {
                    merged.add(p);
                }
            }
        }
        for (String ro : requiredRo) {
            if (!merged.contains(ro)) {
                merged.add(ro);
            }
        }
        return merged;
    }

    /** Resolved MIXED_MULTI storage ids for LOCATION_MIXED persona. */
    public record LocationPermissionIds(long fullA1, long fullA2, long roB1, long roB2) {
        public List<Long> allAllowed() {
            return List.of(fullA1, fullA2, roB1, roB2);
        }

        public List<Long> fullIds() {
            return List.of(fullA1, fullA2);
        }

        public List<Long> roIds() {
            return List.of(roB1, roB2);
        }
    }

    @Step("FIXTURE: Ensure user «{username}» with role {roleName}")
    public UserModelResponse ensureUser(PlaywrightSessionProvider playwright,
                                        String username,
                                        String permanentPassword,
                                        String firstName,
                                        String lastName,
                                        String roleName,
                                        Long storageId) {
        Optional<UserModelResponse> existing = findUserByUsername(username);
        if (existing.isPresent()) {
            UserModelResponse user = existing.get();
            boolean needsUpdate = !userHasRole(user, roleName) || !userHasStorage(user, storageId);
            if (needsUpdate) {
                log.info("Updating existing user {} — assign role={} storage={}", username, roleName, storageId);
                UserRequest update = UserDataFactory.fromExisting(user).toBuilder()
                        .enabled(true)
                        .realmRoles(List.of(fetchRealmRole(roleName)))
                        .storages(List.of(SimpleEntityResponse.builder().id(storageId).name("owner1").build()))
                        .build();
                user = updateUser(UserRole.ADMIN, user.getId(), update);
            } else {
                log.info("User {} already present with role {} and storage {}", username, roleName, storageId);
            }
            return user;
        }

        log.info("Creating user {} with role {} storage={}", username, roleName, storageId);
        RoleModelResponse role = fetchRealmRole(roleName);
        UserRequest request = UserRequest.builder()
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .rank("")
                .enabled(true)
                .storages(List.of(SimpleEntityResponse.builder().id(storageId).name("owner1").build()))
                .permissions(List.of())
                .realmRoles(List.of(role))
                .build();

        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create user " + username);
        OneTimeUserCredentialsResponse credentials = response.as(OneTimeUserCredentialsResponse.class);
        log.info("User created: username={}, one-time password issued — bootstrapping permanent password",
                credentials.getUsername());

        if (playwright == null) {
            throw new IllegalStateException(
                    "User " + username + " was created but PlaywrightSessionProvider is null — "
                            + "cannot bootstrap permanent password. One-time password was issued by API.");
        }
        playwright.bootstrapPermanentPassword(username, credentials.getPassword(), permanentPassword);

        String userId = findUserIdByUsername(username);
        return waitForUser(UserRole.ADMIN, userId);
    }

    /**
     * ADMIN {@code POST /users} → Keycloak user with Owner role and a single storage.
     * Bootstraps a permanent password via Playwright UPDATE_PASSWORD.
     */
    @Step("FIXTURE: створити restricted owner «{storage.name}»")
    public RestrictedOwnerUser createRestrictedOwner(
            PlaywrightSessionProvider playwright,
            StorageResponse storage) {
        if (playwright == null) {
            throw new IllegalStateException(
                    "PlaywrightSessionProvider is required to bootstrap the isolated owner password");
        }
        long suffix = System.nanoTime();
        String username = "visiso" + suffix;
        String permanentPassword = "VisIso1!" + suffix;
        RoleModelResponse ownerRole = fetchRealmRole(BUSINESS_UNIT_OWNER_ROLE_NAME);
        UserRequest request = UserRequest.builder()
                .username(username)
                .firstName("Vis")
                .lastName("Iso")
                .rank("")
                .enabled(true)
                .storages(List.of(SimpleEntityResponse.builder()
                        .id(storage.getId())
                        .name(storage.getName())
                        .build()))
                .permissions(List.of())
                .realmRoles(List.of(ownerRole))
                .build();

        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create isolated restricted owner");
        OneTimeUserCredentialsResponse credentials = response.as(OneTimeUserCredentialsResponse.class);
        String userId = findUserIdByUsername(username);
        trackForCleanup(userId);
        playwright.bootstrapPermanentPassword(username, credentials.getPassword(), permanentPassword);
        waitForUser(UserRole.ADMIN, userId);
        log.info("Created isolated restricted owner username={} storageId={}", username, storage.getId());
        return new RestrictedOwnerUser(userId, username, permanentPassword);
    }

    public record RestrictedOwnerUser(String userId, String username, String password) {
    }

    /**
     * Existing stand user (e.g. {@code 3bat}) gets {@link #BUSINESS_UNIT_OWNER_ROLE_NAME}
     * and the requester UNIT without dropping other roles (unit-analytics).
     * Does not create a new username.
     */
    @Step("FIXTURE: Ensure existing user «{username}» is Owner of UNIT {unitStorageId}")
    public UserModelResponse ensureExistingUserIsUnitOwner(String username, Long unitStorageId) {
        UserModelResponse user = findUserByUsername(username).orElseThrow(() -> new IllegalStateException(
                "User '" + username + "' must already exist on the stand — will not create a new account"));
        boolean hasOwner = userHasRole(user, BUSINESS_UNIT_OWNER_ROLE_NAME);
        boolean hasUnit = userHasStorage(user, unitStorageId);
        if (hasOwner && hasUnit) {
            log.info("User {} already has Owner role and UNIT {}", username, unitStorageId);
            return user;
        }
        RoleModelResponse ownerRole = fetchRealmRole(BUSINESS_UNIT_OWNER_ROLE_NAME);
        List<RoleModelResponse> roles = new ArrayList<>(
                user.getRealmRoles() != null ? user.getRealmRoles() : List.of());
        if (!hasOwner) {
            roles.add(ownerRole);
        }
        List<SimpleEntityResponse> storages = new ArrayList<>(
                user.getStorages() != null ? user.getStorages() : List.of());
        if (!hasUnit) {
            storages.add(SimpleEntityResponse.builder().id(unitStorageId).name("unit").build());
        }
        log.info("Updating existing user {} — add Owner on UNIT {} (keep {} roles)",
                username, unitStorageId, roles.size());
        UserRequest update = UserDataFactory.fromExisting(user).toBuilder()
                .enabled(true)
                .realmRoles(roles)
                .storages(storages)
                .build();
        UserModelResponse updated = updateUser(UserRole.ADMIN, user.getId(), update);
        apiExecutor.clearSessionCache();
        return updated;
    }

    @Step("API: GET realm role «{roleName}»")
    public RoleModelResponse fetchRealmRole(String roleName) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_GET_ROLE_BY_NAME, UserRole.ADMIN, roleName);
        validateSuccess(response, "Get realm role " + roleName);
        return response.as(RoleModelResponse.class);
    }

    public Optional<UserModelResponse> findUserByUsername(String username) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.USER_GET_PAGE,
                UserRole.ADMIN,
                Map.of("username", username, "size", 20, "page", 0));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }
        PagedUserResponse page = response.as(PagedUserResponse.class);
        if (page.getContent() == null) {
            return Optional.empty();
        }
        return page.getContent().stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    private static boolean userHasRole(UserModelResponse user, String roleName) {
        return user.getRealmRoles() != null
                && user.getRealmRoles().stream().anyMatch(r -> roleName.equals(r.getName()));
    }

    private static boolean userHasStorage(UserModelResponse user, Long storageId) {
        return user.getStorages() != null
                && user.getStorages().stream().anyMatch(s -> storageId.equals(s.getId()));
    }

    @Step("API: створити тестового користувача «{prefix}»")
    public UserModelResponse createTestUser(String prefix) {
        UserRequest request = UserDataFactory.createRandom(prefix);
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create user");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.USER_POST_CREATE);

        OneTimeUserCredentialsResponse credentials = response.as(OneTimeUserCredentialsResponse.class);
        log.info("User created: username={}", credentials.getUsername());

        String userId = findUserIdByUsername(request.getUsername());
        UserModelResponse user = waitForUser(UserRole.ADMIN, userId);
        trackForCleanup(userId);
        return user;
    }

    @Step("API: отримати користувача {userId}")
    public UserModelResponse getUser(UserRole role, String userId) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_GET_BY_ID, role, userId);
        validateSuccess(response, "Get user by id");
        return response.as(UserModelResponse.class);
    }

    @Step("API: GET /users/me роллю {role}")
    public UserMeResponse getMe(UserRole role) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_GET_ME, role, null);
        validateSuccess(response, "GET /users/me as " + role);
        return response.as(UserMeResponse.class);
    }

    private UserModelResponse waitForUser(UserRole role, String userId) {
        return PollUtils.waitUntil(
                () -> {
                    Response response = apiExecutor.execute(
                            ApiEndpointDefinition.USER_GET_BY_ID, role, userId);
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.as(UserModelResponse.class);
                    }
                    return null;
                },
                Objects::nonNull,
                10_000,
                "Get user by id " + userId);
    }

    @Step("API: оновити користувача {userId}")
    public UserModelResponse updateUser(UserRole role, String userId, UserRequest body) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_PUT_UPDATE, role, body, userId);
        validateSuccess(response, "Update user");
        return response.as(UserModelResponse.class);
    }

    @Step("API: деактивувати користувача {userId}")
    public void deactivateUser(UserRole role, String userId) {
        UserModelResponse existing = getUser(role, userId);
        UserRequest body = UserDataFactory.deactivated(existing);
        updateUser(role, userId, body);
        untrackForCleanup(userId);
    }

    public void trackForCleanup(String userId) {
        if (userId != null && !trackedUserIds.contains(userId)) {
            trackedUserIds.add(userId);
        }
    }

    public void untrackForCleanup(String userId) {
        trackedUserIds.remove(userId);
    }

    @Step("API: cleanup tracked test users")
    public void deactivateTrackedUsers() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            log.warn("Staging mode — skipping user cleanup (-Dstaging.cleanup=false)");
            trackedUserIds.clear();
            return;
        }
        List<String> ids = new ArrayList<>(trackedUserIds);
        for (String userId : ids) {
            try {
                deactivateUser(UserRole.ADMIN, userId);
            } catch (Exception e) {
                log.warn("Failed to deactivate user {}: {}", userId, e.getMessage());
            }
        }
        trackedUserIds.clear();
    }

    public void trackUserByUsername(String username) {
        trackForCleanup(findUserIdByUsername(username));
    }

    public String findUserIdByUsername(String username) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.USER_GET_PAGE,
                UserRole.ADMIN,
                Map.of("username", username, "size", 20, "page", 0));
        validateSuccess(response, "Search user by username");
        PagedUserResponse page = response.as(PagedUserResponse.class);
        return page.getContent().stream()
                .filter(u -> username.equals(u.getUsername()))
                .map(UserModelResponse::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("User not found after create: " + username));
    }
}
