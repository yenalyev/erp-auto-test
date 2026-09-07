package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UnitType;
import com.erp.enums.UserRole;
import com.erp.fixtures.AlertFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.StorageResponse;
import com.erp.pages.StorageAlertsPage;
import com.erp.pages.UnitManagementPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.Allure;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-ALERT")
public class AlertStorageTypeUiTest extends BaseUITest {

    private AlertFixture alertFixture;
    private InventoryFixture inventoryFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private Long parentId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        alertFixture = new AlertFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();

        StorageResponse member = storageFixture.getById(UserRole.ADMIN, ConfigProvider.getOwner1StorageId());
        parentId = member.getParent() != null ? member.getParent().getId() : member.getId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupStoragesAfterMethod() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupStoragesAfterClass() {
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
        injectRoleSession(UserRole.ADMIN, seed.storage().getId());

        UnitManagementPage stock = openInventory(type, seed.storage().getId())
                .searchAndWaitForResource(seed.searchToken(), seed.alerted().getName());
        if (!stock.isResourceVisibleInTable(seed.plain().getName())) {
            stock.refreshInventoryTable().searchAndWaitForResource(seed.searchToken(), seed.plain().getName());
        }
        stock.waitForResourceInTable(seed.plain().getName());
        int alertedIdx = stock.indexOfResource(seed.alerted().getName());
        int plainIdx = stock.indexOfResource(seed.plain().getName());
        assertThat(alertedIdx).as("рядок з алертом знайдено type=%s", type).isGreaterThanOrEqualTo(0);
        assertThat(plainIdx).as("рядок без алерту знайдено type=%s", type).isGreaterThanOrEqualTo(0);
        assertThat(alertedIdx)
                .as("спочатку залишки з алертом, потім решта (type=%s)", type)
                .isLessThan(plainIdx);
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

    private UnitManagementPage openInventory(UnitType type, long storageId) {
        UnitManagementPage stock = new UnitManagementPage(page);
        if (type == UnitType.CREW || type == UnitType.FLY_POINT) {
            return stock.openWithStorageIdQuery(storageId, false);
        }
        return stock.openForStorage(storageId).waitForLoaded();
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        Map<String, String> cookies = cachedSessionCookies(role);
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
