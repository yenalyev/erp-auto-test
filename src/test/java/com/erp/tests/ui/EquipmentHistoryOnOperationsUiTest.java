package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.InventorySessionStatus;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.pages.RelocationPage;
import com.erp.pages.RelocationUpdateOutputPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Operation History")
@Feature("Equipment operations on /history")
public class EquipmentHistoryOnOperationsUiTest extends BaseUITest {

    private EquipmentFixture equipmentFixture;
    private InventoryFixture inventoryFixture;
    private long owner1StorageId;
    private long owner2StorageId;
    private Long categoryId;
    private Boolean equipmentInventoryWasOpen;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        equipmentFixture.prepareCategoryContext();
        owner1StorageId = ConfigProvider.getOwner1StorageId();
        owner2StorageId = ConfigProvider.getOwner2StorageId();
        categoryId = testContext.get(ContextKey.EQUIPMENT_CATEGORY_ID);
    }

    @BeforeMethod(alwaysRun = true)
    public void snapshotEquipmentInventoryStatus() {
        InventorySessionStatus status = inventoryFixture.getEquipmentStatus(owner1StorageId, UserRole.ADMIN);
        equipmentInventoryWasOpen = Boolean.TRUE.equals(status.getOpen());
    }

    @AfterMethod(alwaysRun = true)
    public void restoreEquipmentInventoryStatus() {
        if (equipmentInventoryWasOpen == null) {
            return;
        }
        try {
            if (equipmentInventoryWasOpen) {
                inventoryFixture.openEquipmentSession(owner1StorageId);
            } else {
                inventoryFixture.ensureEquipmentClosed(owner1StorageId);
            }
        } catch (RuntimeException e) {
            log.warn("Could not restore equipment inventory session on storage {}: {}",
                    owner1StorageId, e.getMessage());
        }
    }

    @Test
    @TestCaseId("TC-UI-HIST-EQ-001")
    @Story("Sent equipment card on operation history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після send Owner1→Owner2 на складі відправника в «Історія операцій»
            видима картка «Відправлено (Обладнання)» і в таблиці є назва/інв.№.
            """)
    public void sentEquipmentShowsOnOperationHistory() {
        EquipmentResponse equipment = Allure.step("API: створити обладнання на Owner 1 і видати на Owner 2", () -> {
            EquipmentResponse created = equipmentFixture.createEquipmentOnStorage(
                    UserRole.ADMIN, owner1StorageId, categoryId);
            equipmentFixture.sendEquipment(
                    UserRole.OWNER_1, owner1StorageId, owner2StorageId, created.getId());
            return created;
        });

        Allure.parameter("equipmentName", equipment.getName());
        Allure.parameter("inventoryNumber", equipment.getInventoryNumber());

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        OperationHistoryPage historyPage = Allure.step("Відкрити /history на складі відправника", () ->
                new OperationHistoryPage(page).open());

        Allure.step("Перевірити картку «Відправлено (Обладнання)» та таблицю", () -> {
            historyPage.attachScreenshot("TC-UI-HIST-EQ-001 — sent equipment on /history");
            assertThat(historyPage.isEquipmentSummaryCardVisible(OperationHistoryPage.EQUIPMENT_SENT_CARD))
                    .as("Картка «Відправлено (Обладнання)» має бути видима")
                    .isTrue();
            assertThat(historyPage.equipmentHistoryContains(equipment.getName()))
                    .as("У таблиці/картках має бути назва виданої одиниці")
                    .isTrue();
            assertThat(historyPage.equipmentHistoryContains(equipment.getInventoryNumber()))
                    .as("У таблиці має бути інвентарний номер виданої одиниці")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-HIST-EQ-002")
    @Story("Received equipment card after FINISHED")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після FINISHED на складі отримувача в «Історія операцій»
            видима картка «Отримано (Обладнання)» і в таблиці є назва/інв.№.
            """)
    public void receivedEquipmentShowsOnOperationHistory() {
        EquipmentResponse equipment = Allure.step(
                "API: створити, видати Owner1→Owner2 і підтвердити FINISHED", () -> {
                    EquipmentResponse created = equipmentFixture.createEquipmentOnStorage(
                            UserRole.ADMIN, owner1StorageId, categoryId);
                    RelocationResponse relocation = equipmentFixture.sendEquipment(
                            UserRole.OWNER_1, owner1StorageId, owner2StorageId, created.getId());
                    equipmentFixture.resolveEquipment(
                            UserRole.OWNER_2, relocation.getId(), owner2StorageId, RelocationState.FINISHED);
                    EquipmentStatus status = equipmentFixture.getEquipmentStatus(
                            UserRole.OWNER_2, owner2StorageId, created.getId());
                    assertThat(status).isEqualTo(EquipmentStatus.AVAILABLE);
                    return created;
                });

        Allure.parameter("equipmentName", equipment.getName());
        Allure.parameter("inventoryNumber", equipment.getInventoryNumber());

        injectRoleSession(UserRole.OWNER_2, owner2StorageId);
        page = browserContext.newPage();

        OperationHistoryPage historyPage = Allure.step("Відкрити /history на складі отримувача", () ->
                new OperationHistoryPage(page).open());

        Allure.step("Перевірити картку «Отримано (Обладнання)» та таблицю", () -> {
            historyPage.attachScreenshot("TC-UI-HIST-EQ-002 — received equipment on /history");
            assertThat(historyPage.isEquipmentSummaryCardVisible(OperationHistoryPage.EQUIPMENT_RECEIVED_CARD))
                    .as("Картка «Отримано (Обладнання)» має бути видима")
                    .isTrue();
            assertThat(historyPage.equipmentHistoryContains(equipment.getName()))
                    .as("У таблиці/картках має бути назва отриманої одиниці")
                    .isTrue();
            assertThat(historyPage.equipmentHistoryContains(equipment.getInventoryNumber()))
                    .as("У таблиці має бути інвентарний номер отриманої одиниці")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-HIST-EQ-003")
    @Story("Inventory ADDED equipment on received card")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Інвентаризаційне додавання без постачальника (ADDED) потрапляє
            в картку «Отримано (Обладнання)» на /history.
            """)
    public void inventoryAddedEquipmentShowsOnOperationHistory() {
        inventoryFixture.openEquipmentSession(owner1StorageId);
        EquipmentResponse equipment = Allure.step(
                "API: створити обладнання без постачальника при відкритій сесії", () ->
                        equipmentFixture.createEquipmentWithoutSupplier(
                                UserRole.OWNER_1, owner1StorageId, categoryId));

        Allure.parameter("equipmentName", equipment.getName());
        Allure.parameter("inventoryNumber", equipment.getInventoryNumber());

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        OperationHistoryPage historyPage = Allure.step("Відкрити /history на складі Owner 1", () ->
                new OperationHistoryPage(page).open());

        Allure.step("Перевірити картку «Отримано (Обладнання)» та таблицю", () -> {
            historyPage.attachScreenshot("TC-UI-HIST-EQ-003 — inventory ADDED on /history");
            assertThat(historyPage.isEquipmentSummaryCardVisible(OperationHistoryPage.EQUIPMENT_RECEIVED_CARD))
                    .as("Картка «Отримано (Обладнання)» має бути видима")
                    .isTrue();
            assertThat(historyPage.equipmentHistoryContains(equipment.getName()))
                    .as("У таблиці має бути назва інвентаризаційно доданої одиниці")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-HIST-EQ-004")
    @Story("Cancelled equipment relocation removed from /history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після send рядок «Відправлено» є в «Історія операцій»;
            після скасування відправником у журналі (UI RETURNED з версією форми) рядок зникає.

            Відомий дефект: EquipmentHistoryService пише SENT на send і не видаляє запис при RETURNED;
            GET /equipment/history не фільтрує за станом relocation — рядок «Відправлено» лишається.
            Очікувана поведінка (REQ-OPER-HIST AC-11): після UI RETURNED рядка SENT немає.
            """)
    public void cancelledEquipmentRelocationRemovedFromOperationHistory() {
        EquipmentResponse equipment = Allure.step("API: створити обладнання на Owner 1 і видати на Owner 2", () -> {
            EquipmentResponse created = equipmentFixture.createEquipmentOnStorage(
                    UserRole.ADMIN, owner1StorageId, categoryId);
            equipmentFixture.sendEquipment(
                    UserRole.OWNER_1, owner1StorageId, owner2StorageId, created.getId());
            return created;
        });

        Allure.parameter("equipmentName", equipment.getName());
        Allure.parameter("inventoryNumber", equipment.getInventoryNumber());

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        OperationHistoryPage historyPage = Allure.step("Відкрити /history на складі відправника", () ->
                new OperationHistoryPage(page).open());

        Allure.step("Перевірити, що після send є рядок «Відправлено»", () -> {
            historyPage.attachScreenshot("TC-UI-HIST-EQ-004 — SENT before cancel");
            assertThat(historyPage.equipmentTableHasOperation(
                    equipment.getInventoryNumber(), OperationHistoryPage.EQUIPMENT_OP_SENT)
                    || historyPage.equipmentTableHasOperation(
                    equipment.getName(), OperationHistoryPage.EQUIPMENT_OP_SENT))
                    .as("Після send у таблиці має бути рядок «Відправлено» для одиниці")
                    .isTrue();
        });

        Allure.step("UI: скасувати відправлення на «В дорозі» (Повернено)", () -> {
            RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
            journal.attachScreenshot("TC-UI-HIST-EQ-004 — in transit before cancel");
            journal.cancelInTransitAsSender(equipment.getName(), "ui-eq-hist-004-cancel");
        });

        Allure.step("Оновити /history і перевірити, що рядок «Відправлено» зник", () -> {
            historyPage.open();
            historyPage.attachScreenshot("TC-UI-HIST-EQ-004 — SENT removed after UI cancel");
            assertThat(historyPage.equipmentTableHasOperation(
                    equipment.getInventoryNumber(), OperationHistoryPage.EQUIPMENT_OP_SENT))
                    .as("Після скасування рядка «Відправлено» з інв.№ не має бути")
                    .isFalse();
            assertThat(historyPage.equipmentTableHasOperation(
                    equipment.getName(), OperationHistoryPage.EQUIPMENT_OP_SENT))
                    .as("Після скасування рядка «Відправлено» з назвою не має бути")
                    .isFalse();
        });
    }

    @Test
    @TestCaseId("TC-UI-HIST-EQ-005")
    @Story("Edited equipment send description visible on /history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після UI edit send (форма шле version) унікальний опис видимий в «Історія операцій».

            Відомий дефект: EquipmentHistoryResponse.message — i18n-шаблон equipment.history.sent,
            не relocation.description; updateSend не синхронізує опис у history.
            Очікувана поведінка (REQ-OPER-HIST AC-12): новий опис видимий на /history.
            """)
    public void editedEquipmentSendShowsUpdatedDescriptionOnOperationHistory() {
        String marker = "ui-eq-hist-edit-" + (System.currentTimeMillis() % 1_000_000);
        EquipmentResponse equipment = Allure.step("API: створити обладнання і видати Owner1→Owner2", () -> {
            EquipmentResponse created = equipmentFixture.createEquipmentOnStorage(
                    UserRole.ADMIN, owner1StorageId, categoryId);
            equipmentFixture.sendEquipment(
                    UserRole.OWNER_1, owner1StorageId, owner2StorageId, created.getId());
            return created;
        });

        Allure.parameter("equipmentName", equipment.getName());
        Allure.parameter("editMarker", marker);

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        Allure.step("UI: редагувати видачу на «В дорозі» — примітки з version", () -> {
            RelocationPage journal = new RelocationPage(page).open().openInTransitTab();
            RelocationUpdateOutputPage form = journal.clickEditSendInRow(equipment.getName());
            form.attachScreenshot("TC-UI-HIST-EQ-005 — edit send form");
            form.fillDescription(marker).submitVersionedEquipmentSend();
        });

        OperationHistoryPage historyPage = Allure.step("Відкрити /history на складі відправника", () ->
                new OperationHistoryPage(page).open());

        Allure.step("Перевірити оновлений опис у історії обладнання", () -> {
            historyPage.attachScreenshot("TC-UI-HIST-EQ-005 — edited description on /history");
            assertThat(historyPage.equipmentHistoryContains(equipment.getName())
                    || historyPage.equipmentHistoryContains(equipment.getInventoryNumber()))
                    .as("Одиниця має бути в історії")
                    .isTrue();
            assertThat(historyPage.equipmentHistoryContains(marker))
                    .as("Оновлений опис переміщення має бути видимий")
                    .isTrue();
        });
    }

    @Test
    @TestCaseId("TC-UI-HIST-EQ-006")
    @Story("Deleted supplier receive removes unit from /history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після створення обладнання від стороннього постачальника рядок є в «Історія операцій»;
            після UI-видалення цього отримання запис зникає.
            """)
    public void deletedSupplierReceiveRemovesEquipmentFromOperationHistory() {
        EquipmentResponse equipment = Allure.step(
                "API: створити обладнання від стороннього постачальника на Owner 1", () ->
                        equipmentFixture.createEquipmentOnStorage(
                                UserRole.ADMIN, owner1StorageId, categoryId));

        Allure.parameter("equipmentName", equipment.getName());
        Allure.parameter("inventoryNumber", equipment.getInventoryNumber());

        injectRoleSession(UserRole.OWNER_1, owner1StorageId);
        page = browserContext.newPage();

        OperationHistoryPage historyPage = Allure.step("Відкрити /history на складі отримувача", () ->
                new OperationHistoryPage(page).open());

        Allure.step("Перевірити, що після отримання від постачальника одиниця є в історії", () -> {
            historyPage.attachScreenshot("TC-UI-HIST-EQ-006 — receive before delete");
            assertThat(historyPage.equipmentHistoryContains(equipment.getName())
                    || historyPage.equipmentHistoryContains(equipment.getInventoryNumber()))
                    .as("Після отримання від постачальника одиниця має бути в історії")
                    .isTrue();
        });

        Allure.step("UI: Admin видаляє отримання на табі «Отримано»", () -> {
            reopenWithSession(UserRole.ADMIN, owner1StorageId);
            RelocationPage journal = new RelocationPage(page).open().openReceivedTab();
            journal.attachScreenshot("TC-UI-HIST-EQ-006 — received tab before delete");
            journal.deleteRowAndConfirm(equipment.getName());
        });

        Allure.step("Оновити /history і перевірити, що запис зник", () -> {
            reopenWithSession(UserRole.OWNER_1, owner1StorageId);
            OperationHistoryPage afterDelete = new OperationHistoryPage(page).open();
            afterDelete.attachScreenshot("TC-UI-HIST-EQ-006 — receive removed after UI delete");
            assertThat(afterDelete.equipmentHistoryContains(equipment.getName()))
                    .as("Після видалення отримання назви одиниці в історії не має бути")
                    .isFalse();
            assertThat(afterDelete.equipmentHistoryContains(equipment.getInventoryNumber()))
                    .as("Після видалення отримання інв.№ в історії не має бути")
                    .isFalse();
        });
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
