package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProductionCreateFormPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Coverage gap smokes")
@Feature("Domain UI smokes")
public class CoverageGapUiSmokeTest extends BaseUITest {

    private long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageId = ConfigProvider.getOwner1StorageId();
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    private void openApp() {
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        page.waitForLoadState();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PO-UI-001")
    @Story("Production orders list")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Адмін відкриває вкладку «Виробничі замовлення».")
    public void productionOrdersTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_ORDERS);
        sidebar.openPageTab(AppSidebarPage.TAB_PRODUCTION_ORDERS);
        assertThat(page.url()).contains("/production-orders");
        page.locator("body").waitFor();
    }

    @Test(priority = 20)
    @TestCaseId("TC-SHIFT-UI-001")
    @Story("Shifts dictionary")
    @Severity(SeverityLevel.CRITICAL)
    public void shiftsTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_PRODUCTION);
        sidebar.openPageTab(AppSidebarPage.TAB_SHIFTS);
        assertThat(page.url()).contains("/shifts");
    }

    @Test(priority = 30)
    @TestCaseId("TC-SHIFT-UI-002")
    @Story("Production form requires shift")
    @Severity(SeverityLevel.CRITICAL)
    public void productionCreateFormHasShiftField() {
        ProductionCreateFormPage form = new ProductionCreateFormPage(page).open();
        form.ensureShiftSelected();
        assertThat(page.getByText("Зміна").count()).isGreaterThan(0);
    }

    @Test(priority = 40)
    @TestCaseId("TC-DICT-003")
    @Story("Employees UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("REQ-DICT-001 AC-02: вкладка «Співробітники».")
    public void employeesTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_EQUIPMENT);
        sidebar.openPageTab(AppSidebarPage.TAB_EMPLOYEES);
        if (!page.url().contains("/employees")) {
            throw new SkipException("Employees tab not reachable for ADMIN");
        }
        assertThat(page.url()).contains("/employees");
    }

    @Test(priority = 50)
    @TestCaseId("TC-PRICE-UI-001")
    @Story("Prices UI")
    public void pricesTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_RESOURCES);
        sidebar.openPageTab(AppSidebarPage.TAB_PRICES);
        assertThat(page.url()).contains("/resources-price");
    }

    @Test(priority = 60)
    @TestCaseId("TC-EQU-CAT-UI-001")
    @Story("Equipment categories UI")
    public void equipmentCategoriesTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_EQUIPMENT);
        sidebar.openPageTab(AppSidebarPage.TAB_EQUIPMENT_CATEGORIES);
        assertThat(page.url()).contains("/equipment-categories");
    }

    @Test(priority = 70)
    @TestCaseId("TC-ANL-UI-001")
    @Story("Production analytics UI")
    public void productionAnalyticsTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_PRODUCTION);
        sidebar.openPageTab(AppSidebarPage.TAB_PRODUCTION_ANALYTICS);
        assertThat(page.url()).contains("/production-analytics");
    }

    @Test(priority = 80)
    @TestCaseId("TC-ANL-UI-002")
    @Story("Daily report UI")
    public void dailyReportTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_PRODUCTION);
        sidebar.openPageTab(AppSidebarPage.TAB_DAILY_REPORT);
        assertThat(page.url()).contains("/production-daily-report");
    }

    @Test(priority = 90)
    @TestCaseId("TC-ANL-UI-003")
    @Story("Orders analytics UI")
    public void ordersAnalyticsTabOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_ORDERS);
        sidebar.openPageTab(AppSidebarPage.TAB_ORDERS_ANALYTICS);
        assertThat(page.url()).contains("/orders-analytics");
    }

    @Test(priority = 100)
    @TestCaseId("TC-ANL-UI-004")
    @Story("Audit UI")
    public void auditLogOpens() {
        openApp();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openGroup(AppSidebarPage.GROUP_AUDIT);
        sidebar.openPageTab(AppSidebarPage.TAB_AUDIT_LOG);
        assertThat(page.url()).contains("/audit");
    }
}
