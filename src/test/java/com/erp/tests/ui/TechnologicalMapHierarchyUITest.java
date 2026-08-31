package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TechnologicalMapHierarchyFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.TechnologicalMapsListPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-MFG-035 UI: selecting a STORAGE or PRODUCTION parent lists the entire subtree
 * (including maps on locations without access); selecting leaf A lists only A's map.
 */
@Slf4j
@Epic("Technological Maps")
@Feature("REQ-MFG-001-03 Tech map list hierarchy UI")
public class TechnologicalMapHierarchyUITest extends BaseUITest {

    private StorageFixture storageFixture;
    private StorageRegionFixture regionFixture;
    private TechnologicalMapHierarchyFixture hierarchyFixture;
    private TechnologicalMapHierarchyFixture.Seed seed;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        regionFixture = new StorageRegionFixture(testContext, apiExecutor);
        hierarchyFixture = new TechnologicalMapHierarchyFixture(
                testContext, apiExecutor, storageFixture, regionFixture, getPlaywrightSessionProvider());
        seed = hierarchyFixture.acquireAndSeed();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupHierarchyStoragesAfterMethod() {
        if (hierarchyFixture != null) {
            hierarchyFixture.deactivateCreatedMaps();
        }
        TestArtifactCleanup.cleanupRegionsAndStorages(regionFixture, storageFixture);
    }

    @AfterClass(alwaysRun = true)
    public void releaseIsolatedHierarchy() {
        if (hierarchyFixture != null) {
            hierarchyFixture.release();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-MFG-035")
    @Story("Workspace STORAGE/PRODUCTION parent shows entire subtree tech maps")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Isolated REGIONS owner bound to STORAGE parent P (not UNIT).
            Open /technological-maps with STORAGE P, then PRODUCTION P.
            Each table contains TM-P, TM-A, TM-B (no location access) and TM-C; not TM-X or the other branch.
            Switch workspace to STORAGE A → only TM-A.
            """)
    public void parentWorkspaceShowsEntireSubtreeMaps() {
        TechnologicalMapHierarchyFixture.Branch storage = seed.getStorageParent();
        TechnologicalMapHierarchyFixture.Branch production = seed.getProductionParent();

        injectIsolatedOwnerSession(storage.getParent().getId());
        page = browserContext.newPage();

        TechnologicalMapsListPage listPage = new TechnologicalMapsListPage(page)
                .openForStorage(storage.getParent().getId());
        assertParentTable("STORAGE", listPage.getDisplayedTechMapNames(), storage, production);
        listPage.attachScreenshot("TC-MFG-035 — STORAGE parent P subtree");

        switchWorkspace(production.getParent().getName());
        assertParentTable("PRODUCTION", listPage.getDisplayedTechMapNames(), production, storage);
        listPage.attachScreenshot("TC-MFG-035 — PRODUCTION parent P subtree");

        switchWorkspace(storage.getStorageA().getName());
        List<String> leafNames = listPage.getDisplayedTechMapNames();
        assertThat(leafNames)
                .as("Leaf A table must contain TM-A and not expand the tree")
                .anyMatch(name -> name.contains(storage.getMapA().getName()))
                .noneMatch(name -> name.contains(storage.getMapParent().getName()))
                .noneMatch(name -> name.contains(storage.getMapB().getName()))
                .noneMatch(name -> name.contains(storage.getMapC().getName()))
                .noneMatch(name -> name.contains(seed.getMapX().getName()));
        listPage.attachScreenshot("TC-MFG-035 — leaf A only");
    }

    private void assertParentTable(
            String parentType,
            List<String> names,
            TechnologicalMapHierarchyFixture.Branch branch,
            TechnologicalMapHierarchyFixture.Branch other) {
        assertThat(names)
                .as("%s parent P table must include TM-P, TM-A, TM-B (no access) and TM-C", parentType)
                .anyMatch(name -> name.contains(branch.getMapParent().getName()))
                .anyMatch(name -> name.contains(branch.getMapA().getName()))
                .anyMatch(name -> name.contains(branch.getMapB().getName()))
                .anyMatch(name -> name.contains(branch.getMapC().getName()));
        assertThat(names)
                .as("%s parent P table must not include TM-X or the other parent branch", parentType)
                .noneMatch(name -> name.contains(seed.getMapX().getName()))
                .noneMatch(name -> name.contains(other.getMapParent().getName()))
                .noneMatch(name -> name.contains(other.getMapA().getName()))
                .noneMatch(name -> name.contains(other.getMapB().getName()))
                .noneMatch(name -> name.contains(other.getMapC().getName()));
    }

    private void switchWorkspace(String locationName) {
        try {
            page.waitForResponse(
                    response -> {
                        String url = response.url();
                        return url.contains("/technological-maps")
                                && !url.contains("/technological-maps/mode")
                                && "GET".equals(response.request().method())
                                && response.status() < 500;
                    },
                    () -> new AppSidebarPage(page).selectWorkspaceByName(locationName));
        } catch (com.microsoft.playwright.PlaywrightException e) {
            log.debug("Workspace switch did not trigger tech-map GET, selecting anyway: {}", e.getMessage());
            new AppSidebarPage(page).selectWorkspaceByName(locationName);
        }
        new TechnologicalMapsListPage(page).waitForTableSettled();
    }

    private void injectIsolatedOwnerSession(long selectedStorageId) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(seed.getOwner().username(), seed.getOwner().password());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
    }
}
