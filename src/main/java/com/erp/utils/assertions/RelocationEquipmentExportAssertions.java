package com.erp.utils.assertions;

import com.erp.models.response.RelocationResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Compare every exported equipment item with the same filtered journal result. */
public final class RelocationEquipmentExportAssertions {
    private RelocationEquipmentExportAssertions() { }

    public static void assertMatches(List<List<String>> workbook, List<RelocationResponse> relocations) {
        assertThat(workbook).as("Equipment workbook has a header").isNotEmpty();
        List<String> headers = workbook.getFirst();
        List<String> columns = List.of("Назва", "Інв. номер", "Від", "До", "Дата", "Примітки");
        assertThat(headers).containsAll(columns);
        List<Integer> indexes = columns.stream().map(headers::indexOf).toList();
        List<List<String>> actual = workbook.stream().skip(1)
                .filter(row -> row.stream().anyMatch(value -> !value.isBlank()))
                .map(row -> indexes.stream().map(i -> i < row.size() ? row.get(i) : "").toList()).toList();
        List<List<String>> expected = relocations.stream().flatMap(relocation ->
                relocation.getEquipmentItems().stream().map(equipment -> List.of(
                        equipment.getName() + (equipment.getSerialNumber() == null
                                || equipment.getSerialNumber().isBlank() ? "" : " " + equipment.getSerialNumber()),
                        equipment.getInventoryNumber(), relocation.getSender().getName(),
                        relocation.getRecipient().getName(), relocation.getDate().toString(),
                        relocation.getDescription() == null ? "" : relocation.getDescription()))).toList();
        assertThat(actual).as("Exact equipment cargo in XLSX: model, serial, inventory number, route, date, notes")
                .containsExactlyInAnyOrderElementsOf(expected);
    }
}
