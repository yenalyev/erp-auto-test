package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.DefectFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.pages.DefectsPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-DEF-001 / AC-08 UI: parent workspace shows defects from all structural children
 * (same scope as production/relocation/equipment journals — no per-child visibility filter).
 */
@Epic("Defects")
@Feature("Defect hierarchy filter UI")
public class DefectHierarchyUiTest extends BaseUITest {

    private DefectFixture defectFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        defectFixture = new DefectFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupStoragesAfterMethod() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupStoragesAfterClass() {
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-DEF-009")
    @Story("Parent workspace shows child-location defect row")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-DEF-001 AC-08: parent workspace lists child-only defect (all structural children
            in scope, not filtered by storage regions / allowedStorageIds per child).
            Arrange (API): parent + child, defect on child. UI: ADMIN, parent selected, /defects.
            Assert: row visible; «Локація» = child name.
            """)
    public void parentWorkspaceShowsChildDefect() {
        StorageResponse parent = storageFixture.createUniqueStorage("ui-def-hier-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "ui-def-hier-c-");

        ResourceResponse resource = resourceFixture.createUniqueResource("ui-def-hier-res-");
        relocationFixture.ensureStock(child.getId(), resource.getId(), 10.0);
        defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(child.getId(), resource.getId(), 4.0));

        String resourceName = resource.getName().trim();
        injectRoleSession(UserRole.ADMIN, parent.getId());

        DefectsPage defectsPage = new DefectsPage(page).open();
        defectsPage.attachScreenshot("TC-UI-DEF-009 — parent workspace, defects loaded");

        defectsPage.revealResource(resourceName);

        assertThat(defectsPage.isRowWithResourceVisible(resourceName))
                .as("Child-only defect must appear when parent workspace is selected")
                .isTrue();
        assertThat(defectsPage.getLocationNameForResource(resourceName))
                .as("Location column must show the child storage name")
                .contains(child.getName().trim());
        assertThat(defectsPage.getRemainingAmount(resourceName)).contains("4");
        defectsPage.attachScreenshot("TC-UI-DEF-009 — child defect visible under parent");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-DEF-010")
    @Story("Child workspace still shows its own defect")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Control: selecting the child workspace directly still lists the same defect row.
            """)
    public void childWorkspaceShowsOwnDefect() {
        StorageResponse parent = storageFixture.createUniqueStorage("ui-def-hier2-p-");
        StorageResponse child = storageFixture.createChildStorage(parent.getId(), "ui-def-hier2-c-");

        ResourceResponse resource = resourceFixture.createUniqueResource("ui-def-hier2-res-");
        relocationFixture.ensureStock(child.getId(), resource.getId(), 10.0);
        defectFixture.createAs(UserRole.ADMIN,
                DefectDataFactory.buildStorageFifoDefect(child.getId(), resource.getId(), 5.0));

        String resourceName = resource.getName().trim();
        injectRoleSession(UserRole.ADMIN, child.getId());

        DefectsPage defectsPage = new DefectsPage(page).open();
        defectsPage.revealResource(resourceName);

        assertThat(defectsPage.isRowWithResourceVisible(resourceName)).isTrue();
        assertThat(defectsPage.getLocationNameForResource(resourceName))
                .contains(child.getName().trim());
        defectsPage.attachScreenshot("TC-UI-DEF-010 — child workspace defect row");
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
