package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * One battalion has one CREWS region, but each warehouse keeper may act only for their own
 * warehouse, crews and fly points. These are product expectations, including cases that expose
 * currently missing initiator/sender scope checks.
 */
@Epic("Relocation")
@Feature("Edit by initiator or sender")
public class BattalionRelocationEditUiTest extends BaseUITest {

    private static final UserRole KEEPER_A = UserRole.CREW_MANAGER;
    private static final UserRole KEEPER_A_PEER = UserRole.CREW_READ;
    private static final UserRole KEEPER_B = UserRole.CREW_WRITE;
    private static final UserRole PRODUCTION_OWNER = UserRole.OWNER_3;

    private LocationProfileFixture locationProfiles;
    private StorageFixture storages;
    private StorageRegionFixture regions;
    private UserFixture users;
    private RelocationFixture relocations;
    private ResourceFixture resources;

    private StorageResponse warehouseA;
    private StorageResponse warehouseB;
    private StorageResponse locationA;
    private StorageResponse locationB;
    private StorageResponse pointA1;
    private StorageResponse pointA2;
    private StorageResponse pointB1;
    private StorageResponse pointB2;
    private StorageResponse crewA;
    private StorageResponse crewB;
    private StorageResponse attachedCrewA;
    private StorageResponse production;
    private StorageResponse productionDestination;
    private Long resourceId;
    private UserFixture.BusinessActor keeperA;
    private UserFixture.BusinessActor keeperAPeer;
    private UserFixture.BusinessActor keeperB;
    private UserFixture.BusinessActor productionOwner;
    private String sentControlA;
    private String receivedControlA;
    private String sentControlB;
    private String receivedControlB;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        regions = new StorageRegionFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        relocations = new RelocationFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);

        var battalion = locationProfiles.create(LocationProfile.BATTALION_WARENHAUSE_UNIT, 2);
        warehouseA = battalion.locations().get(0);
        warehouseB = battalion.locations().get(1);
        locationA = storages.createChildStorage(warehouseA.getId(), "rel-edit-a-destination-");
        locationB = storages.createChildStorage(warehouseB.getId(), "rel-edit-b-destination-");
        pointA1 = storages.createFlyPointStorage(warehouseA.getId(), "rel-edit-a-point-1-");
        pointA2 = storages.createFlyPointStorage(warehouseA.getId(), "rel-edit-a-point-2-");
        pointB1 = storages.createFlyPointStorage(warehouseB.getId(), "rel-edit-b-point-1-");
        pointB2 = storages.createFlyPointStorage(warehouseB.getId(), "rel-edit-b-point-2-");
        crewA = storages.createCrewStorage(warehouseA.getId(), "rel-edit-a-crew-");
        crewB = storages.createCrewStorage(warehouseB.getId(), "rel-edit-b-crew-");
        attachedCrewA = storages.createCrewStorage(pointA1.getId(), "rel-edit-a-attached-crew-");

        // The battalion has exactly one shared CREWS region. Membership must not grant access to
        // the other warehouse's relocation actions; that is what the negative cases assert.
        var region = regions.createRegion(battalion.parent(), StorageAccessMode.CREWS, "rel-edit-battalion-");
        regions.addRegionMembers(region.getId(), warehouseA.getId(), warehouseB.getId());
        regions.addRegionLocations(region.getId(), warehouseA.getId(), warehouseB.getId(),
                pointA1.getId(), pointA2.getId(), pointB1.getId(), pointB2.getId(),
                crewA.getId(), crewB.getId(), attachedCrewA.getId());

        keeperA = createActor(BusinessRole.UNIT_KOMIRNIK, KEEPER_A, warehouseA);
        keeperAPeer = createActor(BusinessRole.UNIT_KOMIRNIK, KEEPER_A_PEER, warehouseA);
        keeperB = createActor(BusinessRole.UNIT_KOMIRNIK, KEEPER_B, warehouseB);

        production = locationProfiles.create(LocationProfile.TSUK_PRODUCTION, 1).locations().getFirst();
        productionDestination = storages.createChildStorage(production.getId(), "rel-edit-production-destination-");
        productionOwner = createActor(BusinessRole.BUSINESS_UNIT_OWNER, PRODUCTION_OWNER, production);

        resources.fetchSharedUnit(3);
        resources.fetchSharedResourceCategory();
        ResourceResponse resource = resources.createUniqueResource("rel-edit-scope-");
        resourceId = resource.getId();
        for (StorageResponse source : List.of(warehouseA, warehouseB, production)) {
            relocations.ensureStock(source.getId(), resourceId, 100.0);
        }
        // A fly-point transfer consumes the departing point's stock, not warehouse stock.
        relocations.createSendAndFinishBySender(UserRole.ADMIN, warehouseA.getId(), pointA1.getId(), resourceId, 40.0);
        relocations.createSendAndFinishBySender(UserRole.ADMIN, warehouseB.getId(), pointB1.getId(), resourceId, 40.0);
        sentControlA = createSentHistoryControl(KEEPER_A, warehouseA, crewA, "scope-control-a-sent");
        receivedControlA = createReceivedHistoryControl(KEEPER_A, crewA, warehouseA, "scope-control-a-received");
        sentControlB = createSentHistoryControl(KEEPER_B, warehouseB, crewB, "scope-control-b-sent");
        receivedControlB = createReceivedHistoryControl(KEEPER_B, crewB, warehouseB, "scope-control-b-received");
    }

    private String createSentHistoryControl(UserRole role, StorageResponse warehouse,
                                            StorageResponse crew, String prefix) {
        RelocationResponse sent = send(role, warehouse, crew, prefix);
        RelocationResponse finished = relocations.resolve(
                role, sent.getId(), warehouse.getId(), RelocationState.FINISHED, sent.getDescription());
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);
        return sent.getDescription();
    }

    private String createReceivedHistoryControl(UserRole role, StorageResponse sender,
                                                StorageResponse warehouse, String prefix) {
        String description = marker(prefix);
        RelocationResponse received = receiveFrom(role, sender, warehouse, 1.0, description);
        assertThat(received.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        return description;
    }

    private UserFixture.BusinessActor createActor(BusinessRole businessRole, UserRole slot, StorageResponse location) {
        var actor = users.createBusinessActor(getPlaywrightSessionProvider(), businessRole, List.of(location));
        apiExecutor.setSessionForRole(slot, actor.username(), actor.password());
        return actor;
    }

    @AfterClass(alwaysRun = true)
    public void cleanupScenario() {
        for (UserRole slot : List.of(KEEPER_A, KEEPER_A_PEER, KEEPER_B, PRODUCTION_OWNER)) {
            apiExecutor.evictSessionForRole(slot);
        }
        if (users != null) users.deactivateTrackedUsers();
        if (regions != null && storages != null) TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages);
        if (locationProfiles != null) locationProfiles.cleanup();
    }

    @Test
    @TestCaseId("TC-UI-REL-EDIT-001")
    @Description("Адмін редагує видачу на локацію через ту саму форму")
    public void adminEditsLocationSend() {
        assertEditFormAndSave(UserRole.ADMIN, null, warehouseA, locationA, Form.LOCATION);
    }

    @Test
    @TestCaseId("TC-UI-REL-EDIT-002")
    @Description("Адмін редагує видачу на екіпаж через форму екіпажу")
    public void adminEditsCrewSend() {
        assertEditFormAndSave(UserRole.ADMIN, null, warehouseA, crewA, Form.CREW);
    }

    @Test
    @TestCaseId("TC-UI-REL-EDIT-003")
    @Description("Адмін редагує переміщення між точками через форму між точками")
    public void adminEditsBetweenFlyPoints() {
        assertEditFormAndSave(UserRole.ADMIN, null, pointA1, pointA2, Form.FLY_POINT);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-004", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Комірник батальйону редагує власну видачу на локацію")
    public void battalionKeeperEditsLocationSend() {
        assertEditFormAndSave(KEEPER_A, keeperA, warehouseA, locationA, Form.LOCATION);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-005", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Комірник батальйону редагує власну видачу на екіпаж")
    public void battalionKeeperEditsCrewSend() {
        assertEditFormAndSave(KEEPER_A, keeperA, warehouseA, crewA, Form.CREW);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-006", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Комірник батальйону редагує власне переміщення між точками")
    public void battalionKeeperEditsBetweenFlyPoints() {
        assertEditFormAndSave(KEEPER_A, keeperA, pointA1, pointA2, Form.FLY_POINT);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-007", roles = BusinessRole.BUSINESS_UNIT_OWNER,
            locationProfiles = LocationProfile.TSUK_PRODUCTION)
    @Description("Business Unit Owner виробництва редагує власну видачу на локацію")
    public void productionOwnerEditsLocationSend() {
        assertEditFormAndSave(PRODUCTION_OWNER, productionOwner, production, productionDestination, Form.LOCATION);
    }

    @Test
    @TestCaseId(value = "TC-REL-EDIT-SCOPE-001", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Інший комірник того самого складу редагує переміщення між його точками")
    public void peerKeeperCanEditSameWarehouseFlyPointTransfer() {
        String marker = marker("peer-fly-point");
        RelocationResponse sent = relocations.createSendWithDescription(
                KEEPER_A, pointA1.getId(), pointA2.getId(), resourceId, 2.0, marker);
        var edit = RelocationDataFactory.buildSendEditRequest(resourceId, 1.0, marker + "-edited")
                .toBuilder().version(sent.getVersion()).senderId(pointA1.getId()).build();

        var response = relocations.editSendRaw(KEEPER_A_PEER, sent.getId(), pointA1.getId(), edit);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        RelocationResponse updated = relocations.findInTransitById(UserRole.ADMIN, pointA1.getId(), sent.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getDescription()).contains("-edited");
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-008", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Два склади бачать лише свої локаційні переміщення")
    public void warehousesDoNotSeeEachOthersLocationTransfers() {
        RelocationResponse a = send(KEEPER_A, warehouseA, locationA, "visible-a");
        RelocationResponse b = send(KEEPER_B, warehouseB, locationB, "visible-b");
        assertJournalScope(KEEPER_A, keeperA, warehouseA, a, b);
        assertJournalScope(KEEPER_B, keeperB, warehouseB, b, a);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-009", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Спільна область CREWS не показує комірнику переміщення чужих точок")
    public void warehousesDoNotSeeEachOthersFlyPointTransfers() {
        RelocationResponse a = send(KEEPER_A, pointA1, pointA2, "point-visible-a");
        RelocationResponse b = send(KEEPER_B, pointB1, pointB2, "point-visible-b");
        assertJournalScope(KEEPER_A, keeperA, warehouseA, a, b);
        assertJournalScope(KEEPER_B, keeperB, warehouseB, b, a);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-010", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Спільна область CREWS не показує комірнику видачі на чужий екіпаж")
    public void warehousesDoNotSeeEachOthersCrewTransfers() {
        RelocationResponse a = send(KEEPER_A, warehouseA, crewA, "crew-visible-a");
        RelocationResponse b = send(KEEPER_B, warehouseB, crewB, "crew-visible-b");
        assertJournalScope(KEEPER_A, keeperA, warehouseA, a, b);
        assertJournalScope(KEEPER_B, keeperB, warehouseB, b, a);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-011", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Склад-ініціатор бачить повернення свого екіпажу, інший склад його не бачить")
    public void crewReturnIsScopedToInitiatingRecipientWarehouse() {
        relocations.createSendAndFinishBySender(UserRole.ADMIN,
                warehouseA.getId(), crewA.getId(), resourceId, 5.0);
        relocations.createSendAndFinishBySender(UserRole.ADMIN,
                warehouseB.getId(), crewB.getId(), resourceId, 5.0);
        String markerA = marker("crew-return-a");
        String markerB = marker("crew-return-b");
        var requestA = RelocationDataFactory.buildCrewReceiveRequest(
                crewA.getId(), warehouseA.getId(), resourceId, 2.0)
                .toBuilder().description(markerA).build();
        var requestB = RelocationDataFactory.buildCrewReceiveRequest(
                crewB.getId(), warehouseB.getId(), resourceId, 2.0)
                .toBuilder().description(markerB).build();
        var responseA = apiExecutor.executeRelocationReceive(requestA, KEEPER_A);
        var responseB = apiExecutor.executeRelocationReceive(requestB, KEEPER_B);
        assertThat(responseA.statusCode()).as(responseA.asString()).isEqualTo(200);
        assertThat(responseB.statusCode()).as(responseB.asString()).isEqualTo(200);
        assertThat(responseA.as(RelocationResponse.class).getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        assertThat(responseB.as(RelocationResponse.class).getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        assertReceivedJournalScope(KEEPER_A, keeperA, warehouseA, markerA, markerB);
        assertReceivedJournalScope(KEEPER_B, keeperB, warehouseB, markerB, markerA);
        assertThat(relocations.findHistoryByDescription(KEEPER_A, warehouseA.getId(),
                RelocationJournalQuery.Perspective.RECEIVED, markerA)).isNotNull();
        assertThat(relocations.findHistoryByDescription(KEEPER_B, warehouseB.getId(),
                RelocationJournalQuery.Perspective.RECEIVED, markerB)).isNotNull();
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-012", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Після підтвердження переміщення між точками склад не бачить його у вкладках «Видано» та «Отримано»")
    public void approvedFlyPointTransferIsAbsentFromWarehouseHistory() {
        String pointMarker = marker("approved-between-points");
        RelocationResponse transfer = relocations.createSendWithDescription(
                KEEPER_A, pointA1.getId(), pointA2.getId(), resourceId, 2.0, pointMarker);
        RelocationResponse finished = relocations.resolve(
                KEEPER_A, transfer.getId(), pointA1.getId(), RelocationState.FINISHED, pointMarker);
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);

        // Positive controls prove that both history tabs have loaded warehouse data.
        RelocationResponse issuedToCrew = send(KEEPER_A, warehouseA, crewA, "warehouse-sent-control");
        relocations.resolve(KEEPER_A, issuedToCrew.getId(), warehouseA.getId(), RelocationState.FINISHED);
        String receivedControl = marker("warehouse-received-control");
        var crewReturn = RelocationDataFactory.buildCrewReceiveRequest(
                crewA.getId(), warehouseA.getId(), resourceId, 1.0)
                .toBuilder().description(receivedControl).build();
        var received = apiExecutor.executeRelocationReceive(crewReturn, KEEPER_A);
        assertThat(received.statusCode()).as(received.asString()).isEqualTo(200);

        assertThat(relocations.findHistoryByDescription(UserRole.ADMIN, pointA1.getId(),
                RelocationJournalQuery.Perspective.SENT, pointMarker)).isNotNull();
        assertThat(relocations.findHistoryByDescription(UserRole.ADMIN, pointA2.getId(),
                RelocationJournalQuery.Perspective.RECEIVED, pointMarker)).isNotNull();

        openJournal(KEEPER_A, keeperA, warehouseA.getId());
        RelocationPage journal = new RelocationPage(page);
        page.waitForResponse(response -> response.url().contains("/storages/names/crew-units")
                        && response.url().contains("storageId=" + warehouseA.getId())
                        && response.status() == 200,
                journal::open);
        journal.openSentTab();
        journal.rowContainingText(issuedToCrew.getDescription()).waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean shownAsSent = journal.isRowWithTextVisible(pointMarker);
        journal.openReceivedTab();
        journal.rowContainingText(receivedControl).waitFor(
                new com.microsoft.playwright.Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean shownAsReceived = journal.isRowWithTextVisible(pointMarker);

        RelocationResponse warehouseSent = relocations.findHistoryByDescription(KEEPER_A,
                warehouseA.getId(), RelocationJournalQuery.Perspective.SENT, pointMarker);
        RelocationResponse warehouseReceived = relocations.findHistoryByDescription(KEEPER_A,
                warehouseA.getId(), RelocationJournalQuery.Perspective.RECEIVED, pointMarker);
        assertSoftly(softly -> {
            softly.assertThat(shownAsSent).as("склад не повинен бачити FP→FP у «Видано»").isFalse();
            softly.assertThat(shownAsReceived).as("склад не повинен бачити FP→FP у «Отримано»").isFalse();
            softly.assertThat(warehouseSent).as("API-історія «Видано» складу").isNull();
            softly.assertThat(warehouseReceived).as("API-історія «Отримано» складу").isNull();
        });
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-013", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Видача складу A на екіпаж є лише у «Видано» A і відсутня в обох вкладках B")
    public void issuedToUnattachedCrewAppearsOnlyInSenderWarehouseHistory() {
        RelocationResponse finished = finishWarehouseIssue(crewA, "history-issue-unattached-crew");
        assertWarehouseHistoryDirection(finished.getDescription(), RelocationJournalQuery.Perspective.SENT);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-014", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Видача складу A на прив'язаний до точки екіпаж є лише у «Видано» A")
    public void issuedToAttachedCrewAppearsOnlyInSenderWarehouseHistory() {
        RelocationResponse finished = finishWarehouseIssue(attachedCrewA, "history-issue-attached-crew");
        assertWarehouseHistoryDirection(finished.getDescription(), RelocationJournalQuery.Perspective.SENT);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-015", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Видача складу A на точку є лише у «Видано» A і відсутня в обох вкладках B")
    public void issuedToFlyPointAppearsOnlyInSenderWarehouseHistory() {
        RelocationResponse finished = finishWarehouseIssue(pointA2, "history-issue-fly-point");
        assertWarehouseHistoryDirection(finished.getDescription(), RelocationJournalQuery.Perspective.SENT);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-016", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Отримання складом A від екіпажу є лише в «Отримано» A і відсутнє в обох вкладках B")
    public void receivedFromUnattachedCrewAppearsOnlyInRecipientWarehouseHistory() {
        finishWarehouseIssue(crewA, "history-seed-crew-return");
        RelocationResponse received = receiveFrom(KEEPER_A, crewA, warehouseA, 1.0,
                marker("history-return-from-crew"));
        assertThat(received.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        assertWarehouseHistoryDirection(received.getDescription(), RelocationJournalQuery.Perspective.RECEIVED);
    }

    @Test
    @TestCaseId(value = "TC-UI-REL-EDIT-017", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Отримання складом A від точки є лише в «Отримано» A і відсутнє в обох вкладках B")
    public void receivedFromFlyPointAppearsOnlyInRecipientWarehouseHistory() {
        RelocationResponse received = receiveFrom(KEEPER_A, pointA1, warehouseA, 1.0,
                marker("history-return-from-fly-point"));
        assertThat(received.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        assertWarehouseHistoryDirection(received.getDescription(), RelocationJournalQuery.Perspective.RECEIVED);
    }

    @Test
    @TestCaseId(value = "TC-REL-EDIT-SCOPE-002", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Чужий склад не може редагувати, приймати чи скасувати переміщення за ID")
    public void foreignWarehouseCannotMutateActiveTransferById() {
        RelocationResponse foreign = send(KEEPER_B, warehouseB, locationB, "foreign-action");
        var edit = RelocationDataFactory.buildSendEditRequest(resourceId, 1.0, "foreign-edit")
                .toBuilder().version(foreign.getVersion()).senderId(warehouseB.getId()).build();

        var editResponse = relocations.editSendRaw(KEEPER_A, foreign.getId(), warehouseA.getId(), edit);
        assertThat(editResponse.statusCode()).as("чужа правка: " + editResponse.asString()).isEqualTo(403);
        var acceptResponse = relocations.resolveRaw(KEEPER_A, foreign.getId(), warehouseA.getId(), RelocationState.FINISHED);
        assertThat(acceptResponse.statusCode()).as("чужий прийом: " + acceptResponse.asString()).isEqualTo(403);
        var cancelResponse = relocations.resolveRaw(KEEPER_A, foreign.getId(), warehouseA.getId(), RelocationState.CANCELLED);
        assertThat(cancelResponse.statusCode()).as("чуже скасування: " + cancelResponse.asString()).isEqualTo(403);
        assertThat(relocations.findInTransitById(UserRole.ADMIN, warehouseB.getId(), foreign.getId()))
                .as("чужі запити не змінили стан").isNotNull();
    }

    @Test
    @TestCaseId(value = "TC-REL-EDIT-SCOPE-005", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Спільна область CREWS не дозволяє змінити переміщення чужих точок за ID")
    public void foreignWarehouseCannotEditFlyPointTransferById() {
        RelocationResponse foreign = send(KEEPER_B, pointB1, pointB2, "foreign-point-edit");
        var edit = RelocationDataFactory.buildSendEditRequest(resourceId, 1.0, "foreign-point-changed")
                .toBuilder().version(foreign.getVersion()).senderId(pointB1.getId()).build();
        var response = relocations.editSendRaw(KEEPER_A, foreign.getId(), pointB1.getId(), edit);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(403);
        RelocationResponse unchanged = relocations.findInTransitById(UserRole.ADMIN, pointB1.getId(), foreign.getId());
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.getDescription()).isEqualTo(foreign.getDescription());
    }

    @Test
    @TestCaseId(value = "TC-REL-EDIT-SCOPE-003", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Чужий склад не може видалити завершене переміщення, яке можна видаляти власнику")
    public void foreignWarehouseCannotDeleteById() {
        // EXTERNAL recipient makes this an AUTO_FINISHED delete candidate. A CREATED send would
        // reject deletion for its state and would not exercise the foreign-warehouse guard.
        StorageResponse externalB = storages.createExternalChildStorage(warehouseB.getId(), "rel-edit-b-external-");
        RelocationResponse foreign = send(KEEPER_B, warehouseB, externalB, "foreign-delete");
        assertThat(foreign.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        var response = relocations.deleteRelocationRaw(KEEPER_A, foreign.getId(), warehouseA.getId());
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(403);
        assertThat(relocations.findHistoryByDescription(UserRole.ADMIN, warehouseB.getId(),
                com.erp.models.query.RelocationJournalQuery.Perspective.SENT, foreign.getDescription())).isNotNull();
    }

    @Test
    @TestCaseId(value = "TC-REL-EDIT-SCOPE-004", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.BATTALION_WARENHAUSE_UNIT)
    @Description("Прямий запит на журнал чужого складу заборонений")
    public void foreignWarehouseJournalScopeIsForbidden() {
        var a = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_PAGE, KEEPER_A,
                Map.of("senderIds", warehouseB.getId(), "states", List.of("CREATED")));
        var b = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_PAGE, KEEPER_B,
                Map.of("senderIds", warehouseA.getId(), "states", List.of("CREATED")));
        assertThat(a.statusCode()).isEqualTo(403);
        assertThat(b.statusCode()).isEqualTo(403);
        var pointA = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_PAGE, KEEPER_A,
                Map.of("senderIds", pointB1.getId(), "states", List.of("CREATED")));
        var pointB = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_PAGE, KEEPER_B,
                Map.of("senderIds", pointA1.getId(), "states", List.of("CREATED")));
        assertThat(pointA.statusCode()).as("чужі точки A").isEqualTo(403);
        assertThat(pointB.statusCode()).as("чужі точки B").isEqualTo(403);
    }

    private void assertEditFormAndSave(UserRole role, UserFixture.BusinessActor actor,
                                       StorageResponse sender, StorageResponse recipient, Form form) {
        String initial = marker("edit-" + form.name().toLowerCase());
        RelocationResponse sent = relocations.createSendWithDescription(
                role, sender.getId(), recipient.getId(), resourceId, 2.0, initial);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);
        long workspaceId = sender.getId().equals(pointA1.getId()) ? warehouseA.getId() : sender.getId();
        openJournal(role, actor, workspaceId);
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.rowContainingText(initial).waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));
        assertThat(journal.isEditButtonVisibleInRow(initial)).as("олівець для " + form).isTrue();
        journal.rowContainingText(initial)
                .getByRole(AriaRole.BUTTON,
                        new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Редагувати"))
                .click();
        page.waitForURL("**/relocation/update-output/" + sent.getId());
        page.locator("#description").waitFor();
        assertCorrectForm(form);
        assertThat(page.locator("#description").inputValue()).isEqualTo(initial);
        String edited = initial + "-edited";
        page.locator("#description").fill(edited);
        var issuer = page.locator("input[name='sendingPersonName']");
        if (issuer.count() > 0 && issuer.first().inputValue().isBlank()) issuer.first().fill("Autotest");
        Response put = page.waitForResponse(
                r -> r.url().contains("/relocations/" + sent.getId() + "/send")
                        && "PUT".equals(r.request().method()),
                () -> page.getByRole(AriaRole.BUTTON,
                        new com.microsoft.playwright.Page.GetByRoleOptions().setName("Підтвердити")).click());
        assertThat(put.status()).as(put.text()).isBetween(200, 299);
        RelocationResponse updated = relocations.findInTransitById(UserRole.ADMIN, sender.getId(), sent.getId());
        assertThat(updated).isNotNull();
        assertThat(updated.getDescription()).isEqualTo(edited);
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
    }

    private void assertCorrectForm(Form form) {
        switch (form) {
            case LOCATION -> {
                assertThat(page.getByText("Форма видачі", new com.microsoft.playwright.Page.GetByTextOptions()
                        .setExact(true)).count()).isEqualTo(1);
                assertThat(page.getByText("Форма видачі на екіпаж").count()).isZero();
            }
            case CREW -> assertThat(page.getByText("Форма видачі на екіпаж").count()).isEqualTo(1);
            case FLY_POINT -> {
                assertThat(page.getByText("Точка вильоту (звідки)").count()).isPositive();
                assertThat(page.getByText("Точка вильоту (куди)").count()).isPositive();
            }
        }
    }

    private RelocationResponse finishWarehouseIssue(StorageResponse recipient, String prefix) {
        RelocationResponse sent = send(KEEPER_A, warehouseA, recipient, prefix);
        RelocationResponse finished = relocations.resolve(
                KEEPER_A, sent.getId(), warehouseA.getId(), RelocationState.FINISHED, sent.getDescription());
        assertThat(finished.getState()).isEqualTo(RelocationState.FINISHED);
        assertThat(finished.getDescription()).isEqualTo(sent.getDescription());
        return finished;
    }

    private RelocationResponse receiveFrom(UserRole role, StorageResponse sender,
                                           StorageResponse warehouse, double amount, String description) {
        RelocationInputRequest request = RelocationInputRequest.builder()
                .senderId(sender.getId())
                .recipientId(warehouse.getId())
                .description(description)
                .date(LocalDate.now())
                .items(List.of(RelocationDataFactory.usage(resourceId, amount)))
                .build();
        var response = apiExecutor.executeRelocationReceive(request, role);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        RelocationResponse received = response.as(RelocationResponse.class);
        assertThat(received.getDescription()).isEqualTo(description);
        return received;
    }

    private void assertWarehouseHistoryDirection(String description,
                                                 RelocationJournalQuery.Perspective expected) {
        RelocationResponse aSent = relocations.findHistoryByDescription(KEEPER_A,
                warehouseA.getId(), RelocationJournalQuery.Perspective.SENT, description);
        RelocationResponse aReceived = relocations.findHistoryByDescription(KEEPER_A,
                warehouseA.getId(), RelocationJournalQuery.Perspective.RECEIVED, description);
        RelocationResponse bSent = relocations.findHistoryByDescription(KEEPER_B,
                warehouseB.getId(), RelocationJournalQuery.Perspective.SENT, description);
        RelocationResponse bReceived = relocations.findHistoryByDescription(KEEPER_B,
                warehouseB.getId(), RelocationJournalQuery.Perspective.RECEIVED, description);

        boolean[] a = warehouseHistoryVisibility(KEEPER_A, keeperA, warehouseA,
                sentControlA, receivedControlA, description);
        boolean[] b = warehouseHistoryVisibility(KEEPER_B, keeperB, warehouseB,
                sentControlB, receivedControlB, description);
        boolean expectedSent = expected == RelocationJournalQuery.Perspective.SENT;
        assertSoftly(softly -> {
            if (expectedSent) {
                softly.assertThat(aSent).as("API A → Видано").isNotNull();
                softly.assertThat(aReceived).as("API A → Отримано").isNull();
            } else {
                softly.assertThat(aSent).as("API A → Видано").isNull();
                softly.assertThat(aReceived).as("API A → Отримано").isNotNull();
            }
            softly.assertThat(bSent).as("API B → Видано").isNull();
            softly.assertThat(bReceived).as("API B → Отримано").isNull();
            softly.assertThat(a[0]).as("UI A → Видано").isEqualTo(expectedSent);
            softly.assertThat(a[1]).as("UI A → Отримано").isEqualTo(!expectedSent);
            softly.assertThat(b[0]).as("UI B → Видано").isFalse();
            softly.assertThat(b[1]).as("UI B → Отримано").isFalse();
        });
    }

    /** Returns visibility in [«Видано», «Отримано»] after both tables show their own control row. */
    private boolean[] warehouseHistoryVisibility(UserRole role, UserFixture.BusinessActor actor,
                                                  StorageResponse warehouse, String sentControl,
                                                  String receivedControl, String description) {
        openJournal(role, actor, warehouse.getId());
        RelocationPage journal = new RelocationPage(page);
        page.waitForResponse(response -> response.url().contains("/storages/names/crew-units")
                        && response.url().contains("storageId=" + warehouse.getId())
                        && response.status() == 200,
                journal::open);
        journal.openSentTab();
        journal.rowContainingText(sentControl).waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));
        page.waitForLoadState(LoadState.NETWORKIDLE);
        boolean sent = journal.isRowWithTextVisible(description);
        journal.openReceivedTab();
        journal.rowContainingText(receivedControl).waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));
        page.waitForLoadState(LoadState.NETWORKIDLE);
        boolean received = journal.isRowWithTextVisible(description);
        return new boolean[]{sent, received};
    }

    private RelocationResponse send(UserRole role, StorageResponse sender, StorageResponse recipient, String prefix) {
        return relocations.createSendWithDescription(role, sender.getId(), recipient.getId(),
                resourceId, 2.0, marker(prefix));
    }

    private void assertJournalScope(UserRole role, UserFixture.BusinessActor actor, StorageResponse workspace,
                                    RelocationResponse own, RelocationResponse foreign) {
        openJournal(role, actor, workspace.getId());
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.rowContainingText(own.getDescription()).waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));
        assertThat(journal.isRowWithTextVisible(foreign.getDescription())).as("чуже переміщення").isFalse();
    }

    private void assertReceivedJournalScope(UserRole role, UserFixture.BusinessActor actor,
                                            StorageResponse workspace, String own, String foreign) {
        openJournal(role, actor, workspace.getId());
        RelocationPage journal = new RelocationPage(page).open().openReceivedTab();
        journal.rowContainingText(own).waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));
        assertThat(journal.isRowWithTextVisible(foreign)).as("чуже повернення від екіпажу").isFalse();
    }

    private void openJournal(UserRole role, UserFixture.BusinessActor actor, long workspaceId) {
        browserContext.clearCookies();
        Map<String, String> cookies = actor == null
                ? cachedSessionCookies(role)
                : authService.getSessionForUser(actor.username(), actor.password());
        injectSessionCookies(cookies, sessionCookieDomain());
        page.navigate(ConfigProvider.getBaseUrl());
        page.evaluate("id => localStorage.setItem('selectedStorageId', id)", String.valueOf(workspaceId));
    }

    private static String marker(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private enum Form { LOCATION, CREW, FLY_POINT }
}
