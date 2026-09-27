package com.erp.tests.framework;

import com.erp.pages.ProductionAnalyticsPage;
import com.erp.pages.ProjectProductionListPage;
import com.erp.utils.helpers.XlsxWorkbookReader;
import com.microsoft.playwright.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.*;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.*;

/** Local browser contracts: all requests are fulfilled in memory, without ERP credentials. */
public class PageObjectContractTest {
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeClass
    public void startBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true);
        String channel = System.getProperty("framework.browser.channel");
        if (channel != null && !channel.isBlank()) options.setChannel(channel);
        browser = playwright.chromium().launch(options);
    }

    @BeforeMethod
    public void createPage() {
        context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
        page = context.newPage();
        page.setDefaultTimeout(2_000);
        page.route("**/*", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("text/html; charset=utf-8").setBody("<html><body></body></html>")));
        page.navigate("https://framework.invalid/");
    }

    @AfterMethod(alwaysRun = true)
    public void closePage() {
        if (context != null) context.close();
    }

    @AfterClass(alwaysRun = true)
    public void stopBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    public void deletePermissionMustNotBeMistakenForEdit() {
        page.setContent("""
                <table><tbody><tr><td>serial-42</td><td>
                <button>Ресурси</button><button title="Видалити" onclick="window.deleted=true">X</button>
                </td></tr></tbody></table>
                """);
        assertThatThrownBy(() -> new ProjectProductionListPage(page).clickEditByRowText("serial-42"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no edit action");
        assertThat(page.evaluate("window.deleted === true")).isEqualTo(false);
    }

    @Test
    public void editWorksEvenWhenActionOrderChanges() {
        page.route("**/project-production/update/42", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("text/html; charset=utf-8").setBody("""
                        <h1>Редагування проєктного виробництва</h1>
                        <form><div>Етапи виробництва<button>Додати етап</button></div></form>
                        """)));
        page.setContent("""
                <table><tbody><tr><td>serial-42</td><td>
                <button title="Редагувати" onclick="location.href='/project-production/update/42'">Edit</button>
                <button>Ресурси</button><button title="Видалити">Delete</button>
                </td></tr></tbody></table>
                """);
        new ProjectProductionListPage(page).clickEditByRowText("serial-42");
        assertThat(page.url()).endsWith("/project-production/update/42");
    }

    @Test
    public void fetchBlobMustProduceAnActualDownload() throws Exception {
        installExport(true);
        var download = analytics().exportStatisticsToExcel();
        try {
            assertThat(download.suggestedFilename()).isEqualTo("browser-export.xlsx");
            assertThat(download.sizeBytes()).isPositive();
            assertThat(XlsxWorkbookReader.sheetNames(Files.readAllBytes(download.path())))
                    .containsExactly("Export");
        } finally {
            Files.deleteIfExists(download.path());
        }
    }

    @Test
    public void successfulFetchWithoutDownloadFails() throws Exception {
        installExport(false);
        assertThatThrownBy(() -> analytics().exportStatisticsToExcel()).isInstanceOf(TimeoutError.class);
        assertThat(page.evaluate("window.fetched === true")).isEqualTo(true);
    }

    private ProductionAnalyticsPage analytics() {
        return new ProductionAnalyticsPage(page) {
            @Override protected int uiTimeoutMs() { return 2_000; }
        };
    }

    private void installExport(boolean download) throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            workbook.createSheet("Export").createRow(0).createCell(0).setCellValue("Amount");
            workbook.write(out);
            bytes = out.toByteArray();
        }
        page.route("**/api/v1/production/analytic/export", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .setBodyBytes(bytes)));
        page.setContent("""
                <button data-testid="production-analytics-export-excel" onclick="exportFile()">Експорт в Excel</button>
                <script>
                async function exportFile() {
                  const response = await fetch('/api/v1/production/analytic/export');
                  const blob = await response.blob();
                  window.fetched = true;
                  if (%s) {
                    const a = document.createElement('a');
                    a.href = URL.createObjectURL(blob);
                    a.download = 'browser-export.xlsx';
                    document.body.appendChild(a);
                    a.click();
                  }
                }
                </script>
                """.formatted(download));
    }
}
