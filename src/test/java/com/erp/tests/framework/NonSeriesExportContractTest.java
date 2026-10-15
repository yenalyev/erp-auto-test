package com.erp.tests.framework;

import com.erp.enums.NonSeriesProductionStatus;
import com.erp.models.response.*;
import com.erp.utils.assertions.NonSeriesExportAssertions;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Local verifier checks: broken spreadsheet data must not become a false PASS on dev. */
public class NonSeriesExportContractTest {
    private NonSeriesProductionResponse product() {
        return NonSeriesProductionResponse.builder().id(1L).product("Контроль").start(LocalDate.of(2026, 10, 15))
                .amount(new BigDecimal("3.5")).workerQty(2).status(NonSeriesProductionStatus.DONE)
                .storage(SimpleEntityResponse.builder().id(10L).name("Цех").build())
                .resourceUsageList(List.of(NonSeriesProductionResourceUsageResponse.builder()
                        .resource(SimpleEntityResponse.builder().id(20L).name("Мідь").build())
                        .amount(new BigDecimal("1.25")).unit(MeasurementUnitResponse.builder().shortName("кг").build()).build()))
                .build();
    }

    private byte[] workbook(String mutation) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Несерійне виробництво");
            var header = sheet.createRow(0);
            for (int i = 0; i < NonSeriesExportAssertions.HEADERS.size(); i++) header.createCell(i).setCellValue(NonSeriesExportAssertions.HEADERS.get(i));
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("15.10.2026");
            row.createCell(1).setCellValue(mutation.equals("wrong product") ? "Сторонній" : "Контроль");
            row.createCell(2).setCellValue("Цех");
            if (mutation.equals("text volume")) row.createCell(3).setCellValue("3.5");
            else row.createCell(3).setCellValue(3.5);
            row.createCell(4).setCellValue(mutation.equals("wrong material") ? "Сталь" : "Мідь");
            row.createCell(5).setCellValue(1.25);
            row.createCell(6).setCellValue("кг");
            row.createCell(7).setCellValue(2);
            row.createCell(8).setCellValue("Завершено");
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    @Test public void acceptsExactWorkbook() throws Exception {
        NonSeriesExportAssertions.assertMatches(workbook("valid"), List.of(product()));
    }

    @Test public void rejectsForeignProducts() throws Exception {
        byte[] bytes = workbook("wrong product");
        assertThatThrownBy(() -> NonSeriesExportAssertions.assertMatches(bytes, List.of(product()))).isInstanceOf(AssertionError.class);
    }

    @Test public void rejectsForeignMaterials() throws Exception {
        byte[] bytes = workbook("wrong material");
        assertThatThrownBy(() -> NonSeriesExportAssertions.assertMatches(bytes, List.of(product()))).isInstanceOf(AssertionError.class);
    }

    @Test public void rejectsTextInsteadOfNumber() throws Exception {
        byte[] bytes = workbook("text volume");
        assertThatThrownBy(() -> NonSeriesExportAssertions.assertMatches(bytes, List.of(product()))).isInstanceOf(AssertionError.class);
    }
}
