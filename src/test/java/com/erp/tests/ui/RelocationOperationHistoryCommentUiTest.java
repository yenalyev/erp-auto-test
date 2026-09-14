package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.request.RelocationInputRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Notes in operation history UI")
public class RelocationOperationHistoryCommentUiTest extends BaseUITest {

    private RelocationFixture relocations;
    private ResourceFixture resources;
    private long storageId;
    private long supplierId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        relocations = new RelocationFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        relocations.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
    }

    @TestCaseId("TC-UI-REL-HIST-CMT-001")
    @Test
    @Story("Edited relocation note is shown in the Comment column")
    @Severity(SeverityLevel.CRITICAL)
    public void editedReceiveNoteAppearsInHistoryCommentColumn() {
        ResourceResponse resource = resources.createUniqueResource("ui-rel-history-note-");
        String beforeNote = "Примітка до редагування " + UUID.randomUUID();
        String afterNote = "Оновлена примітка " + UUID.randomUUID();
        String batch = RelocationDataFactory.uniqueBatchNumber();
        RelocationInputRequest request = RelocationDataFactory.buildReceiveRequest(
                supplierId, storageId, resource.getId(), 3.0, batch)
                .toBuilder().description(beforeNote).build();
        Response response = apiExecutor.executeRelocationReceive(request, UserRole.OWNER_1);
        assertThat(response.statusCode()).isBetween(200, 299);
        RelocationResponse received = response.as(RelocationResponse.class);

        injectRoleSession(UserRole.ADMIN, storageId);
        OperationHistoryPage history = new OperationHistoryPage(page).open();
        assertThat(history.resourceOperationHasComment(resource.getName(), "Отримано", beforeNote))
                .as("Original note in operation history")
                .isTrue();

        relocations.editExternalReceive(UserRole.ADMIN, received.getId(), storageId,
                RelocationDataFactory.buildReceiveEditRequest(
                        resource.getId(), 3.0, batch, afterNote));
        history.open();
        assertThat(history.resourceOperationHasComment(resource.getName(), "Отримано", afterNote))
                .as("Updated note in the Comment column")
                .isTrue();
        assertThat(history.resourceOperationHasComment(resource.getName(), "Отримано", beforeNote))
                .as("Old note must disappear from the existing row")
                .isFalse();
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
}
