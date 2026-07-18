package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.storage.StorageDataFactory;
import com.erp.enums.StorageAccessMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ResourcesListPage;
import com.erp.tests.functional.storage.RestrictedUnitResourceSetup;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.PollUtils;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke: словник ресурсів на /resources фільтрується за sidebar workspace (UNIT).
 */
@Slf4j
@Epic("Master Data")
@Feature("Resources dictionary")
@Story("UNIT-scoped resource list")
public class UnitResourceDictionaryUiTest extends BaseUITest {

    private static final String SCENARIO_PREFIX = "ui-res-unit-";
    private static final String RESOURCE_PREFIX = "ui-res-unit-";

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private ResourceFixture resourceFixture;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupScenario() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupClassArtifacts() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test
    @TestCaseId("TC-UI-RES-UNIT-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_RES_UNIT_001)
    public void unitWorkspaceShowsOnlyGrantedResourcesInDictionary() {
        ResourceResponse granted = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "vis-");
        ResourceResponse hidden = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "hid-");
        String grantedName = normalizeResourceName(granted.getName());

        RestrictedUnitResourceSetup.Setup setup = Allure.step(
                "API: UNIT accessMode=REGIONS + RESOURCES region",
                () -> {
                    RestrictedUnitResourceSetup.Setup created = RestrictedUnitResourceSetup.createUnit(
                            storageFixture, regionFixture, SCENARIO_PREFIX + "dict-");
                    regionFixture.addRegionResources(created.region().getId(), granted.getId());
                    return created;
                });

        injectRoleSession(UserRole.ADMIN, setup.unit().getId());

        ResourcesListPage listPage = Allure.step(
                "UI: відкрити /resources з workspace=UNIT",
                () -> new ResourcesListPage(page).open(setup.unit().getId()));

        Allure.step("UI: таблиця містить granted і не містить hidden", () -> {
            listPage.searchByName(RESOURCE_PREFIX);
            assertThat(listPage.isResourceVisible(grantedName))
                    .as("Granted ресурс має бути у словнику UNIT")
                    .isTrue();
            assertThat(listPage.isResourceVisible(normalizeResourceName(hidden.getName())))
                    .as("Ресурс поза областю не має бути у словнику UNIT")
                    .isFalse();
            listPage.attachScreenshot("TC-UI-RES-UNIT-001 — scoped dictionary");
        });
    }

    @Test
    @TestCaseId("TC-UI-RES-UNIT-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_RES_UNIT_002)
    public void switchingWorkspaceChangesResourceDictionary() {
        ResourceResponse granted = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "sw-gr-");
        ResourceResponse extra = resourceFixture.createUniqueResource(RESOURCE_PREFIX + "sw-ex-");
        String grantedName = normalizeResourceName(granted.getName());
        String extraName = normalizeResourceName(extra.getName());

        RestrictedUnitResourceSetup.Setup restricted = Allure.step(
                "API: restricted UNIT + granted resource",
                () -> {
                    RestrictedUnitResourceSetup.Setup created = RestrictedUnitResourceSetup.createUnit(
                            storageFixture, regionFixture, SCENARIO_PREFIX + "rest-");
                    regionFixture.addRegionResources(created.region().getId(), granted.getId());
                    return created;
                });

        StorageResponse fullAccessUnit = Allure.step(
                "API: FULL_ACCESS UNIT для контрасту",
                () -> {
                    StorageResponse parent = storageFixture.resolveParentUnit();
                    return storageFixture.createStorage(
                            StorageDataFactory.unitStorage(parent.getId(), SCENARIO_PREFIX + "full-")
                                    .accessMode(StorageAccessMode.FULL_ACCESS)
                                    .build());
                });

        injectRoleSession(UserRole.ADMIN, restricted.unit().getId());

        ResourcesListPage listPage = new ResourcesListPage(page).open(restricted.unit().getId());
        listPage.searchByName(RESOURCE_PREFIX);

        Allure.step("UI: restricted UNIT — granted є, extra відсутній", () -> {
            assertThat(listPage.isResourceVisible(grantedName)).isTrue();
            assertThat(listPage.isResourceVisible(extraName)).isFalse();
            listPage.attachScreenshot("TC-UI-RES-UNIT-002 — restricted workspace");
        });

        Allure.step("UI: перемикання workspace на FULL_ACCESS UNIT", () -> {
            new AppSidebarPage(page).selectWorkspaceByName(fullAccessUnit.getName());
            listPage.waitForLoaded().searchByName(RESOURCE_PREFIX);
            PollUtils.waitUntilTrue(
                    () -> listPage.isResourceVisible(extraName),
                    10_000,
                    "Extra resource visible after workspace switch");
            assertThat(listPage.isResourceVisible(extraName))
                    .as("FULL_ACCESS workspace має показувати extra у глобальному словнику")
                    .isTrue();
            listPage.attachScreenshot("TC-UI-RES-UNIT-002 — full access workspace");
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

    private static String normalizeResourceName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
