package com.erp.utils.helpers;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
import java.util.Set;
import java.util.LinkedHashSet;

/** Small semantic XLSX reader used by export contract tests. */
public final class XlsxWorkbookReader {
    private XlsxWorkbookReader() {
    }

    public static List<List<String>> firstSheet(byte[] bytes) {
        return sheets(bytes).values().iterator().next();
    }

    /** Returns worksheet names in workbook order. Hidden sheets are included. */
    public static List<String> sheetNames(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("XLSX response is empty");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                names.add(workbook.getSheetName(i));
            }
            return names;
        } catch (IOException e) {
            throw new IllegalArgumentException("Response is not a readable XLSX workbook", e);
        }
    }

    /** Returns hidden and very-hidden worksheet names in workbook order. */
    public static Set<String> hiddenSheetNames(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("XLSX response is empty");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Set<String> names = new LinkedHashSet<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                if (workbook.isSheetHidden(i) || workbook.isSheetVeryHidden(i)) {
                    names.add(workbook.getSheetName(i));
                }
            }
            return names;
        } catch (IOException e) {
            throw new IllegalArgumentException("Response is not a readable XLSX workbook", e);
        }
    }

    /** Reads the named numeric column, rejecting text, missing cells and formulas in data rows. */
    public static List<java.math.BigDecimal> numericColumnValues(byte[] bytes, String sheetName, String header) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("XLSX response is empty");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Workbook has no sheet: " + sheetName);
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Workbook sheet has no header: " + sheetName);
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(formatter.formatCellValue(headerRow.getCell(i)).trim());
            }
            int column = columnIndex(headers, header);
            List<java.math.BigDecimal> values = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                boolean hasData = false;
                for (Cell cell : row) {
                    if (!formatter.formatCellValue(cell).isBlank()) {
                        hasData = true;
                        break;
                    }
                }
                if (!hasData) {
                    continue;
                }
                Cell cell = row.getCell(column);
                if (cell == null || cell.getCellType() != CellType.NUMERIC
                        || org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    throw new AssertionError("Expected native number in " + sheetName + "/" + header
                            + " at row " + (rowIndex + 1));
                }
                values.add(java.math.BigDecimal.valueOf(cell.getNumericCellValue()));
            }
            return values;
        } catch (IOException e) {
            throw new IllegalArgumentException("Response is not a readable XLSX workbook", e);
        }
    }

    public static int columnIndex(List<String> headers, String header) {
        int index = headers.indexOf(header);
        if (index < 0 || index != headers.lastIndexOf(header)) {
            throw new IllegalArgumentException("Expected one column named " + header + ", got " + headers);
        }
        return index;
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
