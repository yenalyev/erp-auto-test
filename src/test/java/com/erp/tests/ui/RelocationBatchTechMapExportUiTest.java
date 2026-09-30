package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.RelocationPage;
import com.erp.pages.components.DateRangePickerComponent;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.UiDownloadAssertions;
import com.erp.utils.helpers.XlsxWorkbookReader;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("Excel export from received and sent relocation tabs")
public class RelocationBatchTechMapExportUiTest extends BaseUITest {
    private RelocationFixture relocations;
    private RelocationResponse receive;
    private RelocationResponse send;
    private long storageId;
    private String batchNumber;
    private final List<String> exportRequests = new CopyOnWriteArrayList<>();

    @BeforeMethod(alwaysRun = true)
    @Override
    public void testSetup() {
        super.testSetup();
        exportRequests.clear();
        page.onRequest(request -> {
            if (request.url().contains("export")) {
                exportRequests.add(request.url());
            }
        });
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageId = ConfigProvider.getOwner1StorageId();
        relocations = new RelocationFixture(testContext, apiExecutor);
        relocations.prepareContext();
        batchNumber = "REL-XLS-UI-" + java.util.UUID.randomUUID();
        receive = relocations.createExternalReceive(UserRole.ADMIN, storageId,
                testContext.get(ContextKey.RELOCATION_RESOURCE_ID), 4.0, batchNumber);
        send = relocations.createSendWithBatch(UserRole.ADMIN, storageId,
                testContext.get(ContextKey.RELOCATION_UNIT_STORAGE_ID),
                testContext.get(ContextKey.RELOCATION_RESOURCE_ID),
                2.0, batchNumber, false);
        injectSessionCookies(cachedSessionCookies(UserRole.OWNER_1), sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '"
                + storageId + "');");
    }

    @Test(priority = 10)
    @TestCaseId("TC-REL-XLS-005")
    @Severity(SeverityLevel.CRITICAL)
    public void receiveJournalDownloadsWorkbookWithBatchColumn() throws IOException {
        RelocationPage journal = new RelocationPage(page).open().openReceivedTab();
        selectCurrentDay();
        RelocationPage.ExcelDownloadResult download = journal.exportToExcel();
        assertWorkbookContainsBatch(download.path(), download.sizeBytes(), receive.getId(), "4");
    }

    @Test(priority = 20)
    @TestCaseId("TC-REL-XLS-006")
    @Severity(SeverityLevel.CRITICAL)
    public void sentHistoryDownloadsWorkbookWithBatchColumn() throws IOException {
        RelocationPage journal = new RelocationPage(page).open().openSentTab();
        selectCurrentDay();
        RelocationPage.ExcelDownloadResult download = journal.exportToExcel();
        assertWorkbookContainsBatch(download.path(), download.sizeBytes(), send.getId(), "2");
    }

    private void selectCurrentDay() {
        new DateRangePickerComponent(page, ConfigProvider.getUiTimeoutSeconds() * 1000)
                .selectPreset(DateRangePickerComponent.PRESET_1_DAY);
    }

    private void assertWorkbookContainsBatch(Path path, long size, long relocationId,
                                             String expectedAmount) throws IOException {
        UiDownloadAssertions.assertNonEmptyXlsx(path, size, "Relocation batch Excel");
        var sheets = XlsxWorkbookReader.sheets(Files.readAllBytes(path));
        assertThat(sheets.values().stream().flatMap(List::stream)
                .anyMatch(row -> row.contains("Партія") && row.contains("Тех карта")))
                .as("Workbook has both new columns; export requests %s; first rows %s",
                        exportRequests,
                        sheets.values().stream().flatMap(List::stream).limit(3).toList())
                .isTrue();
        List<List<String>> matchingRows = sheets.values().stream()
                .flatMap(List::stream)
                .filter(row -> row.contains(String.valueOf(relocationId)) && row.contains(batchNumber))
                .toList();
        assertThat(matchingRows)
                .as("Relocation %s with batch %s in sheets %s; requests %s; first rows: %s",
                        relocationId, batchNumber, sheets.keySet(), exportRequests,
                        sheets.values().stream().flatMap(List::stream).limit(8).toList())
                .hasSize(1);
        assertThat(exportRequests).anyMatch(url -> url.contains("/api/v1/relocations/export"));
        List<String> header = sheets.values().stream().flatMap(List::stream)
                .filter(row -> row.contains("Партія") && row.contains("Кількість"))
                .findFirst().orElseThrow();
        String quantity = matchingRows.getFirst().get(XlsxWorkbookReader.columnIndex(header, "Кількість"));
        assertThat(new BigDecimal(quantity)).isEqualByComparingTo(expectedAmount);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupExportScenario() {
        if (relocations != null && send != null) {
            relocations.deleteRelocation(UserRole.ADMIN, send.getId(), storageId);
        }
        if (relocations != null && receive != null) {
            relocations.deleteRelocation(UserRole.ADMIN, receive.getId(), storageId);
        }
    }
}
