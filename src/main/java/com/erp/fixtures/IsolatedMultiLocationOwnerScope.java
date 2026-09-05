package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserMeResponse;
import com.erp.utils.auth.PlaywrightSessionProvider;
import com.erp.utils.helpers.PollUtils;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ephemeral pair of UNIT storages plus a Keycloak owner with full access to both.
 * Binds credentials to a {@link UserRole} for {@link ApiExecutor} / UI cookie inject.
 */
@Slf4j
public class IsolatedMultiLocationOwnerScope {

    private static final Object LOCK = new Object();
    private static final String UNIT_NAME_PREFIX = "mloc-u-";
    private static final long LOGIN_TIMEOUT_MS = 20_000;

    private final StorageFixture storageFixture;
    private final UserFixture userFixture;
    private final ApiExecutor apiExecutor;
    private final PlaywrightSessionProvider playwright;

    private final List<Long> isolatedStorageIds = new ArrayList<>();
    private UserRole boundRole;
    private UserFixture.RestrictedOwnerUser boundOwner;
    private long storageAId;
    private long storageBId;

    public IsolatedMultiLocationOwnerScope(
            StorageFixture storageFixture,
            UserFixture userFixture,
            ApiExecutor apiExecutor,
            PlaywrightSessionProvider playwright) {
        this.storageFixture = storageFixture;
        this.userFixture = userFixture;
        this.apiExecutor = apiExecutor;
        this.playwright = playwright;
    }

    public record Context(
            UserFixture.RestrictedOwnerUser owner,
            long storageAId,
            long storageBId,
            UserRole role) {
    }

    @Step("FIXTURE: isolated multi-location owner + 2 UNIT storages")
    public Context acquire(UserRole role) {
        synchronized (LOCK) {
            if (boundRole != null) {
                throw new IllegalStateException("Scope already acquired for " + boundRole);
            }
            StorageResponse storageA = storageFixture.createStorage(
                    StorageDataFactory.unitStorage(
                            storageFixture.resolveParentUnit().getId(),
                            UNIT_NAME_PREFIX + "a-").build());
            StorageResponse storageB = storageFixture.createStorage(
                    StorageDataFactory.unitStorage(
                            storageFixture.resolveParentUnit().getId(),
                            UNIT_NAME_PREFIX + "b-").build());
            storageFixture.untrackForCleanup(storageA.getId());
            storageFixture.untrackForCleanup(storageB.getId());
            isolatedStorageIds.add(storageA.getId());
            isolatedStorageIds.add(storageB.getId());

            UserFixture.RestrictedOwnerUser owner =
                    userFixture.createMultiLocationOwner(playwright, List.of(storageA, storageB));
            apiExecutor.setSessionForRole(role, owner.username(), owner.password());
            boundRole = role;
            boundOwner = owner;
            storageAId = storageA.getId();
            storageBId = storageB.getId();
            waitUntilAllowedStorageIds(role, Set.of(storageAId, storageBId));
            log.info("Multi-location owner {} role={} storages=[{}, {}]",
                    owner.username(), role, storageAId, storageBId);
            return new Context(owner, storageAId, storageBId, role);
        }
    }

    public UserFixture.RestrictedOwnerUser boundOwner() {
        if (boundOwner == null) {
            throw new IllegalStateException("Call acquire() first");
        }
        return boundOwner;
    }

    @Step("FIXTURE: release multi-location owner + archive UNITs")
    public void release() {
        synchronized (LOCK) {
            if (boundRole != null) {
                try {
                    apiExecutor.evictSessionForRole(boundRole);
                } catch (Exception e) {
                    log.warn("Failed to evict {} session: {}", boundRole, e.getMessage());
                }
            }
            boundRole = null;
            boundOwner = null;
            storageAId = 0;
            storageBId = 0;
            try {
                userFixture.deactivateTrackedUsers();
            } catch (Exception e) {
                log.warn("Failed to deactivate multi-location owner: {}", e.getMessage());
            }
            List<Long> ids = List.copyOf(isolatedStorageIds);
            isolatedStorageIds.clear();
            for (Long id : ids) {
                archiveIsolatedUnit(id);
            }
        }
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
                    log.info("Multi-location {} allowedStorageIds={} (expected {})", role, allowed, expected);
                    return allowed.containsAll(expected) && allowed.size() >= expected.size() ? me : null;
                },
                Objects::nonNull,
                LOGIN_TIMEOUT_MS,
                "multi-location " + role + " allowedStorageIds superset of " + expected);
    }
}
