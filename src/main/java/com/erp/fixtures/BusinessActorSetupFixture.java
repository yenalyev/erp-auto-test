package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.test_context.TestContext;
import com.erp.utils.auth.PlaywrightSessionProvider;
import io.qameta.allure.Step;

/** Composes independent role and location catalogs into a runtime test actor. */
public class BusinessActorSetupFixture {

    private final LocationProfileFixture locationFixture;
    private final UserFixture userFixture;
    private final PlaywrightSessionProvider playwright;

    public BusinessActorSetupFixture(
            TestContext testContext,
            ApiExecutor apiExecutor,
            PlaywrightSessionProvider playwright) {
        this.locationFixture = new LocationProfileFixture(testContext, apiExecutor);
        this.userFixture = new UserFixture(testContext, apiExecutor);
        this.playwright = playwright;
    }

    @Step("FIXTURE: створити бізнес-актора {businessRole} з профілем {locationProfile}")
    public BusinessActorSetup create(
            BusinessRole businessRole,
            LocationProfile locationProfile,
            int locationCount) {
        LocationProfileFixture.LocationSet locationSet = locationFixture.create(locationProfile, locationCount);
        UserFixture.BusinessActor actor = userFixture.createBusinessActor(
                playwright,
                businessRole,
                locationSet.locations());
        return new BusinessActorSetup(actor, locationSet);
    }

    /**
     * Battalion keeper setup: UNIT children with INTERNAL relation and no milUnitType,
     * plus a fresh user with DB-backed «Керівник локації» and «Екіпажі: перегляд» access roles.
     */
    public BusinessActorSetup createUnitKomirnik(int locationCount) {
        return create(BusinessRole.UNIT_KOMIRNIK, LocationProfile.BATTALION_UNIT, locationCount);
    }

    @Step("FIXTURE: cleanup бізнес-акторів та їхніх локацій")
    public void cleanup() {
        userFixture.deactivateTrackedUsers();
        locationFixture.cleanup();
    }

    public record BusinessActorSetup(
            UserFixture.BusinessActor actor,
            LocationProfileFixture.LocationSet locationSet) {
    }
}
