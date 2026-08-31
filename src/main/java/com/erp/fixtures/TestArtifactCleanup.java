package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.UserRole;
import com.erp.test_context.GlobalTestContext;
import com.erp.test_context.TestContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Cleanup тестових локацій і областей видимості.
 * <p>Два шари:
 * <ol>
 *   <li><b>Tracked</b> — id з {@code create*} поточного класу ({@link #cleanupRegionsAndStorages}).</li>
 *   <li><b>Orphan sweep</b> — сиріти з попередніх прогонів за маркером імен
 *       {@link StorageDataFactory#isAutotestUniqueName(String)} ({@link #sweepOrphanAutotestArtifacts}).</li>
 * </ol>
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

    /**
     * Suite-level sweep of autotest regions (and optionally storages).
     * Before tests: regions only — mass storage deactivate starves ADMIN session / first @BeforeClass.
     * After tests: regions + best-effort storage deactivate.
     * Opt-out: {@code -Dsuite.artifact.sweep=false}.
     */
    public static void sweepOrphanAutotestArtifacts(
            StorageRegionFixture regionFixture,
            StorageFixture storageFixture,
            boolean includeStorages) {
        if (regionFixture == null || storageFixture == null) {
            return;
        }
        if (shouldSkipApiCleanup()) {
            log.info("Skipping suite artifact sweep — API cleanup disabled");
            return;
        }
        if (!shouldSweepOrphans()) {
            log.info("Skipping suite artifact sweep (-Dsuite.artifact.sweep=false)");
            return;
        }
        int regions = regionFixture.purgeAutotestNamedRegions(UserRole.ADMIN);
        int storages = 0;
        if (includeStorages) {
            storages = storageFixture.deactivateAutotestStorages(UserRole.ADMIN);
        }
        log.info("Suite artifact sweep: deleted {} regions, deactivated {} storages (storages={})",
                regions, storages, includeStorages);
    }

    /**
     * Sweep using a throwaway context — for {@code @BeforeSuite}/{@code @AfterSuite}
     * before class fixtures exist.
     */
    public static void sweepOrphanAutotestArtifacts(ApiExecutor apiExecutor, boolean includeStorages) {
        if (apiExecutor == null) {
            log.warn("Skipping suite artifact sweep — ApiExecutor is not ready");
            return;
        }
        TestContext ctx = new GlobalTestContext();
        sweepOrphanAutotestArtifacts(
                new StorageRegionFixture(ctx, apiExecutor),
                new StorageFixture(ctx, apiExecutor),
                includeStorages);
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

    /**
     * Suite orphan sweep flag. Default {@code true}; opt-out with {@code -Dsuite.artifact.sweep=false}.
     */
    public static boolean shouldSweepOrphans() {
        return Boolean.parseBoolean(System.getProperty("suite.artifact.sweep", "true"));
    }
}
