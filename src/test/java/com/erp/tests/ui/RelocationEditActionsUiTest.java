package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.enums.RelocationState;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.RelocationPage;
import com.erp.pages.RelocationUpdateOutputPage;
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
import org.testng.annotations.BeforeMethod;
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
    private long owner2StorageId;
    private Long resourceId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
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

    @BeforeMethod(alwaysRun = true)
    public void ensureStockForSends() {
        if (relocationFixture != null && resourceId != null) {
            relocationFixture.ensureStock(storageId, resourceId, 50.0);
        }
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

    @Test(priority = 20)
    @TestCaseId("TC-UI-REL-018")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-EDIT_REL-007 AC-06: Owner на складі, з якого відправив, бачить олівець
            на «В дорозі» і відкриває «Редагування видачі».
            """)
    public void senderSeesEditOnInTransit() {
        String marker = "in-transit-edit-" + System.currentTimeMillis();
        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2StorageId, resourceId, 8.0, marker);
        assertThat(sent.getState()).isEqualTo(RelocationState.CREATED);

        reopenPageWithSession(UserRole.OWNER_1, storageId);
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.attachScreenshot("TC-UI-REL-018 — in transit");

        assertThat(journal.isEditButtonVisibleInRow(marker))
                .as("Олівець «Редагувати» для CREATED видачі (%s)", marker)
                .isTrue();

        RelocationUpdateOutputPage form = journal.clickEditSendInRow(marker);
        form.attachScreenshot("TC-UI-REL-018 — edit form");
    }

    @Test(priority = 21)
    @TestCaseId("TC-UI-REL-019")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-EDIT_REL-007 AC-06: отримувач на «В дорозі» бачить «Прийняти» / «Скасувати»,
            олівця немає.
            """)
    public void recipientDoesNotSeeEditOnInTransit() {
        String marker = "in-transit-recipient-" + System.currentTimeMillis();
        relocationFixture.createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2StorageId, resourceId, 8.0, marker);

        reopenPageWithSession(UserRole.OWNER_2, owner2StorageId);
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.attachScreenshot("TC-UI-REL-019 — recipient in transit");

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Рядок видачі на складі отримувача (%s)", marker)
                .isTrue();
        assertThat(journal.isAcceptButtonVisibleInRow(marker))
                .as("Кнопка «Прийняти» для отримувача")
                .isTrue();
        assertThat(journal.isCancelButtonVisibleInRow(marker))
                .as("Кнопка «Скасувати» для отримувача")
                .isTrue();
        assertThat(journal.isEditButtonVisibleInRow(marker))
                .as("Олівця «Редагувати» у отримувача немає")
                .isFalse();
    }

    @Test(priority = 22)
    @TestCaseId("TC-UI-REL-020")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-EDIT_REL-007 AC-06: після FINISHED рядок зникає з «В дорозі», олівця немає.
            """)
    public void finishedSendLeavesInTransitTab() {
        String marker = "in-transit-finished-" + System.currentTimeMillis();
        RelocationResponse sent = relocationFixture.createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2StorageId, resourceId, 8.0, marker);
        relocationFixture.resolve(UserRole.OWNER_2, sent.getId(), owner2StorageId, RelocationState.FINISHED);

        reopenPageWithSession(UserRole.ADMIN, storageId);
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.attachScreenshot("TC-UI-REL-020 — after accept");

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Після FINISHED рядка немає на «В дорозі» (%s)", marker)
                .isFalse();
    }

    @Test(priority = 23)
    @TestCaseId("TC-UI-REL-021")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            REQ-EDIT_REL-007 AC-06: якщо для адміна (або користувача з багатьма локаціями)
            не обрана локація («Всі локації» / «Оберіть склад...»), олівець на «В дорозі»
            не відображається.
            """)
    public void allLocationsHidesInTransitEdit() {
        String marker = "in-transit-all-loc-" + System.currentTimeMillis();
        relocationFixture.createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2StorageId, resourceId, 8.0, marker);

        reopenPageWithSession(UserRole.ADMIN, storageId);
        RelocationPage journal = new RelocationPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(sidebar.isWorkspaceSelectorVisible())
                .as("У адміна з багатьма локаціями є селектор «Робочий простір»")
                .isTrue();
        sidebar.selectAllLocations();

        journal.waitForLoaded().waitForJournalDataSettled().openInTransitTab();
        journal.attachScreenshot("TC-UI-REL-021 — no location selected");

        assertThat(sidebar.getSelectedLocationName())
                .as("Локація не обрана — плейсхолдер селектора")
                .containsIgnoringCase("Оберіть склад");
        assertThat(journal.isRowWithTextVisible(marker))
                .as("Рядок видачі видимий без обраної локації (%s)", marker)
                .isTrue();
        assertThat(journal.isEditButtonVisibleInRow(marker))
                .as("Олівця немає, поки не обрана конкретна локація")
                .isFalse();
    }

    @Test(priority = 24)
    @TestCaseId("TC-UI-REL-022")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-EDIT_REL-007 AC-06: у режимі «Всі локації» «Прийняти» і «Скасувати»
            на «В дорозі» disabled; hover показує тултіп про вибір локації;
            банера «Помилка. Оновіть сторінку» немає.
            """)
    public void allLocationsDisablesInTransitAccept() {
        String marker = "in-transit-accept-all-" + System.currentTimeMillis();
        relocationFixture.createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2StorageId, resourceId, 8.0, marker);

        reopenPageWithSession(UserRole.ADMIN, storageId);
        RelocationPage journal = new RelocationPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(sidebar.isWorkspaceSelectorVisible())
                .as("У адміна з багатьма локаціями є селектор «Робочий простір»")
                .isTrue();
        sidebar.selectAllLocations();

        journal.waitForLoaded().waitForJournalDataSettled().openInTransitTab();
        journal.attachScreenshot("TC-UI-REL-022 — all locations");

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Рядок видачі видимий без обраної локації (%s)", marker)
                .isTrue();
        assertThat(journal.isAcceptButtonDisabledInRow(marker))
                .as("«Прийняти» disabled у режимі «Всі локації»")
                .isTrue();
        assertThat(journal.isCancelButtonDisabledInRow(marker))
                .as("«Скасувати» disabled у режимі «Всі локації»")
                .isTrue();
        assertThat(journal.hoverDisabledAcceptTooltip(marker))
                .as("Тултіп disabled «Прийняти»")
                .contains("Оберіть конкретну локацію в бічній панелі для виконання дії");
        assertThat(journal.hasResolveErrorBanner())
                .as("Банера «Помилка. Оновіть сторінку» немає")
                .isFalse();
    }

    @Test(priority = 25)
    @TestCaseId("TC-UI-REL-023")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-EDIT_REL-007 AC-06: на складі-отримувачі «Прийняти» enabled;
            підтвердження завершує видачу, рядка немає на «В дорозі»,
            банера «Помилка. Оновіть сторінку» немає.
            """)
    public void recipientLocationEnablesAccept() {
        String marker = "in-transit-accept-ok-" + System.currentTimeMillis();
        relocationFixture.createSendWithDescription(
                UserRole.OWNER_1, storageId, owner2StorageId, resourceId, 8.0, marker);

        reopenPageWithSession(UserRole.OWNER_2, owner2StorageId);
        RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
        journal.attachScreenshot("TC-UI-REL-023 — recipient location");

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Рядок видачі на складі отримувача (%s)", marker)
                .isTrue();
        assertThat(journal.isAcceptButtonVisibleInRow(marker))
                .as("Кнопка «Прийняти» видима")
                .isTrue();
        assertThat(journal.isAcceptButtonDisabledInRow(marker))
                .as("«Прийняти» enabled на складі отримувача")
                .isFalse();
        assertThat(journal.hasResolveErrorBanner())
                .as("Банера немає до прийому")
                .isFalse();

        journal.acceptInTransitAsRecipient(marker);
        journal.attachScreenshot("TC-UI-REL-023 — after accept");

        assertThat(journal.isRowWithTextVisible(marker))
                .as("Після прийому рядка немає на «В дорозі» (%s)", marker)
                .isFalse();
        assertThat(journal.hasResolveErrorBanner())
                .as("Банера «Помилка. Оновіть сторінку» немає після прийому")
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

    private void reopenPageWithSession(UserRole role, long selectedStorageId) {
        if (page != null) {
            try {
                page.close();
            } catch (Exception e) {
                log.debug("Could not close page before session reinject: {}", e.getMessage());
            }
        }
        injectRoleSession(role, selectedStorageId);
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }
}
