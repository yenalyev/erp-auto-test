package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.request.RelocationOutputEditRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** Date-based write window for non-admin personas and the administrator exemption. */
@Epic("Relocation")
@Feature("One-day relocation write limit")
public class RelocationOneDayLimitTest extends BaseFunctionalTest {

    private static final UserRole KEEPER_SLOT = UserRole.CREW_READ;
    private RelocationFixture relocations;
    private StorageFixture storages;
    private LocationProfileFixture locationProfiles;
    private UserFixture users;
    private Long resourceId;
    private Actor owner;
    private Actor keeper;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareActors() {
        relocations = new RelocationFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        relocations.prepareContext();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);

        long ownerStorage = ConfigProvider.getOwner1StorageId();
        owner = new Actor(BusinessRole.BUSINESS_UNIT_OWNER, UserRole.OWNER_1,
                ownerStorage, ConfigProvider.getOwner2StorageId(), null);

        StorageResponse keeperStorage = locationProfiles
                .create(LocationProfile.BATTALION_WARENHAUSE_UNIT, 1).locations().getFirst();
        StorageResponse keeperRecipient = storages.createChildStorage(
                keeperStorage.getId(), "rel-date-recipient-");
        StorageResponse keeperExternal = storages.createExternalChildStorage(
                keeperStorage.getId(), "rel-date-keeper-external-");
        UserFixture.BusinessActor keeperUser = users.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.UNIT_KOMIRNIK, List.of(keeperStorage));
        apiExecutor.setSessionForRole(KEEPER_SLOT, keeperUser.username(), keeperUser.password());
        keeper = new Actor(BusinessRole.UNIT_KOMIRNIK, KEEPER_SLOT,
                keeperStorage.getId(), keeperRecipient.getId(), keeperExternal.getId());
        relocations.ensureStock(keeperStorage.getId(), resourceId, 200.0);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupActors() {
        apiExecutor.evictSessionForRole(KEEPER_SLOT);
        if (users != null) users.deactivateTrackedUsers();
        if (storages != null && !TestArtifactCleanup.shouldSkipApiCleanup()) {
            storages.deactivateTrackedStorages(UserRole.ADMIN);
        }
        if (locationProfiles != null && !TestArtifactCleanup.shouldSkipApiCleanup()) {
            locationProfiles.cleanup();
        }
    }

    @DataProvider(name = "nonAdminIssuers")
    public Object[][] nonAdminIssuers() {
        return new Object[][]{{owner}, {keeper}};
    }

    @DataProvider(name = "nonAdminDeleters")
    public Object[][] nonAdminDeleters() {
        return new Object[][]{{keeper}};
    }

    @DataProvider(name = "adminDates")
    public Object[][] adminDates() {
        return new Object[][]{
                {LocalDate.now().minusDays(1)},
                {LocalDate.now().minusDays(2)}
        };
    }

    @Test(dataProvider = "nonAdminIssuers")
    @TestCaseId(value = "TC-REL-DATE-001",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін із правом видачі може створити переміщення з датою вчора.")
    public void nonAdminCanCreateYesterday(Actor actor) {
        identify(actor);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RelocationResponse created = send(actor.role(), actor.senderId(), actor.recipientId(),
                yesterday, marker("create-yesterday"));
        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(created.getDate()).isEqualTo(yesterday);
    }

    @Test(dataProvider = "nonAdminIssuers")
    @TestCaseId(value = "TC-REL-DATE-002",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін не може створити переміщення з датою позавчора; залишок не змінюється.")
    public void nonAdminCannotCreateTwoDaysAgo(Actor actor) {
        identify(actor);
        double before = stock(actor.senderId());
        RelocationOutputRequest request = sendRequest(actor.senderId(), actor.recipientId(),
                LocalDate.now().minusDays(2), marker("create-expired"));
        Response response = relocations.sendRaw(actor.role(), request);
        assertDateLimit(response);
        assertThat(stock(actor.senderId())).isCloseTo(before, offset(0.01));
    }

    @Test(dataProvider = "nonAdminIssuers")
    @TestCaseId(value = "TC-REL-DATE-003",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін із правом на відправника може редагувати вчорашню видачу в дорозі.")
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

    @Test(dataProvider = "nonAdminIssuers")
    @TestCaseId(value = "TC-REL-DATE-004",
            roles = {BusinessRole.BUSINESS_UNIT_OWNER, BusinessRole.UNIT_KOMIRNIK})
    @Description("Неадмін не може редагувати видачу з датою позавчора, навіть задавши сьогоднішню дату.")
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

    @Test(dataProvider = "nonAdminDeleters")
    @TestCaseId(value = "TC-REL-DATE-005",
            roles = BusinessRole.UNIT_KOMIRNIK)
    @Description("Неадмін із правом на відправника видаляє завершену видачу з датою вчора.")
    public void nonAdminCanDeleteYesterday(Actor actor) {
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

    @Test(dataProvider = "nonAdminDeleters")
    @TestCaseId(value = "TC-REL-DATE-006",
            roles = BusinessRole.UNIT_KOMIRNIK)
    @Description("Неадмін не може видалити завершену видачу з датою позавчора.")
    public void nonAdminCannotDeleteTwoDaysAgo(Actor actor) {
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

    @Test(dataProvider = "adminDates")
    @TestCaseId("TC-REL-DATE-007")
    @Description("Адміністратор створює переміщення з датою вчора і позавчора.")
    public void adminCanCreateRegardlessOfDate(LocalDate issueDate) {
        Allure.parameter("issueDate", issueDate);
        RelocationResponse created = send(UserRole.ADMIN, owner.senderId(), owner.recipientId(),
                issueDate, marker("admin-create"));
        assertThat(created.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(created.getDate()).isEqualTo(issueDate);
    }

    @Test(dataProvider = "adminDates")
    @TestCaseId("TC-REL-DATE-008")
    @Description("Адміністратор редагує переміщення з датою вчора і позавчора.")
    public void adminCanEditRegardlessOfDate(LocalDate issueDate) {
        Allure.parameter("issueDate", issueDate);
        RelocationResponse sent = send(UserRole.ADMIN, owner.senderId(), owner.recipientId(),
                issueDate, marker("admin-edit"));
        String changedDescription = marker("admin-edited");
        RelocationResponse updated = relocations.editSend(UserRole.ADMIN, sent.getId(), owner.senderId(),
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
        double beforeSend = stock(keeper.senderId());
        RelocationResponse sent = send(UserRole.ADMIN, keeper.senderId(), keeper.externalRecipientId(),
                issueDate, marker("admin-delete"));
        assertThat(sent.getState()).isEqualTo(RelocationState.AUTO_FINISHED);

        Response response = relocations.deleteRelocationRaw(UserRole.ADMIN, sent.getId(), keeper.senderId());
        assertThat(response.statusCode()).as(response.asString()).isIn(200, 204);
        assertThat(relocations.findHistoryByDescription(UserRole.ADMIN, keeper.senderId(),
                RelocationJournalQuery.Perspective.SENT, sent.getDescription())).isNull();
        assertThat(stock(keeper.senderId())).isCloseTo(beforeSend, offset(0.01));
    }

    @Test(dataProvider = "nonAdminIssuers")
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
        double before = stock(owner.senderId());
        Response response = relocations.sendRaw(UserRole.ADMIN, sendRequest(
                owner.senderId(), owner.recipientId(), LocalDate.now().plusDays(1),
                marker("admin-create-future")));
        returnUnexpectedSend(response, owner.senderId(), owner.recipientId());
        assertInvalidDate(response);
        assertThat(stock(owner.senderId())).isCloseTo(before, offset(0.01));
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
    }

    private static String marker(String label) {
        return "rel-date-" + label + "-" + UUID.randomUUID();
    }

    private record Actor(BusinessRole businessRole, UserRole role, Long senderId,
                         Long recipientId, Long externalRecipientId) {
        @Override
        public String toString() {
            return businessRole.name();
        }
    }
}
