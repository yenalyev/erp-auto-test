package com.erp.fixtures;

import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
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

    private static final Set<String> WORKSPACE_TYPES = Set.of(
            UnitType.UNIT.name(),
            UnitType.STORAGE.name(),
            UnitType.PRODUCTION.name());

    /**
     * Resolves B2 (second RO location): config {@code location-mixed.ro2.storage.id} when &gt; 0,
     * otherwise first admin UNIT/STORAGE/PRODUCTION not equal to A1/A2/B1.
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
                List.of(UnitType.UNIT, UnitType.STORAGE, UnitType.PRODUCTION),
                null, null);
        return names.stream()
                .filter(s -> s.getId() != null && !reserved.contains(s.getId()))
                .filter(s -> s.getType() == null || isWorkspaceType(s.getType()))
                .filter(s -> !isTestArtifactName(s.getName()))
                .map(StorageResponse::getId)
                .findFirst()
                .or(() -> names.stream()
                        .filter(s -> s.getId() != null && !reserved.contains(s.getId()))
                        .filter(s -> s.getType() == null || isWorkspaceType(s.getType()))
                        .map(StorageResponse::getId)
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot resolve location-mixed RO2 storage id — set location-mixed.ro2.storage.id "
                                + "to a UNIT/STORAGE/PRODUCTION visible in the workspace picker"));
    }

    private static boolean isWorkspaceVisible(StorageFixture storageFixture, long storageId) {
        StorageResponse storage = storageFixture.getById(UserRole.ADMIN, storageId);
        return storage != null && isWorkspaceType(storage.getType());
    }

    private static boolean isWorkspaceType(String type) {
        return type != null && WORKSPACE_TYPES.contains(type);
    }

    private static boolean isTestArtifactName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith("ui-");
    }
}

