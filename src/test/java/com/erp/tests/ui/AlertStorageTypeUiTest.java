package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.BusinessRole;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.StorageResponse;
import com.erp.pages.StorageAlertsPage;
import com.erp.pages.UnitManagementPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Page;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-ALERT")
@Slf4j
public class AlertStorageTypeUiTest extends BaseUITest {

    private AlertFixture alertFixture;
    private InventoryFixture inventoryFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private UserFixture userFixture;
    private Long parentId;
    private final List<AlertFixture.TypedAlertSeed> createdSeeds = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        alertFixture = new AlertFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();

        StorageResponse member = storageFixture.getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        parentId = member.getParent() != null ? member.getParent().getId() : member.getId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupStoragesAfterMethod() {
        cleanupCreatedSeeds();
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupStoragesAfterClass() {
        cleanupCreatedSeeds();
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @DataProvider(name = "inventoryStorageTypes")
    public Object[][] inventoryStorageTypes() {
        return new Object[][]{
                {UnitType.STORAGE},
                {UnitType.UNIT},
                {UnitType.PRODUCTION},
                {UnitType.CREW},
                {UnitType.FLY_POINT}
        };
    }

    @Test(priority = 10, dataProvider = "inventoryStorageTypes")
    @TestCaseId("TC-UI-ALERT-003")
    @Story("Alerts page for every storage type")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Дефект: для UNIT на /alerts/{storageId} сповіщення по залишках не відображаються.
            На /inventory: спочатку рядки з налаштованим алертом, потім решта
            (zzz-alert вище aaa-plain попри алфавіт); бейдж «відсутній» + «(мін. 10)».
            Далі /alerts/{id} показує збережений поріг.
            """)
    public void alertsPageShowsThresholdForEveryStorageType(UnitType type) {
        Allure.parameter("storageType", type.name());
        AlertFixture.TypedAlertSeed seed = alertFixture.seedAlertForType(
                storageFixture, resourceFixture, inventoryFixture,
                parentId, type, AlertFixture.DEFAULT_LIMIT);
        createdSeeds.add(seed);
        boolean detachedLocation = type == UnitType.CREW || type == UnitType.FLY_POINT;
        StorageResponse workspace = detachedLocation
                ? storageFixture.getById(UserRole.ADMIN, seed.storage().getParent().getId())
                : seed.storage();
        UserFixture.BusinessActor owner = userFixture.createBusinessActor(
                getPlaywrightSessionProvider(),
                detachedLocation ? BusinessRole.UNIT_KOMIRNIK : BusinessRole.BUSINESS_UNIT_OWNER,
                detachedLocation ? List.of(workspace, seed.storage()) : List.of(seed.storage()));
        injectOwnerSession(owner, seed.storage().getId());

        UnitManagementPage stock = openInventory(seed.storage().getId())
                .setShowZeroStock(true)
                .searchAndWaitForResource(seed.searchToken(), seed.alerted().getName());
        if (!stock.isResourceVisibleInTable(seed.plain().getName())) {
            stock.refreshInventoryTable().searchAndWaitForResource(seed.searchToken(), seed.plain().getName());
        }
        page.waitForCondition(
                () -> stock.indexOfResource(seed.alerted().getName()) >= 0
                        && stock.indexOfResource(seed.plain().getName()) >= 0,
                new Page.WaitForConditionOptions().setTimeout(30_000));
        int alertedIdx = stock.indexOfResource(seed.alerted().getName());
        int plainIdx = stock.indexOfResource(seed.plain().getName());
        assertThat(alertedIdx).as("рядок з алертом знайдено type=%s", type).isGreaterThanOrEqualTo(0);
        assertThat(plainIdx).as("рядок без алерту знайдено type=%s", type).isGreaterThanOrEqualTo(0);
        assertThat(alertedIdx)
                .as("спочатку залишки з алертом, потім решта (type=%s)", type)
                .isLessThan(plainIdx);
        stock.waitForInventoryTableSettled();
        page.waitForCondition(
                () -> stock.hasStatusBadge(seed.alerted().getName(), UnitManagementPage.BADGE_ABSENT),
                new Page.WaitForConditionOptions().setTimeout(30_000));
        assertThat(stock.hasStatusBadge(seed.alerted().getName(), UnitManagementPage.BADGE_ABSENT))
                .as("бейдж «відсутній» на /inventory type=%s", type)
                .isTrue();
        assertThat(stock.hasStatusBadge(seed.plain().getName(), UnitManagementPage.BADGE_ABSENT)).isFalse();
        assertThat(stock.hasStatusBadge(seed.plain().getName(), UnitManagementPage.BADGE_BELOW_NORM)).isFalse();
        assertThat(stock.hasStatusBadge(seed.plain().getName(), UnitManagementPage.BADGE_ENOUGH)).isFalse();
        String locationCell = stock.getLocationCellText(seed.alerted().getName());
        assertThat(locationCell).as("підпис мін. на /inventory type=%s", type).contains("мін.");
        assertThat(locationCell).contains(String.valueOf((int) AlertFixture.DEFAULT_LIMIT));
        stock.attachScreenshot("TC-UI-ALERT-003 inventory — " + type.name());

        StorageAlertsPage alerts;
        if (stock.isConfigureAlertsButtonVisible()) {
            alerts = stock.openConfigureAlerts();
        } else {
            Allure.step("Кнопка «Налаштувати сповіщення» відсутня — deep-link /alerts/" + seed.storage().getId());
            alerts = new StorageAlertsPage(page).open(seed.storage().getId());
        }

        assertThat(alerts.isOnAlertsPath(seed.storage().getId()))
                .as("URL /alerts/%s for type %s", seed.storage().getId(), type)
                .isTrue();
        assertThat(alerts.showsResource(seed.resource().getName()))
                .as("ресурс на /alerts/%s type=%s не порожній", seed.storage().getId(), type)
                .isTrue();
        assertThat(alerts.showsThreshold(seed.resource().getName(), AlertFixture.DEFAULT_LIMIT))
                .as("поріг %s на type=%s", (int) AlertFixture.DEFAULT_LIMIT, type)
                .isTrue();

        alerts.attachScreenshot("TC-UI-ALERT-003 alerts — " + type.name());
    }

    private UnitManagementPage openInventory(long storageId) {
        return new UnitManagementPage(page).openWithStorageIdQuery(storageId, false);
    }

    private void cleanupCreatedSeeds() {
        if (createdSeeds.isEmpty()) {
            return;
        }
        if (TestArtifactCleanup.shouldSkipApiCleanup()) {
            createdSeeds.clear();
            return;
        }
        for (AlertFixture.TypedAlertSeed seed : createdSeeds) {
            try {
                alertFixture.removeResourceFromAlerts(
                        seed.storage().getId(), seed.alerted().getId(), UserRole.ADMIN);
            } catch (RuntimeException e) {
                log.warn("Could not remove alert for resource {}", seed.alerted().getId(), e);
            }
            for (var resource : List.of(seed.alerted(), seed.plain())) {
                try {
                    inventoryFixture.removeResourceFromStorage(
                            seed.storage().getId(), resource.getId(), UserRole.ADMIN);
                } catch (RuntimeException e) {
                    log.warn("Could not clear resource {} from storage {}",
                            resource.getId(), seed.storage().getId(), e);
                }
                try {
                    var response = resourceFixture.deactivate(UserRole.ADMIN, resource.getId());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        log.warn("Could not deactivate resource {}: HTTP {}",
                                resource.getId(), response.statusCode());
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not deactivate resource {}", resource.getId(), e);
                }
            }
        }
        createdSeeds.clear();
    }

    private void injectOwnerSession(UserFixture.BusinessActor owner, long selectedStorageId) {
        browserContext.clearCookies();
        Map<String, String> cookies = authService.getSessionForUser(owner.username(), owner.password());
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');"
                        + "localStorage.setItem('selectedStorageId:" + owner.username() + "', '"
                        + selectedStorageId + "');");
    }
}
