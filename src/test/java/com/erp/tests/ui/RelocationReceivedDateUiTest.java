package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.LocationFeature;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.RelocationCreateInputPage;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
import com.erp.tests.support.ReceivedDateJournalLookup;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Receive date picker")
public class RelocationReceivedDateUiTest extends BaseUITest {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private RelocationFixture fixture;
    private ResourceFixture resources;
    private StorageFixture storages;
    private Long senderId;
    private Long recipientId;
    private String recipientName;
    private Long resourceId;
    private Long supplierId;
    private String resourceName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new RelocationFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        storages = new StorageFixture(testContext, apiExecutor);
        fixture.fetchSharedUnit(3);
        fixture.fetchSharedResourceCategory();
        fixture.setupSharedResourceList(3);
        senderId = storages.createStorage(StorageDataFactory.childStorage(
                ConfigProvider.getOwner1StorageId(), "ui-received-date-sender-")
                .features(Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT))
                .build()).getId();
        var recipient = storages.createStorage(StorageDataFactory.childStorage(
                ConfigProvider.getOwner2StorageId(), "ui-received-date-recipient-")
                .features(Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT))
                .build());
        recipientId = recipient.getId();
        recipientName = recipient.getName();
        supplierId = storages.createStorage(StorageDataFactory.externalStorage(
                ConfigProvider.getOwner1StorageId(), "ui-received-date-external-")
                .features(Set.of(LocationFeature.RELOCATIONS, LocationFeature.EQUIPMENT))
                .build()).getId();
        ResourceResponse resource = resources.createUniqueResource("ui-received-date-");
        resourceId = resource.getId();
        resourceName = resource.getName();

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl().replaceFirst("https?://", "").split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + recipientId + "');");
    }

    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        super.testSetup();
        new RelocationPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.selectWorkspaceByName(recipientName);
        assertThat(sidebar.getSelectedLocationName()).contains(recipientName);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupReceivedDateUiStorages() {
        if (storages != null) {
            storages.deactivateTrackedStorages(UserRole.ADMIN);
        }
    }

    @Test
    @TestCaseId("TC-UI-REL-RECEIVED-DATE-002")
    @Description("Форма «Отримано» зберігає обрану вчорашню дату в журналі, а не сьогоднішню.")
    public void receiveFormUsesChosenDateInReceivedJournal() {
        LocalDate selected = LocalDate.now(KYIV).minusDays(1);
        String marker = "ui-receive-selected-date-" + System.nanoTime();
        String supplierName = storages.getById(UserRole.ADMIN, supplierId).getName();

        RelocationCreateInputPage form = new RelocationPage(page).open().clickReceive();
        form.fillDate(selected.toString())
                .selectSourceByName(supplierName)
                .selectResourceByName(resourceName)
                .fillQuantity(0, "1")
                .fillDescription(marker)
                .submit();
        page.waitForURL("**/relocations");

        RelocationResponse received = ReceivedDateJournalLookup.byDescription(
                fixture, UserRole.ADMIN, recipientId, resourceId, marker);
        assertThat(received).as("Отримання після submit форми").isNotNull();
        assertThat(received.getReceivedAt()).isNotNull();
        assertThat(received.getReceivedAt().atZone(KYIV).toLocalDate()).isEqualTo(selected);
    }

    @Test
    @TestCaseId("TC-UI-REL-RECEIVED-DATE-003")
    @Description("Форма видачі назовні зберігає обрану дату в журналі «Отримано» отримувача.")
    public void externalSendFormUsesChosenDateInReceivedJournal() {
        LocalDate selected = LocalDate.now(KYIV).minusDays(1);
        String marker = "ui-send-external-selected-date-" + System.nanoTime();
        Long externalId = supplierId;
        String externalName = storages.getById(UserRole.ADMIN, externalId).getName();
        fixture.ensureStock(recipientId, resourceId, 5.0);

        RelocationCreateOutputPage form = new RelocationPage(page).open().clickSend();
        form.fillDate(selected.toString())
                .selectRecipientByLabel(externalName)
                .selectOutputResourceByName(resourceName)
                .fillOutputQuantity("1")
                .fillInvoiceIssuerDefaults()
                .fillDescription(marker)
                .confirmSend();

        RelocationResponse received = ReceivedDateJournalLookup.byDescription(
                fixture, UserRole.ADMIN, externalId, resourceId, marker);
        assertThat(received).as("Зовнішня видача після submit форми").isNotNull();
        assertThat(received.getReceivedAt()).isNotNull();
        assertThat(received.getReceivedAt().atZone(KYIV).toLocalDate()).isEqualTo(selected);
    }

    @Test
    @TestCaseId("TC-UI-REL-RECEIVED-DATE-001")
    @Description("«Прийняти»: date picker має межі від «Надіслано» до сьогодні; обрана дата потрапляє в «Отримано».")
    public void recipientSelectsReceiptDateWithinSendAndToday() {
        LocalDate today = LocalDate.now(KYIV);
        LocalDate sentDate = today.minusDays(2);
        LocalDate selected = today.minusDays(1);
        String marker = "ui-accept-date-" + System.nanoTime();
        fixture.ensureStock(senderId, resourceId, 5.0);
        RelocationOutputRequest send = RelocationDataFactory.buildSendRequest(
                        senderId, recipientId, resourceId, 1.0, marker)
                .toBuilder().date(sentDate).build();
        Response sentResponse = fixture.sendRaw(UserRole.ADMIN, send);
        assertThat(sentResponse.statusCode()).as(sentResponse.asString()).isEqualTo(200);

        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.selectPageSize(500);
        assertThat(journal.isRowWithTextVisible(marker)).as("Нове переміщення у «В дорозі»").isTrue();
        journal.openAcceptDialog(marker);

        assertThat(journal.acceptDateInputType()).as("Дейтпікер як у формі «Отримано»").isEqualTo("date");
        assertThat(journal.acceptDateMin()).as("Нижня межа — «Надіслано»").isEqualTo(sentDate.toString());
        assertThat(journal.acceptDateMax()).as("Верхня межа — сьогодні").isEqualTo(today.toString());
        assertThat(journal.acceptDateValue()).as("Дата за замовчуванням").isEqualTo(today.toString());

        journal.fillAcceptDate(sentDate.minusDays(1).toString());
        assertThat(journal.isAcceptConfirmationEnabled()).as("Раніше «Надіслано»").isFalse();
        journal.fillAcceptDate(today.plusDays(1).toString());
        assertThat(journal.isAcceptConfirmationEnabled()).as("Пізніше сьогодні").isFalse();
        journal.fillAcceptDate(sentDate.toString());
        assertThat(journal.isAcceptConfirmationEnabled()).as("Сама дата відправлення дозволена").isTrue();
        journal.fillAcceptDate(today.toString());
        assertThat(journal.isAcceptConfirmationEnabled()).as("Сьогодні дозволено").isTrue();

        journal.fillAcceptDate(selected.toString()).confirmAcceptDialog();
        journal.open().openInTransitTab();
        assertThat(journal.isRowWithTextVisible(marker)).as("Прийнятий запис зник з «В дорозі»").isFalse();
        RelocationResponse received = ReceivedDateJournalLookup.byDescription(
                fixture, UserRole.ADMIN, recipientId, resourceId, marker);
        assertThat(received).as("Прийнятий запис у «Отримано»").isNotNull();
        assertThat(received.getDate()).as("«Надіслано» не змінюється").isEqualTo(sentDate);
        assertThat(received.getReceivedAt()).isNotNull();
        assertThat(received.getReceivedAt().atZone(KYIV).toLocalDate())
                .as("«Отримано» дорівнює обраній даті")
                .isEqualTo(selected);
    }
}
