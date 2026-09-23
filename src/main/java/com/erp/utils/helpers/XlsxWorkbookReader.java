package com.erp.utils.helpers;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small semantic XLSX reader used by export contract tests. */
public final class XlsxWorkbookReader {
    private XlsxWorkbookReader() {
    }

    public static List<List<String>> firstSheet(byte[] bytes) {
        return sheets(bytes).values().iterator().next();
    }

    /** Reads every worksheet in workbook order; plan execution uses separate planned/out-of-plan sheets. */
    public static Map<String, List<List<String>>> sheets(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("XLSX response is empty");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                List<List<String>> rows = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        values.add(formatter.formatCellValue(cell).trim());
                    }
                    rows.add(values);
                }
                sheets.put(sheet.getSheetName(), rows);
            }
            return sheets;
        } catch (IOException e) {
            throw new IllegalArgumentException("Response is not a readable XLSX workbook", e);
        }
    }
}
