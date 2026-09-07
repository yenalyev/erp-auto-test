package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.models.query.ProductionJournalQuery;
import com.erp.pages.ProductionPage;
import com.erp.pages.components.DateRangePickerComponent;
import com.erp.pages.components.DateRangePickerComponent.DisplayedRange;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DateRangePicker on the production journal ({@code /production}): presets, reset, pinned default.
 */
@Slf4j
@Epic("Production")
@Feature("Production Journal UI")
@Story("DateRangePicker")
public class ProductionJournalDatePickerUiTest extends BaseUITest {

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        long storageId = ConfigProvider.getOwner1StorageId();
        Map<String, String> cookies = cachedSessionCookies(UserRole.OWNER_1);
        injectSessionCookies(cookies, sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');"
                        + "localStorage.setItem('" + ProductionPage.pageSizeStorageKey() + "', '"
                        + ProductionJournalQuery.DEFAULT_UI_PAGE_SIZE + "');");
        log.info("OWNER_1 session injected for production journal DateRangePicker, storageId={}",
                storageId);
    }

    @AfterMethod(alwaysRun = true)
    public void clearPinnedJournalDefaultPreset() {
        if (page == null) {
            return;
        }
        try {
            page.evaluate("key => { try { localStorage.removeItem(key); } catch (e) {} }",
                    DateRangePickerComponent.defaultPresetStorageKey(
                            ProductionPage.DATE_RANGE_STORAGE_KEY));
        } catch (Exception e) {
            log.debug("Could not clear pinned DateRangePicker default: {}", e.getMessage());
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-PROD-DRP-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** усі предефайни DateRangePicker на `/production` ставлять точний діапазон
            (tk-ui `getPresets()`: today; today−7; `subMonths(1)`; `startOfMonth`; `startOfYear`).

            **Кроки:** OWNER_1 відкриває журнал → «Період» → по черзі «1 день», «7 днів»,
            «30 днів», «Місяць», «Рік».

            **Очікування:** тригер `dd.MM.yyyy – dd.MM.yyyy` збігається з датою браузера
            для кожного пресета. «30 днів» може збігатися з дефолтом журналу — це не помилка.
            """)
    public void allPresetsApplyExactRangesOnProductionJournal() {
        ProductionPage journal = new ProductionPage(page).open();
        DateRangePickerComponent picker = journal.periodPicker();
        assertThat(picker.isVisible()).as("DateRangePicker «Період»").isTrue();

        picker.open();
        assertThat(picker.visiblePresetLabels())
                .as("Предефайни в поповері журналу")
                .containsExactlyElementsOf(DateRangePickerComponent.PRESET_LABELS);
        attachScreenshot("TC-UI-PROD-DRP-001 — presets visible");

        LocalDate today = picker.browserToday();
        for (String preset : DateRangePickerComponent.PRESET_LABELS) {
            DisplayedRange expected = DateRangePickerComponent.expectedPresetRange(preset, today);
            picker.selectPreset(preset);
            picker.waitUntilRange(expected.from(), expected.to());
            attachScreenshot("TC-UI-PROD-DRP-001 — after " + preset);

            assertThat(picker.getFromIso())
                    .as("«%s»: from", preset)
                    .isEqualTo(expected.from().toString());
            assertThat(picker.getToIso())
                    .as("«%s»: to", preset)
                    .isEqualTo(expected.to().toString());
        }
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-PROD-DRP-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** «Скинути» в поповері DateRangePicker журналу очищає період.

            **Кроки:** журнал з дефолтним діапазоном → «Період» → «Скинути».

            **Очікування:** тригер показує «Оберіть період»; from/to порожні;
            після повторного відкриття кнопки «Скинути» немає.
            """)
    public void resetClearsPeriodOnProductionJournal() {
        ProductionPage journal = new ProductionPage(page).open();
        DateRangePickerComponent picker = journal.periodPicker();
        assertThat(picker.isVisible()).isTrue();
        assertThat(picker.getFromIso()).as("Журнал стартує з обраним періодом").isNotBlank();

        assertThat(picker.isResetButtonVisible()).as("Кнопка «Скинути» при вибраному періоді").isTrue();
        attachScreenshot("TC-UI-PROD-DRP-002 — before reset");

        picker.clear();
        picker.waitUntilCleared();
        attachScreenshot("TC-UI-PROD-DRP-002 — after reset");
        log.info("After reset: url={} trigger='{}' dom='{}' from={} to={}",
                page.url(), picker.getTriggerText(), picker.readTriggerTextFromDom(),
                picker.getFromIso(), picker.getToIso());

        assertThat(picker.readTriggerTextFromDom())
                .as("Тригер після «Скинути»")
                .isEqualTo(DateRangePickerComponent.PLACEHOLDER);
        assertThat(picker.isCleared()).as("Період очищено").isTrue();
        assertThat(picker.isResetButtonVisible())
                .as("«Скинути» ховається, коли період порожній")
                .isFalse();
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-PROD-DRP-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            **Мета:** «По-замовчуванню» лише пише localStorage; діапазон змінюється після reload.

            **Кроки:** журнал (дефолт «30 днів») → «Період» → «По-замовчуванню» = «1 день»
            → перевірити, що поточний тригер ще 30 днів → reload.

            **Очікування:** ключ `date-range-picker:default-preset:production-list` = `1d`;
            після reload тригер = сьогодні–сьогодні.
            """)
    public void pinnedDefaultPresetAppliesAfterReload() {
        ProductionPage journal = new ProductionPage(page).open();
        DateRangePickerComponent picker = journal.periodPicker();
        LocalDate today = picker.browserToday();
        DisplayedRange thirtyDays = DateRangePickerComponent.expectedPresetRange(
                DateRangePickerComponent.PRESET_30_DAYS, today);

        picker.waitUntilRange(thirtyDays.from(), thirtyDays.to());
        assertThat(picker.getFromIso()).isEqualTo(thirtyDays.from().toString());
        assertThat(picker.getToIso()).isEqualTo(thirtyDays.to().toString());

        assertThat(picker.isDefaultPresetRowVisible())
                .as("Рядок «По-замовчуванню» (storageKey журналу)")
                .isTrue();
        attachScreenshot("TC-UI-PROD-DRP-003 — default row");

        picker.setDefaultPreset(DateRangePickerComponent.PRESET_1_DAY);
        attachScreenshot("TC-UI-PROD-DRP-003 — pinned 1 day");

        assertThat(picker.readPinnedDefaultPresetId(ProductionPage.DATE_RANGE_STORAGE_KEY))
                .as("localStorage pin")
                .isEqualTo(DateRangePickerComponent.presetId(DateRangePickerComponent.PRESET_1_DAY));
        assertThat(picker.getFromIso())
                .as("Пін не застосовує діапазон одразу")
                .isEqualTo(thirtyDays.from().toString());
        assertThat(picker.getToIso()).isEqualTo(thirtyDays.to().toString());

        page.reload();
        journal.waitForLoaded();
        DateRangePickerComponent afterReload = journal.periodPicker();
        DisplayedRange oneDay = DateRangePickerComponent.expectedPresetRange(
                DateRangePickerComponent.PRESET_1_DAY, afterReload.browserToday());
        afterReload.waitUntilRange(oneDay.from(), oneDay.to());
        attachScreenshot("TC-UI-PROD-DRP-003 — after reload");

        assertThat(afterReload.getFromIso()).isEqualTo(oneDay.from().toString());
        assertThat(afterReload.getToIso()).isEqualTo(oneDay.to().toString());
    }
}
