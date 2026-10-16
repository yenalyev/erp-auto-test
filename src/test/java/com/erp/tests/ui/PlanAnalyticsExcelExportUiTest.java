package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.pages.PlanAnalyticsPage;
import com.erp.pages.components.DateRangePickerComponent;
import com.erp.tests.functional.analytics.PlanAnalyticsExcelExportApiTest;
import com.erp.utils.assertions.PlanAnalyticsExportAssertions;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.PlanAnalyticsRowsSnapshot;
import com.erp.utils.helpers.UiDownloadAssertions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Response;
import io.qameta.allure.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Analytics")
@Feature("Plan analytics Excel export")
public class PlanAnalyticsExcelExportUiTest extends BaseUITest {
    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '"
                + ConfigProvider.getOwner1StorageId() + "');");
    }

    @Test
    @TestCaseId("TC-PLAN-ANL-XLSX-UI-001")
    @Description("Після явного очищення початкової категорії Боєприпас показано порожній стан, кнопка експорту недоступна.")
    public void exportIsUnavailableWithoutSelection() {
        var analytics = new PlanAnalyticsPage(page).open().clearCategories();
        page.waitForCondition(analytics::isEmptyStateVisible);
        analytics.attachScreenshot("TC-PLAN-ANL-XLSX-UI-001 — empty selection");
        assertThat(analytics.isEmptyStateVisible()).isTrue();
        assertThat(analytics.isExportVisible()).isFalse();
    }

    @Test
    @TestCaseId("TC-PLAN-ANL-XLSX-UI-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("REQ-PLAN-ANL-EXCEL / AC-01..03. Реальний UI та backend без підміни rows/export. "
            + "Обрана категорія з кількома сторінками; файл містить усю вибірку. "
            + "Перевірка повторюється після вибору Постачальника та зміни періоду; запит export має ті самі фільтри, що UI rows.")
    public void downloadedWorkbookMatchesUiFiltersAndAllPages() throws Exception {
        var resources = new ResourceFixture(testContext, apiExecutor).getPage(UserRole.ADMIN, true, null);
        var candidates = resources.stream().filter(r -> r.getCategory() != null)
                .map(r -> r.getCategory()).distinct().toList();
        String category = null;
        for (var candidate : candidates) {
            var filters = PlanAnalyticsExcelExportApiTest.period();
            filters.put("categoryIds", List.of(candidate.getId()));
            filters.put("size", 1);
            var probe = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET,
                    UserRole.ADMIN, filters);
            assertThat(probe.statusCode()).isEqualTo(200);
            if (probe.jsonPath().getInt("page.totalElements") > 25) {
                category = candidate.getName();
                break;
            }
        }
        assertThat(category).as("Потрібна категорія з >25 рядками для реальної UI-пагінації").isNotNull();
        var analytics = new PlanAnalyticsPage(page).open().clearCategories();
        String selectedCategory = category;
        page.waitForResponse(PlanAnalyticsExcelExportUiTest::isRows,
                () -> analytics.selectCategory(selectedCategory));
        page.waitForResponse(response -> isRows(response)
                        && query(response.url()).getOrDefault("size", List.of()).contains("25"),
                () -> analytics.setPageSize(25));
        Response initial = page.waitForResponse(response -> isRows(response)
                        && query(response.url()).getOrDefault("page", List.of()).contains("1"),
                analytics::nextTablePage);
        var firstBody = new ObjectMapper().readTree(initial.text());
        assertThat(firstBody.path("page").path("totalElements").asInt())
                .isGreaterThan(firstBody.path("content").size());
        assertDownload(analytics, initial, "all pages");

        var supplierChoices = analytics.supplierOptions().stream().limit(2).toList();
        assertThat(supplierChoices).as("Два доступні постачальники з конфігурації UI").hasSize(2);
        page.waitForResponse(response -> isRows(response)
                        && query(response.url()).getOrDefault("supplier", List.of()).containsAll(supplierChoices),
                () -> analytics.selectSuppliers(supplierChoices.toArray(String[]::new)));
        String threeMonthsFrom = PlanAnalyticsPage.threeFullMonthsFrom(analytics.browserToday()).toString();
        Response periodRows = page.waitForResponse(response -> isRows(response)
                        && query(response.url()).getOrDefault("fromDate", List.of()).contains(threeMonthsFrom),
                () -> analytics.periodPicker().clickPreset(DateRangePickerComponent.PRESET_3_MONTHS));
        assertThat(query(periodRows.url()).get("supplier")).containsExactlyInAnyOrderElementsOf(supplierChoices);
        assertThat(query(periodRows.url()).get("fromDate")).isNotEqualTo(query(initial.url()).get("fromDate"));
        assertDownload(analytics, periodRows, "suppliers and changed period");
        Response clearedRows = page.waitForResponse(response -> isRows(response)
                        && !query(response.url()).containsKey("supplier"),
                () -> analytics.clearSuppliers(supplierChoices.toArray(String[]::new)));
        assertDownload(analytics, clearedRows, "cleared suppliers");
    }

    @Test
    @TestCaseId("TC-PLAN-ANL-XLSX-UI-003")
    @Severity(SeverityLevel.CRITICAL)
    @Description("REQ-PLAN-ANL-EXCEL / AC-03. Експорт одразу після відкриття використовує "
            + "початкову категорію Боєприпас; усі сторінки, показники та підсумки відповідають rows.")
    public void defaultAmmunitionExportMatchesAllFilteredRows() throws Exception {
        var analytics = new PlanAnalyticsPage(page);
        Response rows = page.waitForResponse(PlanAnalyticsExcelExportUiTest::isRows, analytics::open);
        page.waitForCondition(() -> analytics.selectedCategoryNames().equals(List.of(PlanAnalyticsPage.DEFAULT_CATEGORY)));
        assertThat(query(rows.url()).get("categoryIds")).hasSize(1);
        assertDownload(analytics, rows, "default ammunition category");
    }

    private void assertDownload(PlanAnalyticsPage analytics, Response uiRows, String label) throws Exception {
        assertThat(uiRows.status()).isEqualTo(200);
        var filters = query(uiRows.url());
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");
        Map<String, Object> params = new LinkedHashMap<>(filters);
        var expected = PlanAnalyticsRowsSnapshot.load(apiExecutor, params, 100);
        var body = new ObjectMapper().readTree(uiRows.text());
        page.waitForCondition(() -> analytics.dataRowCount() == body.path("content").size());
        var shown = analytics.dataRowCells();
        for (int row = 0; row < shown.size(); row++) {
            var expectedRow = body.path("content").get(row);
            var cells = shown.get(row);
            assertThat(cells).hasSize(9);
            assertThat(visibleText(cells.get(1))).isEqualTo(visibleText(expectedRow.path("resourceName").asText()));
            assertThat(visibleText(cells.get(2))).isEqualTo(visibleText(expectedRow.path("resourceCategoryName").asText()));
            assertThat(cells.get(3)).isEqualTo(expectedRow.path("unit").asText("—"));
            for (int metric = 0; metric < 5; metric++) {
                var value = expectedRow.path(PlanAnalyticsExportAssertions.KEYS.get(metric)).decimalValue();
                String displayed = cells.get(metric + 4).replaceAll("[\\p{Z}\\s]", "").replace(',', '.');
                if (value.signum() == 0) assertThat(displayed).isEqualTo("—");
                else assertThat(new BigDecimal(displayed)).isEqualByComparingTo(value.setScale(5, RoundingMode.HALF_UP));
            }
        }
        for (int metric = 0; metric < 5; metric++) {
            String key = PlanAnalyticsExportAssertions.KEYS.get(metric);
            String card = analytics.totalCardText(PlanAnalyticsExportAssertions.LABELS.get(metric))
                    .replaceAll("[\\p{Z}\\s]", "").replace(',', '.');
            boolean hasNonZero = false;
            for (var total : body.path("totals")) {
                var value = total.path(key).decimalValue();
                if (value.signum() == 0) continue;
                hasNonZero = true;
                String quantity = value.setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                String unit = total.path("unit").asText("").replaceAll("[\\p{Z}\\s]", "");
                assertThat(card).containsPattern("(?<![\\d.])" + java.util.regex.Pattern.quote(quantity + unit));
            }
            if (!hasNonZero) assertThat(card).contains("—");
        }
        analytics.attachScreenshot("Plan analytics Excel — " + label);
        final com.microsoft.playwright.Download[] download = new com.microsoft.playwright.Download[1];
        Response export = page.waitForResponse(response -> response.url().contains("/analytics/plan/export"),
                () -> download[0] = analytics.exportExcel());
        assertThat(export.status()).isEqualTo(200);
        assertThat(query(export.url())).as("Export filters equal UI filters").isEqualTo(filters);
        assertThat(download[0].failure()).isNull();
        var path = download[0].path();
        UiDownloadAssertions.assertNonEmptyXlsx(path, Files.size(path), label);
        byte[] bytes = Files.readAllBytes(path);
        Allure.addAttachment("Plan analytics — " + label,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new java.io.ByteArrayInputStream(bytes), ".xlsx");
        PlanAnalyticsExportAssertions.assertMatches(bytes, expected.rows(), expected.totals());
    }

    private static boolean isRows(Response response) {
        return response.url().contains("/api/v1/analytics/plan/rows") && response.request().method().equals("GET");
    }

    private static String visibleText(String value) {
        return value.replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    private static Map<String, List<String>> query(String url) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null) return result;
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).replace("[]", "");
            if (pair.length == 2) result.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
