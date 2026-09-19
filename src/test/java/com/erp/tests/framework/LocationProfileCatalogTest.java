package com.erp.tests.framework;

import com.erp.data.LocationProfileCatalog;
import com.erp.enums.LocationFeature;
import com.erp.enums.LocationProfile;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageKind;
import com.erp.enums.StorageRelation;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.models.request.StorageRequest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocationProfileCatalogTest {

    @Test
    public void battalionUnitProfileDefinesLocationWithoutMilitaryType() {
        LocationProfileCatalog.Definition definition =
                LocationProfileCatalog.definition(LocationProfile.BATTALION_UNIT);

        assertThat(definition.kind()).isEqualTo(StorageKind.LOCATION);
        assertThat(definition.features()).containsExactlyInAnyOrder(
                LocationFeature.RELOCATIONS, LocationFeature.ORDERS);
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
        assertThat(request.getKind()).isEqualTo(StorageKind.LOCATION);
        assertThat(request.getFeatures()).containsExactlyInAnyOrder(
                LocationFeature.RELOCATIONS, LocationFeature.ORDERS);
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
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_WARENHAUSE).kind())
                .isEqualTo(StorageKind.LOCATION);
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_WARENHAUSE).features())
                .containsExactlyInAnyOrder(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT);
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_PRODUCTION).kind())
                .isEqualTo(StorageKind.LOCATION);
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_PRODUCTION).features())
                .containsExactlyInAnyOrder(
                        LocationFeature.RELOCATIONS, LocationFeature.PRODUCE, LocationFeature.EQUIPMENT);
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_WARENHAUSE).parentPool())
                .isEqualTo("TSUK_PARENT_UNITS");
        assertThat(LocationProfileCatalog.definition(LocationProfile.TSUK_PRODUCTION).parentPool())
                .isEqualTo("TSUK_PARENT_UNITS");
        assertThat(LocationProfileCatalog.parentPool("TSUK_PARENT_UNITS").candidates())
                .containsExactly(2151L);
    }
}
