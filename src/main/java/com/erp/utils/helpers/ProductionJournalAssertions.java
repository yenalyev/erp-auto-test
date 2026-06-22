package com.erp.utils.helpers;

import com.erp.models.common.ProductionJournalRow;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class ProductionJournalAssertions {

    private static final double AMOUNT_EPSILON = 0.0001;

    public static Optional<ProductionJournalRow> findRowByBatchNumber(List<ProductionJournalRow> rows,
                                                                      String batchNumber) {
        if (batchNumber == null || batchNumber.isBlank()) {
            return Optional.empty();
        }
        return rows.stream()
                .filter(row -> batchNumber.equals(row.getBatchNumber()))
                .findFirst();
    }

    /**
     * Verifies the anchor production record is visible on UI and key fields match API.
     * Time is asserted only when UI renders it (optional per {@code ProductionsTable}).
     */
    public static void assertAnchorRowMatchesApi(ProductionJournalRow expectedFromApi,
                                                 List<ProductionJournalRow> displayedOnUi) {
        assertThat(expectedFromApi.getBatchNumber())
                .as("Опорний запис API має містити номер партії")
                .isNotBlank();

        ProductionJournalRow actualOnUi = findRowByBatchNumber(
                displayedOnUi, expectedFromApi.getBatchNumber())
                .orElseThrow(() -> new AssertionError(
                        "Опорний запис з партією «" + expectedFromApi.getBatchNumber() + "» не знайдено на UI"));

        assertRowEquals(expectedFromApi, actualOnUi, "опорний запис", true);
    }

    public static void assertRowEquals(ProductionJournalRow expected,
                                       ProductionJournalRow actual,
                                       String rowLabel) {
        assertRowEquals(expected, actual, rowLabel, false);
    }

    private static void assertRowEquals(ProductionJournalRow expected,
                                        ProductionJournalRow actual,
                                        String rowLabel,
                                        boolean timeOnlyIfDisplayedOnUi) {
        assertThat(normalizeText(actual.getProductName()))
                .as("%s — продукт", rowLabel)
                .isEqualTo(normalizeText(expected.getProductName()));

        assertThat(actual.getDate())
                .as("%s — дата", rowLabel)
                .isEqualTo(expected.getDate());

        if (!timeOnlyIfDisplayedOnUi || actual.getTime() != null) {
            if (timeOnlyIfDisplayedOnUi) {
                assertThat(actual.getTime())
                        .as("%s — час (відображено на UI)", rowLabel)
                        .isEqualTo(expected.getTime());
            } else {
                assertThat(actual.getTime())
                        .as("%s — час", rowLabel)
                        .isEqualTo(expected.getTime());
            }
        }

        assertThat(actual.getAmount())
                .as("%s — об'єм", rowLabel)
                .isCloseTo(expected.getAmount(), within(AMOUNT_EPSILON));

        assertThat(actual.getBatchNumber())
                .as("%s — номер партії", rowLabel)
                .isEqualTo(expected.getBatchNumber());

        assertThat(actual.getTechnologicalMapName())
                .as("%s — тех. карта", rowLabel)
                .isEqualTo(expected.getTechnologicalMapName());
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.strip();
    }
}
