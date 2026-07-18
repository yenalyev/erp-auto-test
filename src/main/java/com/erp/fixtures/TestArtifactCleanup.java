package com.erp.fixtures;

import com.erp.enums.UserRole;
import lombok.extern.slf4j.Slf4j;

/**
 * Спільний cleanup тестових локацій і областей видимості після API/UI тестів.
 */
@Slf4j
public final class TestArtifactCleanup {

    private TestArtifactCleanup() {
    }

    /**
     * Області — DELETE; локації (UNIT/CREW/STORAGE тощо) — deactivate (архівація).
     * На staging за замовчуванням лише очищує черги без виклику API.
     * Opt-in: {@code -Dstaging.cleanup=true} виконує той самий API cleanup, що на dev.
     */
    public static void cleanupRegionsAndStorages(
            StorageRegionFixture regionFixture,
            StorageFixture storageFixture) {
        if (regionFixture == null || storageFixture == null) {
            return;
        }
        if (isStagingEnv() && !isStagingCleanupEnabled()) {
            log.warn("Staging mode — skipping automatic storage/region cleanup");
            regionFixture.clearTrackedRegions();
            storageFixture.clearTrackedStorages();
            return;
        }
        regionFixture.deleteTrackedRegions(UserRole.ADMIN);
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }

    static boolean isStagingEnv() {
        return "staging".equals(System.getProperty("env", "debug"));
    }

    public static boolean isStagingCleanupEnabled() {
        return Boolean.parseBoolean(System.getProperty("staging.cleanup", "false"));
    }
}
