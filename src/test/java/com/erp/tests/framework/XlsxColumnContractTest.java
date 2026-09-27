package com.erp.tests.framework;

import com.erp.utils.helpers.XlsxWorkbookReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class XlsxColumnContractTest {
    @Test
    public void numericCountCannotHideTextAmountInAnyRow() throws Exception {
        byte[] bytes = workbook(false);
        assertThat(XlsxWorkbookReader.numericColumnValues(bytes, "Production", "Count")).hasSize(2);
        assertThatThrownBy(() -> XlsxWorkbookReader.numericColumnValues(bytes, "Production", "Amount"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("row 3");
    }

    @Test
    public void selectsNamedColumnAndChecksEveryAmount() throws Exception {
        var values = XlsxWorkbookReader.numericColumnValues(workbook(true), "Production", "Amount");
        assertThat(values).hasSize(2);
        assertThat(values.get(0)).isEqualByComparingTo("12.5");
        assertThat(values.get(1)).isEqualByComparingTo("3.25");
        assertThatThrownBy(() -> XlsxWorkbookReader.columnIndex(List.of("Amount", "Amount"), "Amount"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> XlsxWorkbookReader.columnIndex(List.of("Count"), "Amount"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] workbook(boolean numericAmount) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Production");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Count");
            header.createCell(1).setCellValue("Amount");
            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(1);
            first.createCell(1).setCellValue(12.5);
            var second = sheet.createRow(2);
            second.createCell(0).setCellValue(2);
            if (numericAmount) second.createCell(1).setCellValue(3.25);
            else second.createCell(1).setCellValue("3.25");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
