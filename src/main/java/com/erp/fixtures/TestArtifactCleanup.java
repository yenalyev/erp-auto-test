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
     * На staging cleanup увімкнений за замовчуванням (як на dev).
     * Opt-out: {@code -Dstaging.cleanup=false} — лише очищує черги без виклику API
     * (залишити артефакти для дебагу).
     */
    public static void cleanupRegionsAndStorages(
            StorageRegionFixture regionFixture,
            StorageFixture storageFixture) {
        if (regionFixture == null || storageFixture == null) {
            return;
        }
        if (shouldSkipApiCleanup()) {
            log.warn("Staging mode — skipping automatic storage/region cleanup (-Dstaging.cleanup=false)");
            regionFixture.clearTrackedRegions();
            storageFixture.clearTrackedStorages();
            return;
        }
        regionFixture.deleteTrackedRegions(UserRole.ADMIN);
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }

    /** {@code true} when env=staging and {@code -Dstaging.cleanup=false}. */
    public static boolean shouldSkipApiCleanup() {
        return isStagingEnv() && !isStagingCleanupEnabled();
    }

    public static boolean isStagingEnv() {
        return "staging".equals(System.getProperty("env", "debug"));
    }

    /**
     * Staging API cleanup flag. Default {@code true}; opt-out with {@code -Dstaging.cleanup=false}.
     */
    public static boolean isStagingCleanupEnabled() {
        return Boolean.parseBoolean(System.getProperty("staging.cleanup", "true"));
    }
}
