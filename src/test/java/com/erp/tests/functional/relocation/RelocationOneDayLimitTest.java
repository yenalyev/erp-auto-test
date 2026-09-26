package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.data.LocationProfileCatalog;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.LocationFeature;
import com.erp.enums.RelocationState;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.StorageRelation;
import com.erp.enums.StorageKind;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.request.StorageRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** Date-based write window for every sender inside the configured TSUK hierarchy. */
@Epic("Relocation")
@Feature("TSUK-hierarchy one-day relocation write limit")
public class RelocationOneDayLimitTest extends BaseFunctionalTest {

    private static final String TSUK_PARENT_POOL = "TSUK_PARENT_UNITS";
    private static final UserRole TSUK_PRODUCTION_SLOT = UserRole.OWNER_3;
    private static final UserRole TSUK_WAREHOUSE_SLOT = UserRole.RESOURCE_VIEWER;
    private static final UserRole OTHER_PARENT_SLOT = UserRole.CREW_READ;
    private static final UserRole ROOT_SLOT = UserRole.CREW_WRITE;
    private RelocationFixture relocations;
    private StorageFixture storages;
    private StorageFixture rootStorages;
    private StorageRegionFixture regions;
    private LocationProfileFixture locationProfiles;
    private UserFixture users;
    private Long resourceId;
    private Long tsukParentId;
    private Actor tsukProduction;
    private Actor tsukWarehouse;
    private Actor otherParent;
    private Actor root;
    private StorageResponse flyPoint1;
    private StorageResponse flyPoint2;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareActors() {
        relocations = new RelocationFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        rootStorages = new StorageFixture(testContext, apiExecutor);
        regions = new StorageRegionFixture(testContext, apiExecutor);
        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        relocations.fetchSharedUnit(3);
        relocations.fetchSharedResourceCategory();
        relocations.setupSharedResourceList(3);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = resources.getFirst().getId();
        testContext.set(ContextKey.RELOCATION_RESOURCE_ID, resourceId);

        tsukParentId = LocationProfileCatalog.parentPool(TSUK_PARENT_POOL).candidates().getFirst();

        var productionSet = locationProfiles.create(LocationProfile.TSUK_PRODUCTION, 1);
        assertThat(productionSet.parent().getId()).isEqualTo(tsukParentId);
        tsukProduction = createActor(BusinessRole.BUSINESS_UNIT_OWNER,
                TSUK_PRODUCTION_SLOT, productionSet.locations().getFirst());
        assertThat(tsukProduction.insideTsukHierarchy()).isTrue();

        var warehouseSet = locationProfiles.create(LocationProfile.TSUK_WARENHAUSE, 1);
        assertThat(warehouseSet.parent().getId()).isEqualTo(tsukParentId);
        StorageResponse nestedTsukWarehouse = storages.createChildStorage(
                warehouseSet.locations().getFirst().getId(), "rel-date-tsuk-descendant-");
        assertThat(nestedTsukWarehouse.getParent().getId()).isNotEqualTo(tsukParentId);
        flyPoint1 = storages.createFlyPointStorage(
                nestedTsukWarehouse.getId(), "rel-date-tsuk-fp1-");
        flyPoint2 = storages.createFlyPointStorage(
                nestedTsukWarehouse.getId(), "rel-date-tsuk-fp2-");
        var flyPointRegion = regions.createRegion(
                nestedTsukWarehouse, StorageAccessMode.CREWS, "rel-date-tsuk-fp-region-");
        regions.addRegionMembers(flyPointRegion.getId(), nestedTsukWarehouse.getId());
        regions.addRegionLocations(flyPointRegion.getId(),
                nestedTsukWarehouse.getId(), flyPoint1.getId(), flyPoint2.getId());
        tsukWarehouse = createActor(BusinessRole.UNIT_KOMIRNIK,
                TSUK_WAREHOUSE_SLOT, nestedTsukWarehouse);
        assertThat(tsukWarehouse.insideTsukHierarchy()).isTrue();
        assertThat(flyPoint1.getKind()).isEqualTo(StorageKind.FLY_POINT);
        assertThat(flyPoint2.getKind()).isEqualTo(StorageKind.FLY_POINT);
        assertThat(hasTsukAncestor(flyPoint1)).isTrue();
        assertThat(hasTsukAncestor(flyPoint2)).isTrue();
        relocations.createSendAndFinishBySender(UserRole.ADMIN,
                nestedTsukWarehouse.getId(), flyPoint1.getId(), resourceId, 100.0);

        StorageResponse keeperStorage = locationProfiles
                .create(LocationProfile.BATTALION_WARENHAUSE_UNIT, 1).locations().getFirst();
        assertThat(keeperStorage.getParent()).isNotNull();
        assertThat(keeperStorage.getParent().getId()).isNotEqualTo(tsukParentId);
        otherParent = createActor(BusinessRole.UNIT_KOMIRNIK, OTHER_PARENT_SLOT, keeperStorage);
        assertThat(otherParent.insideTsukHierarchy()).isFalse();

        StorageResponse rootStorage = rootStorages.createStorage(StorageRequest.builder()
                .name(StorageDataFactory.uniqueName("rel-date-root-"))
                .kind(StorageKind.LOCATION)
                .features(Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT))
                .relation(StorageRelation.INTERNAL)
                .accessMode(StorageAccessMode.FULL_ACCESS)
                .build());
        assertThat(rootStorage.getParent()).isNull();
        root = createActor(BusinessRole.UNIT_KOMIRNIK, ROOT_SLOT, rootStorage);
        assertThat(root.insideTsukHierarchy()).isFalse();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupActors() {
        for (UserRole slot : List.of(TSUK_PRODUCTION_SLOT, TSUK_WAREHOUSE_SLOT,
                OTHER_PARENT_SLOT, ROOT_SLOT)) {
            apiExecutor.evictSessionForRole(slot);
        }
        if (users != null) users.deactivateTrackedUsers();
        TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages);
        if (locationProfiles != null && !TestArtifactCleanup.shouldSkipApiCleanup()) {
            locationProfiles.cleanup();
        }
        if (rootStorages != null && !TestArtifactCleanup.shouldSkipApiCleanup()) {
            rootStorages.deactivateTrackedStorages(UserRole.ADMIN);
        }
    }

    @DataProvider(name = "tsukActors")
    public Object[][] tsukActors() {
        return new Object[][]{{tsukProduction}, {tsukWarehouse}};
    }

    @DataProvider(name = "unrestrictedActors")
    public Object[][] unrestrictedActors() {
        return new Object[][]{{otherParent}, {root}};
    }

    @DataProvider(name = "allNonAdminActors")
    public Object[][] allNonAdminActors() {
        return new Object[][]{{tsukProduction}, {tsukWarehouse}, {otherParent}, {root}};
    }

    @DataProvider(name = "unrestrictedDeleters")
    public Object[][] unrestrictedDeleters() {
        return new Object[][]{{otherParent}, {root}};
    }

    @DataProvider(name = "adminDates")
    public Object[][] adminDates() {
        return new Object[][]{
                {LocalDate.now().minusDays(1)},
                {LocalDate.now().minusDays(2)}
        };
    }

    @Test(dataProvider = "tsukActors")
    @TestCaseId(value = "TC-REL-DATE-001",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін у TSUK-ієрархії може створити переміщення з датою вчора.")
    public void nonAdminCanCreateYesterday(Actor actor) {
        identify(actor);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RelocationResponse created = send(actor.role(), actor.senderId(), actor.recipientId(),
                yesterday, marker("create-yesterday"));
        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(created.getDate()).isEqualTo(yesterday);
    }

    @Test(dataProvider = "tsukActors")
    @TestCaseId(value = "TC-REL-DATE-002",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін у TSUK-ієрархії не може створити переміщення з датою позавчора.")
    public void nonAdminCannotCreateTwoDaysAgo(Actor actor) {
        identify(actor);
        double before = stock(actor.senderId());
        RelocationOutputRequest request = sendRequest(actor.senderId(), actor.recipientId(),
                LocalDate.now().minusDays(2), marker("create-expired"));
        Response response = relocations.sendRaw(actor.role(), request);
        assertDateLimit(response);
        assertThat(stock(actor.senderId())).isCloseTo(before, offset(0.01));
    }

    @Test(dataProvider = "tsukActors")
    @TestCaseId(value = "TC-REL-DATE-003",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін у TSUK-ієрархії може редагувати вчорашню видачу в дорозі.")
    public void nonAdminCanEditYesterday(Actor actor) {
        identify(actor);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RelocationResponse sent = send(UserRole.ADMIN, actor.senderId(), actor.recipientId(),
                yesterday, marker("edit-yesterday"));
        RelocationResponse updated = relocations.editSend(actor.role(), sent.getId(), actor.senderId(),
                editRequest(yesterday, marker("edited")));
        assertThat(updated.getId()).isEqualTo(sent.getId());
        assertThat(updated.getDate()).isEqualTo(yesterday);
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(updated.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    @Test(dataProvider = "tsukActors")
    @TestCaseId(value = "TC-REL-DATE-004",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін у TSUK-ієрархії не розблоковує стару видачу сьогоднішньою датою.")
    public void nonAdminCannotEditTwoDaysAgo(Actor actor) {
        identify(actor);
        LocalDate issueDate = LocalDate.now().minusDays(2);
        RelocationResponse sent = send(UserRole.ADMIN, actor.senderId(), actor.recipientId(),
                issueDate, marker("edit-expired"));
        double before = stock(actor.senderId());
        Response response = relocations.editSendRaw(actor.role(), sent.getId(), actor.senderId(),
                editRequest(LocalDate.now(), marker("edited-late")));
        assertDateLimit(response);

        RelocationResponse still = relocations.findInTransitById(
                UserRole.ADMIN, actor.senderId(), sent.getId());
        assertThat(still).isNotNull();
        assertThat(still.getDate()).isEqualTo(issueDate);
        assertThat(still.getDescription()).isEqualTo(sent.getDescription());
        assertThat(still.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(stock(actor.senderId())).isCloseTo(before, offset(0.01));
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-005",
            roles = BusinessRole.UNIT_KOMIRNIK)
    @Description("Неадмін із правом на відправника видаляє завершену видачу з датою вчора.")
    public void nonAdminCanDeleteYesterday() {
        Actor actor = tsukWarehouse;
        identify(actor);
        double beforeSend = stock(actor.senderId());
        RelocationResponse sent = send(UserRole.ADMIN, actor.senderId(), actor.externalRecipientId(),
                LocalDate.now().minusDays(1), marker("delete-yesterday"));
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        Response response = relocations.deleteRelocationRaw(actor.role(), sent.getId(), actor.senderId());
        assertThat(response.statusCode()).as(response.asString()).isIn(200, 204);
        assertThat(relocations.findHistoryByDescription(UserRole.ADMIN, actor.senderId(),
                RelocationJournalQuery.Perspective.SENT, sent.getDescription())).isNull();
        assertThat(stock(actor.senderId())).isCloseTo(beforeSend, offset(0.01));
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-006",
            roles = BusinessRole.UNIT_KOMIRNIK)
    @Description("Неадмін на вкладеній локації TSUK не може видалити стару завершену видачу.")
    public void nonAdminCannotDeleteTwoDaysAgo() {
        Actor actor = tsukWarehouse;
        identify(actor);
        RelocationResponse sent = send(UserRole.ADMIN, actor.senderId(), actor.externalRecipientId(),
                LocalDate.now().minusDays(2), marker("delete-expired"));
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        double beforeDelete = stock(actor.senderId());

        Response response = relocations.deleteRelocationRaw(actor.role(), sent.getId(), actor.senderId());
        assertDateLimit(response);
        RelocationResponse still = relocations.findHistoryByDescription(UserRole.ADMIN, actor.senderId(),
                RelocationJournalQuery.Perspective.SENT, sent.getDescription());
        assertThat(still).isNotNull();
        assertThat(still.getId()).isEqualTo(sent.getId());
        assertThat(still.getDate()).isEqualTo(sent.getDate());
        assertThat(stock(actor.senderId())).isCloseTo(beforeDelete, offset(0.01));
    }

    @Test(dataProvider = "unrestrictedActors")
    @TestCaseId(value = "TC-REL-DATE-014", roles = BusinessRole.UNIT_KOMIRNIK)
    @Description("Неадмін на локації з іншим parent або без parent створює давнє переміщення.")
    public void nonTsukParentCanCreateOldRelocation(Actor actor) {
        identify(actor);
        assertOutsideTsukHierarchy(actor);
        LocalDate oldDate = LocalDate.now().minusDays(30);

        RelocationResponse created = send(actor.role(), actor.senderId(), actor.recipientId(),
                oldDate, marker("unrestricted-create-old"));

        assertThat(created.getDate()).isEqualTo(oldDate);
        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);
    }

    @Test(dataProvider = "unrestrictedActors")
    @TestCaseId(value = "TC-REL-DATE-015", roles = BusinessRole.UNIT_KOMIRNIK)
    @Description("Неадмін на локації з іншим parent або без parent редагує давнє переміщення.")
    public void nonTsukParentCanEditOldRelocation(Actor actor) {
        identify(actor);
        assertOutsideTsukHierarchy(actor);
        LocalDate oldDate = LocalDate.now().minusDays(30);
        RelocationResponse sent = send(UserRole.ADMIN, actor.senderId(), actor.recipientId(),
                oldDate, marker("unrestricted-edit-old"));
        String changedDescription = marker("unrestricted-edited-old");

        Response response = relocations.editSendRaw(actor.role(), sent.getId(), actor.senderId(),
                editRequest(oldDate, changedDescription));
        assertThat(response.statusCode()).as(response.asString()).isBetween(200, 299);
        RelocationResponse updated = response.as(RelocationResponse.class);

        assertThat(updated.getId()).isEqualTo(sent.getId());
        assertThat(updated.getDate()).isEqualTo(oldDate);
        assertThat(updated.getDescription()).isEqualTo(changedDescription);
        assertThat(updated.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    @Test(dataProvider = "unrestrictedDeleters")
    @TestCaseId(value = "TC-REL-DATE-016", roles = BusinessRole.UNIT_KOMIRNIK)
    @Description("Неадмін на локації з іншим parent або без parent видаляє давнє завершене переміщення.")
    public void nonTsukParentCanDeleteOldRelocation(Actor actor) {
        identify(actor);
        assertOutsideTsukHierarchy(actor);
        LocalDate oldDate = LocalDate.now().minusDays(30);
        double beforeSend = stock(actor.senderId());
        RelocationResponse sent = send(UserRole.ADMIN, actor.senderId(), actor.externalRecipientId(),
                oldDate, marker("unrestricted-delete-old"));
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        Response response = relocations.deleteRelocationRaw(actor.role(), sent.getId(), actor.senderId());

        assertThat(response.statusCode()).as(response.asString()).isIn(200, 204);
        assertThat(relocations.findHistoryByDescription(UserRole.ADMIN, actor.senderId(),
                RelocationJournalQuery.Perspective.SENT, sent.getDescription())).isNull();
        assertThat(stock(actor.senderId())).isCloseTo(beforeSend, offset(0.01));
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-017",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK},
            locationProfiles = {LocationProfile.TSUK_PRODUCTION, LocationProfile.TSUK_WARENHAUSE})
    @Description("Старе переміщення між локаціями TSUK-ієрархії можна прийняти.")
    public void tsukRecipientCanAlwaysAcceptOldRelocation() {
        LocalDate oldDate = LocalDate.now(java.time.ZoneId.of("Europe/Kyiv")).minusDays(30);
        LocalDate receivedDate = oldDate.plusDays(5);
        RelocationResponse sent = send(UserRole.ADMIN,
                tsukProduction.senderId(), tsukWarehouse.senderId(),
                oldDate, marker("tsuk-accept-old"));
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse accepted = relocations.resolve(
                tsukWarehouse.role(), sent.getId(), tsukWarehouse.senderId(), RelocationState.FINISHED,
                "old relocation accepted with selected receipt date", receivedDate);

        assertThat(accepted.getId()).isEqualTo(sent.getId());
        assertThat(accepted.getDate()).isEqualTo(oldDate);
        assertThat(accepted.getReceivedAt().atZone(java.time.ZoneId.of("Europe/Kyiv")).toLocalDate())
                .isEqualTo(receivedDate);
        assertThat(accepted.getState()).isEqualTo(RelocationState.FINISHED);
    }

    @Test(dataProvider = "adminDates")
    @TestCaseId("TC-REL-DATE-007")
    @Description("Адміністратор створює переміщення з датою вчора і позавчора.")
    public void adminCanCreateRegardlessOfDate(LocalDate issueDate) {
        Allure.parameter("issueDate", issueDate);
        RelocationResponse created = send(UserRole.ADMIN, tsukProduction.senderId(), tsukProduction.recipientId(),
                issueDate, marker("admin-create"));
        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(created.getDate()).isEqualTo(issueDate);
    }

    @Test(dataProvider = "adminDates")
    @TestCaseId("TC-REL-DATE-008")
    @Description("Адміністратор редагує переміщення з датою вчора і позавчора.")
    public void adminCanEditRegardlessOfDate(LocalDate issueDate) {
        Allure.parameter("issueDate", issueDate);
        RelocationResponse sent = send(UserRole.ADMIN, tsukProduction.senderId(), tsukProduction.recipientId(),
                issueDate, marker("admin-edit"));
        String changedDescription = marker("admin-edited");
        RelocationResponse updated = relocations.editSend(UserRole.ADMIN, sent.getId(), tsukProduction.senderId(),
                editRequest(issueDate, changedDescription));
        assertThat(updated.getId()).isEqualTo(sent.getId());
        assertThat(updated.getDate()).isEqualTo(issueDate);
        assertThat(updated.getDescription()).isEqualTo(changedDescription);
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(updated.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    @Test(dataProvider = "adminDates")
    @TestCaseId("TC-REL-DATE-009")
    @Description("Адміністратор видаляє завершене переміщення з датою вчора і позавчора.")
    public void adminCanDeleteRegardlessOfDate(LocalDate issueDate) {
        Allure.parameter("issueDate", issueDate);
        double beforeSend = stock(tsukWarehouse.senderId());
        RelocationResponse sent = send(UserRole.ADMIN, tsukWarehouse.senderId(), tsukWarehouse.externalRecipientId(),
                issueDate, marker("admin-delete"));
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        Response response = relocations.deleteRelocationRaw(UserRole.ADMIN, sent.getId(), tsukWarehouse.senderId());
        assertThat(response.statusCode()).as(response.asString()).isIn(200, 204);
        assertThat(relocations.findHistoryByDescription(UserRole.ADMIN, tsukWarehouse.senderId(),
                RelocationJournalQuery.Perspective.SENT, sent.getDescription())).isNull();
        assertThat(stock(tsukWarehouse.senderId())).isCloseTo(beforeSend, offset(0.01));
    }

    @Test(dataProvider = "allNonAdminActors")
    @TestCaseId(value = "TC-REL-DATE-010",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін із правом видачі не може створити переміщення з датою завтра.")
    public void nonAdminCannotCreateFutureSend(Actor actor) {
        identify(actor);
        double before = stock(actor.senderId());
        Response response = relocations.sendRaw(actor.role(), sendRequest(
                actor.senderId(), actor.recipientId(), LocalDate.now().plusDays(1),
                marker("create-future")));
        returnUnexpectedSend(response, actor.senderId(), actor.recipientId());
        assertInvalidDate(response);
        assertThat(stock(actor.senderId())).isCloseTo(before, offset(0.01));
    }

    @Test
    @TestCaseId("TC-REL-DATE-011")
    @Description("Адміністратор також не може створити переміщення з датою завтра.")
    public void adminCannotCreateFutureSend() {
        double before = stock(tsukProduction.senderId());
        Response response = relocations.sendRaw(UserRole.ADMIN, sendRequest(
                tsukProduction.senderId(), tsukProduction.recipientId(), LocalDate.now().plusDays(1),
                marker("admin-create-future")));
        returnUnexpectedSend(response, tsukProduction.senderId(), tsukProduction.recipientId());
        assertInvalidDate(response);
        assertThat(stock(tsukProduction.senderId())).isCloseTo(before, offset(0.01));
    }

    @Test(dataProvider = "allNonAdminActors")
    @TestCaseId(value = "TC-REL-DATE-012",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін із правом редагування не може змінити дату видачі на завтра.")
    public void nonAdminCannotEditSendToFutureDate(Actor actor) {
        identify(actor);
        assertCannotEditSendToFutureDate(actor, actor.role());
    }

    @Test
    @TestCaseId("TC-REL-DATE-013")
    @Description("Адміністратор також не може змінити дату видачі на завтра.")
    public void adminCannotEditSendToFutureDate() {
        assertCannotEditSendToFutureDate(tsukProduction, UserRole.ADMIN);
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-018", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.TSUK_WARENHAUSE)
    @Description("Комірник створює переміщення FP1 → FP2 з датою вчора в TSUK-ієрархії.")
    public void warehouseKeeperCanCreateYesterdayBetweenFlyPoints() {
        identifyFlyPointTransfer();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        RelocationResponse created = send(tsukWarehouse.role(), flyPoint1.getId(), flyPoint2.getId(),
                yesterday, marker("fp-create-yesterday"));

        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(created.getDate()).isEqualTo(yesterday);
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-019", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.TSUK_WARENHAUSE)
    @Description("Комірник не може створити переміщення FP1 → FP2 з датою позавчора в TSUK-ієрархії.")
    public void warehouseKeeperCannotCreateTwoDaysAgoBetweenFlyPoints() {
        identifyFlyPointTransfer();
        double before = stock(flyPoint1.getId());
        Response response = relocations.sendRaw(tsukWarehouse.role(), sendRequest(
                flyPoint1.getId(), flyPoint2.getId(), LocalDate.now().minusDays(2),
                marker("fp-create-expired")));

        assertDateLimit(response);
        assertThat(stock(flyPoint1.getId())).isCloseTo(before, offset(0.01));
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-020", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.TSUK_WARENHAUSE)
    @Description("Комірник редагує вчорашнє переміщення FP1 → FP2 у TSUK-ієрархії.")
    public void warehouseKeeperCanEditYesterdayBetweenFlyPoints() {
        identifyFlyPointTransfer();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RelocationResponse sent = send(UserRole.ADMIN, flyPoint1.getId(), flyPoint2.getId(),
                yesterday, marker("fp-edit-yesterday"));
        String changedDescription = marker("fp-edited-yesterday");

        RelocationResponse updated = relocations.editSend(
                tsukWarehouse.role(), sent.getId(), flyPoint1.getId(),
                editRequest(yesterday, changedDescription));

        assertThat(updated.getId()).isEqualTo(sent.getId());
        assertThat(updated.getDate()).isEqualTo(yesterday);
        assertThat(updated.getDescription()).isEqualTo(changedDescription);
        assertThat(updated.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(updated.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-021", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.TSUK_WARENHAUSE)
    @Description("Комірник не може передатувати старе переміщення FP1 → FP2 на сьогодні.")
    public void warehouseKeeperCannotEditTwoDaysAgoBetweenFlyPoints() {
        identifyFlyPointTransfer();
        LocalDate issueDate = LocalDate.now().minusDays(2);
        RelocationResponse sent = send(UserRole.ADMIN, flyPoint1.getId(), flyPoint2.getId(),
                issueDate, marker("fp-edit-expired"));
        double before = stock(flyPoint1.getId());

        Response response = relocations.editSendRaw(
                tsukWarehouse.role(), sent.getId(), flyPoint1.getId(),
                editRequest(LocalDate.now(), marker("fp-edited-late")));

        assertDateLimit(response);
        assertFlyPointRelocationUnchanged(sent, issueDate, before);
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-022", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.TSUK_WARENHAUSE)
    @Description("Комірник не може створити переміщення FP1 → FP2 з датою завтра.")
    public void warehouseKeeperCannotCreateFutureSendBetweenFlyPoints() {
        identifyFlyPointTransfer();
        double before = stock(flyPoint1.getId());
        Response response = relocations.sendRaw(tsukWarehouse.role(), sendRequest(
                flyPoint1.getId(), flyPoint2.getId(), LocalDate.now().plusDays(1),
                marker("fp-create-future")));
        returnUnexpectedSend(response, flyPoint1.getId(), flyPoint2.getId());

        assertInvalidDate(response);
        assertThat(stock(flyPoint1.getId())).isCloseTo(before, offset(0.01));
    }

    @Test
    @TestCaseId(value = "TC-REL-DATE-023", roles = BusinessRole.UNIT_KOMIRNIK,
            locationProfiles = LocationProfile.TSUK_WARENHAUSE)
    @Description("Комірник не може змінити дату переміщення FP1 → FP2 на завтра.")
    public void warehouseKeeperCannotEditSendToFutureDateBetweenFlyPoints() {
        identifyFlyPointTransfer();
        LocalDate today = LocalDate.now();
        RelocationResponse sent = send(UserRole.ADMIN, flyPoint1.getId(), flyPoint2.getId(),
                today, marker("fp-edit-future"));
        double before = stock(flyPoint1.getId());
        try {
            Response response = relocations.editSendRaw(
                    tsukWarehouse.role(), sent.getId(), flyPoint1.getId(),
                    editRequest(today.plusDays(1), marker("fp-edited-future")));

            assertInvalidDate(response);
            assertFlyPointRelocationUnchanged(sent, today, before);
        } finally {
            relocations.resolve(UserRole.ADMIN, sent.getId(), flyPoint2.getId(),
                    RelocationState.CANCELLED);
            relocations.resolve(UserRole.ADMIN, sent.getId(), flyPoint1.getId(),
                    RelocationState.RETURNED);
        }
    }

    private void assertCannotEditSendToFutureDate(Actor actor, UserRole editingRole) {
        LocalDate today = LocalDate.now();
        RelocationResponse sent = send(UserRole.ADMIN, actor.senderId(), actor.recipientId(),
                today, marker("edit-future"));
        double beforeEdit = stock(actor.senderId());
        try {
            Response response = relocations.editSendRaw(editingRole, sent.getId(), actor.senderId(),
                    editRequest(today.plusDays(1), marker("edited-future")));
            RelocationResponse unchanged = relocations.findInTransitById(
                    UserRole.ADMIN, actor.senderId(), sent.getId());
            assertThat(unchanged).isNotNull();
            assertThat(unchanged.getDate()).isEqualTo(today);
            assertThat(unchanged.getDescription()).isEqualTo(sent.getDescription());
            assertThat(unchanged.getItems().getFirst().getAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(8));
            assertThat(stock(actor.senderId())).isCloseTo(beforeEdit, offset(0.01));
            assertInvalidDate(response);
        } finally {
            relocations.resolve(UserRole.ADMIN, sent.getId(), actor.recipientId(),
                    RelocationState.CANCELLED);
            relocations.resolve(UserRole.ADMIN, sent.getId(), actor.senderId(),
                    RelocationState.RETURNED);
        }
    }

    private void assertFlyPointRelocationUnchanged(
            RelocationResponse sent, LocalDate expectedDate, double expectedStock) {
        RelocationResponse unchanged = relocations.findInTransitById(
                UserRole.ADMIN, flyPoint1.getId(), sent.getId());
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.getDate()).isEqualTo(expectedDate);
        assertThat(unchanged.getDescription()).isEqualTo(sent.getDescription());
        assertThat(unchanged.getItems().getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(stock(flyPoint1.getId())).isCloseTo(expectedStock, offset(0.01));
    }

    private void identifyFlyPointTransfer() {
        Allure.parameter("businessRole", tsukWarehouse.businessRole());
        Allure.parameter("workspaceId", tsukWarehouse.senderId());
        Allure.parameter("insideTsukHierarchy", true);
        Allure.parameter("senderKind", StorageKind.FLY_POINT);
        Allure.parameter("senderId", flyPoint1.getId());
        Allure.parameter("recipientKind", StorageKind.FLY_POINT);
        Allure.parameter("recipientId", flyPoint2.getId());
    }

    private Actor createActor(BusinessRole businessRole, UserRole slot, StorageResponse sender) {
        boolean insideTsukHierarchy = hasTsukAncestor(sender);
        String label = slot.name().toLowerCase().replace('_', '-');
        StorageResponse recipient = storages.createChildStorage(
                sender.getId(), "rel-date-" + label + "-recipient-");
        StorageResponse externalRecipient = storages.createExternalChildStorage(
                sender.getId(), "rel-date-" + label + "-external-");
        UserFixture.BusinessActor user = users.createBusinessActor(
                getPlaywrightSessionProvider(), businessRole, List.of(sender));
        apiExecutor.setSessionForRole(slot, user.username(), user.password());
        relocations.ensureStock(sender.getId(), resourceId, 200.0);
        Long parentId = sender.getParent() == null ? null : sender.getParent().getId();
        return new Actor(businessRole, slot, sender.getId(), recipient.getId(),
                externalRecipient.getId(), parentId, insideTsukHierarchy);
    }

    private boolean hasTsukAncestor(StorageResponse sender) {
        StorageResponse current = sender;
        Set<Long> visitedParentIds = new HashSet<>();
        while (current.getParent() != null) {
            Long parentId = current.getParent().getId();
            assertThat(visitedParentIds.add(parentId))
                    .as("Ієрархія локацій не повинна містити цикл; повторний parentId=%s", parentId)
                    .isTrue();
            if (tsukParentId.equals(parentId)) return true;
            current = storages.getById(UserRole.ADMIN, parentId);
        }
        return false;
    }

    private RelocationResponse send(UserRole role, Long senderId, Long recipientId,
                                    LocalDate issueDate, String description) {
        Response response = relocations.sendRaw(role, sendRequest(senderId, recipientId, issueDate, description));
        assertThat(response.statusCode()).as(response.asString()).isBetween(200, 299);
        return response.as(RelocationResponse.class);
    }

    private RelocationOutputRequest sendRequest(Long senderId, Long recipientId,
                                                LocalDate issueDate, String description) {
        return RelocationDataFactory.buildSendRequest(senderId, recipientId, resourceId, 8.0, description)
                .toBuilder().date(issueDate).build();
    }

    private RelocationOutputEditRequest editRequest(LocalDate date, String description) {
        return RelocationDataFactory.buildSendEditRequest(resourceId, 3.0, description)
                .toBuilder().date(date).build();
    }

    private double stock(Long storageId) {
        return relocations.getResourceStock(storageId, resourceId, UserRole.ADMIN);
    }

    private void assertOutsideTsukHierarchy(Actor actor) {
        assertThat(actor.insideTsukHierarchy())
                .as("У всьому ланцюжку предків senderId=%s не повинно бути TSUK id=%s",
                        actor.senderId(), tsukParentId)
                .isFalse();
    }

    private void assertDateLimit(Response response) {
        assertInvalidDate(response);
        assertThat(response.asString()).contains("1 дн");
    }

    private void assertInvalidDate(Response response) {
        assertThat(response.statusCode())
                .as("Видачу з недопустимою датою має бути відхилено; HTTP %s", response.statusCode())
                .isEqualTo(400);
        assertThat(response.jsonPath().getList("errors.field", String.class))
                .as("Помилка має стосуватися поля date: %s", response.asString())
                .contains("date");
    }

    private void returnUnexpectedSend(Response response, Long senderId, Long recipientId) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) return;
        Long relocationId = response.as(RelocationResponse.class).getId();
        relocations.resolve(UserRole.ADMIN, relocationId, recipientId, RelocationState.CANCELLED);
        relocations.resolve(UserRole.ADMIN, relocationId, senderId, RelocationState.RETURNED);
    }

    private static void identify(Actor actor) {
        Allure.parameter("businessRole", actor.businessRole());
        Allure.parameter("senderId", actor.senderId());
        Allure.parameter("parentId", actor.parentId());
        Allure.parameter("insideTsukHierarchy", actor.insideTsukHierarchy());
    }

    private static String marker(String label) {
        return "rel-date-" + label + "-" + UUID.randomUUID();
    }

    private record Actor(BusinessRole businessRole, UserRole role, Long senderId,
                         Long recipientId, Long externalRecipientId, Long parentId,
                         boolean insideTsukHierarchy) {
        @Override
        public String toString() {
            return businessRole.name();
        }
    }
}
