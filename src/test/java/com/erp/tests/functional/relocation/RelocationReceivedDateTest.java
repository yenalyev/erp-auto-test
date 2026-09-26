package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.tests.support.ReceivedDateJournalLookup;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Received date in relocation journal")
public class RelocationReceivedDateTest extends BaseFunctionalTest {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private RelocationFixture fixture;
    private Long senderId;
    private Long recipientId;
    private Long supplierId;
    private Long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareRelocations() {
        fixture = new RelocationFixture(testContext, apiExecutor);
        fixture.fetchSharedUnit(3);
        fixture.fetchSharedResourceCategory();
        fixture.setupSharedResourceList(3);
        senderId = ConfigProvider.getOwner1StorageId();
        recipientId = ConfigProvider.getOwner2StorageId();
        supplierId = RelocationStockSeeder.resolveSupplierStorageId(apiExecutor, UserRole.ADMIN);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = resources.getFirst().getId();
    }

    @Test
    @TestCaseId("TC-REL-RECEIVED-DATE-001")
    @Description("Зовнішнє отримання з датою в минулому показує цю дату в «Отримано».")
    public void externalReceiveUsesSelectedDate() {
        LocalDate selected = LocalDate.now(KYIV).minusDays(3);
        String marker = "receive-date-" + System.nanoTime();
        RelocationInputRequest request = RelocationDataFactory.buildReceiveRequest(
                        supplierId, senderId, resourceId, 1.0, RelocationDataFactory.uniqueBatchNumber())
                .toBuilder().date(selected).description(marker).build();

        Response response = apiExecutor.executeRelocationReceive(request, UserRole.ADMIN);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        RelocationResponse created = response.as(RelocationResponse.class);
        assertReceivedOn(created, selected);

        RelocationResponse inJournal = ReceivedDateJournalLookup.byDescription(
                fixture, UserRole.ADMIN, senderId, resourceId, marker);
        assertThat(inJournal).as("Запис у журналі «Отримано»").isNotNull();
        assertReceivedOn(inJournal, selected);
    }

    @Test
    @TestCaseId("TC-REL-RECEIVED-DATE-002")
    @Description("Видача зовнішньому отримувачу показує дату видачі в «Отримано».")
    public void externalSendUsesSelectedDateAsReceivedDate() {
        fixture.ensureStock(senderId, resourceId, 5.0);
        LocalDate selected = LocalDate.now(KYIV).minusDays(2);
        Long externalId = supplierId;
        String marker = "send-external-date-" + System.nanoTime();
        RelocationOutputRequest request = RelocationDataFactory.buildSendRequest(
                        senderId, externalId, resourceId, 1.0, marker)
                .toBuilder().date(selected).build();

        Response response = fixture.sendRaw(UserRole.ADMIN, request);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        RelocationResponse created = response.as(RelocationResponse.class);
        assertThat(created.getState()).isEqualTo(RelocationState.AUTO_FINISHED);
        assertReceivedOn(created, selected);

        RelocationResponse inJournal = ReceivedDateJournalLookup.byDescription(
                fixture, UserRole.ADMIN, externalId, resourceId, marker);
        assertThat(inJournal).as("Зовнішня видача у журналі «Отримано» отримувача").isNotNull();
        assertReceivedOn(inJournal, selected);
    }

    @Test
    @TestCaseId("TC-REL-RECEIVED-DATE-003")
    @Description("Внутрішнє переміщення зберігає окремо дату надсилання та обрану дату прийому.")
    public void internalAcceptanceUsesSelectedDateWithoutChangingSendDate() {
        fixture.ensureStock(senderId, resourceId, 5.0);
        LocalDate sentDate = LocalDate.now(KYIV).minusDays(4);
        LocalDate receivedDate = sentDate.plusDays(2);
        String marker = "internal-received-date-" + System.nanoTime();
        RelocationOutputRequest send = RelocationDataFactory.buildSendRequest(
                        senderId, recipientId, resourceId, 1.0, marker)
                .toBuilder().date(sentDate).build();
        Response sentResponse = fixture.sendRaw(UserRole.ADMIN, send);
        assertThat(sentResponse.statusCode()).as(sentResponse.asString()).isEqualTo(200);
        RelocationResponse sent = sentResponse.as(RelocationResponse.class);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        RelocationResponse accepted = fixture.resolve(UserRole.ADMIN, sent.getId(), recipientId,
                RelocationState.FINISHED, "selected internal receipt date", receivedDate);
        assertThat(accepted.getDate()).isEqualTo(sentDate);
        assertReceivedOn(accepted, receivedDate);

        RelocationResponse inJournal = ReceivedDateJournalLookup.byDescription(
                fixture, UserRole.ADMIN, recipientId, resourceId, marker);
        assertThat(inJournal).as("Прийняте переміщення у журналі «Отримано»").isNotNull();
        assertThat(inJournal.getDate()).isEqualTo(sentDate);
        assertReceivedOn(inJournal, receivedDate);
    }

    private static void assertReceivedOn(RelocationResponse relocation, LocalDate expected) {
        assertThat(relocation.getReceivedAt()).as("receivedAt запису id=%s", relocation.getId()).isNotNull();
        assertThat(relocation.getReceivedAt().atZone(KYIV).toLocalDate())
                .as("Дата у колонці «Отримано» для id=%s", relocation.getId())
                .isEqualTo(expected);
    }
}
