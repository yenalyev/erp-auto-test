package com.erp.fixtures;

import com.erp.enums.UserRole;
import com.erp.models.response.StorageResponse;
import com.erp.utils.config.ConfigProvider;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helpers for CPMA-644 LOCATION_MIXED (full + view-only) storage id resolution.
 */
@UtilityClass
public class LocationPermissionSupport {

    /**
     * Resolves B2 (second RO location): config {@code location-mixed.ro2.storage.id} when &gt; 0,
     * otherwise first admin UNIT/storage name id not equal to A1/A2/B1.
     */
    public static long resolveRo2StorageId(StorageFixture storageFixture) {
        long configured = ConfigProvider.getLocationMixedRo2StorageId();
        long a1 = ConfigProvider.getOwner1StorageId();
        long a2 = ConfigProvider.getUnitStorageId();
        long b1 = ConfigProvider.getOwner2StorageId();
        Set<Long> reserved = new HashSet<>(List.of(a1, a2, b1));

        if (configured > 0 && !reserved.contains(configured)) {
            return configured;
        }

        List<StorageResponse> names = storageFixture.getNames(UserRole.ADMIN, true, null);
        return names.stream()
                .map(StorageResponse::getId)
                .filter(id -> id != null && !reserved.contains(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot resolve location-mixed RO2 storage id — set location-mixed.ro2.storage.id "
                                + "or ensure admin can list at least 4 storages"));
    }
}
