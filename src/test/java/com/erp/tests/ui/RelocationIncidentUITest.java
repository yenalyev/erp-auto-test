package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.incident.IncidentDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.IncidentFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Incident UI — надзвичайна подія")
public class RelocationIncidentUITest extends BaseUITest {

    private static final String INCIDENT_CARD = "Надзвичайні події";

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
                .as("Картка «Надзвичайні події» містить write-off для %s", resourceName)
                .isGreaterThanOrEqualTo(amount - 0.01);

        history.attachScreenshot("TC-UI-INC-002 — incident history");
    }
}
