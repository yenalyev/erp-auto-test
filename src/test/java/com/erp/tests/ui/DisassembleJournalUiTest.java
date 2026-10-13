package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.enums.BusinessRole;
import com.erp.fixtures.DisassembleFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.models.response.DisassembleItemResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Production")
@Feature("Журнал розбору")
public class DisassembleJournalUiTest extends BaseUITest {

    private DisassembleFixture fixture;
    private StorageFixture storageFixture;
    private ResourceFixture resourceFixture;
    private UserFixture userFixture;
    private UserFixture.BusinessActor ownerActor;
    private TechnologicalMapResponse techMap;
    private long storageId;
    private long inputResourceId;
    private long outputResourceId;
    private String inputResourceName;
    private Long createdDisassembleId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new DisassembleFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        StorageResponse storage = storageFixture.createProductionStorage(
                storageFixture.resolveParentUnit().getId(), "ui-dis-journal-");
        storageId = storage.getId();
        ResourceResponse input = resourceFixture.createUniqueResource("ui-dis-journal-in-");
        ResourceResponse output = resourceFixture.createUniqueResource("ui-dis-journal-out-");
        inputResourceId = input.getId();
        outputResourceId = output.getId();
        inputResourceName = input.getName().trim();
        techMap = fixture.createDisassembleTechMapAs(UserRole.ADMIN, storageId,
                List.of(input, output));
        fixture.seedInputStock(UserRole.ADMIN, storageId, inputResourceId);
        userFixture = new UserFixture(testContext, apiExecutor);
        ownerActor = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(storage));
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupDisassemble() {
        if (createdDisassembleId != null) {
            Response deleted = fixture.deleteRaw(UserRole.ADMIN, createdDisassembleId, storageId);
            assertThat(deleted.statusCode()).as(deleted.asString()).isIn(200, 204);
            createdDisassembleId = null;
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanupStorageAndUser() {
        if (techMap != null) {
            new TechnologicalMapFixture(testContext, apiExecutor)
                    .deactivateTechMap(UserRole.ADMIN, techMap.getId(), storageId);
        }
        if (resourceFixture != null && storageId != 0) {
            InventoryFixture inventory = new InventoryFixture(testContext, apiExecutor);
            if (inputResourceId != 0) {
                inventory.removeResourceFromStorage(storageId, inputResourceId, UserRole.ADMIN);
                resourceFixture.deactivate(UserRole.ADMIN, inputResourceId);
            }
            if (outputResourceId != 0) {
                inventory.removeResourceFromStorage(storageId, outputResourceId, UserRole.ADMIN);
                resourceFixture.deactivate(UserRole.ADMIN, outputResourceId);
            }
        }
        if (storageFixture != null) {
            storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        }
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
    }

    @TestCaseId("TC-UI-DIS-JOURNAL-001")
    @Test
    @Story("Керівник і адміністратор бачать запис розбору")
    @Severity(SeverityLevel.NORMAL)
    @Description("Керівник локації й адміністратор відкривають журнал виробництва, вибирають «Розбір» і бачать запис цієї локації.")
    public void ownerAndAdminSeeDisassembleInJournal() {
        String batchNumber = "journal-dis-" + System.currentTimeMillis() % 1_000_000;
        Response created = fixture.createAs(UserRole.ADMIN, storageId, techMap, 1.0, 1.0, batchNumber);
        List<DisassembleItemResponse> items = created.jsonPath().getList("", DisassembleItemResponse.class);
        createdDisassembleId = items.getFirst().getId();

        assertJournalShowsBatch(UserRole.OWNER_1, batchNumber);
        assertJournalShowsBatch(UserRole.ADMIN, batchNumber);
    }

    private void assertJournalShowsBatch(UserRole role, String batchNumber) {
        injectRoleSession(role);
        new ProductionPage(page).open();
        page.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Тип робіт"))
                .locator("xpath=following-sibling::button[1]").first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("Розбір")).click();
        page.getByPlaceholder("Пошук...").fill(inputResourceName);

        page.waitForCondition(() -> page.locator("tbody tr")
                        .filter(new Locator.FilterOptions().setHasText(batchNumber)).count() > 0,
                new Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000L));
        assertThat(page.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(batchNumber)).first().innerText())
                .contains(inputResourceName, batchNumber);
    }

    private void injectRoleSession(UserRole role) {
        String username = role == UserRole.OWNER_1 ? ownerActor.username() : role.getUsername();
        String password = role == UserRole.OWNER_1 ? ownerActor.password() : role.getPassword();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(username, password);
        String domain = ConfigProvider.getBaseUrl().replaceFirst("https?://", "").split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId:" + username + "', '" + storageId + "');");
    }

}
