package com.erp.tests.framework;

import com.erp.models.access.GrantScopeKind;
import com.erp.models.response.AccessGrantSummaryResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.models.response.UserMeResponse;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UserMeResponsePermissionTest {

    @Test
    public void aggregatePermissionsUseGrantScopeToSeparateFullAndReadOnlyLocations() {
        UserMeResponse me = UserMeResponse.builder()
                .permissions(List.of("location::read", "order::create", "order::update"))
                .allowedStorageIds(List.of(10L, 20L))
                .grants(List.of(
                        locationGrant(10L, "Керівник локації"),
                        locationGrant(20L, "Перегляд локації")))
                .build();

        assertThat(me.hasReadOn(10L)).isTrue();
        assertThat(me.hasReadOn(20L)).isTrue();
        assertThat(me.hasMutateOn(10L)).isTrue();
        assertThat(me.hasMutateOn(20L)).isFalse();
        assertThat(me.hasOrderCreateOn(10L)).isTrue();
        assertThat(me.hasOrderCreateOn(20L)).isFalse();
    }

    @Test
    public void legacyPerLocationPermissionsRemainSupported() {
        UserMeResponse me = UserMeResponse.builder()
                .permissions(List.of("order::42::read", "order::42::create"))
                .build();

        assertThat(me.hasReadOn(42L)).isTrue();
        assertThat(me.hasMutateOn(42L)).isTrue();
        assertThat(me.hasOrderCreateOn(42L)).isTrue();
    }

    @Test
    public void explicitLocationGrantIsAuthoritativeWhenAllowedStorageIdsLagsBehind() {
        UserMeResponse me = UserMeResponse.builder()
                .permissions(List.of("order::create"))
                .grants(List.of(locationGrant(10L, "Керівник локації")))
                .build();

        assertThat(me.hasOrderCreateOn(10L)).isTrue();
    }

    private static AccessGrantSummaryResponse locationGrant(long storageId, String name) {
        return AccessGrantSummaryResponse.builder()
                .name(name)
                .scopeKind(GrantScopeKind.LOCATION)
                .storage(SimpleEntityResponse.builder().id(storageId).build())
                .build();
    }
}
