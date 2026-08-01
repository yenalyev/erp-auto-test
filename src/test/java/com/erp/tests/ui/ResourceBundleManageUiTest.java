package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceBundleFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.RelocationBundlesTabPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Resource Bundles UI — manage")
@Story("REQ-WMS-009 AC-03/AC-04")
public class ResourceBundleManageUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-bundle-mgr-";

    private ResourceBundleFixture bundleFixture;
    private ResourceFixture resourceFixture;
    private long storageId;
    private ResourceResponse resource;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        bundleFixture = new ResourceBundleFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        bundleFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();

        storageId = ConfigProvider.getOwner1StorageId();
        resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        bundleFixture.relocation().ensureStock(storageId, resource.getId(), 30.0);
        injectOwner1Session();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (bundleFixture != null) {
            bundleFixture.cleanupCreatedBundles();
        }
    }

    @Test(priority = 1)
    @TestCaseId("TC-BUNDLE-UI-001")
    @Severity(SeverityLevel.BLOCKER)
    @Description("При конкретній локації + BU read є tab «Комплекти для видачі».")
    public void bundlesTabVisibleForLocation() {
        RelocationPage journal = new RelocationPage(page).open();
        assertThat(journal.isBundlesTabVisible()).isTrue();
        RelocationBundlesTabPage tab = journal.openBundlesTab();
        assertThat(tab.isNewBundleButtonVisible()).isTrue();
        attachScreenshot("TC-BUNDLE-UI-001");
    }

    @Test(priority = 2)
    @TestCaseId("TC-BUNDLE-UI-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ADMIN + «Всі локації» → tab «Комплекти для видачі» відсутній.")
    public void bundlesTabHiddenForAllLocations() {
        // OWNER_1 often has a single storage → StorageContext forces that id (isAllLocations=false).
        // ADMIN with multiple locations can select «Всі локації».
        browserContext.clearCookies();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");

        RelocationPage journal = new RelocationPage(page);
        page.navigate(ConfigProvider.getBaseUrl() + RelocationPage.PATH);
        journal.waitForLoaded();
        assertThat(journal.isBundlesTabVisible())
                .as("Bundles tab must be hidden for ADMIN + «Всі локації»")
                .isFalse();
        attachScreenshot("TC-BUNDLE-UI-002");

        browserContext.clearCookies();
        injectOwner1Session();
    }

    @Test(priority = 3)
    @TestCaseId("TC-BUNDLE-UI-003")
    @Severity(SeverityLevel.BLOCKER)
    @Description("«Новий Комплект»: назва + ресурси → рядок у таблиці.")
    public void createBundleViaUi() {
        String name = bundleFixture.uniqueBundleName("ui-new-");
        RelocationBundlesTabPage tab = new RelocationPage(page).open().openBundlesTab();
        tab.openCreateDialog()
                .fillBundleName(name)
                .selectResourceByName(resource.getName())
                .saveDialog()
                .waitForBundleVisible(name);
        assertThat(tab.isBundleRowVisible(name)).isTrue();
        // track for cleanup via API delete
        bundleFixture.deleteBundleRaw(UserRole.OWNER_1, storageId, name);
        attachScreenshot("TC-BUNDLE-UI-003");
    }

    @Test(priority = 4)
    @TestCaseId("TC-BUNDLE-UI-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Duplicate name (case-insensitive) → UI error, save disabled.")
    public void duplicateNameBlocked() {
        String name = bundleFixture.uniqueBundleName("ui-dup-");
        bundleFixture.createBundle(UserRole.OWNER_1, storageId, name, List.of(resource.getId()));

        RelocationBundlesTabPage tab = new RelocationPage(page).open().openBundlesTab();
        tab.openCreateDialog()
                .fillBundleName(name.toUpperCase());
        assertThat(tab.isDuplicateNameErrorVisible()).isTrue();
        assertThat(tab.isSaveEnabled()).isFalse();
        tab.cancelDialog();
        attachScreenshot("TC-BUNDLE-UI-004");
    }

    @Test(priority = 5)
    @TestCaseId("TC-BUNDLE-UI-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Edit: назва disabled; можна зберегти з існуючими ресурсами.")
    public void editBundleNameLocked() {
        String name = bundleFixture.uniqueBundleName("ui-edit-");
        bundleFixture.createBundle(UserRole.OWNER_1, storageId, name, List.of(resource.getId()));

        RelocationBundlesTabPage tab = new RelocationPage(page).open().openBundlesTab();
        tab.openEdit(name);
        assertThat(tab.isBundleNameDisabled()).isTrue();
        tab.cancelDialog();
        attachScreenshot("TC-BUNDLE-UI-005");
    }

    @Test(priority = 6)
    @TestCaseId("TC-BUNDLE-UI-006")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Delete confirm → рядок зникає.")
    public void deleteBundleViaUi() {
        String name = bundleFixture.uniqueBundleName("ui-del-");
        bundleFixture.createBundle(UserRole.OWNER_1, storageId, name, List.of(resource.getId()));

        RelocationBundlesTabPage tab = new RelocationPage(page).open().openBundlesTab();
        tab.openDelete(name).confirmDelete().waitForBundleGone(name);
        assertThat(tab.isBundleRowVisible(name)).isFalse();
        attachScreenshot("TC-BUNDLE-UI-006");
    }

    @Test(priority = 7)
    @TestCaseId("TC-BUNDLE-UI-007")
    @Severity(SeverityLevel.NORMAL)
    @Description("OWNER_2: empty state «Комплектів не знайдено» коли список порожній.")
    public void emptyStateWhenNoBundles() {
        long owner2Storage = ConfigProvider.getOwner2StorageId();
        // Ensure empty list on OWNER_2 (isolated from OWNER_1 fixtures in this class).
        for (var existing : bundleFixture.listBundles(UserRole.OWNER_2, owner2Storage)) {
            bundleFixture.deleteBundleRaw(UserRole.OWNER_2, owner2Storage, existing.getBundleName());
        }

        browserContext.clearCookies();
        injectOwner2Session(owner2Storage);

        RelocationBundlesTabPage tab = new RelocationPage(page).open().openBundlesTab();
        assertThat(tab.isEmptyStateVisible()).isTrue();
        attachScreenshot("TC-BUNDLE-UI-007");

        browserContext.clearCookies();
        injectOwner1Session();
    }

    @Test(priority = 8)
    @TestCaseId("TC-BUNDLE-UI-008")
    @Severity(SeverityLevel.NORMAL)
    @Description("Немає нового пункту сайдбару для комплектів.")
    public void noSidebarItemForBundles() {
        new RelocationPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page);
        assertThat(sidebar.isNavItemVisible("Комплекти для видачі")).isFalse();
        assertThat(sidebar.isNavItemVisible("Комплекти")).isFalse();
        assertThat(sidebar.isNavItemVisible("Видати/Отримати")).isTrue();
        attachScreenshot("TC-BUNDLE-UI-008");
    }

    private void injectOwner1Session() {
        injectRoleSession(UserRole.OWNER_1, storageId);
    }

    private void injectOwner2Session(long owner2StorageId) {
        injectRoleSession(UserRole.OWNER_2, owner2StorageId);
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
