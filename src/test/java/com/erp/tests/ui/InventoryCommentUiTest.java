package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.StorageRelation;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.CrewRegionFixture;
import com.erp.fixtures.CrewRegionFixture.CrewRegionScenario;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.InventoryEditPage;
import com.erp.pages.OperationHistoryPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * REQ-WMS-003 AC-15 — UI comment field on /inventory/{id} for all supported location types.
 */
@Slf4j
@Epic("Inventory")
@Feature("REQ-WMS-003 Comment UI")
public class InventoryCommentUiTest extends BaseUITest {

    private static final String PREFIX = "ui-inv-cmt-";
    private static final double STOCK_AMOUNT = 25.0;
    private static final double ISSUE_AMOUNT = 10.0;

    private record UiLocationSeed(String label, long storageId, long resourceId, String resourceName) {}

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private CrewRegionFixture crewFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private InventoryFixture inventoryFixture;

    private Long parentId;
    private UiLocationSeed unitSeed;
    private UiLocationSeed storageSeed;
    private UiLocationSeed productionSeed;
    private UiLocationSeed flyPointSeed;
    private UiLocationSeed crewSeed;
    private Long sessionStorageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        crewFixture = new CrewRegionFixture(testContext, apiExecutor, storageFixture, regionFixture);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);

        storageFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
        relocationFixture.prepareContext();

        StorageResponse member = storageFixture.getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        parentId = member.getParent() != null ? member.getParent().getId() : member.getId();

        unitSeed = seedSimpleLocation(UnitType.UNIT, PREFIX + "unit-");
        storageSeed = seedSimpleLocation(UnitType.STORAGE, PREFIX + "stor-");
        productionSeed = seedSimpleLocation(UnitType.PRODUCTION, PREFIX + "prod-");
        flyPointSeed = seedFlyPointLocation(PREFIX + "fp-");
        crewSeed = seedUnattachedCrewLocation(PREFIX + "crew-");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupUiSession() {
        if (sessionStorageId != null) {
            try {
                inventoryFixture.ensureClosed(sessionStorageId);
            } catch (Exception e) {
                log.warn("UI comment test session cleanup failed for {}: {}", sessionStorageId, e.getMessage());
            }
            sessionStorageId = null;
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanupArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @DataProvider(name = "supportedInventoryUiLocationTypes")
    public Object[][] supportedInventoryUiLocationTypes() {
        return new Object[][]{
                {unitSeed},
                {storageSeed},
                {productionSeed},
                {flyPointSeed},
                {crewSeed}
        };
    }

    @Test(priority = 10, dataProvider = "supportedInventoryUiLocationTypes")
    @TestCaseId("TC-WMS-003-017")
    @Story("UI comment visible in operation history")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            tk-ui InventoryEditPage: textarea «Коментар», PUT comment + resources snapshot,
            then verify text in OperationHistoryTable «Коментар» column.
            """)
    public void inventoryCommentVisibleInOperationHistoryUi(UiLocationSeed seed) {
        Allure.parameter("locationType", seed.label());
        Allure.parameter("storageId", seed.storageId());
        String comment = "UI коментар " + seed.label() + " " + seed.storageId();

        inventoryFixture.openSession(seed.storageId());
        sessionStorageId = seed.storageId();
        double before = inventoryFixture.getResourceStock(seed.storageId(), seed.resourceId(), UserRole.ADMIN);
        double target = before + 4.0;

        injectRoleSession(UserRole.ADMIN, seed.storageId());
        page = browserContext.newPage();

        InventoryEditPage form = Allure.step("Відкрити форму інвентаризації", () -> {
            InventoryEditPage editPage = new InventoryEditPage(page).open(seed.storageId()).waitForLoaded();
            assertThat(editPage.isCommentFieldVisible()).isTrue();
            assertThat(editPage.commentCounterShows(0, 1000)).isTrue();
            editPage.attachScreenshot("TC-WMS-003-017 — comment field " + seed.label());
            return editPage;
        });

        Allure.step("Змінити кількість, ввести comment і зберегти", () -> {
            form.updateAmountForResource(seed.resourceName(), String.valueOf((int) target))
                    .fillComment(comment)
                    .saveChanges();
            form.attachScreenshot("TC-WMS-003-017 — saved " + seed.label());
        });

        assertThat(inventoryFixture.getResourceStock(seed.storageId(), seed.resourceId(), UserRole.ADMIN))
                .isCloseTo(target, within(0.01));

        Allure.step("Перевірити comment у «Історія операцій»", () -> {
            OperationHistoryPage history = new OperationHistoryPage(page).open();
            history.attachScreenshot("TC-WMS-003-017 — history " + seed.label());
            assertThat(history.tableContainsCommentForResource(seed.resourceName(), comment))
                    .as("Колонка «Коментар» має містити текст для %s", seed.resourceName())
                    .isTrue();
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-WMS-003-018")
    @Story("Save disabled without stock changes")
    @Severity(SeverityLevel.CRITICAL)
    @Description("InventoryEditPage.hasChanges() ignores comment — «Зберегти» disabled without amount delta.")
    public void saveDisabledWhenOnlyCommentFilled() {
        inventoryFixture.openSession(unitSeed.storageId());
        sessionStorageId = unitSeed.storageId();

        injectRoleSession(UserRole.ADMIN, unitSeed.storageId());
        page = browserContext.newPage();

        InventoryEditPage form = Allure.step("Відкрити форму без змін залишків", () -> {
            InventoryEditPage editPage = new InventoryEditPage(page).open(unitSeed.storageId()).waitForLoaded();
            editPage.attachScreenshot("TC-WMS-003-018 — form loaded");
            return editPage;
        });

        Allure.step("Перевірити disabled «Зберегти»", () -> {
            assertThat(form.isSaveEnabled())
                    .as("Без зміни кількостей «Зберегти» має бути disabled")
                    .isFalse();
        });

        Allure.step("Заповнити comment — «Зберегти» лишається disabled", () -> {
            form.fillComment("Лише коментар без зміни залишків");
            form.attachScreenshot("TC-WMS-003-018 — comment filled");
            assertThat(form.isSaveEnabled())
                    .as("Comment alone must not enable save")
                    .isFalse();
        });
    }

    private UiLocationSeed seedSimpleLocation(UnitType type, String prefix) {
        StorageResponse location = storageFixture.createChildStorage(
                parentId, prefix, type, StorageRelation.INTERNAL);
        ResourceResponse resource = resourceFixture.createUniqueResource(prefix + "res-");
        relocationFixture.ensureStock(location.getId(), resource.getId(), STOCK_AMOUNT);
        return new UiLocationSeed(
                type.name(),
                location.getId(),
                resource.getId(),
                normalizeResourceName(resource.getName()));
    }

    private UiLocationSeed seedFlyPointLocation(String prefix) {
        CrewRegionScenario scenario = crewFixture.prepareFlyPointScenario(prefix);
        ResourceResponse resource = resourceFixture.createUniqueResource(prefix + "res-");
        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.flyPoint().getId(),
                resource.getId(),
                ISSUE_AMOUNT);
        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);
        return new UiLocationSeed(
                UnitType.FLY_POINT.name(),
                scenario.flyPoint().getId(),
                resource.getId(),
                normalizeResourceName(resource.getName()));
    }

    private UiLocationSeed seedUnattachedCrewLocation(String prefix) {
        CrewRegionScenario scenario = crewFixture.prepareSingleCrewScenario(prefix);
        ResourceResponse resource = resourceFixture.createUniqueResource(prefix + "res-");
        relocationFixture.ensureStock(scenario.memberStorageId(), resource.getId(), 100.0);
        relocationFixture.createSendAndFinishBySender(
                UserRole.OWNER_1,
                scenario.memberStorageId(),
                scenario.crew().getId(),
                resource.getId(),
                ISSUE_AMOUNT);
        refreshRoleSessions(UserRole.OWNER_1, UserRole.ADMIN);
        return new UiLocationSeed(
                UnitType.CREW.name(),
                scenario.crew().getId(),
                resource.getId(),
                normalizeResourceName(resource.getName()));
    }

    private void refreshRoleSessions(UserRole... roles) {
        for (UserRole role : roles) {
            authService.invalidateSession(role.getUsername(), role.getPassword());
        }
        apiExecutor.clearSessionCache();
    }

    private static String normalizeResourceName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
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
