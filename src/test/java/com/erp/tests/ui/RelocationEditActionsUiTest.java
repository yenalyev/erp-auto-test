package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.RelocationResponse;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: кнопки Редагувати/Видалити для зовнішніх переміщень (Admin).
 */
@Slf4j
@Epic("Relocation")
@Feature("Relocation edit actions UI")
@Story("Edit/Delete buttons for external receives")
public class RelocationEditActionsUiTest extends BaseUITest {

    private RelocationFixture relocationFixture;
    private long storageId;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    @Test(priority = 10)
    @TestCaseId("TC-EDIT_REL-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-EDIT_REL-001 AC-01: для Admin у табі «Отримано» на зовнішньому отриманні
            видимі кнопки «Редагувати» та «Видалити».
            """)
    public void adminSeesEditAndDeleteOnExternalReceive() {
        String batch = RelocationDataFactory.uniqueBatchNumber();
        RelocationResponse receive = relocationFixture.createExternalReceive(
                UserRole.ADMIN, storageId, resourceId, 3.0, batch);
        String rowMarker = receive.getInvoiceNumber() != null && !receive.getInvoiceNumber().isBlank()
                ? receive.getInvoiceNumber()
                : batch;

        RelocationPage journal = new RelocationPage(page).open().openReceivedTab();
        journal.attachScreenshot("TC-EDIT_REL-001 — received tab");

        assertThat(journal.isEditButtonVisibleInRow(rowMarker))
                .as("Кнопка «Редагувати» для зовнішнього отримання (%s)", rowMarker)
                .isTrue();
        assertThat(journal.isDeleteButtonVisibleInRow(rowMarker))
                .as("Кнопка «Видалити» для зовнішнього отримання (%s)", rowMarker)
                .isTrue();
    }
}
