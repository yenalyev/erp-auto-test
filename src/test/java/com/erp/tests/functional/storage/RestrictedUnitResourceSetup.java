package com.erp.tests.functional.storage;

import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;

/**
 * Setup helper: RESTRICTED storage ({@code accessMode=REGIONS}) + RESOURCES region + member link.
 */
public final class RestrictedUnitResourceSetup {

    public record Setup(StorageResponse unit, StorageRegionResponse region) {
    }

    private RestrictedUnitResourceSetup() {
    }

    /** Default {@link UnitType#UNIT} — канонічний сценарій словника ресурсів підрозділу. */
    public static Setup createUnit(
            StorageFixture storageFixture,
            StorageRegionFixture regionFixture,
            String namePrefix) {
        return create(storageFixture, regionFixture, UnitType.UNIT, namePrefix);
    }

    public static Setup create(
            StorageFixture storageFixture,
            StorageRegionFixture regionFixture,
            UnitType type,
            String namePrefix) {
        StorageResponse parent = storageFixture.resolveParentUnit();
        StorageRequest request = buildRestrictedRequest(parent.getId(), type, namePrefix);
        StorageResponse unit = storageFixture.createStorage(request);

        StorageRegionResponse region = regionFixture.createRegion(
                unit, StorageAccessMode.RESOURCES, namePrefix + "reg-");
        regionFixture.addRegionMembers(region.getId(), unit.getId());
        return new Setup(unit, region);
    }

    private static StorageRequest buildRestrictedRequest(Long parentId, UnitType type, String namePrefix) {
        if (type == UnitType.UNIT) {
            return StorageDataFactory.restrictedUnitStorage(parentId, namePrefix + "unit-").build();
        }
        if (type == UnitType.STORAGE) {
            return StorageDataFactory.restrictedStorage(parentId, namePrefix + "unit-").build();
        }
        return StorageDataFactory.childStorage(parentId, namePrefix + "unit-", type, StorageRelation.INTERNAL)
                .accessMode(StorageAccessMode.REGIONS)
                .build();
    }
}
