package com.erp.utils.assertions;

import com.erp.fixtures.InventoryNameFilterFixture;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

public final class InventoryProcessExcelAssertions {
    private InventoryProcessExcelAssertions() { }
    public record Difference(long processId, String resource, Double delta) { }

    public static List<Difference> all(InventoryNameFilterFixture data) {
        return List.of(new Difference(data.mixed.getId(), data.alpha.getName(), 2.25),
                new Difference(data.mixed.getId(), data.beta.getName(), -3.5),
                new Difference(data.mixed.getId(), data.cable.getName(), 4.0),
                new Difference(data.cableOnly.getId(), data.cable.getName(), 1.0),
                new Difference(data.manual.getId(), "", null), new Difference(data.rejected.getId(), "", null));
    }

    public static void assertWorkbook(byte[] bytes, List<Difference> expected) throws IOException {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheet("Інвентаризації");
            assertThat(sheet).isNotNull();
            var format = new DataFormatter();
            assertThat(format.formatCellValue(sheet.getRow(0).getCell(0))).isEqualTo("№");
            assertThat(format.formatCellValue(sheet.getRow(0).getCell(12))).isEqualTo("Ресурс");
            assertThat(format.formatCellValue(sheet.getRow(0).getCell(13))).isEqualTo("Різниця");
            var actual = new ArrayList<Difference>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                assertThat((Object) row).isNotNull();
                Cell processCell = row.getCell(0);
                for (var merged : sheet.getMergedRegions()) {
                    if (merged.isInRange(index, 0)) processCell = sheet.getRow(merged.getFirstRow()).getCell(0);
                }
                assertThat(processCell.getCellType()).isEqualTo(CellType.NUMERIC);
                var amount = row.getCell(13);
                Double delta = null;
                if (amount != null && amount.getCellType() != CellType.BLANK) {
                    assertThat(amount.getCellType()).isEqualTo(CellType.NUMERIC);
                    delta = amount.getNumericCellValue();
                }
                actual.add(new Difference((long) processCell.getNumericCellValue(),
                        format.formatCellValue(row.getCell(12)), delta));
            }
            assertThat(actual).as("Exact process/resource/delta rows, including merged process cells")
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
