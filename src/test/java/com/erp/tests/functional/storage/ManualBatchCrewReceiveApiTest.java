package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.ResourceBatchSelectionRequest;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.StorageResponse;
import com.erp.utils.helpers.ProductionStockAssertions;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Epic("Relocation")
@Feature("Manual input batch accounting API")
public class ManualBatchCrewReceiveApiTest extends CrewApiTestBase {

    private LocationProfileFixture locations;
    private UserFixture users;
    private StorageResponse warehouse;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupCrewApiBase")
    public void setupManualCrewReturn() {
        locations = new LocationProfileFixture(testContext, apiExecutor);
        warehouse = locations.create(LocationProfile.BATTALION_WARENHAUSE_UNIT, 1)
                .locations().getFirst();
        users = new UserFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext(warehouse.getId());
        resourceId = resourceFixture.createUniqueResource("manual-crew-return-").getId();
    }

    @AfterMethod(alwaysRun = true)
    public void restoreCrewRole() {
        apiExecutor.restoreDefaultSessionForRole(UserRole.OWNER_1);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupManualCrewReturn() {
        apiExecutor.restoreDefaultSessionForRole(UserRole.OWNER_1);
        if (users != null) users.deactivateTrackedUsers();
        if (locations != null) locations.cleanup();
        if (resourceId != null) resourceFixture.deactivate(UserRole.ADMIN, resourceId);
    }

    @TestCaseId("TC-MIB-021")
    @Story("A crew return must use the mode of the fly point holding the stock")
    @Description("POST receive without a batch is rejected for MANUAL at the actual stock owner; "
            + "an explicit UUID debits only that fly-point batch")
    @Test
    public void attachedCrewReturnRequiresFlyPointBatch() {
        CrewRegionScenario scenario = crewFixture.prepareReturnScenario(
                warehouse, true, "manual-crew-return-");
        List<StorageResponse> scoped = new ArrayList<>(List.of(
                warehouse, scenario.unit(), scenario.crew(), scenario.flyPoint()));
        UserFixture.BusinessActor actor = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.UNIT_KOMIRNIK, scoped);
        apiExecutor.setSessionForRole(UserRole.OWNER_1, actor.username(), actor.password());

        Long warehouseId = scenario.memberStorageId();
        Long flyPointId = scenario.flyPoint().getId();
        Long crewId = scenario.crew().getId();
        relocationFixture.ensureStock(warehouseId, resourceId, 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1, warehouseId, crewId, resourceId, 12.0);

        StorageItemBatchResponse selected = ProductionStockAssertions.queryBatches(
                apiExecutor, flyPointId, UserRole.ADMIN, resourceId, false, null).stream()
                .filter(batch -> batch.getAmount() != null && batch.getAmount() >= 5.0)
                .findFirst().orElseThrow();
        double before = selected.getAmount();
        Response enabled = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT,
                UserRole.ADMIN, new ResourceBatchSelectionRequest(true), flyPointId, resourceId);
        assertThat(enabled.statusCode()).as(enabled.asString()).isBetween(200, 299);
        try {
            RelocationInputRequest missing = RelocationDataFactory.buildCrewReceiveRequest(
                    crewId, warehouseId, resourceId, 5.0);
            Response rejected = apiExecutor.executeRelocationReceive(missing, UserRole.OWNER_1);
            assertThat(rejected.statusCode()).as(rejected.asString()).isBetween(400, 499);
            assertThat(batchAmount(flyPointId, selected)).isCloseTo(before, within(0.01));

            RelocationInputRequest exact = missing.toBuilder().items(List.of(
                    RelocationDataFactory.usageWithBatch(resourceId, 5.0,
                            selected.getBatchUuid(), selected.getBatchNumber(), false))).build();
            Response accepted = apiExecutor.executeRelocationReceive(exact, UserRole.OWNER_1);
            assertThat(accepted.statusCode()).as(accepted.asString()).isBetween(200, 299);
            assertThat(batchAmount(flyPointId, selected)).isCloseTo(before - 5.0, within(0.01));
        } finally {
            apiExecutor.execute(ApiEndpointDefinition.RESOURCE_BATCH_SELECTION_PUT,
                    UserRole.ADMIN, new ResourceBatchSelectionRequest(false), flyPointId, resourceId);
        }
    }

    private double batchAmount(Long locationId, StorageItemBatchResponse selected) {
        return ProductionStockAssertions.queryBatches(
                apiExecutor, locationId, UserRole.ADMIN, resourceId, false, null).stream()
                .filter(batch -> selected.getBatchUuid().equals(batch.getBatchUuid()))
                .findFirst().orElseThrow().getAmount();
    }
}
