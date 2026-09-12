package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.data.LocationProfileCatalog;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.TestContext;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/** Creates isolated locations from {@code location-profiles.yml}. */
@Slf4j
public class LocationProfileFixture extends BaseFixture {

    private final StorageFixture storageFixture;
    private final List<Long> createdLocationIds = new ArrayList<>();

    public LocationProfileFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.storageFixture = new StorageFixture(testContext, apiExecutor);
    }

    @Step("FIXTURE: створити {count} локацій за профілем {profile}")
    public LocationSet create(LocationProfile profile, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Location count must be positive, got " + count);
        }
        LocationProfileCatalog.Definition definition = LocationProfileCatalog.definition(profile);
        StorageResponse parent = definition.parentProfile() == null
                ? resolveParent(definition.parentPool())
                : create(definition.parentProfile(), 1).locations().getFirst();
        List<StorageResponse> locations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            StorageResponse location = storageFixture.createStorage(buildRequest(definition, parent.getId()));
            createdLocationIds.add(location.getId());
            locations.add(location);
        }
        return new LocationSet(profile, parent, List.copyOf(locations));
    }

    public static StorageRequest buildRequest(
            LocationProfileCatalog.Definition definition,
            long parentId) {
        return StorageRequest.builder()
                .name(StorageDataFactory.uniqueName(definition.namePrefix()))
                .parentId(parentId)
                .type(definition.unitType())
                .relation(definition.relation())
                .accessMode(definition.accessMode())
                // milUnitType is deliberately omitted for BATTALION_UNIT.
                .build();
    }

    @Step("FIXTURE: cleanup локацій, створених із location profile")
    public void cleanup() {
        for (int index = createdLocationIds.size() - 1; index >= 0; index--) {
            Long locationId = createdLocationIds.get(index);
            if (storageFixture.archiveStorage(UserRole.ADMIN, locationId)) {
                storageFixture.untrackForCleanup(locationId);
                createdLocationIds.remove(index);
            }
        }
    }

    private StorageResponse resolveParent(String poolName) {
        List<String> failures = new ArrayList<>();
        for (long candidate : LocationProfileCatalog.parentPool(poolName).candidates()) {
            try {
                StorageResponse resolved = byConfiguredId(candidate);
                if (resolved != null) {
                    return resolved;
                }
                failures.add(candidate + " did not resolve");
            } catch (RuntimeException e) {
                failures.add(candidate + ": " + e.getMessage());
            }
        }
        throw new IllegalStateException("No parent resolved from pool " + poolName + ": " + failures);
    }

    private StorageResponse byConfiguredId(long id) {
        return id > 0 ? storageFixture.getById(UserRole.ADMIN, id) : null;
    }

    public record LocationSet(
            LocationProfile profile,
            StorageResponse parent,
            List<StorageResponse> locations) {
    }
}
