package com.erp.utils.assertions;

import com.erp.utils.helpers.XlsxWorkbookReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Compares native XLSX cells with the complete /analytics/plan/rows selection. */
public final class PlanAnalyticsExportAssertions {
    public static final List<String> KEYS = List.of("produced", "relocated", "used", "stockTotal", "stockInRoot");
    public static final List<String> LABELS = List.of("Вироблено", "Відвантажено", "Використано",
            "Залишок усього", "Залишок (Цукрарня)");
    public static final List<String> HEADERS = List.of("Ресурс", "Категорія", "Од.", "Вироблено",
            "Відвантажено", "Використано", "Залишок усього", "Залишок (Цукрарня)");

    private PlanAnalyticsExportAssertions() { }

    public static void assertMatches(byte[] bytes, List<Map<String, Object>> rows,
                                     List<Map<String, Object>> totals) throws Exception {
        assertThat(XlsxWorkbookReader.sheetNames(bytes)).containsExactly("Підсумки", "Ресурси");
        assertThat(XlsxWorkbookReader.hiddenSheetNames(bytes)).isEmpty();
        var sheets = XlsxWorkbookReader.sheets(bytes);
        var resources = sheets.get("Ресурси");
        assertThat(resources.getFirst()).containsExactlyElementsOf(HEADERS);
        assertThat(resources.size() - 1).as("Кількість ресурсів у файлі дорівнює повній відфільтрованій вибірці")
                .isEqualTo(rows.size());
        assertThat(resources.subList(1, resources.size())).extracting(row -> row.getFirst())
                .containsExactlyInAnyOrderElementsOf(rows.stream().map(row -> text(row.get("resourceName"))).toList());
        var summary = sheets.get("Підсумки");
        assertThat(summary).hasSize(6);
        assertThat(summary.getFirst().getFirst()).isEqualTo("В загальному");
        assertThat(summary.getFirst().subList(1, summary.getFirst().size()))
                .containsExactlyInAnyOrderElementsOf(totals.stream().map(total -> text(total.get("unit"))).toList());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var resourceSheet = workbook.getSheet("Ресурси");
            List<List<Object>> actualRows = new ArrayList<>();
            for (int index = 1; index < resources.size(); index++) {
                var shown = resources.get(index);
                assertThat(shown).hasSize(8);
                List<Object> values = new ArrayList<>(shown.subList(0, 3));
                for (int metric = 0; metric < KEYS.size(); metric++) {
                    values.add(number(resourceSheet.getRow(index).getCell(metric + 3),
                            shown.getFirst() + "/" + KEYS.get(metric)).stripTrailingZeros());
                }
                actualRows.add(values);
            }
            List<List<Object>> expectedRows = rows.stream().map(row -> {
                List<Object> values = new ArrayList<>(List.of(text(row.get("resourceName")),
                        text(row.get("resourceCategoryName")), text(row.get("unit"))));
                for (String key : KEYS) values.add(new BigDecimal(row.get(key).toString()).stripTrailingZeros());
                return values;
            }).toList();
            assertThat(actualRows).as("Кожен ресурс і всі п'ять показників; порядок довільний, кратність збережена")
                    .containsExactlyInAnyOrderElementsOf(expectedRows);
            var totalSheet = workbook.getSheet("Підсумки");
            for (int metric = 0; metric < KEYS.size(); metric++) {
                assertThat(summary.get(metric + 1).getFirst()).isEqualTo(LABELS.get(metric));
                assertThat(summary.get(metric + 1)).hasSize(totals.size() + 1);
                for (var total : totals) {
                    int column = summary.getFirst().indexOf(text(total.get("unit")));
                    assertNumber(totalSheet.getRow(metric + 1).getCell(column), total.get(KEYS.get(metric)),
                            "Підсумки/" + total.get("unit") + "/" + KEYS.get(metric));
                }
            }
        }
    }

    private static void assertNumber(Cell cell, Object expected, String label) {
        assertThat(number(cell, label)).as(label).isEqualByComparingTo(new BigDecimal(expected.toString()));
    }

    private static BigDecimal number(Cell cell, String label) {
        assertThat(cell).as(label).isNotNull();
        assertThat(cell.getCellType()).as(label + " — числова комірка").isEqualTo(CellType.NUMERIC);
        return BigDecimal.valueOf(cell.getNumericCellValue());
    }

    private static String text(Object value) { return value == null ? "" : value.toString().trim(); }
}
