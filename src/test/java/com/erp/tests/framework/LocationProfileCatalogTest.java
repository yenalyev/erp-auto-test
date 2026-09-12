package com.erp.tests.framework;

import com.erp.data.LocationProfileCatalog;
import com.erp.enums.LocationProfile;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.models.request.StorageRequest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocationProfileCatalogTest {

    @Test
    public void battalionUnitProfileDefinesLocationWithoutMilitaryType() {
        LocationProfileCatalog.Definition definition =
                LocationProfileCatalog.definition(LocationProfile.BATTALION_UNIT);

        assertThat(definition.unitType()).isEqualTo(UnitType.UNIT);
        assertThat(definition.relation()).isEqualTo(StorageRelation.INTERNAL);
        assertThat(definition.accessMode()).isEqualTo(StorageAccessMode.FULL_ACCESS);
        assertThat(definition.parentPool()).isEqualTo("BATTALION_PARENT_UNITS");
        assertThat(definition.parentProfile()).isNull();

        LocationProfileCatalog.Definition parentDefinition =
                LocationProfileCatalog.definition(LocationProfile.BATTALION_WARENHAUSE_UNIT);
        assertThat(parentDefinition.parentProfile()).isEqualTo(LocationProfile.BATTALION_UNIT);
        assertThat(parentDefinition.parentPool()).isNull();

        StorageRequest request = LocationProfileFixture.buildRequest(definition, 77L);
        assertThat(request.getParentId()).isEqualTo(77L);
        assertThat(request.getType()).isEqualTo(UnitType.UNIT);
        assertThat(request.getRelation()).isEqualTo(StorageRelation.INTERNAL);
        assertThat(request.getMilUnitType()).isNull();
    }

    @Test
    public void battalionParentPoolUsesConfiguredLocationIds() {
        assertThat(LocationProfileCatalog.parentPool("BATTALION_PARENT_UNITS").candidates())
                .containsExactly(156L);
    }

    @Test
    public void tsukProfilesUseSharedParentPool() {
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_WARENHAUSE).unitType())
                .isEqualTo(UnitType.STORAGE);
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_PRODUCTION).unitType())
                .isEqualTo(UnitType.PRODUCTION);
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_WARENHAUSE).parentPool())
                .isEqualTo("TSUK_PARENT_UNITS");
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_PRODUCTION).parentPool())
                .isEqualTo("TSUK_PARENT_UNITS");
        assertThat(LocationProfileCatalog.parentPool("TSUK_PARENT_UNITS").candidates())
                .containsExactly(2151L);
    }
}
