package com.erp.utils.assertions;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Deployed contract: one row per operation asset; exact values and duplicate-sensitive comparison. */
public final class LowValueWriteOffExportAssertions {
    private LowValueWriteOffExportAssertions() {}
    private record Entry(String date, String storage, String resource, BigDecimal amount, String unit, String author, String comment) {}

    @SuppressWarnings("unchecked")
    public static void assertMatches(byte[] bytes, List<Map<String, Object>> expected) throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getNumberOfSheets()).as("One history worksheet").isEqualTo(1);
            Sheet sheet = book.getSheetAt(0);
            assertThat((Object) sheet.getRow(0)).as("Headers exist even for empty exports").isNotNull();
            var formatter = new DataFormatter(Locale.US);
            Map<String, Integer> headers = new LinkedHashMap<>();
            for (Cell cell : sheet.getRow(0)) {
                String name = formatter.formatCellValue(cell).trim();
                if (!name.isEmpty()) assertThat(headers.put(name, cell.getColumnIndex())).as("Unique column %s", name).isNull();
            }
            assertThat(headers.keySet()).contains("Дата", "Локація", "Ресурс", "Кількість", "Од. вимір", "Створив", "Коментар");
            List<Entry> wanted = new ArrayList<>();
            for (var operation : expected) {
                String storage = (String) ((Map<String, Object>) operation.get("storage")).get("name");
                for (var asset : (List<Map<String, Object>>) operation.get("assets")) {
                    wanted.add(new Entry(operation.get("date").toString(), storage,
                            (String) ((Map<String, Object>) asset.get("resource")).get("name"),
                            new BigDecimal(asset.get("amount").toString()).stripTrailingZeros(),
                            Objects.toString(((Map<String, Object>) asset.get("unit")).get("shortName"), ""),
                            operation.get("createdBy").toString(), Objects.toString(operation.get("comment"), "")));
                }
            }
            List<Entry> actual = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                assertThat((Object) row).as("No missing data rows").isNotNull();
                if (java.util.stream.StreamSupport.stream(row.spliterator(), false).allMatch(c -> formatter.formatCellValue(c).isBlank())) continue;
                for (Cell cell : row) assertThat(cell.getCellType()).as("Values must not become Excel formulas").isNotEqualTo(CellType.FORMULA);
                Cell amount = row.getCell(headers.get("Кількість"));
                assertThat(amount).isNotNull();
                assertThat(amount.getCellType()).as("Numeric Excel quantity").isEqualTo(CellType.NUMERIC);
                actual.add(new Entry(date(row.getCell(headers.get("Дата")), formatter),
                        text(row, headers, "Локація", formatter), text(row, headers, "Ресурс", formatter),
                        BigDecimal.valueOf(amount.getNumericCellValue()).stripTrailingZeros(),
                        text(row, headers, "Од. вимір", formatter), text(row, headers, "Створив", formatter),
                        text(row, headers, "Коментар", formatter)));
            }
            assertThat(actual).as("Exact exported assets, including duplicates, decimals and metadata")
                    .containsExactlyInAnyOrderElementsOf(wanted);
        }
    }
    private static String text(Row row, Map<String, Integer> headers, String key, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(headers.get(key))).trim();
    }
    private static String date(Cell cell, DataFormatter formatter) {
        assertThat(cell).isNotNull();
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        String value = formatter.formatCellValue(cell).trim();
        return value.matches("\\d{4}-\\d{2}-\\d{2}") ? LocalDate.parse(value).toString()
                : LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString();
    }
}
