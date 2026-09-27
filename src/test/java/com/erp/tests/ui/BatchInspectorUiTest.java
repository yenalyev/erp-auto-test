package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.BatchInspectionResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.AccessForbiddenPage;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.BatchInspectorPage;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-WMS-012 — Batch Inspector UI")
public class BatchInspectorUiTest extends BaseUITest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private UserFixture userFixture;
    private UserFixture.BusinessActor locationOwner;
    private RelocationResponse received;
    private String batchName;
    private String resourceName;
    private String storageName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        inventoryFixture.prepareContext();

        long storageId = ConfigProvider.getOwner1StorageId();
        var ownerStorage = new StorageFixture(testContext, apiExecutor)
                .getById(UserRole.ADMIN, storageId);
        locationOwner = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(ownerStorage));
        Long resourceId = testContext.get(com.erp.test_context.ContextKey.RELOCATION_RESOURCE_ID);
        batchName = "WMS12-UI-" + System.currentTimeMillis();
        received = relocationFixture.createExternalReceive(
                UserRole.ADMIN, storageId, resourceId, 6.0, batchName);
        BatchInspectionResponse inspection = inventoryFixture.inspectBatch(batchName, UserRole.ADMIN).getFirst();
        resourceName = inspection.getResource().getName().trim();
        storageName = inspection.getStock().getFirst().getStorage().getName().trim();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupBatchInspectorData() {
        if (received != null) {
            try {
                relocationFixture.deleteRelocation(
                        UserRole.ADMIN, received.getId(), ConfigProvider.getOwner1StorageId());
            } catch (RuntimeException ignored) {
                // Suite-level artifact cleanup retries this relocation if dev rejects immediate rollback.
            }
        }
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-WMS-012-009")
    @Story("Admin searches a real batch and sees inspection data")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Admin відкриває окрему сторінку Batch Inspector, шукає партію за назвою
            і бачить ресурс, відсутні для зовнішньої партії дату/техкарту,
            рух по складах та фактичний залишок.
            """)
    public void adminSearchesBatchAndSeesMovementAndActualStock() {
        prepareSession(UserRole.ADMIN);

        BatchInspectorPage inspector = new BatchInspectorPage(page)
                .open()
                .search(batchName);

        assertThat(inspector.hasInspection(batchName)).isTrue();
        assertThat(inspector.currentUrl()).contains("/batch-inspector").contains("name=");
        assertThat(inspector.definitionValue(batchName, "Продукт")).contains(resourceName);
        assertThat(inspector.definitionValue(batchName, "Дата виробництва"))
                .isIn("—", "Не вироблялась у системі");
        assertThat(inspector.definitionValue(batchName, "Технологічна карта"))
                .isIn("—", "Не вироблялась у системі");
        assertThat(inspector.sectionRows(batchName, "Рух по складах"))
                .anySatisfy(row -> assertThat(row).contains(storageName, "6"));
        assertThat(inspector.sectionRows(batchName, "Залишок зараз"))
                .anySatisfy(row -> assertThat(row).contains(storageName, "6"));
        inspector.attachScreenshot("TC-WMS-012-009 — admin batch inspection");
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-012-010")
    @Story("Unknown batch has a dedicated empty state")
    @Severity(SeverityLevel.NORMAL)
    public void unknownBatchShowsNotFoundState() {
        prepareSession(UserRole.ADMIN);
        String unknown = "WMS12-UNKNOWN-" + UUID.randomUUID();

        BatchInspectorPage inspector = new BatchInspectorPage(page).open().search(unknown);

        assertThat(inspector.isNotFoundVisible(unknown)).isTrue();
        inspector.attachScreenshot("TC-WMS-012-010 — batch not found");
    }

    @Test(priority = 30)
    @TestCaseId("TC-WMS-012-014")
    @Story("Available invoice number is interactive")
    @Severity(SeverityLevel.CRITICAL)
    @Description("За canGenerateInvoice=true номер накладної відображається кнопкою завантаження.")
    public void availableInvoiceNumberIsInteractive() {
        prepareSession(UserRole.ADMIN);
        String mockedBatch = "WMS12-DOC";
        String invoice = "WMS12-DOC-001";
        page.route("**/api/v1/batches/inspect**", route -> route.fulfill(
                new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody(json(mockInspection(mockedBatch, invoice)))));

        BatchInspectorPage inspector = new BatchInspectorPage(page).open().search(mockedBatch);

        assertThat(inspector.hasInspection(mockedBatch)).isTrue();
        assertThat(inspector.isDocumentInteractive(mockedBatch, invoice))
                .as("Доступна накладна має відображатися інтерактивною кнопкою")
                .isTrue();
        inspector.attachScreenshot("TC-WMS-012-014 — invoice action is available");
    }

    @Test(priority = 40)
    @TestCaseId("TC-WMS-012-013")
    @Story("Batch Inspector is hidden and forbidden for non-admin")
    @Severity(SeverityLevel.BLOCKER)
    public void locationOwnerCannotSeeOrOpenBatchInspector() {
        prepareSession(locationOwner);
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        new ProductionPage(page).waitForLoaded();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        assertThat(sidebar.isNavItemVisible(BatchInspectorPage.NAV_LABEL))
                .as("Owner не бачить entry point Batch Inspector")
                .isFalse();

        page.navigate(ConfigProvider.getBaseUrl() + BatchInspectorPage.PATH);
        page.waitForTimeout(2_000);
        AccessForbiddenPage forbidden = new AccessForbiddenPage(page);
        boolean accessDenied = forbidden.isForbiddenMessageVisible()
                || !page.url().contains(BatchInspectorPage.PATH);
        assertThat(accessDenied)
                .as("Власник локації має отримати 403 або redirect з /batch-inspector, url=%s", page.url())
                .isTrue();
        forbidden.attachScreenshot("TC-WMS-012-013 — owner forbidden");
    }

    private void prepareSession(UserRole role) {
        browserContext.clearCookies();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
    }

    private void prepareSession(UserFixture.BusinessActor actor) {
        browserContext.clearCookies();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(actor.username(), actor.password());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
    }

    private static List<Map<String, Object>> mockInspection(String name, String invoice) {
        return List.of(Map.of(
                "id", UUID.randomUUID().toString(),
                "name", name,
                "resource", Map.of("id", 42, "name", "Контрольний ресурс"),
                "legacy", false,
                "createdAt", "2026-09-25T10:00:00Z",
                "productions", List.of(),
                "ingredients", List.of(),
                "movements", List.of(Map.ofEntries(
                        Map.entry("relocationId", 501),
                        Map.entry("dateTime", "2026-09-25T10:15:30Z"),
                        Map.entry("sender", Map.of("id", 1, "name", "Склад A")),
                        Map.entry("recipient", Map.of("id", 2, "name", "Склад B")),
                        Map.entry("state", "FINISHED"),
                        Map.entry("invoiceNumber", invoice),
                        Map.entry("canGenerateInvoice", true),
                        Map.entry("hasExternalInvoicePhoto", false),
                        Map.entry("resource", Map.of("id", 42, "name", "Контрольний ресурс")),
                        Map.entry("amount", 2),
                        Map.entry("unit", "шт"))),
                "stock", List.of(Map.of(
                        "storage", Map.of("id", 2, "name", "Склад B"),
                        "resource", Map.of("id", 42, "name", "Контрольний ресурс"),
                        "amount", 2,
                        "unit", "шт"))));
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize Batch Inspector mock", e);
        }
    }
}
