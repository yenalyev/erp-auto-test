package com.erp.utils.assertions;

import com.erp.enums.NonSeriesProductionStatus;
import com.erp.models.response.NonSeriesProductionResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads merged production blocks without counting material rows as separate products. */
public final class NonSeriesExportAssertions {
    private NonSeriesExportAssertions() {}

    public static final List<String> HEADERS = List.of("Дата", "Продукт", "Локація/Організм", "Об'єм",
            "Використана сировина", "Кількість", "Од. вимір", "Люди", "Статус");

    public static void assertMatches(byte[] bytes, List<NonSeriesProductionResponse> expected) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Несерійне виробництво");
            DataFormatter format = new DataFormatter(Locale.US);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) HEADERS.size());
            for (int col = 0; col < HEADERS.size(); col++) {
                assertThat(text(sheet.getRow(0), col, format)).isEqualTo(HEADERS.get(col));
            }
            Map<String, NonSeriesProductionResponse> byProduct = new LinkedHashMap<>();
            for (var product : expected) {
                assertThat(byProduct.put(product.getProduct(), product)).as("Unique fixture product").isNull();
            }
            List<String> actualProducts = new ArrayList<>();
            int rowIndex = 1;
            while (rowIndex <= sheet.getLastRowNum()) {
                Row row = sheet.getRow(rowIndex);
                String name = text(row, 1, format);
                actualProducts.add(name);
                assertThat(byProduct).as("Unexpected production at Excel row %s", rowIndex + 1).containsKey(name);
                var product = byProduct.get(name);
                assertThat(text(row, 0, format)).isEqualTo(product.getStart().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                assertThat(text(row, 2, format)).isEqualTo(product.getStorage().getName());
                number(row, 3, product.getAmount());
                number(row, 7, BigDecimal.valueOf(product.getWorkerQty()));
                assertThat(text(row, 8, format)).isEqualTo(product.getStatus() == NonSeriesProductionStatus.DONE
                        ? "Завершено" : "В роботі");
                var usages = product.getResourceUsageList();
                Map<String, BigDecimal> actualMaterials = new LinkedHashMap<>();
                for (int i = 0; i < usages.size(); i++) {
                    Row materialRow = sheet.getRow(rowIndex + i);
                    String materialName = text(materialRow, 4, format);
                    var usage = usages.stream().filter(u -> u.getResource().getName().equals(materialName))
                            .findFirst().orElseThrow(() -> new AssertionError("Unexpected material " + materialName));
                    assertThat(actualMaterials.put(materialName, usage.getAmount())).as("No duplicate material").isNull();
                    number(materialRow, 5, usage.getAmount());
                    assertThat(text(materialRow, 6, format)).isEqualTo(usage.getUnit().getShortName());
                    if (i > 0) {
                        for (int col : List.of(0, 1, 2, 3, 7, 8)) {
                            assertThat(text(materialRow, col, format)).as("Production values appear once").isEmpty();
                        }
                    }
                }
                assertThat(actualMaterials.keySet()).containsExactlyInAnyOrderElementsOf(
                        usages.stream().map(u -> u.getResource().getName()).toList());
                if (usages.isEmpty()) {
                    for (int col : List.of(4, 5, 6)) assertThat(text(row, col, format)).isEmpty();
                }
                rowIndex += Math.max(1, usages.size());
            }
            assertThat(actualProducts).containsExactlyInAnyOrderElementsOf(byProduct.keySet());
            assertThat(sheet.getLastRowNum()).isEqualTo(expected.stream()
                    .mapToInt(p -> Math.max(1, p.getResourceUsageList().size())).sum());
        }
    }

    private static String text(Row row, int col, DataFormatter format) {
        assertThat(row).as("Workbook row exists").isNotNull();
        return format.formatCellValue(row.getCell(col)).trim();
    }

    private static void number(Row row, int col, BigDecimal expected) {
        Cell cell = row.getCell(col);
        assertThat(cell).as("Numeric cell %s", col).isNotNull();
        assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(BigDecimal.valueOf(cell.getNumericCellValue())).isEqualByComparingTo(expected);
    }
}
