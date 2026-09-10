package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.GlobalPlanWizardPage;
import com.erp.pages.PlanAnalyticsPage;
import com.erp.pages.components.DateRangePickerComponent;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: аналітика «Для плану» і підказка залишку при створенні глобального плану.
 */
@Slf4j
@Epic("Analytics")
@Feature("Plan analytics")
public class PlanAnalyticsUiTest extends BaseUITest {

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        long storageId = ConfigProvider.getOwner1StorageId();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
        log.info("ADMIN session injected for plan analytics UI, storageId={}", storageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-PLAN-ANL-UI-001")
    @Story("Plan analytics page")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-03.
            Сайдбар «Аналітика» → «Для плану». Без вибору — порожній стан.
            Пресети «Місяць» (попередній повний місяць) і «3 місяці».
            Після вибору ресурсу — підсумки Вироблено / Відвантажено / Використано /
            Залишок усього / Залишок (Цукрарня); розгортання рядка — Цукрарня / Інші локації.
            """)
    public void planAnalyticsPageShowsMonthAndThreeMonthProductFigures() {
        page.navigate(ConfigProvider.getBaseUrl() + "/production");
        page.waitForLoadState();
        AppSidebarPage sidebar = new AppSidebarPage(page).waitForSidebarLoaded();
        sidebar.openCollapsibleItem(AppSidebarPage.GROUP_ANALYTICS, AppSidebarPage.NAV_PLAN_ANALYTICS);

        PlanAnalyticsPage analytics = new PlanAnalyticsPage(page).waitForLoaded();
        assertThat(page.url()).contains("/analytics/plan");
        assertThat(analytics.isEmptyStateVisible())
                .as("Порожній стан без вибору ресурсу/категорії")
                .isTrue();
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — empty state");

        DateRangePickerComponent picker = analytics.periodPicker();
        assertThat(picker.isVisible()).as("DateRangePicker «Період»").isTrue();

        LocalDate today = analytics.browserToday();
        LocalDate monthFrom = PlanAnalyticsPage.lastFullMonthFrom(today);
        LocalDate monthTo = PlanAnalyticsPage.lastFullMonthTo(today);
        assertThat(picker.getFromIso()).as("дефолт «Місяць»: from").isEqualTo(monthFrom.toString());
        assertThat(picker.getToIso()).as("дефолт «Місяць»: to").isEqualTo(monthTo.toString());

        picker.open();
        assertThat(picker.visiblePresetLabels(DateRangePickerComponent.PLAN_PRESET_LABELS))
                .as("Пресети плану")
                .containsExactlyElementsOf(DateRangePickerComponent.PLAN_PRESET_LABELS);
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — presets");

        String initialTrigger = picker.getTriggerText();
        picker.clickPreset(DateRangePickerComponent.PRESET_3_MONTHS);
        analytics.waitUntilTriggerChanges(initialTrigger);
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — 3 months");
        assertThat(picker.getFromIso())
                .as("«3 місяці»: from")
                .isEqualTo(PlanAnalyticsPage.threeFullMonthsFrom(today).toString());
        assertThat(picker.getToIso()).as("«3 місяці»: to").isEqualTo(monthTo.toString());

        picker.clickPreset(DateRangePickerComponent.PRESET_MONTH);
        picker.waitUntilRange(monthFrom, monthTo);

        analytics.selectFirstResource();
        analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — resource selected");
        assertThat(analytics.hasTotals())
                .as("Підсумки Вироблено / Відвантажено / Використано / залишки")
                .isTrue();
        assertThat(analytics.hasColumnHeaders())
                .as("Колонки таблиці аналітики плану")
                .isTrue();
        assertThat(analytics.isEmptyStateVisible())
                .as("Порожній стан зникає після вибору")
                .isFalse();

        if (analytics.dataRowCount() > 0) {
            analytics.expandFirstRow();
            analytics.attachScreenshot("TC-PLAN-ANL-UI-001 — stock expanded");
            assertThat(analytics.hasStockLocationGroups())
                    .as("Розгортання: Цукрарня / Інші локації або «Залишків немає»")
                    .isTrue();
        }
    }

    @Test(priority = 20)
    @TestCaseId("TC-PLAN-ANL-UI-002")
    @Story("Plan create product stock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-04.
            Створення глобального плану, крок «Заплановано»: після вибору виробу
            під рядком видно «В наявності» (поточний залишок). Детальні вироблено/видано/використано
            за місяць і 3 місяці — на сторінці «Для плану», не в цьому рядку.
            План не зберігається.
            """)
    public void creatingPlanShowsAvailableStockForSelectedProduct() {
        GlobalPlanWizardPage wizard = new GlobalPlanWizardPage(page).openCreate();
        assertThat(wizard.isWizardHeadingVisible()).as("Візард глобального плану").isTrue();
        wizard.attachScreenshot("TC-PLAN-ANL-UI-002 — create form");

        wizard.selectFirstPlannableProduct();
        wizard.attachScreenshot("TC-PLAN-ANL-UI-002 — product selected");
        assertThat(wizard.isStockHintVisible())
                .as("Під обраним виробом є «В наявності»")
                .isTrue();
        assertThat(wizard.stockHintText())
                .as("Текст підказки залишку")
                .contains("В наявності");
    }
}
