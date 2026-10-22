package com.erp.utils.assertions;

import com.erp.fixtures.RelocationExportColumnsFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.utils.helpers.XlsxWorkbookReader;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public final class RelocationExportColumnsAssertions {
    private RelocationExportColumnsAssertions() { }
    public static final String SHEET = "Переміщення";
    public static final List<String> HEADERS = List.of("ID", "Надіслано", "Отримано", "Відправник",
            "Група відправника", "Отримувач", "Група отримувача", "Тип отримувача", "Категорія",
            "Назва", "Кількість", "Одиниця виміру", "Накладна", "Статус", "Примітки", "Постачальник",
            "Тип БЧ", "Тип Носія", "Завод", "Вага");

    public static List<List<String>> rows(byte[] bytes) {
        var sheets = XlsxWorkbookReader.sheets(bytes);
        assertThat(sheets.keySet()).contains(SHEET);
        var rows = sheets.get(SHEET);
        assertThat(rows).isNotEmpty();
        assertThat(rows.getFirst()).as("Exact order and unique headers").containsExactlyElementsOf(HEADERS);
        return rows.subList(1, rows.size());
    }

    public static String cell(List<String> row, String header) {
        int index = HEADERS.indexOf("Видав".equals(header) ? "Відправник" : header);
        assertThat(index).isNotNegative();
        assertThat(row.size()).as("Row contains column %s", header).isGreaterThan(index);
        return row.get(index);
    }

    public static List<List<String>> ownedRows(byte[] bytes, RelocationExportColumnsFixture fixture) {
        var senders = fixture.transfers().stream().map(t -> t.sender().getName()).toList();
        var recipients = fixture.transfers().stream().map(t -> t.recipient().getName()).toList();
        var products = fixture.products().stream().map(p -> p.getName()).toList();
        return rows(bytes).stream().filter(r -> senders.contains(cell(r, "Видав"))
                && recipients.contains(cell(r, "Отримувач")) && products.contains(cell(r, "Назва"))).toList();
    }

    public static void assertKinds(byte[] bytes, RelocationExportColumnsFixture fixture) {
        var rows = ownedRows(bytes, fixture);
        for (var transfer : fixture.transfers()) {
            var matches = rows.stream().filter(r -> cell(r, "Отримувач").equals(transfer.recipient().getName())).toList();
            assertThat(matches).isNotEmpty().allSatisfy(r -> assertThat(cell(r, "Тип отримувача"))
                    .as("Recipient %s kind", transfer.recipient().getName()).isEqualTo(transfer.kind()));
        }
    }

    public static void assertGroups(byte[] bytes, RelocationExportColumnsFixture fixture, boolean sender) {
        var rows = ownedRows(bytes, fixture);
        assertThat(rows).hasSize(fixture.transfers().size() * fixture.products().size());
        var softly = new SoftAssertions();
        for (var transfer : fixture.transfers()) {
            var matches = rows.stream().filter(r -> cell(r, "Видав").equals(transfer.sender().getName())
                    && cell(r, "Отримувач").equals(transfer.recipient().getName())).toList();
            softly.assertThat(matches).hasSize(fixture.products().size());
            for (var row : matches) softly.assertThat(cell(row, sender ? "Група відправника" : "Група отримувача"))
                    .as("%s → %s (%s)", transfer.sender().getName(), transfer.recipient().getName(), transfer.kind())
                    .isEqualTo(sender ? transfer.senderGroup() : transfer.recipientGroup());
        }
        softly.assertAll();
    }

    /** Compare multisets, preserving two legitimate transfers with identical visible cells. */
    public static void assertJournalMatches(byte[] bytes, RelocationExportColumnsFixture fixture,
                                            List<RelocationResponse> journal) {
        var expected = new LinkedHashMap<String, Integer>();
        for (var relocation : journal) for (var item : relocation.getItems()) {
            String key = relocation.getId() + " | " + key(relocation.getSender().getName(), relocation.getRecipient().getName(),
                    item.getResource().getName(), item.getAmount());
            expected.merge(key, 1, Integer::sum);
        }
        var actual = new LinkedHashMap<String, Integer>();
        for (var row : ownedRows(bytes, fixture)) {
            actual.merge(cell(row, "ID") + " | " + key(cell(row, "Видав"), cell(row, "Отримувач"), cell(row, "Назва"),
                    new BigDecimal(cell(row, "Кількість").replace(',', '.'))), 1, Integer::sum);
            var product = fixture.products().stream().filter(p -> p.getName().equals(cell(row, "Назва"))).findFirst().orElseThrow();
            assertThat(cell(row, "Категорія")).isEqualTo(product.getCategory().getName());
            assertThat(cell(row, "Статус")).isEqualTo("Завершено");
            assertThat(cell(row, "Надіслано")).isNotBlank();
        }
        assertThat(actual).as("Excel positions vs both paginated journal tabs; batch count must not multiply rows")
                .isEqualTo(expected);
    }

    private static String key(String sender, String recipient, String product, BigDecimal amount) {
        return String.join(" | ", sender, recipient, product, amount.stripTrailingZeros().toPlainString());
    }
}
