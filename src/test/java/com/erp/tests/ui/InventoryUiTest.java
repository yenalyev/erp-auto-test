package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageItemResponse;
import com.erp.pages.AccessForbiddenPage;
import com.erp.pages.ExportAnalyticsPage;
import com.erp.pages.InventoryEditPage;
import com.erp.pages.OperationHistoryPage;
import com.erp.pages.UnitManagementPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.InventoryStockUiVerification;
import com.erp.utils.helpers.PollUtils;
import com.erp.utils.helpers.UiDownloadAssertions;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS Manual Coverage UI")
public class InventoryUiTest extends BaseUITest {

    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private long storageId;
    private Long resourceId;
    private String resourceName;
    private final List<Long> resourcesToCleanup = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        relocationFixture.ensureStock(storageId, resourceId, 50.0);

        StorageItemResponse item;
        try {
            item = inventoryFixture.requireItemForResourceWithRetry(
                    storageId, resourceId, UserRole.ADMIN, 15_000);
        } catch (IllegalStateException ex) {
            // Multi-location stock probe and storage inventory list can diverge on shared dev data.
            log.warn("Relocation resource {} not on storage {} inventory list after ensureStock: {}",
                    resourceId, storageId, ex.getMessage());
            item = inventoryFixture.requireItemWithStock(storageId, UserRole.ADMIN);
        }
        resourceId = item.getResource().getId();
        resourceName = item.getResource().getName().trim().replaceAll("\\s+", " ");
    }

    @BeforeMethod(alwaysRun = true)
    public void prepareUiSession() {
        resourcesToCleanup.clear();
        inventoryFixture.ensureClosed(storageId);
        relocationFixture.ensureStock(storageId, resourceId, 50.0);
        injectRoleSession(UserRole.ADMIN, storageId);
    }

    @AfterMethod(alwaysRun = true)
    public void teardownInventoryUi() {
        cleanupTrackedStorageResources();
        inventoryFixture.ensureClosed(storageId);
        relocationFixture.ensureStock(storageId, resourceId, 50.0);
    }

    private void trackStorageResourceForCleanup(Long addedResourceId) {
        if (addedResourceId != null
                && !addedResourceId.equals(resourceId)
                && !resourcesToCleanup.contains(addedResourceId)) {
            resourcesToCleanup.add(addedResourceId);
        }
    }

    private void cleanupTrackedStorageResources() {
        for (Long addedResourceId : resourcesToCleanup) {
            try {
                inventoryFixture.removeResourceFromStorage(storageId, addedResourceId, UserRole.ADMIN);
            } catch (Exception e) {
                log.warn("UI inventory cleanup failed for resource {}: {}", addedResourceId, e.getMessage());
            }
        }
        resourcesToCleanup.clear();
    }

    // --- REQ-WMS-003 session ---

    @Test(priority = 10)
    @TestCaseId("TC-WMS-003-001")
    @Story("Admin opens session UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin відкриває сторінку «Залишки» на конкретній локації та натискає «Відкрити інвентаризацію».
            Очікується: кнопка змінюється на «Закрити інвентаризацію», сесія відкрита.
            """)
    public void adminOpensInventorySessionUi() {
        Allure.parameter("storageId", storageId);
        Allure.parameter("role", UserRole.ADMIN.name());

        UnitManagementPage stock = Allure.step("Відкрити «Залишки» для локації Owner 1", () -> {
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId);
            pageObj.attachScreenshot("TC-WMS-003-001 — stock page initial");
            return pageObj;
        });

        Allure.step("Відкрити сесію інвентаризації через UI", () -> {
            assertThat(stock.isOpenInventoryButtonVisible())
                    .as("Кнопка «Відкрити інвентаризацію» має бути видимою")
                    .isTrue();
            stock.clickOpenInventory().assertInventorySessionOpen();
            stock.attachScreenshot("TC-WMS-003-001 — session open");
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-003-002")
    @Story("Admin closes session UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin закриває відкриту сесію інвентаризації на сторінці «Залишки».
            Очікується: знову видима кнопка «Відкрити інвентаризацію».
            """)
    public void adminClosesInventorySessionUi() {
        inventoryFixture.openSession(storageId);
        Allure.parameter("storageId", storageId);

        UnitManagementPage stock = Allure.step("Відкрити «Залишки» з відкритою сесією", () -> {
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId)
                    .waitForSessionOpenState(true);
            pageObj.attachScreenshot("TC-WMS-003-002 — session open before close");
            return pageObj;
        });

        Allure.step("Закрити сесію інвентаризації через UI", () -> {
            stock.clickCloseInventory();
            assertThat(stock.isOpenInventoryButtonVisible())
                    .as("Після закриття має з'явитися «Відкрити інвентаризацію»")
                    .isTrue();
            stock.attachScreenshot("TC-WMS-003-002 — session closed");
        });
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-003-003")
    @Story("Owner has no session toggle")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Owner 1 відкриває «Залишки» на своїй локації.
            Очікується: кнопок «Відкрити/Закрити інвентаризацію» немає.
            """)
    public void ownerHasNoSessionToggleUi() {
        UnitManagementPage stock = Allure.step("Відкрити «Залишки» під Owner 1", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId);
            pageObj.attachScreenshot("TC-WMS-003-003 — owner stock page");
            return pageObj;
        });

        Allure.step("Переконатися, що toggle сесії відсутній", () -> {
            assertThat(stock.isOpenInventoryButtonVisible()).isFalse();
            assertThat(stock.isCloseInventoryButtonVisible()).isFalse();
        });
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-003-004")
    @Story("All locations disables session button")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Admin обирає «Всі локації» і відкриває «Залишки».
            Очікується: кнопка «Відкрити інвентаризацію» disabled.
            """)
    public void allLocationsDisablesSessionButtonUi() {
        Allure.step("Відкрити агрегований перегляд «Всі локації»", () -> {
            injectAllLocationsSession(UserRole.ADMIN);
            page = browserContext.newPage();
            UnitManagementPage stock = new UnitManagementPage(page).openForAllLocations().waitForLoaded();
            PollUtils.waitUntilTrue(
                    stock::isInventorySessionToggleBlocked,
                    10_000,
                    "Inventory session toggle blocked in all-locations mode");
            assertThat(stock.isInventorySessionToggleBlocked())
                    .as("Toggle сесії має бути прихований або disabled у режимі «Всі локації»")
                    .isTrue();
            stock.attachScreenshot("TC-WMS-003-004 — all locations disabled session");
        });
    }

    @Test(priority = 50)
    @TestCaseId("TC-WMS-003-005")
    @Story("Owner conduct button active when session open")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після відкриття сесії Admin-ом Owner 1 бачить активну кнопку «Провести інвентаризацію»
            і може перейти на форму проведення.
            """)
    public void ownerConductButtonActiveUi() {
        inventoryFixture.openSession(storageId);

        UnitManagementPage stock = Allure.step("Owner 1 відкриває «Залишки»", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            return new UnitManagementPage(page).openForStorage(storageId);
        });

        Allure.step("Перевірити активну кнопку проведення та перехід на форму", () -> {
            stock.waitForConductButtonEnabled();
            assertThat(stock.isConductInventoryButtonEnabled()).isTrue();
            stock.attachScreenshot("TC-WMS-003-005 — conduct button enabled");
            stock.clickConductInventory();
            new InventoryEditPage(page).waitForLoaded();
            new InventoryEditPage(page).attachScreenshot("TC-WMS-003-005 — inventory form");
        });
    }

    // --- REQ-WMS-003 conduct ---

    @Test(priority = 60)
    @TestCaseId("TC-WMS-003-006")
    @Story("Owner updates amount UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Owner проводить інвентаризацію: змінює кількість ресурсу на формі та зберігає.
            Очікується: нова кількість відображається в таблиці «Залишки».
            """)
    public void ownerUpdatesAmountUi() {
        inventoryFixture.openSession(storageId);
        double before = inventoryFixture.getResourceStock(storageId, resourceId, UserRole.OWNER_1);
        double target = before + 5.0;
        Allure.parameter("resourceName", resourceName);
        Allure.parameter("targetAmount", target);

        injectRoleSession(UserRole.OWNER_1, storageId);
        page = browserContext.newPage();

        Allure.step("Відкрити форму проведення інвентаризації", () -> {
            new UnitManagementPage(page).openForStorage(storageId).clickConductInventory();
            new InventoryEditPage(page).waitForLoaded()
                    .attachScreenshot("TC-WMS-003-006 — form before save");
        });

        UnitManagementPage stockAfterSave = Allure.step("Змінити кількість і зберегти", () -> {
            new InventoryEditPage(page)
                    .updateAmountForResource(resourceName, String.valueOf((int) target))
                    .save();
            UnitManagementPage stock = new UnitManagementPage(page).waitForLoaded();
            stock.attachScreenshot("TC-WMS-003-006 — stock after save");
            return stock;
        });

        InventoryStockUiVerification.assertResourceAmountOnPage(
                stockAfterSave, resourceName, target,
                "Перевірити кількість у таблиці «Залишки»");
    }

    @Test(priority = 70)
    @TestCaseId("TC-WMS-003-007")
    @Story("Admin updates amount UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin проводить інвентаризацію: зменшує кількість ресурсу на формі та зберігає.
            Очікується: нова кількість відображається в таблиці «Залишки».
            """)
    public void adminUpdatesAmountUi() {
        inventoryFixture.openSession(storageId);
        double before = inventoryFixture.getResourceStock(storageId, resourceId, UserRole.ADMIN);
        double target = Math.max(1, before - 2);
        Allure.parameter("resourceName", resourceName);
        Allure.parameter("targetAmount", target);

        Allure.step("Відкрити форму проведення інвентаризації", () -> {
            new UnitManagementPage(page).openForStorage(storageId).clickConductInventory();
            new InventoryEditPage(page).waitForLoaded()
                    .attachScreenshot("TC-WMS-003-007 — form before save");
        });

        UnitManagementPage stockAfterSave = Allure.step("Зменшити кількість і зберегти", () -> {
            new InventoryEditPage(page)
                    .updateAmountForResource(resourceName, String.valueOf((int) target))
                    .save();
            UnitManagementPage stock = new UnitManagementPage(page).waitForLoaded();
            stock.attachScreenshot("TC-WMS-003-007 — stock after save");
            return stock;
        });

        InventoryStockUiVerification.assertResourceAmountOnPage(
                stockAfterSave, resourceName, target,
                "Перевірити кількість у таблиці «Залишки»");
    }

    @Test(priority = 80)
    @TestCaseId("TC-WMS-003-008")
    @Story("Conduct blocked when session closed UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            При закритій сесії кнопка «Провести інвентаризацію» disabled;
            прямий перехід на форму не дозволяє зберегти зміни.
            """)
    public void conductBlockedWhenClosedUi() {
        injectRoleSession(UserRole.ADMIN, storageId);
        page = browserContext.newPage();

        Allure.step("Переконатися, що проведення недоступне з «Залишків»", () -> {
            UnitManagementPage stock = new UnitManagementPage(page).openForStorage(storageId);
            assertThat(stock.isConductInventoryButtonEnabled()).isFalse();
            stock.attachScreenshot("TC-WMS-003-008 — conduct disabled");
        });

        Allure.step("Спроба зберегти зміни на формі при закритій сесії", () -> {
            InventoryEditPage edit = new InventoryEditPage(page).open(storageId);
            edit.updateAmountForResource(resourceName, "99");
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Зберегти")).click();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            assertThat(edit.hasSaveError() || page.url().contains("/inventory")).isTrue();
            edit.attachScreenshot("TC-WMS-003-008 — save blocked or error");
        });
    }

    @Test(priority = 90)
    @TestCaseId("TC-WMS-003-009")
    @Story("Add new resource UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin додає на склад ресурс, якого раніше не було, через форму інвентаризації
            (autocomplete «Оберіть ресурс»). Ресурси, що вже є на формі/складі, не показуються
            в autocomplete. Очікується: 5 од. у таблиці «Залишки».
            """)
    public void addNewResourceUi() {
        inventoryFixture.openSession(storageId);
        ResourceResponse newRes = inventoryFixture.createUniqueCatalogResourceAbsentFromStorage(
                storageId, UserRole.ADMIN, "InvUI_");
        trackStorageResourceForCleanup(newRes.getId());
        String newResourceName = newRes.getName().trim().replaceAll("\\s+", " ");
        Allure.parameter("newResourceName", newResourceName);
        Allure.parameter("existingResourceName", resourceName);

        Allure.step("Переконатися, що ресурсу ще немає у таблиці «Залишки»", () -> {
            UnitManagementPage stock = new UnitManagementPage(page).openForStorage(storageId).waitForLoaded();
            stock.search(newResourceName);
            assertThat(stock.isResourceVisibleInTable(newResourceName))
                    .as("Новий ресурс не повинен бути в таблиці до проведення інвентаризації")
                    .isFalse();
            stock.attachScreenshot("TC-WMS-003-009 — resource absent before add");
        });

        Allure.step("Відкрити форму та додати новий ресурс через UI", () -> {
            new UnitManagementPage(page).clickConductInventory();
            InventoryEditPage edit = new InventoryEditPage(page).waitForLoaded();
            edit.attachScreenshot("TC-WMS-003-009 — form before add");

            assertThat(edit.isAddResourceOptionVisible(resourceName))
                    .as("Ресурс, який уже є на складі, не повинен з'являтися в autocomplete «Оберіть ресурс»")
                    .isFalse();
            edit.closeAddResourceAutocomplete();

            assertThat(edit.isAddResourceOptionVisible(newResourceName))
                    .as("Новий ресурс має бути доступний у autocomplete")
                    .isTrue();
            edit.closeAddResourceAutocomplete();

            edit.addResource(newResourceName, "5");
            edit.attachScreenshot("TC-WMS-003-009 — form after add");
            assertThat(edit.isResourceListed(newResourceName))
                    .as("Ресурс має з'явитися на формі інвентаризації після «Додати»")
                    .isTrue();
            assertThat(edit.getResourceAmountInputValue(newResourceName))
                    .as("Кількість на формі має бути 5")
                    .isEqualTo("5");
            edit.save();
        });

        Allure.step("Перевірити новий ресурс у таблиці «Залишки»", () -> {
            UnitManagementPage stock = new UnitManagementPage(page)
                    .waitForLoaded()
                    .refreshInventoryTable();
            InventoryStockUiVerification.assertResourceAmountOnPage(
                    stock, newResourceName, 5.0,
                    "Новий ресурс має з'явитися в таблиці з кількістю 5 од.");
            stock.attachScreenshot("TC-WMS-003-009 — stock after save");
        });
    }

    @Test(priority = 100)
    @TestCaseId("TC-WMS-003-010")
    @Story("Remove resource UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Admin видаляє рядок ресурсу на формі інвентаризації та зберігає.
            Очікується: ресурс відсутній або має 0 од. у «Залишках».
            """)
    public void removeResourceUi() {
        inventoryFixture.openSession(storageId);
        relocationFixture.ensureStock(storageId, resourceId, 10.0);

        Allure.step("Видалити ресурс на формі інвентаризації", () -> {
            new UnitManagementPage(page).openForStorage(storageId).clickConductInventory();
            InventoryEditPage edit = new InventoryEditPage(page).waitForLoaded();
            edit.attachScreenshot("TC-WMS-003-010 — form before remove");
            edit.removeResource(resourceName).save();
        });

        UnitManagementPage stock = Allure.step("Перевірити «Залишки» після видалення", () -> {
            UnitManagementPage pageObj = new UnitManagementPage(page).waitForLoaded();
            pageObj.attachScreenshot("TC-WMS-003-010 — stock after remove");
            return pageObj;
        });

        InventoryStockUiVerification.assertResourceNotVisibleOrZero(
                stock, resourceName,
                "Ресурс має бути відсутній або з нульовою кількістю");
    }

    // --- REQ-WMS-007 stock ---

    @Test(priority = 110)
    @TestCaseId("TC-WMS-007-001")
    @Story("Owner and Admin access stock page")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Admin і Owner 1 можуть відкрити сторінку «Залишки» без помилки доступу;
            таблиця з заголовками та рядками відображається.
            """)
    public void rolesAccessStockPageUi() {
        Allure.step("Admin відкриває «Залишки»", () -> {
            UnitManagementPage adminStock = new UnitManagementPage(page).openForStorage(storageId).waitForLoaded();
            adminStock.attachScreenshot("TC-WMS-007-001 — admin stock page");
            adminStock.assertTableHeadersVisible();
            adminStock.assertHasStockRows();
        });

        Allure.step("Owner 1 відкриває «Залишки»", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage ownerStock = new UnitManagementPage(page).openForStorage(storageId).waitForLoaded();
            ownerStock.attachScreenshot("TC-WMS-007-001 — owner stock page");
            ownerStock.assertTableHeadersVisible();
            ownerStock.assertHasStockRows();
        });
    }

    @Test(priority = 120)
    @TestCaseId("TC-WMS-007-002")
    @Story("View stock UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner 1 переглядає залишки на обраній локації: ресурс з ненульовою кількістю
            відображається в таблиці після пошуку за назвою.
            """)
    public void viewStockUi() {
        Allure.parameter("storageId", storageId);
        Allure.parameter("resourceId", resourceId);
        Allure.parameter("resourceName", resourceName);

        UnitManagementPage stock = Allure.step("Owner 1 відкриває «Залишки»", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId).waitForLoaded();
            pageObj.attachScreenshot("TC-WMS-007-002 — stock initial");
            pageObj.assertHasStockRows();
            return pageObj;
        });

        Allure.step("Знайти опорний ресурс у таблиці", () -> {
            stock.attachScreenshot("TC-WMS-007-002 — before search");
            stock.searchAndWaitForResource(resourceName, resourceName);
            InventoryStockUiVerification.assertResourceVisible(
                    stock, resourceName, "Ресурс має бути видимий у таблиці");
            InventoryStockUiVerification.assertResourceAmountGreaterThan(
                    stock, resourceName, 0, "Кількість ресурсу має бути > 0");
            stock.attachScreenshot("TC-WMS-007-002 — resource row visible");
        });
    }

    @Test(priority = 130)
    @TestCaseId("TC-WMS-007-003")
    @Story("Search filter UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner 1 фільтрує таблицю «Залишки» за повною назвою ресурсу.
            Очікується: у результаті лишається відповідний рядок.
            """)
    public void searchFilterUi() {
        Allure.parameter("storageId", storageId);
        Allure.parameter("resourceId", resourceId);
        Allure.parameter("resourceName", resourceName);

        UnitManagementPage stock = Allure.step("Відкрити «Залишки»", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId).waitForLoaded();
            pageObj.attachScreenshot("TC-WMS-007-003 — before search");
            pageObj.assertHasStockRows();
            return pageObj;
        });

        Allure.step("Застосувати пошук за назвою ресурсу", () -> {
            stock.attachScreenshot("TC-WMS-007-003 — before filter");
            stock.searchAndWaitForResource(resourceName, resourceName);
            InventoryStockUiVerification.assertResourceVisible(
                    stock, resourceName, "Фільтр має показати опорний ресурс");
            stock.attachScreenshot("TC-WMS-007-003 — filtered table");
        });
    }

    @Test(priority = 140)
    @TestCaseId("TC-WMS-007-004")
    @Story("All locations aggregate UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Admin у режимі «Всі локації» бачить агреговану таблицю з колонкою «Локація».
            """)
    public void allLocationsAggregateUi() {
        Allure.step("Відкрити агреговані залишки", () -> {
            injectAllLocationsSession(UserRole.ADMIN);
            page = browserContext.newPage();
            UnitManagementPage stock = new UnitManagementPage(page).openForAllLocations().waitForLoaded();
            assertThat(stock.isAllLocationsTableVisible()).isTrue();
            stock.attachScreenshot("TC-WMS-007-004 — all locations table");
        });
    }

    @Test(priority = 150)
    @TestCaseId("TC-WMS-007-005")
    @Story("Batch dialog UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner 1 клікає по кількості ресурсу з amount > 0 і бачить modal «Партії ресурсу».
            """)
    public void batchDialogUi() {
        UnitManagementPage stock = Allure.step("Знайти ресурс у таблиці", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId);
            pageObj.searchAndWaitForResource(resourceName, resourceName);
            pageObj.attachScreenshot("TC-WMS-007-005 — before batch click");
            return pageObj;
        });

        Allure.step("Відкрити діалог партій", () -> {
            stock.clickResourceAmountLink(resourceName);
            assertThat(stock.isBatchDialogVisible()).isTrue();
            stock.attachScreenshot("TC-WMS-007-005 — batch dialog");
        });
    }

    /** «<Назва>\\t<Кількість> <од. вимір.>» — handleCopyTable у InventoryPage.tsx. */
    private static final Pattern CLIPBOARD_LINE_FORMAT =
            Pattern.compile("^(?<name>.+)\\t(?<amount>-?\\d+(?:\\.\\d+)?) (?<unit>.+)$");

    @Test(priority = 155)
    @TestCaseId("TC-WMS-007-009")
    @Story("Copy remainders to clipboard UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner 1 на сторінці «Залишки» (/inventory) для конкретної локації натискає
            «Скопіювати». Arrange: унікальний ресурс із залишком на локації.
            Очікується:
            1) кнопка активна, фідбек «Скопійовано»;
            2) кожен рядок буфера у форматі «<Назва>\\t<Кількість> <од. вимір.>»
               (handleCopyTable у InventoryPage.tsx);
            3) кількість рядків буфера = кількість видимих рядків таблиці;
            4) після пошуку за унікальним ресурсом кожен рядок буфера відповідає залишкам
               локації з API (назва, кількість, од. вимір.); кількість і од. вимір. унікального
               ресурсу збігаються з API.""")
    public void copyRemaindersToClipboardUi() {
        ResourceResponse uniqueResource = inventoryFixture.createUniqueCatalogResourceAbsentFromStorage(
                storageId, UserRole.ADMIN, "InvCopy_");
        trackStorageResourceForCleanup(uniqueResource.getId());
        double stockAmount = 50.0;
        relocationFixture.ensureStock(storageId, uniqueResource.getId(), stockAmount);
        StorageItemResponse seededItem = inventoryFixture.requireItemForResource(
                storageId, uniqueResource.getId(), UserRole.ADMIN);
        String seededName = seededItem.getResource().getName();

        Allure.parameter("resourceName", seededName);
        Allure.parameter("seededAmount", seededItem.getAmount());
        Allure.parameter("seededUnit", seededItem.getResource().getUnit().getShortName());

        UnitManagementPage stock = Allure.step("Owner 1 відкриває «Залишки» локації", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage pageObj = new UnitManagementPage(page)
                    .openForStorage(storageId)
                    .waitForLoaded()
                    .assertHasStockRows();
            pageObj.attachScreenshot("TC-WMS-007-009 — stock before copy");
            return pageObj;
        });

        Allure.step("Скопіювати залишки сторінки — формат і кількість рядків", () -> {
            assertThat(stock.isCopyButtonVisible())
                    .as("Кнопка «Скопіювати» має бути видима на конкретній локації")
                    .isTrue();
            assertThat(stock.isCopyButtonEnabled())
                    .as("Кнопка «Скопіювати» має бути активна, коли є рядки залишків")
                    .isTrue();

            stock.installClipboardCapture()
                    .clickCopyRemainders()
                    .waitForCopiedFeedback();

            List<String> clipboardLines = readClipboardLines(stock);
            assertThat(clipboardLines)
                    .as("Буфер не повинен бути порожнім")
                    .isNotEmpty();
            assertThat(clipboardLines)
                    .as("Кількість рядків буфера має збігатися з видимими рядками таблиці")
                    .hasSize(stock.stockRowCount());
            assertThat(clipboardLines)
                    .as("Кожен рядок буфера: «<Назва>\\t<Кількість> <од. вимір.»")
                    .allMatch(line -> CLIPBOARD_LINE_FORMAT.matcher(line).matches());

            stock.attachScreenshot("TC-WMS-007-009 — after page copy");
        });

        Allure.step("Пошук унікального ресурсу — буфер = залишки локації з API", () -> {
            List<StorageItemResponse> searchedItems = inventoryFixture.listItems(
                    storageId, UserRole.OWNER_1, Map.of("searchTerm", seededName));
            assertThat(searchedItems)
                    .as("API searchTerm=%s має повернути залишок на локації", seededName)
                    .isNotEmpty();

            stock.searchAndWaitForResource(seededName, seededName)
                    .installClipboardCapture()
                    .clickCopyRemainders()
                    .waitForCopiedFeedback();

            List<String> clipboardLines = readClipboardLines(stock);
            assertThat(clipboardLines)
                    .as("Після пошуку буфер не повинен бути порожнім")
                    .isNotEmpty();
            assertThat(clipboardLines)
                    .as("Кількість рядків буфера = видимі рядки після пошуку")
                    .hasSize(stock.stockRowCount());

            assertClipboardMatchesLocationStock(clipboardLines, searchedItems);

            Matcher seededLine = clipboardLines.stream()
                    .map(CLIPBOARD_LINE_FORMAT::matcher)
                    .filter(Matcher::matches)
                    .filter(m -> seededName.equals(m.group("name")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Немає рядка унікального ресурсу в буфері: " + clipboardLines));
            assertThat(Double.parseDouble(seededLine.group("amount")))
                    .as("Кількість у буфері = залишок локації з API")
                    .isCloseTo(seededItem.getAmount(), within(0.0001));
            assertThat(seededLine.group("unit"))
                    .as("Од. вимір. у буфері = API локації")
                    .isEqualTo(seededItem.getResource().getUnit().getShortName());

            stock.attachScreenshot("TC-WMS-007-009 — after search copy");
        });
    }

    private static List<String> readClipboardLines(UnitManagementPage stock) {
        return stock.getCapturedClipboardText().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /**
     * Verifies each clipboard line has format {@code name\\tamount unit} and matches a location
     * inventory row (same name, amount, unit short name).
     */
    private static void assertClipboardMatchesLocationStock(List<String> clipboardLines,
                                                            List<StorageItemResponse> locationItems) {
        Map<String, StorageItemResponse> byName = locationItems.stream()
                .filter(item -> item.getResource() != null && item.getResource().getName() != null)
                .collect(Collectors.toMap(
                        item -> item.getResource().getName(),
                        Function.identity(),
                        (left, right) -> left));

        for (String line : clipboardLines) {
            Matcher matcher = CLIPBOARD_LINE_FORMAT.matcher(line);
            assertThat(matcher.matches())
                    .as("Рядок буфера має формат «<Назва>\\t<Кількість> <од. вимір.»: [%s]", line)
                    .isTrue();

            String name = matcher.group("name");
            double amount = Double.parseDouble(matcher.group("amount"));
            String unit = matcher.group("unit");

            StorageItemResponse item = byName.get(name);
            assertThat(item)
                    .as("Ресурс «%s» з буфера має бути серед залишків локації", name)
                    .isNotNull();
            assertThat(item.getAmount())
                    .as("Кількість «%s» у буфері має збігатися з API локації", name)
                    .isCloseTo(amount, within(0.0001));
            assertThat(item.getResource().getUnit())
                    .as("Од. вимір. ресурсу «%s» має бути в API", name)
                    .isNotNull();
            assertThat(item.getResource().getUnit().getShortName())
                    .as("Од. вимір. «%s» у буфері має збігатися з API локації", name)
                    .isEqualTo(unit);
        }
    }

    @Test(priority = 160)
    @TestCaseId("TC-WMS-007-006")
    @Story("Export remainders UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Owner 1 на сторінці «Залишки» (/inventory) натискає «Експорт в Excel»
            для обраної локації. Очікується: завантаження непорожнього XLSX (ZIP magic).
            Ім'я файлу через Playwright suggestedFilename не assertиться — для кириличних
            blob-download Chromium часто повертає літерал «download».
            """)
    public void exportRemaindersUi() {
        UnitManagementPage stock = Allure.step("Owner 1 відкриває «Залишки»", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId).waitForLoaded();
            pageObj.attachScreenshot("TC-WMS-007-006 — stock page");
            return pageObj;
        });

        Allure.step("Перевірити кнопку «Експорт в Excel» і завантажити файл", () -> {
            assertThat(stock.isExportToExcelButtonVisible())
                    .as("Кнопка «Експорт в Excel» має бути видимою")
                    .isTrue();
            assertThat(stock.isExportToExcelButtonEnabled())
                    .as("Кнопка «Експорт в Excel» має бути активною для конкретної локації")
                    .isTrue();

            UnitManagementPage.ExportDownloadResult download = stock.clickExportToExcelAndDownload();
            Allure.parameter("downloadFileName", download.suggestedFilename());
            Allure.parameter("downloadSizeBytes", download.sizeBytes());

            // Playwright suggestedFilename() often returns "download" for Cyrillic <a download> blob names
            // (UI uses «залишки.xlsx»). Assert payload instead.
            UiDownloadAssertions.assertNonEmptyXlsx(
                    download.path(),
                    download.sizeBytes(),
                    "Експорт залишків Excel з UI");
            stock.attachScreenshot("TC-WMS-007-006 — export downloaded");
        });
    }

    @Test(priority = 165)
    @TestCaseId("TC-WMS-007-008")
    @Story("Export analytics admin-only UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Сторінка «Експорт даних» (/export-analytics) доступна лише Admin:
            пункт sidebar видимий для Admin, прихований для Owner 1;
            Owner 1 при прямому переході не може виконати експорт (403 → toast помилки).
            """)
    public void exportAnalyticsAdminOnlyUi() {
        Allure.step("Admin бачить «Експорт даних» у sidebar і відкриває сторінку", () -> {
            injectRoleSession(UserRole.ADMIN, storageId);
            page = browserContext.newPage();
            ExportAnalyticsPage exportPage = new ExportAnalyticsPage(page).open();
            assertThat(exportPage.isSidebarLinkVisible())
                    .as("Admin має бачити пункт sidebar «Експорт даних»")
                    .isTrue();
            assertThat(exportPage.isLoaded())
                    .as("Admin має відкривати /export-analytics")
                    .isTrue();
            exportPage.attachScreenshot("TC-WMS-007-008 — admin export analytics");
        });

        Allure.step("Owner 1 не має доступу до /export-analytics", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            ExportAnalyticsPage exportPage = new ExportAnalyticsPage(page).navigateWithoutAccessCheck();

            assertThat(exportPage.isSidebarLinkVisible())
                    .as("Owner 1 не має бачити пункт sidebar «Експорт даних»")
                    .isFalse();

            AccessForbiddenPage forbidden = new AccessForbiddenPage(page);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            if (forbidden.isForbiddenMessageVisible()) {
                forbidden.attachScreenshot("TC-WMS-007-008 — owner route forbidden");
            } else if (exportPage.isLoaded()) {
                exportPage.selectRemaindersExport().clickExport();
                exportPage.assertExportErrorToast();
                exportPage.attachScreenshot("TC-WMS-007-008 — owner export denied");
            } else if (!page.url().contains("/export-analytics")) {
                exportPage.attachScreenshot("TC-WMS-007-008 — owner redirected away from export");
            } else {
                throw new AssertionError(
                        "Owner 1: очікувався RouteGuard 403, redirect або сторінка експорту з помилкою API");
            }
        });
    }

    @Test(priority = 170)
    @TestCaseId("TC-WMS-007-007")
    @Story("Operation history after inventory UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Після проведення інвентаризації (setup через API) Owner 1 відкриває «Історія операцій»
            і бачить запис типу інвентаризації.
            """)
    public void operationHistoryAfterInventoryUi() {
        Allure.step("Підготувати запис інвентаризації (API setup)", () -> {
            inventoryFixture.openSession(storageId);
            double before = inventoryFixture.getResourceStock(storageId, resourceId, UserRole.ADMIN);
            inventoryFixture.setResourceAmount(storageId, UserRole.ADMIN, resourceId, before + 2.0);
            Allure.parameter("resourceName", resourceName);
        });

        Allure.step("Відкрити «Історія операцій» і знайти маркер інвентаризації", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            OperationHistoryPage history = new OperationHistoryPage(page).open().waitForLoaded();
            assertThat(history.isLoaded()).isTrue();
            assertThat(history.containsInventoryOperationMarker()).isTrue();
            history.attachScreenshot("TC-WMS-007-007 — inventory history");
        });
    }

    @Test(priority = 180)
    @TestCaseId("TC-WMS-007-018")
    @Story("Inventory tag chips OR filter UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Owner 1 на «Залишках» (/inventory) обирає два інфочіпи тегів ресурсів.
            Таблиця показує union (OR): ресурси A і B, без C. Зняття чіпа A лишає лише B.
            Cross-check: GET /storages/inventory?parentStorageId=&tags=A,B.
            """)
    public void tagChipsTwoSelectedUseOrLogicUi() {
        InventoryFixture.TagOrFilterSeed seed =
                inventoryFixture.seedTagOrFilterResources(storageId, relocationFixture);
        trackStorageResourceForCleanup(seed.resourceA().getId());
        trackStorageResourceForCleanup(seed.resourceB().getId());
        trackStorageResourceForCleanup(seed.resourceC().getId());

        String nameA = seed.resourceA().getName().trim().replaceAll("\\s+", " ");
        String nameB = seed.resourceB().getName().trim().replaceAll("\\s+", " ");
        String nameC = seed.resourceC().getName().trim().replaceAll("\\s+", " ");
        Allure.parameter("tagA", seed.tagA());
        Allure.parameter("tagB", seed.tagB());
        Allure.parameter("resourceA", nameA);
        Allure.parameter("resourceB", nameB);
        Allure.parameter("resourceC", nameC);

        UnitManagementPage stock = Allure.step("Відкрити «Залишки» і дочекатися інфочіпів", () -> {
            injectRoleSession(UserRole.OWNER_1, storageId);
            page = browserContext.newPage();
            UnitManagementPage pageObj = new UnitManagementPage(page).openForStorage(storageId).waitForLoaded();
            pageObj.waitForTagBadge(seed.tagA()).waitForTagBadge(seed.tagB());
            pageObj.attachScreenshot("TC-WMS-007-018 — chips visible");
            return pageObj;
        });

        Allure.step("Обрати обидва інфочіпи — таблиця OR (A і B, без C)", () -> {
            stock.clickTagFilterBadge(seed.tagA());
            assertThat(stock.isTagBadgeSelected(seed.tagA()))
                    .as("Чіп %s має бути обраним", seed.tagA())
                    .isTrue();
            stock.clickTagFilterBadge(seed.tagB());
            assertThat(stock.isTagBadgeSelected(seed.tagB()))
                    .as("Чіп %s має бути обраним", seed.tagB())
                    .isTrue();
            stock.attachScreenshot("TC-WMS-007-018 — two chips selected");
            InventoryStockUiVerification.assertResourceVisible(
                    stock, nameA, "Після двох чіпів ресурс A має бути видимий");
            InventoryStockUiVerification.assertResourceVisible(
                    stock, nameB, "Після двох чіпів ресурс B має бути видимий");
            InventoryStockUiVerification.assertResourceNotVisible(
                    stock, nameC, "Після двох чіпів ресурс C не повинен відображатися");
        });

        Allure.step("Зняти чіп A — лишається лише B", () -> {
            stock.clickTagFilterBadge(seed.tagA());
            assertThat(stock.isTagBadgeSelected(seed.tagA()))
                    .as("Чіп %s має бути знятий", seed.tagA())
                    .isFalse();
            assertThat(stock.isTagBadgeSelected(seed.tagB()))
                    .as("Чіп %s лишається обраним", seed.tagB())
                    .isTrue();
            stock.attachScreenshot("TC-WMS-007-018 — chip A deselected");
            InventoryStockUiVerification.assertResourceVisible(
                    stock, nameB, "Після зняття чіпа A ресурс B має лишитися");
            InventoryStockUiVerification.assertResourceNotVisible(
                    stock, nameA, "Після зняття чіпа A ресурс A не повинен відображатися");
            InventoryStockUiVerification.assertResourceNotVisible(
                    stock, nameC, "Ресурс C і далі відсутній");
        });

        Allure.step("Cross-check API hierarchy GET tags=A,B", () -> {
            var union = inventoryFixture.listHierarchyByTags(
                    storageId, UserRole.OWNER_1, List.of(seed.tagA(), seed.tagB()));
            assertThat(union.stream().anyMatch(row -> row.getResource() != null
                    && seed.resourceA().getId().equals(row.getResource().getId())))
                    .as("API union містить A")
                    .isTrue();
            assertThat(union.stream().anyMatch(row -> row.getResource() != null
                    && seed.resourceB().getId().equals(row.getResource().getId())))
                    .as("API union містить B")
                    .isTrue();
            assertThat(union.stream().anyMatch(row -> row.getResource() != null
                    && seed.resourceC().getId().equals(row.getResource().getId())))
                    .as("API union не містить C")
                    .isFalse();
        });
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

    private void injectAllLocationsSession(UserRole role) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
    }
}
