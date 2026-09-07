package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.pages.ProductionAnalyticsPage;
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
 * DateRangePicker presets on {@code /analytics/production}.
 */
@Slf4j
@Epic("Analytics")
@Feature("Production analytics")
@Story("DateRangePicker presets")
public class ProductionAnalyticsDatePickerUiTest extends BaseUITest {

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        long storageId = ConfigProvider.getOwner1StorageId();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
        log.info("ADMIN session injected for production analytics UI, storageId={}", storageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-ANL-UI-005")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** вибір предефайнів у DateRangePicker на `/analytics/production` змінює період.

            **Кроки:** ADMIN відкриває аналітику виробництва → «Період» → пресети
            «1 день», потім «7 днів» (tk-ui `getPresets()`: today; today-7…today).

            **Очікування:** чіпи видимі; після кліку тригер показує відповідний `dd.MM.yyyy – dd.MM.yyyy`,
            діапазон відрізняється від дефолтного `currentMonthPeriod` (1-ше…останній день місяця).
            """)
    public void predefinedRangesApplyOnProductionAnalyticsPicker() {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        assertThat(page.url()).contains("/analytics/production");
        assertThat(analytics.isDailyTabVisible())
                .as("Вкладка «Поденне»")
                .isTrue();

        DateRangePickerComponent picker = analytics.periodPicker();
        assertThat(picker.isVisible())
                .as("DateRangePicker «Період»")
                .isTrue();

        String initialTrigger = picker.getTriggerText();
        analytics.attachScreenshot("TC-ANL-UI-005 — initial period");

        picker.open();
        assertThat(picker.visiblePresetLabels())
                .as("Предефайни в поповері")
                .containsExactlyElementsOf(DateRangePickerComponent.PRESET_LABELS);
        analytics.attachScreenshot("TC-ANL-UI-005 — presets visible");

        LocalDate today = analytics.browserToday();
        picker.clickPreset(DateRangePickerComponent.PRESET_1_DAY);
        analytics.waitUntilTriggerChanges(initialTrigger);
        analytics.attachScreenshot("TC-ANL-UI-005 — after 1 day preset");

        assertThat(picker.getFromIso())
                .as("«1 день»: from = сьогодні браузера")
                .isEqualTo(today.toString());
        assertThat(picker.getToIso())
                .as("«1 день»: to = сьогодні браузера")
                .isEqualTo(today.toString());
        assertThat(picker.getTriggerText())
                .as("Тригер змінився після «1 день»")
                .isNotEqualTo(initialTrigger);

        String oneDayTrigger = picker.getTriggerText();
        LocalDate sevenFrom = today.minusDays(7);
        picker.clickPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.waitUntilTriggerChanges(oneDayTrigger);
        analytics.attachScreenshot("TC-ANL-UI-005 — after 7 days preset");

        assertThat(picker.getFromIso())
                .as("«7 днів»: from = сьогодні − 7 (date-fns subDays)")
                .isEqualTo(sevenFrom.toString());
        assertThat(picker.getToIso())
                .as("«7 днів»: to = сьогодні")
                .isEqualTo(today.toString());
        assertThat(picker.getTriggerText())
                .as("Тригер змінився після «7 днів»")
                .isNotEqualTo(oneDayTrigger);
    }
}
