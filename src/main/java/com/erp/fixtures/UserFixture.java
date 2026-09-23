package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.BusinessRoleCatalog;
import com.erp.data.factories.user.UserDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.models.access.AccessScopeKind;
import com.erp.models.access.GrantScopeKind;
import com.erp.models.request.UserRequest;
import com.erp.models.response.AccessGrantResponse;
import com.erp.models.response.AccessRoleResponse;
import com.erp.models.response.OneTimeUserCredentialsResponse;
import com.erp.models.response.PagedUserResponse;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class UserFixture extends BaseFixture {

    public static final String ADMINISTRATOR_ROLE_NAME = "Адміністратор системи";
    public static final List<String> PROJECT_PRODUCTION_PERMISSION_KEYS = List.of(
            "project-production.read",
            "project-production.create",
            "project-production.update",
            "project-production.delete",
            "project-production-template.read",
            "project-production-template.manage");
    public static final String BUSINESS_UNIT_OWNER_ROLE_NAME = BusinessRoleCatalog.DEFAULT_LOCATION_ROLE_NAME;
    public static final String UNIT_OWNER_ROLE_NAME = BUSINESS_UNIT_OWNER_ROLE_NAME;
    public static final String BUSINESS_UNIT_VIEWER_ROLE_NAME = "Перегляд локації";
    public static final String CREW_READ_ROLE_NAME = "Екіпажі: перегляд";
    public static final String CREW_WRITE_ROLE_NAME = "Екіпажі: облік";
    public static final int MIN_ROLES_FOR_LIST_OVERFLOW = 5;
    private static final int MAX_ROLES_FOR_LIST_OVERFLOW = 8;

    private final List<String> trackedUserIds = new ArrayList<>();
    private final AccessFixture accessFixture;

    public UserFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.accessFixture = new AccessFixture(testContext, apiExecutor);
    }

    @Step("API: підготувати контекст користувача для RBAC matrix")
    public void prepareRbacUserContext() {
        if (testContext.get(ContextKey.SHARED_USER_ID) != null) return;
        testContext.set(ContextKey.SHARED_ROLE_NAME, ADMINISTRATOR_ROLE_NAME);
        UserModelResponse user = createTestUser("rbac-matrix-");
        accessFixture.ensureGrants(user.getId(), List.of(ADMINISTRATOR_ROLE_NAME), List.of(),
                GrantScopeKind.ALL, null);
        testContext.set(ContextKey.SHARED_USER_ID, user.getId());
        testContext.set(ContextKey.SHARED_USER, getUser(UserRole.ADMIN, user.getId()));
    }

    @Step("FIXTURE: Ensure project-production users with granular permissions")
    public void ensureProjectProductionUsers(PlaywrightSessionProvider playwright) {
        Long storageId = ConfigProvider.getOwner1StorageId();
        ensureProjectProductionUser(playwright, UserRole.PROJECT_ADMIN,
                "Проектний", "Адмін", storageId);
        ensureProjectProductionUser(playwright, UserRole.PROJECT_MANAGER,
                "Проектний", "Менеджер", storageId);
    }

    private UserModelResponse ensureProjectProductionUser(PlaywrightSessionProvider playwright,
                                                           UserRole role,
                                                           String firstName,
                                                           String lastName,
                                                           Long storageId) {
        String username = role.getUsername();
        UserModelResponse user = findUserByUsername(username).orElse(null);
        if (user == null) {
            user = createUserProfile(username, firstName, lastName, playwright, role.getPassword());
        } else if (!user.isEnabled()) {
            user = updateUser(UserRole.ADMIN, user.getId(),
                    UserDataFactory.fromExisting(user).toBuilder().enabled(true).build());
        }
        accessFixture.ensureGrants(user.getId(), locationActorRoles(List.of()),
                PROJECT_PRODUCTION_PERMISSION_KEYS, GrantScopeKind.LOCATION, storageId);
        apiExecutor.clearSessionCache();
        return getUser(UserRole.ADMIN, user.getId());
    }

    @Step("FIXTURE: Ensure LOCATION_MIXED user (full A1/A2 + RO B1/B2)")
    public LocationPermissionIds ensureLocationMixedUser(PlaywrightSessionProvider playwright, long ro2StorageId) {
        long a1 = ConfigProvider.getOwner1StorageId();
        long a2 = ConfigProvider.getUnitStorageId();
        long b1 = ConfigProvider.getOwner2StorageId();
        long b2 = ro2StorageId;
        if (b2 <= 0 || b2 == a1 || b2 == a2 || b2 == b1) {
            throw new IllegalArgumentException("ro2StorageId must be distinct from A1/A2/B1, got: " + b2);
        }

        String username = UserRole.LOCATION_MIXED.getUsername();
        String permanentPassword = UserRole.LOCATION_MIXED.getPassword();
        UserModelResponse user = findUserByUsername(username).orElse(null);
        if (user == null) {
            user = createUserProfile(username, "Location", "Mixed", playwright, permanentPassword);
        } else if (!user.isEnabled()) {
            user = updateUser(UserRole.ADMIN, user.getId(),
                    UserDataFactory.fromExisting(user).toBuilder().enabled(true).build());
        }

        accessFixture.revokeAll(user.getId());
        accessFixture.ensureGrants(user.getId(), List.of(BUSINESS_UNIT_OWNER_ROLE_NAME), List.of(),
                GrantScopeKind.LOCATION, a1);
        accessFixture.ensureGrants(user.getId(), List.of(BUSINESS_UNIT_OWNER_ROLE_NAME), List.of(),
                GrantScopeKind.LOCATION, a2);
        accessFixture.ensureGrants(user.getId(), List.of(BUSINESS_UNIT_VIEWER_ROLE_NAME), List.of(),
                GrantScopeKind.LOCATION, b1);
        accessFixture.ensureGrants(user.getId(), List.of(BUSINESS_UNIT_VIEWER_ROLE_NAME), List.of(),
                GrantScopeKind.LOCATION, b2);
        apiExecutor.clearSessionCache();
        return new LocationPermissionIds(a1, a2, b1, b2);
    }

    @Step("FIXTURE: Ensure CREW_READ / CREW_WRITE battalion user")
    public void ensureCrewBattalionUser(PlaywrightSessionProvider playwright, UserRole role) {
        if (role != UserRole.CREW_READ && role != UserRole.CREW_WRITE) {
            throw new IllegalArgumentException("Expected CREW_READ or CREW_WRITE, got: " + role);
        }
        ensureUser(playwright, role.getUsername(), role.getPassword(), "Crew",
                role == UserRole.CREW_WRITE ? "Write" : "Read",
                role == UserRole.CREW_WRITE ? CREW_WRITE_ROLE_NAME : CREW_READ_ROLE_NAME,
                ConfigProvider.getUnitStorageId());
        apiExecutor.clearSessionCache();
    }

    public record LocationPermissionIds(long fullA1, long fullA2, long roB1, long roB2) {
        public List<Long> allAllowed() { return List.of(fullA1, fullA2, roB1, roB2); }
        public List<Long> fullIds() { return List.of(fullA1, fullA2); }
        public List<Long> roIds() { return List.of(roB1, roB2); }
    }

    @Step("FIXTURE: Ensure user «{username}» with access role {roleName}")
    public UserModelResponse ensureUser(PlaywrightSessionProvider playwright,
                                        String username,
                                        String permanentPassword,
                                        String firstName,
                                        String lastName,
                                        String roleName,
                                        Long storageId) {
        UserModelResponse user = findUserByUsername(username).orElse(null);
        if (user == null) {
            user = createUserProfile(username, firstName, lastName, playwright, permanentPassword);
        } else if (!user.isEnabled()) {
            user = updateUser(UserRole.ADMIN, user.getId(),
                    UserDataFactory.fromExisting(user).toBuilder().enabled(true).build());
        }
        accessFixture.ensureGrants(user.getId(), locationActorRoles(List.of(roleName)), List.of(),
                GrantScopeKind.LOCATION, storageId);
        apiExecutor.clearSessionCache();
        return getUser(UserRole.ADMIN, user.getId());
    }

    @Step("FIXTURE: створити актора {businessRole} на тестових локаціях")
    public BusinessActor createBusinessActor(PlaywrightSessionProvider playwright,
                                             BusinessRole businessRole,
                                             List<StorageResponse> storages) {
        return createBusinessActor(playwright, businessRole, storages, List.of());
    }

    public BusinessActor createBusinessActor(PlaywrightSessionProvider playwright,
                                             BusinessRole businessRole,
                                             List<StorageResponse> storages,
                                             List<String> permissionKeys) {
        return createBusinessActor(playwright, businessRole, storages, permissionKeys, true);
    }

    /** Creates an actor whose access roles are global and therefore require no location grants. */
    @Step("FIXTURE: створити глобального бізнес-актора {businessRole}")
    public BusinessActor createGlobalBusinessActor(
            PlaywrightSessionProvider playwright,
            BusinessRole businessRole) {
        if (playwright == null) {
            throw new IllegalStateException("PlaywrightSessionProvider is required to bootstrap actor password");
        }
        BusinessRoleCatalog.Definition definition = BusinessRoleCatalog.definition(businessRole);
        if (definition.accessRoles().isEmpty()) {
            throw new IllegalStateException("Global business actor requires at least one access role: " + businessRole);
        }
        List<AccessRoleResponse> resolvedRoles = definition.accessRoles().stream()
                .map(accessFixture::roleByName)
                .toList();
        List<String> nonGlobalRoles = resolvedRoles.stream()
                .filter(role -> role.getScopeKind() != AccessScopeKind.GLOBAL)
                .map(AccessRoleResponse::getName)
                .toList();
        if (!nonGlobalRoles.isEmpty()) {
            throw new IllegalStateException(
                    "Global business actor contains non-global access roles: " + nonGlobalRoles);
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "autotest-" + businessRole.name().toLowerCase().replace('_', '-') + "-" + suffix;
        String permanentPassword = "Autotest1!" + suffix;
        UserModelResponse created = createUserProfile(
                username, "Autotest", businessRole.name(), playwright, permanentPassword);
        trackForCleanup(created.getId());

        accessFixture.ensureGrants(
                created.getId(), definition.accessRoles(), definition.permissionKeys(), GrantScopeKind.ALL, null);
        LinkedHashSet<String> expectedPermissionKeys = resolvedRoles.stream()
                .flatMap(role -> Optional.ofNullable(role.getPermissionKeys()).orElse(List.of()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        expectedPermissionKeys.addAll(definition.permissionKeys());
        assertBusinessActorAccess(
                created.getId(), businessRole, definition.accessRoles(), List.of(), expectedPermissionKeys);
        log.info("Created global business actor username={} businessRole={} accessRoles={}",
                username, businessRole, definition.accessRoles());
        return new BusinessActor(created.getId(), username, permanentPassword, businessRole, List.of());
    }

    /**
     * Explicit escape hatch for permission-denied scenarios. Normal business actors must use
     * {@link #createBusinessActor(PlaywrightSessionProvider, BusinessRole, List)}.
     */
    public BusinessActor createLowPrivilegeBusinessActor(PlaywrightSessionProvider playwright,
                                                         BusinessRole businessRole,
                                                         List<StorageResponse> storages) {
        return createBusinessActor(playwright, businessRole, storages, List.of(), false);
    }

    private BusinessActor createBusinessActor(PlaywrightSessionProvider playwright,
                                              BusinessRole businessRole,
                                              List<StorageResponse> storages,
                                              List<String> permissionKeys,
                                              boolean includeDefaultLocationRole) {
        if (playwright == null) {
            throw new IllegalStateException("PlaywrightSessionProvider is required to bootstrap actor password");
        }
        if (storages == null || storages.isEmpty() || storages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one non-null storage is required for " + businessRole);
        }
        if (permissionKeys == null || permissionKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("Permission keys cannot be null or blank");
        }

        BusinessRoleCatalog.Definition definition = BusinessRoleCatalog.definition(businessRole);
        List<String> accessRoles = includeDefaultLocationRole
                ? BusinessRoleCatalog.effectiveAccessRoles(businessRole)
                : definition.accessRoles();
        LinkedHashSet<String> allPermissionKeys = new LinkedHashSet<>(definition.permissionKeys());
        allPermissionKeys.addAll(permissionKeys);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "autotest-" + businessRole.name().toLowerCase().replace('_', '-') + "-" + suffix;
        String permanentPassword = "Autotest1!" + suffix;
        UserModelResponse created = createUserProfile(username, "Autotest", businessRole.name(),
                playwright, permanentPassword);
        trackForCleanup(created.getId());

        for (StorageResponse storage : storages) {
            accessFixture.ensureGrants(created.getId(), accessRoles, List.copyOf(allPermissionKeys),
                    GrantScopeKind.LOCATION, storage.getId());
        }
        assertBusinessActorAccess(created.getId(), businessRole, accessRoles, storages, allPermissionKeys);
        List<Long> storageIds = storages.stream().map(StorageResponse::getId).toList();
        log.info("Created business actor username={} businessRole={} accessRoles={} storageIds={}",
                username, businessRole, accessRoles, storageIds);
        return new BusinessActor(created.getId(), username, permanentPassword, businessRole, storageIds);
    }

    private void assertBusinessActorAccess(String userId,
                                           BusinessRole businessRole,
                                           List<String> expectedAccessRoles,
                                           List<StorageResponse> storages,
                                           Set<String> permissionKeys) {
        List<AccessGrantResponse> grants = accessFixture.grants(userId, false);
        Set<String> grantedRoleNames = grants.stream()
                .filter(grant -> grant.getRole() != null)
                .map(grant -> grant.getRole().getName())
                .collect(Collectors.toSet());
        if (!grantedRoleNames.containsAll(expectedAccessRoles)) {
            throw new IllegalStateException("Business actor " + businessRole + " role drift: expected="
                    + expectedAccessRoles + ", actual=" + grantedRoleNames);
        }
        for (StorageResponse storage : storages) {
            for (String key : permissionKeys) {
                if (!accessFixture.hasEffectivePermission(userId, key, storage.getId())) {
                    throw new IllegalStateException("Business actor " + businessRole
                            + " lacks effective permission " + key + " at storage " + storage.getId());
                }
            }
        }
        if (storages.isEmpty()) {
            for (String key : permissionKeys) {
                if (!accessFixture.hasEffectivePermission(userId, key, null)) {
                    throw new IllegalStateException("Global business actor " + businessRole
                            + " lacks effective permission " + key);
                }
            }
        }
    }

    public record BusinessActor(String userId, String username, String password,
                                BusinessRole businessRole, List<Long> storageIds) { }

    @Step("FIXTURE: створити restricted owner «{storage.name}»")
    public RestrictedOwnerUser createRestrictedOwner(PlaywrightSessionProvider playwright,
                                                     StorageResponse storage) {
        BusinessActor actor = createBusinessActor(playwright, BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
        return new RestrictedOwnerUser(actor.userId(), actor.username(), actor.password());
    }

    @Step("FIXTURE: створити multi-location owner на кількох локаціях")
    public RestrictedOwnerUser createMultiLocationOwner(PlaywrightSessionProvider playwright,
                                                        List<StorageResponse> storages) {
        if (storages == null || storages.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 storages for multi-location owner");
        }
        BusinessActor actor = createBusinessActor(playwright, BusinessRole.BUSINESS_UNIT_OWNER, storages);
        return new RestrictedOwnerUser(actor.userId(), actor.username(), actor.password());
    }

    public record RestrictedOwnerUser(String userId, String username, String password) { }

    @Step("FIXTURE: Ensure existing user «{username}» is location head of {unitStorageId}")
    public UserModelResponse ensureExistingUserIsUnitOwner(String username, Long unitStorageId) {
        UserModelResponse user = findUserByUsername(username).orElseThrow(() -> new IllegalStateException(
                "User '" + username + "' must already exist on the stand — will not create a new account"));
        accessFixture.ensureGrants(user.getId(), List.of(UNIT_OWNER_ROLE_NAME), List.of(),
                GrantScopeKind.LOCATION, unitStorageId);
        apiExecutor.clearSessionCache();
        return getUser(UserRole.ADMIN, user.getId());
    }

    @Step("API: GET access role «{roleName}»")
    public AccessRoleResponse fetchRealmRole(String roleName) { return accessFixture.roleByName(roleName); }

    @Step("API: GET /access/roles")
    public List<AccessRoleResponse> listRealmRoles() { return accessFixture.listRoles(); }

    public Optional<UserModelResponse> findUserByUsername(String username) {
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.USER_GET_PAGE, UserRole.ADMIN,
                Map.of("username", username, "size", 20, "page", 0));
        if (response.statusCode() < 200 || response.statusCode() >= 300) return Optional.empty();
        PagedUserResponse page = response.as(PagedUserResponse.class);
        return page.getContent() == null ? Optional.empty() : page.getContent().stream()
                .filter(user -> username.equalsIgnoreCase(user.getUsername())).findFirst();
    }

    /** Creates a profile without access grants for user-admin and access-lifecycle tests. */
    @Step("API: створити тестового користувача «{prefix}»")
    public UserModelResponse createTestUser(String prefix) {
        UserRequest request = UserDataFactory.createRandom(prefix);
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create user");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.USER_POST_CREATE);
        OneTimeUserCredentialsResponse credentials = response.as(OneTimeUserCredentialsResponse.class);
        String userId = credentials.getUserId() != null
                ? credentials.getUserId() : findUserIdByUsername(request.getUsername());
        UserModelResponse user = waitForUser(UserRole.ADMIN, userId);
        trackForCleanup(userId);
        return user;
    }

    @Step("API: створити користувача «{prefix}» з кількома access roles")
    public UserModelResponse createTestUserWithManyRoles(String prefix) {
        UserModelResponse user = createTestUser(prefix);
        LinkedHashSet<String> roles = listRealmRoles().stream()
                .filter(role -> role.getName() != null && !role.getName().isBlank())
                .sorted(Comparator.comparing(AccessRoleResponse::getName))
                .map(AccessRoleResponse::getName)
                .filter(roleName -> !BUSINESS_UNIT_OWNER_ROLE_NAME.equals(roleName))
                .limit(MAX_ROLES_FOR_LIST_OVERFLOW - 1L)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        roles.add(BUSINESS_UNIT_OWNER_ROLE_NAME);
        if (roles.size() < MIN_ROLES_FOR_LIST_OVERFLOW) {
            throw new IllegalStateException("Need ≥" + MIN_ROLES_FOR_LIST_OVERFLOW
                    + " access roles for overflow, got " + roles.size());
        }
        accessFixture.ensureGrants(user.getId(), List.copyOf(roles), List.of(), GrantScopeKind.LOCATION,
                ConfigProvider.getOwner1StorageId());
        return getUser(UserRole.ADMIN, user.getId());
    }

    private static List<String> locationActorRoles(List<String> additionalRoles) {
        return BusinessRoleCatalog.withDefaultLocationRole(additionalRoles);
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

    @Step("API: оновити користувача {userId}")
    public UserModelResponse updateUser(UserRole role, String userId, UserRequest body) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_PUT_UPDATE, role, body, userId);
        validateSuccess(response, "Update user");
        return response.as(UserModelResponse.class);
    }

    @Step("API: деактивувати користувача {userId}")
    public void deactivateUser(UserRole role, String userId) {
        if (role == UserRole.ADMIN) accessFixture.revokeAll(userId);
        updateUser(role, userId, UserDataFactory.deactivated(getUser(role, userId)));
        untrackForCleanup(userId);
    }

    public void trackForCleanup(String userId) {
        if (userId != null && !trackedUserIds.contains(userId)) trackedUserIds.add(userId);
    }

    public void untrackForCleanup(String userId) { trackedUserIds.remove(userId); }

    @Step("API: cleanup tracked test users")
    public void deactivateTrackedUsers() {
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            log.warn("Staging mode — skipping user cleanup (-Dstaging.cleanup=false)");
            trackedUserIds.clear();
            return;
        }
        for (String userId : new ArrayList<>(trackedUserIds)) {
            try { deactivateUser(UserRole.ADMIN, userId); }
            catch (Exception e) { log.warn("Failed to deactivate user {}: {}", userId, e.getMessage()); }
        }
        trackedUserIds.clear();
    }

    public void trackUserByUsername(String username) { trackForCleanup(findUserIdByUsername(username)); }

    public String findUserIdByUsername(String username) {
        return findUserByUsername(username).map(UserModelResponse::getId)
                .orElseThrow(() -> new IllegalStateException("User not found after create: " + username));
    }

    private UserModelResponse createUserProfile(String username,
                                                String firstName,
                                                String lastName,
                                                PlaywrightSessionProvider playwright,
                                                String permanentPassword) {
        UserRequest request = UserRequest.builder().username(username).firstName(firstName).lastName(lastName)
                .rank("").enabled(true).build();
        Response response = apiExecutor.execute(ApiEndpointDefinition.USER_POST_CREATE, UserRole.ADMIN, request);
        validateSuccess(response, "Create user " + username);
        OneTimeUserCredentialsResponse credentials = response.as(OneTimeUserCredentialsResponse.class);
        if (playwright == null) {
            throw new IllegalStateException("User " + username + " created but password cannot be bootstrapped");
        }
        playwright.bootstrapPermanentPassword(username, credentials.getPassword(), permanentPassword);
        String userId = credentials.getUserId() != null ? credentials.getUserId() : findUserIdByUsername(username);
        return waitForUser(UserRole.ADMIN, userId);
    }

    private UserModelResponse waitForUser(UserRole role, String userId) {
        return PollUtils.waitUntil(() -> {
            Response response = apiExecutor.execute(ApiEndpointDefinition.USER_GET_BY_ID, role, userId);
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? response.as(UserModelResponse.class) : null;
        }, Objects::nonNull, 10_000, "Get user by id " + userId);
    }
}
