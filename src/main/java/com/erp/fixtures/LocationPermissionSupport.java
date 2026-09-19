package com.erp.fixtures;

import com.erp.enums.StorageKind;
import com.erp.enums.UserRole;
import com.erp.enums.StorageRelation;
import com.erp.models.response.StorageResponse;
import com.erp.utils.config.ConfigProvider;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helpers for CPMA-644 LOCATION_MIXED (full + view-only) storage id resolution.
 */
@UtilityClass
public class LocationPermissionSupport {

    /**
     * Resolves B2 (second RO location): config {@code location-mixed.ro2.storage.id} when &gt; 0,
     * otherwise first admin LOCATION not equal to A1/A2/B1.
     * Skips CREW/FLY_POINT (they are not in the sidebar workspace tree) and {@code ui-*} test artifacts.
     */
    public static long resolveRo2StorageId(StorageFixture storageFixture) {
        long configured = ConfigProvider.getLocationMixedRo2StorageId();
        long a1 = ConfigProvider.getOwner1StorageId();
        long a2 = ConfigProvider.getUnitStorageId();
        long b1 = ConfigProvider.getOwner2StorageId();
        Set<Long> reserved = new HashSet<>(List.of(a1, a2, b1));

        if (configured > 0 && !reserved.contains(configured) && isWorkspaceVisible(storageFixture, configured)) {
            return configured;
        }

        List<StorageResponse> names = storageFixture.getNames(
                UserRole.ADMIN, true, null,
                null,
                null, null);
        return names.stream()
                .filter(s -> s.getId() != null && !reserved.contains(s.getId()))
                .filter(LocationPermissionSupport::isWorkspaceLocation)
                .filter(LocationPermissionSupport::isInternal)
                .filter(s -> !isTestArtifactName(s.getName()))
                .map(StorageResponse::getId)
                .findFirst()
                .or(() -> names.stream()
                        .filter(s -> s.getId() != null && !reserved.contains(s.getId()))
                        .filter(LocationPermissionSupport::isWorkspaceLocation)
                        .filter(LocationPermissionSupport::isInternal)
                        .map(StorageResponse::getId)
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot resolve location-mixed RO2 storage id — set location-mixed.ro2.storage.id "
                                + "to a LOCATION visible in the workspace picker"));
    }

    private static boolean isWorkspaceVisible(StorageFixture storageFixture, long storageId) {
        StorageResponse storage = storageFixture.getById(UserRole.ADMIN, storageId);
        return isWorkspaceLocation(storage) && isInternal(storage);
    }

    private static boolean isWorkspaceLocation(StorageResponse storage) {
        return storage != null && storage.getKind() == StorageKind.LOCATION;
    }

    private static boolean isTestArtifactName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith("ui-");
    }

    private static boolean isInternal(StorageResponse storage) {
        return storage.getRelation() != null
                && StorageRelation.INTERNAL.name().equalsIgnoreCase(storage.getRelation());
    }
}

