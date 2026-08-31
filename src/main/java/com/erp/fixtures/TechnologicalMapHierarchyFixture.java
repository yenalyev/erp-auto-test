package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.auth.PlaywrightSessionProvider;
import io.qameta.allure.Step;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Isolated REGIONS owner bound to a STORAGE parent P (not a UNIT) + a sibling PRODUCTION
 * parent, each with A (granted), B (not granted — still listed under P) → leaf C (granted),
 * and sibling X (granted, outside both trees).
 * <p>Newly created locations are not in visibility regions — do not purge or scan regions for them.
 */
@Slf4j
public class TechnologicalMapHierarchyFixture {

    private final StorageFixture storageFixture;
    private final StorageRegionFixture regionFixture;
    private final TechnologicalMapFixture techMapFixture;
    private final IsolatedRestrictedOwnerScope isolatedScope;

    private final List<CreatedMap> createdMaps = new ArrayList<>();
    private Seed seed;

    public TechnologicalMapHierarchyFixture(
            TestContext testContext,
            ApiExecutor apiExecutor,
            StorageFixture storageFixture,
            StorageRegionFixture regionFixture,
            PlaywrightSessionProvider playwright) {
        this.storageFixture = storageFixture;
        this.regionFixture = regionFixture;
        this.techMapFixture = new TechnologicalMapFixture(testContext, apiExecutor);
        InventoryFixture inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        UserFixture userFixture = new UserFixture(testContext, apiExecutor);
        this.isolatedScope = new IsolatedRestrictedOwnerScope(
                storageFixture, userFixture, inventoryFixture, apiExecutor, playwright);
    }

    public TechnologicalMapFixture techMaps() {
        return techMapFixture;
    }

    public IsolatedRestrictedOwnerScope isolatedScope() {
        return isolatedScope;
    }

    public Seed seed() {
        if (seed == null) {
            throw new IllegalStateException("Hierarchy not seeded — call acquireAndSeed() first");
        }
        return seed;
    }

    @Step("FIXTURE: isolated REGIONS STORAGE home + PRODUCTION sibling + A/B/C/X tech maps")
    public Seed acquireAndSeed() {
        techMapFixture.prepareContext();
        Long homeId = isolatedScope.acquireLocation(UnitType.STORAGE);
        StorageResponse storageHome = storageFixture.getById(UserRole.ADMIN, homeId);
        Long standParentId = storageFixture.resolveParentUnit().getId();

        Branch storageParent = seedChildren(storageHome, "s", homeId);
        Branch productionParent = seedBranch(standParentId, UnitType.PRODUCTION, "p", homeId);

        StorageResponse storageX = storageFixture.createChildStorage(standParentId, "tm-hier-x-");
        regionFixture.addExplicitLocations(storageX.getId(), homeId);
        TechnologicalMapResponse mapX = createMap(storageX.getId(), "tm-hier-X");

        seed = Seed.builder()
                .storageParent(storageParent)
                .productionParent(productionParent)
                .storageX(storageX)
                .mapX(mapX)
                .owner(isolatedScope.boundOwner(UserRole.OWNER_2))
                .build();
        log.info("Tech-map hierarchy seeded: P-STORAGE={} P-PRODUCTION={} X={}",
                storageParent.getParent().getId(),
                productionParent.getParent().getId(),
                storageX.getId());
        return seed;
    }

    @Step("FIXTURE: deactivate hierarchy tech maps")
    public void deactivateCreatedMaps() {
        for (CreatedMap created : List.copyOf(createdMaps)) {
            try {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, created.techMapId(), created.storageId());
            } catch (Exception e) {
                log.warn("Failed to deactivate tech map {} on storage {}: {}",
                        created.techMapId(), created.storageId(), e.getMessage());
            }
        }
        createdMaps.clear();
    }

    @Step("FIXTURE: release isolated owner + home location")
    public void release() {
        deactivateCreatedMaps();
        isolatedScope.release();
        seed = null;
    }

    private Branch seedBranch(Long parentOfP, UnitType parentType, String tag, Long viewerId) {
        StorageResponse parent = storageFixture.createChildStorage(
                parentOfP, "tm-hier-" + tag + "-p-", parentType, StorageRelation.INTERNAL);
        regionFixture.addExplicitLocations(parent.getId(), viewerId);
        return seedChildren(parent, tag, viewerId);
    }

    private Branch seedChildren(StorageResponse parent, String tag, Long viewerId) {
        StorageResponse storageA = storageFixture.createChildStorage(parent.getId(), "tm-hier-" + tag + "-a-");
        StorageResponse storageB = storageFixture.createChildStorage(parent.getId(), "tm-hier-" + tag + "-b-");
        StorageResponse storageC = storageFixture.createChildStorage(storageB.getId(), "tm-hier-" + tag + "-c-");

        regionFixture.addExplicitLocations(storageA.getId(), viewerId);
        regionFixture.addExplicitLocations(storageC.getId(), viewerId);

        String mapTag = tag.toUpperCase();
        return Branch.builder()
                .parent(parent)
                .storageA(storageA)
                .storageB(storageB)
                .storageC(storageC)
                .mapParent(createMap(parent.getId(), "tm-hier-" + mapTag + "-P"))
                .mapA(createMap(storageA.getId(), "tm-hier-" + mapTag + "-A"))
                .mapB(createMap(storageB.getId(), "tm-hier-" + mapTag + "-B"))
                .mapC(createMap(storageC.getId(), "tm-hier-" + mapTag + "-C"))
                .build();
    }

    private TechnologicalMapResponse createMap(Long storageId, String namePrefix) {
        TechnologicalMapResponse map = techMapFixture
                .createIsolatedProductionTechMap(UserRole.ADMIN, storageId, namePrefix + "-" + System.nanoTime())
                .getTechMap();
        createdMaps.add(new CreatedMap(map.getId(), storageId));
        return map;
    }

    private record CreatedMap(Long techMapId, Long storageId) {
    }

    @Value
    @Builder
    public static class Branch {
        StorageResponse parent;
        StorageResponse storageA;
        StorageResponse storageB;
        StorageResponse storageC;
        TechnologicalMapResponse mapParent;
        TechnologicalMapResponse mapA;
        TechnologicalMapResponse mapB;
        TechnologicalMapResponse mapC;
    }

    @Value
    @Builder
    public static class Seed {
        Branch storageParent;
        Branch productionParent;
        StorageResponse storageX;
        TechnologicalMapResponse mapX;
        UserFixture.RestrictedOwnerUser owner;
    }
}
