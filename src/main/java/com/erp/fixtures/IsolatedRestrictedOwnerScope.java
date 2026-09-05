package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.utils.auth.PlaywrightSessionProvider;
import com.erp.utils.helpers.PollUtils;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class-scoped ephemeral UNITs plus new Keycloak owners (ADMIN {@code POST /users}).
 * Tests keep calling {@link UserRole#OWNER_2} / {@link UserRole#OWNER_1} — this fixture swaps
 * those roles' sessions, so shared {@code bar}/{@code alkatras} are not mutated.
 * <p>{@link #release()} from {@code @AfterClass(alwaysRun=true)}: evict sessions, deactivate
 * users, zero leftover stock via inventory, then archive UNITs. UNITs are untracked so
 * {@code @AfterMethod} does not archive them mid-class.
 */
@Slf4j
public class IsolatedRestrictedOwnerScope {

    private static final Object LOCK = new Object();
    private static final String REGIONS_NAME_PREFIX = "vis-iso-";
    private static final String FULL_ACCESS_NAME_PREFIX = "vis-fa-";
    private static final long LOGIN_TIMEOUT_MS = 20_000;

    private final StorageFixture storageFixture;
    private final UserFixture userFixture;
    private final ApiExecutor apiExecutor;
    private final PlaywrightSessionProvider playwright;

    private final List<Long> isolatedStorageIds = new ArrayList<>();
    private final List<UserRole> boundRoles = new ArrayList<>();
    private final Map<UserRole, UserFixture.RestrictedOwnerUser> boundOwners = new EnumMap<>(UserRole.class);

    public IsolatedRestrictedOwnerScope(
            StorageFixture storageFixture,
            UserFixture userFixture,
            ApiExecutor apiExecutor,
            PlaywrightSessionProvider playwright) {
        this.storageFixture = storageFixture;
        this.userFixture = userFixture;
        this.apiExecutor = apiExecutor;
        this.playwright = playwright;
    }

    @Step("FIXTURE: isolated REGIONS UNIT + Keycloak restricted owner")
    public Long acquire() {
        synchronized (LOCK) {
            return acquireOwner(
                    UserRole.OWNER_2,
                    storageFixture.createStorage(
                            StorageDataFactory.restrictedUnitStorage(
                                    storageFixture.resolveParentUnit().getId(),
                                    REGIONS_NAME_PREFIX).build()));
        }
    }

    @Step("FIXTURE: isolated REGIONS STORAGE/PRODUCTION + Keycloak restricted owner")
    public Long acquireLocation(UnitType type) {
        if (type != UnitType.STORAGE && type != UnitType.PRODUCTION) {
            throw new IllegalArgumentException(
                    "Isolated owner home must be STORAGE or PRODUCTION, got " + type);
        }
        synchronized (LOCK) {
            return acquireOwner(
                    UserRole.OWNER_2,
                    storageFixture.createStorage(
                            StorageDataFactory.childStorage(
                                            storageFixture.resolveParentUnit().getId(),
                                            REGIONS_NAME_PREFIX,
                                            type,
                                            StorageRelation.INTERNAL)
                                    .accessMode(StorageAccessMode.REGIONS)
                                    .build()));
        }
    }

    @Step("FIXTURE: isolated FULL_ACCESS UNIT + Keycloak owner")
    public Long acquireFullAccessOwner() {
        synchronized (LOCK) {
            return acquireOwner(
                    UserRole.OWNER_1,
                    storageFixture.createStorage(
                            StorageDataFactory.unitStorage(
                                    storageFixture.resolveParentUnit().getId(),
                                    FULL_ACCESS_NAME_PREFIX).build()));
        }
    }

    @Step("FIXTURE: deactivate isolated owners + UNITs; restore role sessions")
    public void release() {
        synchronized (LOCK) {
            for (UserRole role : boundRoles) {
                try {
                    apiExecutor.evictSessionForRole(role);
                } catch (Exception e) {
                    log.warn("Failed to evict isolated {} session: {}", role, e.getMessage());
                }
            }
            boundRoles.clear();
            boundOwners.clear();
            try {
                userFixture.deactivateTrackedUsers();
            } catch (Exception e) {
                log.warn("Failed to deactivate isolated owners: {}", e.getMessage());
            }
            List<Long> ids = List.copyOf(isolatedStorageIds);
            isolatedStorageIds.clear();
            for (Long id : ids) {
                archiveIsolatedUnit(id);
            }
        }
    }

    /**
     * Credentials of the ephemeral Keycloak owner bound to {@code role} by {@link #acquire()}
     * / {@link #acquireFullAccessOwner()}. For UI cookie inject — {@link UserRole#getUsername()}
     * still points at the shared stand user.
     */
    public UserFixture.RestrictedOwnerUser boundOwner(UserRole role) {
        UserFixture.RestrictedOwnerUser owner = boundOwners.get(role);
        if (owner == null) {
            throw new IllegalStateException("No isolated owner bound to " + role + " — call acquire() first");
        }
        return owner;
    }

    private Long acquireOwner(UserRole role, StorageResponse unit) {
        storageFixture.untrackForCleanup(unit.getId());
        isolatedStorageIds.add(unit.getId());

        UserFixture.RestrictedOwnerUser owner = userFixture.createRestrictedOwner(playwright, unit);
        apiExecutor.setSessionForRole(role, owner.username(), owner.password());
        boundRoles.add(role);
        boundOwners.put(role, owner);
        waitUntilAllowedStorageIds(role, Set.of(unit.getId()));
        log.info("Isolated owner {} role={} bound to {} id={} name={} accessMode={}",
                owner.username(), role, unit.getType(), unit.getId(), unit.getName(), unit.getAccessMode());
        return unit.getId();
    }

    private void archiveIsolatedUnit(Long id) {
        if (!storageFixture.archiveStorage(UserRole.ADMIN, id)) {
            log.warn("Failed to archive isolated UNIT id={}", id);
        }
    }

    private void waitUntilAllowedStorageIds(UserRole role, Set<Long> expected) {
        PollUtils.waitUntil(
                () -> {
                    UserMeResponse me = userFixture.getMe(role);
                    Set<Long> allowed = me.getAllowedStorageIds() == null
                            ? Set.of()
                            : new HashSet<>(me.getAllowedStorageIds());
                    log.info("Isolated {} allowedStorageIds={} (expected {})", role, allowed, expected);
                    return allowed.equals(expected) ? me : null;
                },
                Objects::nonNull,
                LOGIN_TIMEOUT_MS,
                "isolated " + role + " allowedStorageIds=" + expected);
    }
}
