package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationPermissionSupport;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.StorageResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProductionCreateFormPage;
import com.erp.pages.ProductionPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPMA-644 UI: LOCATION_MIXED — create/edit gating on full vs view-only locations.
 */
@Slf4j
@Epic("Administration")
@Feature("REQ-LOC-PERM")
@Story("UI location permissions")
public class LocationPermissionsUiTest extends BaseUITest {

    private UserFixture userFixture;
    private StorageFixture storageFixture;
    private UserFixture.LocationPermissionIds ids;
    private String fullA1Name;
    private String fullA2Name;
    private String roB1Name;
    private String roB2Name;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        userFixture = new UserFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        long ro2 = LocationPermissionSupport.resolveRo2StorageId(storageFixture);
        ids = userFixture.ensureLocationMixedUser(getPlaywrightSessionProvider(), ro2);
        fullA1Name = storageName(ids.fullA1());
        fullA2Name = storageName(ids.fullA2());
        roB1Name = storageName(ids.roB1());
        roB2Name = storageName(ids.roB2());
    }

    @Test(priority = 1)
    @TestCaseId("TC-LOC-UI-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            LOCATION_MIXED: на full A1 CTA «Виготовлення» enabled і відкриває create wizard;
            на RO B1 CTA прихована або disabled.
            """)
    public void createAvailableOnFullBlockedOnRo() {
        injectRoleSession(UserRole.LOCATION_MIXED, ids.fullA1());
        ProductionPage production = new ProductionPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.selectWorkspaceByName(fullA1Name);
        production.waitForJournalDataSettled();

        assertThat(production.isManufacturingButtonEnabled())
                .as("Виготовлення must be enabled on full A1")
                .isTrue();
        production.clickManufacturing();
        ProductionCreateFormPage createForm = new ProductionCreateFormPage(page).waitForLoaded();
        assertThat(page.url()).contains(ProductionCreateFormPage.PATH);
        createForm.attachScreenshot("TC-LOC-UI-001 — create on full");

        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        production.waitForLoaded();
        sidebar.selectWorkspaceByName(roB1Name);
        production.waitForJournalDataSettled();

        assertThat(production.isManufacturingButtonEnabled())
                .as("Виготовлення must be hidden/disabled on RO B1")
                .isFalse();
        production.attachScreenshot("TC-LOC-UI-001 — blocked on RO");
    }

    @Test(priority = 2)
    @TestCaseId("TC-LOC-UI-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            LOCATION_MIXED: на full A2 edit notes доступний коли є рядки;
            на RO B1 edit notes недоступний (немає кнопки або disabled).
            """)
    public void editAvailableOnFullBlockedOnRo() {
        injectRoleSession(UserRole.LOCATION_MIXED, ids.fullA2());
        ProductionPage production = new ProductionPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.selectWorkspaceByName(fullA2Name);
        production.waitForJournalDataSettled();

        if (production.getProductionRecordCount() > 0) {
            assertThat(production.isNotesEditVisibleForRow(0))
                    .as("Notes edit should be available on full A2 when rows exist")
                    .isTrue();
            String note = "loc-perm-" + System.currentTimeMillis();
            production.openNotesEditorForRow(0)
                    .fillNotesDialog(note)
                    .saveNotesDialog();
            production.attachScreenshot("TC-LOC-UI-002 — edit on full");
        } else {
            log.warn("No production rows on A2 — asserting create CTA as edit proxy");
            assertThat(production.isManufacturingButtonEnabled()).isTrue();
        }

        sidebar.selectWorkspaceByName(roB1Name);
        production.waitForJournalDataSettled();
        if (production.getProductionRecordCount() > 0) {
            assertThat(production.isNotesEditVisibleForRow(0))
                    .as("Notes edit must be blocked on RO")
                    .isFalse();
        } else {
            assertThat(production.isManufacturingButtonEnabled()).isFalse();
        }
        production.attachScreenshot("TC-LOC-UI-002 — blocked on RO");
    }

    @Test(priority = 3)
    @TestCaseId("TC-LOC-UI-003")
    @Severity(SeverityLevel.NORMAL)
    @Description("LOCATION_MIXED: селектор показує 4 локації; перемикання A1→B1→A2 оновлює CTA.")
    public void workspaceShowsAllAndSwitchUpdatesCta() {
        injectRoleSession(UserRole.LOCATION_MIXED, ids.fullA1());
        ProductionPage production = new ProductionPage(page).open();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();

        assertThat(sidebar.isWorkspaceOptionVisible(fullA1Name)).isTrue();
        assertThat(sidebar.isWorkspaceOptionVisible(fullA2Name)).isTrue();
        assertThat(sidebar.isWorkspaceOptionVisible(roB1Name)).isTrue();
        assertThat(sidebar.isWorkspaceOptionVisible(roB2Name)).isTrue();

        sidebar.selectWorkspaceByName(fullA1Name);
        production.waitForJournalDataSettled();
        production.waitForManufacturingButtonEnabled(true);
        assertThat(production.isManufacturingButtonEnabled()).isTrue();

        sidebar.selectWorkspaceByName(roB1Name);
        production.waitForJournalDataSettled();
        production.waitForManufacturingButtonEnabled(false);
        assertThat(production.isManufacturingButtonEnabled()).isFalse();

        sidebar.selectWorkspaceByName(fullA2Name);
        production.waitForJournalDataSettled();
        production.waitForManufacturingButtonEnabled(true);
        assertThat(production.isManufacturingButtonEnabled()).isTrue();
        production.attachScreenshot("TC-LOC-UI-003 — switch full↔RO");
    }

    private String storageName(long storageId) {
        return storageFixture.getNames(UserRole.ADMIN, true, null, storageId)
                .stream()
                .map(StorageResponse::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No storage name for id=" + storageId));
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(role.getUsername(), role.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
    }
}
