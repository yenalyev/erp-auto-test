package com.erp.tests.framework;

import com.erp.utils.assertions.PlanAnalyticsExportAssertions;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mutation checks for export assertions; local in-memory XLSX, no ERP or browser. */
public class PlanAnalyticsExportContractTest {
    private static final List<Map<String, Object>> ROWS = List.of(row("Resource A", "шт", 3.25), row("Resource B", "кг", 7.5));
    private static final List<Map<String, Object>> TOTALS = ROWS.stream().map(row -> {
        Map<String, Object> total = new LinkedHashMap<>(row);
        total.remove("resourceName");
        total.remove("resourceCategoryName");
        return total;
    }).toList();

    @Test
    public void acceptsDecimalsZeroesAndSeparateUnits() throws Exception {
        PlanAnalyticsExportAssertions.assertMatches(workbook("valid"), ROWS, TOTALS);
    }

    @DataProvider(name = "regressions")
    public Object[][] regressions() {
        return new Object[][] {{"missing row"}, {"extra row"}, {"wrong total"}, {"text number"},
                {"swapped sheets"}, {"wrong category"}, {"wrong resource amount"}};
    }

    @Test(dataProvider = "regressions")
    public void rejectsBrokenExport(String regression) {
        assertThatThrownBy(() -> PlanAnalyticsExportAssertions.assertMatches(workbook(regression), ROWS, TOTALS))
                .isInstanceOf(AssertionError.class);
    }

    private static Map<String, Object> row(String name, String unit, double amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceName", name);
        row.put("resourceCategoryName", "Category");
        row.put("unit", unit);
        for (String key : PlanAnalyticsExportAssertions.KEYS) row.put(key, key.equals("used") ? 0 : amount);
        return row;
    }

    private static byte[] workbook(String mutation) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var summary = workbook.createSheet("Підсумки");
            var resources = workbook.createSheet("Ресурси");
            var header = resources.createRow(0);
            for (int col = 0; col < PlanAnalyticsExportAssertions.HEADERS.size(); col++) {
                header.createCell(col).setCellValue(PlanAnalyticsExportAssertions.HEADERS.get(col));
            }
            for (int index = 0; index < ROWS.size(); index++) {
                var expected = ROWS.get(index);
                var row = resources.createRow(index + 1);
                row.createCell(0).setCellValue(expected.get("resourceName").toString());
                row.createCell(1).setCellValue("Category");
                row.createCell(2).setCellValue(expected.get("unit").toString());
                for (int metric = 0; metric < 5; metric++) row.createCell(metric + 3)
                        .setCellValue(((Number) expected.get(PlanAnalyticsExportAssertions.KEYS.get(metric))).doubleValue());
            }
            var totalHeader = summary.createRow(0);
            totalHeader.createCell(0).setCellValue("В загальному");
            for (int col = 0; col < TOTALS.size(); col++) totalHeader.createCell(col + 1)
                    .setCellValue(TOTALS.get(col).get("unit").toString());
            for (int metric = 0; metric < 5; metric++) {
                var row = summary.createRow(metric + 1);
                row.createCell(0).setCellValue(PlanAnalyticsExportAssertions.LABELS.get(metric));
                for (int col = 0; col < TOTALS.size(); col++) row.createCell(col + 1).setCellValue(
                        ((Number) TOTALS.get(col).get(PlanAnalyticsExportAssertions.KEYS.get(metric))).doubleValue());
            }
            switch (mutation) {
                case "missing row" -> resources.removeRow(resources.getRow(2));
                case "extra row" -> resources.createRow(3).createCell(0).setCellValue("Unfiltered resource");
                case "wrong total" -> summary.getRow(1).getCell(1).setCellValue(99);
                case "text number" -> resources.getRow(1).getCell(3).setCellValue("3.25");
                case "swapped sheets" -> workbook.setSheetOrder("Ресурси", 0);
                case "wrong category" -> resources.getRow(1).getCell(1).setCellValue("Other");
                case "wrong resource amount" -> resources.getRow(1).getCell(3).setCellValue(99);
                default -> { }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
