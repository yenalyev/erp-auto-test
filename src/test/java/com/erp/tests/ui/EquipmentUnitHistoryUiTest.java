package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.pages.EmployeeListPage;
import com.erp.pages.EquipmentCreatePage;
import com.erp.pages.EquipmentDetailDialog;
import com.erp.pages.EquipmentListPage;
import com.erp.pages.RelocationPage;
import com.erp.pages.RelocationUpdateOutputPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Equipment")
@Feature("REQ-EQU-004 Unit history dialog")
public class EquipmentUnitHistoryUiTest extends BaseUITest {

    private EquipmentFixture equipmentFixture;
    private StorageFixture storageFixture;
    private long owner1StorageId;
    private long owner2StorageId;
    private String owner1StorageName;
    private String owner2StorageName;
    private String categoryName;
    private String supplierName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        new RelocationFixture(testContext, apiExecutor).prepareContext();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        equipmentFixture.prepareCategoryContext();
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        Long categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
        Long supplierId = testContext.get(ContextKey.RELOCATION_SUPPLIER_ID);
        categoryName = equipmentFixture.resolveCategoryName(categoryId);
        supplierName = equipmentFixture.resolveSupplierName(supplierId);
        owner1StorageName = storageFixture.getById(UserRole.ADMIN, owner1StorageId).getName();
        owner2StorageName = storageFixture.getById(UserRole.ADMIN, owner2StorageId).getName();
    }

    @Test
    @TestCaseId("TC-UI-EQ-HIST-001")
    @Story("Unit history after create from supplier")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI-створення обладнання з постачальником діалог одиниці
            показує секцію «Історія» з операцією «Додано» або «Отримано».
            """)
    public void unitHistoryShowsAddedOrReceivedAfterCreate() {
        String name = uniqueEquipmentName();
        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        EquipmentListPage list = Allure.step("UI: створити обладнання з постачальником", () ->
                createEquipmentViaUi(name));

        Allure.parameter("equipmentName", name);

        EquipmentDetailDialog dialog = Allure.step("Відкрити діалог одиниці на /equipment", () ->
                list.openUnitDialog(name));

        Allure.step("Перевірити секцію «Історія»", () -> {
            dialog.attachScreenshot("TC-UI-EQ-HIST-001 — unit history after create");
            assertThat(dialog.isHistorySectionVisible())
                    .as("Секція «Історія» має бути видима")
                    .isTrue();
            assertThat(dialog.hasOperation(EquipmentDetailDialog.OP_ADDED)
                    || dialog.hasOperation(EquipmentDetailDialog.OP_RECEIVED))
                    .as("У «Історія» має бути «Додано» або «Отримано»")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-EQ-HIST-002")
    @Story("Unit history after send and FINISHED")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI send storage→storage і «Прийняти» (FINISHED) діалог одиниці
            на складі отримувача показує «Відправлено» та вхідну «Отримано» або «Додано».
            """)
    public void unitHistoryShowsSentAndReceivedAfterRelocation() {
        String name = uniqueEquipmentName();
        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        Allure.step("UI: створити обладнання і передати Owner1→Owner2", () -> {
            createEquipmentViaUi(name);
            sendToOwner2(name);
        });

        Allure.parameter("equipmentName", name);

        reopenWithSession(UserRole.OWNER_2, owner2StorageId);
        Allure.step("UI: отримувач приймає видачу на «В дорозі»", () ->
                new RelocationPage(page).open().openInTransitTab()
                        .acceptInTransitAsRecipient(name));

        EquipmentDetailDialog dialog = Allure.step("Відкрити діалог одиниці на складі отримувача", () ->
                new EquipmentListPage(page)
                        .openForStorage(owner2StorageId)
                        .openUnitDialog(name));

        Allure.step("Перевірити типи операцій у «Історія»", () -> {
            dialog.attachScreenshot("TC-UI-EQ-HIST-002 — unit history after send+FINISHED");
            assertThat(dialog.isHistorySectionVisible()).isTrue();
            assertThat(dialog.hasOperation(EquipmentDetailDialog.OP_SENT))
                    .as("У «Історія» має бути «Відправлено»")
                    .isTrue();
            assertThat(dialog.hasOperation(EquipmentDetailDialog.OP_RECEIVED)
                    || dialog.hasOperation(EquipmentDetailDialog.OP_ADDED))
                    .as("У «Історія» має бути вхідна операція «Отримано» або «Додано»")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-EQ-HIST-003")
    @Story("Assignment history after assign")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI-закріплення обладнання за співробітником діалог показує
            рядок у «Історія закріплень» з позивним.
            """)
    public void assignmentHistoryShowsEmployeeAfterAssign() {
        String callSign = "ui-eq-hist-emp-" + (System.currentTimeMillis() % 1_000_000);
        String name = uniqueEquipmentName();

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        Allure.step("UI: створити співробітника", () ->
                new EmployeeListPage(page)
                        .openForStorage(owner1StorageId)
                        .createEmployee(callSign, owner1StorageName));

        Allure.step("UI: створити обладнання", () -> createEquipmentViaUi(name));

        Allure.parameter("equipmentName", name);
        Allure.parameter("callSign", callSign);

        EquipmentDetailDialog dialog = Allure.step("Відкрити діалог і закріпити за співробітником", () -> {
            EquipmentDetailDialog opened = new EquipmentListPage(page)
                    .openForStorage(owner1StorageId)
                    .openUnitDialog(name);
            opened.assignTo(callSign);
            return opened;
        });

        Allure.step("Перевірити «Історія закріплень»", () -> {
            dialog.attachScreenshot("TC-UI-EQ-HIST-003 — assignment history");
            assertThat(dialog.isAssignmentHistorySectionVisible())
                    .as("Секція «Історія закріплень» має бути видима")
                    .isTrue();
            assertThat(dialog.assignmentHistoryContains(callSign))
                    .as("У «Історія закріплень» має бути позивний співробітника")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-EQ-HIST-004")
    @Story("Unit history after return from assignee")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI-повернення закріпленого обладнання діалог одиниці показує «Повернено»
            у «Історія», а рядок у «Історія закріплень» більше не позначений як «Поточний».
            """)
    public void unitHistoryShowsReturnedAfterAssignmentReturn() {
        String callSign = "ui-eq-hist-ret-" + (System.currentTimeMillis() % 1_000_000);
        String note = "ui-eq-hist-004-return";
        String name = uniqueEquipmentName();

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        Allure.step("UI: створити співробітника і обладнання", () -> {
            new EmployeeListPage(page)
                    .openForStorage(owner1StorageId)
                    .createEmployee(callSign, owner1StorageName);
            createEquipmentViaUi(name);
        });

        Allure.parameter("equipmentName", name);
        Allure.parameter("callSign", callSign);

        EquipmentDetailDialog dialog = Allure.step("UI: закріпити за співробітником і повернути", () -> {
            EquipmentDetailDialog opened = new EquipmentListPage(page)
                    .openForStorage(owner1StorageId)
                    .openUnitDialog(name);
            opened.assignTo(callSign);
            return opened.returnFromAssignee(note);
        });

        Allure.step("Перевірити «Повернено» в «Історія» і закриття закріплення", () -> {
            dialog.attachScreenshot("TC-UI-EQ-HIST-004 — returned from assignee");
            assertThat(dialog.hasOperation(EquipmentDetailDialog.OP_ASSIGNED))
                    .as("«Закріплено» має лишитися в «Історія»")
                    .isTrue();
            assertThat(dialog.hasOperation(EquipmentDetailDialog.OP_RETURNED))
                    .as("Після повернення в «Історія» має бути «Повернено»")
                    .isTrue();
            assertThat(dialog.assignmentHistoryContains(callSign))
                    .as("Закріплення за співробітником має лишитися в «Історія закріплень»")
                    .isTrue();
            assertThat(dialog.assignmentHistoryHasOpenAssignment())
                    .as("Після повернення не має лишатися активного закріплення «Поточний»")
                    .isFalse();
        });
    }

    @Test
    @TestCaseId("TC-UI-EQ-HIST-005")
    @Story("Unit history shows edited send description")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI edit send (форма шле version з payload) унікальний опис видимий у діалозі одиниці.

            Відомий дефект: GET /equipment/{id}/history віддає i18n message, не description переміщення;
            updateSend історію не оновлює. Очікувана поведінка (REQ-EQU-004 AC-03): новий опис у «Історія».
            """)
    public void unitHistoryShowsEditedSendDescription() {
        String marker = "ui-eq-unit-edit-" + (System.currentTimeMillis() % 1_000_000);
        String name = uniqueEquipmentName();

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        Allure.step("UI: створити обладнання і передати Owner1→Owner2", () -> {
            createEquipmentViaUi(name);
            sendToOwner2(name);
        });

        Allure.parameter("equipmentName", name);
        Allure.parameter("editMarker", marker);

        Allure.step("UI: редагувати видачу на «В дорозі» — примітки з version", () -> {
            RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
            RelocationUpdateOutputPage form = journal.clickEditSendInRow(name);
            form.attachScreenshot("TC-UI-EQ-HIST-005 — edit send form");
            form.fillDescription(marker).submitVersionedEquipmentSend();
        });

        EquipmentDetailDialog dialog = Allure.step("Відкрити діалог одиниці (статус «В дорозі»)", () ->
                new EquipmentListPage(page)
                        .openForStorage(owner1StorageId)
                        .includeStatus("В дорозі")
                        .openUnitDialog(name));

        Allure.step("Перевірити оновлений опис у «Історія»", () -> {
            dialog.attachScreenshot("TC-UI-EQ-HIST-005 — edited description in unit history");
            assertThat(dialog.isHistorySectionVisible()).isTrue();
            assertThat(dialog.historyContains(marker))
                    .as("У «Історія» має бути оновлений опис переміщення")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-EQ-HIST-006")
    @Story("Cancelled send removes Відправлено from unit history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI send і UI-скасування (Повернено) діалог одиниці не показує «Відправлено».
            «Додано» лишається.

            Відомий дефект: SENT лишається в GET /equipment/{id}/history після RETURNED
            (REQ-EQU-004 docs). Очікувана поведінка (AC-04): немає «Відправлено».
            """)
    public void cancelledSendRemovesSentFromUnitHistory() {
        String name = uniqueEquipmentName();

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        Allure.step("UI: створити обладнання і передати Owner1→Owner2", () -> {
            createEquipmentViaUi(name);
            sendToOwner2(name);
        });

        Allure.parameter("equipmentName", name);

        Allure.step("UI: скасувати відправлення на «В дорозі»", () -> {
            RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
            journal.attachScreenshot("TC-UI-EQ-HIST-006 — in transit before cancel");
            journal.cancelInTransitAsSender(name, "ui-eq-hist-006-cancel");
        });

        EquipmentDetailDialog dialog = Allure.step("Відкрити діалог одиниці на /equipment", () ->
                new EquipmentListPage(page)
                        .openForStorage(owner1StorageId)
                        .openUnitDialog(name));

        Allure.step("Перевірити, що «Відправлено» немає, «Додано» є", () -> {
            dialog.attachScreenshot("TC-UI-EQ-HIST-006 — SENT gone after UI cancel");
            assertThat(dialog.isHistorySectionVisible()).isTrue();
            assertThat(dialog.hasOperation(EquipmentDetailDialog.OP_ADDED)
                    || dialog.hasOperation(EquipmentDetailDialog.OP_RECEIVED))
                    .as("«Додано»/«Отримано» має лишитися")
                    .isTrue();
            assertThat(dialog.operationCount(EquipmentDetailDialog.OP_SENT))
                    .as("Після скасування відправлення «Відправлено» не має бути")
                    .isZero();
        });
    }

    @Test
    @TestCaseId("TC-UI-EQ-HIST-007")
    @Story("Edit send does not duplicate Відправлено in unit history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI edit send (version) у діалозі одиниці не більше одного «Відправлено».

            Відомий дефект: edit send може писати другий SENT у GET /equipment/{id}/history
            (REQ-EQU-004 docs). Очікувана поведінка (AC-04): не більше одного «Відправлено».
            """)
    public void editSendDoesNotDuplicateSentInUnitHistory() {
        String name = uniqueEquipmentName();

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        Allure.step("UI: створити обладнання і передати Owner1→Owner2", () -> {
            createEquipmentViaUi(name);
            sendToOwner2(name);
        });

        Allure.parameter("equipmentName", name);

        Allure.step("UI: edit send і скасувати, щоб відкрити журнал", () -> {
            RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
            RelocationUpdateOutputPage form = journal.clickEditSendInRow(name);
            form.fillDescription("ui-eq-no-dup-" + (System.currentTimeMillis() % 1_000_000))
                    .submitVersionedEquipmentSend();
            journal = new RelocationPage(page).open().openInTransitTab();
            journal.cancelInTransitAsSender(name, "ui-eq-hist-007-cancel");
        });

        EquipmentDetailDialog dialog = Allure.step("Відкрити діалог одиниці на /equipment", () ->
                new EquipmentListPage(page)
                        .openForStorage(owner1StorageId)
                        .openUnitDialog(name));

        Allure.step("Перевірити, що «Відправлено» не задубльоване", () -> {
            dialog.attachScreenshot("TC-UI-EQ-HIST-007 — SENT not duplicated after UI edit");
            assertThat(dialog.isHistorySectionVisible()).isTrue();
            assertThat(dialog.operationCount(EquipmentDetailDialog.OP_SENT))
                    .as("Після send+edit не має бути двох однакових «Відправлено»")
                    .isLessThanOrEqualTo(1);
        });
    }

    private String uniqueEquipmentName() {
        return "ui-eq-hist-" + (System.currentTimeMillis() % 1_000_000);
    }

    private EquipmentListPage createEquipmentViaUi(String name) {
        return new EquipmentCreatePage(page)
                .openForStorage(owner1StorageId)
                .ensureSupplier(supplierName)
                .fillItem(0, name, categoryName)
                .submitAll(1);
    }

    private void sendToOwner2(String equipmentName) {
        new EquipmentListPage(page)
                .openForStorage(owner1StorageId)
                .selectGroupByName(equipmentName)
                .sendSelectedTo(owner2StorageName);
    }

    private void reopenWithSession(UserRole role, long selectedStorageId) {
        injectRoleSession(role, selectedStorageId);
        if (page != null && !page.isClosed()) {
            page.close();
        }
        page = browserContext.newPage();
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
