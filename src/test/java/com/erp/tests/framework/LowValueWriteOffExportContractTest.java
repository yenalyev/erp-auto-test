package com.erp.tests.framework;

import com.erp.utils.assertions.LowValueWriteOffExportAssertions;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rejects data leakage, duplicates and changed decimal quantities. No ERP access. */
public class LowValueWriteOffExportContractTest {
    private static final List<Map<String, Object>> EXPECTED = List.of(Map.of(
            "date", "2026-10-28", "storage", Map.of("name", "A"), "createdBy", "Actor", "comment", "Коментар",
            "assets", List.of(Map.of("resource", Map.of("name", "Тканина"), "amount", 1.25, "unit", Map.of("shortName", "шт")))));
    private byte[] workbook(String resource, double amount, int count, boolean textAmount) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("МШП");
            var header = sheet.createRow(0);
            String[] columns = {"Дата", "Локація", "Ресурс", "Кількість", "Од. вимір", "Створив", "Коментар"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            for (int i = 1; i <= count; i++) {
                var row = sheet.createRow(i);
                String[] values = {"28.10.2026", "A", resource, "", "шт", "Actor", "Коментар"};
                for (int j = 0; j < values.length; j++) row.createCell(j).setCellValue(values[j]);
                if (textAmount) row.getCell(3).setCellValue(Double.toString(amount));
                else row.getCell(3).setCellValue(amount);
            }
            book.write(out);
            return out.toByteArray();
        }
    }
    @Test public void acceptsExactDecimalAndEmptyWorkbooks() throws Exception {
        LowValueWriteOffExportAssertions.assertMatches(workbook("Тканина", 1.25, 1, false), EXPECTED);
        LowValueWriteOffExportAssertions.assertMatches(workbook("", 0, 0, false), List.of());
    }
    @Test public void rejectsWrongDecimalAndForeignResource() throws Exception {
        byte[] wrong = workbook("Тканина", 1.26, 1, false), leaked = workbook("Сторонній", 1.25, 1, false);
        assertThatThrownBy(() -> LowValueWriteOffExportAssertions.assertMatches(wrong, EXPECTED)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> LowValueWriteOffExportAssertions.assertMatches(leaked, EXPECTED)).isInstanceOf(AssertionError.class);
    }
    @Test public void rejectsMissingAndDuplicateOperations() throws Exception {
        byte[] duplicate = workbook("Тканина", 1.25, 2, false), missing = workbook("", 0, 0, false);
        assertThatThrownBy(() -> LowValueWriteOffExportAssertions.assertMatches(duplicate, EXPECTED)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> LowValueWriteOffExportAssertions.assertMatches(missing, EXPECTED)).isInstanceOf(AssertionError.class);
    }
    @Test public void rejectsTextInsteadOfNumericAmount() throws Exception {
        byte[] bytes = workbook("Тканина", 1.25, 1, true);
        assertThatThrownBy(() -> LowValueWriteOffExportAssertions.assertMatches(bytes, EXPECTED)).isInstanceOf(AssertionError.class);
    }
}
