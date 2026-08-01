package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.incident.IncidentDataFactory;
import com.erp.enums.IncidentResourceOperation;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.IncidentFixture;
import com.erp.models.response.RelocationIncidentResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Relocation")
@Feature("Incident UI — надзвичайна подія")
public class RelocationIncidentUITest extends BaseUITest {

    private static final String INCIDENT_CARD = "Надзвичайні події";
    private static final String RECEIVED_CARD = "Отримано";

    private IncidentFixture incidentFixture;
    private long storageId;
    private Long owner2Storage;
    private Long resourceId;
    private String resourceName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        incidentFixture = new IncidentFixture(testContext, apiExecutor);
        incidentFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        owner2Storage = ConfigProvider.getOwner2StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceName = resources.getFirst().getName().trim();
        injectRoleSession(UserRole.OWNER_1, storageId);
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }

    @Test
    @TestCaseId("TC-UI-INC-001")
    @Story("BC-INC-01 / BC-INC-20 / BC-INC-21 / BC-INC-50: create → Втрачено → details → delete")
    @Severity(SeverityLevel.CRITICAL)
    public void createViewAndDeleteIncidentViaUi() {
        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, 1.0, marker);

        RelocationPage relocationPage = new RelocationPage(page).open().openInTransitTab();
        assertThat(relocationPage.isCreateIncidentButtonVisibleInRow(marker))
                .as("Кнопка «Створити інцидент» на рядку В дорозі")
                .isTrue();

        relocationPage.clickCreateIncidentInRow(marker)
                .fillDescription(marker)
                .saveAndReturnToJournal();

        assertThat(relocationPage.isLostTabVisible())
                .as("Вкладка «Втрачено» доступна при incident::view")
                .isTrue();

        relocationPage.openLostTab();
        assertThat(relocationPage.isRowWithTextVisible(marker))
                .as("Після create рядок у вкладці Втрачено")
                .isTrue();

        relocationPage.openIncidentDetailsInRow(marker);
        assertThat(relocationPage.isIncidentDescriptionVisible(marker))
                .as("Деталі інциденту показують опис")
                .isTrue();

        relocationPage.deleteIncidentFromDetails();

        RelocationResponse restored = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, storageId, marker);
        assertThat(restored).as("Після delete relocation знову CREATED").isNotNull();
        assertThat(restored.getState()).isEqualTo(RelocationState.CREATED);
        assertThat(restored.getId()).isEqualTo(sent.getId());
    }

    @Test
    @TestCaseId("TC-UI-INC-002")
    @Story("BC-INC-12: incident write-off on «Історія операцій»")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після створення надзвичайної події (setup через API) Owner 1 відкриває
            «Історія операцій» (/history): видима картка «Надзвичайні події»,
            маркер «Надзвичайна подія: втрата», сума write-off для ресурсу.
            """)
    public void operationHistoryAfterIncidentUi() {
        double amount = 2.0;
        String marker = IncidentDataFactory.uniqueDescription();

        incidentFixture.sendAndCreateIncident(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, amount, marker);

        injectRoleSession(UserRole.OWNER_1, storageId);
        page = browserContext.newPage();

        OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
        assertThat(history.isLoaded()).isTrue();
        assertThat(history.isIncidentSummaryVisible())
                .as("Картка «Надзвичайні події»")
                .isTrue();
        assertThat(history.containsIncidentOperationMarker())
                .as("Маркер INCIDENT_WRITE_OFF / «Надзвичайна подія: втрата»")
                .isTrue();

        double cardAmount = history.getSummaryCardAmountForResource(INCIDENT_CARD, resourceName);
        assertThat(cardAmount)
                .as("Картка «Надзвичайні події» містить WRITE_OFF (втрачено) для %s", resourceName)
                .isGreaterThanOrEqualTo(amount - 0.01);

        history.attachScreenshot("TC-UI-INC-002 — incident history");
    }

    @Test
    @TestCaseId("TC-UI-INC-003")
    @Story("BC-INC-51: Lost tab показує кількість втраченого (= WRITE_OFF)")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після повної втрати (API): вкладка «Втрачено» у колонці ресурсів показує
            кількість WRITE_OFF (= sent при повній втраті), а не інший total.
            Історія «Надзвичайні події» містить той самий WRITE_OFF (≥ amount).
            """)
    public void lostTabAndHistoryShowWriteOffAmountAfterFullLoss() {
        String marker = IncidentDataFactory.uniqueDescription();
        double writeOffAmount = 3.0;

        incidentFixture.sendAndCreateIncident(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, writeOffAmount, marker);

        injectRoleSession(UserRole.OWNER_1, storageId);
        page = browserContext.newPage();

        RelocationPage relocationPage = new RelocationPage(page).open().openLostTab();
        assertThat(relocationPage.isRowWithTextVisible(marker)).isTrue();
        assertThat(relocationPage.getResourceAmountInRow(marker, resourceName))
                .as("Lost tab: ресурси = WRITE_OFF amount")
                .isCloseTo(writeOffAmount, within(0.01));

        page = browserContext.newPage();
        OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
        assertThat(history.getSummaryCardAmountForResource(INCIDENT_CARD, resourceName))
                .as("Історія: «Надзвичайні події» містить WRITE_OFF")
                .isGreaterThanOrEqualTo(writeOffAmount - 0.01);
    }

    @Test
    @TestCaseId("TC-UI-INC-PD-001")
    @Story("BC-INC-PD-50: UI часткова доставка → Втрачено → details")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Owner 1: В дорозі → Створити інцидент → radio «Часткова доставка» →
            склад отримувача → «Доставлено» < amount → Зберегти → вкладка Втрачено →
            деталі показують «Частково доставлено до {recipient}».
            """)
    public void createPartialDeliveryIncidentViaUi() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 5.0;
        double delivered = 2.0;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, sentAmount, marker);
        String recipientName = sent.getRecipient().getName();

        RelocationPage relocationPage = new RelocationPage(page).open().openInTransitTab();
        assertThat(relocationPage.isCreateIncidentButtonVisibleInRow(marker))
                .as("Кнопка «Створити інцидент» на рядку В дорозі")
                .isTrue();

        relocationPage.clickCreateIncidentInRow(marker)
                .selectPartialDelivery()
                .selectDeliveryStorage(recipientName)
                .setDeliveredAmount(resourceName, String.valueOf(delivered))
                .fillDescription(marker)
                .saveAndReturnToJournal();

        assertThat(relocationPage.isLostTabVisible())
                .as("Вкладка «Втрачено» після partial delivery")
                .isTrue();

        relocationPage.openLostTab();
        assertThat(relocationPage.isRowWithTextVisible(marker))
                .as("Рядок у вкладці Втрачено")
                .isTrue();

        RelocationIncidentResponse incident = incidentFixture.getIncident(UserRole.OWNER_1, sent.getId());
        assertThat(incident.getResources())
                .as("API: PARTIAL_DELIVERY після UI create")
                .anyMatch(r -> r.getOperation() == IncidentResourceOperation.PARTIAL_DELIVERY
                        && resourceId.equals(r.getResourceId()));
        assertThat(incident.getResources())
                .as("API: auto WRITE_OFF remainder після UI create")
                .anyMatch(r -> r.getOperation() == IncidentResourceOperation.WRITE_OFF
                        && resourceId.equals(r.getResourceId()));

        relocationPage.openIncidentDetailsInRow(marker);
        assertThat(relocationPage.isIncidentDescriptionVisible(marker))
                .as("Опис інциденту в діалозі")
                .isTrue();
        assertThat(relocationPage.isPartialDeliveryDetailsVisible(recipientName))
                .as("Колонка «Частково доставлено до %s»", recipientName)
                .isTrue();

        relocationPage.deleteIncidentFromDetails();

        RelocationResponse restored = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, storageId, marker);
        assertThat(restored).as("Після delete relocation знову CREATED").isNotNull();
        assertThat(restored.getState()).isEqualTo(RelocationState.CREATED);
    }

    @Test
    @TestCaseId("TC-UI-INC-PD-002")
    @Story("BC-INC-PD-51: UI історія після часткової доставки")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після API partial delivery:
            — Owner 1 / склад відправника: картка «Надзвичайні події» += remainder (INCIDENT_WRITE_OFF);
            — Owner 2 / склад отримувача: картка «Отримано» += delivered (ADDED).
            """)
    public void operationHistoryAfterPartialDeliveryUi() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 5.0;
        double delivered = 2.0;
        double expectedWriteOff = sentAmount - delivered;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, sentAmount, marker);
        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        injectRoleSession(UserRole.OWNER_1, storageId);
        page = browserContext.newPage();

        OperationHistoryPage senderHistory = new OperationHistoryPage(page).open().waitForLoaded();
        assertThat(senderHistory.isLoaded()).isTrue();
        assertThat(senderHistory.isIncidentSummaryVisible())
                .as("Картка «Надзвичайні події» на відправнику")
                .isTrue();
        assertThat(senderHistory.containsIncidentOperationMarker())
                .as("Маркер INCIDENT_WRITE_OFF / «Надзвичайна подія: втрата»")
                .isTrue();
        double incidentCard = senderHistory.getSummaryCardAmountForResource(INCIDENT_CARD, resourceName);
        assertThat(incidentCard)
                .as("Картка «Надзвичайні події» містить WRITE_OFF remainder для %s", resourceName)
                .isGreaterThanOrEqualTo(expectedWriteOff - 0.01);

        senderHistory.attachScreenshot("TC-UI-INC-PD-002 — sender history");

        injectRoleSession(UserRole.OWNER_2, owner2Storage);
        page = browserContext.newPage();

        OperationHistoryPage recipientHistory = new OperationHistoryPage(page).open().waitForLoaded();
        assertThat(recipientHistory.isLoaded()).isTrue();
        assertThat(recipientHistory.isSummaryCardVisible(RECEIVED_CARD))
                .as("Картка «Отримано» на отримувачі після PARTIAL_DELIVERY→ADDED")
                .isTrue();
        double receivedCard = recipientHistory.getSummaryCardAmountForResource(RECEIVED_CARD, resourceName);
        assertThat(receivedCard)
                .as("Картка «Отримано» містить delivered для %s", resourceName)
                .isGreaterThanOrEqualTo(delivered - 0.01);

        recipientHistory.attachScreenshot("TC-UI-INC-PD-002 — recipient history");
    }

    @Test
    @TestCaseId("TC-UI-INC-PD-003")
    @Issue("CPMA-652")
    @Story("BC-INC-PD-52: Lost tab показує лише втрачене (remainder), не sent")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після partial delivery (sent=10, delivered=4 → WRITE_OFF=6):
            вкладка «Втрачено» у колонці ресурсів показує 6 (втрачено), НЕ 10 (відправлено).
            Історія «Надзвичайні події» містить WRITE_OFF remainder (≥ 6).

            Відомий дефект UI CPMA-652: Lost journal рендерить relocation.items.amount (sent),
            а не WRITE_OFF з інциденту — тест червоний до фіксу в tk-ui.
            """)
    public void lostTabShowsOnlyWriteOffNotSentAfterPartialDelivery() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 10.0;
        double delivered = 4.0;
        double expectedLost = sentAmount - delivered;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, sentAmount, marker);
        incidentFixture.createPartialDeliveryIncident(
                UserRole.OWNER_1, sent, owner2Storage, resourceId, delivered, marker);

        injectRoleSession(UserRole.OWNER_1, storageId);
        page = browserContext.newPage();

        RelocationPage relocationPage = new RelocationPage(page).open().openLostTab();
        assertThat(relocationPage.isRowWithTextVisible(marker)).isTrue();

        double displayed = relocationPage.getResourceAmountInRow(marker, resourceName);
        assertThat(displayed)
                .as("Lost tab: ресурси = WRITE_OFF remainder, не sent")
                .isCloseTo(expectedLost, within(0.01));
        assertThat(displayed)
                .as("Lost tab не повинен показувати повну кількість переміщення")
                .isNotCloseTo(sentAmount, within(0.01));

        page = browserContext.newPage();
        OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
        assertThat(history.getSummaryCardAmountForResource(INCIDENT_CARD, resourceName))
                .as("Історія: «Надзвичайні події» містить WRITE_OFF remainder")
                .isGreaterThanOrEqualTo(expectedLost - 0.01);
    }

    @Test
    @TestCaseId("TC-UI-INC-PD-004")
    @Story("BC-INC-PD-53: UI delivered > sent — Save disabled + «Перевищує…»")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Часткова доставка: delivered > sent → повідомлення «Перевищує кількість у переміщенні»,
            кнопка «Зберегти» disabled; сторінка create-incident не залишається після спроби submit.
            Симетрично API TC-INC-PD-009.
            """)
    public void partialDeliveryExceedingSentDisablesSave() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 5.0;
        double delivered = 8.0;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, sentAmount, marker);
        String recipientName = sent.getRecipient().getName();

        var form = new RelocationPage(page).open().openInTransitTab()
                .clickCreateIncidentInRow(marker)
                .selectPartialDelivery()
                .selectDeliveryStorage(recipientName)
                .setDeliveredAmount(resourceName, String.valueOf(delivered))
                .fillDescription(marker);

        assertThat(form.isExceedingMessageVisible())
                .as("Повідомлення «Перевищує кількість у переміщенні»")
                .isTrue();
        assertThat(form.isSaveDisabled())
                .as("Зберегти disabled при delivered > sent")
                .isTrue();
        assertThat(form.isOnCreateIncidentPage())
                .as("Залишаємось на create-incident")
                .isTrue();

        form.attachScreenshot("TC-UI-INC-PD-004 — exceeding delivered");
    }

    @Test
    @TestCaseId("TC-UI-INC-PD-005")
    @Story("BC-INC-PD-54: UI negative delivered — Save disabled")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Часткова доставка: delivered < 0 → «Зберегти» disabled
            (selectedRows вимагає amount > 0; min=0 на input).
            Інцидент не створюється; relocation лишається CREATED.
            Симетрично API TC-INC-PD-010.
            """)
    public void partialDeliveryNegativeAmountDisablesSave() {
        String marker = IncidentDataFactory.uniqueDescription();
        double sentAmount = 4.0;

        RelocationResponse sent = incidentFixture.relocation().createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2Storage, resourceId, sentAmount, marker);
        String recipientName = sent.getRecipient().getName();

        var form = new RelocationPage(page).open().openInTransitTab()
                .clickCreateIncidentInRow(marker)
                .selectPartialDelivery()
                .selectDeliveryStorage(recipientName)
                .setDeliveredAmount(resourceName, "-1")
                .fillDescription(marker);

        assertThat(form.isSaveDisabled())
                .as("Зберегти disabled при negative delivered")
                .isTrue();
        assertThat(form.isOnCreateIncidentPage())
                .as("Залишаємось на create-incident")
                .isTrue();

        RelocationResponse stillInTransit = incidentFixture.relocation().findInTransitByDescription(
                UserRole.OWNER_1, storageId, marker);
        assertThat(stillInTransit).as("Relocation лишається CREATED").isNotNull();
        assertThat(stillInTransit.getState()).isEqualTo(RelocationState.CREATED);

        form.attachScreenshot("TC-UI-INC-PD-005 — negative delivered");
    }
}
