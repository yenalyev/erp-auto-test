package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationExportColumnsFixture;
import com.erp.pages.ExportAnalyticsPage;
import com.erp.pages.RelocationFiltersPage;
import com.erp.pages.RelocationPage;
import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.assertions.RelocationExportColumnsAssertions;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Експорт даних")
@Feature("Переміщення — порядок колонок, групи та kind")
public class RelocationExportColumnsUiTest extends BaseUITest {
    private RelocationExportColumnsFixture data;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        data = new RelocationExportColumnsFixture(testContext, apiExecutor);
        data.arrangeKinds(ConfigProvider.getOwner1StorageId());
    }

    @Test(priority = 1)
    @TestCaseId("TC-REL-COL-UI-001")
    public void globalExportDownloadsCorrectColumnsAndAllKinds() throws Exception {
        var exportPage = new ExportAnalyticsPage(page).open().selectRelocationsExport();
        new DateRangePickerComponent(page, ConfigProvider.getUiTimeoutSeconds() * 1000)
                .selectPreset(DateRangePickerComponent.PRESET_1_DAY);
        var today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
        var bytes = exportPage.downloadRelocationsWorkbook(today, today);
        RelocationExportColumnsAssertions.assertKinds(bytes, data);
        RelocationExportColumnsAssertions.assertJournalMatches(bytes, data, data.journalOracle());
        exportPage.attachScreenshot("Переміщення — фактичне завантаження Excel");
    }

    @Test(priority = 2)
    @TestCaseId("TC-REL-COL-UI-002")
    public void sentAndReceivedScreensMatchExportedResourceAmounts() throws Exception {
        // Two identical transfers appear as two journal entries and four Excel resource positions.
        var transfer = data.transfers().getFirst();
        var sentFilters = new RelocationFiltersPage(page).open("sent");
        sentFilters.location("Від кого", transfer.sender().getName(), transfer.sender().getId());
        var sent = new RelocationPage(page);
        new DateRangePickerComponent(page, ConfigProvider.getUiTimeoutSeconds() * 1000)
                .selectPreset(DateRangePickerComponent.PRESET_1_DAY);
        sent.waitForJournalDataSettled();
        page.waitForCondition(() -> sent.getDisplayedJournalRows().size() == 4);
        assertThat(sent.getDisplayedJournalRows()).hasSize(4)
                .allSatisfy(row -> assertThat(row.getSenderName()).isEqualTo(transfer.sender().getName()));
        for (var product : data.products()) {
            double expected = product.getId().equals(data.products().getFirst().getId()) ? 3.75 : 4.75;
            assertThat(sent.getResourceAmountInRow(transfer.recipient().getName(), product.getName())).isEqualTo(expected);
        }
        sent.attachScreenshot("Видано — чотири окремі переміщення");
        var receivedFilters = new RelocationFiltersPage(page).open("received");
        receivedFilters.location("Кому", transfer.recipient().getName(), transfer.recipient().getId());
        var received = new RelocationPage(page);
        new DateRangePickerComponent(page, ConfigProvider.getUiTimeoutSeconds() * 1000)
                .selectPreset(DateRangePickerComponent.PRESET_1_DAY);
        received.waitForJournalDataSettled();
        page.waitForCondition(() -> received.getDisplayedJournalRows().size() == 2);
        assertThat(received.getDisplayedJournalRows()).hasSize(2)
                .allSatisfy(row -> assertThat(row.getRecipientName()).isEqualTo(transfer.recipient().getName()));
        for (var product : data.products()) {
            double expected = product.getId().equals(data.products().getFirst().getId()) ? 3.75 : 4.75;
            assertThat(received.getResourceAmountInRow(transfer.sender().getName(), product.getName())).isEqualTo(expected);
        }
        received.attachScreenshot("Отримано — два переміщення з однаковими позиціями");
        var exportPage = new ExportAnalyticsPage(page).open().selectRelocationsExport();
        new DateRangePickerComponent(page, ConfigProvider.getUiTimeoutSeconds() * 1000)
                .selectPreset(DateRangePickerComponent.PRESET_1_DAY);
        var today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
        RelocationExportColumnsAssertions.assertJournalMatches(exportPage.downloadRelocationsWorkbook(today, today),
                data, data.journalOracle());
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() { if (data != null) data.cleanup(); }
}
